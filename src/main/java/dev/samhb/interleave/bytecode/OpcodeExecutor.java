/**
 * One deterministic opcode executor and zero-work event protocol, isolated from search strategies.
 */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;
import static java.lang.classfile.ClassFile.*;

/**
 * Interprets admitted instructions on copied state; never invokes application code on the host JVM.
 */
final class OpcodeExecutor {
    /**
     * Prevents construction of the stateless instruction executor.
     */
    private OpcodeExecutor() {}
    /**
     * Result classification, separate from future public search verdicts.
     */
    enum Kind {
        /**
         * Selected instruction or implicit event committed an isolated state.
         */ ADVANCED,
        /**
         * A return, fault or cutoff yielded before terminal delivery.
         */ PENDING,
        /**
         * No work or state change occurred because a monitor was unavailable.
         */ BLOCKED,
        /**
         * This selection delivered or observed a terminal event.
         */ TERMINAL
    }
    /**
     * Returns an isolated state and the selected instruction or event outcome.
     * @param state isolated execution snapshot
     * @param kind transition/event classification
     */
    record Result(BytecodeState state,Kind kind) {}
    /**
     * Internal control transfer for a modeled VM fault, not a host throwable from target execution.
     */
    private static final class Trap extends RuntimeException {
        /**
         * Existing cleanup exception token, or null for a new fault.
         */
        final Fault original;
        /**
         * Transfers control for a modeled VM failure without exposing a host stack.
         * @param type modeled exception type
         * @param original original cleanup fault
         */
        Trap(String type,Fault original) { super(type,null,false,false); this.original=original; }
    }

    /**
     * Executes one selected opcode or pending event on a copy of its parent.
     * @param program immutable validated metadata
     * @param parent committed state, never modified
     * @param owner setup or fixed actor
     * @return isolated instruction/event result; blocked attempts preserve every parent value
     */
    static Result step(BytecodeProgram program,BytecodeState parent,int owner) {
        Phase selected=parent.executions.get(owner).phase;
        if (selected!=Phase.FAILED && selected!=Phase.CUTOFF && parent.executions.values().stream()
            .anyMatch(e->e.phase==Phase.FAILED || e.phase==Phase.CUTOFF))
            throw new IllegalStateException("Execution prefix already ended");
        BytecodeState state=parent.copy(); Execution execution=state.executions.get(owner);
        switch(execution.phase) {
            case TERMINATED, FAILED, CUTOFF -> { return new Result(state,Kind.TERMINAL); }
            case TERMINATION_PENDING -> { execution.phase=Phase.TERMINATED; return new Result(state,Kind.TERMINAL); }
            case FAILURE_PENDING -> { execution.phase=Phase.FAILED; return new Result(state,Kind.TERMINAL); }
            case CUTOFF_PENDING -> { execution.phase=Phase.CUTOFF; return new Result(state,Kind.TERMINAL); }
            case RETURNING -> { finishReturn(state,execution,owner); return new Result(state,Kind.ADVANCED); }
            case EXCEPTION -> { unwind(state,execution,owner); return new Result(state,Kind.ADVANCED); }
            case RUNNING -> { /* Execute only after pure budget/enabledness decisions below. */ }
        }
        BytecodeFrame frame=execution.frames.getLast(); BytecodeMethod.Op op=frame.method.ops.get(frame.bci);
        int required=op.instruction() instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/lang/Object") ? 2 : 1;
        int limit=owner==SETUP ? program.limits.setupInstructions() : program.limits.actorInstructions();
        if(required>limit-execution.instructions) {
            cutoff(execution,frame,"INSTRUCTION_FUEL",limit,required,limit-execution.instructions);
            return new Result(state,Kind.PENDING);
        }
        if(!frame.entered) {
            Ref monitor=(frame.method.flags & ACC_STATIC)!=0 ? classMonitor(frame.method.id.owner(),program) : (Ref)frame.locals.get(0);
            if(!acquire(state,monitor,owner)) return new Result(state,Kind.BLOCKED);
            frame.methodMonitor=monitor; frame.entered=true; return new Result(state,Kind.ADVANCED);
        }
        if(op.instruction().opcode()==Opcode.MONITORENTER) {
            var monitor=(Ref)frame.stack.getLast();
            if(!monitor.equals(NULL) && busy(state,monitor,owner)) return new Result(state,Kind.BLOCKED);
        }
        execution.instructions+=required;
        try {
            if(execute(program,state,execution,frame,op,owner)) frame.bci=op.next();
        } catch(Trap fault) {
            execution.fault=fault.original!=null ? fault.original : new Fault(fault.getMessage(),frame.method.location(frame.bci),
                execution.frames.reversed().stream().map(f->f.method.location(f.bci)).toList());
            execution.eventLocation=frame.method.location(frame.bci); execution.phase=Phase.EXCEPTION;
        }
        return new Result(state,execution.phase==Phase.RUNNING ? Kind.ADVANCED : Kind.PENDING);
    }

