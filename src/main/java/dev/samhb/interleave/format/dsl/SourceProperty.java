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
     * @param state dynamic shared state at the observation boundary
     * @param config matching configuration used to determine whether all threads terminated
     * @return detailed false-predicate or runtime error evidence only at observation points
     */
    @Override
    public FailureEvidence failure(SharedState state, Configuration config) {
        if (!always && !config.allTerminated()) return null;
        try {
            return Evaluator.eval(expression, (DynamicState) state, 0).asBool() ? null
                : new FailureEvidence(FailureEvidence.Kind.INVARIANT, FailureEvidence.Phase.PROPERTY, span, null);
        } catch (Evaluator.EvalException | IndexOutOfBoundsException e) {
            FailureEvidence.Category category = Evaluator.failureCategory(e);
            return new FailureEvidence(FailureEvidence.Kind.RUNTIME_ERROR, FailureEvidence.Phase.PROPERTY, span, category);
        }
    }
    /** Returns complete observations for always properties, unknown for termination-dependent ones.
     * @return complete observations for always properties, unknown for termination-dependent ones
     */
    @Override
    public Optional<Set<MemoryLocation>> observedLocations() { return always ? Optional.of(reads) : Optional.empty(); }
}
