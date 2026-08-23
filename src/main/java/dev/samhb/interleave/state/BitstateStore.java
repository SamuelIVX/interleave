package dev.samhb.interleave.state;

import dev.samhb.interleave.core.SharedState;
import java.util.*;

public final class BitstateStore {
    private final CanonicalEncoder encoder;
    private final BitSet bitset;
    private final int size;

    public BitstateStore(int size) {
        this.encoder = new CanonicalEncoder();
        this.size = size;
        this.bitset = new BitSet(size);
    }

    public boolean isVisited(SharedState state) {
        int hash = encoder.hashCode(state);
        int index = Math.abs(hash) % size;
        return bitset.get(index);
    }

    public void markVisited(SharedState state) {
        int hash = encoder.hashCode(state);
        int index = Math.abs(hash) % size;
        bitset.set(index);
    }

    public void clear() {
        bitset.clear();
    }

    public int size() {
        return size;
    }
}
