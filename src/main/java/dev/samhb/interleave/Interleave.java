/** Static facade for the interleave model checker. Provides ergonomic entry points for program construction, verification, and replay. */
package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.por.*;
import dev.samhb.interleave.dpor.*;
import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.*;
import java.util.function.Supplier;

/**
 * Static facade for the interleave model checker.
 * Provides ergonomic entry points for program construction, verification, and replay.
 */
public final class Interleave {
    /** Prevents instantiation of this utility class. */
    private Interleave() {}

    /**
     * Creates a program from an initial state and thread array.
     *
     * @param state the initial shared state
     * @param threads the model threads (must not be empty)
     * @return program containing the supplied initial state and ordered threads
     * @throws IllegalArgumentException if state is null or threads is null/empty
     */
    public static Program program(SharedState state, ModelThread... threads) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (threads == null || threads.length == 0) throw new IllegalArgumentException("threads must not be empty");
        List<ModelThread> threadList = Arrays.asList(threads);
        return new Program(state, threadList);
    }

    /**
     * Verifies a program with the given strategy (no invariant).
     *
     * @param program the program to verify
     * @param strategy the exploration strategy
     * @return verification result with traces and runtime measurements
     */
    public static VerificationResult verify(Program program, Strategy strategy) {
        return verify(program, strategy, null);
    }

    /**
     * Verifies a program with the given strategy and invariant.
     * Uses the default exact state store ({@link HashingStateStore}).
     *
     * @param program the program to verify
     * @param strategy the exploration strategy
     * @param invariant the invariant to check, or null
     * @return verification result with traces and runtime measurements
     */
    public static VerificationResult verify(Program program, Strategy strategy, Invariant invariant) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        DfsResult result = switch (strategy) {
            case DFS -> new DfsExplorer().explore(program, invariant);
            case STATIC_POR -> new StaticPorExplorer().explore(program, invariant);
            case DPOR -> new DporExplorer().explore(program, invariant);
            case CONTEXT_BOUNDED -> new ContextBoundedExplorer().explore(program, invariant);
        };

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long heapDelta = Math.max(0, memAfter - memBefore);

        return VerificationResult.from(result, strategy, wallTime, heapDelta);
    }

    /**
     * Verifies a program with the given strategy, invariant, and custom state store factory.
     * Allows using approximate state stores like {@link BitstateStore}.
     *
     * @param program the program to verify
     * @param strategy the exploration strategy
     * @param invariant the invariant to check, or null
     * @param stateStoreFactory a factory for creating a fresh state store per verification
     * @return verification result with traces and runtime measurements
     */
    public static VerificationResult verify(Program program, Strategy strategy, Invariant invariant, Supplier<StateStore> stateStoreFactory) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // Resolve once, before dispatch. A null factory means "caller did not choose a store", the
        // same signal the explorer overloads already accept, so it falls back to a default exact
        // store rather than throwing at the caller.
        StateStore store = stateStoreFactory != null ? stateStoreFactory.get() : null;
        DfsResult result = switch (strategy) {
            case DFS -> new DfsExplorer().explore(program, invariant, store, null);
            case STATIC_POR -> new StaticPorExplorer().explore(program, invariant, store, null);
            case DPOR -> new DporExplorer().explore(program, invariant, store, null);
            // Must pass the caller's store through; dropping it would silently substitute a
            // default exact store and discard their configuration.
            case CONTEXT_BOUNDED -> new ContextBoundedExplorer().explore(program, invariant, store, null);
        };

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long heapDelta = Math.max(0, memAfter - memBefore);

        return VerificationResult.from(result, strategy, wallTime, heapDelta);
    }

    /**
     * Verifies a program with an explicit preemption bound.
     *
     * <p>Only meaningful for {@link Strategy#CONTEXT_BOUNDED}; other strategies ignore the bound.
     * Provided because the existing signatures have nowhere to put it, and hardcoding a default
     * would leave library callers with no way to select a bound outside the builder.
     *
     * @param program the program to verify
     * @param strategy the exploration strategy
     * @param invariant the invariant to check, or null
     * @param maxPreemptions the preemption bound; must not be negative
     * @return verification result with traces and runtime measurements
     * @throws IllegalArgumentException if {@code maxPreemptions} is negative
     */
    public static VerificationResult verify(Program program, Strategy strategy,
                                            Invariant invariant, int maxPreemptions) {
        requireNonNegativeBound(maxPreemptions);
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        DfsResult result = switch (strategy) {
            case DFS -> new DfsExplorer().explore(program, invariant);
            case STATIC_POR -> new StaticPorExplorer().explore(program, invariant);
            case DPOR -> new DporExplorer().explore(program, invariant);
            case CONTEXT_BOUNDED -> new ContextBoundedExplorer()
                .explore(program, invariant, null, null, maxPreemptions);
        };

        return timed(result, strategy, start, runtime, memBefore);
    }

    /**
     * Verifies a program with an explicit preemption bound and a custom state store factory.
     *
     * <p>{@code int} and {@link Supplier} are disjoint types, so adding this alongside the
     * factory-only overload leaves {@code verify(p, s, inv, null)} resolving unambiguously to
     * the {@code Supplier} version.
     *
     * @param program the program to verify
     * @param strategy the exploration strategy
     * @param invariant the invariant to check, or null
     * @param stateStoreFactory a factory for creating a fresh state store per verification
     * @param maxPreemptions the preemption bound; must not be negative
     * @return verification result with traces and runtime measurements
     * @throws IllegalArgumentException if {@code maxPreemptions} is negative
     */
    public static VerificationResult verify(Program program, Strategy strategy, Invariant invariant,
                                            Supplier<StateStore> stateStoreFactory, int maxPreemptions) {
        requireNonNegativeBound(maxPreemptions);
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // Resolve once, before dispatch. See the note on the other factory overload: null means
        // "caller did not choose a store".
        StateStore store = stateStoreFactory != null ? stateStoreFactory.get() : null;
        DfsResult result = switch (strategy) {
            case DFS -> new DfsExplorer().explore(program, invariant, store, null);
            case STATIC_POR -> new StaticPorExplorer().explore(program, invariant, store, null);
            case DPOR -> new DporExplorer().explore(program, invariant, store, null);
            case CONTEXT_BOUNDED -> new ContextBoundedExplorer()
                .explore(program, invariant, store, null, maxPreemptions);
        };

        return timed(result, strategy, start, runtime, memBefore);
    }

    /**
     * Rejects a negative preemption bound before search begins.
     * @param maxPreemptions nonnegative preemption bound
     */
    private static void requireNonNegativeBound(int maxPreemptions) {
        if (maxPreemptions < 0) {
            throw new IllegalArgumentException("maxPreemptions must not be negative: " + maxPreemptions);
        }
    }

    /**
     * Adds elapsed time and heap measurements to the completed exploration result.
     * @param result completed exploration result
     * @param strategy exploration strategy used for these results
     * @param start exploration start time in milliseconds from System.currentTimeMillis()
     * @param runtime runtime used to sample heap usage
     * @param memBefore heap usage before exploration in bytes
     * @return verification result annotated with elapsed milliseconds and heap delta
     */
    private static VerificationResult timed(DfsResult result, Strategy strategy, long start,
                                            Runtime runtime, long memBefore) {
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long heapDelta = Math.max(0, memAfter - memBefore);
        return VerificationResult.from(result, strategy, wallTime, heapDelta);
    }

    /**
     * Replays a trace against a program to reconstruct the final configuration.
     *
     * @param program the program
     * @param trace the trace to replay
     * @return the final {@link Configuration} after replaying the trace
     */
    public static Configuration replay(Program program, Trace trace) {
        TraceReplayer replayer = new TraceReplayer();
        return replayer.replay(program, trace);
    }

    /**
     * Quick-check a program with the default strategy ({@link Strategy#DFS}) and exact state store.
     *
     * @param program the program to check
     * @return a {@link TestResult} with states explored, verdict, and traces
     */
    public static TestResult quickCheck(Program program) {
        return InterleaveRunner.builder().build().run(program);
    }

    /**
     * Quick-check a program with the specified strategy and default exact state store.
     *
     * @param program the program to check
     * @param strategy the exploration strategy
     * @return a {@link TestResult}
     */
    public static TestResult quickCheck(Program program, Strategy strategy) {
        return InterleaveRunner.builder().strategy(strategy).build().run(program);
    }

    /**
     * Quick-check a program with the specified strategy and custom state store factory.
     *
     * @param program the program to check
     * @param strategy the exploration strategy
     * @param stateStoreFactory a factory for creating a fresh state store per run
     * @return a {@link TestResult}
     */
    public static TestResult quickCheck(Program program, Strategy strategy, java.util.function.Supplier<StateStore> stateStoreFactory) {
        return InterleaveRunner.builder()
                .strategy(strategy)
                .stateStoreFactory(stateStoreFactory)
                .build()
                .run(program);
    }

    /**
     * Quick-check a program with the default strategy ({@link Strategy#DFS}) and custom state store factory.
     *
     * @param program the program to check
     * @param stateStoreFactory a factory for creating a fresh state store per run
     * @return a {@link TestResult}
     */
    public static TestResult quickCheck(Program program, java.util.function.Supplier<StateStore> stateStoreFactory) {
        return InterleaveRunner.builder()
                .stateStoreFactory(stateStoreFactory)
                .build()
                .run(program);
    }

    /**
     * Returns a builder for creating a reusable, thread-safe {@link InterleaveRunner}.
     *
     * @return a new {@link InterleaveRunner.Builder}
     */
    public static InterleaveRunner.Builder builder() {
        return InterleaveRunner.builder();
    }
}
