/** Reconstructs a configuration from recorded choices and trusted step outcomes. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.TraceRecord;
import dev.samhb.interleave.core.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reconstructs a configuration from recorded choices and trusted step outcomes. */
public final class TraceReplayer {
    /** Creates trace replayer with its default configuration. */
    public TraceReplayer() {}

    /**
     * Replays thread choices while validating each step outcome against the trace.
     *
     * <p>The program supplies a fresh initial configuration. Each chosen step mutates that
     * configuration’s state and must produce the outcome recorded in the trace.
     * An empty trace returns the initial configuration. Replay does not reject a chosen step
     * solely because it is disabled, or validate the trace's terminal verdict. Exceptions from
     * model callbacks propagate to the caller.
     *
     * <pre>{@code
     * Configuration end = new TraceReplayer().replay(program, trace);
     * }</pre>
     * @param program program whose initial state and steps are replayed
     * @param trace recorded thread choices and outcomes
     * @return configuration after the recorded prefix has been executed
     * @throws CorruptedTraceException if an executed step outcome differs from the trace outcome
     * @throws IndexOutOfBoundsException if a recorded thread, counter, or outcome index is invalid
     */
    public Configuration replay(Program program, Trace trace) {
        Configuration config = program.initialConfiguration();

        for (int i = 0; i < trace.threadIds().size(); i++) {
            int threadId = trace.threadIds().get(i);
            StepOutcome outcome = trace.outcomes().get(i);

            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);

            StepOutcome actualOutcome = step.execute(config.state());
            if (actualOutcome != outcome) {
                throw new CorruptedTraceException(
                    "Trace outcome mismatch at step " + i + " (thread " + threadId + "): "
                    + "recorded " + outcome + " but execution produced " + actualOutcome
                );
            }
            config = config.successor(threadId, outcome, program.threads(), config.state());
        }

        return config;
    }

    /**
     * Replays thread choices and captures an immutable state snapshot at each configuration along the path.
     *
     * <p>Returns a list of length {@code trace.length() + 1}, starting with the initial configuration's
     * snapshot and ending with the terminal configuration's snapshot.
     *
     * @param program program whose initial state and steps are replayed
     * @param trace recorded thread choices and outcomes
     * @return ordered list of state snapshots for each configuration from initial to terminal
     * @throws CorruptedTraceException if an executed step outcome differs from the trace outcome
     * @throws IndexOutOfBoundsException if a recorded thread, counter, or outcome index is invalid
     */
    public List<StateSnapshot> captureSnapshots(Program program, Trace trace) {
        List<StateSnapshot> snapshots = new ArrayList<>();
        Configuration config = program.initialConfiguration();
        snapshots.add(StateSnapshotExtractor.extract(config.state()));

        for (int i = 0; i < trace.threadIds().size(); i++) {
            int threadId = trace.threadIds().get(i);
            StepOutcome outcome = trace.outcomes().get(i);

            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);

            StepOutcome actualOutcome = step.execute(config.state());
            if (actualOutcome != outcome) {
                throw new CorruptedTraceException(
                    "Trace outcome mismatch at step " + i + " (thread " + threadId + "): "
                    + "recorded " + outcome + " but execution produced " + actualOutcome
                );
            }
            config = config.successor(threadId, outcome, program.threads(), config.state());
            snapshots.add(StateSnapshotExtractor.extract(config.state()));
        }

        return Collections.unmodifiableList(snapshots);
    }

    /**
     * Replays a trace record against the given program and returns an enriched trace record
     * containing state snapshots.
     *
     * @param program program whose steps are replayed
     * @param record trace record to enrich
     * @return enriched trace record containing captured state snapshots
     */
    public TraceRecord enrich(Program program, TraceRecord record) {
        Trace trace = Trace.of(record.threadIds(), record.outcomes(), record.outcome());
        List<StateSnapshot> snapshots = captureSnapshots(program, trace);
        return record.withStates(snapshots);
    }
}

