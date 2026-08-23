package dev.samhb.modelcheck.report;

import dev.samhb.modelcheck.bugs.BenchmarkProgram;
import dev.samhb.modelcheck.bugs.BugCorpus;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkHarnessTest {

    @Test
    void benchmarkHarness_runsAllPrograms() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();
        
        assertTrue(results.size() >= 3, "Should have at least 3 results (1 bug x 3 strategies)");
    }

    @Test
    void benchmarkHarness_producesSameVerdictAcrossStrategies() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();
        
        SoundnessAttestation attestation = new SoundnessAttestation(results);
        assertTrue(attestation.isSound(), "All strategies should agree on verdict");
    }

    @Test
    void reportWriter_producesValidMarkdown() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();
        
        ReportWriter writer = new ReportWriter(results);
        String markdown = writer.writeMarkdown();
        
        assertTrue(markdown.contains("interleave Benchmark Report"));
        assertTrue(markdown.contains("States Explored Reduction Table"));
    }

    @Test
    void reportWriter_producesValidJson() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();
        
        ReportWriter writer = new ReportWriter(results);
        String json = writer.writeJson();
        
        assertTrue(json.contains("\"benchmarks\""));
        assertTrue(json.contains("\"soundness\""));
    }

    @Test
    void statesExploredTable_formatsCorrectly() {
        List<BenchmarkResult> results = List.of(
            new BenchmarkResult("DFS", "peterson", 100, 10, 1024, "PASS"),
            new BenchmarkResult("STATIC_POR", "peterson", 50, 5, 512, "PASS"),
            new BenchmarkResult("DPOR", "peterson", 30, 3, 256, "PASS")
        );
        
        StatesExploredTable table = new StatesExploredTable(results);
        String markdown = table.formatMarkdown();
        String reduction = table.formatReductionTable();
        
        assertTrue(markdown.contains("peterson"));
        assertTrue(reduction.contains("NAIVE: 100"));
        assertTrue(reduction.contains("STATIC_POR: 50"));
        assertTrue(reduction.contains("DPOR: 30"));
    }
}
