package dev.samhb.interleave.report;

import java.util.*;

/**
 * Formats benchmark results into Markdown tables.
 * Produces two views:
 * <ul>
 *   <li>{@link #formatMarkdown()} — detailed table with every result row</li>
 *   <li>{@link #formatReductionTable()} — reduction table grouped by bug, then strategy,
 *       showing both exact and bitstate state counts with reduction percentages</li>
 * </ul>
 */
public final class StatesExploredTable {
    private final List<BenchmarkResult> results;

    /**
     * Creates a table formatter for the given results.
     *
     * @param results the benchmark results to format
     */
    public StatesExploredTable(List<BenchmarkResult> results) {
        this.results = List.copyOf(results);
    }

    /**
     * Formats a detailed Markdown table with one row per result.
     * Columns: Bug, Strategy, Store Type, States Explored, Wall Time, Heap Delta, Verdict.
     *
     * @return the Markdown table as a string
     */
    public String formatMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("| Bug | Strategy | Store Type | States Explored | Wall Time (ms) | Heap Delta (bytes) | Verdict |\n");
        sb.append("|-----|----------|------------|-----------------|----------------|---------------------|---------|\n");

        for (BenchmarkResult result : results) {
            sb.append(String.format("| %s | %s | %s | %d | %d | %d | %s |\n",
                result.bugName(),
                result.strategy(),
                result.storeType().name(),
                result.statesExplored(),
                result.wallTimeMs(),
                result.heapDeltaBytes(),
                result.verdict()));
        }

        return sb.toString();
    }

    /**
     * Formats a reduction table grouped by bug, then strategy.
     * Shows both exact and bitstate state counts for each strategy,
     * with reduction percentages relative to DFS baseline.
     *
     * @return the reduction table as a Markdown string
     */
    public String formatReductionTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("## States Explored Reduction Table\n\n");

        // Group by bugName -> strategy -> storeType -> result
        Map<String, Map<String, Map<StoreType, BenchmarkResult>>> grouped = new LinkedHashMap<>();

        for (BenchmarkResult result : results) {
            grouped.computeIfAbsent(result.bugName(), k -> new LinkedHashMap<>())
                   .computeIfAbsent(result.strategy(), k -> new EnumMap<>(StoreType.class))
                   .put(result.storeType(), result);
        }

        for (Map.Entry<String, Map<String, Map<StoreType, BenchmarkResult>>> bugEntry : grouped.entrySet()) {
            String bugName = bugEntry.getKey();
            Map<String, Map<StoreType, BenchmarkResult>> strategyMap = bugEntry.getValue();

            sb.append(String.format("### %s\n\n", bugName));

            // Get DFS baseline for reduction percentages
            Map<StoreType, BenchmarkResult> dfsResults = strategyMap.getOrDefault("DFS", Map.of());
            long dfsExact = dfsResults.containsKey(StoreType.EXACT) ? dfsResults.get(StoreType.EXACT).statesExplored() : 0;
            long dfsBitstate = dfsResults.containsKey(StoreType.BITSTATE) ? dfsResults.get(StoreType.BITSTATE).statesExplored() : 0;

            // Order strategies: DFS, STATIC_POR, DPOR
            String[] strategyOrder = {"DFS", "STATIC_POR", "DPOR"};
            for (String strategy : strategyOrder) {
                Map<StoreType, BenchmarkResult> storeMap = strategyMap.get(strategy);
                if (storeMap == null) continue;

                BenchmarkResult exactResult = storeMap.get(StoreType.EXACT);
                BenchmarkResult bitstateResult = storeMap.get(StoreType.BITSTATE);

                if (exactResult != null) {
                    String pct = reductionPct(exactResult.statesExplored(), dfsExact);
                    sb.append(String.format("- %s (exact): %,d states%s\n", strategy, exactResult.statesExplored(), pct));
                }
                if (bitstateResult != null) {
                    String pct = reductionPct(bitstateResult.statesExplored(), dfsBitstate);
                    sb.append(String.format("- %s (bitstate): %,d states%s\n", strategy, bitstateResult.statesExplored(), pct));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Formats a bitstate summary section showing false-positive rates
     * and memory metrics for all bitstate results.
     *
     * @return the bitstate summary as a Markdown string, or empty string if no bitstate results
     */
    public String formatBitstateSummary() {
        List<BenchmarkResult> bitstateResults = results.stream()
            .filter(r -> r.storeType() == StoreType.BITSTATE)
            .toList();

        if (bitstateResults.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Bitstate Summary\n\n");
        sb.append("| Bug | Strategy | FPR | Bit Count | Bit Density |\n");
        sb.append("|-----|----------|-----|-----------|-------------|\n");

        for (BenchmarkResult result : bitstateResults) {
            sb.append(String.format("| %s | %s | %s | %,d | %s |\n",
                result.bugName(),
                result.strategy(),
                formatSmallDouble(result.estimatedFalsePositiveRate()),
                result.bitstateBitCount(),
                formatSmallDouble(result.bitstateBitDensity())));
        }

        sb.append("\n");
        return sb.toString();
    }

    private static String reductionPct(long states, long baseline) {
        if (baseline == 0 || states >= baseline) {
            return "";
        }
        double pct = (1.0 - (double) states / baseline) * 100.0;
        if (pct > 0 && pct < 1.0) {
            return " (<1%↓)";
        }
        return String.format(" (%.0f%%↓)", pct);
    }

    private static String formatSmallDouble(double value) {
        if (value == 0.0) return "0.0";
        if (Math.abs(value) < 0.001) {
            return String.format(java.util.Locale.ROOT, "%.2e", value);
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }
}
