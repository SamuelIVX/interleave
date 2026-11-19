/** Immutable public trace containing thread choices, step outcomes, and terminal verdict. */
package dev.samhb.interleave;

import dev.samhb.interleave.core.StateSnapshot;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.core.TraceEvidence;
import dev.samhb.interleave.search.TraceOutcome;
import java.io.Serializable;
import java.util.List;
import static dev.samhb.interleave.ResultJson.GSON;

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
    /** State snapshots. */
    private final List<StateSnapshot> states;
    /** Optional immutable BYTECODE diagnostics; source metadata is never fabricated for this profile. */
    private final dev.samhb.interleave.core.BytecodeEvidence bytecodeEvidence;
    /** Optional immutable source diagnostics. */
    private final TraceEvidence evidence;

    /**
     * Creates trace record from the supplied values.
     * @param threadIds ordered thread choices in the trace
     * @param outcomes outcome of each recorded step
     * @param outcome execution outcome being recorded
     * @param programHash diagnostic program fingerprint, if available
     * @throws IllegalArgumentException if thread choices and outcomes differ in length
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
     * @throws IllegalArgumentException if thread choices and outcomes differ in length
     */
    public TraceRecord(List<Integer> threadIds, List<StepOutcome> outcomes, TraceOutcome outcome, String programHash, List<StateSnapshot> states) {
        this(threadIds, outcomes, outcome, programHash, states, null);
    }

    /**
     * Creates an aligned trace with optional source diagnostics.
     * @param threadIds recorded choices
     * @param outcomes recorded outcomes
     * @param outcome terminal verdict
     * @param programHash legacy fingerprint
     * @param states snapshots
     * @param evidence source metadata
     * @throws IllegalArgumentException if thread choices and outcomes differ in length, or
     *        evidence has a source digest but its executed steps do not align with the choices
     */
    public TraceRecord(List<Integer> threadIds, List<StepOutcome> outcomes, TraceOutcome outcome, String programHash,
                       List<StateSnapshot> states, TraceEvidence evidence) {
        this(threadIds,outcomes,outcome,programHash,states,evidence,null);
    }

    /** Creates an aligned record with explicitly separate source or bytecode payloads.
     * Native streams are build-specific; this field addition deliberately changes the generated UID.
     * @param threadIds original selected actors
     * @param outcomes actual coarse operation outcomes
     * @param outcome original terminal category
     * @param programHash diagnostic hash, equal to artifact identity for bytecode
     * @param states immutable display snapshots
     * @param evidence optional source diagnostics
     * @param bytecodeEvidence optional artifact-bound bytecode diagnostics
     * @throws IllegalArgumentException for conflicting profiles or misaligned bytecode data */
    public TraceRecord(List<Integer> threadIds,List<StepOutcome> outcomes,TraceOutcome outcome,String programHash,
                       List<StateSnapshot> states,TraceEvidence evidence,dev.samhb.interleave.core.BytecodeEvidence bytecodeEvidence) {
        this.threadIds = List.copyOf(threadIds);
        this.outcomes = List.copyOf(outcomes);
        this.outcome = outcome;
        this.programHash = programHash;
        this.states = states == null ? List.of() : List.copyOf(states);
        if (threadIds.size() != outcomes.size() || (evidence != null && evidence.sourceDigest() != null
                && evidence.executedSteps().size() != threadIds.size())) throw new IllegalArgumentException("Misaligned trace");
        this.evidence = evidence;this.bytecodeEvidence=bytecodeEvidence;
        if(bytecodeEvidence!=null && (evidence!=null || !java.util.Objects.equals(programHash,bytecodeEvidence.identity().artifactDigest())
            || bytecodeEvidence.transitions().size()!=threadIds.size() || this.states.size()!=threadIds.size()+1))throw new IllegalArgumentException("Conflicting or misaligned BYTECODE record");
    }

    /** Returns distinct artifact-bound bytecode diagnostics, otherwise null.
     * @return immutable BYTECODE payload, never source spans */
    public dev.samhb.interleave.core.BytecodeEvidence bytecodeEvidence(){return bytecodeEvidence;}

    /** Returns source diagnostics, or null for legacy records.
     * @return source diagnostics, or null for legacy records
     */
    public TraceEvidence evidence() { return evidence; }

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
        return new TraceRecord(this.threadIds, this.outcomes, this.outcome, this.programHash, states, evidence,bytecodeEvidence);
    }

    /** Imports a bounded BYTECODE witness without executing model code.
     * The payload remains untrusted until {@code TraceReplayer.replayBytecode} authenticates it.
     * Existing source and declarative import routes retain their own schemas.
     * @param json JSON produced by {@link #toJson()} for a BYTECODE record
     * @return immutable typed trace
     * @throws IllegalArgumentException for malformed, oversized or conflicting evidence
     */
    public static TraceRecord fromJson(String json) { return BytecodeTraceJson.read(json); }

    /**
     * Serializes thread choices, step outcomes, terminal verdict, the optional program fingerprint,
     * and optional state snapshots.
     * @return JSON containing this result’s recorded values
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        if(bytecodeEvidence!=null)sb.append("  \"programKind\": \"BYTECODE\",\n  \"bytecodeEvidence\": ").append(GSON.toJson(bytecodeEvidence)).append(",\n");
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
        if (evidence != null) {
            if (evidence.sourceDigest() != null) {
                sb.append(",\n  \"sourceDigest\": ").append(GSON.toJson(evidence.sourceDigest()));
                sb.append(",\n  \"executedSteps\": ").append(GSON.toJson(evidence.executedSteps()));
            }
            if (evidence.failure() != null) sb.append(",\n  \"failure\": ").append(GSON.toJson(evidence.failure()));
            if (evidence.cutoff() != null) sb.append(",\n  \"cutoff\": ").append(GSON.toJson(evidence.cutoff()));
        }
        sb.append("\n}");
        return bytecodeEvidence == null ? sb.toString() : ResultJson.bytecodeText(sb.toString());
    }
}
