/** Logical compiler-work assertions for unrolling and token emission inside PIT JVMs only. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import static java.lang.constant.ConstantDescs.*;

/** Two independently derived bounds; neither promises whole-compilation resource equivalence. */
final class CompilerWorkBudget {
    /** Sole compiler class this transformer may change. */
    private static final String TARGET = "dev/samhb/interleave/format/dsl/SourceCompiler";
    /** Runtime field owner, avoiding any dependency on agent classes from instrumented code. */
    private static final ClassDesc COMPILER = ClassDesc.of(TARGET.replace('/', '.'));
    /** JDK assertion injected into the application class. */
    private static final ClassDesc ASSERTION = ClassDesc.of("java.lang.AssertionError");
    /** Erased expansion signature, covering both scheduled and private syntax lists. */
    private static final String EXPAND = "(Ljava/util/List;Ljava/util/List;)Ljava/util/List;";
    /** Erased tokenizer signature. */
    private static final String TOKENIZE = "()Ljava/util/List;";

    /** Prevents constructing this instrumentation utility. */
    private CompilerWorkBudget() {}

    /** Installs the compiler transformer alongside the existing selector transformer.
     * @param instrumentation JVM instrumentation service
     */
    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new Transformer(), true);
    }

    /** Adds invocation-local bounds to an original or singly mutated compiler definition.
     * @param bytes compiler definition
     * @param redefinition whether PIT replaces an initially validated definition
     * @param loader defining loader, used to resolve application types while generating frames
     * @return rewritten compiler definition
     * @throws IllegalArgumentException if expected methods, field or checkpoints have drifted
     */
    static byte[] instrument(byte[] bytes, boolean redefinition, ClassLoader loader) {
        ClassFile file = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofClassLoading(loader)));
        ClassModel model = file.parse(bytes);
        if (!model.thisClass().asInternalName().equals(TARGET)
                || model.methods().stream().filter(m -> matches(m, "expand", EXPAND)).count() != 1
                || model.methods().stream().filter(m -> matches(m, "tokenize", TOKENIZE)).count() != 1
                || model.fields().stream().filter(f -> f.fieldName().equalsString("clean")
                    && f.fieldType().equalsString("Ljava/lang/String;")
                    && !f.flags().has(java.lang.reflect.AccessFlag.STATIC)).count() != 1) {
            throw new IllegalArgumentException("Unexpected compiler method/field shape");
        }
        int[] expansionSites = {0}, tokenSites = {0};
        var expansion = ClassTransform.transformingMethodBodies(m -> matches(m, "expand", EXPAND),
            CodeTransform.ofStateful(() -> new CodeTransform() {
                /** Fresh integer slot for one frame's remaining unrolling events. */
                private int remaining;

                /** Initializes the independent inclusive instruction bound.
                 * @param code destination body
                 */
                @Override public void atStart(CodeBuilder code) {
                    remaining = code.allocateLocal(TypeKind.INT);
                    code.ldc(64).istore(remaining);
                }

                /** Charges each repeat iteration or while check before ancestry allocation.
                 * @param code destination body
                 * @param element original bytecode element
                 */
                @Override public void accept(CodeBuilder code, CodeElement element) {
                    if (element instanceof InvokeInstruction invoke && invoke.owner().asInternalName().equals(TARGET)
                            && invoke.name().equalsString("ancestry")
                            && invoke.type().equalsString("(Ljava/util/List;II)Ljava/util/List;")) {
                        expansionSites[0]++;
                        code.iinc(remaining, -1).iload(remaining).ifThen(Opcode.IFLT, bad ->
                            bad.new_(ASSERTION).dup()
                                .ldc("Compiler expansion exceeded 64 unrolling events in one frame")
                                .invokespecial(ASSERTION, INIT_NAME,
                                    MethodTypeDesc.of(CD_void, CD_Object)).athrow());
                    }
                    code.with(element);
                }
            }));
        var tokens = ClassTransform.transformingMethodBodies(m -> matches(m, "tokenize", TOKENIZE),
            CodeTransform.ofStateful(() -> new CodeTransform() {
                /** Long slot keeps source.length()+1 exact without integer overflow. */
                private int remaining;

                /** Includes the one EOF token beyond the source-unit bound.
                 * @param code destination body
                 */
                @Override public void atStart(CodeBuilder code) {
                    remaining = code.allocateLocal(TypeKind.LONG);
                    code.aload(0).getfield(COMPILER, "clean", CD_String)
                        .invokevirtual(CD_String, "length", MethodTypeDesc.of(CD_int))
                        .i2l().lconst_1().ladd().lstore(remaining);
                }

                /** Charges both ordinary and EOF token emissions.
                 * @param code destination body
                 * @param element original bytecode element
                 */
                @Override public void accept(CodeBuilder code, CodeElement element) {
                    if (element instanceof InvokeInstruction invoke && invoke.owner().asInternalName().equals("java/util/List")
                            && invoke.name().equalsString("add")
                            && invoke.type().equalsString("(Ljava/lang/Object;)Z")) {
                        tokenSites[0]++;
                        code.lload(remaining).lconst_1().lsub().lstore(remaining)
                            .lload(remaining).lconst_0().lcmp().ifThen(Opcode.IFLT, bad ->
                                bad.new_(ASSERTION).dup()
                                    .ldc("Compiler tokenization exceeded source units plus EOF")
                                    .invokespecial(ASSERTION, INIT_NAME,
                                        MethodTypeDesc.of(CD_void, CD_Object)).athrow());
                    }
                    code.with(element);
                }
            }));
        byte[] result = file.transformClass(model, expansion.andThen(tokens));
        // A single call-removal mutation can remove one checkpoint; its progress guards remain intact.
        if ((expansionSites[0] != 3 && !(redefinition && expansionSites[0] == 2))
                || (tokenSites[0] != 2 && !(redefinition && tokenSites[0] == 1))) {
            throw new IllegalArgumentException("Unexpected compiler checkpoint shape");
        }
        return result;
    }

    /** Matches an instance-method layout without relying on source lines or local variable names.
     * @param method candidate method
     * @param name expected method name
     * @param descriptor expected erased descriptor
     * @return whether the method matches the required layout
     */
    private static boolean matches(MethodModel method, String name, String descriptor) {
        return method.methodName().equalsString(name) && method.methodType().equalsString(descriptor)
            && !method.flags().has(java.lang.reflect.AccessFlag.STATIC);
    }

    /** Filters unrelated definitions and makes initial binding failures visible. */
    private static final class Transformer implements ClassFileTransformer {
        /** Weaves assertions on initial definition and PIT redefinition.
         * @param module defining module
         * @param loader defining class loader
         * @param name JVM internal class name
         * @param redefining existing class, or null for initial definition
         * @param domain protection domain
         * @param bytes incoming definition
         * @return null for unrelated classes, instrumented bytes, or invalid bytes on binding failure
         */
        @Override public byte[] transform(Module module, ClassLoader loader, String name,
                Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
            if (!TARGET.equals(name)) return null;
            try {
                return instrument(bytes, redefining != null, loader);
            } catch (RuntimeException failure) {
                System.err.println("Compiler mutation harness rejected unexpected bytecode shape");
                return new byte[] {0};
            }
        }
    }
}
