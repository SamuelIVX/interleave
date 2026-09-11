package dev.samhb.interleave;

import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class InterleaveRunner implements Serializable {
    private final Strategy strategy;
    private final Invariant invariant;
    private final StateStore stateStore;
    private final long maxStates;
    private final Duration maxTime;

    private InterleaveRunner(Builder builder) {
        this.strategy = builder.strategy;
        this.invariant = builder.invariant;
        this.stateStore = builder.stateStore;
        this.maxStates = builder.maxStates;
        this.maxTime = builder.maxTime;
    }

    public TestResult run(Program program) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        StateVisitor visitor = createLimitEnforcingVisitor();
        
        DfsResult result;
        try {
            switch (strategy) {
                case DFS -> {
                    DfsExplorer explorer = new DfsExplorer();
                    result = explorer.explore(program, invariant, stateStore, visitor);
                }
                case STATIC_POR -> {
                    StaticPorExplorer explorer = new StaticPorExplorer();
                    result = explorer.explore(program, invariant, stateStore, visitor);
                }
                case DPOR -> {
                    DporExplorer explorer = new DporExplorer();
                    result = explorer.explore(program, invariant, stateStore, visitor);
                }
                default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
            }
        } catch (LimitExceededException e) {
            // Explorer was interrupted by limit - we need to get partial results
            // Since the exception was thrown during exploration, we don't have a DfsResult
            // For now, return a partial TestResult with what we know
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long wallTime = System.currentTimeMillis() - start;
            long heapDelta = Math.max(0, memAfter - memBefore);
            
            return new TestResult(strategy, 0, wallTime, heapDelta,
                                  List.of(), List.of(), List.of(), true);
        }

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long heapDelta = Math.max(0, memAfter - memBefore);

        return convertToTestResult(result, wallTime, heapDelta);
    }

    private StateVisitor createLimitEnforcingVisitor() {
        long[] stateCount = {0};
        long startTime = System.currentTimeMillis();
        
        return config -> {
            stateCount[0]++;
            if (maxStates > 0 && stateCount[0] >= maxStates) {
                throw new LimitExceededException("Max states limit exceeded: " + maxStates);
            }
            if (maxTime != null && System.currentTimeMillis() - startTime >= maxTime.toMillis()) {
                throw new LimitExceededException("Max time limit exceeded: " + maxTime);
            }
        };
    }

    private TestResult convertToTestResult(DfsResult result, long wallTime, long heapDelta) {
        List<TraceRecord> failingTraces = new ArrayList<>();
        List<TraceRecord> deadlockedTraces = new ArrayList<>();
        List<TraceRecord> completedTraces = new ArrayList<>();

        String programHash = ""; // Could compute hash from program for replay verification

        for (Trace trace : result.traces()) {
            TraceRecord record = trace.toRecord();
            switch (trace.outcome()) {
                case VIOLATION -> failingTraces.add(record);
                case DEADLOCK -> deadlockedTraces.add(record);
                case COMPLETED -> completedTraces.add(record);
            }
        }

        // Note: limitExceeded is handled by catching LimitExceededException in run()
        // This method is called only on successful completion
        return new TestResult(strategy, result.statesExplored(), wallTime, heapDelta,
                              failingTraces, deadlockedTraces, completedTraces, false);
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