/**
 * Heap-profile opcode/purity admission and structured compiler monitor-cleanup validation.
 */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeLoader.problem;

/**
 * Additional profile verification beyond the JDK class-file verifier.
 */
final class BytecodeVerifier {
    /**
     * Prevents construction of the stateless profile verifier.
     */
    private BytecodeVerifier() {}
    /**
     * Category-one operations whose semantics are implemented by OpcodeExecutor.
     */
    private static final Set<Opcode> SIMPLE=EnumSet.of(Opcode.NOP,Opcode.POP,Opcode.POP2,Opcode.DUP,
        Opcode.DUP_X1,Opcode.DUP_X2,Opcode.DUP2,Opcode.DUP2_X1,Opcode.DUP2_X2,Opcode.SWAP,
        Opcode.IADD,Opcode.ISUB,Opcode.IMUL,Opcode.IDIV,Opcode.IREM,Opcode.INEG,
        Opcode.ISHL,Opcode.ISHR,Opcode.IUSHR,Opcode.IAND,Opcode.IOR,Opcode.IXOR,
        Opcode.I2B,Opcode.I2C,Opcode.I2S,Opcode.ARRAYLENGTH,Opcode.MONITORENTER,Opcode.MONITOREXIT,Opcode.ATHROW);
    /**
     * Supported conditional and unconditional branches.
     */
    private static final Set<Opcode> BRANCHES=EnumSet.of(Opcode.IFEQ,Opcode.IFNE,Opcode.IFLT,Opcode.IFGE,
        Opcode.IFGT,Opcode.IFLE,Opcode.IF_ICMPEQ,Opcode.IF_ICMPNE,Opcode.IF_ICMPLT,Opcode.IF_ICMPGE,
        Opcode.IF_ICMPGT,Opcode.IF_ICMPLE,Opcode.IF_ACMPEQ,Opcode.IF_ACMPNE,Opcode.IFNULL,Opcode.IFNONNULL,
        Opcode.GOTO,Opcode.GOTO_W);

    /**
     * Rejects unsupported operations/handlers before any setup or search executes.
     * @param method decoded reachable method
     * @param role allocation/purity context
     */
    static void validate(BytecodeMethod method,BytecodeLoader.Role role) {
        handlerRanges(method);
        for (var op : method.ops.values()) {
            Instruction instruction=op.instruction(); Opcode code=instruction.opcode();
            boolean allowed=switch(instruction) {
                case LoadInstruction load -> category(load.typeKind());
                case StoreInstruction store -> category(store.typeKind());
                case IncrementInstruction ignored -> true;
                case ConstantInstruction constant -> code==Opcode.ACONST_NULL || constant.constantValue() instanceof Integer || constant.constantValue() instanceof String;
                case ReturnInstruction returned -> returned.typeKind()==TypeKind.VOID || category(returned.typeKind());
                case BranchInstruction ignored -> BRANCHES.contains(code);
                case LookupSwitchInstruction ignored -> true;
                case TableSwitchInstruction ignored -> true;
                case FieldInstruction ignored -> true;
                case InvokeInstruction ignored -> true;
                case NewObjectInstruction ignored -> true;
                case NewPrimitiveArrayInstruction ignored -> true;
                case NewReferenceArrayInstruction ignored -> true;
                case NewMultiArrayInstruction ignored -> true;
                case TypeCheckInstruction ignored -> true;
                case ArrayLoadInstruction load -> load.typeKind()!=TypeKind.LONG && load.typeKind()!=TypeKind.FLOAT && load.typeKind()!=TypeKind.DOUBLE;
                case ArrayStoreInstruction store -> store.typeKind()!=TypeKind.LONG && store.typeKind()!=TypeKind.FLOAT && store.typeKind()!=TypeKind.DOUBLE;
                default -> SIMPLE.contains(code);
            };
            if (!allowed)
                throw problem("UNSUPPORTED","Unsupported reachable opcode "+code,method.location(op.bci()));
            if (role==BytecodeLoader.Role.CHECK && (instruction instanceof NewObjectInstruction || instruction instanceof NewPrimitiveArrayInstruction
                || instruction instanceof NewReferenceArrayInstruction || instruction instanceof NewMultiArrayInstruction || instruction instanceof ArrayStoreInstruction
                || code==Opcode.PUTFIELD || code==Opcode.PUTSTATIC || code==Opcode.MONITORENTER || code==Opcode.MONITOREXIT))
                throw problem("PURITY","Checks cannot mutate, allocate or synchronize",method.location(op.bci()));
            for (int successor : successors(method,op)) {
                if (!method.ops.containsKey(successor)) throw problem("MALFORMED","Invalid instruction target",method.location(op.bci()));
            }
        }
    }

