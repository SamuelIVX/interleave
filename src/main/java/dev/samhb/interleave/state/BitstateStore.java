package dev.samhb.interleave.state;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.search.StateStore;
import java.util.BitSet;

public final class BitstateStore implements StateStore {
    private final CanonicalEncoder encoder;
    private final BitSet bitset;
    private final int size;
    private final int numHashFunctions;
    private int statesMarked;

    public BitstateStore(int size) {
        this(size, 1);
    }

    public BitstateStore(int size, int numHashFunctions) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (numHashFunctions <= 0) {
            throw new IllegalArgumentException("numHashFunctions must be positive");
        }
        this.encoder = new CanonicalEncoder();
        this.size = size;
        this.numHashFunctions = numHashFunctions;
        this.bitset = new BitSet(size);
        this.statesMarked = 0;
    }

    @Override
    public boolean isVisited(Configuration config) {
        int[] indices = hashIndices(config);
        for (int idx : indices) {
            if (!bitset.get(idx)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void markVisited(Configuration config) {
        int[] indices = hashIndices(config);
        for (int idx : indices) {
            bitset.set(idx);
        }
        statesMarked++;
    }

    @Override
    public void clear() {
        bitset.clear();
        statesMarked = 0;
    }

    public int size() {
        return size;
    }

    public int numHashFunctions() {
        return numHashFunctions;
    }

    public int statesMarked() {
        return statesMarked;
    }

    public int bitCount() {
        return bitset.cardinality();
    }

    public double bitDensity() {
        return (double) bitCount() / size;
    }

    public double estimatedFalsePositiveRate() {
        if (statesMarked == 0) {
            return 0.0;
        }
        double m = size;
        double n = statesMarked;
        double k = numHashFunctions;
        double prob = 1.0 - Math.exp(-k * n / m);
        return Math.pow(prob, k);
    }

    @Override
    public StateStore freshCopy() {
        return new BitstateStore(size, numHashFunctions);
    }

    private int[] hashIndices(Configuration config) {
        int h1 = hashCode(config);
        int h2 = Integer.rotateLeft(h1, 17);
        if (h2 == 0) {
            h2 = 1;
        }
        int[] indices = new int[numHashFunctions];
        for (int i = 0; i < numHashFunctions; i++) {
            int val = h1 + i * h2;
            indices[i] = Math.abs(val) % size;
        }
        return indices;
    }

    private int hashCode(Configuration config) {
        int result = encoder.hashCode(config.state());
        result = 31 * result + config.programCounters().hashCode();
        return result;
    }
}