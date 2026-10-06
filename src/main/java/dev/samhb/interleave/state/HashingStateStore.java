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
    private final Set<Integer> preemptionHashes;
    private final Map<String, Integer> minPreemptions;

    /**
     * Creates a new exact state store.
     */
    public HashingStateStore() {
        this.encoder = new CanonicalEncoder();
        this.visitedHashes = new HashSet<>();
        this.visitedStates = new HashSet<>();
        this.preemptionHashes = new HashSet<>();
        this.minPreemptions = new HashMap<>();
    }

    @Override
    public boolean isVisited(Configuration config) {
        int hash = hashCode(config);
        if (!visitedHashes.contains(hash)) {
            return false;
        }
        String encoded = encoder.configurationKey(config);
        return visitedStates.contains(encoded);
    }

    @Override
    public void markVisited(Configuration config) {
        int hash = hashCode(config);
        visitedHashes.add(hash);
        String encoded = encoder.configurationKey(config);
        visitedStates.add(encoded);
    }

    @Override
    public void clear() {
        visitedHashes.clear();
        visitedStates.clear();
        preemptionHashes.clear();
        minPreemptions.clear();
    }

    @Override
    public boolean isVisited(Configuration config, int lastThreadId, int preemptions) {
        // Two-level scheme, same shape as the single-argument path: the cheap hash prefilter runs
        // first so a state that was never marked never pays for the Base64 encoding. The prefilter
        // set is deliberately separate from visitedHashes because the two paths hash different
        // things (the preemption hash folds in lastThreadId) -- sharing one set would make each
        // path's misses desynchronize the other's prefilter.
        if (!preemptionHashes.contains(preemptionHash(config, lastThreadId))) {
            return false;
        }
        Integer min = minPreemptions.get(encoder.configurationKey(config, lastThreadId));
        // Visited at budget p iff some recorded q satisfies q <= p, which holds iff min(q) <= p.
        return min != null && min <= preemptions;
    }

    @Override
    public void markVisited(Configuration config, int lastThreadId, int preemptions) {
        preemptionHashes.add(preemptionHash(config, lastThreadId));
        // Only the minimum budget matters: any higher count is subsumed by it, and keeping the
        // minimum is what makes isVisited O(1) instead of a scan over every recorded count.
        minPreemptions.merge(encoder.configurationKey(config, lastThreadId), preemptions, Math::min);
    }

    /**
     * Returns the number of unique configurations stored.
     *
     * @return count of visited states
     */
    public int size() {
        return visitedStates.size();
    }

    /**
     * Returns the number of distinct {@code (config, lastThreadId)} pairs recorded by the
     * preemption-aware API. Each pair is stored once regardless of how many different preemption
     * counts reached it.
     *
     * @return count of distinct preemption-aware entries
     */
    public int preemptionEntryCount() {
        return minPreemptions.size();
    }

    private int hashCode(Configuration config) {
        int result = encoder.hashCode(config.state());
        result = 31 * result + config.programCounters().hashCode();
        return result;
    }

    private int preemptionHash(Configuration config, int lastThreadId) {
        return 31 * hashCode(config) + lastThreadId;
    }

    @Override
    public StateStore freshCopy() {
        return new HashingStateStore();
    }
}
