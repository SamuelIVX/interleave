package dev.samhb.interleave;

import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.state.HashingStateStore;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class InterleaveRunner implements Serializable {
    private final Strategy strategy;
    private final Invariant invariant;
    private final StateStore stateStoreTemplate;
    private final long maxStates;
    private final Duration maxTime;

    private InterleaveRunner(Builder builder) {
        this.strategy = builder.strategy;
        this.invariant = builder.invariant;
        this.stateStoreTemplate = builder.stateStore;
        this.maxStates = builder.maxStates;
        this.maxTime = builder.maxTime;
    }

    public TestResult run(Program program) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create fresh StateStore for this run to avoid cross-run contamination
        StateStore runStateStore = createFreshStateStore();
        
        LimitState limitState = new LimitState();
        StateVisitor visitor = createLimitEnforcingVisitor(limitState);
        
        DfsResult result;
        try {
            switch (strategy) {
                case DFS -> {
                    DfsExplorer explorer = new DfsExplorer();
                    result = explorer.explore(program, invariant, runStateStore, visitor);
                }
                case STATIC_POR -> {
                    StaticPorExplorer explorer = new StaticPorExplorer();
                    result = explorer.explore(program, invariant, runStateStore, visitor);
                }
                case DPOR -> {
                    DporExplorer explorer = new DporExplorer();
                    result = explorer.explore(program, invariant, runStateStore, visitor);
                }
                default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
            }
        } catch (LimitExceededException e) {
            // Explorer was interrupted by limit - return partial results from limitState
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long wallTime = System.currentTimeMillis() - start;
            long heapDelta = Math.max(0, memAfter - memBefore);
            
            return convertToTestResult(limitState.partialResult, wallTime, heapDelta, true);
        }

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long heapDelta = Math.max(0, memAfter - memBefore);

        return convertToTestResult(result, wallTime, heapDelta, false);
    }

    private StateStore createFreshStateStore() {
        if (stateStoreTemplate != null) {
            // Try to create a new instance of the same type
            if (stateStoreTemplate instanceof HashingStateStore) {
                return new HashingStateStore();
            }
            // For other store types, use the template if it's stateless, 
            // or fall back to HashingStateStore
            return new HashingStateStore();
        }
        return new HashingStateStore();
    }

    private StateVisitor createLimitEnforcingVisitor(LimitState limitState) {
        long[] stateCount = {0};
        long startTime = System.currentTimeMillis();
        
        return config -> {
            stateCount[0]++;
            limitState.partialResult = new PartialResult(
                stateCount[0],
                limitState.partialResult.failingTraces(),
                limitState.partialResult.deadlockedTraces(),
                limitState.partialResult.completedTraces()
            );
            
            if (maxStates > 0 && stateCount[0] >= maxStates) {
                throw new LimitExceededException("Max states limit exceeded: " + maxStates);
            }
            if (maxTime != null && System.currentTimeMillis() - startTime >= maxTime.toMillis()) {
                throw new LimitExceededException("Max time limit exceeded: " + maxTime);
            }
        };
    }

    // Helper class to capture partial results during limit enforcement
    private static class LimitState {
        PartialResult partialResult = new PartialResult(0, List.of(), List.of(), List.of());
    }
    
    private static class PartialResult {
        private final long statesExplored;
        private final List<TraceRecord> failingTraces;
        private final List<TraceRecord> deadlockedTraces;
        private final List<TraceRecord> completedTraces;
        
        PartialResult(long statesExplored, List<TraceRecord> failingTraces, 
                      List<TraceRecord> deadlockedTraces, List<TraceRecord> completedTraces) {
            this.statesExplored = statesExplored;
            this.failingTraces = failingTraces;
            this.deadlockedTraces = deadlockedTraces;
            this.completedTraces = completedTraces;
        }
        
        long statesExplored() { return statesExplored; }
        List<TraceRecord> failingTraces() { return failingTraces; }
        List<TraceRecord> deadlockedTraces() { return deadlockedTraces; }
        List<TraceRecord> completedTraces() { return completedTraces; }
    }

    private TestResult convertToTestResult(DfsResult result, long wallTime, long heapDelta, boolean limitExceeded) {
        List<TraceRecord> failingTraces = new ArrayList<>();
        List<TraceRecord> deadlockedTraces = new ArrayList<>();
        List<TraceRecord> completedTraces = new ArrayList<>();

        for (Trace trace : result.traces()) {
            TraceRecord record = trace.toRecord();
            switch (trace.outcome()) {
                case VIOLATION -> failingTraces.add(record);
                case DEADLOCK -> deadlockedTraces.add(record);
                case COMPLETED -> completedTraces.add(record);
            }
        }

        return new TestResult(strategy, result.statesExplored(), wallTime, heapDelta,
                              failingTraces, deadlockedTraces, completedTraces, limitExceeded);
    }
    
    private TestResult convertToTestResult(PartialResult partial, long wallTime, long heapDelta, boolean limitExceeded) {
        return new TestResult(strategy, partial.statesExplored(), wallTime, heapDelta,
                              partial.failingTraces(), partial.deadlockedTraces(), 
                              partial.completedTraces(), limitExceeded);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements Serializable {
        private Strategy strategy = Strategy.DFS;
        private Invariant invariant = null;
        private StateStore stateStore = null;
        private long maxStates = 0;
        private Duration maxTime = null;

        public Builder strategy(Strategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder invariant(Invariant invariant) {
            this.invariant = invariant;
            return this;
        }

        public Builder stateStore(StateStore stateStore) {
            this.stateStore = stateStore;
            return this;
        }

        public Builder maxStates(long maxStates) {
            if (maxStates < 0) throw new IllegalArgumentException("maxStates must be non-negative");
            this.maxStates = maxStates;
            return this;
        }

        public Builder maxTime(Duration maxTime) {
            this.maxTime = maxTime;
            return this;
        }

        public InterleaveRunner build() {
            return new InterleaveRunner(this);
        }
    }
}