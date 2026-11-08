/** Immutable source instructions and transactional atomic execution. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.SourceCompiler.Node;
import dev.samhb.interleave.format.dsl.SourceCompiler.Kind;
import java.util.*;

/** Immutable executable source instruction with stable conservative footprints. */
final class SourceInstruction implements SourceStep {
    /** Immutable source text, independent of parser state. */
    private final SourceText text;
    /** Expanded typed syntax, including diagnostic metadata retained only for actual execution. */
    private final Node node;
    /** Stable helper callsite chain, empty for ordinary source actions. */
    private final String callLabel;
    /** Strictly forward true/success continuation. */
    private final int yes;
    /** Strictly forward false continuation. */
    private final int no;
    /** Original owner used to resolve local references. */
    private final int owner;
    /** Complete conservative reads for ordinary reduction strategies. */
    private final Set<MemoryLocation> reads;
    /** Complete conservative writes for ordinary reduction strategies. */
    private final Set<MemoryLocation> writes;
    /** Whether enabledness needs a bounded preview of private guards. */
    private final boolean guardedInterior;
    /** Freezes targets and recursively derives every control/effect dependency.
     * @param node expanded atomic, decision or limit instruction
     * @param yes strictly forward true/success target
     * @param no strictly forward false target
     * @param owner modeled thread owning local references
     * @param shape validated declarations for shared assignment-footprint rules
     * @param text immutable original source map
     * @param callLabel outermost-first helper callsite annotation for the bounded label
     */
    SourceInstruction(Node node, int yes, int no, int owner, StateDecl shape, SourceText text, String callLabel) {
        this.text = text;
        this.callLabel = callLabel;
        this.node = node; this.yes = yes; this.no = no; this.owner = owner;
        Set<MemoryLocation> r = new HashSet<>(), w = new HashSet<>();
        if (node.kind() == Kind.ATOMIC) guardedInterior = footprint(node, owner, shape, r, w);
        else { guardedInterior = false; ExpressionReads.collect(node.test(), owner, r); }
        this.reads = Set.copyOf(r); this.writes = Set.copyOf(w);
    }
    /** Validates original owner/targets and returns metadata-free execution shape.
     * @param expectedOwner actual containing thread slot
     * @param pc actual instruction position
     * @param end termination position for the containing thread
     * @return immutable structural value, or null when this owner's behavior observes tid
     * @throws IllegalArgumentException for a mismatched binding or non-forward/out-of-range target
     */
    Object symmetryShape(int expectedOwner, int pc, int end) {
        if (owner != expectedOwner || yes <= pc || no <= pc || yes > end || no > end) {
            throw new IllegalArgumentException("Symmetry requires correctly bound forward source instructions");
        }
        SymmetryNode shape = symmetryNode(node);
        return shape == null ? null : new SymmetryInstruction(yes, no, shape);
    }

    /** Removes only diagnostic source identity, retaining every semantic expression/control value.
     * @param node expanded source syntax whose metadata is intentionally ignored
     * @return immutable semantic tree, or null for any nested tid observation
     */
    private static SymmetryNode symmetryNode(Node node) {
        if (usesTid(node.test())) return null;
        Effect effect = node.effect();
        if (effect != null && (usesTid(effect.rhs())
                || effect.lhs() instanceof Lhs.ArrayLhs a && usesTid(a.index())
                || effect.lhs() instanceof Lhs.LocalArrayLhs a && usesTid(a.index()))) return null;
        List<SymmetryNode> yes = node.yes().stream().map(SourceInstruction::symmetryNode).toList();
        List<SymmetryNode> no = node.no().stream().map(SourceInstruction::symmetryNode).toList();
        if (yes.stream().anyMatch(Objects::isNull) || no.stream().anyMatch(Objects::isNull)) return null;
        return new SymmetryNode(node.kind(), effect, node.test(), yes, no, node.count());
    }

    /** Finds identity observations even in short-circuited expressions and array indices.
     * @param expression typed source expression, or null for an absent guard/effect
     * @return true if any expression node observes tid
     */
    private static boolean usesTid(Expr expression) {
        if (expression == null) return false;
        return switch (expression) {
            case Expr.TidRef ignored -> true;
            case Expr.ArrayAccess a -> usesTid(a.index());
            case Expr.UnaryOp u -> usesTid(u.operand());
            case Expr.BinaryOp b -> usesTid(b.left()) || usesTid(b.right());
            case Expr.IntLit ignored -> false;
            case Expr.BoolLit ignored -> false;
            case Expr.VarRef ignored -> false;
            case Expr.LocalRef ignored -> false;
        };
    }

    /** Complete instruction semantics without owner/source identity.
     * @param yes forward true/success target
     * @param no forward false target
     * @param node metadata-free semantic tree
     */
    private record SymmetryInstruction(int yes, int no, SymmetryNode node) {}

