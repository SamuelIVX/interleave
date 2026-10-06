/** Runs the full benchmark suite across all programs and strategies. Each program is executed with all four strategies (DFS, STATIC_POR, DPOR, CONTEXT_BOUNDED) using both exact ({@link HashingStateStore}) and bitstate ({@link BitstateStore}) state stores, producing 8 results per program. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
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
 * Each program is executed with all four strategies (DFS, STATIC_POR, DPOR,
 * CONTEXT_BOUNDED) using both exact ({@link HashingStateStore}) and bitstate
 * ({@link BitstateStore}) state stores, producing 8 results per program.
 */
public final class BenchmarkHarness {
    /** Default bitstate size. */
    private static final int DEFAULT_BITSTATE_SIZE = 1_000_003;
    /** Default bitstate k. */
    private static final int DEFAULT_BITSTATE_K = 4;
    /** Default max preemptions. */
    private static final int DEFAULT_MAX_PREEMPTIONS = ContextBoundedExplorer.DEFAULT_MAX_PREEMPTIONS;
    /** Context bounded. */
    private static final String CONTEXT_BOUNDED = "CONTEXT_BOUNDED";

    /** Bitstate size. */
    private final int bitstateSize;
    /** Bitstate k. */
    private final int bitstateK;
    /** Max preemptions. */
    private final int maxPreemptions;
    /** Iterative deepening. */
    private final boolean iterativeDeepening;
    /** Store filter. */
    private final Set<StoreType> storeFilter;
    /** Strategy filter. */
    private final Set<String> strategyFilter;

    /**
     * Creates a harness with default parameters (all stores, all strategies, CBS at K=2).
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
        this(bitstateSize, bitstateK, storeFilter, strategyFilter, DEFAULT_MAX_PREEMPTIONS, false);
    }

    /**
     * Creates a harness with full control over the context-bounded parameters.
     *
     * @param bitstateSize the bit-array size for BitstateStore
     * @param bitstateK the number of hash functions for BitstateStore
     * @param storeFilter the store types to run, or null for all
     * @param strategyFilter the strategies to run, or null for all
     * @param maxPreemptions the preemption bound for context-bounded search
     * @param iterativeDeepening whether to run increasing bounds and stop at the first failure
     */
    public BenchmarkHarness(int bitstateSize, int bitstateK,
                            Set<StoreType> storeFilter, Set<String> strategyFilter,
                            int maxPreemptions, boolean iterativeDeepening) {
        if (maxPreemptions < 0) {
            throw new IllegalArgumentException("maxPreemptions must not be negative");
        }
        this.bitstateSize = bitstateSize;
        this.bitstateK = bitstateK;
        this.storeFilter = storeFilter;
        this.strategyFilter = strategyFilter;
        this.maxPreemptions = maxPreemptions;
        this.iterativeDeepening = iterativeDeepening;
    }

