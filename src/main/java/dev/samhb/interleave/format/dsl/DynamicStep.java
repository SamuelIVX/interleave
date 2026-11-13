/** Executes declarative steps with shared expression-derived footprints. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.MemoryLocation;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.Step;
import dev.samhb.interleave.core.StepOutcome;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declarative step with derived reads/writes and sandboxed execution.
 */
public final class DynamicStep implements Step {
    /** Whether loader validation, rather than public construction, issued this instruction. */
    private final boolean certified;
    /** Owner. */
    private final int owner;
    /** Guard. */
    private final Expr guard;
    /** Effects. */
    private final List<Effect> effects;
    /** Decl. */
    private final StateDecl decl;
    /** Reads. */
    private final Set<MemoryLocation> reads;
    /** Writes. */
    private final Set<MemoryLocation> writes;
    /** Name. */
    private final String name;

    /**
     * Creates step.
     *
     * @param owner owning thread id
     * @param guard guard expression or null
     * @param effects effects list (non-empty)
     * @param decl state declaration
     * @param name diagnostic name or null
     */
    public DynamicStep(int owner, Expr guard, List<Effect> effects, StateDecl decl, String name) {
        this(owner, guard, effects, decl, name, false);
    }

    /** Constructs immutable semantics and footprints, retaining loader provenance privately.
     * @param owner original action owner
     * @param guard optional guard
     * @param effects ordered effects
     * @param decl declaration shared with the initial state
     * @param name optional diagnostic label
     * @param certified whether the loader capability authorized construction
     */
    private DynamicStep(int owner, Expr guard, List<Effect> effects, StateDecl decl, String name, boolean certified) {
        this.certified = certified;
        this.owner = owner;
        this.guard = guard;
        this.effects = List.copyOf(effects);
        this.decl = decl;
        this.name = name;
        // derive reads/writes
        Set<MemoryLocation> r = new HashSet<>();
        Set<MemoryLocation> w = new HashSet<>();
        if (guard != null) ExpressionReads.collect(guard, owner, r);
        for (Effect e : effects) {
            // lhs writes
            addWrite(e.lhs(), w);
            // rhs reads
            ExpressionReads.collect(e.rhs(), owner, r);
            // lhs index reads for array
            if (e.lhs() instanceof Lhs.ArrayLhs al) ExpressionReads.collect(al.index(), owner, r);
            if (e.lhs() instanceof Lhs.LocalArrayLhs lal) ExpressionReads.collect(lal.index(), owner, r);
        }
        // Property observations are separate; step footprints describe execution and enabledness.
        this.reads = Set.copyOf(r);
        this.writes = Set.copyOf(w);
    }

    /** Issues trusted semantics only to the loader holding its inaccessible permit.
     * @param permit non-null loader-owned capability
     * @param owner original owner
     * @param guard validated optional boolean guard
     * @param effects validated ordered effects
     * @param decl validated declaration
     * @param name optional diagnostic label
     * @return immutable loader-certified instruction
     * @throws NullPointerException if the caller lacks the loader permit
     */
    static DynamicStep validated(DslLoader.ValidationPermit permit, int owner, Expr guard,
                                 List<Effect> effects, StateDecl decl, String name) {
        Objects.requireNonNull(permit, "loader permit");
        return new DynamicStep(owner, guard, effects, decl, name, true);
    }

    /** Returns the declaration for package-owned complete-model certification.
     * @return immutable declaration identity, not an authorization capability
     */
    StateDecl declaration() { return decl; }

    /** Validates trust and binding before returning complete owner-independent semantics.
     * @param expectedOwner actual containing owner
     * @param expectedDecl declaration shared by the complete model
     * @return ordered shape, or null when any syntax observes tid
     * @throws IllegalArgumentException for an uncertified instruction or foreign binding
     */
    Object symmetryShape(int expectedOwner, StateDecl expectedDecl) {
        if (!certified || owner != expectedOwner || decl != expectedDecl) {
            throw new IllegalArgumentException("Symmetry requires loader-certified declarative owner/declaration bindings");
        }
        if (SourceInstruction.usesTid(guard)) return null;
        for (Effect effect : effects) {
            if (SourceInstruction.usesTid(effect.rhs())
                    || effect.lhs() instanceof Lhs.ArrayLhs a && SourceInstruction.usesTid(a.index())
                    || effect.lhs() instanceof Lhs.LocalArrayLhs a && SourceInstruction.usesTid(a.index())) return null;
        }
        return new Shape(guard, effects);
    }

    /** Counts this already-validated instruction occurrence for reassembled model admission.
     * @return bounded AST node count including guard and assignment indices
     */
    int symmetryNodes() {
        int count = guard == null ? 0 : Expr.nodeCount(guard);
        for (Effect effect : effects) {
            count += Expr.nodeCount(effect.rhs());
            if (effect.lhs() instanceof Lhs.ArrayLhs a) count += Expr.nodeCount(a.index());
            if (effect.lhs() instanceof Lhs.LocalArrayLhs a) count += Expr.nodeCount(a.index());
        }
        return count;
    }

