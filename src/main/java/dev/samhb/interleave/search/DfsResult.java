package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import java.util.*;

public final class DfsResult {
    private final Map<String, Configuration> states;
    private final List<Trace> traces;
    private final long statesExplored;

    public DfsResult(Map<String, Configuration> states, List<Trace> traces, long statesExplored) {
        this.states = Map.copyOf(states);
        this.traces = List.copyOf(traces);
        this.statesExplored = statesExplored;
    }

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
