package dev.samhb.modelcheck.minimize;

import dev.samhb.modelcheck.core.*;
import dev.samhb.modelcheck.search.*;
import java.util.*;

public final class DeltaDebugger {
    
    public Trace minimize(Program program, Trace failingTrace) {
        List<Integer> threadIds = new ArrayList<>(failingTrace.threadIds());
        List<StepOutcome> outcomes = new ArrayList<>(failingTrace.outcomes());
        
        int n = threadIds.size();
        if (n <= 1) {
            return Trace.of(threadIds, outcomes);
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
                
                Trace reducedTrace = Trace.of(reducedThreadIds, reducedOutcomes);
                if (isStillFailing(program, reducedTrace)) {
                    return minimize(program, reducedTrace);
                }
            }
        }
        
        return Trace.of(threadIds, outcomes);
    }
    
    private boolean isStillFailing(Program program, Trace trace) {
        try {
            ExecutionDriver driver = new ExecutionDriver();
            Configuration config = driver.run(program, new Schedule(trace.threadIds()));
            return config != null;
        } catch (Exception e) {
            return false;
        }
    }
}
