/** Optional detailed property result for source diagnostics without changing legacy callbacks. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;

/** A pure property that can distinguish false predicates from evaluation errors. */
public interface DiagnosticInvariant extends Invariant {
    /**
     * Evaluates at the property's configured observation point.
     * @param state modeled values
     * @param config current committed position
     * @return failure evidence, or null when satisfied/not due
     */
    FailureEvidence failure(SharedState state, Configuration config);

    /** Returns whether detailed evaluation reports no failure.
     * @return whether detailed evaluation reports no failure
     */
    @Override default boolean holds(SharedState state, Configuration config) {
        return failure(state, config) == null;
    }
}
