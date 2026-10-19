/** A program under verification: an initial shared state plus the model threads that run against it. */
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

    /** Initial state. */
    private final SharedState initialState;
    /** Threads. */
    private final List<ModelThread> threads;
    /** Exact source fingerprint, absent for legacy models. */
    private final String sourceDigest;

    /**
     * Creates a program.
     *
     * <p>The initial state is deep-copied here, and not merely per run. {@link SharedState}
     * implementations are mutable ({@code setFlag}, {@code setTurn}, {@code setInCriticalSection}),
     * and {@link Configuration#initial} consults the supplied state to decide which threads start
     * enabled -- which happens <em>before</em> its own deep copy. Retaining the caller's object
     * would therefore let a mutation between construction and the first
     * {@link #initialConfiguration()} call change which threads a run starts with, which is
     * precisely the aliasing this class documents itself as not having.
     *
     * @param initialState the shared state every run starts from, deep-copied on construction
     * @param threads the model threads, copied defensively; must be non-empty
     * @throws IllegalArgumentException if {@code initialState} is null or {@code threads} is null
     *         or empty
     */
    public Program(SharedState initialState, List<ModelThread> threads) {
        this(initialState, threads, null);
    }

    /**
     * Creates an immutable source model with diagnostic identity.
     * @param initialState isolated initial values
     * @param threads immutable forward instructions; every step must implement {@link SourceStep}
     *        when a source digest is supplied
     * @param sourceDigest exact UTF-8 SHA-256, or null for legacy models
     * @throws IllegalArgumentException if the initial state is null, threads are null or empty,
     *        the digest is not 64 lowercase hexadecimal characters, or a digest accompanies a
     *        step that does not implement {@link SourceStep}
     */
    public Program(SharedState initialState, List<ModelThread> threads, String sourceDigest) {
        if (initialState == null) throw new IllegalArgumentException("initialState must not be null");
        if (threads == null || threads.isEmpty()) throw new IllegalArgumentException("threads must not be empty");
        this.initialState = initialState.deepCopy();
        this.threads = List.copyOf(threads);
        if (sourceDigest != null && !sourceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid source digest");
        }
        if (sourceDigest != null && this.threads.stream().flatMap(thread -> thread.steps().stream())
                .anyMatch(step -> !(step instanceof SourceStep))) {
            throw new IllegalArgumentException("Source digest requires SourceStep instructions");
        }
        this.sourceDigest = sourceDigest;
    }

    /** Returns source fingerprint, or null for a legacy Java/JSON model.
     * @return source fingerprint, or null for a legacy Java/JSON model
     */
    public String sourceDigest() { return sourceDigest; }

    /** Returns whether source execution/reporting semantics apply.
     * @return whether source execution/reporting semantics apply
     */
    public boolean isSource() { return sourceDigest != null; }

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
     * @return requested number of modeled threads
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
