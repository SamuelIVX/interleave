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
    private final boolean limitExceeded;

    public TestResult(Strategy strategy, long statesExplored, long wallTimeMs, long heapDeltaBytes,
                      List<TraceRecord> failingTraces, List<TraceRecord> deadlockedTraces,
                      List<TraceRecord> completedTraces, boolean limitExceeded) {
        this.strategy = strategy;
        this.statesExplored = statesExplored;
        this.wallTimeMs = wallTimeMs;
        this.heapDeltaBytes = heapDeltaBytes;
        this.failingTraces = List.copyOf(failingTraces);
        this.deadlockedTraces = List.copyOf(deadlockedTraces);
        this.completedTraces = List.copyOf(completedTraces);
        this.limitExceeded = limitExceeded;
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
        sb.append("  \"completedTraces\": ").append(jsonTraces(completedTraces)).append("\n");
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