/** Generates Markdown and JSON reports from benchmark results. */
package dev.samhb.interleave.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.StateSnapshot;
import dev.samhb.interleave.search.Trace;
import java.util.*;

/**
 * Generates Markdown and JSON reports from benchmark results.
 */
public final class ReportWriter {
    /** JSON serializer with standard string escaping and HTML escaping disabled. */
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    /** Results. */
    private final List<BenchmarkResult> results;
    /** Actual models used to validate custom file/source traces. */
    private final List<BenchmarkProgram> programs;

    /**
     * Creates a report writer for the given results.
     *
     * @param results the benchmark results to report
     */
    public ReportWriter(List<BenchmarkResult> results) {
        this(results, BugCorpus.all());
    }

    /**
     * Creates a writer with the actual models needed for soundness replay.
     * @param results recorded rows
     * @param programs model definitions, including custom source programs
     */
    public ReportWriter(List<BenchmarkResult> results, List<BenchmarkProgram> programs) {
        this.results = List.copyOf(results);
        this.programs = List.copyOf(programs);
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

        SoundnessAttestation attestation = new SoundnessAttestation(results, programs);
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
            sb.append("      \"bug\": ").append(GSON.toJson(result.bugName())).append(",\n");
            sb.append(String.format("      \"strategy\": \"%s\",\n", result.strategy()));
            sb.append(String.format("      \"storeType\": \"%s\",\n", result.storeType()));
            sb.append(String.format(java.util.Locale.ROOT, "      \"statesExplored\": %d,\n", result.statesExplored()));
            sb.append(String.format(java.util.Locale.ROOT, "      \"wallTimeMs\": %d,\n", result.wallTimeMs()));
            sb.append(String.format(java.util.Locale.ROOT, "      \"heapDeltaBytes\": %d,\n", result.heapDeltaBytes()));
            // Always emitted, so a report's shape does not depend on which strategies ran.
            sb.append(String.format("      \"preemptionsUsed\": %s,\n", formatPreemptions(result)));
            if (result.sourceModel()) sb.append("      \"programKind\": \"SOURCE\",\n");
            if (!result.incompletenessReasons().isEmpty()) {
                sb.append("      \"incompletenessReasons\": ").append(GSON.toJson(result.incompletenessReasons())).append(",\n");
            }
            if (!result.incompleteTraces().isEmpty() && result.sourceModel()) {
                sb.append("      \"incompleteTrace\": ")
                    .append(result.incompleteTraces().getFirst().toRecord().withStates(result.incompleteStates()).toJson()).append(",\n");
            }

            // Failing trace
            if (result.failingTrace().isPresent()) {
                sb.append("      \"failingTrace\": ");
                sb.append(formatFailingTrace(result.failingTrace().get(), result.states()));
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

        SoundnessAttestation attestation = new SoundnessAttestation(results, programs);
        sb.append("  \"soundness\": ");
        sb.append(attestation.isSound() ? "true" : "false");
        sb.append("\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Formats the optional preemption bound for a report row.
     * @param result completed exploration result
     * @return preemption bound formatted for the report, or the unbounded placeholder
     */
    private static String formatPreemptions(BenchmarkResult result) {
        // null renders as JSON null rather than being omitted, so a consumer can distinguish
        // "this strategy has no bound" from a missing field.
        return result.preemptionsUsed() == null ? "null" : result.preemptionsUsed().toString();
    }

    /**
     * Formats recorded thread choices, outcomes, and optional state snapshots for a failure row.
     * @param trace recorded execution to inspect or replay
     * @param states captured state snapshots, if available
     * @return formatted thread choices, outcomes, and state snapshots
     */
    private static String formatFailingTrace(Trace trace, List<StateSnapshot> states) {
        if (trace.evidence() != null) {
            var json = com.google.gson.JsonParser.parseString(trace.toRecord().withStates(states).toJson()).getAsJsonObject();
            json.add("threadIds", GSON.toJsonTree(trace.threadIds()));
            return GSON.toJson(json);
        }
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
        sb.append("]");
        if (states != null && !states.isEmpty()) {
            sb.append(",\n        \"states\": ").append(GSON.toJson(states));
        }
        sb.append("\n      }");
        return sb.toString();
    }

    /**
     * Formats approximation diagnostics only for a bitstate result.
     * @param result completed exploration result
     * @return formatted approximation diagnostics, or an empty string for exact storage
     */
    private static String formatBitstateMetrics(BenchmarkResult result) {
        return String.format(java.util.Locale.ROOT, """
            {
              "falsePositiveRate": %s,
              "bitCount": %d,
              "bitDensity": %s
            }""",
            formatSmallDouble(result.estimatedFalsePositiveRate()),
            result.bitstateBitCount(),
            formatSmallDouble(result.bitstateBitDensity()));
    }

    /**
     * Formats small diagnostic values with a locale-independent representation.
     * @param value value to assign
     * @return locale-independent numeric text
     */
    private static String formatSmallDouble(double value) {
        if (value == 0.0) return "0.0";
        if (Math.abs(value) < 0.001) {
            return String.format(java.util.Locale.ROOT, "%.2e", value);
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }
}
