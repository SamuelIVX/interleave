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

    /** Checks literal budget/proof boundaries and fresh snapshots through actual CLI processes.
     * Writes proven and partial artifacts for consumer verification.
     * @param directory temporary source files
     * @throws Exception if process execution or file I/O fails
     */
    @Test
    void shortestCutoffProducesProofArtifacts(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var source = directory.resolve("noise.ilv");
        java.nio.file.Files.writeString(source, "program noise { shared int noise = 0; thread 0 { repeat 4 { atomic { noise = noise + 1; } } } thread 1 { atomic { while (true) limit 0 {} } } }");
        for (int budget : new int[]{0, 1, 3, 4, Integer.MAX_VALUE}) {
            var result = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS", "--shortest-cutoff", Integer.toString(budget));
            assertEquals(0, result.exitCode(), result.output());
            var trace = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output());
            var search = trace.getAsJsonObject("cutoffSearch");
            assertEquals("ORIGINAL_SUBSEQUENCES", search.get("domain").getAsString());
            assertEquals(budget >= 4, search.get("shortestProven").getAsBoolean());
            int length = budget == 0 ? 4 : budget == 1 ? 3 : budget == 3 ? 1 : 0;
            int checked = budget == 0 ? 0 : budget == 1 ? 1 : budget == 3 ? 3 : 4;
            assertEquals(checked, search.get("candidatesChecked").getAsInt());
            assertEquals(budget, search.get("candidateLimit").getAsInt());
            assertEquals("INCOMPLETE", trace.get("outcome").getAsString());
            assertEquals(length, trace.getAsJsonArray("threads").size());
            assertEquals(length, trace.getAsJsonArray("executedSteps").size());
            assertEquals(length + 1, trace.getAsJsonArray("states").size());
            assertEquals(length, trace.getAsJsonArray("states").get(length).getAsJsonObject().getAsJsonObject("fields").get("noise").getAsInt());
            assertEquals("LOOP_LIMIT", trace.getAsJsonObject("cutoff").get("reason").getAsString());
            assertEquals(1, trace.getAsJsonObject("cutoff").get("threadId").getAsInt());
            assertEquals(0, trace.getAsJsonObject("cutoff").get("limit").getAsInt());
            if (budget == 1 || budget == 4) {
                var artifact = java.nio.file.Path.of("build/source-shortest-cutoff-" + (budget == 4 ? "proven" : "partial") + ".json");
                java.nio.file.Files.createDirectories(artifact.getParent());
                java.nio.file.Files.writeString(artifact, result.output());
            }
        }
        for (String strategy : List.of("DFS", "STATIC_POR", "DPOR", "CONTEXT_BOUNDED")) {
            java.nio.file.Files.writeString(source, "program empty { shared int x = 0; thread 0 { while (true) limit 0 {} } thread 1 { while (true) limit 0 {} } }");
            var result = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", strategy, "--shortest-cutoff", "0", "--symmetry");
            assertEquals(0, result.exitCode(), result.output());
            var trace = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output());
            assertTrue(trace.getAsJsonObject("cutoffSearch").get("shortestProven").getAsBoolean());
            assertEquals(0, trace.getAsJsonObject("cutoffSearch").get("candidatesChecked").getAsInt());
            assertEquals(0, trace.getAsJsonArray("threads").size());
            assertEquals("INCOMPLETE", trace.get("outcome").getAsString());
        }
        java.nio.file.Files.writeString(source, "program switches { shared int x = 0; thread 0 { repeat 4 { atomic {} } } thread 1 { repeat 4 { atomic {} } } }");
        var bounded = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "CONTEXT_BOUNDED", "--max-preemptions", "0", "--shortest-cutoff", "100");
        assertEquals(0, bounded.exitCode(), bounded.output());
        var trace = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(bounded.output());
        assertEquals("PREEMPTION_BOUND", trace.getAsJsonObject("cutoff").get("reason").getAsString());
        assertEquals(0, trace.getAsJsonObject("cutoff").get("limit").getAsInt());
        assertEquals(1, trace.getAsJsonArray("threads").size());
        assertTrue(trace.getAsJsonObject("cutoffSearch").get("shortestProven").getAsBoolean());
    }

    /** Gives PIT in-process coverage of CLI payload construction without replacing the search.
     * Numeric expectations come from four removable noise actions and the empty lower bound.
     * @param directory temporary source files
     * @throws Exception if source I/O fails
     */
    @Test
    void shortestCutoffPayloadContract(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var source = directory.resolve("noise.ilv");
        java.nio.file.Files.writeString(source, "program noise { shared int noise = 0; thread 0 { repeat 4 { atomic { noise = noise + 1; } } } thread 1 { atomic { while (true) limit 0 {} } } }");
        var model = new dev.samhb.interleave.format.SourceProgramLoader().loadFromFile(source);
        var original = new BenchmarkHarness(1_000_003, 4, java.util.Set.of(StoreType.EXACT),
            java.util.Set.of("DFS"), 2, false).runProgram(model).getFirst().incompleteTraces().getFirst();
        assertThrows(IllegalArgumentException.class, () -> Main.shortestCutoffPayload(model, original, -1));
        for (int budget : new int[]{0, 1, 4}) {
            var json = Main.shortestCutoffPayload(model, original, budget);
            var search = json.getAsJsonObject("cutoffSearch");
            assertEquals("ORIGINAL_SUBSEQUENCES", search.get("domain").getAsString());
            assertEquals(budget == 4, search.get("shortestProven").getAsBoolean());
            assertEquals(budget, search.get("candidateLimit").getAsInt());
            assertEquals(budget, search.get("candidatesChecked").getAsInt());
            int length = budget == 0 ? 4 : budget == 1 ? 3 : 0;
            assertEquals(length, json.getAsJsonArray("threads").size());
            assertEquals(length + 1, json.getAsJsonArray("states").size());
            assertEquals("INCOMPLETE", json.get("outcome").getAsString());
            assertEquals("LOOP_LIMIT", json.getAsJsonObject("cutoff").get("reason").getAsString());
        }
        var legacy = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS", "--simplify-cutoff");
        var legacyJson = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(legacy.output());
        assertFalse(legacyJson.has("cutoffSearch"));
        assertEquals(0, legacyJson.getAsJsonArray("threads").size());
        var ordinary = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS");
        var ordinaryJson = dev.samhb.interleave.testsupport.JsonAssertions.parseObject(ordinary.output());
        assertFalse(ordinaryJson.has("cutoffSearch"));
        assertEquals("INCOMPLETE", ordinaryJson.getAsJsonArray("benchmarks").get(0).getAsJsonObject().get("verdict").getAsString());
    }

    /** The presentation seam must pass the source property into shortest search.
     * The weights 1,2,4,8 permit x=5 or 15 at the cutoff; the property forbids the pair for 5.
     * All fifteen proper subsets therefore reject; removing the property would return two choices.
     */
    @Test
    void shortestCutoffPayloadPreservesSourceProperty() {
        var model = new dev.samhb.interleave.format.SourceProgramLoader().load(
            "program weighted { shared int x = 0; thread 0 { atomic { x = x + 1; } } "
            + "thread 1 { atomic { x = x + 2; } } thread 2 { atomic { x = x + 4; } } "
            + "thread 3 { atomic { x = x + 8; } } "
            + "thread 4 { atomic when (x == 5 || x == 15) { while (true) limit 0 {} } } invariant always (x != 5); }");
        var execution = new dev.samhb.interleave.core.ExecutionDriver().runDetailed(model.program(),
            new dev.samhb.interleave.core.Schedule(List.of(0, 1, 2, 3, 4)), model.invariant().orElseThrow());
        assertEquals(dev.samhb.interleave.core.ScheduleExecution.Kind.INCOMPLETE, execution.kind());
        var payload = Main.shortestCutoffPayload(model, execution.trace(), 15);
        assertEquals(4, payload.getAsJsonArray("threads").size());
        assertEquals(5, payload.getAsJsonArray("states").size());
        assertEquals(15, payload.getAsJsonObject("cutoffSearch").get("candidatesChecked").getAsInt());
        assertTrue(payload.getAsJsonObject("cutoffSearch").get("shortestProven").getAsBoolean());
        assertEquals("INCOMPLETE", payload.get("outcome").getAsString());
    }

    /** Rejects malformed budgets, ambiguous selections and conclusive runs without a cutoff result.
     * @param directory temporary source files
     * @throws Exception if process execution or file I/O fails
     */
    @Test
    void shortestCutoffRejectsInvalidSelections(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var source = directory.resolve("complete.ilv");
        java.nio.file.Files.writeString(source, "program complete { shared int x = 0; thread 0 { atomic {} } }");
        for (String budget : List.of("-1", "1.5", "abc", "2147483648")) {
            var result = invokeCli("--source", source.toString(), "--shortest-cutoff", budget);
            assertEquals(1, result.exitCode());
            assertTrue(result.output().contains("--shortest-cutoff must be a non-negative integer"), result.output());
        }
        assertTrue(invokeCli("--shortest-cutoff").output().contains("--shortest-cutoff requires a non-negative integer"));
        for (String[] args : List.of(
            new String[]{"--source", source.toString(), "--store", "exact", "--strategy", "DFS"},
            new String[]{"--source", source.toString(), "--json", "--strategy", "DFS"},
            new String[]{"--source", source.toString(), "--json", "--store", "exact"},
            new String[]{"--source", source.toString(), "--json", "--store", "bitstate", "--strategy", "DFS"},
            new String[]{"peterson", "--json", "--store", "exact", "--strategy", "DFS"},
            new String[]{"--all", "--json", "--store", "exact", "--strategy", "DFS"})) {
            var arguments = new java.util.ArrayList<>(List.of(args));
            arguments.addAll(List.of("--shortest-cutoff", "0"));
            var result = invokeCli(arguments.toArray(String[]::new));
            assertEquals(1, result.exitCode());
            assertTrue(result.output().contains("--shortest-cutoff requires"), result.output());
        }
        var duplicate = invokeCli("--source", source.toString(), "--shortest-cutoff", "0", "--shortest-cutoff", "4");
        assertEquals(1, duplicate.exitCode());
        assertTrue(duplicate.output().contains("may be supplied only once"));
        var conflict = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS", "--simplify-cutoff", "--shortest-cutoff", "1");
        assertEquals(1, conflict.exitCode());
        assertTrue(conflict.output().contains("mutually exclusive"));
        var absent = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS", "--shortest-cutoff", "0");
        assertEquals(1, absent.exitCode());
        assertTrue(absent.output().contains("INCOMPLETE selected run with cutoff evidence"));
        java.nio.file.Files.writeString(source, "program mixed { shared int x = 0; thread 0 { assert (false); } thread 1 { while (true) limit 0 {} } }");
        var mixed = invokeCli("--source", source.toString(), "--json", "--store", "exact", "--strategy", "DFS", "--shortest-cutoff", "1");
        assertEquals(1, mixed.exitCode());
        assertTrue(mixed.output().contains("INCOMPLETE selected run with cutoff evidence"));
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
        assertTrue(markdown.output().contains("representative visits"));
        var artifact = java.nio.file.Path.of("build/source-symmetry-visualizer.json");
        java.nio.file.Files.createDirectories(artifact.getParent());
        java.nio.file.Files.writeString(artifact, reduced.output());
    }

    /** Produces real approximate-symmetry JSON for normalization and browser verification.
     * @throws Exception if child JVM execution or artifact writing fails
     */
    @Test void approximateSymmetryCliReportIdentity() throws Exception {
        for (String strategy : List.of("DFS", "STATIC_POR")) {
            var result = invokeCli("--source", "examples/programs/symmetry-counter.ilv", "--store", "bitstate",
                "--strategy", strategy, "--symmetry", "--json");
            assertEquals(0,result.exitCode(),result.output());
            var report = com.google.gson.JsonParser.parseString(result.output()).getAsJsonObject();
            var row = report.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
            assertTrue(row.get("symmetryReduction").getAsBoolean());
            assertEquals(strategy,row.get("strategy").getAsString());
            assertEquals("BITSTATE",row.get("storeType").getAsString());
            assertEquals("APPROXIMATE_PASS",row.get("verdict").getAsString());
            assertEquals(5,row.get("statesExplored").getAsInt());
            assertTrue(report.get("soundness").getAsBoolean());
            if (strategy.equals("DFS")) {
                var artifact = java.nio.file.Path.of("build/source-approximate-symmetry-visualizer.json");
                java.nio.file.Files.createDirectories(artifact.getParent());
                java.nio.file.Files.writeString(artifact,result.output());
            }
        }
    }

    /** Actual approximate failure/cutoff reports retain replay authentication and precedence.
     * @param dir isolated fixture directory
     * @throws Exception if fixture writing or CLI execution fails
     */
    @Test void approximateSymmetryCliWitnesses(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var source = dir.resolve("pair.ilv");
        for (String strategy : List.of("DFS","STATIC_POR")) {
            java.nio.file.Files.writeString(source,"program split { shared int x = 0; local int r = 0; helper inc { atomic { local.r = x; } atomic { x = local.r + 1; } } thread 0 { call inc; } thread 1 { call inc; } invariant final (x == 2); }");
            var failure = invokeCli("--source",source.toString(),"--store","bitstate","--strategy",strategy,"--symmetry","--json");
            assertEquals(0,failure.exitCode(),failure.output());
            var report = com.google.gson.JsonParser.parseString(failure.output()).getAsJsonObject();
            assertEquals("VIOLATION",report.getAsJsonArray("benchmarks").get(0).getAsJsonObject().get("verdict").getAsString());
            assertTrue(report.get("soundness").getAsBoolean()); // Attestation replays every violation.
            java.nio.file.Files.writeString(source,"program loops { shared int x = 0; thread 0 { while (true) limit 0 {} } thread 1 { while (true) limit 0 {} } }");
            var cutoff = invokeCli("--source",source.toString(),"--store","bitstate","--strategy",strategy,"--symmetry","--json");
            assertEquals(0,cutoff.exitCode(),cutoff.output());
            var row = com.google.gson.JsonParser.parseString(cutoff.output()).getAsJsonObject().getAsJsonArray("benchmarks").get(0).getAsJsonObject();
            assertEquals("INCOMPLETE",row.get("verdict").getAsString());
            var incomplete = row.getAsJsonObject("incompleteTrace");
            assertNotNull(incomplete);
            assertEquals("LOOP_LIMIT",incomplete.getAsJsonObject("cutoff").get("reason").getAsString());
            assertEquals("BITSTATE",row.get("storeType").getAsString()); assertTrue(row.get("symmetryReduction").getAsBoolean());
        }
    }

    /** Fresh approximate CBS reports show the producing pass and authenticated observed evidence.
     * @param dir isolated source fixtures
     * @throws Exception if fixture writing or CLI execution fails
     */
    @Test void approximateCbsCliArtifacts(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var absent = invokeCli("--source","examples/programs/symmetry-counter.ilv","--symmetry","--store","bitstate",
            "--strategy","CONTEXT_BOUNDED","--max-preemptions","2","--iterative-deepening","--bitstate-size","1","--json");
        assertEquals(0,absent.exitCode(),absent.output());
        var report = com.google.gson.JsonParser.parseString(absent.output()).getAsJsonObject();
        var row = report.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals("BITSTATE",row.get("storeType").getAsString());assertTrue(row.get("symmetryReduction").getAsBoolean());
        assertEquals("APPROXIMATE_PASS",row.get("verdict").getAsString());assertEquals(2,row.get("preemptionsUsed").getAsInt());
        assertEquals(1,row.getAsJsonObject("bitstateMetrics").get("bitCount").getAsInt());
        assertEquals(1.0,row.getAsJsonObject("bitstateMetrics").get("bitDensity").getAsDouble());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-cbs-absence.json"),absent.output());
        var source = dir.resolve("race.ilv");
        java.nio.file.Files.writeString(source,"program split { shared int x = 0; local int r = 0; helper inc { atomic { local.r = x; } atomic { x = local.r + 1; } } thread 0 { call inc; } thread 1 { call inc; } invariant final (x == 2); }");
        var failure = invokeCli("--source",source.toString(),"--symmetry","--store","bitstate","--strategy","CONTEXT_BOUNDED",
            "--max-preemptions","2","--iterative-deepening","--json");
        assertEquals(0,failure.exitCode(),failure.output());
        var failed = com.google.gson.JsonParser.parseString(failure.output()).getAsJsonObject();
        assertEquals("VIOLATION",failed.getAsJsonArray("benchmarks").get(0).getAsJsonObject().get("verdict").getAsString());
        assertTrue(failed.get("soundness").getAsBoolean());
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-cbs-failure.json"),failure.output());
        java.nio.file.Files.writeString(source,"program loops { shared int x = 0; thread 0 { while (true) limit 0 {} } thread 1 { while (true) limit 0 {} } }");
        var cutoff = invokeCli("--source",source.toString(),"--symmetry","--store","bitstate","--strategy","CONTEXT_BOUNDED",
            "--max-preemptions","2","--iterative-deepening","--json");
        assertEquals(0,cutoff.exitCode(),cutoff.output());
        var incomplete = com.google.gson.JsonParser.parseString(cutoff.output()).getAsJsonObject().getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals("INCOMPLETE",incomplete.get("verdict").getAsString());
        assertEquals("LOOP_LIMIT",incomplete.getAsJsonObject("incompleteTrace").getAsJsonObject("cutoff").get("reason").getAsString());
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-cbs-cutoff.json"),cutoff.output());
    }

    /** Final approximate default selection emits all strategies and authenticated DPOR observations.
     * @param dir isolated source fixtures
     * @throws Exception if fixture writing or CLI execution fails
     */
    @Test void approximateDporAndAllStrategyArtifacts(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var all = invokeCli("--source","examples/programs/symmetry-counter.ilv","--symmetry","--store","bitstate","--json");
        assertEquals(0,all.exitCode(),all.output());
        var report = com.google.gson.JsonParser.parseString(all.output()).getAsJsonObject();
        var strategies = new java.util.HashSet<String>();
        for (var item : report.getAsJsonArray("benchmarks")) {
            var row = item.getAsJsonObject();strategies.add(row.get("strategy").getAsString());
            assertTrue(row.get("symmetryReduction").getAsBoolean());assertEquals("BITSTATE",row.get("storeType").getAsString());
            assertEquals("APPROXIMATE_PASS",row.get("verdict").getAsString());
            if (row.get("strategy").getAsString().equals("DPOR")) {
                assertEquals(5,row.get("statesExplored").getAsInt()); // Independently shadowed five population ranks.
                // Five products plus five VALUE marks: independent F=2.559764492176605e-18, rounded by the existing JSON format.
                assertEquals(2.56e-18,row.getAsJsonObject("bitstateMetrics").get("falsePositiveRate").getAsDouble());
            }
        }
        assertEquals(java.util.Set.of("DFS","STATIC_POR","DPOR","CONTEXT_BOUNDED"),strategies);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-all-strategies.json"),all.output());
        var source = dir.resolve("observed.ilv");
        for (var fixture : List.of(List.of("assert(false);","ASSERTION","assertion"),
                List.of("atomic { x = 1 % x; }","RUNTIME_ERROR","runtime"))) {
            String body = fixture.get(0);
            java.nio.file.Files.writeString(source,"program observed { shared int x = 0; thread 0 { "+body+" } thread 1 { "+body+" } }");
            var failure = invokeCli("--source",source.toString(),"--symmetry","--store","bitstate","--strategy","DPOR","--bitstate-size","1","--json");
            assertEquals(0,failure.exitCode(),failure.output());
            var failed = com.google.gson.JsonParser.parseString(failure.output()).getAsJsonObject();
            var row = failed.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
            assertEquals("VIOLATION",row.get("verdict").getAsString());assertTrue(failed.get("soundness").getAsBoolean());
            var trace = row.getAsJsonObject("failingTrace");
            assertEquals(0,trace.getAsJsonArray("threads").get(0).getAsInt());
            assertEquals(fixture.get(1),trace.getAsJsonObject("failure").get("kind").getAsString());
            if (fixture.get(2).equals("runtime")) assertEquals("MODULO_BY_ZERO",trace.getAsJsonObject("failure").get("category").getAsString());
            else assertFalse(trace.getAsJsonObject("failure").has("category"));
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-dpor-"+fixture.get(2)+".json"),failure.output());
        }
        for (var entry : List.of(List.of("atomic when (false) { x = 1; }","DEADLOCK","deadlock"),
                List.of("while (true) limit 0 {}","INCOMPLETE","cutoff"))) {
            java.nio.file.Files.writeString(source,"program observed { shared int x = 0; thread 0 { "+entry.get(0)+" } thread 1 { "+entry.get(0)+" } }");
            var actual = invokeCli("--source",source.toString(),"--symmetry","--store","bitstate","--strategy","DPOR","--json");
            assertEquals(0,actual.exitCode(),actual.output());
            var row = com.google.gson.JsonParser.parseString(actual.output()).getAsJsonObject().getAsJsonArray("benchmarks").get(0).getAsJsonObject();
            assertEquals(entry.get(1),row.get("verdict").getAsString());
            if (entry.get(2).equals("cutoff")) assertEquals("LOOP_LIMIT",row.getAsJsonObject("incompleteTrace").getAsJsonObject("cutoff").get("reason").getAsString());
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-dpor-"+entry.get(2)+".json"),actual.output());
        }
    }

    /** CLI default selection composes all four exact strategies, including iterative CBS.
     * @throws Exception if child JVM execution fails
     */
    @Test void sourceSymmetryCliSupportsAllStrategiesAndIterativeCbs() throws Exception {
        var all = invokeCli("--source", "examples/programs/symmetry-counter.ilv", "--symmetry", "--store", "exact", "--json");
        assertEquals(0,all.exitCode(),all.output());
        var rows = com.google.gson.JsonParser.parseString(all.output()).getAsJsonObject().getAsJsonArray("benchmarks");
        assertEquals(4, rows.size());
        var strategies = new java.util.HashSet<String>();
        for (var value : rows) {
            var row = value.getAsJsonObject();
            strategies.add(row.get("strategy").getAsString());
            assertTrue(row.get("symmetryReduction").getAsBoolean());
            assertEquals(5,row.get("statesExplored").getAsInt());
        }
        assertEquals(java.util.Set.of("DFS","STATIC_POR","DPOR","CONTEXT_BOUNDED"),strategies);
        var bounded = invokeCli("--source", "examples/programs/symmetry-counter.ilv", "--symmetry", "--store", "exact",
            "--strategy", "CONTEXT_BOUNDED", "--max-preemptions", "0", "--json");
        assertEquals(0,bounded.exitCode(),bounded.output());
        var boundedRow = com.google.gson.JsonParser.parseString(bounded.output()).getAsJsonObject().getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals(5,boundedRow.get("statesExplored").getAsInt());
        assertEquals("CONTEXT_BOUNDED",boundedRow.get("strategy").getAsString());
        assertTrue(boundedRow.get("symmetryReduction").getAsBoolean());
        assertEquals(0,boundedRow.get("preemptionsUsed").getAsInt());
        var iterative = invokeCli("--source", "examples/programs/symmetry-counter.ilv", "--symmetry", "--store", "exact",
            "--strategy", "CONTEXT_BOUNDED", "--iterative-deepening", "--max-preemptions", "1", "--json");
        assertEquals(0,iterative.exitCode(),iterative.output());
        var iterativeRow = com.google.gson.JsonParser.parseString(iterative.output()).getAsJsonObject().getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals(5,iterativeRow.get("statesExplored").getAsInt());
        assertEquals("CONTEXT_BOUNDED",iterativeRow.get("strategy").getAsString());
        assertTrue(iterativeRow.get("symmetryReduction").getAsBoolean());
        // Each one-action owner terminates before switching, so K=0 already prunes no edge.
        assertEquals(0,iterativeRow.get("preemptionsUsed").getAsInt());
    }

    /** An option-looking source filename is a value, not an explicitly supplied CBS option.
     * @param directory isolated child JVM working directory
     * @throws Exception if fixture creation or child JVM execution fails
     */
    @Test void sourceSymmetryCliDoesNotTreatSourceFilenameAsCbsFlag(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.writeString(directory.resolve("--max-preemptions"),
            "program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var result = invokeCli(directory,"--source","--max-preemptions","--symmetry","--store","exact","--strategy","DFS","--json");
        assertEquals(0,result.exitCode(),result.output());
        var row = com.google.gson.JsonParser.parseString(result.output()).getAsJsonObject().getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals(3,row.get("statesExplored").getAsInt());
        assertTrue(row.get("symmetryReduction").getAsBoolean());
    }

    /** Unsupported CLI selections reject instead of silently falling back.
     * @throws Exception if child JVM execution fails
     */
    @Test void sourceSymmetryCliRejectsUnsupportedSelections() throws Exception {
        for (var selection : List.of(List.<String>of(),
                List.of("--store", "exact", "--strategy", "STATIC_POR", "--max-preemptions", "0"),
                List.of("--store", "exact", "--strategy", "DPOR", "--iterative-deepening"),
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

    /** Declarative symmetry uses existing file syntax and emits four exact non-source rows.
     * @throws Exception if the child JVM or artifact write fails
     */
    @Test void declarativeSymmetryCliSupportsExactMatrix() throws Exception {
        var result=invokeCli("--file","examples/programs/semaphore-declarative.json","--symmetry","--store","exact","--json");
        assertEquals(0,result.exitCode(),result.output());
        var report=dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output());
        assertTrue(report.get("soundness").getAsBoolean());
        var rows=report.getAsJsonArray("benchmarks");
        assertEquals(4,rows.size());
        var strategies=new java.util.HashSet<String>();
        for(var element:rows) {
            var row=element.getAsJsonObject();
            assertTrue(row.get("symmetryReduction").getAsBoolean());
            assertEquals("EXACT",row.get("storeType").getAsString());
            assertEquals("PASS",row.get("verdict").getAsString());
            assertFalse(row.has("programKind"));
            assertTrue(strategies.add(row.get("strategy").getAsString()));
        }
        assertEquals(java.util.Set.of("DFS","STATIC_POR","DPOR","CONTEXT_BOUNDED"),strategies);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/declarative-symmetry-exact-report.json"),result.output());
        var failed=invokeCli("--file","src/test/resources/dsl/symmetry-committed-failure.json","--symmetry","--store","exact","--strategy","DFS","--json");
        assertEquals(0,failed.exitCode(),failed.output());
        var failedReport=dev.samhb.interleave.testsupport.JsonAssertions.parseObject(failed.output());
        assertTrue(failedReport.get("soundness").getAsBoolean());
        var failedRow=failedReport.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals("VIOLATION",failedRow.get("verdict").getAsString());
        var snapshots=failedRow.getAsJsonObject("failingTrace").getAsJsonArray("states");
        assertFalse(snapshots.isEmpty());
        assertEquals(1,snapshots.get(snapshots.size()-1).getAsJsonObject().getAsJsonObject("fields").get("x").getAsInt());
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/declarative-symmetry-failure-cli.json"),failed.output());
        for(var strategy:strategies) {
            var explicit=invokeCli("--file","examples/programs/semaphore-declarative.json","--symmetry","--store","exact","--strategy",strategy,"--json");
            assertEquals(0,explicit.exitCode(),explicit.output());
            var selected=dev.samhb.interleave.testsupport.JsonAssertions.parseObject(explicit.output()).getAsJsonArray("benchmarks");
            assertEquals(1,selected.size());
            assertEquals(strategy,selected.get(0).getAsJsonObject().get("strategy").getAsString());
        }
    }

    /** Explicit BITSTATE runs one selected strategy or the full declarative matrix with honest precision.
     * @throws Exception if child JVM execution or fresh artifact writes fail
     */
    @Test void declarativeSymmetryCliSupportsApproximateMatrix() throws Exception {
        var result=invokeCli("--file","examples/programs/semaphore-declarative.json","--symmetry","--store","bitstate","--json");
        assertEquals(0,result.exitCode(),result.output());
        var report=dev.samhb.interleave.testsupport.JsonAssertions.parseObject(result.output());
        assertTrue(report.get("soundness").getAsBoolean());var rows=report.getAsJsonArray("benchmarks");assertEquals(4,rows.size());
        var strategies=new java.util.HashSet<String>();
        for(var element:rows) {
            var row=element.getAsJsonObject();assertTrue(row.get("symmetryReduction").getAsBoolean());
            assertEquals("BITSTATE",row.get("storeType").getAsString());assertEquals("APPROXIMATE_PASS",row.get("verdict").getAsString());
            assertFalse(row.has("programKind"));assertFalse(row.has("sourceDigest"));
            assertTrue(row.getAsJsonObject("bitstateMetrics").get("bitCount").getAsInt()>0);
            assertTrue(strategies.add(row.get("strategy").getAsString()));
        }
        assertEquals(java.util.Set.of("DFS","STATIC_POR","DPOR","CONTEXT_BOUNDED"),strategies);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/declarative-symmetry-approximate-report.json"),result.output());
        for(var strategy:strategies) {
            var selected=invokeCli("--file","examples/programs/semaphore-declarative.json","--symmetry","--store","bitstate","--strategy",strategy,"--json");
            assertEquals(0,selected.exitCode(),selected.output());
            var single=dev.samhb.interleave.testsupport.JsonAssertions.parseObject(selected.output()).getAsJsonArray("benchmarks");
            assertEquals(1,single.size());assertEquals(strategy,single.get(0).getAsJsonObject().get("strategy").getAsString());
            assertEquals("APPROXIMATE_PASS",single.get(0).getAsJsonObject().get("verdict").getAsString());
        }
        var failed=invokeCli("--file","src/test/resources/dsl/symmetry-committed-failure.json","--symmetry","--store","bitstate","--json");
        assertEquals(0,failed.exitCode(),failed.output());
        var failure=dev.samhb.interleave.testsupport.JsonAssertions.parseObject(failed.output());assertTrue(failure.get("soundness").getAsBoolean());
        for(var item:failure.getAsJsonArray("benchmarks")) {
            var row=item.getAsJsonObject();assertEquals("VIOLATION",row.get("verdict").getAsString());
            var states=row.getAsJsonObject("failingTrace").getAsJsonArray("states");assertFalse(states.isEmpty());
            assertEquals(1,states.get(states.size()-1).getAsJsonObject().getAsJsonObject("fields").get("x").getAsInt());
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/declarative-symmetry-approximate-failure-cli.json"),failed.output());
        var markdown=invokeCli("--file","examples/programs/semaphore-declarative.json","--symmetry","--store","bitstate","--strategy","DPOR");
        assertEquals(0,markdown.exitCode());assertTrue(markdown.output().contains("APPROXIMATE_PASS"));
        var iterative=invokeCli("--file","src/test/resources/dsl/symmetry-committed-failure.json","--symmetry","--store","bitstate",
            "--strategy","CONTEXT_BOUNDED","--bitstate-size","1","--max-preemptions","2","--iterative-deepening","--json");
        assertEquals(0,iterative.exitCode(),iterative.output());
        var producing=dev.samhb.interleave.testsupport.JsonAssertions.parseObject(iterative.output()).getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals(2,producing.get("preemptionsUsed").getAsInt());assertEquals("APPROXIMATE_PASS",producing.get("verdict").getAsString());
    }

    /** Declarative support retains explicit storage and source-only witness restrictions.
     * @throws Exception if a child JVM fails to launch
     */
    @Test void declarativeSymmetryCliRetainsPhaseAndEvidenceGuards() throws Exception {
        for(var args:java.util.List.of(
                java.util.List.<String>of(),
                java.util.List.of("--store","exact","--strategy","DFS","--simplify-cutoff"),
                java.util.List.of("--store","exact","--strategy","DFS","--shortest-cutoff","4"))) {
            var command=new java.util.ArrayList<>(java.util.List.of("--file","examples/programs/semaphore-declarative.json","--symmetry","--json"));
            command.addAll(args);
            var result=invokeCli(command.toArray(String[]::new));
            assertEquals(1,result.exitCode(),result.output());
            String reason=args.contains("--simplify-cutoff") ? "--simplify-cutoff requires --source"
                : args.contains("--shortest-cutoff") ? "--shortest-cutoff requires --source" : "explicit --store exact|bitstate";
            assertTrue(result.output().contains(reason),result.output());
        }
        for(String store:java.util.List.of("exact","bitstate")) {
            var typed=invokeCli("--file","examples/programs/lost-update.json","--symmetry","--store",store);
            assertEquals(1,typed.exitCode(),typed.output());assertTrue(typed.output().contains("--symmetry requires"));
        }
    }

    /** Runs the actual entry point in a child JVM because validation calls System.exit.
     * @param arguments CLI arguments
     * @return exit status and merged output
     * @throws Exception if launch, resource lookup, or waiting fails
     */
    private static CliResult invokeCli(String... arguments) throws Exception {
        return invokeCli((java.nio.file.Path) null, arguments);
    }

    /** Runs the entry point in an optional isolated working directory with an absolute classpath.
     * @param directory child working directory, or null to inherit the current directory
     * @param arguments CLI arguments
     * @return exit status and merged output
     * @throws Exception if launch, resource lookup, or waiting fails
     */
    private static CliResult invokeCli(java.nio.file.Path directory, String... arguments) throws Exception {
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
            process = new ProcessBuilder(command).directory(directory == null ? null : directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
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
