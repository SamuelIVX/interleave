/** Simplifies an incomplete source prefix without changing the bound witness it explains. */
package dev.samhb.interleave.minimize;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Source-only chunk deletion and bounded shortest-subsequence search preserving exact cutoff evidence. */
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
        Invariant property = authenticate(program, witness, invariant);
        TraceReplayer replayer = new TraceReplayer();
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

    /**
     * Searches for a shortest valid subsequence of the supplied witness's owner choices.
     * This does not search every legal schedule. Every answer remains INCOMPLETE with the entire
     * original cutoff, including its omitted action and allowance. The original authenticates even
     * at zero budget; an empty original is already shortest. Otherwise zero returns it unproven.
     * Candidate lengths descend, skipping ties after an improvement, so interrupted searches can
     * return a shorter answer without having proved it shortest or deletion-1-minimal.
     *
     * <p>The nonnegative limit counts tested prefixes, not original authentication, replay steps,
     * elapsed time or memory. The complete search can be exponential. Source steps and predicates
     * must obey the same deterministic, attempt-owned/pure contracts as {@link #simplify}.
     * Inputs are not modified; invocation state is local.
     * <pre>{@code
     * var result = new CutoffSimplifier().shortestSubsequence(model.program(), witness,
     *     model.invariant().orElse(null), 10_000);
     * Trace best = result.trace(); // shortest only within the input subsequences if proven
     * boolean provedShortest = result.shortestProven();
     * }</pre>
     * @param program immutable source model that produced the witness
     * @param witness authenticated INCOMPLETE loop/preemption witness
     * @param invariant actual timed property, or null for no additional predicate
     * @param maxCandidates maximum candidate evaluations; zero authenticates without searching
     * @return immutable best-found witness, domain-specific proof status and work counters
     * @throws IllegalArgumentException if the limit is negative or input lacks source cutoff evidence
     * @throws NullPointerException if program or witness is null and the limit is nonnegative
     * @throws CorruptedTraceException if original evidence does not reproduce
     * @throws IllegalScheduleException if an original choice is disabled or invalid
     */
    public ShortestResult shortestSubsequence(Program program, Trace witness, Invariant invariant, long maxCandidates) {
        if (maxCandidates < 0) throw new IllegalArgumentException("maxCandidates must be non-negative");
        Invariant property = authenticate(program, witness, invariant);
        Trace best = witness;
        long checked = 0;
        int count = witness.length();
        if (count == 0) return new ShortestResult(best, true, checked, maxCandidates);
        int length = count - 1;
        int[] indices = IntStream.range(0, length).toArray();
        // Independent guards retain a finite candidate bound under a single progress-counter fault.
        for (long remaining = maxCandidates; remaining > 0 && checked < maxCandidates; remaining--) {
            List<Integer> choices = new ArrayList<>(length);
            for (int index : indices) choices.add(witness.threadIds().get(index));
            checked++;
            Trace candidate = candidate(program, choices, witness.cutoff(), property);
            if (candidate != null) best = candidate;
            // Equal-length ties cannot improve a valid answer; otherwise exhaust this length.
            if (candidate != null || !advance(indices, count)) {
                if (length == 0) return new ShortestResult(best, true, checked, maxCandidates);
                length--;
                indices = IntStream.range(0, length).toArray();
            }
        }
        return new ShortestResult(best, false, checked, maxCandidates);
    }

    /** Advances one fixed-size lexicographic index combination without a recursive search stack.
     * @param indices increasing source indices; updated in place when another combination exists
     * @param count original prefix length, at least indices.length
     * @return true when the next combination is ready, false after the final combination
     */
    private static boolean advance(int[] indices, int count) {
        for (int cursor = indices.length - 1; cursor >= 0; cursor--) {
            if (indices[cursor] < count - indices.length + cursor) {
                indices[cursor]++;
                for (int next = cursor + 1; next < indices.length; next++) indices[next] = indices[next - 1] + 1;
                return true;
            }
        }
        return false;
    }

    /** Authenticates an original before any simplification or zero-budget result is returned.
     * @param program immutable source model
     * @param witness supplied original cutoff trace
     * @param invariant actual property, or null for no additional predicate
     * @return supplied predicate or a pure true predicate for absent property
     * @throws NullPointerException for null program or witness, in that order
     * @throws IllegalArgumentException for non-source/non-incomplete input or missing cutoff identity
     * @throws CorruptedTraceException if original evidence does not reproduce
     * @throws IllegalScheduleException for an invalid original choice
     */
    private static Invariant authenticate(Program program, Trace witness, Invariant invariant) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(witness, "witness");
        if (!program.isSource() || witness.outcome() != TraceOutcome.INCOMPLETE || witness.cutoff() == null
                || witness.evidence().sourceDigest() == null) {
            throw new IllegalArgumentException("Requires an INCOMPLETE source trace with source identity and cutoff evidence");
        }
        Invariant property = invariant == null ? (state, config) -> true : invariant;
        new TraceReplayer().replay(program, witness, property);
        return property;
    }

    /** Immutable search output; optimality concerns only subsequences of the authenticated input.
     * Only this simplifier constructs results. A proof never changes INCOMPLETE into PASS.
     */
    public static final class ShortestResult {
        /** Authenticated best-found witness; may be the unchanged immutable original. */
        private final Trace trace;
        /** Whether every shorter subsequence was excluded or the empty lower bound was reached. */
        private final boolean shortestProven;
        /** Number of evaluated candidates, excluding original authentication. */
        private final long candidatesChecked;
        /** Requested maximum number of evaluated candidates. */
        private final long candidateLimit;

        /** Captures one invocation's authenticated answer and bounded work status.
         * @param trace immutable best-found source cutoff
         * @param shortestProven whether shortest length in the original subsequence domain is proved
         * @param candidatesChecked evaluated candidates, between zero and candidateLimit
         * @param candidateLimit nonnegative requested limit
         */
        private ShortestResult(Trace trace, boolean shortestProven, long candidatesChecked, long candidateLimit) {
            this.trace = trace;
            this.shortestProven = shortestProven;
            this.candidatesChecked = candidatesChecked;
            this.candidateLimit = candidateLimit;
        }

        /** Returns authentic INCOMPLETE evidence with the original entire cutoff.
         * @return immutable shorter or unchanged witness
         */
        public Trace trace() { return trace; }

        /** Reports shortest-length proof only within subsequences of the supplied original.
         * False means the candidate budget stopped proof; the answer may still be shortest.
         * @return whether all shorter candidates were rejected or length zero was reached
         */
        public boolean shortestProven() { return shortestProven; }

        /** Reports candidate work rather than modeled steps, time or original validation work.
         * @return actual candidate evaluations, no greater than candidateLimit
         */
        public long candidatesChecked() { return candidatesChecked; }

        /** Returns the work allowance supplied to this invocation.
         * @return nonnegative maximum candidate evaluations
         */
        public long candidateLimit() { return candidateLimit; }
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
