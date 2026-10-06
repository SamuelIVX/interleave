/** Supported exploration strategies for shared-memory programs. */
package dev.samhb.interleave;

/** Supported exploration strategies for shared-memory programs. */
public enum Strategy {
    /** Dfs. */
    DFS,
    /** Static por. */
    STATIC_POR,
    /** Dpor. */
    DPOR,
    /**
     * Context-bounded search: explores interleavings up to a preemption bound.
     * Yields {@code INCOMPLETE} rather than a pass when the bound is reached.
     */
    CONTEXT_BOUNDED
}