    /**
     * Admits only the heap profile's category-one load and store types.
     * @param kind verifier type kind
     * @return whether one-slot int/reference transport is supported
     */
    private static boolean category(TypeKind kind) { return kind==TypeKind.INT || kind==TypeKind.REFERENCE; }

    /** Validates handler region and target positions before full operand verification.
     * @param method decoded method
     * @throws BytecodeLoadException for malformed handler bounds */
    private static void handlerRanges(BytecodeMethod method) {
        for(var handler:method.code.exceptionHandlers()) {
            int start=method.target(handler.tryStart()),end=method.target(handler.tryEnd()),target=method.target(handler.handler());
            if(start>=end || !method.ops.containsKey(start) || (end!=method.code.codeLength() && !method.ops.containsKey(end)) || !method.ops.containsKey(target))
                throw problem("MALFORMED","Invalid exception handler bounds",method.location(target));
        }
    }

    /**
     * Propagates lock-slot stacks through all control paths, including protected-entry handler seeds.
     * @param method decoded method
     * @param hierarchy frozen closed hierarchy
     */
    static void monitors(BytecodeMethod method,BytecodeHierarchy hierarchy) {
        if(method.code.exceptionHandlers().isEmpty() && method.ops.values().stream().noneMatch(op->op.instruction().opcode()==Opcode.MONITORENTER || op.instruction().opcode()==Opcode.MONITOREXIT))return;
        var throwableTypes=hierarchy.types.keySet().stream().filter(name->hierarchy.assignable(name,"java/lang/Throwable")).toList();
        var predecessors=new HashMap<Integer,Set<Integer>>();
        for(var op:method.ops.values()) for(int next:successors(method,op))
            predecessors.computeIfAbsent(next,ignored->new HashSet<>()).add(op.bci());
        for(var handler:method.code.exceptionHandlers()) {
            int bci=method.target(handler.handler());
            if(!predecessors.getOrDefault(bci,Set.of()).isEmpty())
                throw problem("UNSUPPORTED","Cleanup cannot have normal control-flow entry",method.location(bci));
        }
        var states=new HashMap<Integer,List<Integer>>(); var queue=new ArrayDeque<Integer>();
        merge(method,states,queue,0,List.of());
        while(!queue.isEmpty()) {
            int bci=queue.removeFirst(); var op=method.ops.get(bci); var before=states.get(bci);
            var locks=new ArrayList<>(before); Instruction instruction=op.instruction(); Opcode code=instruction.opcode();
            // Seed even nonthrowing compiler regions, so unreachable cleanup cannot forge a lock proof.
            for(var handler : method.code.exceptionHandlers()) {
                if(method.target(handler.tryStart())==bci)for(String type:throwableTypes) {
                    int selected=method.handler(bci,type,hierarchy);if(selected>=0)merge(method,states,queue,selected,before);
                }
            }
            if (instruction instanceof StoreInstruction store && locks.contains(store.slot())
                || instruction instanceof IncrementInstruction increment && locks.contains(increment.slot()))
                throw problem("MALFORMED","Active monitor slot overwritten",method.location(bci));
            if (code==Opcode.MONITORENTER) {
                var previous=method.ops.lowerEntry(bci);
                if (previous==null || !(previous.getValue().instruction() instanceof StoreInstruction saved)
                    || saved.typeKind()!=TypeKind.REFERENCE || method.ops.lowerEntry(previous.getKey())==null
                    || method.ops.lowerEntry(previous.getKey()).getValue().instruction().opcode()!=Opcode.DUP)
                    throw problem("UNSUPPORTED","Monitor entry needs compiler lock-slot wiring",method.location(bci));
                sequentialEntry(method,predecessors,method.ops.lowerKey(previous.getKey()),previous.getKey());
                sequentialEntry(method,predecessors,previous.getKey(),bci);
                locks.add(saved.slot());
            }
            if (code==Opcode.MONITOREXIT) {
                var previous=method.ops.lowerEntry(bci);
                if (locks.isEmpty() || previous==null || !(previous.getValue().instruction() instanceof LoadInstruction load)
                    || load.typeKind()!=TypeKind.REFERENCE || load.slot()!=locks.getLast())
                    throw problem("MALFORMED","Unbalanced monitor exit",method.location(bci));
                sequentialEntry(method,predecessors,previous.getKey(),bci);
                locks.removeLast();
            }
            if (instruction instanceof ReturnInstruction && !locks.isEmpty())
                throw problem("MALFORMED","Return with unmatched monitor entry",method.location(bci));
            boolean canThrow=instruction instanceof FieldInstruction || instruction instanceof InvokeInstruction
                || instruction instanceof ArrayLoadInstruction || instruction instanceof ArrayStoreInstruction || instruction instanceof TypeCheckInstruction
                || instruction instanceof NewObjectInstruction || instruction instanceof NewPrimitiveArrayInstruction || instruction instanceof NewReferenceArrayInstruction
                || instruction instanceof NewMultiArrayInstruction || code==Opcode.ARRAYLENGTH
                || code==Opcode.IDIV || code==Opcode.IREM || code==Opcode.MONITORENTER || code==Opcode.ATHROW;
            if (canThrow) {
                for(String type:throwableTypes) {
                    int handler=method.handler(bci,type,hierarchy);
                    if(handler>=0)merge(method,states,queue,handler,before);
                    else if(!before.isEmpty())throw problem("MALFORMED","Missing exceptional monitor cleanup",method.location(bci));
                }
            }
            for(int successor : successors(method,op)) merge(method,states,queue,successor,locks);
        }
    }

