package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;

public interface StateStore {
    boolean isVisited(Configuration config);

    void markVisited(Configuration config);

    void clear();
}
