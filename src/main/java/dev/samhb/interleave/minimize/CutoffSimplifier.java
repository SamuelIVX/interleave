/** Simplifies an incomplete source prefix without changing the bound witness it explains. */
package dev.samhb.interleave.minimize;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Source-only chunk deletion that preserves the exact omitted-action evidence and bound. */
public final class CutoffSimplifier {
    /** Creates a stateless simplifier; all candidate state belongs to an invocation. */
    public CutoffSimplifier() {}

    /**
     * Removes choices while preserving an authenticated loop or preemption cutoff.
     * The result is deletion-1-minimal, not necessarily globally shortest. It remains INCOMPLETE;
     * unrelated state values and selected branches may change. The input is never modified.
     * Step behavior must be deterministic and mutate only attempt-owned modeled state;
     * supplied predicates must be pure and deterministic, as required for source replay.
     * A null invariant supplies no additional global predicate; callers with a source property
     * must pass it to reject candidates that would fail before the omitted bound action.
     * Candidate testing repeatedly copies schedules and replays source execution. Accepted
     * deletions restart chunk refinement, so long witnesses can require substantial work.
     * Use this opt-in operation to simplify an explanation outside the exploration hot path.
     * <pre>{@code
     * Trace shorter = new CutoffSimplifier().simplify(model.program(), witness,
     *     model.invariant().orElse(null));
     * }</pre>
     * @param program immutable source model that produced the witness
     * @param witness INCOMPLETE trace with source identity and a separate cutoff
     * @param invariant actual timed property, or null when none is supplied
     * @return replay-validated shorter or unchanged witness with the same CutoffEvidence
     * @throws NullPointerException if program or witness is null
     * @throws IllegalArgumentException for legacy programs, non-incomplete traces or absent evidence
     * @throws CorruptedTraceException if the original evidence does not reproduce
     * @throws IllegalScheduleException if the original schedule contains a disabled/invalid choice
     */
    public Trace simplify(Program program, Trace witness, Invariant invariant) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(witness, "witness");
        if (!program.isSource() || witness.outcome() != TraceOutcome.INCOMPLETE || witness.cutoff() == null
                || witness.evidence().sourceDigest() == null) {
            throw new IllegalArgumentException("Requires an INCOMPLETE source trace with source identity and cutoff evidence");
        }
        Invariant property = invariant == null ? (state, config) -> true : invariant;
        TraceReplayer replayer = new TraceReplayer();
        replayer.replay(program, witness, property);
        Trace best = witness;
        // At most one successful deletion per original choice, followed by a final minimality pass.
        for (long remaining = witness.length(); remaining >= 0; remaining--) {
            int length = best.length();
            boolean reduced = false;
            int[] levels = IntStream.range(1, Integer.SIZE)
                .map(power -> (int) Math.min(length, 1L << power)).distinct().toArray();
            for (int chunks : levels) {
                for (int part = 0; part < chunks; part++) {
                    int from = (int) ((long) part * length / chunks);
                    int to = (int) ((long) (part + 1) * length / chunks);
                    List<Integer> choices = new ArrayList<>(best.threadIds());
                    choices.subList(from, to).clear();
                    Trace candidate = candidate(program, choices, witness.cutoff(), property);
                    if (candidate != null && candidate.length() < length) {
                        best = candidate;
                        reduced = true;
                        break;
                    }
                }
                if (reduced) break;
            }
            if (!reduced) break;
        }
        replayer.replay(program, best, property);
        return best;
    }

    /** Rebuilds actual outcomes/source metadata and validates the same omitted action.
     * @param program original immutable source model
     * @param choices candidate prefix after chunk deletion
     * @param cutoff original complete evidence, never adjusted for a shorter prefix
     * @param property actual property or an explicit true predicate when absent
     * @return authenticated candidate, or null for a terminal/invalid prefix or changed cutoff
     */
    private static Trace candidate(Program program, List<Integer> choices, CutoffEvidence cutoff, Invariant property) {
        try {
            ScheduleExecution execution = new ExecutionDriver().runDetailed(program, new Schedule(choices), property);
            if (execution.kind() != ScheduleExecution.Kind.PREFIX) return null;
            Trace trace = Trace.create(program, execution.threadIds(), execution.outcomes(), TraceOutcome.INCOMPLETE, property, cutoff);
            new TraceReplayer().replay(program, trace, property);
            return trace;
        } catch (IllegalScheduleException | CorruptedTraceException rejected) {
            // Deleting choices can disable an action or invalidate its exact source/bound witness.
            return null;
        }
    }
}
