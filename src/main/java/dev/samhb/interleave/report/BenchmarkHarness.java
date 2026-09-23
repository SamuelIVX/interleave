package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;
import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.state.HashingStateStore;
import dev.samhb.interleave.search.StateStore;
import java.util.*;

/**
 * Runs the full benchmark suite across all programs and strategies.
 * Each program is executed with all three strategies (DFS, STATIC_POR, DPOR)
 * using both exact ({@link HashingStateStore}) and bitstate ({@link BitstateStore})
 * state stores, producing 6 results per program (42 total for the corpus).
 */
public final class BenchmarkHarness {
    private static final int DEFAULT_BITSTATE_SIZE = 1_000_003;
    private static final int DEFAULT_BITSTATE_K = 4;

    private final int bitstateSize;
    private final int bitstateK;
    private final Set<StoreType> storeFilter;
    private final Set<String> strategyFilter;

    /**
     * Creates a harness with default parameters (all stores, all strategies).
     */
    public BenchmarkHarness() {
        this(DEFAULT_BITSTATE_SIZE, DEFAULT_BITSTATE_K, null, null);
    }

    /**
     * Creates a harness with custom bitstate parameters.
     *
     * @param bitstateSize the bit-array size for BitstateStore
     * @param bitstateK the number of hash functions for BitstateStore
     */
    public BenchmarkHarness(int bitstateSize, int bitstateK) {
        this(bitstateSize, bitstateK, null, null);
    }

    /**
     * Creates a harness with custom parameters and optional filters.
     *
     * @param bitstateSize the bit-array size for BitstateStore
     * @param bitstateK the number of hash functions for BitstateStore
     * @param storeFilter the store types to run, or null for all
     * @param strategyFilter the strategies to run, or null for all
     */
    public BenchmarkHarness(int bitstateSize, int bitstateK,
                            Set<StoreType> storeFilter, Set<String> strategyFilter) {
        this.bitstateSize = bitstateSize;
        this.bitstateK = bitstateK;
        this.storeFilter = storeFilter;
        this.strategyFilter = strategyFilter;
    }

    /**
     * Runs the complete benchmark suite for all programs in the corpus.
     * Returns up to 6 results per program: 3 strategies × 2 store types,
     * filtered by the configured store and strategy filters.
     *
     * @return list of {@link BenchmarkResult}s
     */
    public List<BenchmarkResult> runAll() {
        List<BenchmarkResult> results = new ArrayList<>();

        for (BenchmarkProgram program : BugCorpus.all()) {
            results.addAll(runProgram(program));
        }

        return results;
    }

    /**
     * Runs all strategies for a single program with both exact and bitstate stores.
     * Produces up to 6 results: DFS, STATIC_POR, DPOR × {EXACT, BITSTATE},
     * filtered by the configured store and strategy filters.
     *
     * @param program the benchmark program
     * @return list of {@link BenchmarkResult}s
     */
    public List<BenchmarkResult> runProgram(BenchmarkProgram program) {
        List<BenchmarkResult> results = new ArrayList<>();
        Invariant invariant = program.invariant().orElse(null);

        boolean runExact = storeFilter == null || storeFilter.contains(StoreType.EXACT);
        boolean runBitstate = storeFilter == null || storeFilter.contains(StoreType.BITSTATE);

        // Run with exact state store (HashingStateStore)
        if (runExact) {
            results.addAll(runProgramWithStore(program, invariant, HashingStateStore::new, StoreType.EXACT));
        }

        // Run with bitstate store (BitstateStore)
        if (runBitstate) {
            results.addAll(runProgramWithStore(program, invariant,
                () -> new BitstateStore(bitstateSize, bitstateK), StoreType.BITSTATE));
        }

        return results;
    }

