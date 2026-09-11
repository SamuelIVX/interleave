package dev.samhb.interleave;

import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.search.TraceOutcome;
import java.io.Serializable;
import java.util.List;

public final class TraceRecord implements Serializable {
    private final List<Integer> threadIds;
    private final List<StepOutcome> outcomes;
    private final TraceOutcome outcome;
    private final String programHash;

    public TraceRecord(List<Integer> threadIds, List<StepOutcome> outcomes, TraceOutcome outcome, String programHash) {
        this.threadIds = List.copyOf(threadIds);
        this.outcomes = List.copyOf(outcomes);
        this.outcome = outcome;
        this.programHash = programHash;
    }

    public List<Integer> threadIds() {
        return threadIds;
    }

    public List<StepOutcome> outcomes() {
        return outcomes;
    }

    public TraceOutcome outcome() {
        return outcome;
    }

    public String programHash() {
        return programHash;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"threads\": [");
        for (int i = 0; i < threadIds.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(threadIds.get(i));
        }
        sb.append("],\n");
        sb.append("  \"outcomes\": [");
        for (int i = 0; i < outcomes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(outcomes.get(i)).append("\"");
        }
        sb.append("],\n");
        sb.append("  \"outcome\": \"").append(outcome).append("\",\n");
        sb.append("  \"programHash\": \"").append(programHash).append("\"\n");
        sb.append("}");
        return sb.toString();
    }
}