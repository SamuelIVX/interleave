package dev.samhb.interleave.report;

import dev.samhb.interleave.search.Trace;
import java.util.Optional;

/**
 * Immutable result of a single benchmark run (one strategy + one store type).
 */
public final class BenchmarkResult {
    private final String strategy;
    private final String bugName;
    private final long statesExplored;
    private final long wallTimeMs;
    private final long heapDeltaBytes;
    private final String verdict;
    private final Trace failingTrace;
    private final StoreType storeType;

    /**
     * Creates a result with the exact store type (backward compatible).
     *
     * @param strategy the strategy name (e.g., "DFS", "STATIC_POR", "DPOR")
     * @param bugName the benchmark program name
     * @param statesExplored number of states explored
     * @param wallTimeMs wall-clock time in milliseconds
     * @param heapDeltaBytes heap memory delta in bytes
     * @param verdict the verdict ("PASS", "VIOLATION", "DEADLOCK")
     */
    public BenchmarkResult(String strategy, String bugName, long statesExplored,
                           long wallTimeMs, long heapDeltaBytes, String verdict) {
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, null, StoreType.EXACT);
    }

    /**
     * Creates a result with the exact store type and failing trace (backward compatible).
     *
     * @param strategy the strategy name
     * @param bugName the benchmark program name
     * @param statesExplored number of states explored
     * @param wallTimeMs wall-clock time in milliseconds
     * @param heapDeltaBytes heap memory delta in bytes
     * @param verdict the verdict
     * @param failingTrace the failing trace, or null if none
     */
    public BenchmarkResult(String strategy, String bugName, long statesExplored,
                           long wallTimeMs, long heapDeltaBytes, String verdict,
                           Trace failingTrace) {
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, failingTrace, StoreType.EXACT);
    }

    /**
     * Creates a result with an explicit store type (for bitstate results).
     *
     * @param strategy the strategy name
     * @param bugName the benchmark program name
     * @param statesExplored number of states explored
     * @param wallTimeMs wall-clock time in milliseconds
     * @param heapDeltaBytes heap memory delta in bytes
     * @param verdict the verdict
     * @param storeType the store type (EXACT or BITSTATE)
     */
    public BenchmarkResult(String strategy, String bugName, long statesExplored,
                           long wallTimeMs, long heapDeltaBytes, String verdict,
                           StoreType storeType) {
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, null, storeType);
    }

    /**
     * Full constructor with all fields.
     *
     * @param strategy the strategy name
     * @param bugName the benchmark program name
     * @param statesExplored number of states explored
     * @param wallTimeMs wall-clock time in milliseconds
     * @param heapDeltaBytes heap memory delta in bytes
     * @param verdict the verdict
     * @param failingTrace the failing trace, or null if none
     * @param storeType the store type (EXACT or BITSTATE)
     */
    public BenchmarkResult(String strategy, String bugName, long statesExplored,
                           long wallTimeMs, long heapDeltaBytes, String verdict,
                           Trace failingTrace, StoreType storeType) {
        this.strategy = strategy;
        this.bugName = bugName;
        this.statesExplored = statesExplored;
        this.wallTimeMs = wallTimeMs;
        this.heapDeltaBytes = heapDeltaBytes;
        this.verdict = verdict;
        this.failingTrace = failingTrace;
        this.storeType = storeType;
    }

    /**
     * Returns the strategy name.
     *
     * @return the strategy (e.g., "DFS", "STATIC_POR", "DPOR")
     */
    public String strategy() {
        return strategy;
    }

    /**
     * Returns the benchmark program name.
     *
     * @return the bug/program name
     */
    public String bugName() {
        return bugName;
    }

    /**
     * Returns the number of states explored.
     *
     * @return states explored count
     */
    public long statesExplored() {
        return statesExplored;
    }

    /**
     * Returns the wall-clock time in milliseconds.
     *
     * @return wall time in ms
     */
    public long wallTimeMs() {
        return wallTimeMs;
    }

    /**
     * Returns the heap memory delta in bytes.
     *
     * @return heap delta in bytes
     */
    public long heapDeltaBytes() {
        return heapDeltaBytes;
    }

    /**
     * Returns the verdict.
     *
     * @return "PASS", "VIOLATION", or "DEADLOCK"
     */
    public String verdict() {
        return verdict;
    }

    /**
     * Returns the failing trace if a violation was found.
     *
     * @return optional failing trace
     */
    public Optional<Trace> failingTrace() {
        return Optional.ofNullable(failingTrace);
    }

    /**
     * Returns the store type used for this run.
     *
     * @return EXACT for HashingStateStore, BITSTATE for BitstateStore
     */
    public StoreType storeType() {
        return storeType;
    }
}