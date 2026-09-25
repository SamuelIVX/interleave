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

    /** addWrite method. */
    private void addWrite(Lhs lhs, Set<MemoryLocation> w) {
        if (lhs instanceof Lhs.FieldLhs fl) w.add(MemoryLocation.of(fl.name()));
        else if (lhs instanceof Lhs.LocalLhs ll) w.add(MemoryLocation.of("t" + owner + "." + ll.name()));
        else if (lhs instanceof Lhs.ArrayLhs al) {
            if (al.index() instanceof Expr.IntLit lit) w.add(MemoryLocation.of(al.arrayName() + "[" + lit.value() + "]"));
            else w.add(MemoryLocation.of(al.arrayName()));
        }
    }

    /** collectReads method. */
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
    /** enabled method. */
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
    /** execute method. */
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

    @Override
    /** toString method. */
    public String toString() {
        return "DynamicStep{owner=" + owner + (name != null ? ", name=" + name : "") + ", guard=" + guard + ", effects=" + effects + "}";
    }
}
