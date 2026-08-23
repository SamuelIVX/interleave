package dev.samhb.modelcheck.state;

import dev.samhb.modelcheck.core.SharedState;
import java.util.*;

public final class HashingStateStore {
    private final CanonicalEncoder encoder;
    private final Set<Integer> visitedHashes;
    private final Set<String> visitedStates;

    public HashingStateStore() {
        this.encoder = new CanonicalEncoder();
        this.visitedHashes = new HashSet<>();
        this.visitedStates = new HashSet<>();
    }

    public boolean isVisited(SharedState state) {
        int hash = encoder.hashCode(state);
        if (!visitedHashes.contains(hash)) {
            return false;
        }
        String encoded = Base64.getEncoder().encodeToString(encoder.encode(state));
        return visitedStates.contains(encoded);
    }

    public void markVisited(SharedState state) {
        int hash = encoder.hashCode(state);
        visitedHashes.add(hash);
        String encoded = Base64.getEncoder().encodeToString(encoder.encode(state));
        visitedStates.add(encoded);
    }

    public void clear() {
        visitedHashes.clear();
        visitedStates.clear();
    }

    public int size() {
        return visitedStates.size();
    }
}
