/** PIT-only finite-graph, loader-closure and observer-event assertions, derived from model bounds rather than elapsed time. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import static java.lang.constant.ConstantDescs.*;

/** Instruments host checker loops only; target model bytecode is never loaded or executed on the host. */
final class BytecodeWorkBudget {
    /** Package containing the owned checker loops. */
    private static final String PACKAGE="dev/samhb/interleave/bytecode/";
    /** Exact iterative search entry. */
    private static final String SEARCH="(L"+PACKAGE+"BytecodeExplorer$Budget;)L"+PACKAGE+"BytecodeExplorer$Result;";
    /** Exact pure observer entry. */
    private static final String CHECK="(L"+PACKAGE+"BytecodeProgram;L"+PACKAGE+"BytecodeState;Z)L"+PACKAGE+"BytecodeChecks$Evaluation;";
    /** Exact private metadata-closure/setup entry. */
    private static final String LOAD="(L"+PACKAGE+"BytecodeLimits;)L"+PACKAGE+"BytecodeProgram;";
    /** Assertion has no runtime dependency on this agent. */
    private static final ClassDesc ASSERTION=ClassDesc.of("java.lang.AssertionError");
    /** Host checker owner whose admitted-state counter bounds every DFS prefix. */
    private static final ClassDesc EXPLORER=ClassDesc.of((PACKAGE+"BytecodeExplorer").replace('/','.'));
    /** Nullable external deadline representation, never target model state. */
    private static final ClassDesc BOXED_LONG=ClassDesc.of("java.lang.Long");

    /** Prevents construction of this test-only transformer utility. */
    private BytecodeWorkBudget() {}

    /** Adds the owned checker-progress transformer to the established PIT agent.
     * @param instrumentation host instrumentation service */
    static void install(Instrumentation instrumentation){instrumentation.addTransformer(new Transformer(),true);}

