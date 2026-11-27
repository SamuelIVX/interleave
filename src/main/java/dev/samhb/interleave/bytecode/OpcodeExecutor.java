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
     * @param referencesChanged whether new target reference edges need sharedness closure
     * @param kind transition/event classification
     */
    record Result(BytecodeState state,Kind kind,boolean referencesChanged) {
        /** Constructs a result without committed reference graph changes.
         * @param state isolated successor
         * @param kind event classification */
        Result(BytecodeState state,Kind kind){this(state,kind,false);}
    }
    /** Pure availability classification for one actor's next opcode or implicit event. */
    enum Availability {
        /** A scheduler selection can commit an opcode or pending event. */ READY,
        /** A sufficiently fueled monitor acquisition is disabled. */ BLOCKED,
        /** The actor or its entire prefix has ended. */ DONE
    }

    /** Describes a choice without consuming fuel or modifying target state.
     * @param availability enabled, disabled or ended
     * @param location actual next opcode or causing event position
     * @param opcode next executed opcode, null for an implicit event or fuel cutoff
     * @param monitor acquisition identity, otherwise null
     * @param observable whether batching must preserve this scheduling point
     * @param exhausted whether selection will omit the next opcode for insufficient fuel
     */
    record Probe(Availability availability,BytecodeLocation location,Opcode opcode,Ref monitor,
                 boolean observable,boolean exhausted) {}

    /** Classifies the next operation, preserving fuel-before-blocking priority.
     * @param program validated artifact
     * @param state committed state, never modified
     * @param owner selected execution owner
     * @return pure next-operation classification
     */
    static Probe probe(BytecodeProgram program,BytecodeState state,int owner) {
        var execution=state.executions.get(owner);
        if(state.executions.values().stream().anyMatch(e->e.phase==Phase.FAILED || e.phase==Phase.CUTOFF)
            || execution.phase==Phase.TERMINATED)
            return new Probe(Availability.DONE,execution.eventLocation,null,null,true,false);
        if(execution.phase!=Phase.RUNNING) {
            var location=execution.frames.isEmpty() ? execution.eventLocation
                : execution.frames.getLast().method.location(execution.frames.getLast().bci);
            var opcode=execution.phase==Phase.INTRINSIC_BODY ? execution.frames.getLast().method.ops.get(execution.frames.getLast().bci).instruction().opcode() : null;
            return new Probe(Availability.READY,location,opcode,null,true,false);
        }
        var frame=execution.frames.getLast();var op=frame.method.ops.get(frame.bci);var location=frame.method.location(frame.bci);
        if(work(program,state,frame,op)>limit(program,owner)-execution.instructions)
            return new Probe(Availability.READY,location,null,null,true,true);
        if(!frame.entered) {
            Ref monitor=(frame.method.flags & ACC_STATIC)!=0 ? classMonitor(frame.method.id.owner(),program) : (Ref)frame.locals.get(0);
            return new Probe(busy(state,monitor,owner) ? Availability.BLOCKED : Availability.READY,location,null,monitor,true,false);
        }
        var intrinsic=BytecodeThrowables.plan(program,state,frame,op);
        if(intrinsic!=null && intrinsic.monitor()!=null)return new Probe(busy(state,intrinsic.monitor(),owner) ? Availability.BLOCKED : Availability.READY,
            location,null,intrinsic.monitor(),true,false);
        var code=op.instruction().opcode();Ref monitor=null;
        if(code==Opcode.MONITORENTER) {
            monitor=(Ref)frame.stack.getLast();
            if(!monitor.equals(NULL) && busy(state,monitor,owner))
                return new Probe(Availability.BLOCKED,location,code,monitor,true,false);
        }
        boolean observable=intrinsic!=null || op.instruction() instanceof FieldInstruction || op.instruction() instanceof ReturnInstruction
            || op.instruction() instanceof NewObjectInstruction || op.instruction() instanceof NewPrimitiveArrayInstruction
            || op.instruction() instanceof NewReferenceArrayInstruction || op.instruction() instanceof NewMultiArrayInstruction
            || op.instruction() instanceof ArrayLoadInstruction || op.instruction() instanceof ArrayStoreInstruction
            || code==Opcode.MONITORENTER || code==Opcode.MONITOREXIT || code==Opcode.ATHROW || losesReferenceRoot(frame,op);
        return new Probe(Availability.READY,location,code,monitor,observable,false);
    }

    /** Preserves scheduling around local root loss, which another actor's sharing traversal can observe.
     * Loads/duplication preserve the actor's root set; overwrites, discards and reference comparisons
     * may remove its last alias. These boundaries retain exact sharing-history outcomes.
     * @param frame current locals and operand roots
     * @param op selected verified operation
     * @return whether local transport must remain separately schedulable */
    private static boolean losesReferenceRoot(BytecodeFrame frame,BytecodeMethod.Op op) {
        if(op.instruction() instanceof StoreInstruction store) {
            if(frame.locals.get(store.slot()) instanceof Ref old && !old.equals(NULL))return true;
            if((store.typeKind()==TypeKind.LONG || store.typeKind()==TypeKind.DOUBLE) && frame.locals.get(store.slot()+1) instanceof Ref old && !old.equals(NULL))return true;
        }
        return switch(op.instruction().opcode()) {
            case IFNULL,IFNONNULL,IF_ACMPEQ,IF_ACMPNE,ARRAYLENGTH,INSTANCEOF,CHECKCAST -> true;
            case INVOKESPECIAL -> ((InvokeInstruction)op.instruction()).owner().asInternalName().equals("java/lang/Object");
            case POP -> frame.stack.getLast() instanceof Ref reference && !reference.equals(NULL);
            case POP2 -> frame.stack.subList(frame.stack.size()-(BytecodeFrame.width(frame.stack.getLast())==2 ? 1 : 2),frame.stack.size()).stream().anyMatch(value->value instanceof Ref reference && !reference.equals(NULL));
            default -> false;
        };
    }

    /** Computes one admitted opcode's complete intrinsic work before execution.
     * @param program frozen hierarchy
     * @param state committed heap
     * @param frame selected frame
     * @param op decoded instruction
     * @return opcode plus fixed intrinsic fuel
     */
    private static int work(BytecodeProgram program,BytecodeState state,BytecodeFrame frame,BytecodeMethod.Op op) {
        var intrinsic=BytecodeThrowables.plan(program,state,frame,op);if(intrinsic!=null)return intrinsic.cost();
        return op.instruction() instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/lang/Object") ? 2 : 1;
    }

    /** Selects the independent setup, observer or actor fuel allowance.
     * @param program immutable limits
     * @param owner execution namespace
     * @return maximum instruction/intrinsic work
     */
    private static int limit(BytecodeProgram program,int owner) {
        return owner==SETUP ? program.limits.setupInstructions()
            : owner==OBSERVER ? program.limits.checkInstructions() : program.limits.actorInstructions();
    }

    /**
     * Internal control transfer for a modeled VM fault, not a host throwable from target execution.
     */
    static final class Trap extends RuntimeException {
        /**
         * Existing cleanup exception token, or null for a new fault.
         */
        final Fault original;
        /** Existing target exception identity, absent for a fresh VM fault. */
        Ref reference;
        /** Exact supported validation message/cause, otherwise unavailable. */
        BytecodeThrowables.Payload payload;
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
        var choice=probe(program,parent,owner);
        BytecodeState state=parent.copy(); Execution execution=state.executions.get(owner);
        if(choice.availability()==Availability.BLOCKED)return new Result(state,Kind.BLOCKED);
        switch(execution.phase) {
            case TERMINATED, FAILED, CUTOFF -> { return new Result(state,Kind.TERMINAL); }
            case TERMINATION_PENDING -> { execution.phase=Phase.TERMINATED; return new Result(state,Kind.TERMINAL); }
            case FAILURE_PENDING -> { execution.phase=Phase.FAILED; return new Result(state,Kind.TERMINAL); }
            case CUTOFF_PENDING -> { execution.phase=Phase.CUTOFF; return new Result(state,Kind.TERMINAL); }
            case RETURNING -> { finishReturn(state,execution,owner); return new Result(state,Kind.ADVANCED); }
            case INTRINSIC_BODY -> {
                var call=execution.intrinsic;execution.instructions+=call.cost();
                boolean changed=false;
                try {var body=BytecodeThrowables.body(program,state,execution,owner,call);execution.intrinsic=body.call();changed=body.referencesChanged();}
                catch(Trap fault){recordFault(state,execution,execution.frames.getLast(),fault);}
                if(execution.phase!=Phase.CUTOFF_PENDING)execution.phase=Phase.INTRINSIC_EXIT;
                return new Result(state,execution.phase==Phase.CUTOFF_PENDING ? Kind.PENDING : Kind.ADVANCED,changed);
            }
            case INTRINSIC_EXIT -> {
                var call=execution.intrinsic;release(state,call.monitor(),owner);execution.intrinsic=null;
                if(execution.fault!=null){execution.phase=Phase.EXCEPTION;return new Result(state,Kind.PENDING);}
                var caller=execution.frames.getLast();caller.bci=caller.method.ops.get(caller.bci).next();
                if(call.result()!=null)caller.push(call.result());execution.phase=Phase.RUNNING;
                return new Result(state,Kind.ADVANCED);
            }
            case EXCEPTION -> { unwind(program,state,execution,owner); return new Result(state,Kind.ADVANCED); }
            case RUNNING -> { /* Execute only after pure budget/enabledness decisions below. */ }
        }
        BytecodeFrame frame=execution.frames.getLast(); BytecodeMethod.Op op=frame.method.ops.get(frame.bci);
        int required=work(program,state,frame,op), limit=limit(program,owner);
        if(choice.exhausted()) {
            cutoff(execution,frame,owner,"INSTRUCTION_FUEL",limit,required,limit-execution.instructions);
            return new Result(state,Kind.PENDING);
        }
        if(!frame.entered) {
            if(!acquire(state,choice.monitor(),owner))return new Result(state,Kind.BLOCKED);
            frame.methodMonitor=choice.monitor();frame.entered=true;return new Result(state,Kind.ADVANCED);
        }
        var intrinsic=BytecodeThrowables.plan(program,state,frame,op);
        if(intrinsic!=null && intrinsic.monitor()!=null) {
            if(!acquire(state,intrinsic.monitor(),owner))return new Result(state,Kind.BLOCKED);
            for(int i=0;i<intrinsic.arguments().size()+1;i++)frame.pop();
            execution.intrinsic=intrinsic;execution.phase=Phase.INTRINSIC_BODY;return new Result(state,Kind.ADVANCED);
        }
        execution.instructions+=required;
        try {
            if(execute(program,state,execution,frame,op,owner)) frame.bci=op.next();
        } catch(Trap fault) {
            recordFault(state,execution,frame,fault);
        }
        return new Result(state,execution.phase==Phase.RUNNING ? Kind.ADVANCED : Kind.PENDING,
            intrinsic!=null && intrinsic.method().name().equals("<init>") && execution.phase==Phase.RUNNING);
    }

    /** Captures original modeled failure identity without host target execution.
     * @param state owned heap for first-throw metadata
     * @param execution selected owner
     * @param frame actual throwing invocation/instruction
     * @param fault internal modeled control transfer */
    private static void recordFault(BytecodeState state,Execution execution,BytecodeFrame frame,Trap fault) {
        execution.fault=fault.original!=null ? fault.original : new Fault(fault.getMessage(),frame.method.location(frame.bci),
            execution.frames.reversed().stream().map(f->f.method.location(f.bci)).toList());
        execution.throwable=fault.reference;execution.faultPayload=fault.payload;
        if(fault.reference!=null)state.heap.get(fault.reference).fields.putIfAbsent(BytecodeThrowables.ORIGIN,new Thrown(execution.fault));
        execution.eventLocation=frame.method.location(frame.bci);execution.phase=Phase.EXCEPTION;
    }

    /**
     * Implements admitted opcode semantics on logical primitive/reference values.
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
            frame.push(code==Opcode.ACONST_NULL ? NULL : constant.constantValue() instanceof String text ? program.strings.get(text) : frame.method.constants.get(op.bci())); return true;
        }
        if(instruction instanceof LoadInstruction load) { frame.push(frame.locals.get(load.slot())); return true; }
        if(instruction instanceof StoreInstruction store) { frame.store(store.slot(),frame.pop()); return true; }
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
            if(execution.returned!=null)execution.returned=BytecodePrimitives.narrow(frame.method.id.descriptor().substring(frame.method.id.descriptor().indexOf(')')+1),execution.returned);
            execution.eventLocation=frame.method.location(frame.bci); execution.phase=Phase.RETURNING; return false;
        }
        if(instruction instanceof FieldInstruction field) {
            var id=program.hierarchy.field(new BytecodeProgram.FieldId(field.owner().asInternalName(),field.name().stringValue(),field.type().stringValue()));
            switch(code) {
                case GETSTATIC -> frame.push(state.statics.get(id));
                case PUTSTATIC -> state.statics.put(id,narrow(id,frame.pop()));
                case GETFIELD -> frame.push(object(state,(Ref)frame.pop()).fields.get(id));
                case PUTFIELD -> { Value value=narrow(id,frame.pop()); object(state,(Ref)frame.pop()).fields.put(id,value); }
                default -> throw new IllegalStateException("Unvalidated field opcode");
            }
            return true;
        }
        if(BytecodeArrays.handles(instruction))return BytecodeArrays.execute(program,state,execution,frame,instruction,owner);
        if(instruction instanceof NewObjectInstruction allocation) {
            String type=allocation.className().asInternalName();
            var fields=type.equals("java/lang/Object") ? List.<BytecodeProgram.Field>of() : program.hierarchy.instanceFields(type);
            int cells=BytecodeThrowables.baseCells(program.hierarchy,type)+fields.size();
            if(!reserve(program,execution,frame,owner,cells))return false;
            var reference=new Ref(owner,execution.nextSequence++);var object=new ObjectValue(type);
            object.shared=owner==SETUP;fields.forEach(field->object.fields.put(field.id(),field.initial()));
            state.heap.put(reference,object);execution.allocations++;execution.cells+=cells;frame.push(reference);return true;
        }
        if(instruction instanceof InvokeInstruction call) {
            int count=call.typeSymbol().parameterCount();boolean isStatic=code==Opcode.INVOKESTATIC;
            Ref receiver=isStatic ? null : (Ref)frame.stack.get(frame.stack.size()-count-1);
            if(receiver!=null) {
                var target=object(state,receiver); // Dynamic validity precedes depth exhaustion.
                if(!program.hierarchy.assignable(target.type,call.owner().asInternalName()))throw new Trap("java/lang/IncompatibleClassChangeError",null);
            }
            var intrinsic=BytecodeThrowables.plan(program,state,frame,op);
            if(intrinsic!=null) {
                var completed=BytecodeThrowables.body(program,state,execution,owner,intrinsic).call();
                for(int i=0;i<count+1;i++)frame.pop();if(completed.result()!=null)frame.push(completed.result());return true;
            }
            if(call.owner().asInternalName().equals("java/lang/Object")) {
                frame.pop();if(object(state,receiver).type.equals("java/lang/Object"))object(state,receiver).initialized=true;return true;
            }
            if(execution.frames.size()==program.limits.maxCallDepth()) {
                cutoff(execution,frame,owner,"CALL_DEPTH",program.limits.maxCallDepth(),1,0);return false;
            }
            String type=call.owner().asInternalName(),name=call.name().stringValue(),descriptor=call.type().stringValue();
            var id=program.hierarchy.method(type,name,descriptor);
            if(!isStatic && code!=Opcode.INVOKESPECIAL && (program.hierarchy.flags(id) & ACC_PRIVATE)==0)
                id=program.hierarchy.dispatch(object(state,receiver).type,id);
            if(code==Opcode.INVOKESPECIAL)id=program.hierarchy.special(frame.method.id.owner(),type,id);
            var arguments=new ArrayList<Value>();
            for(int i=0;i<count;i++)arguments.add(frame.pop());
            if(!isStatic)arguments.add(frame.pop());Collections.reverse(arguments);
            execution.frames.add(new BytecodeFrame(Objects.requireNonNull(program.methods.get(id),"Unvalidated dispatch target"),arguments));return false;
        }
        if(BytecodePrimitives.OPCODES.contains(code)){BytecodePrimitives.execute(frame,code);return true;}
        switch(code) {
            case NOP -> { }
            case POP,POP2,DUP,DUP_X1,DUP_X2,DUP2,DUP2_X1,DUP2_X2,SWAP -> BytecodeStack.apply(frame.stack,code,BytecodeFrame::width);
            case MONITORENTER -> { if(!acquire(state,(Ref)frame.pop(),owner)) throw new IllegalStateException("Enabledness changed"); }
            case MONITOREXIT -> release(state,(Ref)frame.pop(),owner);
            case ATHROW -> {
                Value value=frame.pop();
                if(value instanceof Thrown thrown) {
                    var trap=new Trap(thrown.fault().type(),thrown.fault());trap.payload=execution.faultPayload;throw trap;
                }
                Ref reference=(Ref)value;var target=object(state,reference);
                var origin=target.fields.get(BytecodeThrowables.ORIGIN);
                var trap=new Trap(target.type,origin instanceof Thrown thrown ? thrown.fault() : null);
                trap.reference=reference;throw trap;
            }
            default -> throw new IllegalStateException("Unvalidated opcode "+code);
        }
        return true;
    }

    /** Checks permanent per-allocator counts/cells before creating an identity.
     * @param program selected limits
     * @param execution charged owner
     * @param frame allocating instruction
     * @param owner setup or actor namespace
     * @param cells entire new object charge
     * @return whether creation may commit */
    static boolean reserve(BytecodeProgram program,Execution execution,BytecodeFrame frame,int owner,int cells) {
        int allocations=owner==SETUP ? program.limits.setupAllocations() : program.limits.actorAllocations();
        int heap=owner==SETUP ? program.limits.setupHeapCells() : program.limits.actorHeapCells();
        if(execution.allocations>=allocations){cutoff(execution,frame,owner,"ALLOCATIONS",allocations,1,allocations-execution.allocations);return false;}
        if(cells>heap-execution.cells){cutoff(execution,frame,owner,"HEAP_CELLS",heap,cells,heap-execution.cells);return false;}
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
        if(frame.method.id.name().equals("<init>")) {
            Ref reference=frame.constructorReceiver;var receiver=state.heap.get(reference);
            if(receiver.type.equals(frame.method.id.owner()) && execution.frames.stream().noneMatch(f->f.method.id.name().equals("<init>")
                && reference.equals(f.constructorReceiver)))receiver.initialized=true;
        }
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
     * @param program validated hierarchy and limits
     * @param state owned state
     * @param execution faulting execution
     * @param owner actor/setup
     */
    private static void unwind(BytecodeProgram program,BytecodeState state,Execution execution,int owner) {
        if(execution.frames.isEmpty()) { execution.phase=Phase.FAILURE_PENDING; return; }
        var frame=execution.frames.getLast(); int handler=frame.method.handler(frame.bci,execution.fault.type(),program.hierarchy);
        if(handler>=0) {
            Value delivered;
            if(execution.throwable==null && frame.method.metadataCleanup(handler))delivered=new Thrown(execution.fault);
            else {
                if(execution.throwable==null && !BytecodeThrowables.materialize(program,state,execution,owner))return;
                delivered=execution.throwable;
            }
            frame.stack.clear();frame.push(delivered);frame.bci=handler;execution.phase=Phase.RUNNING;
            if(!(delivered instanceof Thrown)){execution.fault=null;execution.faultPayload=null;}
            execution.throwable=null;execution.eventLocation=null;
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
    static ObjectValue object(BytecodeState state,Ref reference) {
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
     * @param owner charged resource namespace
     * @param reason resource
     * @param limit configured allowance
     * @param required attempted work
     * @param remaining unspent allowance
     */
    static void cutoff(Execution execution,BytecodeFrame frame,int owner,String reason,int limit,int required,int remaining) {
        execution.eventLocation=frame.method.location(frame.bci);
        execution.cutoff=new Cutoff(reason,execution.eventLocation,limit,required,remaining,owner); execution.phase=Phase.CUTOFF_PENDING;
    }
    /**
     * Applies JVM narrow integral field storage while retaining wide/reference values.
     * @param field declared JVM field
     * @param value popped value
     * @return narrowed or unchanged value
     */
    private static Value narrow(BytecodeProgram.FieldId field,Value value) {
        return BytecodePrimitives.narrow(field.descriptor(),value);
    }
    /**
     * Reads the raw value of a verifier-proven int operand.
     * @param value verified int
     * @return raw JVM int
     */
    private static int integer(Value value) { return ((Int)value).value(); }
}
