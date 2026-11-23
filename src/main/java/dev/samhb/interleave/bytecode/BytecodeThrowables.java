/** Owned Throwable payloads and resource-accounted VM fault materialization. */
package dev.samhb.interleave.bytecode;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Keeps modeled exceptions entirely in the interpreted heap. */
final class BytecodeThrowables {
    /** Original interpreted throw location and stack, charged within the object header. */
    static final BytecodeProgram.FieldId ORIGIN=slot("origin");
    /** Exact trusted validation messages, interned only when suppression is reachable. */
    static final String SELF_MESSAGE="Self-suppression not permitted", NULL_MESSAGE="Cannot suppress a null exception.";
    /** Shared empty suppression result; constants do not consume application allocation sequences. */
    static final Ref EMPTY=new Ref(8,0);
    /** Payload for VM faults with an exact supported message and cause.
     * @param message modeled message identity
     * @param cause modeled cause identity
     * @param causeInitialized whether the cause was explicitly supplied */
    record Payload(Value message,Ref cause,boolean causeInitialized) {}
    /** Prevents construction of this payload utility. */
    private BytecodeThrowables() {}

    /** Counts the object header and modeled Throwable payload before application fields.
     * Shared by fixture setup, actor allocation and implicit fault materialization.
     * @param hierarchy frozen closed type relation
     * @param type actual allocated class
     * @return one ordinary header or the six-cell Throwable base */
    static int baseCells(BytecodeHierarchy hierarchy,String type){return hierarchy.assignable(type,"java/lang/Throwable") ? 6 : 1;}

    /** Names an interpreter-owned payload slot, separate from application declarations.
     * @param name payload member
     * @return stable internal field identity */
    private static BytecodeProgram.FieldId slot(String name){return new BytecodeProgram.FieldId("@Throwable",name,"internal");}

    /** Frozen exact intrinsic call selected under its receiver monitor.
     * @param method trusted declaration
     * @param receiver target object identity, possibly null before fault delivery
     * @param arguments ordered target arguments
     * @param cost full invoke and intrinsic work
     * @param suppressionCount frozen receiver payload count, zero for unrelated operations
     * @param monitor required receiver acquisition, or null
     * @param result completed target return value, null for void */
    record Call(BytecodeMethod.Id method,Ref receiver,List<Value> arguments,int cost,int suppressionCount,Ref monitor,Value result) {
        /** Freezes argument transport without retaining mutable interpreter containers. */
        Call {arguments=List.copyOf(arguments);}
    }

    /** Resolves an exact current runtime intrinsic without committing fuel or effects.
     * @param program frozen dispatch hierarchy
     * @param state committed heap
     * @param frame selected invocation frame
     * @param op actual next operation
     * @return pure call plan, or null for interpreted code */
    static Call plan(BytecodeProgram program,BytecodeState state,BytecodeFrame frame,BytecodeMethod.Op op) {
        if(!(op.instruction() instanceof InvokeInstruction invoke) || invoke.owner().asInternalName().equals("java/lang/Object"))return null;
        String owner=invoke.owner().asInternalName();
        var id=program.hierarchy.method(owner,invoke.name().stringValue(),invoke.type().stringValue());
        int count=invoke.typeSymbol().parameterCount();
        Ref receiver=(invoke.opcode()==Opcode.INVOKESTATIC) ? null : (Ref)frame.stack.get(frame.stack.size()-count-1);
        if(receiver==null)return null;
        if(invoke.opcode()==Opcode.INVOKESPECIAL)id=program.hierarchy.special(frame.method.id.owner(),owner,id);
        else if((program.hierarchy.flags(id)&ClassFile.ACC_PRIVATE)==0 && !receiver.equals(NULL)) {
            String actual=state.heap.get(receiver).type;
            if(!program.hierarchy.assignable(actual,owner))return null;
            id=program.hierarchy.dispatch(actual,id);
        }
        if(!BytecodePlatform.intrinsic(id))return null;
        var arguments=new ArrayList<Value>(frame.stack.subList(frame.stack.size()-count,frame.stack.size()));
        boolean constructor=id.name().equals("<init>");
        boolean writable=!id.descriptor().equals("(Ljava/lang/String;Ljava/lang/Throwable;ZZ)V") || ((Int)arguments.get(3)).value()!=0;
        boolean nullNpeMessage=id.owner().equals("java/lang/NullPointerException") && id.name().equals("getMessage")
            && !receiver.equals(NULL) && NULL.equals(state.heap.get(receiver).fields.get(slot("message")));
        boolean locks=constructor ? writable : (program.hierarchy.flags(id)&ClassFile.ACC_SYNCHRONIZED)!=0 || nullNpeMessage;
        int suppressionCount=!receiver.equals(NULL) && Set.of("addSuppressed","getSuppressed").contains(id.name()) ? suppressionCount(state.heap.get(receiver)) : 0;
        int cost=id.name().equals("getSuppressed") ? 1+Math.max(1,suppressionCount) : 2;
        return new Call(id,receiver,arguments,cost,suppressionCount,locks && !receiver.equals(NULL) ? receiver : null,null);
    }

