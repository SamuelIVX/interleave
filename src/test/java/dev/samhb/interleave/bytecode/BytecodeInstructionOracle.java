/** Test-only opcode-choice enumeration, independent of production scheduling, encoding and observers. */
package dev.samhb.interleave.bytecode;

import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** A small bounded reference for ASCII-named compiler fixtures, using sorted full structural text keys. */
final class BytecodeInstructionOracle {
    /** Prevents construction of this stateless test reference. */
    private BytecodeInstructionOracle() {}

    /** Normalized terminal observations without local snapshots or scheduling statistics.
     * @param kind terminal category
     * @param actor terminal actor, otherwise -1
     * @param fault original fault
     * @param cutoff original omitted operation
     * @param observer independently evaluated check identity/failure
     * @param waits sorted monitor relationships
     * @param fields sorted shared observations */
    record Observation(String kind,int actor,Fault fault,Cutoff cutoff,String observer,List<String> waits,Map<String,Value> fields) {}

    /** Enumerates primitive steps without consulting probe, scheduler, explorer or canonicalBytes.
     * @param program validated bounded fixture
     * @return independent observable outcomes */
    static Set<Observation> enumerate(BytecodeProgram program) {
        var pending=new ArrayDeque<BytecodeState>();pending.push(program.initialState());
        var seen=new TreeSet<String>();var outcomes=new HashSet<Observation>();
        while(!pending.isEmpty()) {
            var state=pending.pop();if(!seen.add(key(state)))continue;
            if(seen.size()>200000)throw new AssertionError("Independent fixture exceeded its test ceiling");
            boolean terminal=false;
            for(int actor=0;actor<program.actors.size();actor++) {
                var execution=state.executions.get(actor);
                if(execution.phase==Phase.FAILED || execution.phase==Phase.CUTOFF) {
                    outcomes.add(observation(state,execution.phase==Phase.FAILED ? "UNCAUGHT_EXCEPTION" : "CUTOFF",actor,
                        execution.fault,execution.cutoff,null,List.of()));terminal=true;break;
                }
            }
            if(terminal)continue;
            var failure=checks(program,state,true);
            if(failure!=null) {outcomes.add(failure);continue;}
            if(state.executions.entrySet().stream().filter(e->e.getKey()>=0).allMatch(e->e.getValue().phase==Phase.TERMINATED)) {
                failure=checks(program,state,false);
                outcomes.add(failure==null ? observation(state,"COMPLETED",-1,null,null,null,List.of()) : failure);continue;
            }
            var waits=new ArrayList<String>();int choices=0;
            for(int actor=0;actor<program.actors.size();actor++) {
                if(state.executions.get(actor).phase==Phase.TERMINATED)continue;
                boolean disabled=disabled(program,state,actor);var next=OpcodeExecutor.step(program,state,actor);
                if(disabled!=(next.kind()==OpcodeExecutor.Kind.BLOCKED))throw new AssertionError("Primitive enabledness disagrees with independent monitor/fuel rule");
                if(disabled) {
                    // Infer the waited-on object from the failed acquisition's operands/implicit frame.
                    var frame=state.frame(actor);Ref monitor;
                    if(!frame.entered)monitor=(frame.method.flags & java.lang.classfile.ClassFile.ACC_STATIC)!=0
                        ? BytecodeState.classMonitor(frame.method.id.owner(),program) : (Ref)frame.locals.get(0);
                    else monitor=(Ref)frame.stack.getLast();
                    var object=state.heap.get(monitor);waits.add(waitKey(actor,object.monitorOwner,monitor,object.monitorDepth));
                } else {
                    var before=state.executions.get(actor);
                    if(before.phase==Phase.RUNNING && state.frame(actor).entered) {
                        var instruction=state.frame(actor).method.ops.get(state.frame(actor).bci).instruction();
                        boolean changed=instruction instanceof java.lang.classfile.instruction.NewObjectInstruction
                            || instruction instanceof java.lang.classfile.instruction.NewPrimitiveArrayInstruction
                            || instruction instanceof java.lang.classfile.instruction.NewReferenceArrayInstruction
                            || instruction instanceof java.lang.classfile.instruction.NewMultiArrayInstruction
                            || instruction.opcode()==java.lang.classfile.Opcode.AASTORE;
                        if(instruction instanceof java.lang.classfile.instruction.FieldInstruction field
                            && (field.opcode()==java.lang.classfile.Opcode.PUTFIELD || field.opcode()==java.lang.classfile.Opcode.PUTSTATIC))
                            changed=field.type().stringValue().startsWith("L") || field.type().stringValue().startsWith("[");
                        if(changed && (next.state().executions.get(actor).phase==Phase.RUNNING || next.state().heap.size()!=state.heap.size()))promote(next.state());
                    }
                    pending.push(next.state());choices++;
                }
            }
            if(choices==0)outcomes.add(observation(state,"DEADLOCK",-1,null,null,null,List.copyOf(waits)));
        }
        return Set.copyOf(outcomes);
    }

