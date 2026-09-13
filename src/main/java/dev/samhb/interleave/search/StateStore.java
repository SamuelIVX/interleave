package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;

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