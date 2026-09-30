package dev.samhb.interleave.core;

import java.util.*;

/**
 * A program under verification: an initial shared state plus the model threads that run against it.
 *
 * <p>This is the input to every exploration strategy. The program itself is immutable and carries
 * no search state -- the visited set, the schedule being built and the verdict all live in the
 * explorer -- so one {@code Program} can be run repeatedly under different strategies and stores.
 */
public final class Program {

    private final SharedState initialState;
    private final List<ModelThread> threads;

    /**
     * Creates a program.
     *
     * @param initialState the shared state every run starts from, deep-copied per run
     * @param threads the model threads, copied defensively; must be non-empty
     * @throws IllegalArgumentException if {@code initialState} is null or {@code threads} is null
     *         or empty
     */
    public Program(SharedState initialState, List<ModelThread> threads) {
        if (initialState == null) throw new IllegalArgumentException("initialState must not be null");
        if (threads == null || threads.isEmpty()) throw new IllegalArgumentException("threads must not be empty");
        this.initialState = initialState;
        this.threads = List.copyOf(threads);
    }

    /**
     * Returns the starting configuration, with every thread's program counter at zero.
     *
     * <p>Freshly built on each call, so callers may mutate the returned configuration's state
     * without disturbing later runs.
     *
     * @return the initial configuration
     */
    public Configuration initialConfiguration() {
        return Configuration.initial(initialState, threads);
    }

    /**
     * Returns the number of threads in this program.
     *
     * @return the thread count
     */
    public int threadCount() {
        return threads.size();
    }

    /**
     * Returns the model threads.
     *
     * @return an immutable list
     */
    public List<ModelThread> threads() {
        return threads;
    }
}
