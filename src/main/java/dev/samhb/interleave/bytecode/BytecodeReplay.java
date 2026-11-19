/** Strict replay of artifact-bound bytecode records through the same opcode/observation semantics. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.TraceRecord;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.CorruptedTraceException;
import dev.samhb.interleave.search.TraceOutcome;
import java.util.*;

/** Authenticates concrete execution; external stop causes remain separate from replay observations. */
final class BytecodeReplay {
    /** Prevents construction of this stateless strict replayer. */
    private BytecodeReplay() {}

    /** Checks frozen selection identity before any target operation, then validates every boundary.
     * @param program immutable selected program
     * @param record original captured record
     * @return immutable record with actual replay observations and snapshots
     * @throws CorruptedTraceException for mismatched identity, evidence, state or terminal meaning
     * @throws IllegalScheduleException for a foreign/disabled actor choice
     * @throws java.util.concurrent.CancellationException when the caller interrupts replay */
    static TraceRecord replay(BytecodeProgram program,TraceRecord record) {
        Objects.requireNonNull(program);Objects.requireNonNull(record);cancelled();
        var data=record.bytecodeEvidence();
        if(data==null || record.evidence()!=null || !BytecodeRuntime.identity(program).equals(data.identity())
            || !program.artifactDigest().equals(record.programHash()))throw corrupt("Bytecode artifact/model/declaration/profile/limits/mode mismatch");
        if(data.transitions().size()!=record.threadIds().size() || record.outcomes().size()!=record.threadIds().size()
            || data.snapshots().size()!=record.threadIds().size()+1 || record.states().size()!=data.snapshots().size())throw corrupt("Misaligned bytecode record");
        var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.BATCHED);var state=program.initialState();
        var snapshots=new ArrayList<BytecodeEvidence.Snapshot>();var transitions=new ArrayList<BytecodeEvidence.Transition>();
        verifySnapshot(record,data,state,0,snapshots);
        var observation=observe(program,state);
        for(int index=0;index<record.threadIds().size();index++) {
            cancelled();if(observation!=null)throw corrupt("Trailing bytecode choices after an earlier terminal observation");
            int actor=record.threadIds().get(index);
            if(!scheduler.choices(state).contains(actor))throw new IllegalScheduleException("Bytecode actor is not enabled: "+actor);
            var step=scheduler.advance(state,actor);var selection=new BytecodeExplorer.Selection(actor,step.executed());
            var actual=BytecodeRuntime.transition(program,selection);
            if(!actual.equals(data.transitions().get(index)) || BytecodeRuntime.stepOutcome(selection)!=record.outcomes().get(index))throw corrupt("Bytecode operation/outcome mismatch at transition "+index);
            transitions.add(actual);state=step.state();verifySnapshot(record,data,state,index+1,snapshots);observation=observe(program,state);
        }
        var actualTerminal=BytecodeRuntime.terminal(observation);var original=data.terminal();
        if(original.kind().equals("INTERRUPTED")) {
            if(record.outcome()!=TraceOutcome.INCOMPLETE)throw corrupt("External bytecode interruption was upgraded");
        } else if(!original.equals(actualTerminal) || record.outcome()!=BytecodeRuntime.traceOutcome(actualTerminal))throw corrupt("Bytecode terminal/check/monitor/cutoff mismatch");
        if(data.replayObservation()!=null && !data.replayObservation().equals(actualTerminal))throw corrupt("Changed bytecode replay observation");
        var replayed=new BytecodeEvidence(1,BytecodeRuntime.identity(program),transitions,snapshots,original,actualTerminal);
        return new TraceRecord(record.threadIds(),record.outcomes(),record.outcome(),program.artifactDigest(),
            snapshots.stream().map(BytecodeSnapshots::display).toList(),null,replayed);
    }

    /** Verifies full-state authentication and all supplied display/monitor/frame data.
     * @param record recorded legacy-compatible display snapshots
     * @param data typed bytecode evidence
     * @param state actual committed state
     * @param index boundary index
     * @param actual destination for owned replay snapshots
     * @throws CorruptedTraceException for changed state or display values */
    private static void verifySnapshot(TraceRecord record,BytecodeEvidence data,BytecodeState state,int index,List<BytecodeEvidence.Snapshot> actual) {
        var snapshot=BytecodeSnapshots.capture(state,state.canonicalBytes());
        if(!snapshot.equals(data.snapshots().get(index)) || !BytecodeSnapshots.display(snapshot).equals(record.states().get(index)))throw corrupt("Bytecode state/monitor/frame snapshot mismatch at boundary "+index);
        actual.add(snapshot);
    }

    /** Reuses exact-search check timing and delivered-terminal ordering.
     * @param program frozen model
     * @param state committed state
     * @return actual terminal, or null for runnable prefix
     * @throws java.util.concurrent.CancellationException for host interruption during an observer */
    private static BytecodeExplorer.Outcome observe(BytecodeProgram program,BytecodeState state) {
        var outcome=BytecodeObservations.evaluate(program,state).outcome();
        if(outcome!=null && outcome.kind()==BytecodeExplorer.Kind.INTERRUPTED)throw new java.util.concurrent.CancellationException("Bytecode replay cancelled");
        return outcome;
    }

    /** Preserves caller interrupt state instead of treating cancellation as a modeled fault. */
    private static void cancelled(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Bytecode replay cancelled");}

    /** Creates a precise replay rejection without retaining target throwable objects.
     * @param message failed authentication rule
     * @return replay mismatch exception */
    private static CorruptedTraceException corrupt(String message){return new CorruptedTraceException(message);}
}
