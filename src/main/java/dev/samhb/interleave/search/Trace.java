package dev.samhb.interleave.search;

import dev.samhb.interleave.core.StepOutcome;
import java.util.*;

public final class Trace {
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
