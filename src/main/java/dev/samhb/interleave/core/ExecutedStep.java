/** Immutable identity of a recorded source action and its expansion ancestry. */
package dev.samhb.interleave.core;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Diagnostic instruction identity, never configuration identity.
 * @param instructionIndex actual pre-execution PC
 * @param source original outer action span
 * @param label source-derived text
 * @param expansionPath outermost-first repeat/while ancestry
 */
public record ExecutedStep(int instructionIndex, SourceLocation source, String label,
                           List<Expansion> expansionPath) implements Serializable {
    /** Validates identity and freezes ancestry. */
    public ExecutedStep {
        if (instructionIndex < 0) throw new IllegalArgumentException("Negative instruction index");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(label, "label");
        expansionPath = List.copyOf(expansionPath);
    }

    /**
     * One enclosing expanded loop occurrence.
     * @param sourceOffset original UTF-16 keyword offset
     * @param iteration zero-based occurrence, or N for the final while check
     */
    public record Expansion(int sourceOffset, int iteration) implements Serializable {
        /** Rejects negative ancestry values. */
        public Expansion {
            if (sourceOffset < 0 || iteration < 0) throw new IllegalArgumentException("Invalid expansion");
        }
    }
}
