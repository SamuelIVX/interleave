package dev.samhb.interleave.search;

import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.core.Configuration;

@FunctionalInterface
public interface StateVisitor {
    void onStateVisited(Configuration config);
    
    default void onTraceCreated(Trace trace) {
        // Default no-op - implementations can override to capture traces
    }
}