    /** Expanded private/scheduled syntax compared structurally, not through diagnostic strings.
     * @param kind execution kind
     * @param effect typed assignment, or null
     * @param test typed guard/condition/assertion, or null
     * @param yes semantic true/body sequence
     * @param no semantic false sequence
     * @param count loop limit used by cutoff execution
     */
    private record SymmetryNode(Kind kind, Effect effect, Expr test,
                               List<SymmetryNode> yes, List<SymmetryNode> no, int count) {}

    /** Returns conservative reads including guards and terminal conditions.
     * @return conservative reads including guards and terminal conditions
     */
    @Override
    public Set<MemoryLocation> reads() { return reads; }
    /** Returns conservative writes across all atomic arms.
     * @return conservative writes across all atomic arms
     */
    @Override
    public Set<MemoryLocation> writes() { return writes; }
    /** Checks whole-action guards without publishing tentative values or terminal evidence.
     * @param state committed values; private guarded work runs only on an isolated copy
     * @return false for the first reached false guard; modeled failures/cutoffs stay enabled
     */
    @Override
    public boolean enabled(SharedState state) {
        if (node.kind() != Kind.ATOMIC) return true;
        try {
            if (node.test() != null && !Evaluator.eval(node.test(), (DynamicState) state, owner).asBool()) return false;
            return !guardedInterior || executeNodes(node.yes(), (DynamicState) state.deepCopy(), owner);
        } catch (RuntimeException e) { return true; }
    }
    /** Executes privately and publishes only a complete successful state.
     * @param threads immutable definitions used to derive the successor
     * @param parent committed configuration; never mutated
     * @param threadId selected owner and PC slot
     * @param scratch isolated mutable state owned by this attempt
     * @return ADVANCED with committed scratch on success; defensive BLOCKED identity if a false
     *         guard is called directly; LOOP_LIMIT with no successor on cutoff; ASSERTION_FAILED
     *         with STEP failure evidence and no successor on a false assertion or runtime error
     */
    @Override
    public StepTransition transition(List<ModelThread> threads, Configuration parent, int threadId, SharedState scratch) {
        int target = yes;
        try {
            if (node.kind() == Kind.ATOMIC) {
                boolean entry = node.test() == null || evaluate(node.test(), (DynamicState) scratch, owner, node).asBool();
                if (!entry || !executeNodes(node.yes(), (DynamicState) scratch, owner)) {
                    return new StepTransition(StepOutcome.BLOCKED,
                        parent.successorAt(threadId, StepOutcome.BLOCKED, threads, parent.state(), parent.programCounters().get(threadId)), null, null);
                }
            } else if (node.kind() == Kind.ASSERT) {
                checkAssertion(node, (DynamicState) scratch, owner);
            } else {
                boolean value = evaluate(node.test(), (DynamicState) scratch, owner, node).asBool();
                if (node.kind() == Kind.LIMIT && value) throw new BoundedStop(node);
                target = value ? yes : no;
            }
            return new StepTransition(StepOutcome.ADVANCED,
                parent.successorAt(threadId, StepOutcome.ADVANCED, threads, scratch, target), null, null);
        } catch (BoundedStop stop) {
            Node loop = stop.node;
            CutoffEvidence evidence = new CutoffEvidence(CutoffEvidence.Reason.LOOP_LIMIT, threadId,
                parent.programCounters().get(threadId), loop.count(), loop.count(), text.location(loop.start(), loop.end()), loop.path());
            return new StepTransition(null, null, null, evidence);
        } catch (EvaluationFailure failure) {
            return new StepTransition(StepOutcome.ASSERTION_FAILED, null, failure.evidence, null);
        }
    }
    /** Returns original source text and expansion identity for this actual PC.
     * @return original source text and expansion identity for this actual PC
     */
    @Override
    public ExecutedStep metadata(int pc) {
        String label = text.source().substring(node.start(), node.end()).strip().lines().findFirst().orElse("atomic {}");
        if (!callLabel.isEmpty()) label = callLabel + label;
        if (label.length() > 256) label = label.substring(0, 256) + "…";
        return new ExecutedStep(pc, text.location(node.start(), node.end()), label, node.path());
    }
    /** Derives stable complete footprints from expressions and assignment semantics.
     * @param node private syntax including both conditional arms
     * @param owner local-state owner
     * @param decl declarations for existing assignment footprint derivation
     * @param reads mutable destination for conditions, guards and assignment reads
     * @param writes mutable destination for all possible assignment writes
     * @return whether any private guard requires tentative-state enabledness evaluation
     */
    private static boolean footprint(Node node, int owner, StateDecl decl, Set<MemoryLocation> reads, Set<MemoryLocation> writes) {
        if (node.test() != null) ExpressionReads.collect(node.test(), owner, reads);
        if (node.effect() != null) {
            DynamicStep assignment = new DynamicStep(owner, null, List.of(node.effect()), decl, null);
            reads.addAll(assignment.reads()); writes.addAll(assignment.writes());
        }
        boolean guarded = node.kind() == Kind.GUARD;
        for (Node n : node.yes()) guarded |= footprint(n, owner, decl, reads, writes);
        for (Node n : node.no()) guarded |= footprint(n, owner, decl, reads, writes);
        return guarded;
    }

