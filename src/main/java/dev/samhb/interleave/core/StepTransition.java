/** Isolated result of one attempted action; terminal attempts have no committed successor. */
package dev.samhb.interleave.core;

/**
 * Shared execution result for all search and replay callers.
 * @param outcome legacy step outcome, null for cutoff
 * @param successor committed next configuration, or null for terminal attempts
 * @param failure classified source error, when present
 * @param cutoff omitted source action, when present
 */
public record StepTransition(StepOutcome outcome, Configuration successor,
                             FailureEvidence failure, CutoffEvidence cutoff) {
    /** Validates mutually exclusive terminal evidence. */
    public StepTransition {
        if (cutoff != null ? outcome != null || successor != null || failure != null
                : outcome == null || (outcome == StepOutcome.ASSERTION_FAILED ? successor != null : successor == null)
                    || (failure != null && outcome != StepOutcome.ASSERTION_FAILED)) {
            throw new IllegalArgumentException("Inconsistent transition result");
        }
    }
}
