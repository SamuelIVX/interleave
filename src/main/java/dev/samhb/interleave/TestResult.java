package dev.samhb.interleave;

import java.io.Serializable;
import java.util.List;

public final class TestResult implements Serializable {
    private final Strategy strategy;
    private final long statesExplored;
    private final long wallTimeMs;
    private final long heapDeltaBytes;
    private final List<TraceRecord> failingTraces;
    private final List<TraceRecord> deadlockedTraces;
    private final List<TraceRecord> completedTraces;
    private final List<TraceRecord> incompleteTraces;
    private final boolean limitExceeded;

    public TestResult(Strategy strategy, long statesExplored, long wallTimeMs, long heapDeltaBytes,
                      List<TraceRecord> failingTraces, List<TraceRecord> deadlockedTraces,
                      List<TraceRecord> completedTraces, List<TraceRecord> incompleteTraces,
                      boolean limitExceeded) {
        this.strategy = strategy;
        this.statesExplored = statesExplored;
        this.wallTimeMs = wallTimeMs;
        this.heapDeltaBytes = heapDeltaBytes;
        this.failingTraces = List.copyOf(failingTraces);
        this.deadlockedTraces = List.copyOf(deadlockedTraces);
        this.completedTraces = List.copyOf(completedTraces);
        this.incompleteTraces = List.copyOf(incompleteTraces);
        this.limitExceeded = limitExceeded;
    }

    /**
     * Creates a result with no INCOMPLETE traces. Kept so the pre-context-bounded call signature
     * keeps compiling and produces exactly the output it did before.
     */
    public TestResult(Strategy strategy, long statesExplored, long wallTimeMs, long heapDeltaBytes,
                      List<TraceRecord> failingTraces, List<TraceRecord> deadlockedTraces,
                      List<TraceRecord> completedTraces, boolean limitExceeded) {
        this(strategy, statesExplored, wallTimeMs, heapDeltaBytes,
             failingTraces, deadlockedTraces, completedTraces, List.of(), limitExceeded);
    }

    public Strategy strategy() {
        return strategy;
    }

    public long statesExplored() {
        return statesExplored;
    }

    public long wallTimeMs() {
        return wallTimeMs;
    }

    public long heapDeltaBytes() {
        return heapDeltaBytes;
    }

    public List<TraceRecord> failingTraces() {
        return failingTraces;
    }

    public List<TraceRecord> deadlockedTraces() {
        return deadlockedTraces;
    }

    public List<TraceRecord> completedTraces() {
        return completedTraces;
    }

    /**
     * Returns traces from a context-bounded search that ran out of preemption budget without
     * finding a violation. Empty for strategies that are exhaustive regardless of budget.
     *
     * @return the INCOMPLETE traces
     */
    public List<TraceRecord> incompleteTraces() {
        return incompleteTraces;
    }

    /**
     * Returns whether this run was inconclusive because a preemption bound was reached.
     *
     * <p>Independent of {@link #limitExceeded()} in the sense that neither is inferred from the
     * other, but not co-occurring today: a run stopped by the resource limit throws out of
     * {@code dfs()} before the INCOMPLETE trace is emitted, so it reports
     * {@code limitExceeded() == true} and {@code hasIncomplete() == false}. Neither flag says
     * anything about whether the preemption bound was reached in a resource-limited run.
     *
     * @return true if the search was truncated by its preemption bound
     */
    public boolean hasIncomplete() {
        return !incompleteTraces.isEmpty();
    }

    /**
     * Returns whether this run was stopped by a resource limit rather than by exhausting the
     * state space.
     *
     * <p>Distinct from {@link #hasIncomplete()}: a resource limit aborts the search outright and
     * yields no INCOMPLETE trace, so a limited run asserts nothing about completeness.
     *
     * @return true if the run was truncated by a resource limit
     */
    public boolean limitExceeded() {
        return limitExceeded;
    }

    public boolean hasViolation() {
        return !failingTraces.isEmpty();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"strategy\": \"").append(strategy).append("\",\n");
        sb.append("  \"statesExplored\": ").append(statesExplored).append(",\n");
        sb.append("  \"wallTimeMs\": ").append(wallTimeMs).append(",\n");
        sb.append("  \"heapDeltaBytes\": ").append(heapDeltaBytes).append(",\n");
        sb.append("  \"hasViolation\": ").append(hasViolation()).append(",\n");
        sb.append("  \"limitExceeded\": ").append(limitExceeded).append(",\n");
        sb.append("  \"failingTraces\": ").append(jsonTraces(failingTraces)).append(",\n");
        sb.append("  \"deadlockedTraces\": ").append(jsonTraces(deadlockedTraces)).append(",\n");
        sb.append("  \"completedTraces\": ").append(jsonTraces(completedTraces)).append(",\n");
        // Emitted unconditionally, including as [] when empty, so the JSON shape does not depend
        // on the result's content. Consumers can then branch on the key without checking presence.
        sb.append("  \"incompleteTraces\": ").append(jsonTraces(incompleteTraces)).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String jsonTraces(List<TraceRecord> traces) {
        if (traces.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < traces.size(); i++) {
            sb.append("  ").append(traces.get(i).toJson());
            if (i < traces.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
}