    /** Executes expanded atomic syntax without creating scheduler-visible intermediate states.
     * @param nodes private expanded statements in source order
     * @param state attempt-owned tentative state
     * @param owner local-state owner
     * @return false to abort the entire action on a reached false private guard, true otherwise
     * @throws BoundedStop on a true final loop test
     * @throws EvaluationFailure on a false assertion or modeled runtime error
     */
    private boolean executeNodes(List<Node> nodes, DynamicState state, int owner) {
        for (Node node : nodes) {
            switch (node.kind()) {
                case ASSIGN -> {
                    try { Evaluator.assign(node.effect(), state, owner); }
                    catch (Evaluator.EvalException | IndexOutOfBoundsException e) { throw evaluationFailure(e, node, FailureEvidence.Phase.STEP); }
                }
                case ASSERT -> checkAssertion(node, state, owner);
                case IF -> {
                    if (!executeNodes(evaluate(node.test(), state, owner, node).asBool() ? node.yes() : node.no(), state, owner)) return false;
                }
                case GUARD -> {
                    if (!evaluate(node.test(), state, owner, node).asBool() || !executeNodes(node.yes(), state, owner)) return false;
                }
                case LIMIT -> { if (evaluate(node.test(), state, owner, node).asBool()) throw new BoundedStop(node); }
                default -> throw new IllegalStateException("Unexpanded atomic instruction");
            }
        }
        return true;
    }

    /** Evaluates a scheduled or private assertion at its actual observation point.
     * @param node validated assertion with its original statement span
     * @param state committed-copy or tentative outer-atomic state
     * @param owner caller-local owner for expression evaluation
     * @throws EvaluationFailure for a false assertion or modeled expression error
     */
    private void checkAssertion(Node node, DynamicState state, int owner) {
        if (!evaluate(node.test(), state, owner, node).asBool()) {
            throw new EvaluationFailure(new FailureEvidence(FailureEvidence.Kind.ASSERTION,
                FailureEvidence.Phase.STEP, text.location(node.start(), node.end()), null));
        }
    }

    /** Evaluates a source expression while retaining the precise error location.
     * @param expression validated expression
     * @param state current tentative state, read without mutation
     * @param owner local-state owner
     * @param node source construct to identify on error
     * @return existing DSL value
     * @throws EvaluationFailure on modeled evaluation/bounds errors
     */
    private Evaluator.Value evaluate(Expr expression, DynamicState state, int owner, Node node) {
        try { return Evaluator.eval(expression, state, owner); }
        catch (Evaluator.EvalException | IndexOutOfBoundsException e) { throw evaluationFailure(e, node, FailureEvidence.Phase.STEP); }
    }

    /** Classifies deterministic runtime errors without logging arbitrary source/state.
     * @param error evaluator error whose category is inspected locally
     * @param node actual failing source construct
     * @param phase observation boundary
     * @return private exception retaining only classified artifact-safe evidence
     */
    private EvaluationFailure evaluationFailure(RuntimeException error, Node node, FailureEvidence.Phase phase) {
        FailureEvidence.Category category = Evaluator.failureCategory(error);
        return new EvaluationFailure(new FailureEvidence(FailureEvidence.Kind.RUNTIME_ERROR, phase,
            text.location(node.start(), node.end()), category));
    }

    /** Internal noncommitting terminal event; never escapes the transition boundary. */
    private static final class BoundedStop extends RuntimeException {
        /** Actual exhausted loop occurrence with its source identity and limit. */
        private final Node node;
        /** Retains the actual exhausted loop occurrence.
         * @param node expanded exhausted loop and source identity
         */
        private BoundedStop(Node node) { this.node = node; }
    }
    /** Internal classified assertion or evaluation failure. */
    private static final class EvaluationFailure extends RuntimeException {
        /** Artifact-safe classified failure, independent of host stack text. */
        private final FailureEvidence evidence;
        /** Retains modeled failure evidence rather than a host stack trace in artifacts.
         * @param evidence authenticated source failure category and observation point
         */
        private EvaluationFailure(FailureEvidence evidence) { this.evidence = evidence; }
    }

}
