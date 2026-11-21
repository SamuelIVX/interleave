/** Shared actor/check/deadlock observations for exact search and strict replay, without source callbacks. */
package dev.samhb.interleave.bytecode;

import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Defines one observation boundary over committed owned state; observers use separate scratch frames. */
final class BytecodeObservations {
    /** Prevents construction of this stateless observation module. */
    private BytecodeObservations() {}

    /** One boundary's terminal meaning and separate observer work.
     * @param outcome terminal observation, or null for a runnable prefix
     * @param invocations attempted check invocations
     * @param instructions actual observer opcode work */
    record Evaluation(BytecodeExplorer.Outcome outcome,long invocations,long instructions) {}

    /** Applies the existing terminal-before-check, ALWAYS and normal-only FINAL order.
     * @param program validated immutable model
     * @param state committed owned state
     * @return terminal/prefix meaning and actual observer work */
    static Evaluation evaluate(BytecodeProgram program,BytecodeState state) {
        for(int actor=0;actor<program.actors.size();actor++) {
            var execution=state.executions.get(actor);
            if(execution.phase==Phase.FAILED || execution.phase==Phase.CUTOFF)return new Evaluation(outcome(state,
                execution.phase==Phase.FAILED ? BytecodeExplorer.Kind.UNCAUGHT_EXCEPTION : BytecodeExplorer.Kind.CUTOFF,
                actor,execution.fault,execution.cutoff,null,List.of(),null),0,0);
        }
        var always=BytecodeChecks.evaluate(program,state,true);
        if(always.failure()!=null)return new Evaluation(failed(state,always.failure()),always.invocations(),always.instructions());
        if(java.util.stream.IntStream.range(0,program.actors.size()).allMatch(actor->state.executions.get(actor).phase==Phase.TERMINATED)) {
            var last=BytecodeChecks.evaluate(program,state,false);
            return new Evaluation(last.failure()==null ? outcome(state,BytecodeExplorer.Kind.COMPLETED,-1,null,null,null,List.of(),null)
                : failed(state,last.failure()),always.invocations()+last.invocations(),always.instructions()+last.instructions());
        }
        var waits=new ArrayList<BytecodeExplorer.Wait>();boolean runnable=false;
        for(int actor=0;actor<program.actors.size();actor++) {
            var probe=OpcodeExecutor.probe(program,state,actor);
            if(probe.availability()==OpcodeExecutor.Availability.READY)runnable=true;
            if(probe.availability()==OpcodeExecutor.Availability.BLOCKED) {
                var monitor=state.heap.get(probe.monitor());waits.add(new BytecodeExplorer.Wait(actor,monitor.monitorOwner,probe.monitor(),monitor.monitorDepth));
            }
        }
        return new Evaluation(runnable ? null : outcome(state,BytecodeExplorer.Kind.DEADLOCK,-1,null,null,null,waits,null),always.invocations(),always.instructions());
    }

    /** Converts a private observer disposition without mistaking it for an actor fault.
     * @param state committed state
     * @param failure actual scratch observer failure
     * @return normalized observer/external observation */
    private static BytecodeExplorer.Outcome failed(BytecodeState state,BytecodeChecks.Failure failure) {
        var kind=switch(failure.kind()) {
            case FALSE->BytecodeExplorer.Kind.CHECK_FALSE;case ERROR->BytecodeExplorer.Kind.OBSERVER_ERROR;
            case LIMIT->BytecodeExplorer.Kind.OBSERVER_LIMIT;case INTERRUPTED->BytecodeExplorer.Kind.INTERRUPTED;
        };
        return outcome(state,kind,-1,null,null,kind==BytecodeExplorer.Kind.INTERRUPTED ? "CANCELLED" : null,List.of(),kind==BytecodeExplorer.Kind.INTERRUPTED ? null : failure);
    }

    /** Captures normalized shared observations for all terminal categories, including incomplete prefixes.
     * @param state actual committed state
     * @param kind category
     * @param actor fault/cutoff owner
     * @param fault actor fault
     * @param cutoff modeled omitted operation
     * @param interruption external cause
     * @param waits blocked monitor relationships
     * @param observer separate check failure
     * @return immutable normalized observation */
    static BytecodeExplorer.Outcome outcome(BytecodeState state,BytecodeExplorer.Kind kind,int actor,Fault fault,Cutoff cutoff,String interruption,List<BytecodeExplorer.Wait> waits,BytecodeChecks.Failure observer) {
        var shared=new TreeMap<BytecodeExplorer.Cell,Value>();state.statics.forEach((field,value)->shared.put(new BytecodeExplorer.Cell(NULL,field),value));
        state.heap.forEach((reference,object)->{
            if(!object.shared)return;
            object.fields.forEach((field,value)->shared.put(new BytecodeExplorer.Cell(reference,field),value));
            if(object.elements!=null)for(int i=0;i<object.elements.size();i++)
                shared.put(new BytecodeExplorer.Cell(reference,new BytecodeProgram.FieldId(object.type,"["+i+"]",object.type.substring(1))),object.elements.get(i));
        });
        return new BytecodeExplorer.Outcome(kind,actor,fault,cutoff,interruption,waits,shared,observer);
    }
}