    /** Complete straight-line action semantics without diagnostic identity.
     * @param guard optional validated guard
     * @param effects immutable ordered effects
     */
    private record Shape(Expr guard, List<Effect> effects) {}

    /**
     * Records the memory location an assignment target writes.
     *
     * <p>Locals are namespaced with the owning thread ({@code t0.x}) so a local write cannot be
     * confused with a same-named shared field in the read/write sets a dependency analysis consumes.
     *
     * @param lhs the assignment target
     * @param w the set to add the written location to
     */
    private void addWrite(Lhs lhs, Set<MemoryLocation> w) {
        if (lhs instanceof Lhs.FieldLhs fl) w.add(MemoryLocation.of(fl.name()));
        else if (lhs instanceof Lhs.LocalLhs ll) w.add(MemoryLocation.of("t" + owner + "." + ll.name()));
        else if (lhs instanceof Lhs.ArrayLhs al) {
            if (al.index() instanceof Expr.IntLit lit) w.add(MemoryLocation.of(al.arrayName() + "[" + lit.value() + "]"));
            else w.add(MemoryLocation.of(al.arrayName()));
        } else if (lhs instanceof Lhs.LocalArrayLhs lal) {
            if (lal.index() instanceof Expr.IntLit lit) w.add(MemoryLocation.of("t" + owner + "." + lal.localName() + "[" + lit.value() + "]"));
            else w.add(MemoryLocation.of("t" + owner + "." + lal.localName()));
        }
    }

    /** @return the immutable complete expression/guard read footprint */
    @Override
    public Set<MemoryLocation> reads() { return reads; }

    /** @return the immutable over-approximated assignment destinations */
    @Override
    public Set<MemoryLocation> writes() { return writes; }

    /**
     * Whether this step may run against the given state.
     *
     * <p>Three outcomes, and the distinction between the last two is the whole point:
     *
     * <ul>
     *   <li><b>No guard</b> — always enabled.
     *   <li><b>Guard evaluates false</b> — disabled. This is the ordinary "not this step" case.
     *   <li><b>Guard errors or yields a non-boolean</b> (out-of-bounds field, division by zero, wrong
     *       type) — <em>still enabled</em>, so that {@link #execute} returns
     *       {@link StepOutcome#ASSERTION_FAILED} and the explorers record it as a violation.
     * </ul>
     *
     * <p>An erroring guard is deliberately <em>not</em> treated as a disabled step. Reporting it as
     * "step does not apply here" would silently skip a program whose guard is broken, letting the search
     * report a clean run for a program that never exercised the guarded branch. Surface the error
     * instead.
     *
     * @param state the state to test the guard against
     * @return true if the step may run
     */
    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DynamicState ds)) return false;
        if (guard == null) return true;
        try {
            Evaluator.Value v = Evaluator.eval(guard, ds, owner);
            if (v.type() != FieldType.BOOL) return true; // type mismatch surfaces as violation
            return v.asBool();
        } catch (Evaluator.EvalException e) {
            // Guard OOB / % by zero must surface as VIOLATION, not silent disable.
            // Return true so execute() can report ASSERTION_FAILED (Spec 09).
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Applies the step's effects to the state.
     *
     * <p>The guard is re-evaluated here rather than trusted from {@link #enabled}: between the two
     * calls the state may have changed, since several steps from the same thread are considered in
     * sequence. A guard that has since become false yields {@link StepOutcome#BLOCKED}, and one that
     * now errors yields {@link StepOutcome#ASSERTION_FAILED} — distinct outcomes, so a blocked step is
     * not reported as a failed assertion.
     *
     * @param state the state to mutate
     * @return the outcome of attempting the step
     */
    @Override
    public StepOutcome execute(SharedState state) {
        if (!(state instanceof DynamicState ds)) return StepOutcome.ASSERTION_FAILED;
        // Re-evaluate guard: errors / type mismatch → ASSERTION_FAILED, false → BLOCKED
        if (guard != null) {
            try {
                Evaluator.Value gv = Evaluator.eval(guard, ds, owner);
                if (gv.type() != FieldType.BOOL) return StepOutcome.ASSERTION_FAILED;
                if (!gv.asBool()) return StepOutcome.BLOCKED;
            } catch (Evaluator.EvalException e) {
                return StepOutcome.ASSERTION_FAILED;
            } catch (Exception e) {
                return StepOutcome.ASSERTION_FAILED;
            }
        }
        try {
            for (Effect e : effects) Evaluator.assign(e, ds, owner);
            return StepOutcome.ADVANCED;
        } catch (Evaluator.EvalException e) {
            return StepOutcome.ASSERTION_FAILED;
        } catch (Exception e) {
            return StepOutcome.ASSERTION_FAILED;
        }
    }

    /**
     * Renders owner, name, guard, and effects for failure messages and traces.
     *
     * <p>Diagnostic only; not part of any identity or encoding contract.
     *
     * @return a human-readable rendering of this step
     */
    @Override
    public String toString() {
        return "DynamicStep{owner=" + owner + (name != null ? ", name=" + name : "") + ", guard=" + guard + ", effects=" + effects + "}";
    }
}
