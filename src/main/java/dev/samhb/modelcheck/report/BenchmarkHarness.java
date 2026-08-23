package dev.samhb.modelcheck.report;

import dev.samhb.modelcheck.bugs.BenchmarkProgram;
import dev.samhb.modelcheck.bugs.BugCorpus;
import dev.samhb.modelcheck.core.*;
import dev.samhb.modelcheck.dpor.DporExplorer;
import dev.samhb.modelcheck.por.StaticPorExplorer;
import dev.samhb.modelcheck.search.DfsExplorer;
import dev.samhb.modelcheck.search.DfsResult;
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
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        DfsResult dfsResult = dfsExplorer.explore(program.program());
        
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long peakMemory = Math.max(0, memAfter - memBefore);
        
        results.add(new BenchmarkResult("DFS", program.name(), dfsResult.statesExplored(), 
                                        wallTime, peakMemory, program.expectedVerdict()));
        
        StaticPorExplorer porExplorer = new StaticPorExplorer();
        start = System.currentTimeMillis();
        runtime.gc();
        memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        DfsResult porResult = porExplorer.explore(program.program());
        
        memAfter = runtime.totalMemory() - runtime.freeMemory();
        wallTime = System.currentTimeMillis() - start;
        peakMemory = Math.max(0, memAfter - memBefore);
        
        results.add(new BenchmarkResult("STATIC_POR", program.name(), porResult.statesExplored(), 
                                        wallTime, peakMemory, program.expectedVerdict()));
        
        DporExplorer dporExplorer = new DporExplorer();
        start = System.currentTimeMillis();
        runtime.gc();
        memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        DfsResult dporResult = dporExplorer.explore(program.program());
        
        memAfter = runtime.totalMemory() - runtime.freeMemory();
        wallTime = System.currentTimeMillis() - start;
        peakMemory = Math.max(0, memAfter - memBefore);
        
        results.add(new BenchmarkResult("DPOR", program.name(), dporResult.statesExplored(), 
                                        wallTime, peakMemory, program.expectedVerdict()));
        
        return results;
    }
}
