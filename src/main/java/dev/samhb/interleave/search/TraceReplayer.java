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
        if (program.isSource()) return sourceExecution(program, trace, null).configuration();
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
        if (program.isSource()) return sourceExecution(program, trace, null).states();
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
        Trace trace = new Trace(record.threadIds(), record.outcomes(), record.outcome(), record.evidence());
        List<StateSnapshot> snapshots = captureSnapshots(program, trace);
        return record.withStates(snapshots);
    }

    /**
     * Replays source evidence with terminal property validation when supplied.
     * @param program compiled model
     * @param trace recorded choices and terminal evidence
     * @param invariant timed property, or null for step-only validation
     * @return last committed configuration
     * @throws CorruptedTraceException if execution or evidence does not match
     */
    public Configuration replay(Program program, Trace trace, Invariant invariant) {
        return program.isSource() ? sourceExecution(program, trace, invariant).configuration() : replay(program, trace);
    }

    /** Validates recorded source choices, metadata, runtime outcomes and reason-specific cutoffs. */
    private ScheduleExecution sourceExecution(Program program, Trace trace, Invariant invariant) {
        ScheduleExecution result = new ExecutionDriver().runDetailed(program, new Schedule(trace.threadIds()), invariant);
        if (!result.threadIds().equals(trace.threadIds()) || !result.outcomes().equals(trace.outcomes())) {
            throw new CorruptedTraceException("Source execution outcomes do not match trace");
        }
        Trace actual = Trace.create(program, result.threadIds(), result.outcomes(), trace.outcome(), invariant);
        if (trace.evidence() != null && (!program.sourceDigest().equals(trace.evidence().sourceDigest())
                || !actual.evidence().executedSteps().equals(trace.evidence().executedSteps()))) {
            throw new CorruptedTraceException("Source digest or executed instruction metadata mismatch");
        }
        if (trace.failure() != null && (trace.failure().phase() == FailureEvidence.Phase.STEP || invariant != null)) {
            if (result.trace() == null || !trace.failure().equals(result.trace().failure())) {
                throw new CorruptedTraceException("Source failure classification mismatch");
            }
        }
        if (trace.cutoff() != null) validateCutoff(program, trace, result.configuration());
        if (invariant != null) {
            if (trace.outcome() == TraceOutcome.INCOMPLETE) {
                if (trace.cutoff() == null || result.kind() != ScheduleExecution.Kind.PREFIX) {
                    throw new CorruptedTraceException("Incomplete source trace lacks a nonterminal cutoff prefix");
                }
            } else if (result.trace() == null || result.trace().outcome() != trace.outcome()) {
                throw new CorruptedTraceException("Source terminal verdict does not reproduce");
            }
        }
        return result;
    }

    /** Checks loop cutoffs by scratch execution and scheduling cutoffs by replayed preemption cost. */
    private void validateCutoff(Program program, Trace trace, Configuration end) {
        CutoffEvidence cutoff = trace.cutoff();
        if (!end.enabledThreadIds().contains(cutoff.threadId())
                || end.programCounters().get(cutoff.threadId()) != cutoff.instructionIndex()) {
            throw new CorruptedTraceException("Cutoff does not identify an enabled choice");
        }
        if (cutoff.reason() == CutoffEvidence.Reason.LOOP_LIMIT) {
            if (!cutoff.equals(TransitionExecutor.execute(program, end, cutoff.threadId()).cutoff())) {
                throw new CorruptedTraceException("Loop cutoff does not reproduce");
            }
            return;
        }
        Configuration config = program.initialConfiguration(); int last = -1, cost = 0;
        for (int id : trace.threadIds()) {
            if (last != -1 && last != id && config.enabledThreadIds().contains(last)) cost++;
            if (cost > cutoff.limit()) throw new CorruptedTraceException("Cutoff prefix already exceeds its bound");
            config = TransitionExecutor.execute(program, config, id).successor(); last = id;
        }
        if (last != -1 && last != cutoff.threadId() && end.enabledThreadIds().contains(last)) cost++;
        if (cost <= cutoff.limit()) throw new CorruptedTraceException("Preemption cutoff does not exceed its bound");
    }
}

