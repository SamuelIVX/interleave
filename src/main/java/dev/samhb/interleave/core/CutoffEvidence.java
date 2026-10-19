/** A bounded action or scheduling choice omitted from the committed execution prefix. */
package dev.samhb.interleave.core;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Reproducible bound witness.
 * @param reason cause of omitted exploration
 * @param threadId attempted thread
 * @param instructionIndex attempted outer instruction
 * @param limit exhausted allowance
 * @param iterations completed loop bodies; null for scheduling bounds
 * @param source loop location, if available
 * @param expansionPath enclosing expanded occurrences
 */
public record CutoffEvidence(Reason reason, int threadId, int instructionIndex, int limit,
                             Integer iterations, SourceLocation source,
                             List<ExecutedStep.Expansion> expansionPath) implements Serializable {
    /** Validates the bound and freezes its diagnostic path. */
    public CutoffEvidence {
        Objects.requireNonNull(reason, "reason");
        if (threadId < 0 || instructionIndex < 0 || limit < 0
                || (reason == Reason.LOOP_LIMIT && !Integer.valueOf(limit).equals(iterations))) {
            throw new IllegalArgumentException("Invalid cutoff evidence");
        }
        expansionPath = List.copyOf(expansionPath);
    }
    /** Distinct causes that iterative deepening must not conflate. */
    public enum Reason { /** Source loop still true at its limit. */ LOOP_LIMIT,
        /** Scheduling choice exceeds K. */ PREEMPTION_BOUND }
}
