/** PIT-only lexical-work and operator-cursor assertions for the expression parser. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import static java.lang.constant.ConstantDescs.*;

/** Bounds invocation-local work without changing production parsing or wall-clock policy. */
final class ParserWorkBudget {
    /** Sole class this transformer may change. */
    private static final String TARGET = "dev/samhb/interleave/format/dsl/Parser";
    /** Runtime owner; instrumented code has no agent-class dependency. */
    private static final ClassDesc PARSER = ClassDesc.of(TARGET.replace('/', '.'));
    /** Logical failure type visible to application assertions. */
    private static final ClassDesc ASSERTION = ClassDesc.of("java.lang.AssertionError");
    /** Required lexical methods, in checkpoint-ledger order. */
    private static final List<String> SCANS = List.of("parseIdent", "parseIntLit", "parseWord");
    /** Exact return layouts for the three lexical methods. */
    private static final List<String> TYPES = List.of("()Ljava/lang/String;",
        "()Ldev/samhb/interleave/format/dsl/Expr;", "()Ljava/lang/String;");
    /** Independently counted static peek sites in each original method. */
    private static final List<Integer> PEEKS = List.of(3, 3, 2);

    /** Prevents construction of this instrumentation utility. */
    private ParserWorkBudget() {}

    /**
     * Registers this narrow transformer with the existing PIT-only agent.
     * @param instrumentation JVM instrumentation service
     */
    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new Transformer(), true);
    }

    /**
     * Adds frame-local logical guards to the original or a single-mutant parser definition.
     * @param bytes original or mutated parser class
     * @param redefinition whether PIT replaces a previously validated initial definition
     * @param loader defining loader used to resolve application types for frame generation
     * @return rewritten parser bytes, with no new fields or helper dependencies
     * @throws IllegalArgumentException for method/field/checkpoint binding drift
     */
    static byte[] instrument(byte[] bytes, boolean redefinition, ClassLoader loader) {
        ClassFile file = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofClassLoading(loader)));
        ClassModel model = file.parse(bytes);
        if (!model.thisClass().asInternalName().equals(TARGET)
                || !field(model, "input", "Ljava/lang/String;", true)
                || !field(model, "pos", "I", false)
                || model.methods().stream().filter(m -> matches(m, "match", "(Ljava/lang/String;)Z")).count() != 1) {
            throw new IllegalArgumentException("Unexpected parser method/field shape");
        }
        for (int i = 0; i < SCANS.size(); i++) {
            int index = i;
            if (model.methods().stream().filter(m -> matches(m, SCANS.get(index), TYPES.get(index))).count() != 1) {
                throw new IllegalArgumentException("Unexpected parser lexical method shape");
            }
        }
        int[] sites = new int[3], returns = {0};
        ClassTransform transform = matching(returns);
        for (int i = 0; i < SCANS.size(); i++) transform = transform.andThen(lexical(i, sites));
        byte[] result = file.transformClass(model, transform);
        int missing = 0;
        for (int i = 0; i < sites.length; i++) {
            int difference = PEEKS.get(i) - sites[i];
            if (difference < 0 || difference > 1) {
                throw new IllegalArgumentException("Unexpected parser peek checkpoint shape");
            }
            missing += difference;
        }
        // A single PIT call-removal mutant may omit one peek; original consume/progress remains.
        if (returns[0] != 2 || missing > (redefinition ? 1 : 0)) {
            throw new IllegalArgumentException("Unexpected parser checkpoint shape");
        }
        return result;
    }

    /**
     * Charges each lexical peek against the independent inclusive 2*N+3 bound.
     * @param index lexical method's checkpoint-ledger index
     * @param sites static checkpoint counts, updated during transformation
     * @return transform with a fresh long counter for every method invocation
     */
    private static ClassTransform lexical(int index, int[] sites) {
        return ClassTransform.transformingMethodBodies(
            method -> matches(method, SCANS.get(index), TYPES.get(index)),
            CodeTransform.ofStateful(() -> new CodeTransform() {
                /** Invocation-local remaining peek operations, widened before arithmetic. */
                private int remaining;

                /**
                 * Allows at most two peeks per source unit plus three endpoint checks.
                 * @param code destination method body
                 */
                @Override public void atStart(CodeBuilder code) {
                    remaining = code.allocateLocal(TypeKind.LONG);
                    code.aload(0).getfield(PARSER, "input", CD_String)
                        .invokevirtual(CD_String, "length", MethodTypeDesc.of(CD_int))
                        .i2l().ldc(2L).lmul().ldc(3L).ladd().lstore(remaining);
                }

                /**
                 * Checks work before the real peek, independently of a removable consume call.
                 * @param code destination body
                 * @param element original bytecode element
                 */
                @Override public void accept(CodeBuilder code, CodeElement element) {
                    if (element instanceof InvokeInstruction invoke
                            && invoke.owner().asInternalName().equals(TARGET)
                            && invoke.name().equalsString("peek") && invoke.type().equalsString("()C")) {
                        sites[index]++;
                        code.lload(remaining).lconst_1().lsub().lstore(remaining)
                            .lload(remaining).lconst_0().lcmp().ifThen(Opcode.IFLT, bad ->
                                bad.new_(ASSERTION).dup()
                                    .ldc("Parser lexical scan exceeded twice input units plus three")
                                    .invokespecial(ASSERTION, INIT_NAME,
                                        MethodTypeDesc.of(CD_void, CD_Object)).athrow());
                    }
                    code.with(element);
                }
            }));
    }

    /**
     * Checks each operator-match return against that invocation's entry cursor.
     * @param returns static IRETURN checkpoint count, updated during transformation
     * @return transform permitting operator misses and caller-local cursor handling
     */
    private static ClassTransform matching(int[] returns) {
        return ClassTransform.transformingMethodBodies(
            method -> matches(method, "match", "(Ljava/lang/String;)Z"),
            CodeTransform.ofStateful(() -> new CodeTransform() {
                /** Cursor snapshot belonging only to this match call. */
                private int start;

                /**
                 * Snapshots the cursor before the original match logic runs.
                 * @param code destination body
                 */
                @Override public void atStart(CodeBuilder code) {
                    start = code.allocateLocal(TypeKind.INT);
                    code.aload(0).getfield(PARSER, "pos", CD_int).istore(start);
                }

                /**
                 * A match may leave the cursor alone or advance it, but cannot move backwards.
                 * @param code destination body
                 * @param element original bytecode element
                 */
                @Override public void accept(CodeBuilder code, CodeElement element) {
                    if (element instanceof ReturnInstruction instruction && instruction.opcode() == Opcode.IRETURN) {
                        returns[0]++;
                        code.aload(0).getfield(PARSER, "pos", CD_int).iload(start)
                            .ifThen(Opcode.IF_ICMPLT, bad ->
                                bad.new_(ASSERTION).dup()
                                    .ldc("Parser operator matching moved cursor backwards")
                                    .invokespecial(ASSERTION, INIT_NAME,
                                        MethodTypeDesc.of(CD_void, CD_Object)).athrow());
                    }
                    code.with(element);
                }
            }));
    }

    /**
     * Binds one private instance method without relying on source lines or local names.
     * @param method candidate method
     * @param name required name
     * @param descriptor required JVM descriptor
     * @return whether the method has the exact supported layout
     */
    private static boolean matches(MethodModel method, String name, String descriptor) {
        return method.methodName().equalsString(name) && method.methodType().equalsString(descriptor)
            && method.flags().has(java.lang.reflect.AccessFlag.PRIVATE)
            && !method.flags().has(java.lang.reflect.AccessFlag.STATIC);
    }

    /**
     * Binds a single private instance field and its required mutability.
     * @param model parser definition
     * @param name required field name
     * @param descriptor required JVM type
     * @param immutable whether FINAL is required
     * @return whether exactly one matching field exists
     */
    private static boolean field(ClassModel model, String name, String descriptor, boolean immutable) {
        return model.fields().stream().filter(f -> f.fieldName().equalsString(name)
            && f.fieldType().equalsString(descriptor)
            && f.flags().has(java.lang.reflect.AccessFlag.PRIVATE)
            && !f.flags().has(java.lang.reflect.AccessFlag.STATIC)
            && f.flags().has(java.lang.reflect.AccessFlag.FINAL) == immutable).count() == 1;
    }

    /** Rejects binding drift visibly instead of allowing the JVM to ignore transformer exceptions. */
    private static final class Transformer implements ClassFileTransformer {
        /** Creates the target-filtered transformer registered by install. */
        private Transformer() {}

        /**
         * Weaves initial definitions and PIT replacements, filtering all unrelated classes.
         * @param module defining module
         * @param loader defining loader
         * @param name internal JVM class name
         * @param redefining already loaded class, or null on initial definition
         * @param domain protection domain
         * @param bytes incoming definition
         * @return null for unrelated definitions, transformed bytes, or invalid bytes on drift
         */
        @Override public byte[] transform(Module module, ClassLoader loader, String name,
                Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
            if (!TARGET.equals(name)) return null;
            try {
                return instrument(bytes, redefining != null, loader);
            } catch (RuntimeException failure) {
                System.err.println("Parser mutation harness rejected unexpected bytecode shape");
                return new byte[] {0};
            }
        }
    }
}