    /**
     * Runs all three strategies with the given state store factory and store type.
     * Validates verdict against expected only for EXACT store type (bitstate is
     * incomplete by design and may miss violations).
     *
     * @param program the benchmark program
     * @param invariant the invariant to check, or null
     * @param storeFactory factory for creating fresh state stores
     * @param storeType the store type for labeling results
     * @return list of results (one per selected strategy)
     */
    private List<BenchmarkResult> runProgramWithStore(BenchmarkProgram program, Invariant invariant,
                                                       java.util.function.Supplier<StateStore> storeFactory,
                                                       StoreType storeType) {
        List<BenchmarkResult> results = new ArrayList<>();

        boolean runDfs = strategyFilter == null || strategyFilter.contains("DFS");
        boolean runPor = strategyFilter == null || strategyFilter.contains("STATIC_POR");
        boolean runDpor = strategyFilter == null || strategyFilter.contains("DPOR");

        // DFS
        if (runDfs) {
            DfsExplorer dfsExplorer = new DfsExplorer();
            StateStore dfsStore = storeFactory.get();
            DfsResultWithTiming dfsResult = runExplorer(() -> dfsExplorer.explore(program.program(), invariant, dfsStore, null));
            String dfsVerdict = actualVerdict(dfsResult.result());
            String expectedVerdict = program.expectedVerdict();
            if (expectedVerdict != null && storeType == StoreType.EXACT && !expectedVerdict.equals(dfsVerdict)) {
                throw new IllegalStateException("Expected verdict " + expectedVerdict +
                    " for " + program.name() + " but got " + dfsVerdict);
            }
            Trace dfsFailing = findFailingTrace(dfsResult.result());
            results.add(createResult("DFS", program.name(), dfsResult, dfsVerdict, dfsFailing, storeType, dfsStore));
        }

        // STATIC_POR
        if (runPor) {
            StaticPorExplorer porExplorer = new StaticPorExplorer();
            StateStore porStore = storeFactory.get();
            DfsResultWithTiming porResult = runExplorer(() -> porExplorer.explore(program.program(), invariant, porStore, null));
            String porVerdict = actualVerdict(porResult.result());
            Trace porFailing = findFailingTrace(porResult.result());
            results.add(createResult("STATIC_POR", program.name(), porResult, porVerdict, porFailing, storeType, porStore));
        }

        // DPOR
        if (runDpor) {
            DporExplorer dporExplorer = new DporExplorer();
            StateStore dporStore = storeFactory.get();
            DfsResultWithTiming dporResult = runExplorer(() -> dporExplorer.explore(program.program(), invariant, dporStore, null));
            String dporVerdict = actualVerdict(dporResult.result());
            Trace dporFailing = findFailingTrace(dporResult.result());
            results.add(createResult("DPOR", program.name(), dporResult, dporVerdict, dporFailing, storeType, dporStore));
        }

        return results;
    }

    private BenchmarkResult createResult(String strategy, String bugName,
                                          DfsResultWithTiming result, String verdict,
                                          Trace failingTrace, StoreType storeType,
                                          StateStore store) {
        if (store instanceof BitstateStore bs) {
            return new BenchmarkResult(strategy, bugName, result.result().statesExplored(),
                result.wallTimeMs(), result.heapDeltaBytes(), verdict,
                failingTrace, storeType,
                bs.estimatedFalsePositiveRate(), bs.bitCount(), bs.bitDensity());
        }
        return new BenchmarkResult(strategy, bugName, result.result().statesExplored(),
            result.wallTimeMs(), result.heapDeltaBytes(), verdict,
            failingTrace, storeType);
    }

    /**
     * Runs an explorer with timing and memory measurement.
     * The timer starts after forced GC and memory capture, so wall time reflects
     * exploration only, not GC overhead.
     *
     * @param explorer a supplier that runs the exploration
     * @return the result with timing info
     */
    private DfsResultWithTiming runExplorer(java.util.function.Supplier<DfsResult> explorer) {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        long start = System.currentTimeMillis();

        DfsResult result = explorer.get();

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long peakMemory = Math.max(0, memAfter - memBefore);

        return new DfsResultWithTiming(result, wallTime, peakMemory);
    }

    /**
     * Derives the verdict from a set of traces.
     *
     * @param result the exploration result containing traces
     * @return "VIOLATION", "DEADLOCK", or "PASS"
     */
    private static String actualVerdict(DfsResult result) {
        boolean hasViolation = result.traces().stream()
            .anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
        boolean hasDeadlock = result.traces().stream()
            .anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK);

        if (hasViolation) return "VIOLATION";
        if (hasDeadlock) return "DEADLOCK";
        return "PASS";
    }

    /**
     * Finds the first failing trace in the result.
     *
     * @param result the exploration result
     * @return the first trace with VIOLATION outcome, or null if none
     */
    private static Trace findFailingTrace(DfsResult result) {
        return result.traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.VIOLATION)
            .findFirst()
            .orElse(null);
    }

    /**
     * Carries a DfsResult along with timing and memory measurements.
     */
    private record DfsResultWithTiming(DfsResult result, long wallTimeMs, long heapDeltaBytes) {}
}
