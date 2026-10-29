/** Entry point for PIT-only selector and compiler logical-work assertions. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import static java.lang.constant.ConstantDescs.*;

/** Stable agent entry point; no wall-clock limit, production hook or configurable budget. */
public final class SelectorBudgetAgent {
    /** The application class handled by the selector transformer. */
    private static final String TARGET = "dev/samhb/interleave/por/PropertyPersistentSetComputer";
    /** Exact instance-method signature: configuration, seed and the owner-membership array. */
    private static final String COMPONENT = "(Ldev/samhb/interleave/core/Configuration;I[Z)Ljava/util/List;";
    /** Assertion type injected without depending on an agent class at application runtime. */
    private static final ClassDesc ASSERTION = ClassDesc.of("java.lang.AssertionError");

    /** Prevents construction of this JVM entry point. */
    private SelectorBudgetAgent() {}

    /** Installs both narrow transformers, including PIT redefinitions of already loaded classes.
     * @param arguments unused standard Java-agent argument
     * @param instrumentation JVM instrumentation service
     */
    public static void premain(String arguments, Instrumentation instrumentation) {
        instrumentation.addTransformer(new Transformer(), true);
        CompilerWorkBudget.install(instrumentation);
    }

    /** Instruments one expected selector definition with an invocation-local expansion counter.
     * @param bytes original or PIT-mutated selector definition
     * @param redefinition whether PIT is replacing a previously validated definition
     * @return rewritten definition with the finite-owner assertion
     * @throws IllegalArgumentException if the expected method or expansion checkpoint has drifted
     */
    static byte[] instrument(byte[] bytes, boolean redefinition) {
        ClassFile file = ClassFile.of();
        ClassModel model = file.parse(bytes);
        long methods = model.methods().stream().filter(SelectorBudgetAgent::component).count();
        if (!model.thisClass().asInternalName().equals(TARGET) || methods != 1) {
            throw new IllegalArgumentException("Unexpected selector method shape");
        }
        int[] checkpoints = {0};
        byte[] result = file.transformClass(model, ClassTransform.transformingMethodBodies(
            SelectorBudgetAgent::component, CodeTransform.ofStateful(() -> new CodeTransform() {
                /** Fresh local slot for this component invocation's remaining owner count. */
                private int remaining;

                /** Initializes the bound from the existing owner-membership array.
                 * @param code destination method body
                 */
                @Override public void atStart(CodeBuilder code) {
                    remaining = code.allocateLocal(TypeKind.INT);
                    code.aload(3).arraylength().istore(remaining);
                }

                /** Asserts the bound immediately before recording an expanded owner.
                 * @param code destination method body
                 * @param element original bytecode element
                 */
                @Override public void accept(CodeBuilder code, CodeElement element) {
                    if (element instanceof InvokeInstruction invoke
                            && invoke.owner().asInternalName().equals("java/util/List")
                            && invoke.name().equalsString("add")
                            && invoke.type().equalsString("(Ljava/lang/Object;)Z")) {
                        checkpoints[0]++;
                        code.iinc(remaining, -1).iload(remaining).ifThen(Opcode.IFLT, bad ->
                            bad.new_(ASSERTION).dup()
                                .ldc("Selector expanded more owners than the finite program contains")
                                .invokespecial(ASSERTION, INIT_NAME,
                                    MethodTypeDesc.of(CD_void, CD_Object)).athrow());
                    }
                    code.with(element);
                }
            })));
        // A single call-removal mutant can omit List.add. Its original seen/queue guards remain intact.
        if (checkpoints[0] != 1 && !(redefinition && checkpoints[0] == 0)) {
            throw new IllegalArgumentException("Unexpected selector expansion shape");
        }
        return result;
    }

    /** Recognizes the one instance method whose third argument supplies the finite owner count.
     * @param method candidate method
     * @return whether its name, descriptor and instance-method local layout match
     */
    private static boolean component(MethodModel method) {
        return method.methodName().equalsString("component") && method.methodType().equalsString(COMPONENT)
            && !method.flags().has(java.lang.reflect.AccessFlag.STATIC);
    }

    /** Keeps unrelated classes untouched and rejects selector shape drift instead of omitting the check. */
    private static final class Transformer implements ClassFileTransformer {
        /** Rewrites the selector on initial definition and PIT redefinition.
         * @param module defining module
         * @param loader defining class loader
         * @param name JVM internal class name
         * @param redefining existing class, or null during initial definition
         * @param domain defining protection domain
         * @param bytes incoming class definition
         * @return null for unrelated classes, instrumented bytes, or invalid bytes to reject drift
         */
        @Override public byte[] transform(Module module, ClassLoader loader, String name,
                Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
            if (!TARGET.equals(name)) return null;
            try {
                return instrument(bytes, redefining != null);
            } catch (RuntimeException failure) {
                // Transformer exceptions are ignored by the JVM; invalid bytes force a visible failure.
                System.err.println("Selector mutation harness rejected unexpected bytecode shape");
                return new byte[] {0};
            }
        }
    }
}
