/** Pure interpreted observers with private frames and per-invocation fuel, never scheduler actors. */
package dev.samhb.interleave.bytecode;

import java.util.List;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Runs only loader-certified checks, returning diagnostics without modifying committed target state. */
final class BytecodeChecks {
    /** Prevents construction of the stateless observer runner. */
    private BytecodeChecks() {}

    /** Observer-specific failure reasons, separate from actor violations. */
    enum Kind {
        /** A pure check returned false. */ FALSE,
        /** A pure operation raised a modeled fault. */ ERROR,
        /** Observer instruction or call-depth allowance was exhausted. */ LIMIT,
        /** Host cancellation interrupted the checker, not the modeled program. */ INTERRUPTED
    }

    /** One failed check with genuine method/BCI identity.
     * @param kind observer disposition
     * @param check declared check identity
     * @param location actual return, fault or omitted instruction; null for host cancellation
     * @param fault interpreted observer fault, if any
     * @param cutoff observer resource diagnostic, if any
     */
    record Failure(Kind kind,BytecodeMethod.Id check,BytecodeLocation location,Fault fault,Cutoff cutoff) {}

    /** Actual observer work for one ordered timing group.
     * @param failure first failing check, otherwise null
     * @param invocations attempted check invocations
     * @param instructions executed observer instruction/intrinsic work
     */
    record Evaluation(Failure failure,long invocations,long instructions) {}

    /** Executes ordered ALWAYS or FINAL declarations synchronously on private scratch state.
     * @param program immutable validated artifact
     * @param parent committed model state, never modified
     * @param always true for ALWAYS, false for FINAL
     * @return first failure and actual separate observer work
     */
    static Evaluation evaluate(BytecodeProgram program,BytecodeState parent,boolean always) {
        long invocations=0,instructions=0;
        for(var check:program.checks) {
            if(check.always()!=always)continue;
            invocations++;
            var scratch=new BytecodeState();
            for(var entry:parent.heap.entrySet())scratch.heap.put(entry.getKey(),entry.getValue().copy());
            scratch.statics.putAll(parent.statics);
            scratch.start(OBSERVER,program.methods.get(check.method()),List.of(FIXTURE));
            while(true) {
                var execution=scratch.executions.get(OBSERVER);
                if(Thread.currentThread().isInterrupted())
                    return new Evaluation(new Failure(Kind.INTERRUPTED,check.method(),null,null,null),invocations,instructions+execution.instructions);
                if(execution.phase==Phase.EXCEPTION)
                    return new Evaluation(new Failure(Kind.ERROR,check.method(),execution.fault.location(),execution.fault,null),invocations,instructions+execution.instructions);
                if(execution.phase==Phase.CUTOFF_PENDING)
                    return new Evaluation(new Failure(Kind.LIMIT,check.method(),execution.cutoff.location(),null,execution.cutoff),invocations,instructions+execution.instructions);
                if(execution.phase==Phase.RETURNING && execution.frames.size()==1) {
                    instructions+=execution.instructions;
                    if(((Int)execution.returned).value()==0)
                        return new Evaluation(new Failure(Kind.FALSE,check.method(),execution.eventLocation,null,null),invocations,instructions);
                    break;
                }
                scratch=OpcodeExecutor.step(program,scratch,OBSERVER).state();
            }
        }
        return new Evaluation(null,invocations,instructions);
    }
}
