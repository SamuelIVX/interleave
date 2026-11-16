/** PIT-only finite-graph and observer-event assertions, derived from model bounds rather than elapsed time. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import static java.lang.constant.ConstantDescs.*;

/** Instruments host checker loops only; target model bytecode is never loaded or executed on the host. */
final class BytecodeWorkBudget {
    /** Package containing the two owned checker loops. */
    private static final String PACKAGE="dev/samhb/interleave/bytecode/";
    /** Exact iterative search entry. */
    private static final String SEARCH="(L"+PACKAGE+"BytecodeExplorer$Budget;)L"+PACKAGE+"BytecodeExplorer$Result;";
    /** Exact pure observer entry. */
    private static final String CHECK="(L"+PACKAGE+"BytecodeProgram;L"+PACKAGE+"BytecodeState;Z)L"+PACKAGE+"BytecodeChecks$Evaluation;";
    /** Assertion has no runtime dependency on this agent. */
    private static final ClassDesc ASSERTION=ClassDesc.of("java.lang.AssertionError");

    /** Prevents construction of this test-only transformer utility. */
    private BytecodeWorkBudget() {}

    /** Adds the two narrow transformers to the established PIT agent.
     * @param instrumentation host instrumentation service */
    static void install(Instrumentation instrumentation){instrumentation.addTransformer(new Transformer(),true);}

    /** Adds invocation-local work bounds, rejecting initial binding or checkpoint drift.
     * Search expands at mostS frames, with at most8 selections and1 pop per frame:9S loop visits.
     * A pure observer has at mostN opcodes, N helper returns and2 final/cutoff views:2N+2 visits.
     * @param bytes real original or singly mutated checker definition
     * @param redefinition whether PIT replaces a previously validated definition
     * @param loader host checker loader for stack-map resolution, never the model classpath
     * @return verified-shape instrumented definition
     * @throws IllegalArgumentException for binding drift */
    static byte[] instrument(byte[] bytes,boolean redefinition,ClassLoader loader) {
        var file=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader)));
        var model=file.parse(bytes);String name=model.thisClass().asInternalName();boolean search=name.equals(PACKAGE+"BytecodeExplorer");
        if(!search && !name.equals(PACKAGE+"BytecodeChecks"))throw new IllegalArgumentException("Unexpected bytecode checker class");
        if(model.methods().stream().filter(method->matches(method,search)).count()!=1)throw new IllegalArgumentException("Unexpected bytecode checker method");
        int[] checkpoints={0},resets={0};
        byte[] result=file.transformClass(model,ClassTransform.transformingMethodBodies(method->matches(method,search),CodeTransform.ofStateful(()->new CodeTransform(){
            /** Invocation-local counter; observer initialization resets it for each declaration. */
            private int remaining;
            /** Initializes a saturated graph bound or an empty observer allowance pending root start.
             * @param code destination body */
            @Override public void atStart(CodeBuilder code) {
                remaining=code.allocateLocal(search ? TypeKind.LONG : TypeKind.INT);
                if(search) {
                    var budget=ClassDesc.of((PACKAGE+"BytecodeExplorer$Budget").replace('/','.'));
                    code.aload(1).invokevirtual(budget,"maxStates",MethodTypeDesc.of(CD_long)).lstore(remaining);
                    code.lload(remaining).ldc(Long.MAX_VALUE/9).lcmp().ifThenElse(Opcode.IFGT,
                        high->high.ldc(Long.MAX_VALUE).lstore(remaining),
                        bounded->bounded.lload(remaining).ldc(9L).lmul().lstore(remaining));
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
                        if(search)code.lload(remaining).lconst_1().lsub().lstore(remaining).lload(remaining).lconst_0().lcmp();
                        else code.iinc(remaining,-1).iload(remaining);
                        code.ifThen(Opcode.IFLT,bad->bad.new_(ASSERTION).dup().ldc("Bytecode logical progress bound exceeded")
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
            if(!(PACKAGE+"BytecodeExplorer").equals(name) && !(PACKAGE+"BytecodeChecks").equals(name))return null;
            try {return instrument(bytes,redefining!=null,loader);}
            catch(RuntimeException failure){System.err.println("Bytecode mutation harness rejected unexpected shape");return new byte[]{0};}
        }
    }
}