    /** Adds invocation-local work bounds, rejecting initial binding or checkpoint drift.
     * At every search prefix at mostS frames have been admitted, with at most8 selections and1
     * pop per frame:9S loop visits. S is the actual admitted count, not an unlimited caller budget.
     * A pure observer has at mostN opcodes, N helper returns and2 final/cutoff views:2N+2 visits.
     * @param bytes real original or singly mutated checker definition
     * @param redefinition whether PIT replaces a previously validated definition
     * @param loader host checker loader for stack-map resolution, never the model classpath
     * @return verified-shape instrumented definition
     * @throws IllegalArgumentException for binding drift */
    static byte[] instrument(byte[] bytes,boolean redefinition,ClassLoader loader) {
        var file=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader)));
        var model=file.parse(bytes);String name=model.thisClass().asInternalName();
        if(name.equals(PACKAGE+"BytecodeLoader"))return instrumentLoader(file,model,redefinition);
        boolean search=name.equals(PACKAGE+"BytecodeExplorer");
        if(!search && !name.equals(PACKAGE+"BytecodeChecks"))throw new IllegalArgumentException("Unexpected bytecode checker class");
        if(search && model.fields().stream().filter(field->field.fieldName().equalsString("states")
                && field.fieldType().equalsString("J") && !field.flags().has(java.lang.reflect.AccessFlag.STATIC)).count()!=1)
            throw new IllegalArgumentException("Unexpected search state-counter binding");
        if(model.methods().stream().filter(method->matches(method,search)).count()!=1)throw new IllegalArgumentException("Unexpected bytecode checker method");
        int[] checkpoints={0},resets={0};
        byte[] result=file.transformClass(model,ClassTransform.transformingMethodBodies(method->matches(method,search),CodeTransform.ofStateful(()->new CodeTransform(){
            /** Invocation-local remaining work under the original caller/observer allowance. */
            private int remaining;
            /** Actual search-prefix checkpoint count, independent of remaining caller work. */
            private int work;
            /** Saturated search-prefix allowance recomputed at each checkpoint. */
            private int allowance;
            /** Preserves the caller bound and starts prefix work, or an empty observer allowance.
             * @param code destination body */
            @Override public void atStart(CodeBuilder code) {
                remaining=code.allocateLocal(search ? TypeKind.LONG : TypeKind.INT);
                if(search) {
                    var budget=ClassDesc.of((PACKAGE+"BytecodeExplorer$Budget").replace('/','.'));
                    code.aload(1).invokevirtual(budget,"maxStates",MethodTypeDesc.of(CD_long)).lstore(remaining);
                    code.lload(remaining).ldc(Long.MAX_VALUE/9).lcmp().ifThenElse(Opcode.IFGT,
                        high->high.ldc(Long.MAX_VALUE).lstore(remaining),
                        bounded->bounded.lload(remaining).ldc(9L).lmul().lstore(remaining));
                    work=code.allocateLocal(TypeKind.LONG);code.lconst_0().lstore(work);allowance=code.allocateLocal(TypeKind.LONG);
                } else code.iconst_0().istore(remaining);
            }
            /** Charges independently derived loop work and resets each fresh observer invocation.
             * @param code destination body
             * @param element original or PIT-mutated instruction */
            @Override public void accept(CodeBuilder code,CodeElement element) {
                boolean reset=false;
                if(element instanceof InvokeInstruction call) {
                    boolean checkpoint=search ? call.owner().asInternalName().equals("java/util/ArrayDeque")
                        && call.name().equalsString("peek") && call.type().equalsString("()Ljava/lang/Object;")
                        : call.owner().asInternalName().equals("java/lang/Thread") && call.name().equalsString("currentThread")
                            && call.type().equalsString("()Ljava/lang/Thread;");
                    if(checkpoint) {
                        checkpoints[0]++;
                        if(search) {
                            code.lload(remaining).lconst_1().lsub().lstore(remaining).lload(remaining).lconst_0().lcmp()
                                .ifThen(Opcode.IFLT,bad->bad.new_(ASSERTION).dup().ldc("Bytecode logical progress bound exceeded")
                                    .invokespecial(ASSERTION,INIT_NAME,MethodTypeDesc.of(CD_void,CD_Object)).athrow());
                            code.lload(work).lconst_1().ladd().lstore(work);
                            code.aload(0).getfield(EXPLORER,"states",CD_long).ldc(Long.MAX_VALUE/9).lcmp()
                                .ifThenElse(Opcode.IFGT,high->high.ldc(Long.MAX_VALUE).lstore(allowance),
                                    bounded->bounded.aload(0).getfield(EXPLORER,"states",CD_long).ldc(9L).lmul().lstore(allowance));
                            code.lload(work).lload(allowance).lcmp();
                        } else code.iinc(remaining,-1).iload(remaining);
                        code.ifThen(search ? Opcode.IFGT : Opcode.IFLT,bad->bad.new_(ASSERTION).dup().ldc("Bytecode logical progress bound exceeded")
                            .invokespecial(ASSERTION,INIT_NAME,MethodTypeDesc.of(CD_void,CD_Object)).athrow());
                    }
                    reset=!search && call.owner().asInternalName().equals(PACKAGE+"BytecodeState") && call.name().equalsString("start")
                        && call.type().equalsString("(IL"+PACKAGE+"BytecodeMethod;Ljava/util/List;)V");
                }
                code.with(element);
                if(reset) {
                    resets[0]++;var program=ClassDesc.of((PACKAGE+"BytecodeProgram").replace('/','.'));
                    var limits=ClassDesc.of((PACKAGE+"BytecodeLimits").replace('/','.'));
                    code.aload(0).getfield(program,"limits",limits).invokevirtual(limits,"checkInstructions",MethodTypeDesc.of(CD_int))
                        .iconst_2().imul().iconst_2().iadd().istore(remaining);
                }
            }
        })));
        // One call-removal mutation can omit a checkpoint/reset; its remaining original guards still run.
        if((checkpoints[0]!=1 && !(redefinition && checkpoints[0]==0))
            || (!search && resets[0]!=1 && !(redefinition && resets[0]==0)))throw new IllegalArgumentException("Unexpected bytecode progress checkpoint shape");
        return search ? stabilizeZeroDeadline(file,file.parse(result),redefinition) : result;
    }

    /** Fixes the external clock input at the exact zero-duration boundary in the test profile.
     * A zero allowance must stop before admitting a state; pinning elapsed time to zero preserves
     * that result while letting assertions distinguish the real >= boundary from its > mutant.
     * Untimed and positive deadlines retain their host-clock inputs. No agent runtime hook is added.
     * @param file host checker class-file service
     * @param model definition with progress assertions
     * @param redefinition whether a single call-removal mutant may omit the clock checkpoint
     * @return definition with deterministic zero-duration clock input
     * @throws IllegalArgumentException for deadline binding/checkpoint drift */
    private static byte[] stabilizeZeroDeadline(ClassFile file,ClassModel model,boolean redefinition) {
        var methods=model.methods().stream().filter(method->method.methodName().equalsString("stopReason")
            && method.methodType().equalsString("()Ljava/lang/String;") && !method.flags().has(java.lang.reflect.AccessFlag.STATIC)).toList();
        if(methods.size()!=1 || model.fields().stream().filter(field->field.fieldName().equalsString("maxTimeMillis")
                && field.fieldType().equalsString("Ljava/lang/Long;") && !field.flags().has(java.lang.reflect.AccessFlag.STATIC)).count()!=1
            || model.fields().stream().filter(field->field.fieldName().equalsString("startedMillis")
                && field.fieldType().equalsString("J") && !field.flags().has(java.lang.reflect.AccessFlag.STATIC)).count()!=1)
            throw new IllegalArgumentException("Unexpected search deadline binding");
        int[] checkpoints={0};
        var result=file.transformClass(model,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("stopReason")
                && method.methodType().equalsString("()Ljava/lang/String;"),
            CodeTransform.ofStateful(()->new CodeTransform(){
                /** Local copy of the current clock input, preserving the original operand stack. */ private int now;
                /** Allocates one method-local clock slot.
                 * @param code destination body */
                @Override public void atStart(CodeBuilder code){now=code.allocateLocal(TypeKind.LONG);}
                /** Pins only a zero-duration comparison to equality, leaving the original branch intact.
                 * @param code destination body
                 * @param element original or mutated instruction */
                @Override public void accept(CodeBuilder code,CodeElement element) {
                    code.with(element);
                    if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/lang/System")
                        && call.name().equalsString("currentTimeMillis") && call.type().equalsString("()J")) {
                        checkpoints[0]++;
                        code.dup2().lstore(now).aload(0).getfield(EXPLORER,"maxTimeMillis",BOXED_LONG)
                            .ifThen(Opcode.IFNONNULL,present->present.aload(0).getfield(EXPLORER,"maxTimeMillis",BOXED_LONG)
                                .invokevirtual(BOXED_LONG,"longValue",MethodTypeDesc.of(CD_long)).lconst_0().lcmp()
                                .ifThen(Opcode.IFEQ,zero->zero.aload(0).lload(now).putfield(EXPLORER,"startedMillis",CD_long)));
                    }
                }
            })));
        if(checkpoints[0]!=1 && !(redefinition && checkpoints[0]==0))throw new IllegalArgumentException("Unexpected search clock checkpoint shape");
        return result;
    }

    /** Bounds closure passes independently of the loader's convergence tests.
     * A completed pass either ends loading or discovers at least one new class. With at most64
     * application classes there are at most64 passes, each reading Map.size twice. Two spare
     * checkpoints accommodate framing; the assertion never consults the mutated loop condition.
     * @param file host-checker class-file service
     * @param model original or singly mutated loader
     * @param redefinition whether PIT replaced a previously validated definition
     * @return loader with invocation-local progress assertions */
    private static byte[] instrumentLoader(ClassFile file,ClassModel model,boolean redefinition) {
        if(model.methods().stream().filter(m->m.methodName().equalsString("load") && m.methodType().equalsString(LOAD)).count()!=1)
            throw new IllegalArgumentException("Unexpected loader closure binding");
        int[] checkpoints={0};
        var result=file.transformClass(model,ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("load") && method.methodType().equalsString(LOAD),
            CodeTransform.ofStateful(()->new CodeTransform(){
                /** Independent invocation-local class-growth checkpoint allowance. */
                private int remaining;
                /** Starts the independently derived two-checkpoints-per-pass allowance.
                 * @param code destination body */
                @Override public void atStart(CodeBuilder code){remaining=code.allocateLocal(TypeKind.INT);code.ldc(130).istore(remaining);}
                /** Charges each fixed-point Map.size checkpoint before the original call.
                 * @param code destination body
                 * @param element original or mutated instruction */
                @Override public void accept(CodeBuilder code,CodeElement element) {
                    if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/util/Map")
                        && call.name().equalsString("size") && call.type().equalsString("()I")) {
                        checkpoints[0]++;code.iinc(remaining,-1).iload(remaining).ifThen(Opcode.IFLT,bad->bad.new_(ASSERTION).dup()
                            .ldc("Bytecode logical loader closure bound exceeded").invokespecial(ASSERTION,INIT_NAME,MethodTypeDesc.of(CD_void,CD_Object)).athrow());
                    }
                    code.with(element);
                }
            })));
        if(checkpoints[0]!=2 && !(redefinition && checkpoints[0]==1))throw new IllegalArgumentException("Unexpected loader closure checkpoint shape");
        return result;
    }

    /** Matches the exact static/instance method and local-slot layout used by the assertions.
     * @param method candidate method
     * @param search search or observer definition
     * @return whether the method owns the expected loop */
    private static boolean matches(MethodModel method,boolean search) {
        return method.methodName().equalsString(search ? "explore" : "evaluate") && method.methodType().equalsString(search ? SEARCH : CHECK)
            && method.flags().has(java.lang.reflect.AccessFlag.STATIC)!=search;
    }

    /** Filters unrelated classes and fails visibly if the owned host-checker shape drifts. */
    private static final class Transformer implements ClassFileTransformer {
        /** Restricts construction to this utility. */
        private Transformer() {}
        /** Handles original definitions and PIT redefinitions without touching model application classes.
         * @param module host module
         * @param loader checker loader
         * @param name checker internal name
         * @param redefining existing definition, otherwise null
         * @param domain protection domain
         * @param bytes incoming definition
         * @return transformed bytes, null for unrelated classes, invalid bytes on binding failure */
        @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> redefining,ProtectionDomain domain,byte[] bytes) {
            if(!(PACKAGE+"BytecodeExplorer").equals(name) && !(PACKAGE+"BytecodeChecks").equals(name) && !(PACKAGE+"BytecodeLoader").equals(name))return null;
            try {return instrument(bytes,redefining!=null,loader);}
            catch(RuntimeException failure){System.err.println("Bytecode mutation harness rejected unexpected shape");return new byte[]{0};}
        }
    }
}