    /**
     * Runs the complete benchmark suite for all programs in the corpus.
     * Returns up to 8 results per program: 4 strategies × 2 store types,
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
     * Produces up to 8 results: 4 strategies × {EXACT, BITSTATE},
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

        // The bitstate store must be able to represent the bound, or the explorer would reject the
        // run. Sizing it here means capacity equals the bound by construction.
        if (runBitstate) {
            results.addAll(runProgramWithStore(program, invariant,
                () -> new BitstateStore(bitstateSize, bitstateK, maxPreemptions), StoreType.BITSTATE));
        }

        return results;
    }

    /**
     * Runs all four strategies with the given state store factory and store type.
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
        boolean runCb = strategyFilter == null || strategyFilter.contains(CONTEXT_BOUNDED);

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
            results.add(createResult("DFS", program.name(), dfsResult, dfsVerdict, dfsFailing, storeType, dfsStore, null));
        }

        // STATIC_POR
        if (runPor) {
            StaticPorExplorer porExplorer = new StaticPorExplorer();
            StateStore porStore = storeFactory.get();
            DfsResultWithTiming porResult = runExplorer(() -> porExplorer.explore(program.program(), invariant, porStore, null));
            String porVerdict = actualVerdict(porResult.result());
            Trace porFailing = findFailingTrace(porResult.result());
            results.add(createResult("STATIC_POR", program.name(), porResult, porVerdict, porFailing, storeType, porStore, null));
        }

        // DPOR
        if (runDpor) {
            DporExplorer dporExplorer = new DporExplorer();
            StateStore dporStore = storeFactory.get();
            DfsResultWithTiming dporResult = runExplorer(() -> dporExplorer.explore(program.program(), invariant, dporStore, null));
            String dporVerdict = actualVerdict(dporResult.result());
            Trace dporFailing = findFailingTrace(dporResult.result());
            results.add(createResult("DPOR", program.name(), dporResult, dporVerdict, dporFailing, storeType, dporStore, null));
        }

        // CONTEXT_BOUNDED
        if (runCb) {
            CbRun cbRun = iterativeDeepening
                ? runCbsIterative(program.program(), invariant, storeFactory)
                : runCbsSingle(program.program(), invariant, storeFactory.get());

            DfsResultWithTiming result = cbRun.result();
            String verdict = cbVerdict(result.result(), storeType);
            Trace failing = findFailingTrace(result.result());
            // The store that actually produced this result, not the pre-branch one: under
            // iterative deepening the winning iteration used its own store, and the Bloom metrics
            // must describe that one rather than an unsearched instance.
            results.add(createResult(CONTEXT_BOUNDED, program.name(), result, verdict, failing,
                storeType, cbRun.store(), cbRun.preemptionsUsed()));
        }

        return results;
    }

    /**
     * Context-bounded exploration result paired with the bound used for the run.
     * @param result completed exploration result
     * @param store visited-position store for this run
     * @param preemptionsUsed preemption bound used, or null for an unbounded strategy
     */
    private record CbRun(DfsResultWithTiming result, StateStore store, Integer preemptionsUsed) {}

    /**
     * Runs the selected context-bounded policy with an isolated visited-position store.
     * @param program modeled program whose threads are explored
     * @param invariant property to check, or null when no property is supplied
     * @param store visited-position store for this run
     * @return context-bounded exploration result for the configured policy
     */
    private CbRun runCbsSingle(Program program, Invariant invariant, StateStore store) {
        ContextBoundedExplorer explorer = new ContextBoundedExplorer();
        DfsResultWithTiming result = runExplorer(
            () -> explorer.explore(program, invariant, store, null, maxPreemptions));
        return new CbRun(result, store, maxPreemptions);
    }

