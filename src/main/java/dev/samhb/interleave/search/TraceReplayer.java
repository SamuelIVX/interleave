/** Reconstructs a configuration from recorded choices and trusted step outcomes. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.TraceRecord;
import dev.samhb.interleave.core.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Reconstructs a configuration from recorded choices and trusted step outcomes. */
public final class TraceReplayer {
    /** Creates trace replayer with its default configuration. */
    public TraceReplayer() {}

    /**
     * Replays thread choices while validating each step outcome against the trace.
     *
     * <p>The program supplies a fresh initial configuration. Each chosen step mutates that
     * configuration’s state and must produce the outcome recorded in the trace.
     * An empty trace returns the initial configuration. Legacy replay does not reject a chosen
     * step solely because it is disabled, or validate its terminal verdict. Source replay requires
     * enabled choices and checks source identity, supplied step-failure evidence and cutoff witnesses.
     * Without an invariant it does not validate any terminal verdict, including step-failure verdicts,
     * or establish completion. Use the invariant overload to validate the terminal classification.
     * Exceptions from model callbacks propagate to the caller.
     *
     * <pre>{@code
     * Configuration end = new TraceReplayer().replay(program, trace);
     * }</pre>
     * @param program program whose initial state and steps are replayed
     * @param trace recorded thread choices and outcomes
     * @return configuration after the recorded prefix has been executed
     * @throws CorruptedTraceException if outcomes differ or source digest, executed-step metadata,
     *        step failure evidence, or cutoff witnesses do not reproduce
     * @throws IllegalScheduleException if a source choice is disabled or its thread is invalid
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
     * snapshot and ending with the terminal configuration's snapshot. Source snapshots use the
     * same enabled-choice and evidence validation as {@link #replay(Program, Trace)}.
     *
     * @param program program whose initial state and steps are replayed
     * @param trace recorded thread choices and outcomes
     * @return ordered list of state snapshots for each configuration from initial to terminal
     * @throws CorruptedTraceException if outcomes differ or source digest, executed-step metadata,
     *        step failure evidence, or cutoff witnesses do not reproduce
     * @throws IllegalScheduleException if a source choice is disabled or its thread is invalid
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
     * @throws IllegalScheduleException if a source choice is disabled or its thread is invalid
     * @throws CorruptedTraceException if execution or evidence does not match
     */
    public Configuration replay(Program program, Trace trace, Invariant invariant) {
        return program.isSource() ? sourceExecution(program, trace, invariant).configuration() : replay(program, trace);
    }

    /** Validates source choices, identity, outcomes and reason-specific cutoff witnesses.
     * Failure evidence is compared for STEP failures and whenever an invariant is supplied.
     * With an invariant, INCOMPLETE requires a nonterminal PREFIX and a separate cutoff witness;
     * other recorded terminal outcomes must reproduce. Without one, property verdicts are not checked.
     * @param program compiled source model supplying identity and executable instructions
     * @param trace aligned recorded prefix and optional source evidence
     * @param invariant timed property, or null for execution and step-evidence checks only
     * @return execution result containing the last committed configuration and snapshots
     * @throws IllegalScheduleException if a recorded source choice is not enabled or its thread is invalid
     * @throws CorruptedTraceException if outcomes, identity, checked failures, cutoff evidence,
     *        or an invariant-validated terminal verdict do not reproduce
     */
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

    /** Checks loop cutoffs by scratch execution and scheduling cutoffs by replayed preemption cost.
     * A scheduling switch costs one only when the previous thread remains enabled. The entire
     * recorded prefix must fit within the bound, and the omitted enabled choice must exceed it.
     * @param program source model whose recorded prefix has already replayed successfully
     * @param trace prefix carrying a non-null loop or preemption cutoff witness
     * @param end last committed configuration reached by that prefix
     * @throws CorruptedTraceException if the witness is not the enabled instruction at the recorded
     *        counter, source identity or loop-iteration metadata differs, a loop cutoff fails to
     *        reproduce, the prefix exceeds its preemption bound,
     *        or the omitted scheduling choice does not exceed that bound
     */
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
        ExecutedStep expected = ((SourceStep) program.threads().get(cutoff.threadId()).steps()
            .get(cutoff.instructionIndex())).metadata(cutoff.instructionIndex());
        if (cutoff.iterations() != null || !Objects.equals(cutoff.source(), expected.source())
                || !cutoff.expansionPath().equals(expected.expansionPath())) {
            throw new CorruptedTraceException("Preemption cutoff source identity does not match");
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

