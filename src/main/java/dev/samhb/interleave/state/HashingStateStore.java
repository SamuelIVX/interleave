package dev.samhb.interleave.state;

import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.search.StateStore;
import java.util.*;

public final class HashingStateStore implements StateStore {
    private final CanonicalEncoder encoder;
    private final Set<Integer> visitedHashes;
    private final Set<String> visitedStates;

    public HashingStateStore() {
        this.encoder = new CanonicalEncoder();
        this.visitedHashes = new HashSet<>();
        this.visitedStates = new HashSet<>();
    }

    @Override
    public boolean isVisited(SharedState state) {
        int hash = encoder.hashCode(state);
        if (!visitedHashes.contains(hash)) {
            return false;
        }
        String encoded = Base64.getEncoder().encodeToString(encoder.encode(state));
        return visitedStates.contains(encoded);
    }

    @Override
    public void markVisited(SharedState state) {
        int hash = encoder.hashCode(state);
        visitedHashes.add(hash);
        String encoded = Base64.getEncoder().encodeToString(encoder.encode(state));
        visitedStates.add(encoded);
    }

    @Override
    public void clear() {
        visitedHashes.clear();
        visitedStates.clear();
    }

    public int size() {
        return visitedStates.size();
    }
}