    /** Completed intrinsic work and its actual target-reference effects.
     * @param call frozen continuation after the body
     * @param referencesChanged whether target reference edges or identities committed */
    record Body(Call call,boolean referencesChanged) {}

    /** Runs a preflighted body, assigning only owned interpreter values.
     * @param program immutable selected limits and constants
     * @param state selected owned heap
     * @param execution selected actor/setup
     * @param owner executing namespace
     * @param call frozen trusted invocation
     * @return completed continuation and reference effects, or the held call after a cutoff
     * @throws IllegalStateException for an unimplemented admitted descriptor */
    static Body body(BytecodeProgram program,BytecodeState state,Execution execution,int owner,Call call) {
        var object=OpcodeExecutor.object(state,call.receiver());Value result=null;boolean changed=false;
        if(call.method().name().equals("<init>")) {
            var args=call.arguments();boolean cause=args.size()>=2;
            object.fields.put(slot("message"),args.isEmpty() ? NULL : args.getFirst());
            object.fields.put(slot("cause"),cause ? args.get(1) : NULL);
            object.fields.put(slot("causeInitialized"),new Int(cause ? 1 : 0));
            object.fields.put(slot("suppressionEnabled"),new Int(args.size()==4 && ((Int)args.get(2)).value()==0 ? 0 : 1));
            object.fields.put(slot("writableStackTrace"),new Int(args.size()==4 && ((Int)args.get(3)).value()==0 ? 0 : 1));
            if(object.type.equals(call.method().owner()))object.initialized=true;changed=true;
        } else if(call.method().name().equals("getMessage")) {
            result=object.fields.get(slot("message"));
            if(result==Unavailable.MESSAGE)throw new IllegalStateException("Unproved implicit VM message inspection");
        } else if(call.method().name().equals("getCause"))result=object.fields.get(slot("cause"));
        else if(call.method().name().equals("addSuppressed")) {
            Ref exception=(Ref)call.arguments().getFirst();
            if(exception.equals(call.receiver()))throw validation("java/lang/IllegalArgumentException",program.strings.get(SELF_MESSAGE),exception,true);
            if(exception.equals(NULL))throw validation("java/lang/NullPointerException",program.strings.get(NULL_MESSAGE),NULL,false);
            if(((Int)object.fields.get(slot("suppressionEnabled"))).value()!=0) {
                int allocator=call.receiver().owner();var charged=state.executions.get(allocator);
                int limit=allocator==SETUP ? program.limits.setupHeapCells() : program.limits.actorHeapCells();
                if(charged.cells==limit) {
                    OpcodeExecutor.cutoff(execution,execution.frames.getLast(),allocator,"HEAP_CELLS",limit,1,0);return new Body(call,false);
                }
                object.fields.put(slot("suppressed"+call.suppressionCount()),exception);charged.cells++;changed=true;
            }
        } else if(call.method().name().equals("getSuppressed")) {
            var entries=suppressed(object);int count=call.suppressionCount();
            if(count==0)result=EMPTY;
            else {
                var frame=execution.frames.getLast();
                if(count>program.limits.maxArrayLength()) {
                    OpcodeExecutor.cutoff(execution,frame,owner,"ARRAY_LENGTH",program.limits.maxArrayLength(),count,program.limits.maxArrayLength());return new Body(call,false);
                }
                if(!OpcodeExecutor.reserve(program,execution,frame,owner,1+count))return new Body(call,false);
                var reference=new Ref(owner,execution.nextSequence++);var array=new ObjectValue("[Ljava/lang/Throwable;");
                array.shared=owner==SETUP;array.initialized=true;array.elements=new ArrayList<>(entries);
                state.heap.put(reference,array);execution.allocations++;execution.cells+=1+count;result=reference;changed=true;
            }
        } else throw new IllegalStateException("Unimplemented admitted Throwable operation "+call.method());
        return new Body(new Call(call.method(),call.receiver(),call.arguments(),call.cost(),call.suppressionCount(),call.monitor(),result),changed);
    }