    /**
     * Requires unbroken producer/consumer wiring; lexical adjacency alone does not establish value identity.
     * @param method decoded method
     * @param predecessors normal control-flow predecessors
     * @param previous required producer position
     * @param next consumer position
     * @throws BytecodeLoadException when a branch can bypass the required producer
     */
    private static void sequentialEntry(BytecodeMethod method,Map<Integer,Set<Integer>> predecessors,int previous,int next) {
        if(!predecessors.getOrDefault(next,Set.of()).equals(Set.of(previous)))
            throw problem("UNSUPPORTED","Branch bypasses compiler monitor wiring",method.location(next));
    }

    /**
     * Adds one normal/exceptional CFG obligation and rejects incompatible lock histories.
     * @param method validated method
     * @param states per-position lock stacks
     * @param queue pending positions
     * @param bci target position
     * @param locks expected lock-slot stack
     */
    private static void merge(BytecodeMethod method,Map<Integer,List<Integer>> states,ArrayDeque<Integer> queue,
                              int bci,List<Integer> locks) {
        if (!method.ops.containsKey(bci)) throw problem("MALFORMED","Invalid monitor continuation",method.location(bci));
        var old=states.putIfAbsent(bci,List.copyOf(locks));
        if(old==null) queue.addLast(bci);
        else if(!old.equals(locks)) throw problem("MALFORMED","Inconsistent monitor stacks",method.location(bci));
    }

    /**
     * Enumerates normal control-flow targets independently of runtime branch values.
     * @param method decoded method
     * @param op current instruction
     * @return normal successors
     */
    private static List<Integer> successors(BytecodeMethod method,BytecodeMethod.Op op) {
        Instruction instruction=op.instruction();
        if (instruction instanceof ReturnInstruction || instruction.opcode()==Opcode.ATHROW) return List.of();
        if (instruction instanceof BranchInstruction branch) {
            int target=method.target(branch.target());
            return instruction.opcode()==Opcode.GOTO || instruction.opcode()==Opcode.GOTO_W ? List.of(target) : List.of(target,op.next());
        }
        if(instruction instanceof LookupSwitchInstruction select) {
            var targets=new ArrayList<Integer>(); targets.add(method.target(select.defaultTarget()));
            select.cases().forEach(c->targets.add(method.target(c.target()))); return targets;
        }
        if(instruction instanceof TableSwitchInstruction select) {
            var targets=new ArrayList<Integer>(); targets.add(method.target(select.defaultTarget()));
            select.cases().forEach(c->targets.add(method.target(c.target()))); return targets;
        }
        return List.of(op.next());
    }
}
