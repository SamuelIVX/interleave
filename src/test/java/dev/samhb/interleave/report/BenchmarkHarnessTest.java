package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
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
        
        List<BenchmarkResult> correctPrograms = results.stream()
            .filter(r -> "PASS".equals(r.verdict()) || "DEADLOCK".equals(r.verdict()))
            .toList();
        
        SoundnessAttestation attestation = new SoundnessAttestation(correctPrograms);
        assertTrue(attestation.isSound(), 
            "Correct programs should produce identical verdicts across strategies");
    }

    @Test
    void benchmarkHarness_findsViolationsInBuggyPrograms() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();
        
        List<BenchmarkResult> buggyPrograms = results.stream()
            .filter(r -> "VIOLATION".equals(r.verdict()))
            .toList();
        
        assertFalse(buggyPrograms.isEmpty(), "Should find violations in buggy programs");
        
        for (BenchmarkResult result : buggyPrograms) {
            assertTrue(result.failingTrace().isPresent(),
                result.bugName() + " under " + result.strategy() + " should have a failing trace");
        }
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
        assertTrue(reduction.contains("DFS: 100"));
        assertTrue(reduction.contains("STATIC_POR: 50"));
        assertTrue(reduction.contains("DPOR: 30"));
    }
}