    /**
     * Implements admitted category-one opcode semantics.
     * @param program metadata
     * @param state owned scratch
     * @param execution selected resource owner
     * @param frame selected frame
     * @param op instruction and location
     * @param owner selected actor/setup
     * @return whether to advance to the following byte index
     */
    private static boolean execute(BytecodeProgram program,BytecodeState state,Execution execution,
                                   BytecodeFrame frame,BytecodeMethod.Op op,int owner) {
        Instruction instruction=op.instruction(); Opcode code=instruction.opcode();
        if(instruction instanceof ConstantInstruction constant) {
            frame.push(code==Opcode.ACONST_NULL ? NULL : new Int((Integer)constant.constantValue())); return true;
        }
        if(instruction instanceof LoadInstruction load) { frame.push(frame.locals.get(load.slot())); return true; }
        if(instruction instanceof StoreInstruction store) { frame.locals.put(store.slot(),frame.pop()); return true; }
        if(instruction instanceof IncrementInstruction increment) {
            frame.locals.put(increment.slot(),new Int(((Int)frame.locals.get(increment.slot())).value()+increment.constant())); return true;
        }
        if(instruction instanceof BranchInstruction branch) {
            frame.bci=branch(frame,code) ? frame.method.target(branch.target()) : op.next(); return false;
        }
        if(instruction instanceof LookupSwitchInstruction select) {
            int key=integer(frame.pop()); frame.bci=select.cases().stream().filter(c->c.caseValue()==key)
                .findFirst().map(c->frame.method.target(c.target())).orElseGet(()->frame.method.target(select.defaultTarget())); return false;
        }
        if(instruction instanceof TableSwitchInstruction select) {
            int key=integer(frame.pop()); frame.bci=select.cases().stream().filter(c->c.caseValue()==key)
                .findFirst().map(c->frame.method.target(c.target())).orElseGet(()->frame.method.target(select.defaultTarget())); return false;
        }
        if(instruction instanceof ReturnInstruction returned) {
            execution.returned=returned.typeKind()==TypeKind.VOID ? null : frame.pop();
            if(frame.method.id.descriptor().endsWith(")Z")) execution.returned=new Int(integer(execution.returned)&1);
            execution.eventLocation=frame.method.location(frame.bci); execution.phase=Phase.RETURNING; return false;
        }
        if(instruction instanceof FieldInstruction field) {
            var id=new BytecodeProgram.FieldId(field.owner().asInternalName(),field.name().stringValue(),field.type().stringValue());
            switch(code) {
                case GETSTATIC -> frame.push(state.statics.get(id));
                case PUTSTATIC -> state.statics.put(id,narrow(id,frame.pop()));
                case GETFIELD -> frame.push(object(state,(Ref)frame.pop()).fields.get(id));
                case PUTFIELD -> { Value value=narrow(id,frame.pop()); object(state,(Ref)frame.pop()).fields.put(id,value); }
                default -> throw new IllegalStateException("Unvalidated field opcode");
            }
            return true;
        }
        if(instruction instanceof NewObjectInstruction allocation) {
            int allocationLimit=program.limits.setupAllocations(), cellLimit=program.limits.setupHeapCells();
            if(execution.allocations==allocationLimit) { cutoff(execution,frame,"ALLOCATIONS",allocationLimit,1,0); return false; }
            if(execution.cells==cellLimit) { cutoff(execution,frame,"HEAP_CELLS",cellLimit,1,0); return false; }
            var reference=new Ref(owner,execution.nextSequence++);
            state.heap.put(reference,new ObjectValue(allocation.className().asInternalName()));
            execution.allocations++; execution.cells++; frame.push(reference); return true;
        }
        if(instruction instanceof InvokeInstruction call) {
            if(call.owner().asInternalName().equals("java/lang/Object")) {
                object(state,(Ref)frame.pop()).initialized=true; return true;
            }
            if(execution.frames.size()==program.limits.maxCallDepth()) {
                cutoff(execution,frame,"CALL_DEPTH",program.limits.maxCallDepth(),1,0); return false;
            }
            int count=call.typeSymbol().parameterCount(); var arguments=new ArrayList<Value>();
            for(int i=0; i<count; i++) arguments.add(frame.pop()); Collections.reverse(arguments);
            var id=new BytecodeMethod.Id(call.owner().asInternalName(),call.name().stringValue(),call.type().stringValue());
            execution.frames.add(new BytecodeFrame(program.methods.get(id),arguments)); return false;
        }
        switch(code) {
            case NOP -> { }
            case POP -> frame.pop();
            case POP2 -> { frame.pop(); frame.pop(); }
            case DUP -> { Value a=frame.pop(); push(frame,a,a); }
            case DUP_X1 -> { Value a=frame.pop(),b=frame.pop(); push(frame,a,b,a); }
            case DUP_X2 -> { Value a=frame.pop(),b=frame.pop(),c=frame.pop(); push(frame,a,c,b,a); }
            case DUP2 -> { Value a=frame.pop(),b=frame.pop(); push(frame,b,a,b,a); }
            case DUP2_X1 -> { Value a=frame.pop(),b=frame.pop(),c=frame.pop(); push(frame,b,a,c,b,a); }
            case DUP2_X2 -> { Value a=frame.pop(),b=frame.pop(),c=frame.pop(),d=frame.pop(); push(frame,b,a,d,c,b,a); }
            case SWAP -> { Value a=frame.pop(),b=frame.pop(); push(frame,a,b); }
            case INEG -> frame.push(new Int(-integer(frame.pop())));
            case I2B -> frame.push(new Int((byte)integer(frame.pop())));
            case I2C -> frame.push(new Int((char)integer(frame.pop())));
            case I2S -> frame.push(new Int((short)integer(frame.pop())));
            case MONITORENTER -> { if(!acquire(state,(Ref)frame.pop(),owner)) throw new IllegalStateException("Enabledness changed"); }
            case MONITOREXIT -> release(state,(Ref)frame.pop(),owner);
            case ATHROW -> { var thrown=(Thrown)frame.pop(); throw new Trap(thrown.fault().type(),thrown.fault()); }
            default -> {
                int right=integer(frame.pop()),left=integer(frame.pop());
                if((code==Opcode.IDIV || code==Opcode.IREM) && right==0) throw new Trap("java/lang/ArithmeticException",null);
                int result=switch(code) {
                    case IADD -> left+right; case ISUB -> left-right; case IMUL -> left*right;
                    case IDIV -> left/right; case IREM -> left%right; case ISHL -> left<<right;
                    case ISHR -> left>>right; case IUSHR -> left>>>right; case IAND -> left&right;
                    case IOR -> left|right; case IXOR -> left^right;
                    default -> throw new IllegalStateException("Unvalidated opcode "+code);
                };
                frame.push(new Int(result));
            }
        }
        return true;
    }

