package dev.samhb.modelcheck.search;

import dev.samhb.modelcheck.core.StepOutcome;
import java.util.*;

public final class Trace {
    private final List<Integer> threadIds;
    private final List<StepOutcome> outcomes;

    public Trace(List<Integer> threadIds, List<StepOutcome> outcomes) {
        if (threadIds == null) throw new IllegalArgumentException("threadIds must not be null");
        if (outcomes == null) throw new IllegalArgumentException("outcomes must not be null");
        if (threadIds.size() != outcomes.size()) throw new IllegalArgumentException("threadIds and outcomes must have same size");
        this.threadIds = List.copyOf(threadIds);
        this.outcomes = List.copyOf(outcomes);
    }

    public static Trace of(List<Integer> threadIds, List<StepOutcome> outcomes) {
        return new Trace(threadIds, outcomes);
    }

    public List<Integer> threadIds() {
        return threadIds;
    }

    public List<StepOutcome> outcomes() {
        return outcomes;
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
        return sb.toString();
    }
}
