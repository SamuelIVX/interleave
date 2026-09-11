package dev.samhb.interleave.state;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.search.StateStore;
import java.util.*;

public final class BitstateStore implements StateStore {
    private final CanonicalEncoder encoder;
    private final BitSet bitset;
    private final int size;

    public BitstateStore(int size) {
        this.encoder = new CanonicalEncoder();
        this.size = size;
        this.bitset = new BitSet(size);
    }

    @Override
    public boolean isVisited(Configuration config) {
        int hash = hashCode(config);
        int index = Math.abs(hash) % size;
        return bitset.get(index);
    }

    @Override
    public void markVisited(Configuration config) {
        int hash = hashCode(config);
        int index = Math.abs(hash) % size;
        bitset.set(index);
    }

    @Override
    public void clear() {
        bitset.clear();
    }

    public int size() {
        return size;
    }

    private int hashCode(Configuration config) {
        int result = encoder.hashCode(config.state());
        result = 31 * result + config.programCounters().hashCode();
        return result;
    }
}
