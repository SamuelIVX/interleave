/** Immutable ordered execution prefix with its terminal verdict. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.TraceRecord;
import dev.samhb.interleave.core.StepOutcome;
import java.io.Serializable;
import java.util.*;

/** Immutable ordered execution prefix with its terminal verdict. */
public final class Trace implements Serializable {
    /** Thread ids. */
    private final List<Integer> threadIds;
    /** Outcomes. */
    private final List<StepOutcome> outcomes;
    /** Outcome. */
    private final TraceOutcome outcome;

    /**
     * Creates trace from the supplied values.
     * @param threadIds ordered thread choices in the trace
     * @param outcomes outcome of each recorded step
     * @param outcome execution outcome being recorded
     */
    public Trace(List<Integer> threadIds, List<StepOutcome> outcomes, TraceOutcome outcome) {
        if (threadIds == null) throw new IllegalArgumentException("threadIds must not be null");
        if (outcomes == null) throw new IllegalArgumentException("outcomes must not be null");
        if (outcome == null) throw new IllegalArgumentException("outcome must not be null");
        if (threadIds.size() != outcomes.size()) {
            throw new IllegalArgumentException("threadIds and outcomes must have the same size");
        }
        this.threadIds = List.copyOf(threadIds);
        this.outcomes = List.copyOf(outcomes);
        this.outcome = outcome;
    }

    /**
     * Creates trace with the supplied initial values.
     * @param threadIds ordered thread choices in the trace
     * @param outcomes outcome of each recorded step
     * @param outcome execution outcome being recorded
     * @return new modeled value with the supplied initial values
     */
    public static Trace of(List<Integer> threadIds, List<StepOutcome> outcomes, TraceOutcome outcome) {
        return new Trace(threadIds, outcomes, outcome);
    }

    /**
     * Creates a trace describing a context-bounded search that ran out of preemption budget.
     *
     * <p>Both lists must come from a single snapshot of the live search path. The constructor
     * rejects mismatched lengths, and because the DFS path lists are mutated in place and popped
     * as the search unwinds, assembling {@code threadIds} at one moment and {@code outcomes} at
     * another is an easy way to produce a trace that is either rejected or meaningless.
     *
     * @param threadIds the scheduled thread ids of the partial schedule
     * @param outcomes the step outcomes of the partial schedule
     * @return a new {@link Trace} with outcome {@link TraceOutcome#INCOMPLETE}
     */
    public static Trace incomplete(List<Integer> threadIds, List<StepOutcome> outcomes) {
        return new Trace(threadIds, outcomes, TraceOutcome.INCOMPLETE);
    }

    /**
     * Returns thread ids for this trace.
     * @return immutable ordered thread choices
     */
    public List<Integer> threadIds() {
        return threadIds;
    }

    /**
     * Returns ordered step outcomes aligned with the recorded thread choices.
     * @return immutable step outcomes aligned with the thread choices
     */
    public List<StepOutcome> outcomes() {
        return outcomes;
    }

    /**
     * Returns outcome for this trace.
     * @return terminal verdict of this trace
     */
    public TraceOutcome outcome() {
        return outcome;
    }

    /**
     * Returns length for this trace.
     * @return number of recorded thread choices
     */
    public int length() {
        return threadIds.size();
    }

    /**
     * Converts this execution into an immutable public trace record.
     * @return immutable public record of this execution
     */
    public TraceRecord toRecord() {
        // programHash is empty for backward compat - could be computed from program if needed
        return new TraceRecord(threadIds, outcomes, outcome, "");
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < threadIds.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append("t").append(threadIds.get(i));
        }
        sb.append(" (").append(outcome).append(")");
        return sb.toString();
    }
}
