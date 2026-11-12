/** Shared monotone bit-vector membership for precomputed, potentially colliding hashes. */
package dev.samhb.interleave.state;

import java.util.BitSet;

/**
 * Run-owned Bloom probes; callers own key encoding, domains and insertion statistics.
 * Positive membership may be false. This mutable filter is not shared across concurrent runs.
 */
public final class BitstateFilter {
    /** Logical number of positions, independent of BitSet word rounding. */
    private final int size;
    /** Number of probes per hashed key. */
    private final int numHashFunctions;
    /** Monotone membership bits, until explicitly cleared. */
    private final BitSet bits;

    /** Creates an empty filter with the ordinary bitstate probe policy.
     * @param size positive logical bit capacity
     * @param numHashFunctions positive probe count
     * @throws IllegalArgumentException if either argument is not positive
     */
    public BitstateFilter(int size, int numHashFunctions) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        if (numHashFunctions <= 0) throw new IllegalArgumentException("numHashFunctions must be positive");
        this.size = size;
        this.numHashFunctions = numHashFunctions;
        this.bits = new BitSet(size);
    }

    /** Tests every probe for a supplied hash without inserting it.
     * @param hash caller-computed signed hash, including zero and negative values
     * @return false if definitely absent; true may be a false positive
     */
    public boolean contains(int hash) {
        return containsIndices(indices(hash));
    }

    /** Reuses probes while scanning equally configured ordinary CBS cost vectors.
     * @param indices positions produced by this filter or an equally configured filter
     * @return false if definitely absent; true may be a false positive
     */
    boolean containsIndices(int[] indices) {
        for (int index : indices) if (!bits.get(index)) return false;
        return true;
    }

    /** Sets every probe; repeated marks cannot clear any prior membership.
     * @param hash caller-computed signed hash
     */
    public void mark(int hash) {
        for (int index : indices(hash)) bits.set(index);
    }

    /** Resets every membership bit. */
    public void clear() { bits.clear(); }

    /** Counts occupied logical positions, never exceeding configured capacity.
     * @return actual occupied bit count
     */
    public int bitCount() { return bits.cardinality(); }

    /** Preserves ordinary rotated double hashing, including Java int wraparound.
     * @param h1 caller's primary signed hash
     * @return probe positions in the configured logical range
     */
    int[] indices(int h1) {
        int h2 = Integer.rotateLeft(h1, 17);
        if (h2 == 0) h2 = 1;
        int[] indices = new int[numHashFunctions];
        for (int i = 0; i < numHashFunctions; i++) indices[i] = Math.floorMod(h1 + i * h2, size);
        return indices;
    }
}
