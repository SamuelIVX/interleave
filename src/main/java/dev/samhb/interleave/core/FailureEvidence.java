/** Classified modeled failure independent of rolled-back state values. */
package dev.samhb.interleave.core;

import java.io.Serializable;
import java.util.Objects;

/**
 * Evidence for a real source-model failure.
 * @param kind INVARIANT or RUNTIME_ERROR
 * @param phase STEP or PROPERTY
 * @param source actual failing expression location
 * @param category runtime category, null for false invariants
 */
public record FailureEvidence(Kind kind, Phase phase, SourceLocation source, Category category)
        implements Serializable {
    /** Enforces the distinction between false properties and execution errors. */
    public FailureEvidence {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(phase, "phase");
        if ((kind == Kind.RUNTIME_ERROR) != (category != null)
                || (kind == Kind.INVARIANT && phase != Phase.PROPERTY)) {
            throw new IllegalArgumentException("Inconsistent failure classification");
        }
    }
    /** Failure meaning. */
    public enum Kind { /** False property. */ INVARIANT, /** Modeled evaluation failed. */ RUNTIME_ERROR }
    /** Observation boundary. */
    public enum Phase { /** Attempted instruction. */ STEP, /** Observed configuration. */ PROPERTY }
    /** Deterministic runtime error classes. */
    public enum Category { /** Invalid array index. */ ARRAY_BOUNDS, /** Zero remainder divisor. */ MODULO_BY_ZERO,
        /** Other modeled evaluation failure. */ OTHER_EVALUATION_ERROR }
}
