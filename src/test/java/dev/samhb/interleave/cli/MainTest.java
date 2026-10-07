/** CLI process validation and filtering plus typed report contracts. */
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
 * Tests invalid arguments through the entry point and report contracts through the harness.
 */
class MainTest {

    /** Verifies that the peterson program produces results. */
    @Test
    void petersonProgramRuns() {
        BenchmarkProgram program = findProgram("peterson");
        assertNotNull(program, "peterson should exist in corpus");

        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        assertFalse(results.isEmpty(), "should produce results");
        assertEquals(8, results.size(), "should produce 8 results (4 strategies × 2 stores)");
    }

    /** Verifies that --all flag runs the entire corpus. */
    @Test
    void allFlagRunsFullCorpus() throws Exception {
        CliResult result = invokeCli("--all", "--json", "--store", "exact", "--strategy", "DFS");
        assertEquals(0, result.exitCode(), result.output());
        var rows = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output()).getAsJsonArray("benchmarks");
        var names = new java.util.HashSet<String>();
        for (var element : rows) {
            var row = element.getAsJsonObject();
            assertEquals("EXACT", row.get("storeType").getAsString());
            assertEquals("DFS", row.get("strategy").getAsString());
            assertTrue(names.add(row.get("bug").getAsString()));
        }
        assertEquals(java.util.Set.of("peterson", "broken-peterson", "broken-peterson-v2", "lost-update",
            "deadlock", "double-checked-locking", "torn-counter", "lost-update-3t"), names);
    }

    /** Verifies that --store exact filter works correctly. */
    @Test
    void storeFilterExact() throws Exception {
        CliResult result = invokeCli("peterson", "--json", "--store", "exact");
        assertEquals(0, result.exitCode(), result.output());
        var rows = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output()).getAsJsonArray("benchmarks");
        assertEquals(4, rows.size());
        for (var row : rows) assertEquals("EXACT", row.getAsJsonObject().get("storeType").getAsString());
    }

    /** Verifies that --store bitstate filter works correctly. */
    @Test
    void storeFilterBitstate() throws Exception {
        CliResult result = invokeCli("peterson", "--json", "--store", "bitstate");
        assertEquals(0, result.exitCode(), result.output());
        var rows = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output()).getAsJsonArray("benchmarks");
        assertEquals(4, rows.size());
        for (var row : rows) assertEquals("BITSTATE", row.getAsJsonObject().get("storeType").getAsString());
    }

    /** Verifies that --strategy DFS filter works correctly. */
    @Test
    void strategyFilterDfs() throws Exception {
        CliResult result = invokeCli("peterson", "--json", "--strategy", "DFS");
        assertEquals(0, result.exitCode(), result.output());
        var rows = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output()).getAsJsonArray("benchmarks");
        assertEquals(2, rows.size());
        for (var row : rows) assertEquals("DFS", row.getAsJsonObject().get("strategy").getAsString());
    }

    /** Verifies that combining store and strategy filters works correctly. */
    @Test
    void combinedFilterStoreAndStrategy() throws Exception {
        CliResult result = invokeCli("peterson", "--json", "--store", "exact", "--strategy", "CONTEXT_BOUNDED");
        assertEquals(0, result.exitCode(), result.output());
        var rows = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output()).getAsJsonArray("benchmarks");
        assertEquals(1, rows.size());
        var row = rows.get(0).getAsJsonObject();
        assertEquals("EXACT", row.get("storeType").getAsString());
        assertEquals("CONTEXT_BOUNDED", row.get("strategy").getAsString());
        assertEquals(new com.google.gson.JsonPrimitive(2), row.get("preemptionsUsed"));
    }

    /** Verifies that JSON output contains bitstate metrics. */
    @Test
    void jsonReportContainsBitstateMetrics() {
        var rows = new BenchmarkHarness().runProgram(findProgram("peterson"));
        var jsonRows = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(new ReportWriter(rows).writeJson()).getAsJsonArray("benchmarks");
        assertEquals(rows.size(), jsonRows.size());
        for (int i = 0; i < rows.size(); i++) {
            var emitted = jsonRows.get(i).getAsJsonObject();
            var source = rows.get(i);
            if (source.storeType() == StoreType.BITSTATE) {
                var metrics = emitted.getAsJsonObject("bitstateMetrics");
                assertTrue(metrics.get("falsePositiveRate").getAsJsonPrimitive().isNumber());
                assertTrue(metrics.get("bitDensity").getAsJsonPrimitive().isNumber());
                assertEquals(source.estimatedFalsePositiveRate(), metrics.get("falsePositiveRate").getAsDouble(), 0.000001);
                assertEquals(source.bitstateBitDensity(), metrics.get("bitDensity").getAsDouble(), 0.000001);
            } else {
                assertFalse(emitted.has("bitstateMetrics"));
            }
        }
    }

    /** Verifies that JSON output contains failing traces for violations. */
    @Test
    void jsonReportContainsFailingTrace() {
        var rows = new BenchmarkHarness().runProgram(findProgram("broken-peterson"));
        var parsed = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(new ReportWriter(rows).writeJson()).getAsJsonArray("benchmarks");
        assertFalse(rows.isEmpty());
        for (int i = 0; i < rows.size(); i++) {
            var source = rows.get(i).failingTrace().orElseThrow();
            var trace = parsed.get(i).getAsJsonObject().getAsJsonObject("failingTrace");
            assertEquals(source.outcome().name(), parsed.get(i).getAsJsonObject().get("verdict").getAsString());
            var ids = trace.getAsJsonArray("threadIds");
            var outcomes = trace.getAsJsonArray("outcomes");
            assertEquals(source.length(), ids.size());
            assertEquals(source.length(), outcomes.size());
            for (int j = 0; j < source.length(); j++) {
                assertEquals(source.threadIds().get(j).intValue(), ids.get(j).getAsInt());
                assertEquals(source.outcomes().get(j).name(), outcomes.get(j).getAsString());
            }
        }
    }

    /** Verifies that Markdown reports include bitstate summary section. */
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

    /** Verifies that reduction table shows percentage reductions. */
    @Test
    void reductionTableShowsPercentages() {
        BenchmarkProgram program = findProgram("peterson");
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> results = harness.runProgram(program);

        dev.samhb.interleave.report.StatesExploredTable table =
            new dev.samhb.interleave.report.StatesExploredTable(results);

        String reductionTable = table.formatReductionTable();
        // POR strategies should show reduction vs DFS
        assertTrue(reductionTable.contains("DFS (exact):"), "Reduction table should show DFS baseline");
        assertTrue(reductionTable.contains("%↓"), "Reduction table should show POR reduction percentages");
    }

    /** Verifies that invalid store flag values are rejected. */
    @Test
    void invalidStoreFlagRejected() throws Exception {
        for (String value : List.of("HASH","BLOOM","INVALID")) {
            CliResult result = invokeCli("peterson", "--store", value);
            assertEquals(1, result.exitCode());
            assertEquals("Error: --store must be 'exact' or 'bitstate'\n", result.output().replace("\r\n", "\n"));
        }
    }

    /** Verifies that invalid strategy flag values are rejected. */
    @Test
    void invalidStrategyFlagRejected() throws Exception {
        for (String value : List.of("RANDOM","BFS","INVALID")) {
            CliResult result = invokeCli("peterson", "--strategy", value);
            assertEquals(1, result.exitCode());
            assertEquals("Error: --strategy must be 'DFS', 'STATIC_POR', 'DPOR', or 'CONTEXT_BOUNDED'\n", result.output().replace("\r\n", "\n"));
        }
    }

    /** Verifies that bitstate results have populated metrics. */
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

    /** Verifies that exact results have zero bitstate metrics. */
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

    /** Verifies that harness works with custom bitstate parameters. */
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
    /** Runs the actual entry point in a child JVM because validation calls System.exit.
     * @param arguments CLI arguments
     * @return exit status and merged output
     * @throws Exception if launch, resource lookup, or waiting fails
     */
    private static CliResult invokeCli(String... arguments) throws Exception {
        String separator = java.io.File.pathSeparator;
        String classpath = java.nio.file.Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            + separator + java.nio.file.Path.of(com.google.gson.Gson.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            + separator + java.nio.file.Path.of(java.util.Objects.requireNonNull(
                Main.class.getResource("/programs/peterson.json"), "corpus resource must exist").toURI()).getParent().getParent();
        List<String> command = new java.util.ArrayList<>(List.of(
            java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", classpath, Main.class.getName()));
        command.addAll(List.of(arguments));
        java.nio.file.Path output = java.nio.file.Files.createTempFile("interleave-cli-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS), "CLI must terminate");
            return new CliResult(process.exitValue(), java.nio.file.Files.readString(output));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            java.nio.file.Files.deleteIfExists(output);
        }
    }

    /** Observed process result, with stderr merged into stdout. */
    private record CliResult(int exitCode, String output) {}
}
