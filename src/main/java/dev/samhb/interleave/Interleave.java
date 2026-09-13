package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.por.*;
import dev.samhb.interleave.dpor.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Static facade for the interleave model checker.
 * Provides ergonomic entry points for program construction, verification, and replay.
 */
public final class Interleave {
    private Interleave() {}

    /**
     * Creates a program from an initial state and thread array.
     *
     * @param state the initial shared state
     * @param threads the model threads (must not be empty)
     * @return a new {@link Program}
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
     * @return the verification result
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
     * @return the verification result
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
     * @return the verification result
     */
    public static VerificationResult verify(Program program, Strategy strategy, Invariant invariant, Supplier<StateStore> stateStoreFactory) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        StateStore store = stateStoreFactory.get();
        DfsResult result = switch (strategy) {
            case DFS -> new DfsExplorer().explore(program, invariant, store, null);
            case STATIC_POR -> new StaticPorExplorer().explore(program, invariant, store, null);
            case DPOR -> new DporExplorer().explore(program, invariant, store, null);
        };

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