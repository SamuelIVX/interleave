package dev.samhb.interleave.report;

import java.util.*;

/**
 * Formats benchmark results into Markdown tables.
 * Produces two views:
 * <ul>
 *   <li>{@link #formatMarkdown()} — detailed table with every result row</li>
 *   <li>{@link #formatReductionTable()} — reduction table grouped by bug, then strategy,
 *       showing both exact and bitstate state counts</li>
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
     * Shows both exact and bitstate state counts for each strategy.
     *
     * @return the reduction table as a Markdown string
     */
    public String formatReductionTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("## States Explored Reduction Table\n\n");

        // Group by bugName -> strategy -> storeType -> states
        Map<String, Map<String, Map<StoreType, Long>>> grouped = new LinkedHashMap<>();

        for (BenchmarkResult result : results) {
            grouped.computeIfAbsent(result.bugName(), k -> new LinkedHashMap<>())
                   .computeIfAbsent(result.strategy(), k -> new EnumMap<>(StoreType.class))
                   .put(result.storeType(), result.statesExplored());
        }

        for (Map.Entry<String, Map<String, Map<StoreType, Long>>> bugEntry : grouped.entrySet()) {
            String bugName = bugEntry.getKey();
            Map<String, Map<StoreType, Long>> strategyMap = bugEntry.getValue();

            sb.append(String.format("### %s\n\n", bugName));

            // Order strategies: DFS, STATIC_POR, DPOR
            String[] strategyOrder = {"DFS", "STATIC_POR", "DPOR"};
            for (String strategy : strategyOrder) {
                Map<StoreType, Long> storeMap = strategyMap.get(strategy);
                if (storeMap == null) continue;

                long exactStates = storeMap.getOrDefault(StoreType.EXACT, 0L);
                long bitstateStates = storeMap.getOrDefault(StoreType.BITSTATE, 0L);

                if (exactStates > 0) {
                    sb.append(String.format("- %s (exact): %,d states\n", strategy, exactStates));
                }
                if (bitstateStates > 0) {
                    sb.append(String.format("- %s (bitstate): %,d states\n", strategy, bitstateStates));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}