package dev.samhb.interleave;

public enum Strategy {
    DFS,
    STATIC_POR,
    DPOR,
    /**
     * Context-bounded search: explores interleavings up to a preemption bound.
     * Yields {@code INCOMPLETE} rather than a pass when the bound is reached.
     */
    CONTEXT_BOUNDED
}
