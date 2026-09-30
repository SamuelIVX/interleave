package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.io.Serializable;
import java.util.*;

public final class VerificationResult implements Serializable {
    private final Strategy strategy;
    private final long statesExplored;
    private final long wallTimeMs;
    private final long heapDeltaBytes;
    private final List<Trace> failingTraces;
    private final List<Trace> deadlockedTraces;
    private final List<Trace> completedTraces;
    private final List<Trace> incompleteTraces;

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

    public boolean hasViolation() {
        return !failingTraces.isEmpty();
    }

    public List<Trace> failingTraces() {
        return failingTraces;
    }

    public List<Trace> deadlockedTraces() {
        return deadlockedTraces;
    }

    public List<Trace> completedTraces() {
        return completedTraces;
    }

    /**
     * Returns traces from a context-bounded search that ran out of preemption budget without
     * finding a violation.
     *
     * @return the INCOMPLETE traces
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

    public long statesExplored() {
        return statesExplored;
    }

    public long wallTimeMs() {
        return wallTimeMs;
    }

    public long heapDeltaBytes() {
        return heapDeltaBytes;
    }

    public Strategy strategyUsed() {
        return strategy;
    }

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
