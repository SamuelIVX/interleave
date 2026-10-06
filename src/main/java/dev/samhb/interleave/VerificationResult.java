/** Exploration traces and runtime measurements for one selected strategy. */
package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.io.Serializable;
import java.util.*;

/** Exploration traces and runtime measurements for one selected strategy. */
public final class VerificationResult implements Serializable {
    /** Strategy. */
    private final Strategy strategy;
    /** States explored. */
    private final long statesExplored;
    /** Wall time ms. */
    private final long wallTimeMs;
    /** Heap delta bytes. */
    private final long heapDeltaBytes;
    /** Failing traces. */
    private final List<Trace> failingTraces;
    /** Deadlocked traces. */
    private final List<Trace> deadlockedTraces;
    /** Completed traces. */
    private final List<Trace> completedTraces;
    /** Incomplete traces. */
    private final List<Trace> incompleteTraces;

    /**
     * Creates verification result from the supplied values.
     * @param strategy exploration strategy used for these results
     * @param statesExplored number of explored configurations
     * @param wallTimeMs elapsed exploration time in milliseconds
     * @param heapDeltaBytes observed heap delta in bytes
     * @param failingTraces traces ending in a property violation
     * @param deadlockedTraces traces ending in deadlock
     * @param completedTraces traces whose threads all terminate
     * @param incompleteTraces traces stopped before a terminal verdict
     */
    VerificationResult(Strategy strategy, long statesExplored, long wallTimeMs, long heapDeltaBytes,
                              List<Trace> failingTraces, List<Trace> deadlockedTraces, List<Trace> completedTraces,
                              List<Trace> incompleteTraces) {
        this.strategy = strategy;
        this.statesExplored = statesExplored;
        this.wallTimeMs = wallTimeMs;
        this.heapDeltaBytes = heapDeltaBytes;
        this.failingTraces = List.copyOf(failingTraces);
        this.deadlockedTraces = List.copyOf(deadlockedTraces);
        this.completedTraces = List.copyOf(completedTraces);
        this.incompleteTraces = List.copyOf(incompleteTraces);
    }

    /**
     * Wraps exploration traces and counts with the selected strategy and runtime measurements.
     * @param result completed exploration result
     * @param strategy exploration strategy used for these results
     * @param wallTimeMs elapsed exploration time in milliseconds
     * @param heapDeltaBytes observed heap delta in bytes
     * @return verification result preserving the supplied traces and measured metrics
     */
    public static VerificationResult from(DfsResult result, Strategy strategy, long wallTimeMs, long heapDeltaBytes) {
        List<Trace> failing = new ArrayList<>();
        List<Trace> deadlocked = new ArrayList<>();
        List<Trace> completed = new ArrayList<>();
        List<Trace> incomplete = new ArrayList<>();

        for (Trace trace : result.traces()) {
            switch (trace.outcome()) {
                case VIOLATION -> failing.add(trace);
                case DEADLOCK -> deadlocked.add(trace);
                case COMPLETED -> completed.add(trace);
                case INCOMPLETE -> incomplete.add(trace);
            }
        }

        return new VerificationResult(strategy, result.statesExplored(), wallTimeMs, heapDeltaBytes,
                                      failing, deadlocked, completed, incomplete);
    }

    /**
     * Reports whether at least one trace violates the checked property.
     * @return true if at least one recorded trace ends in VIOLATION
     */
    public boolean hasViolation() {
        return !failingTraces.isEmpty();
    }

    /**
     * Returns failing traces for this verification result.
     * @return immutable recorded violation traces
     */
    public List<Trace> failingTraces() {
        return failingTraces;
    }

    /**
     * Returns deadlocked traces for this verification result.
     * @return immutable recorded deadlock traces
     */
    public List<Trace> deadlockedTraces() {
        return deadlockedTraces;
    }

    /**
     * Returns completed traces for this verification result.
     * @return immutable recorded completed traces
     */
    public List<Trace> completedTraces() {
        return completedTraces;
    }

    /**
     * Returns traces from a context-bounded search that ran out of preemption budget without
     * finding a violation.
     *
     * @return immutable recorded incomplete traces
     */
    public List<Trace> incompleteTraces() {
        return incompleteTraces;
    }

    /**
     * Returns whether this run was inconclusive because a preemption bound was reached.
     *
     * @return true if the search was truncated by its preemption bound
     */
    public boolean hasIncomplete() {
        return !incompleteTraces.isEmpty();
    }

    /**
     * Returns states explored for this verification result.
     * @return number of visited search positions
     */
    public long statesExplored() {
        return statesExplored;
    }

    /**
     * Returns wall time ms for this verification result.
     * @return elapsed wall-clock time in milliseconds
     */
    public long wallTimeMs() {
        return wallTimeMs;
    }

    /**
     * Returns heap delta bytes for this verification result.
     * @return observed heap-usage difference in bytes; may be negative after garbage collection
     */
    public long heapDeltaBytes() {
        return heapDeltaBytes;
    }

    /**
     * Returns strategy used for this verification result.
     * @return strategy used to produce this result
     */
    public Strategy strategyUsed() {
        return strategy;
    }

    /**
     * Returns to test result for this verification result.
     * @return public result containing the same trace categories and measurements
     */
    public TestResult toTestResult() {
        List<TraceRecord> failingTraces = new ArrayList<>();
        List<TraceRecord> deadlockedTraces = new ArrayList<>();
        List<TraceRecord> completedTraces = new ArrayList<>();
        List<TraceRecord> incompleteTraces = new ArrayList<>();

        for (Trace trace : this.failingTraces) {
            failingTraces.add(trace.toRecord());
        }
        for (Trace trace : this.deadlockedTraces) {
            deadlockedTraces.add(trace.toRecord());
        }
        for (Trace trace : this.completedTraces) {
            completedTraces.add(trace.toRecord());
        }
        for (Trace trace : this.incompleteTraces) {
            incompleteTraces.add(trace.toRecord());
        }

        return new TestResult(strategy, statesExplored, wallTimeMs, heapDeltaBytes,
                              failingTraces, deadlockedTraces, completedTraces, incompleteTraces, false);
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
        sb.append("  \"failingTraces\": ").append(jsonTraces(failingTraces)).append(",\n");
        sb.append("  \"deadlockedTraces\": ").append(jsonTraces(deadlockedTraces)).append(",\n");
        sb.append("  \"completedTraces\": ").append(jsonTraces(completedTraces)).append(",\n");
        // Emitted unconditionally so the JSON shape does not depend on the result's content.
        sb.append("  \"incompleteTraces\": ").append(jsonTraces(incompleteTraces)).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Serializes trace records for inclusion in the result JSON.
     * @param traces recorded executions
     * @return JSON array of trace records
     */
    private String jsonTraces(List<Trace> traces) {
        if (traces.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            sb.append("    {\"threads\": [");
            for (int j = 0; j < trace.threadIds().size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(trace.threadIds().get(j));
            }
            sb.append("], \"outcome\": \"").append(trace.outcome()).append("\"}");
            if (i < traces.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }
}