    /**
     * Evaluates one admitted signed, identity or null branch condition.
     * @param frame selected frame
     * @param code conditional opcode
     * @return branch decision
     */
    private static boolean branch(BytecodeFrame frame,Opcode code) {
        if(code==Opcode.GOTO || code==Opcode.GOTO_W) return true;
        if(code==Opcode.IFNULL || code==Opcode.IFNONNULL) {
            boolean isNull=frame.pop().equals(NULL); return code==Opcode.IFNULL ? isNull : !isNull;
        }
        if(code==Opcode.IF_ACMPEQ || code==Opcode.IF_ACMPNE) {
            boolean equal=frame.pop().equals(frame.pop()); return code==Opcode.IF_ACMPEQ ? equal : !equal;
        }
        int right=integer(frame.pop());
        int left=switch(code) {
            case IF_ICMPEQ,IF_ICMPNE,IF_ICMPLT,IF_ICMPGE,IF_ICMPGT,IF_ICMPLE -> integer(frame.pop());
            default -> 0;
        };
        // Unary comparisons have the operand on the left, unlike two-operand comparisons.
        boolean unary=switch(code) { case IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE -> true; default -> false; };
        if(unary) { left=right; right=0; }
        return switch(code) {
            case IFEQ,IF_ICMPEQ -> left==right; case IFNE,IF_ICMPNE -> left!=right;
            case IFLT,IF_ICMPLT -> left<right; case IFGE,IF_ICMPGE -> left>=right;
            case IFGT,IF_ICMPGT -> left>right; case IFLE,IF_ICMPLE -> left<=right;
            default -> throw new IllegalStateException("Unvalidated branch");
        };
    }

