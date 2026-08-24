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
import java.util.*;

public final class BenchmarkHarness {
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
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        DfsResult dfsResult = dfsExplorer.explore(program.program(), invariant);
        
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long peakMemory = Math.max(0, memAfter - memBefore);
        
        String dfsVerdict = actualVerdict(dfsResult);
        Trace dfsFailing = findFailingTrace(dfsResult);
        results.add(new BenchmarkResult("DFS", program.name(), dfsResult.statesExplored(), 
                                        wallTime, peakMemory, dfsVerdict,
                                        dfsFailing, invariant));
        
        StaticPorExplorer porExplorer = new StaticPorExplorer();
        start = System.currentTimeMillis();
        runtime.gc();
        memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        DfsResult porResult = porExplorer.explore(program.program(), invariant);
        
        memAfter = runtime.totalMemory() - runtime.freeMemory();
        wallTime = System.currentTimeMillis() - start;
        peakMemory = Math.max(0, memAfter - memBefore);
        
        String porVerdict = actualVerdict(porResult);
        Trace porFailing = findFailingTrace(porResult);
        results.add(new BenchmarkResult("STATIC_POR", program.name(), porResult.statesExplored(), 
                                        wallTime, peakMemory, porVerdict,
                                        porFailing, invariant));
        
        DporExplorer dporExplorer = new DporExplorer();
        start = System.currentTimeMillis();
        runtime.gc();
        memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        DfsResult dporResult = dporExplorer.explore(program.program(), invariant);
        
        memAfter = runtime.totalMemory() - runtime.freeMemory();
        wallTime = System.currentTimeMillis() - start;
        peakMemory = Math.max(0, memAfter - memBefore);
        
        String dporVerdict = actualVerdict(dporResult);
        Trace dporFailing = findFailingTrace(dporResult);
        results.add(new BenchmarkResult("DPOR", program.name(), dporResult.statesExplored(), 
                                        wallTime, peakMemory, dporVerdict,
                                        dporFailing, invariant));
        
        return results;
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
}
