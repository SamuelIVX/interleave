package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import java.io.Serializable;
import java.util.*;

/** Exploration evidence: visited positions, reached traces, and the number of visit events. */
public final class DfsResult implements Serializable {
    private final Map<String, Configuration> states;
    private final List<Trace> traces;
    private final long statesExplored;

    public DfsResult(Map<String, Configuration> states, List<Trace> traces, long statesExplored) {
        this.states = Map.copyOf(states);
        this.traces = List.copyOf(traces);
        this.statesExplored = statesExplored;
    }

    /**
     * Returns configurations indexed by opaque canonical value keys.
     *
     * <p>DFS and the POR explorers use the base configuration key; context-bounded search extends
     * it with the last scheduled thread. Treat keys as identifiers within this run, not a persisted
     * format or diagnostic text. Reaching one scheduling position at a lower cost can produce more
     * visit events than this map has entries.
     *
     * @return an immutable map of visited positions to their configurations
     */
    public Map<String, Configuration> states() {
        return states;
    }

    public List<Trace> traces() {
        return traces;
    }

    public long statesExplored() {
        return statesExplored;
    }
}
