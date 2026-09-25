package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.search.Invariant;

/**
 * Declarative invariant evaluating a predicate expression over shared state.
 */
public final class DslInvariant implements Invariant {
    private final Expr predicate;
    private final StateDecl decl;

    /**
     * Creates invariant.
     *
     * @param predicate predicate expression (must be bool)
     * @param decl state declaration
     */
    public DslInvariant(Expr predicate, StateDecl decl) {
        this.predicate = predicate;
        this.decl = decl;
    }

    @Override
    /** holds method. */
    public boolean holds(SharedState state, Configuration config) {
        if (!(state instanceof DynamicState ds)) return true;
        // Match typed invariant semantics: check only at termination
        // For declarative, predicate is evaluated only when all terminated;
        // intermediate states are considered holding to avoid early false positives
        // (e.g., counter == 2 is false at start but should only be checked at end).
        if (!config.allTerminated()) return true;
        try {
            Evaluator.Value v = Evaluator.eval(predicate, ds, 0);
            if (v.type() != FieldType.BOOL) return false;
            return v.asBool();
        } catch (Evaluator.EvalException e) {
            // runtime evaluation error in invariant is a violation
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns predicate.
     *
     * @return predicate
     */
    public Expr predicate() { return predicate; }
}
