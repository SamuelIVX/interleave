package dev.samhb.interleave.report;

import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.Invariant;
import java.util.Optional;

public final class BenchmarkResult {
    private final String strategy;
    private final String bugName;
    private final long statesExplored;
    private final long wallTimeMs;
    private final long heapDeltaBytes;
    private final String verdict;
    private final Trace failingTrace;
    private final Invariant invariant;

    public BenchmarkResult(String strategy, String bugName, long statesExplored, 
                           long wallTimeMs, long heapDeltaBytes, String verdict) {
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, null, null);
    }

    public BenchmarkResult(String strategy, String bugName, long statesExplored, 
                           long wallTimeMs, long heapDeltaBytes, String verdict,
                           Trace failingTrace, Invariant invariant) {
        this.strategy = strategy;
        this.bugName = bugName;
        this.statesExplored = statesExplored;
        this.wallTimeMs = wallTimeMs;
        this.heapDeltaBytes = heapDeltaBytes;
        this.verdict = verdict;
        this.failingTrace = failingTrace;
        this.invariant = invariant;
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

    public Optional<Trace> failingTrace() {
        return Optional.ofNullable(failingTrace);
    }

    public Optional<Invariant> invariant() {
        return Optional.ofNullable(invariant);
    }
}
