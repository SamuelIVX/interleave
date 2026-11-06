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
    /** Source CLI reports boundedness without treating the declared expectation as a proof. */
    @Test
    void sourceSelectorProducesRealReportAndRejectsInvalidSelections(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path source = directory.resolve("model.ilv");
        java.nio.file.Files.writeString(source, "program cli { shared int counter = 0; thread 0 { while (true) limit 0 {} } expected PASS; }");
        CliResult result = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS");
        assertEquals(0, result.exitCode(), result.output());
        var json = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output());
        var row = json.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals("INCOMPLETE", row.get("verdict").getAsString());
        assertEquals("LOOP_LIMIT", row.getAsJsonObject("incompleteTrace").getAsJsonObject("cutoff").get("reason").getAsString());
        assertTrue(json.get("soundness").getAsBoolean());
        assertEquals(1, invokeCli("--source").exitCode());
        assertEquals(1, invokeCli("--source", source.toString(), "--all").exitCode());
        java.nio.file.Files.writeString(source, "program broken {");
        CliResult bad = invokeCli("--source", source.toString());
        assertEquals(1, bad.exitCode());
        assertTrue(bad.output().contains("syntax"), bad.output());
    }

    /** The explicit operation emits a direct, viewer-compatible incomplete trace with fresh snapshots.
     * Writes build/source-simplified-cutoff.json for the browser integration suite.
     * @param directory temporary source files
     * @throws Exception if process execution or fixture I/O fails
     */
    @Test
    void cutoffSimplificationProducesDirectTrace(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var source = directory.resolve("noise.ilv");
        java.nio.file.Files.writeString(source, "program noise { shared int noise = 0; thread 0 { repeat 4 { atomic { noise = noise + 1; } } } thread 1 { atomic { while (true) limit 0 {} } } }");
        CliResult result = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS", "--simplify-cutoff");
        assertEquals(0, result.exitCode(), result.output());
        var json = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output());
        assertEquals("INCOMPLETE", json.get("outcome").getAsString());
        assertEquals(0, json.getAsJsonArray("threads").size());
        assertEquals(1, json.getAsJsonArray("states").size());
        assertEquals(0, json.getAsJsonArray("states").get(0).getAsJsonObject().getAsJsonObject("fields").get("noise").getAsInt());
        assertEquals("LOOP_LIMIT", json.getAsJsonObject("cutoff").get("reason").getAsString());
        assertEquals(0, json.getAsJsonObject("cutoff").get("limit").getAsInt());
        assertEquals(1, json.getAsJsonObject("cutoff").get("threadId").getAsInt());
        assertFalse(json.has("benchmarks"));
        var artifact = java.nio.file.Path.of("build/source-simplified-cutoff.json");
        java.nio.file.Files.createDirectories(artifact.getParent());
        java.nio.file.Files.writeString(artifact, result.output());
        var original = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS");
        var report = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(original.output());
        assertEquals(4, report.getAsJsonArray("benchmarks").get(0).getAsJsonObject().getAsJsonObject("incompleteTrace").getAsJsonArray("threads").size());
    }

    /** A scheduling cutoff preserves K and a live last-owner choice instead of producing an empty trace.
     * @param directory temporary source files
     * @throws Exception if process execution or fixture I/O fails
     */
    @Test
    void cutoffSimplificationPreservesPreemptionBound(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var source = directory.resolve("switches.ilv");
        java.nio.file.Files.writeString(source, "program switches { shared int noise = 0; thread 0 { repeat 4 { atomic {} } } thread 1 { repeat 4 { atomic {} } } }");
        CliResult result = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "CONTEXT_BOUNDED", "--max-preemptions", "0", "--simplify-cutoff");
        assertEquals(0, result.exitCode(), result.output());
        var json = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output());
        assertEquals("INCOMPLETE", json.get("outcome").getAsString());
        assertEquals(1, json.getAsJsonArray("threads").size());
        assertEquals(2, json.getAsJsonArray("states").size());
        assertEquals("PREEMPTION_BOUND", json.getAsJsonObject("cutoff").get("reason").getAsString());
        assertEquals(0, json.getAsJsonObject("cutoff").get("limit").getAsInt());
    }

    /** Rejects ambiguous/non-source selections and runs with no usable incomplete witness.
     * @param directory temporary source files
     * @throws Exception if process execution or fixture I/O fails
     */
    @Test
    void cutoffSimplificationRejectsInvalidSelections(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var source = directory.resolve("complete.ilv");
        java.nio.file.Files.writeString(source, "program complete { shared int x = 0; thread 0 { atomic {} } }");
        for (String[] args : List.of(
                new String[]{"--source", source.toString(), "--store", "exact", "--strategy", "DFS", "--simplify-cutoff"},
                new String[]{"--source", source.toString(), "--json", "--strategy", "DFS", "--simplify-cutoff"},
                new String[]{"--source", source.toString(), "--json", "--store", "exact", "--simplify-cutoff"},
                new String[]{"--source", source.toString(), "--json", "--store", "bitstate", "--strategy", "DFS", "--simplify-cutoff"},
                new String[]{"peterson", "--json", "--store", "exact", "--strategy", "DFS", "--simplify-cutoff"},
                new String[]{"--all", "--json", "--store", "exact", "--strategy", "DFS", "--simplify-cutoff"})) {
            CliResult result = invokeCli(args);
            assertEquals(1, result.exitCode());
            assertTrue(result.output().contains("--simplify-cutoff requires"), result.output());
        }
        CliResult absent = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS", "--simplify-cutoff");
        assertEquals(1, absent.exitCode());
        assertTrue(absent.output().contains("INCOMPLETE selected run with cutoff evidence"), absent.output());
        java.nio.file.Files.writeString(source, "program mixed { shared int x = 0; thread 0 { assert (false); } thread 1 { while (true) limit 0 {} } }");
        CliResult mixed = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS", "--simplify-cutoff");
        assertEquals(1, mixed.exitCode());
        assertTrue(mixed.output().contains("INCOMPLETE selected run with cutoff evidence"), mixed.output());
    }

    /** Exercises the real CLI before/after and writes build/source-symmetry-visualizer.json for browser CI.
     * @throws Exception if child JVM execution or artifact writing fails
     */
    @Test void sourceSymmetryCliCountsAndReportIdentity() throws Exception {
        String source = "examples/programs/symmetry-counter.ilv";
        var ordinary = invokeCli("--source", source, "--store", "exact", "--strategy", "DFS", "--json");
        var reduced = invokeCli("--source", source, "--store", "exact", "--strategy", "DFS", "--symmetry", "--json");
        assertEquals(0, ordinary.exitCode(), ordinary.output());
        assertEquals(0, reduced.exitCode(), reduced.output());
        var before = com.google.gson.JsonParser.parseString(ordinary.output()).getAsJsonObject();
        var after = com.google.gson.JsonParser.parseString(reduced.output()).getAsJsonObject();
        var oldRow = before.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        var row = after.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals(16, oldRow.get("statesExplored").getAsInt());
        assertFalse(oldRow.has("symmetryReduction"));
        assertEquals(5, row.get("statesExplored").getAsInt());
        assertTrue(row.get("symmetryReduction").getAsBoolean());
        assertEquals("DFS", row.get("strategy").getAsString());
        assertEquals("EXACT", row.get("storeType").getAsString());
        assertEquals("PASS", row.get("verdict").getAsString());
        assertTrue(after.get("soundness").getAsBoolean());
        var markdown = invokeCli("--source", source, "--store", "exact", "--strategy", "DFS", "--symmetry");
        assertEquals(0, markdown.exitCode(), markdown.output());
        assertTrue(markdown.output().contains("DFS + symmetry (exact): 5 states"));
        assertTrue(markdown.output().contains("orbit representatives"));
        var artifact = java.nio.file.Path.of("build/source-symmetry-visualizer.json");
        java.nio.file.Files.createDirectories(artifact.getParent());
        java.nio.file.Files.writeString(artifact, reduced.output());
    }

    /** Unsupported CLI selections reject instead of silently falling back.
     * @throws Exception if child JVM execution fails
     */
    @Test void sourceSymmetryCliRejectsUnsupportedSelections() throws Exception {
        for (var selection : List.of(List.<String>of(), List.of("--store", "exact"),
                List.of("--store", "bitstate", "--strategy", "DFS"),
                List.of("--store", "exact", "--strategy", "STATIC_POR"),
                List.of("--store", "exact", "--strategy", "DPOR"),
                List.of("--store", "exact", "--strategy", "CONTEXT_BOUNDED"),
                List.of("--store", "exact", "--strategy", "DFS", "--max-preemptions", "0"),
                List.of("--store", "exact", "--strategy", "DFS", "--iterative-deepening"))) {
            var args = new java.util.ArrayList<>(List.of("--source", "examples/programs/symmetry-counter.ilv", "--symmetry"));
            args.addAll(selection);
            var result = invokeCli(args.toArray(String[]::new));
            assertEquals(1, result.exitCode(), result.output());
            assertTrue(result.output().contains("--symmetry requires"), result.output());
        }
        var legacy = invokeCli("lost-update", "--symmetry", "--store", "exact", "--strategy", "DFS");
        assertEquals(1, legacy.exitCode());
        assertTrue(legacy.output().contains("--symmetry requires"));
    }

    /** Source errors and property failures keep authenticated representative evidence.
     * @param dir isolated source fixtures
     * @throws Exception if writing a fixture or invoking the CLI fails
     */
    @Test void sourceSymmetryCliFailureAndIneligibleModel(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var source = dir.resolve("split.ilv");
        java.nio.file.Files.writeString(source, "program split { shared int x = 0; local int r = 0;"
            + "helper inc { atomic { local.r = x; } atomic { x = local.r + 1; } }"
            + "thread 0 { call inc; } thread 1 { call inc; } invariant final (x == 2); }");
        var failed = invokeCli("--source", source.toString(), "--store", "exact", "--strategy", "DFS", "--symmetry", "--json");
        assertEquals(0, failed.exitCode(), failed.output());
        var report = com.google.gson.JsonParser.parseString(failed.output()).getAsJsonObject();
        var row = report.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals(8, row.get("statesExplored").getAsInt());
        assertEquals("VIOLATION", row.get("verdict").getAsString());
        assertTrue(report.get("soundness").getAsBoolean());
        var trace = row.getAsJsonObject("failingTrace");
        assertEquals(4, trace.getAsJsonArray("threads").size());
        assertEquals(5, trace.getAsJsonArray("states").size());
        java.nio.file.Files.writeString(source, "program loops { shared int x = 0; thread 0 { while (true) limit 0 {} }"
            + "thread 1 { while (true) limit 0 {} } }");
        var cutoff = invokeCli("--source", source.toString(), "--store", "exact", "--strategy", "DFS", "--symmetry", "--json", "--simplify-cutoff");
        assertEquals(0, cutoff.exitCode(), cutoff.output());
        var witness = com.google.gson.JsonParser.parseString(cutoff.output()).getAsJsonObject();
        assertEquals("INCOMPLETE", witness.get("outcome").getAsString());
        assertEquals(0, witness.getAsJsonArray("threads").size());
        assertEquals(0, witness.getAsJsonObject("cutoff").get("threadId").getAsInt());
        assertEquals(0, witness.getAsJsonObject("cutoff").get("limit").getAsInt());
        assertEquals(1, witness.getAsJsonArray("states").size());
        java.nio.file.Files.writeString(source, "program unique { shared int x = 0; thread 0 { atomic {} } }");
        var ineligible = invokeCli("--source", source.toString(), "--store", "exact", "--strategy", "DFS", "--symmetry");
        assertEquals(1, ineligible.exitCode());
        assertTrue(ineligible.output().contains("no interchangeable"), ineligible.output());
        assertFalse(ineligible.output().contains("Exception in thread"));
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
