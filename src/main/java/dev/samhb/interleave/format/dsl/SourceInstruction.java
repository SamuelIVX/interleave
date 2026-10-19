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
    private final Node node;
    private final int yes, no, owner;
    private final Set<MemoryLocation> reads, writes;
    /** Freezes targets and recursively derives every control/effect dependency.
     * @param node expanded atomic, decision or limit instruction
     * @param yes strictly forward true/success target
     * @param no strictly forward false target
     * @param owner modeled thread owning local references
     * @param shape validated declarations for shared assignment-footprint rules
     * @param text immutable original source map
     */
    SourceInstruction(Node node, int yes, int no, int owner, StateDecl shape, SourceText text) {
        this.text = text;
        this.node = node; this.yes = yes; this.no = no; this.owner = owner;
        Set<MemoryLocation> r = new HashSet<>(), w = new HashSet<>();
        if (node.kind() == Kind.ATOMIC) footprint(node, owner, shape, r, w);
        else ExpressionReads.collect(node.test(), owner, r);
        this.reads = Set.copyOf(r); this.writes = Set.copyOf(w);
    }
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
    /** Returns false only for a successfully evaluated false outer entry guard.
     * @param state committed or attempt-owned values used only for the outer guard
     * @return false only for a successfully evaluated false outer entry guard; guard errors stay enabled
     */
    @Override
    public boolean enabled(SharedState state) {
        if (node.kind() != Kind.ATOMIC || node.test() == null) return true;
        try { return Evaluator.eval(node.test(), (DynamicState) state, owner).asBool(); }
        catch (RuntimeException e) { return true; }
    }
    /** Executes privately and publishes only a complete successful state.
     * @param threads immutable definitions used to derive the successor
     * @param parent committed configuration; never mutated
     * @param threadId selected owner and PC slot
     * @param scratch isolated mutable state owned by this attempt
     * @return ADVANCED with committed scratch on success; defensive BLOCKED identity if a false
     *         guard is called directly; LOOP_LIMIT with no successor on cutoff; ASSERTION_FAILED
     *         with STEP failure evidence and no successor on a runtime error
     */
    @Override
    public StepTransition transition(List<ModelThread> threads, Configuration parent, int threadId, SharedState scratch) {
        int target = yes;
        try {
            if (node.kind() == Kind.ATOMIC) {
                if (!enabled(scratch)) return new StepTransition(StepOutcome.BLOCKED,
                    parent.successorAt(threadId, StepOutcome.BLOCKED, threads, parent.state(), parent.programCounters().get(threadId)), null, null);
                // Re-evaluate erroring guards so they become failures, not disabled actions.
                if (node.test() != null) evaluate(node.test(), (DynamicState) scratch, owner, node);
                executeNodes(node.yes(), (DynamicState) scratch, owner);
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
        if (label.length() > 256) label = label.substring(0, 256) + "…";
        return new ExecutedStep(pc, text.location(node.start(), node.end()), label, node.path());
    }
    /** Derives stable complete footprints from expressions and assignment semantics.
     * @param node private syntax including both conditional arms
     * @param owner local-state owner
     * @param decl declarations for existing assignment footprint derivation
     * @param reads mutable destination for conditions, guards and assignment reads
     * @param writes mutable destination for all possible assignment writes
     */
    private static void footprint(Node node, int owner, StateDecl decl, Set<MemoryLocation> reads, Set<MemoryLocation> writes) {
        if (node.test() != null) ExpressionReads.collect(node.test(), owner, reads);
        if (node.effect() != null) {
            DynamicStep assignment = new DynamicStep(owner, null, List.of(node.effect()), decl, null);
            reads.addAll(assignment.reads()); writes.addAll(assignment.writes());
        }
        for (Node n : node.yes()) footprint(n, owner, decl, reads, writes);
        for (Node n : node.no()) footprint(n, owner, decl, reads, writes);
    }

    /** Executes expanded atomic syntax without creating scheduler-visible intermediate states.
     * @param nodes private expanded statements in source order
     * @param state attempt-owned tentative state
     * @param owner local-state owner
     * @throws BoundedStop on a true final loop test
     * @throws EvaluationFailure on a modeled runtime error
     */
    private void executeNodes(List<Node> nodes, DynamicState state, int owner) {
        for (Node node : nodes) {
            switch (node.kind()) {
                case ASSIGN -> {
                    try { Evaluator.assign(node.effect(), state, owner); }
                    catch (Evaluator.EvalException | IndexOutOfBoundsException e) { throw evaluationFailure(e, node, FailureEvidence.Phase.STEP); }
                }
                case IF -> executeNodes(evaluate(node.test(), state, owner, node).asBool() ? node.yes() : node.no(), state, owner);
                case LIMIT -> { if (evaluate(node.test(), state, owner, node).asBool()) throw new BoundedStop(node); }
                default -> throw new IllegalStateException("Unexpanded atomic instruction");
            }
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
        private final Node node;
        /** Retains the actual exhausted loop occurrence. */
        private BoundedStop(Node node) { this.node = node; }
    }
    /** Internal classified evaluation failure. */
    private static final class EvaluationFailure extends RuntimeException {
        private final FailureEvidence evidence;
        /** Retains modeled failure evidence rather than a host stack trace in artifacts. */
        private EvaluationFailure(FailureEvidence evidence) { this.evidence = evidence; }
    }

}
