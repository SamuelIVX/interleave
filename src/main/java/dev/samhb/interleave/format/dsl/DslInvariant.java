package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.search.Invariant;

import java.util.List;

/**
 * Declarative invariant evaluating one or more predicate expressions over shared state.
 * Supports single-predicate {@code {"expr": "..."}} and conjunction {@code {"all": [...]}} forms
 * with an optional {@code when} timing ({@code "final"} default or {@code "always"}).
 */
public final class DslInvariant implements Invariant {
    /** When the invariant predicate(s) are evaluated. */
    public enum When {
        /** Enforced only at termination (matches typed invariants). */
        FINAL,
        /** Enforced at every reachable configuration. */
        ALWAYS
    }

    private final List<Expr> predicates;
    private final StateDecl decl;
    private final When when;

    /**
     * Creates invariant.
     *
     * @param predicate predicate expression (must be bool)
     * @param decl state declaration
     */
    public DslInvariant(Expr predicate, StateDecl decl) {
        this(List.of(predicate), decl, When.FINAL);
    }

    /**
     * Creates invariant with explicit timing.
     *
     * @param predicate predicate expression (must be bool)
     * @param decl state declaration
     * @param when timing
     */
    public DslInvariant(Expr predicate, StateDecl decl, When when) {
        this(List.of(predicate), decl, when);
    }

    /**
     * Creates conjunction invariant.
     *
     * @param predicates predicate list (1..16, each must be bool)
     * @param decl state declaration
     * @param when timing
     */
    public DslInvariant(List<Expr> predicates, StateDecl decl, When when) {
        if (predicates == null || predicates.isEmpty() || predicates.size() > 16) {
            throw new IllegalArgumentException("predicates must have 1..16 entries");
        }
        this.predicates = List.copyOf(predicates);
        this.decl = decl;
        this.when = when == null ? When.FINAL : when;
    }

    @Override
    /** holds method. */
    public boolean holds(SharedState state, Configuration config) {
        if (!(state instanceof DynamicState ds)) return true;
        if (when == When.FINAL && !config.allTerminated()) return true;
        try {
            for (Expr predicate : predicates) {
                Evaluator.Value v = Evaluator.eval(predicate, ds, 0);
                if (v.type() != FieldType.BOOL) return false;
                if (!v.asBool()) return false;
            }
            return true;
        } catch (Evaluator.EvalException e) {
            // runtime evaluation error in invariant is a violation
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns predicate (first predicate for conjunction form).
     *
     * @return first predicate
     */
    public Expr predicate() { return predicates.get(0); }

    /**
     * Returns all predicates (conjunction).
     *
     * @return predicate list (1..16)
     */
    public List<Expr> predicates() { return predicates; }

    /**
     * Returns timing.
     *
     * @return when
     */
    public When when() { return when; }
}
