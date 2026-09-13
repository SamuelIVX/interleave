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
    private static final int BITSTATE_SIZE = 1_000_003;
    private static final int BITSTATE_K = 4;

    /**
     * Runs the complete benchmark suite for all programs in the corpus.
     * Returns 6 results per program: 3 strategies × 2 store types (EXACT, BITSTATE).
     *
     * @return list of all {@link BenchmarkResult}s (42 for the default corpus)
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
     * Produces 6 results: DFS, STATIC_POR, DPOR × {EXACT, BITSTATE}.
     *
     * @param program the benchmark program
     * @return list of 6 {@link BenchmarkResult}s
     */
    public List<BenchmarkResult> runProgram(BenchmarkProgram program) {
        List<BenchmarkResult> results = new ArrayList<>();
        Invariant invariant = program.invariant().orElse(null);

        // Run with exact state store (HashingStateStore)
        results.addAll(runProgramWithStore(program, invariant, HashingStateStore::new, StoreType.EXACT));

        // Run with bitstate store (BitstateStore)
        results.addAll(runProgramWithStore(program, invariant,
            () -> new BitstateStore(BITSTATE_SIZE, BITSTATE_K), StoreType.BITSTATE));

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
     * @return list of 3 results (one per strategy)
     */
    private List<BenchmarkResult> runProgramWithStore(BenchmarkProgram program, Invariant invariant,
                                                       java.util.function.Supplier<StateStore> storeFactory,
                                                       StoreType storeType) {
        List<BenchmarkResult> results = new ArrayList<>();

        // DFS
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResultWithTiming dfsResult = runExplorer(() -> dfsExplorer.explore(program.program(), invariant, storeFactory.get(), null));
        String dfsVerdict = actualVerdict(dfsResult.result());
        // Only validate verdict for exact store type when expected verdict is provided
        String expectedVerdict = program.expectedVerdict();
        if (expectedVerdict != null && storeType == StoreType.EXACT && !expectedVerdict.equals(dfsVerdict)) {
            throw new IllegalStateException("Expected verdict " + expectedVerdict +
                " for " + program.name() + " but got " + dfsVerdict);
        }
        Trace dfsFailing = findFailingTrace(dfsResult.result());
        results.add(new BenchmarkResult("DFS", program.name(), dfsResult.result().statesExplored(),
                                        dfsResult.wallTimeMs(), dfsResult.heapDeltaBytes(),
                                        dfsVerdict, dfsFailing, storeType));

        // STATIC_POR
        StaticPorExplorer porExplorer = new StaticPorExplorer();
        DfsResultWithTiming porResult = runExplorer(() -> porExplorer.explore(program.program(), invariant, storeFactory.get(), null));
        String porVerdict = actualVerdict(porResult.result());
        Trace porFailing = findFailingTrace(porResult.result());
        results.add(new BenchmarkResult("STATIC_POR", program.name(), porResult.result().statesExplored(),
                                        porResult.wallTimeMs(), porResult.heapDeltaBytes(),
                                        porVerdict, porFailing, storeType));

        // DPOR
        DporExplorer dporExplorer = new DporExplorer();
        DfsResultWithTiming dporResult = runExplorer(() -> dporExplorer.explore(program.program(), invariant, storeFactory.get(), null));
        String dporVerdict = actualVerdict(dporResult.result());
        Trace dporFailing = findFailingTrace(dporResult.result());
        results.add(new BenchmarkResult("DPOR", program.name(), dporResult.result().statesExplored(),
                                        dporResult.wallTimeMs(), dporResult.heapDeltaBytes(),
                                        dporVerdict, dporFailing, storeType));

        return results;
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