package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.search.Trace;
import java.util.*;

/**
 * Generates Markdown and JSON reports from benchmark results.
 */
public final class ReportWriter {
    private final List<BenchmarkResult> results;

    /**
     * Creates a report writer for the given results.
     *
     * @param results the benchmark results to report
     */
    public ReportWriter(List<BenchmarkResult> results) {
        this.results = List.copyOf(results);
    }

    /**
     * Generates a human-readable Markdown report including:
     * <ul>
     *   <li>States Explored Reduction Table (grouped by strategy + store type)</li>
     *   <li>Bitstate Summary (FPR, bit density for bitstate runs)</li>
     *   <li>Detailed results table (bug, strategy, store type, states, time, memory, verdict)</li>
     *   <li>Soundness Attestation (validates exact results + replays all violation traces)</li>
     * </ul>
     *
     * @return the Markdown report as a string
     */
    public String writeMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# interleave Benchmark Report\n\n");

        StatesExploredTable table = new StatesExploredTable(results);
        sb.append(table.formatReductionTable());
        sb.append("\n");

        String bitstateSummary = table.formatBitstateSummary();
        if (!bitstateSummary.isEmpty()) {
            sb.append(bitstateSummary);
            sb.append("\n");
        }

        sb.append("## Detailed Results\n\n");
        sb.append(table.formatMarkdown());
        sb.append("\n");

        SoundnessAttestation attestation = new SoundnessAttestation(results, BugCorpus.all());
        sb.append(attestation.formatMarkdown());

        return sb.toString();
    }

    /**
     * Generates a JSON report with benchmark data and soundness flag.
     * Each benchmark entry includes: bug, strategy, storeType, statesExplored,
     * wallTimeMs, heapDeltaBytes, verdict, failingTrace (if present),
     * and bitstateMetrics (for bitstate runs).
     *
     * @return the JSON report as a string
     */
    public String writeJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"benchmarks\": [\n");

        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult result = results.get(i);
            sb.append("    {\n");
            sb.append(String.format("      \"bug\": \"%s\",\n", result.bugName()));
            sb.append(String.format("      \"strategy\": \"%s\",\n", result.strategy()));
            sb.append(String.format("      \"storeType\": \"%s\",\n", result.storeType()));
            sb.append(String.format("      \"statesExplored\": %d,\n", result.statesExplored()));
            sb.append(String.format("      \"wallTimeMs\": %d,\n", result.wallTimeMs()));
            sb.append(String.format("      \"heapDeltaBytes\": %d,\n", result.heapDeltaBytes()));

            // Failing trace
            if (result.failingTrace().isPresent()) {
                sb.append("      \"failingTrace\": ");
                sb.append(formatFailingTrace(result.failingTrace().get()));
                sb.append(",\n");
            }

            // Bitstate metrics
            if (result.storeType() == StoreType.BITSTATE) {
                sb.append("      \"bitstateMetrics\": ");
                sb.append(formatBitstateMetrics(result));
                sb.append(",\n");
            }

            sb.append(String.format("      \"verdict\": \"%s\"\n", result.verdict()));
            sb.append("    }");
            if (i < results.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ],\n");

        SoundnessAttestation attestation = new SoundnessAttestation(results, BugCorpus.all());
        sb.append("  \"soundness\": ");
        sb.append(attestation.isSound() ? "true" : "false");
        sb.append("\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static String formatFailingTrace(Trace trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("        \"threadIds\": [");
        for (int i = 0; i < trace.threadIds().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(trace.threadIds().get(i));
        }
        sb.append("],\n");
        sb.append("        \"outcomes\": [");
        for (int i = 0; i < trace.outcomes().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("\"%s\"", trace.outcomes().get(i)));
        }
        sb.append("]\n");
        sb.append("      }");
        return sb.toString();
    }

    private static String formatBitstateMetrics(BenchmarkResult result) {
        return String.format("""
            {
              "falsePositiveRate": %.6f,
              "bitCount": %d,
              "bitDensity": %.6f
            }""",
            result.estimatedFalsePositiveRate(),
            result.bitstateBitCount(),
            result.bitstateBitDensity());
    }
}
