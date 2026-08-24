package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BugCorpus;
import java.util.*;

public final class ReportWriter {
    private final List<BenchmarkResult> results;

    public ReportWriter(List<BenchmarkResult> results) {
        this.results = List.copyOf(results);
    }

    public String writeMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# interleave Benchmark Report\n\n");
        
        StatesExploredTable table = new StatesExploredTable(results);
        sb.append(table.formatReductionTable());
        sb.append("\n");
        
        sb.append("## Detailed Results\n\n");
        sb.append(table.formatMarkdown());
        sb.append("\n");
        
        SoundnessAttestation attestation = new SoundnessAttestation(results, BugCorpus.all());
        sb.append(attestation.formatMarkdown());
        
        return sb.toString();
    }

    public String writeJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"benchmarks\": [\n");
        
        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult result = results.get(i);
            sb.append("    {\n");
            sb.append(String.format("      \"bug\": \"%s\",\n", result.bugName()));
            sb.append(String.format("      \"strategy\": \"%s\",\n", result.strategy()));
            sb.append(String.format("      \"statesExplored\": %d,\n", result.statesExplored()));
            sb.append(String.format("      \"wallTimeMs\": %d,\n", result.wallTimeMs()));
            sb.append(String.format("      \"heapDeltaBytes\": %d,\n", result.heapDeltaBytes()));
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
}
