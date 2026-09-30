package dev.samhb.interleave.search;

import dev.samhb.interleave.TraceRecord;
import dev.samhb.interleave.core.StepOutcome;
import java.io.Serializable;
import java.util.*;

public final class Trace implements Serializable {
    private final List<Integer> threadIds;
    private final List<StepOutcome> outcomes;
    private final TraceOutcome outcome;

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

    public List<Integer> threadIds() {
        return threadIds;
    }

    public List<StepOutcome> outcomes() {
        return outcomes;
    }

    public TraceOutcome outcome() {
        return outcome;
    }

    public int length() {
        return threadIds.size();
    }

    public TraceRecord toRecord() {
        // programHash is empty for backward compat - could be computed from program if needed
        return new TraceRecord(threadIds, outcomes, outcome, "");
    }

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
