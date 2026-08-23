package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.*;

public final class VerificationResult {
    private final Strategy strategy;
    private final long statesExplored;
    private final long wallTimeMs;
    private final long peakMemoryBytes;
    private final List<Trace> failingTraces;
    private final List<Trace> deadlockedTraces;
    private final List<Trace> completedTraces;

    public VerificationResult(Strategy strategy, long statesExplored, long wallTimeMs, long peakMemoryBytes,
                              List<Trace> failingTraces, List<Trace> deadlockedTraces, List<Trace> completedTraces) {
        this.strategy = strategy;
        this.statesExplored = statesExplored;
        this.wallTimeMs = wallTimeMs;
        this.peakMemoryBytes = peakMemoryBytes;
        this.failingTraces = List.copyOf(failingTraces);
        this.deadlockedTraces = List.copyOf(deadlockedTraces);
        this.completedTraces = List.copyOf(completedTraces);
    }

    public static VerificationResult from(DfsResult result, Strategy strategy, long wallTimeMs, long peakMemoryBytes) {
        List<Trace> failing = new ArrayList<>();
        List<Trace> deadlocked = new ArrayList<>();
        List<Trace> completed = new ArrayList<>();

        for (Trace trace : result.traces()) {
            switch (trace.outcome()) {
                case VIOLATION -> failing.add(trace);
                case DEADLOCK -> deadlocked.add(trace);
                case COMPLETED -> completed.add(trace);
            }
        }

        return new VerificationResult(strategy, result.statesExplored(), wallTimeMs, peakMemoryBytes,
                                      failing, deadlocked, completed);
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

    public long statesExplored() {
        return statesExplored;
    }

    public long wallTimeMs() {
        return wallTimeMs;
    }

    public long peakMemoryBytes() {
        return peakMemoryBytes;
    }

    public Strategy strategyUsed() {
        return strategy;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"strategy\": \"").append(strategy).append("\",\n");
        sb.append("  \"statesExplored\": ").append(statesExplored).append(",\n");
        sb.append("  \"wallTimeMs\": ").append(wallTimeMs).append(",\n");
        sb.append("  \"peakMemoryBytes\": ").append(peakMemoryBytes).append(",\n");
        sb.append("  \"hasViolation\": ").append(hasViolation()).append(",\n");
        sb.append("  \"failingTraces\": ").append(jsonTraces(failingTraces)).append(",\n");
        sb.append("  \"deadlockedTraces\": ").append(jsonTraces(deadlockedTraces)).append(",\n");
        sb.append("  \"completedTraces\": ").append(jsonTraces(completedTraces)).append("\n");
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
