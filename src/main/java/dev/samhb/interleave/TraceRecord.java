/** Immutable public trace containing thread choices, step outcomes, and terminal verdict. */
package dev.samhb.interleave;

import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.search.TraceOutcome;
import java.io.Serializable;
import java.util.List;

/** Immutable public trace containing thread choices, step outcomes, and terminal verdict. */
public final class TraceRecord implements Serializable {
    /** Thread ids. */
    private final List<Integer> threadIds;
    /** Outcomes. */
    private final List<StepOutcome> outcomes;
    /** Outcome. */
    private final TraceOutcome outcome;
    /** Program hash. */
    private final String programHash;

    /**
     * Creates trace record from the supplied values.
     * @param threadIds ordered thread choices in the trace
     * @param outcomes outcome of each recorded step
     * @param outcome execution outcome being recorded
     * @param programHash diagnostic program fingerprint, if available
     */
    public TraceRecord(List<Integer> threadIds, List<StepOutcome> outcomes, TraceOutcome outcome, String programHash) {
        this.threadIds = List.copyOf(threadIds);
        this.outcomes = List.copyOf(outcomes);
        this.outcome = outcome;
        this.programHash = programHash;
    }

    /**
     * Returns thread ids for this trace record.
     * @return immutable ordered thread choices
     */
    public List<Integer> threadIds() {
        return threadIds;
    }

    /**
     * Returns ordered step outcomes aligned with the recorded thread choices.
     * @return immutable step outcomes aligned with the thread choices
     */
    public List<StepOutcome> outcomes() {
        return outcomes;
    }

    /**
     * Returns outcome for this trace record.
     * @return terminal verdict of this trace
     */
    public TraceOutcome outcome() {
        return outcome;
    }

    /**
     * Returns program hash for this trace record.
     * @return recorded program fingerprint, or null when unavailable
     */
    public String programHash() {
        return programHash;
    }

    /**
     * Serializes thread choices, step outcomes, terminal verdict, and the optional program fingerprint.
     * @return JSON containing this result’s recorded values
     */
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
