/** Immutable public trace containing thread choices, step outcomes, and terminal verdict. */
package dev.samhb.interleave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.samhb.interleave.core.StateSnapshot;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.search.TraceOutcome;
import java.io.Serializable;
import java.util.List;

/** Immutable public trace containing thread choices, step outcomes, and terminal verdict. */
public final class TraceRecord implements Serializable {
    /** JSON serializer with standard string escaping and HTML escaping disabled. */
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    /** Thread ids. */
    private final List<Integer> threadIds;
    /** Outcomes. */
    private final List<StepOutcome> outcomes;
    /** Outcome. */
    private final TraceOutcome outcome;
    /** Program hash. */
    private final String programHash;
    /** State snapshots. */
    private final List<StateSnapshot> states;

    /**
     * Creates trace record from the supplied values.
     * @param threadIds ordered thread choices in the trace
     * @param outcomes outcome of each recorded step
     * @param outcome execution outcome being recorded
     * @param programHash diagnostic program fingerprint, if available
     */
    public TraceRecord(List<Integer> threadIds, List<StepOutcome> outcomes, TraceOutcome outcome, String programHash) {
        this(threadIds, outcomes, outcome, programHash, List.of());
    }

    /**
     * Creates trace record from the supplied values, including execution state snapshots.
     * @param threadIds ordered thread choices in the trace
     * @param outcomes outcome of each recorded step
     * @param outcome execution outcome being recorded
     * @param programHash diagnostic program fingerprint, if available
     * @param states ordered state snapshots along the execution path
     */
    public TraceRecord(List<Integer> threadIds, List<StepOutcome> outcomes, TraceOutcome outcome, String programHash, List<StateSnapshot> states) {
        this.threadIds = List.copyOf(threadIds);
        this.outcomes = List.copyOf(outcomes);
        this.outcome = outcome;
        this.programHash = programHash;
        this.states = states == null ? List.of() : List.copyOf(states);
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
     * Returns ordered execution state snapshots, if captured.
     * @return immutable ordered state snapshots
     */
    public List<StateSnapshot> states() {
        return states;
    }

    /**
     * Returns a copy of this trace record enriched with the supplied state snapshots.
     * @param states state snapshots along the execution path
     * @return enriched trace record
     */
    public TraceRecord withStates(List<StateSnapshot> states) {
        return new TraceRecord(this.threadIds, this.outcomes, this.outcome, this.programHash, states);
    }

    /**
     * Serializes thread choices, step outcomes, terminal verdict, the optional program fingerprint,
     * and optional state snapshots.
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
        sb.append("  \"programHash\": ").append(GSON.toJson(programHash));
        if (!states.isEmpty()) {
            sb.append(",\n  \"states\": ").append(GSON.toJson(states));
        }
        sb.append("\n}");
        return sb.toString();
    }
}