    /** Derives blocked enabledness from fuel, decoded operation and monitor state, never probe.
     * @param program fixture limits
     * @param state committed snapshot
     * @param actor original actor
     * @return whether the next sufficiently fueled acquisition is blocked */
    private static boolean disabled(BytecodeProgram program,BytecodeState state,int actor) {
        var execution=state.executions.get(actor);if(execution.phase!=Phase.RUNNING)return false;
        var frame=execution.frames.getLast();var instruction=frame.method.ops.get(frame.bci).instruction();
        int work=instruction instanceof java.lang.classfile.instruction.InvokeInstruction call
            && call.owner().asInternalName().equals("java/lang/Object") ? 2 : 1;
        if(execution.instructions+work>program.limits.actorInstructions())return false;
        Ref monitor;
        if(!frame.entered)monitor=(frame.method.flags & java.lang.classfile.ClassFile.ACC_STATIC)!=0
            ? BytecodeState.classMonitor(frame.method.id.owner(),program) : (Ref)frame.locals.get(0);
        else if(instruction.opcode()==java.lang.classfile.Opcode.MONITORENTER)monitor=(Ref)frame.stack.getLast();
        else return false;
        if(monitor.equals(NULL))return false;
        var object=state.heap.get(monitor);return object.monitorOwner!=-2 && object.monitorOwner!=actor;
    }

    /** Evaluates checks through private primitive execution, with no production observer adapter.
     * @param program fixture
     * @param parent committed actor state
     * @param always selected timing
     * @return first failed observation, otherwise null */
    private static Observation checks(BytecodeProgram program,BytecodeState parent,boolean always) {
        for(var check:program.checks) {
            if(check.always()!=always)continue;
            var state=new BytecodeState();parent.heap.forEach((id,object)->state.heap.put(id,object.copy()));state.statics.putAll(parent.statics);
            state.start(OBSERVER,program.methods.get(check.method()),List.of(FIXTURE));
            // Keep the mutable holder out of lambdas; successors are distinct owned values.
            var scratch=state;
            for(int event=0;;event++) {
                if(event>100000)throw new AssertionError("Observer event protocol stalled");
                var execution=scratch.executions.get(OBSERVER);
                if(execution.fault!=null)return observation(parent,"OBSERVER_ERROR",-1,null,null,
                    checkKey("ERROR",check.method(),execution.fault.location(),execution.fault,null),List.of());
                if(execution.cutoff!=null)return observation(parent,"OBSERVER_LIMIT",-1,null,null,
                    checkKey("LIMIT",check.method(),execution.cutoff.location(),null,execution.cutoff),List.of());
                if(execution.phase==Phase.RETURNING && execution.frames.size()==1) {
                    if(((Int)execution.returned).value()==0)return observation(parent,"CHECK_FALSE",-1,null,null,
                        checkKey("FALSE",check.method(),execution.eventLocation,null,null),List.of());
                    break;
                }
                scratch=OpcodeExecutor.step(program,scratch,OBSERVER).state();
            }
        }
        return null;
    }

    /** Projects terminal shared state independently of the production outcome builder.
     * @param state terminal state
     * @param kind category
     * @param actor failing actor
     * @param fault fault
     * @param cutoff cutoff
     * @param observer failed observer
     * @param waits deadlock relationships
     * @return normalized independent observation */
    private static Observation observation(BytecodeState state,String kind,int actor,Fault fault,Cutoff cutoff,String observer,List<String> waits) {
        var fields=new TreeMap<String,Value>();
        for(var entry:state.statics.entrySet())fields.put(cellKey(NULL,entry.getKey()),entry.getValue());
        for(var entry:state.heap.entrySet())if(entry.getValue().shared)
            {
                for(var field:entry.getValue().fields.entrySet())fields.put(cellKey(entry.getKey(),field.getKey()),field.getValue());
                var array=entry.getValue().elements;
                if(array!=null)for(int i=0;i<array.size();i++)fields.put(cellKey(entry.getKey(),new BytecodeProgram.FieldId(entry.getValue().type,"["+i+"]",entry.getValue().type.substring(1))),array.get(i));
            }
        return new Observation(kind,actor,fault,cutoff,observer,List.copyOf(waits),Map.copyOf(fields));
    }

