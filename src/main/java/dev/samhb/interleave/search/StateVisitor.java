package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;

@FunctionalInterface
public interface StateVisitor {
    void onStateVisited(Configuration config);
}