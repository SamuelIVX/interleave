package dev.samhb.interleave.state;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.search.StateStore;
import java.util.BitSet;

/**
 * Approximate state store using a Bloom filter (bit-vector with k hash functions).
 * Trades completeness for memory: may return false positives (claims a state was
 * visited when it wasn't) but never false negatives. When a false positive occurs,
 * the explorer skips a reachable state, potentially missing violations.
 * Use {@link HashingStateStore} when exact results are required.
 *
 * <p>Default configuration uses a 1,000,003-bit array (~125 KB) with k=4 hash functions,
 * which keeps the false-positive rate negligible for the built-in benchmark corpus
 * (~46 states max). For larger programs, increase the bit-array size or reduce k.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Bloom_filter">Bloom filter</a>
 */
public final class BitstateStore implements StateStore {
    private final CanonicalEncoder encoder;
    private final BitSet bitset;
    private final int size;
    private final int numHashFunctions;
    private int statesMarked;

    /**
     * Creates a bitstate store with the default of 4 hash functions.
     *
     * @param size the bit-array size (number of bits)
     * @throws IllegalArgumentException if size is not positive
     */
    public BitstateStore(int size) {
        this(size, 4);
    }

    /**
     * Creates a bitstate store with a custom bit-array size and number of hash functions.
     *
     * @param size the bit-array size (number of bits); must be positive
     * @param numHashFunctions the number of hash functions (k); must be positive
     * @throws IllegalArgumentException if size or numHashFunctions is not positive
     */
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

    /**
     * Returns the bit-array size.
     *
     * @return the number of bits in the backing bit vector
     */
    public int size() {
        return size;
    }

    /**
     * Returns the number of hash functions (k) used.
     *
     * @return the number of hash functions
     */
    public int numHashFunctions() {
        return numHashFunctions;
    }

    /**
     * Returns the number of distinct configurations marked as visited.
     *
     * @return the count of states passed to {@link #markVisited}
     */
    public int statesMarked() {
        return statesMarked;
    }

    /**
     * Returns the number of bits currently set in the bit vector.
     *
     * @return the cardinality of the bit set
     */
    public int bitCount() {
        return bitset.cardinality();
    }

    /**
     * Returns the fraction of bits set in the bit vector.
     *
     * @return bit density in [0, 1]
     */
    public double bitDensity() {
        return (double) bitCount() / size;
    }

    /**
     * Estimates the false-positive rate using the standard Bloom filter formula:
     * {@code (1 - e^(-k * n / m))^k} where {@code m = size}, {@code n = statesMarked},
     * {@code k = numHashFunctions}.
     *
     * @return estimated false-positive probability in [0, 1]
     */
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

    /**
     * Computes the k bit indices for a configuration using double hashing.
     * The primary hash is {@code h1 = hash(config)}; the secondary hash is
     * {@code h2 = rotateLeft(h1, 17)} (ensuring h2 != 0). The i-th index is
     * {@code floorMod(h1 + i * h2, size)}. This is the standard double-hashing
     * technique for Bloom filters, giving k pseudo-independent indices from two
     * hash computations.
     *
     * @param config the configuration to hash
     * @return array of k bit indices in [0, size)
     */
    private int[] hashIndices(Configuration config) {
        int h1 = hashCode(config);
        int h2 = Integer.rotateLeft(h1, 17);
        if (h2 == 0) {
            h2 = 1;
        }
        int[] indices = new int[numHashFunctions];
        for (int i = 0; i < numHashFunctions; i++) {
            int val = h1 + i * h2;
            indices[i] = Math.floorMod(val, size);
        }
        return indices;
    }

    /**
     * Computes a 32-bit hash for a configuration from its canonical state encoding
     * and program counters.
     *
     * @param config the configuration to hash
     * @return a 32-bit hash code
     */
    private int hashCode(Configuration config) {
        int result = encoder.hashCode(config.state());
        result = 31 * result + config.programCounters().hashCode();
        return result;
    }
}