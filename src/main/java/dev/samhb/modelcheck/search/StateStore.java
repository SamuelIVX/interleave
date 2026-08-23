package dev.samhb.modelcheck.search;

import dev.samhb.modelcheck.core.Configuration;
import dev.samhb.modelcheck.core.SharedState;

public interface StateStore {
    boolean isVisited(SharedState state);

    void markVisited(SharedState state);

    void clear();
}
