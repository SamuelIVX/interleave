/** Run-owned approximate membership over complete, source-certified value orbits. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.state.BitstateFilter;
import java.util.*;

/**
 * Bloom membership for source symmetry with DFS or static POR. Positive lookups may skip unseen
 * states, so absence of a violation is approximate. The bit budget covers membership only, not
 * traversal, source metadata, temporary encodings or diagnostic maps. Not thread-safe.
 * For example, pass {@code forRun(program, property, Strategy.DFS, 1_000_003, 4, 0)}
 * to a DFS explorer for that exact source program/property pair.
 */
public final class BitstateSymmetryStateStore implements CheckedSymmetryStore, ApproximateStateStore {
    /** Immutable certification shared only with copies of this run. */
    private final SymmetryRunContext context;
    /** Logical membership capacity. */
    private final int bits;
    /** Number of probes per key. */
    private final int hashFunctions;
    /** Independent monotone membership. */
    private final BitstateFilter filter;
    /** Insertion count, including repeated keys. */
    private long marks;

    /** Allocates empty membership for a validated run.
     * @param context immutable checked binding
     * @param bits positive logical capacity
     * @param hashFunctions positive probe count
     */
    private BitstateSymmetryStateStore(SymmetryRunContext context, int bits, int hashFunctions) {
        this.context = context;
        this.bits = bits;
        this.hashFunctions = hashFunctions;
        filter = new BitstateFilter(bits, hashFunctions);
    }

    /** Certifies source eligibility and allocates one VALUE filter.
     * @param program immutable compiled source program
     * @param invariant compiler-built shared property, or null
     * @param strategy DFS or STATIC_POR
     * @param bits positive logical bit budget
     * @param hashFunctions positive probe count
     * @param maxPreemptions zero; approximate CBS is not supported in this stage
     * @return empty checked approximate membership
     * @throws NullPointerException if program or strategy is null
     * @throws IllegalArgumentException for invalid settings, unsupported strategy or source/property
     */
    public static BitstateSymmetryStateStore forRun(Program program, Invariant invariant,
            Strategy strategy, int bits, int hashFunctions, int maxPreemptions) {
        Objects.requireNonNull(strategy, "strategy");
        if (bits <= 0 || hashFunctions <= 0 || maxPreemptions != 0) {
            throw new IllegalArgumentException("Positive bits/probes and zero preemption capacity required");
        }
        if (strategy != Strategy.DFS && strategy != Strategy.STATIC_POR) {
            throw new IllegalArgumentException("Approximate symmetry supports only DFS and static POR");
        }
        return new BitstateSymmetryStateStore(new SymmetryRunContext(program, invariant, strategy), bits, hashFunctions);
    }

    /** {@inheritDoc} */
    @Override public void requireRun(Program program, Invariant invariant, Strategy strategy) {
        context.requireRun(program, invariant, strategy);
    }
    /** Checks approximate membership for a complete value orbit from the bound source run.
     * @param config configuration containing DynamicState from this store's certified program
     * @return false if definitely absent; true may be a false positive for an unmarked orbit
     * @throws ClassCastException if the state is not DynamicState
     */
    @Override public boolean isVisited(Configuration config) {
        return filter.contains(Arrays.hashCode(context.valueBytes(config)));
    }
    /** Records a complete value orbit from the bound source run and increments insertion count.
     * @param config configuration containing DynamicState from this store's certified program
     * @throws ClassCastException if the state is not DynamicState
     */
    @Override public void markVisited(Configuration config) {
        filter.mark(Arrays.hashCode(context.valueBytes(config)));
        marks++;
    }
    /** {@inheritDoc} */
    @Override public void clear() { filter.clear(); marks = 0; }
    /** {@inheritDoc} */
    @Override public StateStore freshCopy() { return new BitstateSymmetryStateStore(context, bits, hashFunctions); }
    /** {@inheritDoc} */
    @Override public double estimatedFalsePositiveRate() {
        return Math.pow(1 - Math.exp(-(double) hashFunctions * marks / bits), hashFunctions);
    }
    /** {@inheritDoc} */
    @Override public int bitCount() { return filter.bitCount(); }
    /** {@inheritDoc} */
    @Override public double bitDensity() { return (double) bitCount() / bits; }
    /** {@inheritDoc} */
    @Override public int maxPreemptions() { return 0; }
    /** {@inheritDoc} */
    @Override public boolean sleepCovered(Configuration config, Set<Map.Entry<Integer, Step>> sleeping) {
        throw new UnsupportedOperationException("Approximate symmetry DPOR is not supported");
    }
}