    /** Independent repeated reachability closure; no production sharing/probe/classifier is used.
     * @param state primitive successor */
    private static void promote(BytecodeState state) {
        var owners=new HashMap<Ref,Set<Integer>>();
        state.heap.forEach((ref,object)->{if(object.shared)owners.computeIfAbsent(ref,k->new HashSet<>()).add(SETUP);});
        java.util.function.BiConsumer<Value,Integer> mark=(value,owner)->{
            if(value instanceof Ref ref && !ref.equals(NULL))owners.computeIfAbsent(ref,k->new HashSet<>()).add(owner);
        };
        mark.accept(FIXTURE,SETUP);state.statics.values().forEach(value->mark.accept(value,SETUP));
        state.executions.forEach((actor,e)->{if(actor>=0){mark.accept(e.returned,actor);for(var f:e.frames){
            f.locals.values().forEach(v->mark.accept(v,actor));f.stack.forEach(v->mark.accept(v,actor));mark.accept(f.methodMonitor,actor);mark.accept(f.constructorReceiver,actor);
        }}});
        boolean changed;
        do {
            var before=new HashMap<Ref,Set<Integer>>();owners.forEach((ref,set)->before.put(ref,Set.copyOf(set)));
            before.forEach((ref,set)->{
                var object=state.heap.get(ref);if(object==null)return;
                for(int owner:set){object.fields.values().forEach(v->mark.accept(v,owner));if(object.elements!=null)object.elements.forEach(v->mark.accept(v,owner));}
            });
            if(before.equals(owners))state.heap.keySet().forEach(ref->{
                var set=owners.get(ref);
                if(set==null || set.size()>1 || set.contains(SETUP))mark.accept(ref,SETUP);
            });
            changed=!before.equals(owners);
        }while(changed);
        state.heap.forEach((ref,object)->{var set=owners.get(ref);if(set==null || set.size()>1 || set.contains(SETUP))object.shared=true;});
    }

    /** Independently writes every mutable state component with length-delimited fixture tokens.
     * Metadata/value records contain only bounded ASCII fixture identifiers and numeric values here.
     * @param state owned snapshot
     * @return full key for the independent sorted set */
    private static String key(BytecodeState state) {
        var key=new StringBuilder();tokens(key,"heap",state.heap.size());
        for(var entry:state.heap.entrySet()) {
            var object=entry.getValue();tokens(key,entry.getKey(),object.type,object.shared,object.initialized,object.monitorOwner,object.monitorDepth,object.fields.size());
            object.fields.forEach((field,value)->tokens(key,field,value));tokens(key,object.elements);
        }
        tokens(key,"statics",state.statics.size());state.statics.forEach((field,value)->tokens(key,field,value));
        tokens(key,"executions",state.executions.size());
        for(var entry:state.executions.entrySet()) {
            var execution=entry.getValue();tokens(key,entry.getKey(),execution.phase,execution.instructions,execution.allocations,execution.cells,
                execution.nextSequence,execution.fault,execution.cutoff,execution.returned,execution.eventLocation,execution.frames.size());
            for(var frame:execution.frames) {
                tokens(key,frame.method.id,frame.bci,frame.entered,frame.methodMonitor,frame.constructorReceiver,frame.locals.size());
                frame.locals.forEach((slot,value)->tokens(key,slot,value));tokens(key,frame.stack.size());frame.stack.forEach(value->tokens(key,value));
            }
        }
        return key.toString();
    }

    /** Appends unambiguous null/scalar/immutable-record tokens for controlled compiler fixtures.
     * @param sink key sink
     * @param values complete tokens */
    private static void tokens(StringBuilder sink,Object... values) {
        for(var value:values) {if(value==null)sink.append("N;");else {String text=value.toString();sink.append(text.length()).append(':').append(text);}}
    }

    /** Names a shared cell without using the production Cell record.
     * @param reference owning object or static sentinel
     * @param field declared slot
     * @return stable fixture cell token */
    static String cellKey(Ref reference,BytecodeProgram.FieldId field) {return reference+"/"+field;}

    /** Names a concrete wait without using production Wait.
     * @param actor waiter
     * @param owner owner
     * @param monitor identity
     * @param depth held depth
     * @return concrete relationship */
    static String waitKey(int actor,int owner,Ref monitor,int depth) {return actor+"/"+owner+"/"+monitor+"/"+depth;}

    /** Names a check's real failure identity.
     * @param kind observer reason
     * @param method declared check
     * @param location real position
     * @param fault original fault
     * @param cutoff omitted operation
     * @return bounded diagnostic token */
    static String checkKey(String kind,BytecodeMethod.Id method,BytecodeLocation location,Fault fault,Cutoff cutoff) {
        return kind+"/"+method+"/"+location+"/"+fault+"/"+cutoff;
    }
}
