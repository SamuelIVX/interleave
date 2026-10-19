/** Detailed timed source predicates with complete observation footprints. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.DiagnosticInvariant;
import java.util.*;

/** Timed pure property with detailed runtime-error diagnostics. */
final class SourceProperty implements DiagnosticInvariant {
    private final Expr expression;
    private final boolean always;
    private final SourceLocation span;
    private final Set<MemoryLocation> reads;
    /** Freezes property observations and timing.
     * @param expression validated shared-state boolean predicate
     * @param always true for each committed configuration, false for all-terminated only
     * @param span original property source span
     * @param reads complete conservative observed locations, copied immutably
     */
    SourceProperty(Expr expression, boolean always, SourceLocation span, Set<MemoryLocation> reads) {
        this.expression = expression; this.always = always; this.span = span; this.reads = Set.copyOf(reads);
    }
    /** Returns detailed false-predicate or runtime error evidence only at observation points.
     * @return detailed false-predicate or runtime error evidence only at observation points
     */
    public FailureEvidence failure(SharedState state, Configuration config) {
        if (!always && !config.allTerminated()) return null;
        try {
            return Evaluator.eval(expression, (DynamicState) state, 0).asBool() ? null
                : new FailureEvidence(FailureEvidence.Kind.INVARIANT, FailureEvidence.Phase.PROPERTY, span, null);
        } catch (Evaluator.EvalException | IndexOutOfBoundsException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            FailureEvidence.Category category = message.startsWith("% by zero") ? FailureEvidence.Category.MODULO_BY_ZERO
                : e instanceof IndexOutOfBoundsException || message.startsWith("Array index out of bounds")
                    ? FailureEvidence.Category.ARRAY_BOUNDS : FailureEvidence.Category.OTHER_EVALUATION_ERROR;
            return new FailureEvidence(FailureEvidence.Kind.RUNTIME_ERROR, FailureEvidence.Phase.PROPERTY, span, category);
        }
    }
    /** Returns complete observations for always properties, unknown for termination-dependent ones.
     * @return complete observations for always properties, unknown for termination-dependent ones
     */
    public Optional<Set<MemoryLocation>> observedLocations() { return always ? Optional.of(reads) : Optional.empty(); }
}
