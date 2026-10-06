/** Executes declarative steps with shared expression-derived footprints. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.MemoryLocation;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.Step;
import dev.samhb.interleave.core.StepOutcome;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Declarative step with derived reads/writes and sandboxed execution.
 */
public final class DynamicStep implements Step {
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
        }
        // Property observations are separate; step footprints describe execution and enabledness.
        this.reads = Set.copyOf(r);
        this.writes = Set.copyOf(w);
    }

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
            for (Effect e : effects) {
                Evaluator.Value rhsVal = Evaluator.eval(e.rhs(), ds, owner);
                Lhs lhs = e.lhs();
                if (lhs instanceof Lhs.FieldLhs fl) {
                    FieldDecl fd = decl.findField(fl.name());
                    if (fd == null) return StepOutcome.ASSERTION_FAILED;
                    if (fd.type() == FieldType.INT) {
                        if (rhsVal.type() != FieldType.INT) return StepOutcome.ASSERTION_FAILED;
                        ds.setInt(fl.name(), rhsVal.asInt());
                    } else if (fd.type() == FieldType.BOOL) {
                        if (rhsVal.type() != FieldType.BOOL) return StepOutcome.ASSERTION_FAILED;
                        ds.setBool(fl.name(), rhsVal.asBool());
                    } else return StepOutcome.ASSERTION_FAILED;
                } else if (lhs instanceof Lhs.LocalLhs ll) {
                    LocalDecl ld = decl.findLocal(ll.name());
                    if (ld == null) return StepOutcome.ASSERTION_FAILED;
                    if (ld.type() == FieldType.INT) {
                        if (rhsVal.type() != FieldType.INT) return StepOutcome.ASSERTION_FAILED;
                        ds.setLocalInt(owner, ll.name(), rhsVal.asInt());
                    } else {
                        if (rhsVal.type() != FieldType.BOOL) return StepOutcome.ASSERTION_FAILED;
                        ds.setLocalBool(owner, ll.name(), rhsVal.asBool());
                    }
                } else if (lhs instanceof Lhs.ArrayLhs al) {
                    FieldDecl fd = decl.findField(al.arrayName());
                    if (fd == null || fd.type() != FieldType.INT_ARRAY) return StepOutcome.ASSERTION_FAILED;
                    if (rhsVal.type() != FieldType.INT) return StepOutcome.ASSERTION_FAILED;
                    Evaluator.Value idxVal = Evaluator.eval(al.index(), ds, owner);
                    if (idxVal.type() != FieldType.INT) return StepOutcome.ASSERTION_FAILED;
                    int idx = idxVal.asInt();
                    int[] arr = ds.getArrayRef(al.arrayName());
                    if (idx < 0 || idx >= arr.length) return StepOutcome.ASSERTION_FAILED;
                    arr[idx] = rhsVal.asInt();
                }
            }
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
