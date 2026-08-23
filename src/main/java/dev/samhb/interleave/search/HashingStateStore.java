package dev.samhb.interleave.search;

import dev.samhb.interleave.core.SharedState;
import java.util.*;

public final class HashingStateStore implements StateStore {
    private final Set<Integer> visitedHashes;

    public HashingStateStore() {
        this.visitedHashes = new HashSet<>();
    }

    @Override
    public boolean isVisited(SharedState state) {
        return visitedHashes.contains(Objects.hash(state));
    }

    @Override
    public void markVisited(SharedState state) {
        visitedHashes.add(Objects.hash(state));
    }

    @Override
    public void clear() {
        visitedHashes.clear();
    }
}
