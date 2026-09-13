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

public final class BenchmarkHarness {
    private static final int BITSTATE_SIZE = 1_000_003;
    private static final int BITSTATE_K = 4;

    public List<BenchmarkResult> runAll() {
        List<BenchmarkResult> results = new ArrayList<>();
        
        for (BenchmarkProgram program : BugCorpus.all()) {
            results.addAll(runProgram(program));
        }
        
        return results;
    }

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

    private List<BenchmarkResult> runProgramWithStore(BenchmarkProgram program, Invariant invariant,
                                                       java.util.function.Supplier<StateStore> storeFactory,
                                                       StoreType storeType) {
        List<BenchmarkResult> results = new ArrayList<>();
        
        // DFS
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResultWithTiming dfsResult = runExplorer(() -> dfsExplorer.explore(program.program(), invariant, storeFactory.get(), null));
        String dfsVerdict = actualVerdict(dfsResult.result());
        // Only validate verdict for exact store type
        if (storeType == StoreType.EXACT && !program.expectedVerdict().equals(dfsVerdict)) {
            throw new IllegalStateException("Expected verdict " + program.expectedVerdict() + 
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

    private DfsResultWithTiming runExplorer(java.util.function.Supplier<DfsResult> explorer) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        DfsResult result = explorer.get();
        
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long peakMemory = Math.max(0, memAfter - memBefore);
        
        return new DfsResultWithTiming(result, wallTime, peakMemory);
    }

    private static String actualVerdict(DfsResult result) {
        boolean hasViolation = result.traces().stream()
            .anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
        boolean hasDeadlock = result.traces().stream()
            .anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK);
        
        if (hasViolation) return "VIOLATION";
        if (hasDeadlock) return "DEADLOCK";
        return "PASS";
    }

    private static Trace findFailingTrace(DfsResult result) {
        return result.traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.VIOLATION)
            .findFirst()
            .orElse(null);
    }

    // Helper record to carry timing info from explorer run
    private record DfsResultWithTiming(DfsResult result, long wallTimeMs, long heapDeltaBytes) {}
}