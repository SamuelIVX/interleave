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
    private final int owner;
    private final Expr guard;
    private final List<Effect> effects;
    private final StateDecl decl;
    private final Set<MemoryLocation> reads;
    private final Set<MemoryLocation> writes;
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
        if (guard != null) collectReads(guard, r);
        for (Effect e : effects) {
            // lhs writes
            addWrite(e.lhs(), w);
            // rhs reads
            collectReads(e.rhs(), r);
            // lhs index reads for array
            if (e.lhs() instanceof Lhs.ArrayLhs al) collectReads(al.index(), r);
        }
        // invariant reads not included here; handled via explorer fallback
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

    /**
     * Walks an expression tree and records every location it reads.
     *
     * <p>Local reads are namespaced exactly as in {@link #addWrite}, which is what lets a read of a
     * thread-local match the write performed by the step that owns it.
     *
     * @param expr the expression to walk
     * @param out the set to add read locations to
     */
    private void collectReads(Expr expr, Set<MemoryLocation> out) {
        if (expr instanceof Expr.VarRef v) out.add(MemoryLocation.of(v.name()));
        else if (expr instanceof Expr.LocalRef l) out.add(MemoryLocation.of("t" + owner + "." + l.name()));
        else if (expr instanceof Expr.ArrayAccess a) {
            if (a.arrayName().startsWith("local.")) out.add(MemoryLocation.of("t" + owner + "." + a.arrayName().substring(6)));
            else {
                if (a.index() instanceof Expr.IntLit lit) out.add(MemoryLocation.of(a.arrayName() + "[" + lit.value() + "]"));
                else {
                    out.add(MemoryLocation.of(a.arrayName()));
                    collectReads(a.index(), out);
                }
            }
        } else if (expr instanceof Expr.TidRef) { /* no location */ }
        else if (expr instanceof Expr.IntLit || expr instanceof Expr.BoolLit) { /* none */ }
        else if (expr instanceof Expr.UnaryOp u) collectReads(u.operand(), out);
        else if (expr instanceof Expr.BinaryOp b) {
            collectReads(b.left(), out);
            collectReads(b.right(), out);
        }
    }

    @Override
    public Set<MemoryLocation> reads() { return reads; }

    @Override
    public Set<MemoryLocation> writes() { return writes; }

    @Override
    /**
     * Whether this step may run against the given state.
     *
     * <p>A step with no guard is always enabled. Otherwise the guard is evaluated against the state,
     * and a guard that errors or type-mismatches reports <em>disabled</em> rather than throwing, so a
     * program that tests for an absent field degrades to "not this step" instead of failing the run.
     *
     * @param state the state to test the guard against
     * @return true if the step may run
     */
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

    @Override
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