    /** Identifies actual owned suppression cells without relying on a contiguous-key loop guard.
     * @param field stored field identity
     * @return whether this is an interpreter-owned suppressed reference */
    private static boolean suppressionSlot(BytecodeProgram.FieldId field){return field.owner().equals("@Throwable") && field.name().startsWith("suppressed");}

    /** Counts stored references without allocating or sorting an entry list during pure preflight.
     * @param object owned Throwable
     * @return bounded payload count */
    private static int suppressionCount(ObjectValue object){return (int)object.fields.keySet().stream().filter(BytecodeThrowables::suppressionSlot).count();}

    /** Reads numeric suppression order from the finite stored field set, preserving duplicates.
     * @param object owned Throwable
     * @return ordered duplicate-preserving references */
    private static List<Value> suppressed(ObjectValue object) {
        return object.fields.entrySet().stream().filter(entry->suppressionSlot(entry.getKey()))
            .sorted(Comparator.comparingInt(entry->Integer.parseInt(entry.getKey().name().substring(10))))
            .map(Map.Entry::getValue).toList();
    }

    /** Creates a supported validation fault without allocating before exception delivery.
     * @param type modeled failure type
     * @param message exact interned message
     * @param cause explicit cause identity
     * @param initialized whether cause was explicitly supplied
     * @return internal control transfer */
    private static OpcodeExecutor.Trap validation(String type,Ref message,Ref cause,boolean initialized) {
        var trap=new OpcodeExecutor.Trap(type,null);trap.payload=new Payload(message,cause,initialized);return trap;
    }

    /** Writes every frozen argument, cost, monitor and result into future-state identity.
     * @param out canonical sink
     * @param call pending intrinsic, nullable
     * @throws IOException for sink failure */
    static void encode(DataOutputStream out,Call call) throws IOException {
        out.writeBoolean(call!=null);if(call==null)return;
        BytecodeState.text(out,call.method().owner());BytecodeState.text(out,call.method().name());BytecodeState.text(out,call.method().descriptor());
        BytecodeState.value(out,call.receiver());out.writeInt(call.arguments().size());
        for(Value argument:call.arguments())BytecodeState.value(out,argument);
        out.writeInt(call.cost());out.writeInt(call.suppressionCount());BytecodeState.value(out,call.monitor());BytecodeState.value(out,call.result());
    }

    /** Creates a catchable VM exception only when a general handler needs its identity.
     * Insufficient resources end the prefix at the original throwing instruction, before allocation.
     * @param program selected execution limits
     * @param state owned successor heap
     * @param execution faulting actor or setup
     * @param owner permanent allocator namespace
     * @return whether materialization committed */
    static boolean materialize(BytecodeProgram program,BytecodeState state,Execution execution,int owner) {
        var frame=execution.frames.getLast();
        int cells=baseCells(program.hierarchy,execution.fault.type());
        if(!OpcodeExecutor.reserve(program,execution,frame,owner,cells)) {
            var cutoff=execution.cutoff;execution.eventLocation=execution.fault.location();
            execution.cutoff=new Cutoff(cutoff.reason(),execution.eventLocation,cutoff.limit(),cutoff.required(),cutoff.remaining(),owner);
            return false;
        }
        var reference=new Ref(owner,execution.nextSequence++);var object=new ObjectValue(execution.fault.type());
        object.shared=owner==SETUP;object.initialized=true;
        var payload=execution.faultPayload;
        object.fields.put(slot("message"),payload==null ? Unavailable.MESSAGE : payload.message());object.fields.put(slot("cause"),payload==null ? NULL : payload.cause());
        object.fields.put(slot("causeInitialized"),new Int(payload!=null && payload.causeInitialized() ? 1 : 0));object.fields.put(slot("suppressionEnabled"),new Int(1));
        object.fields.put(slot("writableStackTrace"),new Int(1));object.fields.put(ORIGIN,new Thrown(execution.fault));
        state.heap.put(reference,object);execution.allocations++;execution.cells+=cells;execution.throwable=reference;return true;
    }
}
