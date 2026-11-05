/** Persistent source evidence for real traversal edges, without re-executing the prefix. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;
import java.util.*;

/**
 * Immutable source path shared by sibling searches; legacy models reuse the empty value.
 * shortcut: eager metadata can cost more with few traces; profile lazy capture if that is material.
 */
public final class TracePrefix {
    /** Shared empty path; contains no model or mutable search state. */
    private static final TracePrefix EMPTY = new TracePrefix(null, null, null);
    /** Previous committed prefix, absent at the root. */
    private final TracePrefix previous;
    /** Immutable source identity of this attempted action, absent at the root. */
    private final ExecutedStep step;
    /** Actual terminal step failure, absent for successful actions. */
    private final FailureEvidence failure;

    /** Creates a persistent node owned by one real search edge.
     * @param previous parent prefix, or null at the root
     * @param step executed source identity, or null at the root
     * @param failure actual transition failure, or null
     */
    private TracePrefix(TracePrefix previous, ExecutedStep step, FailureEvidence failure) {
        this.previous = previous;
        this.step = step;
        this.failure = failure;
    }

    /** Returns an empty evidence path for a new search.
     * @return immutable root, safely shared across runs
     */
    public static TracePrefix empty() { return EMPTY; }

    /** Captures one real traversal choice (never an independence probe) without executing it again.
     * @param program immutable model
     * @param parent configuration before the attempted action
     * @param threadId selected owner
     * @param execution actual traversal result, never a cutoff
     * @return extended source prefix, or this value for legacy models
     * @throws IllegalArgumentException if the action was omitted at a cutoff
     */
    public TracePrefix append(Program program, Configuration parent, int threadId, StepTransition execution) {
        if (execution.cutoff() != null) throw new IllegalArgumentException("A cutoff is not an executed action");
        if (!program.isSource()) return this;
        int pc = parent.programCounters().get(threadId);
        ExecutedStep identity = ((SourceStep) program.threads().get(threadId).steps().get(pc)).metadata(pc);
        return new TracePrefix(this, identity, execution.failure());
    }

    /** Freezes traversal evidence without replaying the model; legacy factory behavior is preserved.
     * Source callers must supply the matching schedule and actual terminal configuration (the parent
     * for a failed action). The failed action is included in this prefix; omitted cutoffs are not.
     * <pre>{@code
     * Trace trace = TracePrefix.empty().trace(program, List.of(), List.of(),
     *     TraceOutcome.COMPLETED, null, program.initialConfiguration(), null);
     * }</pre>
     * @param program immutable model owning this path
     * @param threadIds recorded choices aligned with this path
     * @param outcomes actual outcomes aligned with those choices
     * @param outcome terminal classification
     * @param invariant property for diagnostic invariant failures, or null
     * @param terminal actual terminal configuration, or failed action's parent
     * @param cutoff separate omitted-action evidence, or null
     * @return immutable trace with unchanged evidence representation
     * @throws IllegalArgumentException if the supplied schedule/evidence is inconsistent
     */
    public Trace trace(Program program, List<Integer> threadIds, List<StepOutcome> outcomes,
                       TraceOutcome outcome, Invariant invariant, Configuration terminal, CutoffEvidence cutoff) {
        if (!program.isSource()) return Trace.create(program, threadIds, outcomes, outcome, invariant, cutoff);
        var steps = new ArrayList<ExecutedStep>(threadIds.size());
        for (TracePrefix node = this; node.step != null; node = node.previous) steps.add(node.step);
        Collections.reverse(steps);
        FailureEvidence diagnostic = failure;
        if (outcome == TraceOutcome.VIOLATION && diagnostic == null) {
            diagnostic = invariant instanceof DiagnosticInvariant detailed ? detailed.failure(terminal.state(), terminal)
                : new FailureEvidence(FailureEvidence.Kind.INVARIANT, FailureEvidence.Phase.PROPERTY, null, null);
        }
        return new Trace(threadIds, outcomes, outcome,
            new TraceEvidence(program.sourceDigest(), steps, diagnostic, cutoff));
    }
}
