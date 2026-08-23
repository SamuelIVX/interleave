package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.SharedState;

@FunctionalInterface
public interface Invariant {
    boolean holds(SharedState state, Configuration config);
}
