package dev.samhb.interleave.report;

import java.util.*;

public final class StatesExploredTable {
    private final List<BenchmarkResult> results;

    public StatesExploredTable(List<BenchmarkResult> results) {
        this.results = List.copyOf(results);
    }

    public String formatMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("| Bug | Strategy | States Explored | Wall Time (ms) | Peak Memory (bytes) | Verdict |\n");
        sb.append("|-----|----------|-----------------|----------------|---------------------|---------|\n");
        
        for (BenchmarkResult result : results) {
            sb.append(String.format("| %s | %s | %d | %d | %d | %s |\n",
                result.bugName(),
                result.strategy(),
                result.statesExplored(),
                result.wallTimeMs(),
                result.peakMemoryBytes(),
                result.verdict()));
        }
        
        return sb.toString();
    }

    public String formatReductionTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("## States Explored Reduction Table\n\n");
        
        Map<String, Long> dfsStates = new LinkedHashMap<>();
        Map<String, Long> porStates = new LinkedHashMap<>();
        Map<String, Long> dporStates = new LinkedHashMap<>();
        
        for (BenchmarkResult result : results) {
            long states = result.statesExplored();
            switch (result.strategy()) {
                case "DFS" -> dfsStates.put(result.bugName(), states);
                case "STATIC_POR" -> porStates.put(result.bugName(), states);
                case "DPOR" -> dporStates.put(result.bugName(), states);
            }
        }
        
        for (String bugName : dfsStates.keySet()) {
            long dfs = dfsStates.get(bugName);
            long por = porStates.getOrDefault(bugName, dfs);
            long dpor = dporStates.getOrDefault(bugName, por);
            
            sb.append(String.format("### %s\n\n", bugName));
            sb.append(String.format("- NAIVE: %,d states\n", dfs));
            sb.append(String.format("- HASHING: %,d states\n", dfs));
            sb.append(String.format("- STATIC_POR: %,d states\n", por));
            sb.append(String.format("- DPOR: %,d states\n\n", dpor));
        }
        
        return sb.toString();
    }
}
