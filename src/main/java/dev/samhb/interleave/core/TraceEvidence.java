/** Immutable source identity and terminal diagnostics attached to a trace. */
package dev.samhb.interleave.core;

import java.io.Serializable;
import java.util.List;

/**
 * Optional source diagnostics; legacy traces do not need an instance.
 * @param sourceDigest exact source byte fingerprint
 * @param executedSteps metadata aligned with recorded attempts
 * @param failure source failure, when present
 * @param cutoff separate omitted action, when present
 */
public record TraceEvidence(String sourceDigest, List<ExecutedStep> executedSteps,
                            FailureEvidence failure, CutoffEvidence cutoff) implements Serializable {
    /** Freezes metadata and validates the fingerprint and exclusive terminal causes. */
    public TraceEvidence {
        if (sourceDigest != null && !sourceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid source digest");
        }
        executedSteps = List.copyOf(executedSteps);
        if (failure != null && cutoff != null) throw new IllegalArgumentException("Conflicting terminal evidence");
    }
}
