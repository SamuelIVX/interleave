/** Immutable result of a single benchmark run (one strategy + one store type). */
package dev.samhb.interleave.report;

import dev.samhb.interleave.search.Trace;
import java.util.Optional;

/**
 * Immutable result of a single benchmark run (one strategy + one store type).
 */
public final class BenchmarkResult {
    /** Strategy. */
    private final String strategy;
    /** Bug name. */
    private final String bugName;
    /** States explored. */
    private final long statesExplored;
    /** Wall time ms. */
    private final long wallTimeMs;
    /** Heap delta bytes. */
    private final long heapDeltaBytes;
    /** Verdict. */
    private final String verdict;
    /** Failing trace. */
    private final Trace failingTrace;
    /** Store type. */
    private final StoreType storeType;
    /** Estimated false positive rate. */
    private final double estimatedFalsePositiveRate;
    /** Bitstate bit count. */
    private final int bitstateBitCount;
    /** Bitstate bit density. */
    private final double bitstateBitDensity;
    /** Preemptions used. */
    private final Integer preemptionsUsed;

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
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, null, StoreType.EXACT, 0.0, 0, 0.0, null);
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
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, failingTrace, StoreType.EXACT, 0.0, 0, 0.0, null);
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
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, null, storeType, 0.0, 0, 0.0, null);
    }

    /**
     * Creates a result with an explicit store type and failing trace (no bitstate metrics).
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
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, failingTrace, storeType, 0.0, 0, 0.0, null);
    }

    /**
     * Full constructor with all fields including bitstate metrics.
     *
     * @param strategy the strategy name
     * @param bugName the benchmark program name
     * @param statesExplored number of states explored
     * @param wallTimeMs wall-clock time in milliseconds
     * @param heapDeltaBytes heap memory delta in bytes
     * @param verdict the verdict
     * @param failingTrace the failing trace, or null if none
     * @param storeType the store type (EXACT or BITSTATE)
     * @param estimatedFalsePositiveRate estimated false-positive rate for bitstate (0 for exact)
     * @param bitstateBitCount number of bits set in the bit vector (0 for exact)
     * @param bitstateBitDensity fraction of bits set in [0,1] (0 for exact)
     */
    public BenchmarkResult(String strategy, String bugName, long statesExplored,
                           long wallTimeMs, long heapDeltaBytes, String verdict,
                           Trace failingTrace, StoreType storeType,
                           double estimatedFalsePositiveRate, int bitstateBitCount,
                           double bitstateBitDensity) {
        this(strategy, bugName, statesExplored, wallTimeMs, heapDeltaBytes, verdict, failingTrace,
             storeType, estimatedFalsePositiveRate, bitstateBitCount, bitstateBitDensity, null);
    }

    /**
     * Full constructor including the preemption bound that produced this row.
     *
     * @param preemptionsUsed the preemption bound the search actually ran at, or null for
     *        strategies that have no bound. Boxed because {@code null} is meaningful and must stay
     *        distinct from {@code 0}, which is a legal bound.
     * @param strategy exploration strategy used for these results
     * @param bugName benchmark program name
     * @param statesExplored number of explored configurations
     * @param wallTimeMs elapsed exploration time in milliseconds
     * @param heapDeltaBytes observed heap delta in bytes
     * @param verdict reported exploration verdict
     * @param failingTrace representative failure trace, or null when absent
     * @param storeType visited-store strategy represented by this row
     * @param estimatedFalsePositiveRate estimated Bloom-filter false-positive rate; zero for an exact store
     * @param bitstateBitCount number of set bits in the Bloom filter
     * @param bitstateBitDensity fraction of Bloom-filter bits that are set
     */
    public BenchmarkResult(String strategy, String bugName, long statesExplored,
                           long wallTimeMs, long heapDeltaBytes, String verdict,
                           Trace failingTrace, StoreType storeType,
                           double estimatedFalsePositiveRate, int bitstateBitCount,
                           double bitstateBitDensity, Integer preemptionsUsed) {
        this.strategy = strategy;
        this.bugName = bugName;
        this.statesExplored = statesExplored;
        this.wallTimeMs = wallTimeMs;
        this.heapDeltaBytes = heapDeltaBytes;
        this.verdict = verdict;
        this.failingTrace = failingTrace;
        this.storeType = storeType;
        this.estimatedFalsePositiveRate = estimatedFalsePositiveRate;
        this.bitstateBitCount = bitstateBitCount;
        this.bitstateBitDensity = bitstateBitDensity;
        this.preemptionsUsed = preemptionsUsed;
    }

    /**
     * Returns the strategy name.
     *
     * @return strategy used to produce this result
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
     * @return number of visited search positions
     */
    public long statesExplored() {
        return statesExplored;
    }

    /**
     * Returns the wall-clock time in milliseconds.
     *
     * @return elapsed wall-clock time in milliseconds
     */
    public long wallTimeMs() {
        return wallTimeMs;
    }

    /**
     * Returns the heap memory delta in bytes.
     *
     * @return observed heap-usage difference in bytes; may be negative after garbage collection
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

    /**
     * Returns the estimated false-positive rate for bitstate runs.
     * Zero for exact runs.
     *
     * @return estimated FPR in [0, 1]
     */
    public double estimatedFalsePositiveRate() {
        return estimatedFalsePositiveRate;
    }

    /**
     * Returns the number of bits set in the bit vector for bitstate runs.
     * Zero for exact runs.
     *
     * @return bit cardinality
     */
    public int bitstateBitCount() {
        return bitstateBitCount;
    }

    /**
     * Returns the fraction of bits set for bitstate runs.
     * Zero for exact runs.
     *
     * @return bit density in [0, 1]
     */
    public double bitstateBitDensity() {
        return bitstateBitDensity;
    }

    /**
     * Returns the preemption bound that produced this row, or null for strategies that have no
     * bound.
     *
     * <p>This is the bound the search actually ran at, which under iterative deepening is the
     * minimal K that produced the result -- not the configured ceiling. Reporting the ceiling
     * after the search stopped early would misstate how much was explored.
     *
     * @return the preemption bound, or null if not applicable
     */
    public Integer preemptionsUsed() {
        return preemptionsUsed;
    }
}
