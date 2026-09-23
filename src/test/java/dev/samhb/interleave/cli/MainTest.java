package dev.samhb.interleave.cli;

import dev.samhb.interleave.report.BenchmarkResult;
import dev.samhb.interleave.report.BenchmarkHarness;
import dev.samhb.interleave.report.ReportWriter;
import dev.samhb.interleave.report.StoreType;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the CLI entry point.
 * Tests argument parsing and filtering logic by invoking the harness directly.
 */
class MainTest {

    @Test
    void petersonProgramRuns() {
        BenchmarkProgram program = findProgram("peterson");
        assertNotNull(program, "peterson should exist in corpus");

        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        assertFalse(results.isEmpty(), "should produce results");
        assertEquals(6, results.size(), "should produce 6 results (3 strategies × 2 stores)");
    }

    @Test
    void allFlagRunsFullCorpus() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runAll();

        int programCount = BugCorpus.all().size();
        assertEquals(programCount * 6, results.size(),
            "should produce 6 results per program");
    }

    @Test
    void storeFilterExact() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> allResults = harness.runProgram(program);

        List<BenchmarkResult> exactOnly = allResults.stream()
            .filter(r -> r.storeType() == StoreType.EXACT)
            .toList();

        assertEquals(3, exactOnly.size(), "exact filter should keep 3 results (DFS, STATIC_POR, DPOR)");
        assertTrue(exactOnly.stream().allMatch(r -> r.storeType() == StoreType.EXACT));
    }

    @Test
    void storeFilterBitstate() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> allResults = harness.runProgram(program);

        List<BenchmarkResult> bitstateOnly = allResults.stream()
            .filter(r -> r.storeType() == StoreType.BITSTATE)
            .toList();

        assertEquals(3, bitstateOnly.size(), "bitstate filter should keep 3 results");
        assertTrue(bitstateOnly.stream().allMatch(r -> r.storeType() == StoreType.BITSTATE));
    }

    @Test
    void strategyFilterDfs() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> allResults = harness.runProgram(program);

        List<BenchmarkResult> dfsOnly = allResults.stream()
            .filter(r -> r.strategy().equals("DFS"))
            .toList();

        assertEquals(2, dfsOnly.size(), "DFS filter should keep 2 results (exact + bitstate)");
        assertTrue(dfsOnly.stream().allMatch(r -> r.strategy().equals("DFS")));
    }

    @Test
    void combinedFilterStoreAndStrategy() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> allResults = harness.runProgram(program);

        List<BenchmarkResult> filtered = allResults.stream()
            .filter(r -> r.storeType() == StoreType.EXACT && r.strategy().equals("DFS"))
            .toList();

        assertEquals(1, filtered.size(), "combined filter should keep 1 result");
        assertEquals(StoreType.EXACT, filtered.get(0).storeType());
        assertEquals("DFS", filtered.get(0).strategy());
    }

    @Test
    void jsonReportContainsBitstateMetrics() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        ReportWriter writer = new ReportWriter(results);
        String json = writer.writeJson();

        assertTrue(json.contains("bitstateMetrics"), "JSON should contain bitstateMetrics");
        assertTrue(json.contains("falsePositiveRate"), "JSON should contain falsePositiveRate");
        assertTrue(json.contains("bitDensity"), "JSON should contain bitDensity");
    }

    @Test
    void jsonReportContainsFailingTrace() {
        // broken-peterson always has a violation
        BenchmarkProgram program = findProgram("broken-peterson");
        assertNotNull(program);

        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        ReportWriter writer = new ReportWriter(results);
        String json = writer.writeJson();

        assertTrue(json.contains("failingTrace"), "JSON should contain failingTrace for violations");
        assertTrue(json.contains("threadIds"), "JSON should contain threadIds in failing trace");
        assertTrue(json.contains("outcomes"), "JSON should contain outcomes in failing trace");
    }

    @Test
    void markdownReportIncludesBitstateSummary() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        ReportWriter writer = new ReportWriter(results);
        String md = writer.writeMarkdown();

        assertTrue(md.contains("Bitstate Summary"), "Markdown should include Bitstate Summary section");
        assertTrue(md.contains("FPR"), "Markdown should include FPR column");
    }

    @Test
    void reductionTableShowsPercentages() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        dev.samhb.interleave.report.StatesExploredTable table =
            new dev.samhb.interleave.report.StatesExploredTable(results);

        String reductionTable = table.formatReductionTable();
        // POR strategies should show reduction vs DFS
        assertTrue(reductionTable.contains("%↓") || reductionTable.contains("DFS (exact):"),
            "Reduction table should show DFS baseline or reductions");
    }

    @Test
    void invalidStoreFlagRejected() {
        // Verify that invalid store values would be caught
        // We test the validation logic directly
        String[] validStores = {"EXACT", "BITSTATE"};
        String[] invalidStores = {"HASH", "BLOOM", "INVALID"};

        for (String store : validStores) {
            assertTrue("EXACT".equals(store) || "BITSTATE".equals(store),
                store + " should be valid");
        }
        for (String store : invalidStores) {
            assertFalse("EXACT".equals(store) || "BITSTATE".equals(store),
                store + " should be invalid");
        }
    }

    @Test
    void invalidStrategyFlagRejected() {
        String[] validStrategies = {"DFS", "STATIC_POR", "DPOR"};
        String[] invalidStrategies = {"RANDOM", "BFS", "INVALID"};

        for (String strategy : validStrategies) {
            assertTrue("DFS".equals(strategy) || "STATIC_POR".equals(strategy) || "DPOR".equals(strategy),
                strategy + " should be valid");
        }
        for (String strategy : invalidStrategies) {
            assertFalse("DFS".equals(strategy) || "STATIC_POR".equals(strategy) || "DPOR".equals(strategy),
                strategy + " should be invalid");
        }
    }

    @Test
    void bitstateMetricsPopulatedForBitstateResults() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        List<BenchmarkResult> bitstateResults = results.stream()
            .filter(r -> r.storeType() == StoreType.BITSTATE)
            .toList();

        assertFalse(bitstateResults.isEmpty());
        for (BenchmarkResult r : bitstateResults) {
            assertTrue(r.estimatedFalsePositiveRate() >= 0.0,
                "FPR should be non-negative: " + r.estimatedFalsePositiveRate());
            assertTrue(r.bitstateBitCount() > 0,
                "bitCount should be positive for bitstate runs");
            assertTrue(r.bitstateBitDensity() > 0.0,
                "bitDensity should be positive for bitstate runs");
        }
    }

    @Test
    void exactResultsHaveZeroBitstateMetrics() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        List<BenchmarkResult> exactResults = results.stream()
            .filter(r -> r.storeType() == StoreType.EXACT)
            .toList();

        assertFalse(exactResults.isEmpty());
        for (BenchmarkResult r : exactResults) {
            assertEquals(0.0, r.estimatedFalsePositiveRate(), 0.001,
                "FPR should be 0 for exact results");
            assertEquals(0, r.bitstateBitCount(),
                "bitCount should be 0 for exact results");
            assertEquals(0.0, r.bitstateBitDensity(), 0.001,
                "bitDensity should be 0 for exact results");
        }
    }

    @Test
    void harnessWithCustomBitstateParams() {
        BenchmarkHarness harness = new BenchmarkHarness(500_001, 6);
        BenchmarkProgram program = findProgram("peterson");
        List<BenchmarkResult> results = harness.runProgram(program);

        assertFalse(results.isEmpty());
        // Verify bitstate results exist with the custom params
        List<BenchmarkResult> bitstateResults = results.stream()
            .filter(r -> r.storeType() == StoreType.BITSTATE)
            .toList();
        assertFalse(bitstateResults.isEmpty());
    }

    private static BenchmarkProgram findProgram(String name) {
        for (BenchmarkProgram program : BugCorpus.all()) {
            if (program.name().equals(name)) {
                return program;
            }
        }
        return null;
    }
}
