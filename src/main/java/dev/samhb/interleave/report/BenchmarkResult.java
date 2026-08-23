package dev.samhb.interleave.report;

import dev.samhb.interleave.search.DfsResult;

public final class BenchmarkResult {
    private final String strategy;
    private final String bugName;
    private final long statesExplored;
    private final long wallTimeMs;
    private final long heapDeltaBytes;
    private final String verdict;

    public BenchmarkResult(String strategy, String bugName, long statesExplored, 
                           long wallTimeMs, long heapDeltaBytes, String verdict) {
        this.strategy = strategy;
        this.bugName = bugName;
        this.statesExplored = statesExplored;
        this.wallTimeMs = wallTimeMs;
        this.heapDeltaBytes = heapDeltaBytes;
        this.verdict = verdict;
    }

    public String strategy() {
        return strategy;
    }

    public String bugName() {
        return bugName;
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

    public String verdict() {
        return verdict;
    }
}
