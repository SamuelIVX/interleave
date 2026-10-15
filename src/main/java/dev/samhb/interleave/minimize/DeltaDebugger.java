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
        // INCOMPLETE is a property of the search that hit its preemption bound, not of a
        // schedule, so there is nothing for ddmin to reduce: every reduction "reproduces" it by
        // construction. Rejecting it here is clearer than silently returning the input trace.
        if (expectedOutcome == TraceOutcome.INCOMPLETE) {
            throw new IllegalArgumentException(
                "Cannot minimize an INCOMPLETE trace: it records that a context-bounded search "
                + "exhausted its preemption budget, not a schedule that can be reproduced");
        }

        List<Integer> threadIds = new ArrayList<>(failingTrace.threadIds());
        List<StepOutcome> outcomes = new ArrayList<>(failingTrace.outcomes());

        int n = threadIds.size();

        for (int m = 2; m <= n; ) {
            for (int i = 0; i < m; i++) {
                int start = i * n / m;
                int end = (i + 1) * n / m;

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
