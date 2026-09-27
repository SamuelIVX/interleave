package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.search.Invariant;

import java.util.List;

/**
 * Declarative invariant for {@code format: "declarative"} programs.
 * <p>
 * Evaluates one or more boolean predicates over shared fields and array elements
 * (never {@code local.*} or {@code tid}). Supports the single-predicate form
 * {@code {"expr": "..."}} and the conjunction form {@code {"all": [...]}} (1..16
 * predicates, short-circuit {@code &&}). An optional {@code when} timing selects
 * {@code "final"} (default — predicate enforced only when
 * {@code config.allTerminated()}, matching typed {@code counter_equals}) or
 * {@code "always"} (enforced at every reachable {@code Configuration}).
 * Runtime evaluation errors (OOB, {@code %} by zero) surface as a violation
 * rather than a checker crash.
 * </p>
 */
public final class DslInvariant implements Invariant {
    /**
     * When the invariant predicate(s) are evaluated.
     */
    public enum When {
        /** Enforced only at termination; predicate is true at intermediate states. */
        FINAL,
        /** Enforced at every reachable configuration; transient violations are reported. */
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

    /**
     * Checks whether the invariant holds in the given state and configuration.
     * <p>
     * For {@code when == FINAL}, intermediate configurations are considered holding
     * to avoid spurious failures (e.g., {@code counter == 2} is false at start).
     * For {@code when == ALWAYS}, every configuration is checked. Conjunction
     * predicates are evaluated left-to-right with short-circuit {@code &&}.
     * Runtime evaluation errors return {@code false} so the explorer records a
     * {@code VIOLATION} trace.
     * </p>
     *
     * @param state shared state (expected to be {@link DynamicState})
     * @param config current configuration with program counters and termination info
     * @return {@code true} if the invariant holds, {@code false} if it is violated
     */
    @Override
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
