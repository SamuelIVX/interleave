/** Public verification outcome with categorized traces, measurements, and limit status. */
package dev.samhb.interleave;

import java.io.Serializable;
import java.util.List;

/** Public verification outcome with categorized traces, measurements, and limit status. */
public final class TestResult implements Serializable {
    /** Strategy. */
    private final Strategy strategy;
    /** States explored. */
    private final long statesExplored;
    /** Wall time ms. */
    private final long wallTimeMs;
    /** Heap delta bytes. */
    private final long heapDeltaBytes;
    /** Failing traces. */
    private final List<TraceRecord> failingTraces;
    /** Deadlocked traces. */
    private final List<TraceRecord> deadlockedTraces;
    /** Completed traces. */
    private final List<TraceRecord> completedTraces;
    /** Incomplete traces. */
    private final List<TraceRecord> incompleteTraces;
    /** Limit exceeded. */
    private final boolean limitExceeded;

    /**
     * Creates test result from the supplied values.
     * @param strategy exploration strategy used for these results
     * @param statesExplored number of explored configurations
     * @param wallTimeMs elapsed exploration time in milliseconds
     * @param heapDeltaBytes observed heap delta in bytes
     * @param failingTraces traces ending in a property violation
     * @param deadlockedTraces traces ending in deadlock
     * @param completedTraces traces whose threads all terminate
     * @param incompleteTraces traces stopped before a terminal verdict
     * @param limitExceeded whether a configured exploration limit stopped the run
     */
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
     * @param strategy exploration strategy used for these results
     * @param statesExplored number of explored configurations
     * @param wallTimeMs elapsed exploration time in milliseconds
     * @param heapDeltaBytes observed heap delta in bytes
     * @param failingTraces traces ending in a property violation
     * @param deadlockedTraces traces ending in deadlock
     * @param completedTraces traces whose threads all terminate
     * @param limitExceeded whether a configured exploration limit stopped the run
     */
    public TestResult(Strategy strategy, long statesExplored, long wallTimeMs, long heapDeltaBytes,
                      List<TraceRecord> failingTraces, List<TraceRecord> deadlockedTraces,
                      List<TraceRecord> completedTraces, boolean limitExceeded) {
        this(strategy, statesExplored, wallTimeMs, heapDeltaBytes,
             failingTraces, deadlockedTraces, completedTraces, List.of(), limitExceeded);
    }

    /**
     * Returns strategy for this test result.
     * @return strategy used to produce this result
     */
    public Strategy strategy() {
        return strategy;
    }

    /**
     * Returns states explored for this test result.
     * @return number of visited search positions
     */
    public long statesExplored() {
        return statesExplored;
    }

    /**
     * Returns wall time ms for this test result.
     * @return elapsed wall-clock time in milliseconds
     */
    public long wallTimeMs() {
        return wallTimeMs;
    }

    /**
     * Returns heap delta bytes for this test result.
     * @return observed heap-usage difference in bytes; may be negative after garbage collection
     */
    public long heapDeltaBytes() {
        return heapDeltaBytes;
    }

    /**
     * Returns failing traces for this test result.
     * @return immutable recorded violation traces
     */
    public List<TraceRecord> failingTraces() {
        return failingTraces;
    }

    /**
     * Returns deadlocked traces for this test result.
     * @return immutable recorded deadlock traces
     */
    public List<TraceRecord> deadlockedTraces() {
        return deadlockedTraces;
    }

    /**
     * Returns completed traces for this test result.
     * @return immutable recorded completed traces
     */
    public List<TraceRecord> completedTraces() {
        return completedTraces;
    }

    /**
     * Returns traces from a context-bounded search that ran out of preemption budget without
     * finding a violation. Empty for strategies that are exhaustive regardless of budget.
     *
     * @return immutable recorded incomplete traces
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

    /**
     * Reports whether at least one trace violates the checked property.
     * @return true if at least one recorded trace ends in VIOLATION
     */
    public boolean hasViolation() {
        return !failingTraces.isEmpty();
    }

    /**
     * Returns JSON containing the recorded outcomes and execution metrics.
     * @return JSON containing this result’s recorded values
     */
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

    /**
     * Serializes trace records for inclusion in the result JSON.
     * @param traces recorded executions
     * @return JSON array of trace records
     */
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
