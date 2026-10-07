/** Benchmark completeness and typed report output contracts. */
package dev.samhb.interleave.report;

import static dev.samhb.interleave.testsupport.JsonAssertions.parseObject;
import com.google.gson.JsonPrimitive;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkHarnessTest {


    @Test
    void benchmarkHarness_producesSameVerdictAcrossStrategies() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();

        // Deliberate filter, unchanged by context-bounded search. Its purpose is to exercise the
        // attestation over trace-producing rows only, and the point of leaving it alone is that
        // bitstate DFS/POR/DPOR verdicts must stay "PASS" -- cbVerdict was added as a separate
        // helper precisely so this assertion would keep holding.
        List<BenchmarkResult> traceProducingResults = results.stream()
            .filter(r -> r.failingTrace().isPresent() || !"PASS".equals(r.verdict()))
            .toList();

        SoundnessAttestation attestation = new SoundnessAttestation(traceProducingResults, BugCorpus.all());
        assertTrue(attestation.isSound(),
            "All trace-producing programs should be sound: " +
            (attestation.failureReason() != null ? attestation.failureReason() : ""));
    }

    @Test
    void benchmarkHarness_findsViolationsInBuggyPrograms() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();

        List<BenchmarkProgram> buggyPrograms = BugCorpus.all().stream()
            .filter(p -> "VIOLATION".equals(p.expectedVerdict()))
            .toList();

        assertFalse(buggyPrograms.isEmpty(), "Should have at least one buggy program");

        for (BenchmarkProgram program : buggyPrograms) {
            // Filter to EXACT store type for verdict validation
            List<BenchmarkResult> dfsResults = results.stream()
                .filter(r -> r.bugName().equals(program.name())
                    && "DFS".equals(r.strategy())
                    && r.storeType() == StoreType.EXACT)
                .toList();

            assertFalse(dfsResults.isEmpty(),
                program.name() + " should have DFS result");

            BenchmarkResult dfsResult = dfsResults.get(0);
            assertEquals(program.expectedVerdict(), dfsResult.verdict(),
                program.name() + " under DFS should have expected verdict " + program.expectedVerdict());
            assertTrue(dfsResult.failingTrace().isPresent(),
                program.name() + " under DFS should have a failing trace");
        }
    }

    @Test
    void runProgram_includesBothStoreTypes() {
        BenchmarkHarness harness = new BenchmarkHarness();
        BenchmarkProgram program = BugCorpus.all().get(0); // peterson
        List<BenchmarkResult> results = harness.runProgram(program);

        // Should have 8 results: 4 strategies x 2 store types
        assertEquals(8, results.size(), "Should have 8 results per program");

        // Check both store types are present for each strategy
        String[] strategies = {"DFS", "STATIC_POR", "DPOR", "CONTEXT_BOUNDED"};
        for (String strategy : strategies) {
            long exactCount = results.stream()
                .filter(r -> r.strategy().equals(strategy) && r.storeType() == StoreType.EXACT)
                .count();
            long bitstateCount = results.stream()
                .filter(r -> r.strategy().equals(strategy) && r.storeType() == StoreType.BITSTATE)
                .count();
            assertEquals(1, exactCount, "Should have exactly 1 EXACT result for " + strategy);
            assertEquals(1, bitstateCount, "Should have exactly 1 BITSTATE result for " + strategy);
        }
    }

    @Test
    void runAll_completesAllPrograms() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();

        int expectedPrograms = BugCorpus.all().size();
        assertEquals(expectedPrograms * 8, results.size(),
            "Should have 8 results per program (" + expectedPrograms + " programs)");

        // Verify all programs have both store types
        for (BenchmarkProgram program : BugCorpus.all()) {
            long programResults = results.stream()
                .filter(r -> r.bugName().equals(program.name()))
                .count();
            assertEquals(8, programResults, program.name() + " should have 8 results");

            // Check EXACT and BITSTATE both present
            assertTrue(results.stream()
                .filter(r -> r.bugName().equals(program.name()))
                .anyMatch(r -> r.storeType() == StoreType.EXACT),
                program.name() + " should have EXACT results");
            assertTrue(results.stream()
                .filter(r -> r.bugName().equals(program.name()))
                .anyMatch(r -> r.storeType() == StoreType.BITSTATE),
                program.name() + " should have BITSTATE results");
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

    /** Known report inputs must produce a complete, strictly parseable typed document. */
    @Test
    void reportWriter_producesValidJson() {
        var input = List.of(new BenchmarkResult("DFS", "peterson", 4, 7, 8, "PASS"),
            new BenchmarkResult("STATIC_POR", "peterson", 3, 5, 6, "PASS"));
        var json = parseObject(new ReportWriter(input).writeJson());
        assertEquals(new JsonPrimitive(true), json.get("soundness"));
        var rows = json.getAsJsonArray("benchmarks");
        assertEquals(2, rows.size());
        var dfs = rows.get(0).getAsJsonObject();
        assertEquals(new JsonPrimitive("peterson"), dfs.get("bug"));
        assertEquals(new JsonPrimitive("DFS"), dfs.get("strategy"));
        assertEquals(new JsonPrimitive("EXACT"), dfs.get("storeType"));
        assertEquals(new JsonPrimitive(4), dfs.get("statesExplored"));
        assertEquals(new JsonPrimitive(7), dfs.get("wallTimeMs"));
        assertEquals(new JsonPrimitive(8), dfs.get("heapDeltaBytes"));
        assertEquals(new JsonPrimitive("PASS"), dfs.get("verdict"));
        assertTrue(dfs.get("preemptionsUsed").isJsonNull());
        var por = rows.get(1).getAsJsonObject();
        assertEquals(new JsonPrimitive("STATIC_POR"), por.get("strategy"));
        assertEquals(new JsonPrimitive(3), por.get("statesExplored"));
        assertEquals(new JsonPrimitive(5), por.get("wallTimeMs"));
        assertEquals(new JsonPrimitive(6), por.get("heapDeltaBytes"));
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
        assertTrue(reduction.contains("DFS (exact): 100"));
        assertTrue(reduction.contains("STATIC_POR (exact): 50"));
        assertTrue(reduction.contains("DPOR (exact): 30"));
    }
}
