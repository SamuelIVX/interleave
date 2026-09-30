package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.state.HashingStateStore;

/**
 * Interface for tracking visited configurations during state-space exploration.
 * Implementations provide different trade-offs between memory usage and precision:
 * exact stores (e.g., {@link HashingStateStore}) guarantee no false positives,
 * while approximate stores (e.g., {@link BitstateStore}) trade completeness for memory.
 */
public interface StateStore {

    /**
     * Checks whether a configuration has been visited before.
     *
     * @param config the configuration to check
     * @return true if the configuration has been marked as visited, false otherwise
     */
    boolean isVisited(Configuration config);

    /**
     * Marks a configuration as visited.
     *
     * @param config the configuration to mark
     */
    void markVisited(Configuration config);

    /**
     * Preemption-aware variant of {@link #isVisited(Configuration)}, used by
     * context-bounded search.
     *
     * <p>A state is treated as visited at budget {@code preemptions} if it was previously marked
     * with a count of {@code q <= preemptions}: a search that already reached this state with
     * <em>more</em> budget than we have also subsumed everything we could still explore from it.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException} so a store that
     * cannot represent preemption counts fails loudly. Silently delegating to the single-argument
     * form would prune states reached at a different budget, which is exactly the unsound pruning
     * this method exists to avoid.
     *
     * @param config the configuration to check
     * @param lastThreadId the most recently scheduled thread
     * @param preemptions the preemption budget of the current search
     * @return true if the state was already visited within the given budget
     * @throws UnsupportedOperationException if this store is not preemption-aware
     */
    default boolean isVisited(Configuration config, int lastThreadId, int preemptions) {
        throw new UnsupportedOperationException(
            "StateStore does not support preemption-aware visited checks. "
            + "Use HashingStateStore or BitstateStore for context-bounded search.");
    }

    /**
     * Preemption-aware variant of {@link #markVisited(Configuration)}, used by
     * context-bounded search. Records the minimum budget at which this state was reached, so
     * {@link #isVisited(Configuration, int, int)} can answer in constant time.
     *
     * @param config the configuration to mark
     * @param lastThreadId the most recently scheduled thread
     * @param preemptions the preemption budget of the current search
     * @throws UnsupportedOperationException if this store is not preemption-aware
     */
    default void markVisited(Configuration config, int lastThreadId, int preemptions) {
        throw new UnsupportedOperationException(
            "StateStore does not support preemption-aware visited marking. "
            + "Use HashingStateStore or BitstateStore for context-bounded search.");
    }

    /**
     * Clears all visited state, typically called before starting a new exploration.
     */
    void clear();

    /**
     * Creates a fresh, independent copy of this state store for per-run isolation.
     * The default implementation throws {@link UnsupportedOperationException};
     * concrete implementations should override to return a new instance with the same
     * configuration (e.g., same bit-array size and hash-function count).
     *
     * @return a new, empty {@link StateStore} of the same type and configuration
     * @throws UnsupportedOperationException if the implementation does not support copying
     */
    default StateStore freshCopy() {
        throw new UnsupportedOperationException("StateStore does not support freshCopy");
    }
}