    /**
     * Runs increasing bounds, stopping at the first that fails, so the reported trace is the one
     * needing the fewest preemptions. Each bound gets a genuinely fresh store: a shared visited set
     * would make deepening narrow instead of widen, since states recorded at bound 0 satisfy
     * {@code min <= p} and would be pruned at bound 1. That is caught here, and here, because the
     * harness builds its own factory per store type.
     * @param program modeled program whose threads are explored
     * @param invariant property to check, or null when no property is supplied
     * @param storeFactory supplier returning a fresh store for each preemption bound
     * @return first failing or exhaustive exploration with its bound, or the final bounded result
     */
    private CbRun runCbsIterative(Program program, Invariant invariant,
                                  java.util.function.Supplier<StateStore> storeFactory) {
        CbRun last = null;
        Set<StateStore> seenStores = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int k = 0; k <= maxPreemptions; k++) {
            StateStore store = storeFactory.get();
            if (!seenStores.add(store)) {
                throw new IllegalStateException(
                    "Iterative deepening requires a fresh StateStore per bound, but storeFactory "
                    + "reused an instance. Iterations would share a visited set, so each deeper "
                    + "bound would prune states the previous bound already explored.");
            }

            ContextBoundedExplorer explorer = new ContextBoundedExplorer();
            int bound = k;
            DfsResultWithTiming result = runExplorer(
                () -> explorer.explore(program, invariant, store, null, bound));
            last = new CbRun(result, store, k);

            if (isFailure(result.result())) {
                return last;
            }
            // Only an INCOMPLETE trace justifies deepening: it proves the bound actually pruned
            // something. Without one the bound covered the whole reachable space, so every deeper
            // bound re-explores that same space and returns the same verdict. Each bound here
            // gets its own store and its own state budget, so this costs wall time, not verdicts
            // -- but the corpus runs dozens of CBS rows, and the redundant bounds are the bulk
            // of that time.
            boolean pruned = result.result().traces().stream()
                .anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE);
            if (!pruned) {
                return last; // exhaustive at k; deeper bounds are redundant
            }
        }
        return last;
    }

    /**
     * Reports whether exploration found a violation or deadlock.
     * @param result completed exploration result
     * @return true if a violation or deadlock was recorded
     */
    private static boolean isFailure(DfsResult result) {
        return result.traces().stream().anyMatch(t ->
            t.outcome() == TraceOutcome.VIOLATION || t.outcome() == TraceOutcome.DEADLOCK);
    }

    /**
     * Combines exploration outcomes and measured store diagnostics into a benchmark row.
     * @param strategy exploration strategy used for these results
     * @param bugName benchmark program name
     * @param result completed exploration result
     * @param verdict reported exploration verdict
     * @param failingTrace representative failure trace, or null when absent
     * @param storeType visited-store strategy represented by this row
     * @param store visited-position store for this run
     * @param preemptionsUsed preemption bound used, or null for an unbounded strategy
     * @return benchmark row preserving verdict, trace, measurements, and store diagnostics
     */
    private BenchmarkResult createResult(String strategy, String bugName,
                                          DfsResultWithTiming result, String verdict,
                                          Trace failingTrace, StoreType storeType,
                                          StateStore store, Integer preemptionsUsed) {
        if (store instanceof BitstateStore bs) {
            return new BenchmarkResult(strategy, bugName, result.result().statesExplored(),
                result.wallTimeMs(), result.heapDeltaBytes(), verdict,
                failingTrace, storeType,
                bs.estimatedFalsePositiveRate(), bs.bitCount(), bs.bitDensity(), preemptionsUsed);
        }
        return new BenchmarkResult(strategy, bugName, result.result().statesExplored(),
            result.wallTimeMs(), result.heapDeltaBytes(), verdict,
            failingTrace, storeType, 0.0, 0, 0.0, preemptionsUsed);
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
     * Derives the verdict for a context-bounded search.
     *
     * <p>Separate from {@link #actualVerdict(DfsResult)} on purpose. Two reasons:
     * <ul>
     *   <li>Bitstate DFS/STATIC_POR/DPOR rows keep reporting {@code PASS}. Changing the shared
     *       helper to relabel every approximate result would rewrite every existing report row and
     *       pull all of them into the {@code BenchmarkHarnessTest} attestation filter.</li>
     *   <li>A bitstate context-bounded run is genuinely inconclusive -- Bloom false positives can
     *       prune real states -- so calling it {@code PASS} would overstate what was proven.</li>
     * </ul>
     *
     * <p>The trade-off is deliberate: a report can show {@code APPROXIMATE_PASS} on context-bounded
     * bitstate rows while showing {@code PASS} on the other bitstate rows.
     *
     * @param result the exploration result
     * @param storeType the store type used
     * @return "VIOLATION", "DEADLOCK", "INCOMPLETE", or "APPROXIMATE_PASS"
     */
    private static String cbVerdict(DfsResult result, StoreType storeType) {
        if (result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION)) {
            return "VIOLATION";
        }
        if (result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK)) {
            return "DEADLOCK";
        }
        if (result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE)) {
            return "INCOMPLETE";
        }
        return storeType == StoreType.EXACT ? "PASS" : "APPROXIMATE_PASS";
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
     * @param result completed exploration result
     * @param wallTimeMs elapsed exploration time in milliseconds
     * @param heapDeltaBytes observed heap delta in bytes
     */
    private record DfsResultWithTiming(DfsResult result, long wallTimeMs, long heapDeltaBytes) {}
}
