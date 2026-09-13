package dev.samhb.interleave;

import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.state.HashingStateStore;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Reusable, thread-safe runner for model-checking programs.
 * Configure once via {@link #builder()}, then call {@link #run(Program)} multiple times.
 * Each run creates a fresh {@link StateStore} via the configured factory to ensure isolation.
 */
public final class InterleaveRunner implements Serializable {
    private final Strategy strategy;
    private final Invariant invariant;
    private final Supplier<StateStore> stateStoreFactory;
    private final long maxStates;
    private final Duration maxTime;

    private InterleaveRunner(Builder builder) {
        this.strategy = builder.strategy;
        this.invariant = builder.invariant;
        this.stateStoreFactory = builder.stateStoreFactory;
        this.maxStates = builder.maxStates;
        this.maxTime = builder.maxTime;
    }

    /**
     * Runs verification on the given program with the configured strategy and limits.
     * Creates a fresh {@link StateStore} for this run to avoid cross-run contamination.
     *
     * @param program the program to verify
     * @return the test result including states explored, verdict, and traces
     */
    public TestResult run(Program program) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create fresh StateStore for this run using factory to avoid cross-run contamination
        StateStore runStateStore = stateStoreFactory.get();

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
            // Explorer was interrupted by limit - return partial results including traces
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

    private StateVisitor createLimitEnforcingVisitor(LimitState limitState) {
        long[] stateCount = {0};
        long startTime = System.currentTimeMillis();

        return new StateVisitor() {
            @Override
            public void onStateVisited(Configuration config) {
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
            }

            @Override
            public void onTraceCreated(Trace trace) {
                TraceRecord record = trace.toRecord();
                limitState.partialResult = limitState.partialResult.withTrace(record);
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
            this.failingTraces = List.copyOf(failingTraces);
            this.deadlockedTraces = List.copyOf(deadlockedTraces);
            this.completedTraces = List.copyOf(completedTraces);
        }

        PartialResult withTrace(TraceRecord record) {
            List<TraceRecord> failing = new ArrayList<>(failingTraces);
            List<TraceRecord> deadlocked = new ArrayList<>(deadlockedTraces);
            List<TraceRecord> completed = new ArrayList<>(completedTraces);

            switch (record.outcome()) {
                case VIOLATION -> failing.add(record);
                case DEADLOCK -> deadlocked.add(record);
                case COMPLETED -> completed.add(record);
            }

            return new PartialResult(statesExplored, failing, deadlocked, completed);
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

    /**
     * Creates a new {@link Builder} with default configuration:
     * {@link Strategy#DFS}, no invariant, {@link HashingStateStore}, no limits.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link InterleaveRunner}.
     */
    public static class Builder implements Serializable {
        private Strategy strategy = Strategy.DFS;
        private Invariant invariant = null;
        private Supplier<StateStore> stateStoreFactory = HashingStateStore::new;
        private long maxStates = 0;
        private Duration maxTime = null;

        /**
         * Sets the exploration strategy.
         *
         * @param strategy the strategy to use
         * @return this builder
         */
        public Builder strategy(Strategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /**
         * Sets the invariant to check during exploration.
         *
         * @param invariant the invariant, or null for no invariant checking
         * @return this builder
         */
        public Builder invariant(Invariant invariant) {
            this.invariant = invariant;
            return this;
        }

        /**
         * Sets the state store by wrapping a concrete instance in a supplier.
         * Attempts to use {@link StateStore#freshCopy()} for per-run isolation;
         * falls back to the shared instance if the store doesn't support copying.
         * For full isolation, prefer {@link #stateStoreFactory(Supplier)}.
         *
         * @param stateStore a state store instance
         * @return this builder
         */
        public Builder stateStore(StateStore stateStore) {
            // Accept a concrete instance and wrap in supplier for backward compatibility
            // For true per-run isolation, prefer stateStoreFactory()
            // Uses freshCopy() if available; falls back to shared instance for unsupported types
            this.stateStoreFactory = () -> {
                try {
                    return stateStore.freshCopy();
                } catch (UnsupportedOperationException e) {
                    // Fallback for StateStore implementations without freshCopy()
                    return stateStore;
                }
            };
            return this;
        }

        /**
         * Sets the state store factory. Called once per {@link #run(Program)} to create
         * a fresh store, ensuring complete isolation between runs.
         *
         * @param factory a supplier of new {@link StateStore} instances
         * @return this builder
         */
        public Builder stateStoreFactory(Supplier<StateStore> factory) {
            this.stateStoreFactory = factory;
            return this;
        }

        /**
         * Sets the maximum number of states to explore before stopping.
         *
         * @param maxStates the limit (0 = no limit)
         * @return this builder
         * @throws IllegalArgumentException if maxStates is negative
         */
        public Builder maxStates(long maxStates) {
            if (maxStates < 0) throw new IllegalArgumentException("maxStates must be non-negative");
            this.maxStates = maxStates;
            return this;
        }

        /**
         * Sets the maximum wall-clock time before stopping.
         *
         * @param maxTime the time limit, or null for no limit
         * @return this builder
         */
        public Builder maxTime(Duration maxTime) {
            this.maxTime = maxTime;
            return this;
        }

        /**
         * Builds the runner.
         *
         * @return a configured {@link InterleaveRunner}
         */
        public InterleaveRunner build() {
            return new InterleaveRunner(this);
        }
    }
}