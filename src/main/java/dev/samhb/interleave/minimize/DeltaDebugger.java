/** Minimizes a schedule while retaining the caller’s failure predicate. */
package dev.samhb.interleave.minimize;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.*;

/** Minimizes a schedule while retaining the caller’s failure predicate. */
public final class DeltaDebugger {
    /** Creates delta debugger with its default configuration. */
    public DeltaDebugger() {}


    /**
     * Minimizes a failing trace using delta debugging (ddmin).
     *
     * Iteratively removes chunks from the trace and checks if the reduced
     * trace still reproduces the expected outcome. Recurses on success
     * until no further reduction is possible.
     *
     * @param program the program to replay traces against
     * @param failingTrace the original failing trace to minimize
     * @param expectedOutcome the expected trace outcome (VIOLATION, DEADLOCK, or COMPLETED)
     * @param invariant the invariant to check for VIOLATION outcomes; may be null
     * @return a minimal subsequence of the original trace that still reproduces the failure
     * @throws IllegalArgumentException if {@code expectedOutcome} is {@link TraceOutcome#INCOMPLETE}
     */
    public Trace minimize(Program program, Trace failingTrace, TraceOutcome expectedOutcome, Invariant invariant) {
        // Bound evidence describes unexplored work, rather than a failure schedule to minimize.
        if (expectedOutcome == TraceOutcome.INCOMPLETE) {
            throw new IllegalArgumentException(
                "Cannot minimize an INCOMPLETE trace: it records exhausted loop or scheduling bounds, "
                + "rather than a reproducible failure");
        }

        if (program.isSource()) return minimizeSource(program, failingTrace, expectedOutcome, invariant);

        List<Integer> threadIds = new ArrayList<>(failingTrace.threadIds());
        List<StepOutcome> outcomes = new ArrayList<>(failingTrace.outcomes());

        int n = threadIds.size();

        for (int m = 2; m <= n; ) {
            for (int i = 0; i < m; i++) {
                int start = (int) ((long) i * n / m);
                int end = (int) ((long) (i + 1) * n / m);

                List<Integer> reducedThreadIds = new ArrayList<>();
                List<StepOutcome> reducedOutcomes = new ArrayList<>();

                for (int j = 0; j < n; j++) {
                    if (j < start || j >= end) {
                        reducedThreadIds.add(threadIds.get(j));
                        reducedOutcomes.add(outcomes.get(j));
                    }
                }

                Trace reducedTrace = Trace.of(reducedThreadIds, reducedOutcomes, expectedOutcome);
                if (isStillFailing(program, reducedTrace, expectedOutcome, invariant)) {
                    return minimize(program, reducedTrace, expectedOutcome, invariant);
                }
            }

            int nextM = Math.min(m * 2, n);
            if (nextM == m) {
                break;
            }
            m = nextM;
        }

        return Trace.of(threadIds, outcomes, expectedOutcome);
    }

    /** Removes schedule chunks while rebuilding actual source outcomes and branch identities.
     * @param program immutable compiled source model
     * @param trace original thread-choice schedule
     * @param expected required real terminal outcome, never INCOMPLETE
     * @param invariant timed property required for predicate-failure reproduction, or null
     * @return accepted smaller schedule with regenerated outcomes/source identities
     * @throws IllegalArgumentException if the original schedule does not reproduce the outcome
     */
    private Trace minimizeSource(Program program, Trace trace, TraceOutcome expected, Invariant invariant) {
        dev.samhb.interleave.core.ScheduleExecution initial = new ExecutionDriver().runDetailed(
            program, new Schedule(trace.threadIds()), invariant);
        if (initial.trace() == null || initial.trace().outcome() != expected) {
            throw new IllegalArgumentException("Source trace does not reproduce requested outcome");
        }
        trace = initial.trace();
        int n = trace.length();
        for (int chunks = 2; chunks <= n; chunks = Math.min(n, chunks * 2)) {
            for (int chunk = 0; chunk < chunks; chunk++) {
                int start = (int) ((long) chunk * n / chunks), end = (int) ((long) (chunk + 1) * n / chunks);
                List<Integer> choices = new ArrayList<>(trace.threadIds());
                choices.subList(start, end).clear();
                try {
                    var candidate = new ExecutionDriver().runDetailed(program, new Schedule(choices), invariant);
                    if (candidate.trace() != null && candidate.trace().outcome() == expected) {
                        return minimizeSource(program, candidate.trace(), expected, invariant);
                    }
                } catch (IllegalScheduleException ignored) {
                    // Removing a chunk can select a disabled or already terminated thread.
                }
            }
            if (chunks == n) break;
        }
        new dev.samhb.interleave.search.TraceReplayer().replay(program, trace, invariant);
        return trace;
    }

    /**
     * Checks whether replaying the given trace still produces the expected outcome.
     *
     * <p>Replays the trace from the initial state using {@link ExecutionDriver} and
     * verifies the final configuration matches the expected outcome. Only thread choices are
     * replayed; recorded step outcomes and the trace's terminal verdict are ignored. Replay
     * stops when the schedule is consumed or all threads terminate.
     *
     * <p>For {@code VIOLATION} outcomes with an invariant, the invariant predicate is evaluated
     * directly regardless of thread termination or deadlock status, ensuring invariant violations
     * occurring in deadlocked terminal configurations are recognized and minimizable. When no invariant
     * is supplied, fallback checks require live threads and non-deadlocked configurations.
     *
     * <p>An {@link IllegalScheduleException} from replay or invariant evaluation produces
     * {@code false}; other runtime exceptions propagate to the caller.
     *
     * @param program the program to replay
     * @param trace the trace to replay
     * @param expectedOutcome the expected outcome to verify
     * @param invariant the invariant to check for VIOLATION outcomes; may be null
     * @return true if the final configuration satisfies the selected outcome check;
     *         false for an illegal schedule or {@code INCOMPLETE}
     */
    boolean isStillFailing(Program program, Trace trace, TraceOutcome expectedOutcome, Invariant invariant) {
        try {
            ExecutionDriver driver = new ExecutionDriver();
            Configuration config = driver.run(program, new Schedule(trace.threadIds()));

            return switch (expectedOutcome) {
                case COMPLETED -> config.allTerminated();
                case DEADLOCK -> config.isDeadlockCandidate();
                case VIOLATION -> invariant != null
                    ? !invariant.holds(config.state(), config)
                    : !config.allTerminated() && !config.isDeadlockCandidate();
                // INCOMPLETE describes the search, not a schedule: it means the preemption bound
                // was reached. No replay of a prefix can reproduce that, so reporting true here
                // would let the minimizer shrink a trace into a shorter one that also claims to be
                // INCOMPLETE -- a strictly meaningless result.
                case INCOMPLETE -> false;
            };
        } catch (IllegalScheduleException e) {
            return false;
        }
    }
}
