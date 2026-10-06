/** Runs configured model-checking searches; concurrent isolation depends on the store factory and callbacks. */
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
 * Reusable runner for model-checking programs with configurable per-run isolation.
 * Configure once via {@link #builder()}, then call {@link #run(Program)} multiple times.
 * Concurrent runs require a factory that returns a fresh store and callbacks safe for concurrent use.
 * The concrete-store builder option may reuse a supplied store when freshCopy is unsupported.
 */
public final class InterleaveRunner implements Serializable {
    /** Strategy. */
    private final Strategy strategy;
    /** Invariant. */
    private final Invariant invariant;
    /** State store factory. */
    private final Supplier<StateStore> stateStoreFactory;
    /** Max states. */
    private final long maxStates;
    /** Max time. */
    private final Duration maxTime;
    /** Max preemptions. */
    private final int maxPreemptions;
    /** Iterative deepening. */
    private final boolean iterativeDeepening;

    /**
     * Creates an isolated snapshot of interleave runner from the supplied values.
     * @param builder builder whose settings are captured
     */
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
                        ? runIterativeDeepening(program, invariant, visitor, limitState)
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

    /**
     * Creates a run-local visitor that counts positions and captures traces before enforcing limits.
     * @param limitState run-local counters and interruption state
     * @return visitor sharing the supplied run-local limit counters and captured traces
     */
    private StateVisitor createLimitEnforcingVisitor(LimitState limitState) {
        long[] stateCount = {0};
        long startTime = System.currentTimeMillis();

        return new StateVisitor() {
            /** {@inheritDoc} */
            @Override
            public void onStateVisited(Configuration config) {
                stateCount[0]++;
                // Every bucket must be carried across. Dropping incompleteTraces here silently
                // discarded any INCOMPLETE trace recorded by onTraceCreated as soon as the next
                // state was visited -- which, during a search that also reports completed
                // schedules, is almost immediately.
                limitState.partialResult = new PartialResult(
                    stateCount[0],
                    limitState.partialResult.failingTraces(),
                    limitState.partialResult.deadlockedTraces(),
                    limitState.partialResult.completedTraces(),
                    limitState.partialResult.incompleteTraces()
                );

                if (maxStates > 0 && stateCount[0] >= maxStates) {
                    throw new LimitExceededException("Max states limit exceeded: " + maxStates);
                }
                if (maxTime != null && System.currentTimeMillis() - startTime >= maxTime.toMillis()) {
                    throw new LimitExceededException("Max time limit exceeded: " + maxTime);
                }
            }

            /** {@inheritDoc} */
            @Override
            public void onTraceCreated(Trace trace) {
                TraceRecord record = trace.toRecord();
                limitState.partialResult = limitState.partialResult.withTrace(record);
            }
        };
    }

    // Helper class to capture partial results during limit enforcement
    /** Run-local exploration counters and partial traces captured before limit interruption. */
    private static class LimitState {
        /** Creates limit state with its default configuration. */
        private LimitState() {}

        /** Partial result. */
        PartialResult partialResult = new PartialResult(0, List.of(), List.of(), List.of(), List.of());
    }

    /** Immutable snapshot of visited-count and categorized traces during a limited run. */
    private static class PartialResult {
        /** States explored. */
        private final long statesExplored;
        /** Failing traces. */
        private final List<TraceRecord> failingTraces;
        /** Deadlocked traces. */
        private final List<TraceRecord> deadlockedTraces;
        /** Completed traces. */
        private final List<TraceRecord> completedTraces;
        /** Incomplete traces. */
        private final List<TraceRecord> incompleteTraces;

        /**
         * Creates partial result from the supplied values.
         * @param statesExplored number of explored configurations
         * @param failingTraces traces ending in a property violation
         * @param deadlockedTraces traces ending in deadlock
         * @param completedTraces traces whose threads all terminate
         * @param incompleteTraces traces stopped before a terminal verdict
         */
        PartialResult(long statesExplored, List<TraceRecord> failingTraces,
                      List<TraceRecord> deadlockedTraces, List<TraceRecord> completedTraces,
                      List<TraceRecord> incompleteTraces) {
            this.statesExplored = statesExplored;
            this.failingTraces = List.copyOf(failingTraces);
            this.deadlockedTraces = List.copyOf(deadlockedTraces);
            this.completedTraces = List.copyOf(completedTraces);
            this.incompleteTraces = List.copyOf(incompleteTraces);
        }

        /**
         * Returns partial results with the trace appended to its outcome category.
         * @param record trace record to append
         * @return new partial result with this trace appended to its outcome category
         */
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

        /**
         * Clears the trace buckets while keeping the state count.
         *
         * <p>Used between iterative-deepening bounds. Each bound is an independent top-level
         * search over a fresh store, so it re-discovers the same completed schedules the previous
         * bound found; without clearing, the shared visitor would append each one again and the
         * partial result would carry duplicates. The state count is deliberately preserved, since
         * {@code maxStates} is a total budget across all bounds rather than a per-bound one.
         * @return snapshot retaining the state count with all trace categories empty
         */
        PartialResult withTracesCleared() {
            return new PartialResult(statesExplored, List.of(), List.of(), List.of(), List.of());
        }

        /**
         * Returns states explored for this partial result.
         * @return number of visited search positions
         */
        long statesExplored() { return statesExplored; }
        /**
         * Returns failing traces for this partial result.
         * @return immutable recorded violation traces
         */
        List<TraceRecord> failingTraces() { return failingTraces; }
        /**
         * Returns deadlocked traces for this partial result.
         * @return immutable recorded deadlock traces
         */
        List<TraceRecord> deadlockedTraces() { return deadlockedTraces; }
        /**
         * Returns completed traces for this partial result.
         * @return immutable recorded completed traces
         */
        List<TraceRecord> completedTraces() { return completedTraces; }
        /**
         * Returns incomplete traces for this partial result.
         * @return immutable recorded incomplete traces
         */
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
     * @param program modeled program whose threads are explored
     * @param invariant property to check, or null when no property is supplied
     * @param visitor observer sharing the run’s limits and partial-result capture
     * @param limitState run-local counters and interruption state
     * @return first failing or exhaustive result, or the result at the largest allowed bound
     */
    private DfsResult runIterativeDeepening(Program program, Invariant invariant,
                                          StateVisitor visitor, LimitState limitState) {
        DfsResult lastResult = null;
        // Identity-based: two distinct stores that happen to compare equal must not be conflated.
        Set<StateStore> seenStores = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int k = 0; k <= maxPreemptions; k++) {
            // Each bound is a separate top-level search over a fresh store, so traces found by
            // the previous bound are stale. Cleared per bound; the state counter is not, because
            // maxStates is a total budget across the whole deepening run.
            limitState.partialResult = limitState.partialResult.withTracesCleared();

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
            // An INCOMPLETE trace at this bound is the only thing that justifies deepening: it
            // proves the bound actually pruned something. Without one the search covered the whole
            // reachable space, so every deeper bound re-explores that same space and returns the
            // same verdict while the shared state counter keeps charging maxStates. Returning here
            // is what stops a proven pass from being downgraded to limitExceeded by bounds that
            // cannot change the answer.
            boolean pruned = lastResult.traces().stream()
                .anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE);
            if (!pruned) {
                return lastResult; // exhaustive at k; deeper bounds are redundant
            }
        }
        return lastResult;
    }

    /**
     * Reports whether exploration found a violation or deadlock.
     * @param result completed exploration result
     * @return true if a violation or deadlock was recorded
     */
    private static boolean isFailure(DfsResult result) {
        return result.traces().stream().anyMatch(t ->
            t.outcome() == TraceOutcome.VIOLATION || t.outcome() == TraceOutcome.DEADLOCK);
    }

    /**
     * Converts exploration counts and trace categories into the public result format.
     * @param result completed exploration result
     * @param wallTime elapsed exploration time in milliseconds
     * @param heapDelta observed heap delta in bytes
     * @param limitExceeded whether a configured exploration limit stopped the run
     * @return public result preserving counts, categorized traces, and the interruption flag
     */
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

    /**
     * Converts exploration counts and trace categories into the public result format.
     * @param partial partial results captured before a limit interrupted exploration
     * @param wallTime elapsed exploration time in milliseconds
     * @param heapDelta observed heap delta in bytes
     * @param limitExceeded whether a configured exploration limit stopped the run
     * @return public result preserving counts, categorized traces, and the interruption flag
     */
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
        /** Creates builder with its default configuration. */
        public Builder() {}

        /** Strategy. */
        private Strategy strategy = Strategy.DFS;
        /** Invariant. */
        private Invariant invariant = null;
        /** State store factory. */
        private Supplier<StateStore> stateStoreFactory = HashingStateStore::new;
        /** Max states. */
        private long maxStates = 0;
        /** Max time. */
        private Duration maxTime = null;
        /** Max preemptions. */
        private int maxPreemptions = ContextBoundedExplorer.DEFAULT_MAX_PREEMPTIONS;
        /** Iterative deepening. */
        private boolean iterativeDeepening = false;
        // Whether the caller named these explicitly, which is what lets build() reject a
        // context-bounded option on a strategy that would silently ignore it.
        /** Max preemptions explicit. */
        private boolean maxPreemptionsExplicit = false;
        /** Iterative deepening explicit. */
        private boolean iterativeDeepeningExplicit = false;

        /**
         * Sets the exploration strategy.
         *
         * @param strategy the strategy to use
         * @return this builder for chained configuration
         */
        public Builder strategy(Strategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /**
         * Sets the invariant to check during exploration.
         *
         * @param invariant the invariant, or null for no invariant checking
         * @return this builder for chained configuration
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
