package dev.samhb.interleave.minimize;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.*;

public final class DeltaDebugger {
    
    public Trace minimize(Program program, Trace failingTrace, TraceOutcome expectedOutcome) {
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
                if (isStillFailing(program, reducedTrace, expectedOutcome)) {
                    return minimize(program, reducedTrace, expectedOutcome);
                }
            }
        }
        
        return Trace.of(threadIds, outcomes, expectedOutcome);
    }
    
    private boolean isStillFailing(Program program, Trace trace, TraceOutcome expectedOutcome) {
        try {
            ExecutionDriver driver = new ExecutionDriver();
            Configuration config = driver.run(program, new Schedule(trace.threadIds()));
            if (config == null) return false;
            
            return switch (expectedOutcome) {
                case COMPLETED -> config.allTerminated();
                case DEADLOCK -> config.isDeadlockCandidate();
                case VIOLATION -> !config.allTerminated() && !config.isDeadlockCandidate();
            };
        } catch (Exception e) {
            return false;
        }
    }
}
