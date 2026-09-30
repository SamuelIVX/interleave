package dev.samhb.interleave;

import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.state.HashingStateStore;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
    private final int maxPreemptions;
    private final boolean iterativeDeepening;

    private InterleaveRunner(Builder builder) {
        this.strategy = builder.strategy;
        this.invariant = builder.invariant;
        this.stateStoreFactory = builder.stateStoreFactory;
        this.maxStates = builder.maxStates;
        this.maxTime = builder.maxTime;
        this.maxPreemptions = builder.maxPreemptions;
        this.iterativeDeepening = builder.iterativeDeepening;
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

        // Lazy: DFS, STATIC_POR and DPOR each need exactly one store, but iterative deepening
        // obtains one per bound and must not trigger an extra factory call here -- that would
        // build a store nothing reads and never clears.
        StateStore[] storeSlot = new StateStore[1];
        Supplier<StateStore> runStore = () -> {
            if (storeSlot[0] == null) {
                storeSlot[0] = stateStoreFactory.get();
            }
            return storeSlot[0];
        };

        LimitState limitState = new LimitState();
        StateVisitor visitor = createLimitEnforcingVisitor(limitState);

        DfsResult result;
        try {
            switch (strategy) {
                case DFS -> {
                    DfsExplorer explorer = new DfsExplorer();
                    result = explorer.explore(program, invariant, runStore.get(), visitor);
                }
                case STATIC_POR -> {
                    StaticPorExplorer explorer = new StaticPorExplorer();
                    result = explorer.explore(program, invariant, runStore.get(), visitor);
                }
                case DPOR -> {
                    DporExplorer explorer = new DporExplorer();
                    result = explorer.explore(program, invariant, runStore.get(), visitor);
                }
                case CONTEXT_BOUNDED -> {
                    result = iterativeDeepening
                        ? runIterativeDeepening(program, invariant, visitor)
                        : new ContextBoundedExplorer().explore(
                              program, invariant, runStore.get(), visitor, maxPreemptions);
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
        private final List<TraceRecord> incompleteTraces;

        PartialResult(long statesExplored, List<TraceRecord> failingTraces,
                      List<TraceRecord> deadlockedTraces, List<TraceRecord> completedTraces) {
            this(statesExplored, failingTraces, deadlockedTraces, completedTraces, List.of());
        }

        PartialResult(long statesExplored, List<TraceRecord> failingTraces,
                      List<TraceRecord> deadlockedTraces, List<TraceRecord> completedTraces,
                      List<TraceRecord> incompleteTraces) {
            this.statesExplored = statesExplored;
            this.failingTraces = List.copyOf(failingTraces);
            this.deadlockedTraces = List.copyOf(deadlockedTraces);
            this.completedTraces = List.copyOf(completedTraces);
            this.incompleteTraces = List.copyOf(incompleteTraces);
        }

        PartialResult withTrace(TraceRecord record) {
            List<TraceRecord> failing = new ArrayList<>(failingTraces);
            List<TraceRecord> deadlocked = new ArrayList<>(deadlockedTraces);
            List<TraceRecord> completed = new ArrayList<>(completedTraces);
            List<TraceRecord> incomplete = new ArrayList<>(incompleteTraces);

            switch (record.outcome()) {
                case VIOLATION -> failing.add(record);
                case DEADLOCK -> deadlocked.add(record);
                case COMPLETED -> completed.add(record);
                case INCOMPLETE -> incomplete.add(record);
            }

            return new PartialResult(statesExplored, failing, deadlocked, completed, incomplete);
        }

        long statesExplored() { return statesExplored; }
        List<TraceRecord> failingTraces() { return failingTraces; }
        List<TraceRecord> deadlockedTraces() { return deadlockedTraces; }
        List<TraceRecord> completedTraces() { return completedTraces; }
        List<TraceRecord> incompleteTraces() { return incompleteTraces; }
    }

    /**
     * Runs the search at increasing preemption bounds, stopping at the first bound that produces a
     * failure, so the returned trace is the one needing the fewest preemptions.
     *
     * <p>{@link LimitExceededException} is deliberately not caught here. It propagates to
     * {@link #run}'s handler, which returns partial results with {@code limitExceeded} set.
     * Swallowing it and continuing to a deeper bound would quietly exceed the caller's budget,
     * which is the one thing a limit must never do.
     *
     * <p>Each bound gets a genuinely fresh store. Reusing one would make deepening <em>narrow</em>
     * rather than widen: states reached at bound 0 are recorded with a count of 0, so every one of
     * them satisfies {@code min <= p} and would be pruned at bound 1.
     */
    private DfsResult runIterativeDeepening(Program program, Invariant invariant, StateVisitor visitor) {
        DfsResult lastResult = null;
        // Identity-based: two distinct stores that happen to compare equal must not be conflated.
        Set<StateStore> seenStores = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int k = 0; k <= maxPreemptions; k++) {
            StateStore freshStore = stateStoreFactory.get();
            if (!seenStores.add(freshStore)) {
                throw new IllegalStateException(
                    "Iterative deepening requires a fresh StateStore per bound, but the configured "
                    + "stateStoreFactory reused an instance. Iterations would share a visited set, "
                    + "so each deeper bound would prune states the previous bound already explored. "
                    + "Use stateStoreFactory(...) with a supplier returning a new store per call.");
            }

            ContextBoundedExplorer explorer = new ContextBoundedExplorer();
            lastResult = explorer.explore(program, invariant, freshStore, visitor, k);

            if (isFailure(lastResult)) {
                return lastResult; // minimal-K trace
            }
            // INCOMPLETE or APPROXIMATE_PASS at this bound: deepen
        }
        return lastResult;
    }

    private static boolean isFailure(DfsResult result) {
        return result.traces().stream().anyMatch(t ->
            t.outcome() == TraceOutcome.VIOLATION || t.outcome() == TraceOutcome.DEADLOCK);
    }

    private TestResult convertToTestResult(DfsResult result, long wallTime, long heapDelta, boolean limitExceeded) {
        List<TraceRecord> failingTraces = new ArrayList<>();
        List<TraceRecord> deadlockedTraces = new ArrayList<>();
        List<TraceRecord> completedTraces = new ArrayList<>();
        List<TraceRecord> incompleteTraces = new ArrayList<>();

        for (Trace trace : result.traces()) {
            TraceRecord record = trace.toRecord();
            switch (trace.outcome()) {
                case VIOLATION -> failingTraces.add(record);
                case DEADLOCK -> deadlockedTraces.add(record);
                case COMPLETED -> completedTraces.add(record);
                case INCOMPLETE -> incompleteTraces.add(record);
            }
        }

        return new TestResult(strategy, result.statesExplored(), wallTime, heapDelta,
                              failingTraces, deadlockedTraces, completedTraces, incompleteTraces, limitExceeded);
    }

    private TestResult convertToTestResult(PartialResult partial, long wallTime, long heapDelta, boolean limitExceeded) {
        return new TestResult(strategy, partial.statesExplored(), wallTime, heapDelta,
                              partial.failingTraces(), partial.deadlockedTraces(),
                              partial.completedTraces(), partial.incompleteTraces(), limitExceeded);
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
        private int maxPreemptions = ContextBoundedExplorer.DEFAULT_MAX_PREEMPTIONS;
        private boolean iterativeDeepening = false;
        // Whether the caller named these explicitly, which is what lets build() reject a
        // context-bounded option on a strategy that would silently ignore it.
        private boolean maxPreemptionsExplicit = false;
        private boolean iterativeDeepeningExplicit = false;

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
         * Sets the preemption bound for {@link Strategy#CONTEXT_BOUNDED}.
         *
         * @param maxPreemptions the bound; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if maxPreemptions is negative
         */
        public Builder maxPreemptions(int maxPreemptions) {
            if (maxPreemptions < 0) throw new IllegalArgumentException("maxPreemptions must be non-negative");
            this.maxPreemptions = maxPreemptions;
            this.maxPreemptionsExplicit = true;
            return this;
        }

        /**
         * Enables iterative deepening for {@link Strategy#CONTEXT_BOUNDED}: run the search at
         * increasing preemption bounds and stop at the first failure, so the returned trace is
         * the one needing the fewest preemptions.
         *
         * @param iterativeDeepening whether to deepen
         * @return this builder
         */
        public Builder iterativeDeepening(boolean iterativeDeepening) {
            this.iterativeDeepening = iterativeDeepening;
            this.iterativeDeepeningExplicit = true;
            return this;
        }

        /**
         * Builds the runner.
         *
         * @return a configured {@link InterleaveRunner}
         * @throws IllegalArgumentException if a context-bounded option was explicitly set on a
         *         strategy that would ignore it
         */
        public InterleaveRunner build() {
            // Only reject an *explicit* set. A caller who never touched these options and chose
            // DFS must keep working; a caller who named a context-bounded option while on another
            // strategy has almost certainly changed strategy and left the option behind, and a
            // run that quietly ignores what was asked for is worse than an exception.
            if (strategy != Strategy.CONTEXT_BOUNDED && maxPreemptionsExplicit) {
                throw new IllegalArgumentException(
                    "maxPreemptions applies only to Strategy.CONTEXT_BOUNDED, but strategy is " + strategy);
            }
            if (strategy != Strategy.CONTEXT_BOUNDED && iterativeDeepeningExplicit) {
                throw new IllegalArgumentException(
                    "iterativeDeepening applies only to Strategy.CONTEXT_BOUNDED, but strategy is " + strategy);
            }
            return new InterleaveRunner(this);
        }
    }
}