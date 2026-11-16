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
                var next=OpcodeExecutor.step(program,state,actor);
                if(next.kind()==OpcodeExecutor.Kind.BLOCKED) {
                    // Infer the waited-on object from the failed acquisition's operands/implicit frame.
                    var frame=state.frame(actor);Ref monitor;
                    if(!frame.entered)monitor=(frame.method.flags & java.lang.classfile.ClassFile.ACC_STATIC)!=0
                        ? BytecodeState.classMonitor(frame.method.id.owner(),program) : (Ref)frame.locals.get(0);
                    else monitor=(Ref)frame.stack.getLast();
                    var object=state.heap.get(monitor);waits.add(waitKey(actor,object.monitorOwner,monitor,object.monitorDepth));
                } else {pending.push(next.state());choices++;}
            }
            if(choices==0)outcomes.add(observation(state,"DEADLOCK",-1,null,null,null,List.copyOf(waits)));
        }
        return Set.copyOf(outcomes);
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
            for(var field:entry.getValue().fields.entrySet())fields.put(cellKey(entry.getKey(),field.getKey()),field.getValue());
        return new Observation(kind,actor,fault,cutoff,observer,List.copyOf(waits),Map.copyOf(fields));
    }

    /** Independently writes every mutable state component with length-delimited fixture tokens.
     * Metadata/value records contain only bounded ASCII fixture identifiers and numeric values here.
     * @param state owned snapshot
     * @return full key for the independent sorted set */
    private static String key(BytecodeState state) {
        var key=new StringBuilder();tokens(key,"heap",state.heap.size());
        for(var entry:state.heap.entrySet()) {
            var object=entry.getValue();tokens(key,entry.getKey(),object.type,object.shared,object.initialized,object.monitorOwner,object.monitorDepth,object.fields.size());
            object.fields.forEach((field,value)->tokens(key,field,value));
        }
        tokens(key,"statics",state.statics.size());state.statics.forEach((field,value)->tokens(key,field,value));
        tokens(key,"executions",state.executions.size());
        for(var entry:state.executions.entrySet()) {
            var execution=entry.getValue();tokens(key,entry.getKey(),execution.phase,execution.instructions,execution.allocations,execution.cells,
                execution.nextSequence,execution.fault,execution.cutoff,execution.returned,execution.eventLocation,execution.frames.size());
            for(var frame:execution.frames) {
                tokens(key,frame.method.id,frame.bci,frame.entered,frame.methodMonitor,frame.locals.size());
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
