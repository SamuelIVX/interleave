package dev.samhb.modelcheck.search;

import dev.samhb.modelcheck.core.Configuration;
import dev.samhb.modelcheck.core.SharedState;

@FunctionalInterface
public interface Invariant {
    boolean holds(SharedState state, Configuration config);
}
