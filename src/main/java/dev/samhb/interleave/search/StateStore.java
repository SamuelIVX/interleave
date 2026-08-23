package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.SharedState;

public interface StateStore {
    boolean isVisited(SharedState state);

    void markVisited(SharedState state);

    void clear();
}