    /**
     * Completes a return in a separate zero-opcode event, preserving real return location.
     * @param state owned state
     * @param execution selected execution
     * @param owner actor/setup
     */
    private static void finishReturn(BytecodeState state,Execution execution,int owner) {
        var frame=execution.frames.removeLast();
        if(frame.methodMonitor!=null) release(state,frame.methodMonitor,owner);
        if(execution.frames.isEmpty()) execution.phase=Phase.TERMINATION_PENDING;
        else {
            var caller=execution.frames.getLast(); caller.bci=caller.method.ops.get(caller.bci).next();
            if(execution.returned!=null) caller.push(execution.returned);
            execution.returned=null; execution.phase=Phase.RUNNING;
            execution.eventLocation=null;
        }
    }

    /**
     * Executes one exception delivery/unwind event without charging imaginary opcodes.
     * @param state owned state
     * @param execution faulting execution
     * @param owner actor/setup
     */
    private static void unwind(BytecodeState state,Execution execution,int owner) {
        if(execution.frames.isEmpty()) { execution.phase=Phase.FAILURE_PENDING; return; }
        var frame=execution.frames.getLast(); int handler=frame.method.handler(frame.bci);
        if(handler>=0) {
            frame.stack.clear(); frame.push(new Thrown(execution.fault)); frame.bci=handler; execution.phase=Phase.RUNNING;
        } else {
            if(frame.methodMonitor!=null) release(state,frame.methodMonitor,owner);
            execution.frames.removeLast();
        }
    }

    /**
     * Resolves an object reference or raises the modeled null-access fault.
     * @param state heap
     * @param reference receiver
     * @return modeled object or modeled null fault
     */
    private static ObjectValue object(BytecodeState state,Ref reference) {
        if(reference.equals(NULL)) throw new Trap("java/lang/NullPointerException",null);
        return Objects.requireNonNull(state.heap.get(reference),"Unknown interpreted reference");
    }
    /**
     * Tests monitor enabledness without modifying state or resource counters.
     * @param state heap
     * @param monitor identity
     * @param owner selected actor
     * @return monitor held by competitor
     */
    private static boolean busy(BytecodeState state,Ref monitor,int owner) {
        var object=object(state,monitor); return object.monitorDepth>0 && object.monitorOwner!=owner;
    }
    /**
     * Acquires or reenters an available modeled monitor.
     * @param state heap
     * @param monitor identity
     * @param owner selected actor
     * @return acquired/reentered
     */
    private static boolean acquire(BytecodeState state,Ref monitor,int owner) {
        if(busy(state,monitor,owner)) return false;
        var object=object(state,monitor); object.monitorOwner=owner; object.monitorDepth++; return true;
    }
    /**
     * Releases one owned monitor depth or raises the modeled release fault.
     * @param state heap
     * @param monitor identity
     * @param owner selected actor
     */
    private static void release(BytecodeState state,Ref monitor,int owner) {
        var object=object(state,monitor);
        if(object.monitorOwner!=owner || object.monitorDepth==0) throw new Trap("java/lang/IllegalMonitorStateException",null);
        if(--object.monitorDepth==0) object.monitorOwner=-2;
    }
    /**
     * Prepares a prefix-ending resource event without executing the omitted operation.
     * @param execution selected resource owner
     * @param frame omitted instruction
     * @param reason resource
     * @param limit configured allowance
     * @param required attempted work
     * @param remaining unspent allowance
     */
    private static void cutoff(Execution execution,BytecodeFrame frame,String reason,int limit,int required,int remaining) {
        execution.eventLocation=frame.method.location(frame.bci);
        execution.cutoff=new Cutoff(reason,execution.eventLocation,limit,required,remaining); execution.phase=Phase.CUTOFF_PENDING;
    }
    /**
     * Applies JVM boolean field narrowing while retaining other values.
     * @param field declared JVM field
     * @param value popped value
     * @return boolean-narrowed or unchanged value
     */
    private static Value narrow(BytecodeProgram.FieldId field,Value value) {
        return field.descriptor().equals("Z") ? new Int(integer(value)&1) : value;
    }
    /**
     * Reads the raw value of a verifier-proven int operand.
     * @param value verified int
     * @return raw JVM int
     */
    private static int integer(Value value) { return ((Int)value).value(); }
    /**
     * Appends operands in bottom-to-top order.
     * @param frame operand stack
     * @param values values in bottom-to-top order
     */
    private static void push(BytecodeFrame frame,Value...values) { Collections.addAll(frame.stack,values); }
}
