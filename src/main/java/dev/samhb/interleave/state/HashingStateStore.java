package dev.samhb.interleave.state;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.search.StateStore;
import java.io.Serializable;
import java.util.*;

/**
 * Exact state store using canonical encoding and hash-based deduplication.
 * Guarantees no false positives: if {@code isVisited} returns true, the configuration
 * was definitively visited before. Uses a two-level scheme (hash + full encoding)
 * to avoid hash collisions.
 */
public final class HashingStateStore implements StateStore, Serializable {
    private final CanonicalEncoder encoder;
    private final Set<Integer> visitedHashes;
    private final Set<String> visitedStates;

    /**
     * Creates a new exact state store.
     */
    public HashingStateStore() {
        this.encoder = new CanonicalEncoder();
        this.visitedHashes = new HashSet<>();
        this.visitedStates = new HashSet<>();
    }

    @Override
    public boolean isVisited(Configuration config) {
        int hash = hashCode(config);
        if (!visitedHashes.contains(hash)) {
            return false;
        }
        String encoded = encode(config);
        return visitedStates.contains(encoded);
    }

    @Override
    public void markVisited(Configuration config) {
        int hash = hashCode(config);
        visitedHashes.add(hash);
        String encoded = encode(config);
        visitedStates.add(encoded);
    }

    @Override
    public void clear() {
        visitedHashes.clear();
        visitedStates.clear();
    }

    /**
     * Returns the number of unique configurations stored.
     *
     * @return count of visited states
     */
    public int size() {
        return visitedStates.size();
    }

    private int hashCode(Configuration config) {
        int result = encoder.hashCode(config.state());
        result = 31 * result + config.programCounters().hashCode();
        return result;
    }

    private String encode(Configuration config) {
        String stateEncoded = Base64.getEncoder().encodeToString(encoder.encode(config.state()));
        String pcEncoded = config.programCounters().toString();
        return stateEncoded + "|" + pcEncoded;
    }

    @Override
    public StateStore freshCopy() {
        return new HashingStateStore();
    }
}