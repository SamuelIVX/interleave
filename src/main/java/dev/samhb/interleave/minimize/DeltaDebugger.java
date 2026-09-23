package dev.samhb.interleave.minimize;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.*;

public final class DeltaDebugger {
    
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
     */
    public Trace minimize(Program program, Trace failingTrace, TraceOutcome expectedOutcome, Invariant invariant) {
        List<Integer> threadIds = new ArrayList<>(failingTrace.threadIds());
        List<StepOutcome> outcomes = new ArrayList<>(failingTrace.outcomes());
        
        int n = threadIds.size();
        if (n <= 1) {
            return Trace.of(threadIds, outcomes, expectedOutcome);
        }
        
        for (int m = 2; m <= n; m = m * 2) {
            if (m > n) m = n;
            
            int chunkSize = n / m;
            if (chunkSize == 0) chunkSize = 1;
            
            for (int i = 0; i < m && n - i * chunkSize > 0; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, n);
                
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
        }
        
        return Trace.of(threadIds, outcomes, expectedOutcome);
    }
    
    /**
     * Checks whether replaying the given trace still produces the expected outcome.
     *
     * Replays the trace from the initial state using {@link ExecutionDriver} and
     * verifies the final configuration matches the expected outcome. For VIOLATION
     * outcomes, the invariant is re-checked to ensure the trace genuinely reproduces
     * the bug.
     *
     * @param program the program to replay
     * @param trace the trace to replay
     * @param expectedOutcome the expected outcome to verify
     * @param invariant the invariant to check for VIOLATION outcomes; may be null
     * @return true if the replayed trace reproduces the expected outcome
     */
    private boolean isStillFailing(Program program, Trace trace, TraceOutcome expectedOutcome, Invariant invariant) {
        try {
            ExecutionDriver driver = new ExecutionDriver();
            Configuration config = driver.run(program, new Schedule(trace.threadIds()));
            if (config == null) return false;
            
            return switch (expectedOutcome) {
                case COMPLETED -> config.allTerminated();
                case DEADLOCK -> config.isDeadlockCandidate();
                case VIOLATION -> invariant != null
                    ? !config.allTerminated() && !config.isDeadlockCandidate() && !invariant.holds(config.state(), config)
                    : !config.allTerminated() && !config.isDeadlockCandidate();
            };
        } catch (IllegalScheduleException e) {
            return false;
        }
    }
}
