/** Run-owned approximate membership over trusted-model VALUE, CBS and DPOR products. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.state.BitstateFilter;
import java.util.*;
import java.nio.ByteBuffer;

/**
 * Bloom membership for certified source/declarative symmetry with every built-in strategy. Positive lookups may skip unseen
 * states, so absence of a violation is approximate. The bit budget covers membership only, not
 * traversal, source metadata, temporary encodings or diagnostic maps. Not thread-safe.
 * For example, pass {@code forRun(program, property, Strategy.DFS, 1_000_003, 4, 0)}
 * to a DFS explorer for that same certified program/property pair.
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
    /** Validated configured cost ceiling; not a per-cost allocation size. */
    private final int maxPreemptions;
    /** Bound strategy selects admitted cost and sleep operations. */
    private final Strategy strategy;
    /** Largest validated queried cost in this producing pass, initially zero. */
    private int maximumQueriedCost;

    /** Allocates empty membership for a validated run.
     * @param context immutable checked binding
     * @param bits positive logical capacity
     * @param hashFunctions positive probe count
     * @param maxPreemptions validated capacity
     * @param strategy bound built-in strategy
     */
    private BitstateSymmetryStateStore(SymmetryRunContext context, int bits, int hashFunctions, int maxPreemptions, Strategy strategy) {
        this.context = context;
        this.bits = bits;
        this.hashFunctions = hashFunctions;
        this.maxPreemptions = maxPreemptions;
        this.strategy = strategy;
        filter = new BitstateFilter(bits, hashFunctions);
    }

    /** Certifies trusted model eligibility and allocates one shared VALUE/CBS/DPOR filter.
     * @param program immutable compiled source or loader-certified declarative program
     * @param invariant matching trusted shared property, or null
     * @param strategy built-in algorithm, including DPOR
     * @param bits positive logical bit budget
     * @param hashFunctions positive probe count
     * @param maxPreemptions nonnegative CBS ceiling; zero for non-CBS bindings
     * @return empty checked approximate membership
     * @throws NullPointerException if program or strategy is null
     * @throws IllegalArgumentException for invalid settings, model/property eligibility or DPOR alias layout
     */
    public static BitstateSymmetryStateStore forRun(Program program, Invariant invariant,
            Strategy strategy, int bits, int hashFunctions, int maxPreemptions) {
        Objects.requireNonNull(strategy, "strategy");
        if (bits <= 0 || hashFunctions <= 0 || maxPreemptions < 0
                || (strategy != Strategy.CONTEXT_BOUNDED && maxPreemptions != 0)) {
            throw new IllegalArgumentException("Positive bits/probes and a nonnegative CBS capacity (zero otherwise) required");
        }
        return new BitstateSymmetryStateStore(new SymmetryRunContext(program, invariant, strategy), bits, hashFunctions, maxPreemptions, strategy);
    }

    /** {@inheritDoc} */
    @Override public void requireRun(Program program, Invariant invariant, Strategy strategy) {
        context.requireRun(program, invariant, strategy);
    }
    /** Checks approximate membership for a complete value orbit from the bound certified run.
     * @param config configuration containing DynamicState from this store's certified program
     * @return false if definitely absent; true may be a false positive for an unmarked orbit
     * @throws ClassCastException if the state is not DynamicState
     */
    @Override public boolean isVisited(Configuration config) {
        return filter.contains(Arrays.hashCode(context.valueBytes(config)));
    }
    /** Records a complete value orbit from the bound certified run and increments insertion count.
     * @param config configuration containing DynamicState from this store's certified program
     * @throws ClassCastException if the state is not DynamicState
     */
    @Override public void markVisited(Configuration config) {
        filter.mark(Arrays.hashCode(context.valueBytes(config)));
        marks++;
    }
    /** Probes each complete cost-q product separately for q no greater than the query cost.
     * Canonicalizes once and reuses one encoded buffer; probe fragments from different q never mix.
     * @param config DynamicState configuration from the certified run
     * @param lastThreadId original last owner, or initial -1
     * @param preemptions queried cost within the configured ceiling
     * @return false if definitely uncovered; true may be a Bloom false positive
     * @throws ClassCastException if the state is not DynamicState
     * @throws UnsupportedOperationException for a non-CBS binding
     * @throws IllegalArgumentException for an invalid cost or last owner, without changing statistics
     */
    @Override public boolean isVisited(Configuration config, int lastThreadId, int preemptions) {
        requireCost(preemptions);
        var key = ByteBuffer.wrap(context.costBytes(config,lastThreadId,0));
        maximumQueriedCost = Math.max(maximumQueriedCost,preemptions);
        for (long cost = 0; cost <= preemptions; cost++) {
            key.putInt(key.capacity() - Integer.BYTES,(int)cost);
            if (filter.contains(Arrays.hashCode(key.array()))) return true;
        }
        return false;
    }

    /** Marks only the complete supplied-cost product in the one shared filter.
     * @param config DynamicState configuration from the certified run
     * @param lastThreadId original last owner, or initial -1
     * @param preemptions inserted cost within the configured ceiling
     * @throws ClassCastException if the state is not DynamicState
     * @throws UnsupportedOperationException for a non-CBS binding
     * @throws IllegalArgumentException for an invalid cost or last owner, without partial writes
     */
    @Override public void markVisited(Configuration config, int lastThreadId, int preemptions) {
        requireCost(preemptions);
        byte[] key = context.costBytes(config,lastThreadId,preemptions);
        filter.mark(Arrays.hashCode(key));
        marks++;
    }

    /** Rejects unsupported or out-of-range cost operations before encoding or updating history.
     * @param cost requested cost
     * @throws UnsupportedOperationException for a non-CBS binding
     * @throws IllegalArgumentException if cost is negative or above the configured capacity
     */
    private void requireCost(int cost) {
        if (strategy != Strategy.CONTEXT_BOUNDED) throw new UnsupportedOperationException("Cost membership requires a CBS binding");
        if (cost < 0 || cost > maxPreemptions) throw new IllegalArgumentException("Cost is outside configured CBS capacity");
    }

    /** {@inheritDoc} */
    @Override public void clear() { filter.clear(); marks = 0; maximumQueriedCost = 0; }
    /** {@inheritDoc} */
    @Override public StateStore freshCopy() { return new BitstateSymmetryStateStore(context, bits, hashFunctions, maxPreemptions, strategy); }
    /** Reports the idealized single-lookup heuristic, scaled by the largest CBS query cost plus one.
     * This capped union model is neither calibrated probability nor a behavior-coverage measure.
     * VALUE-only runs have a zero query history and retain the single-lookup estimate.
     * @return min(1,(pMax+1)*F), with empty membership yielding zero
     */
    @Override public double estimatedFalsePositiveRate() {
        double singleLookup = Math.pow(1 - Math.exp(-(double) hashFunctions * marks / bits), hashFunctions);
        return Math.min(1.0,((double)maximumQueriedCost + 1) * singleLookup);
    }
    /** {@inheritDoc} */
    @Override public int bitCount() { return filter.bitCount(); }
    /** {@inheritDoc} */
    @Override public double bitDensity() { return (double) bitCount() / bits; }
    /** {@inheritDoc} */
    @Override public int maxPreemptions() { return maxPreemptions; }
    /** Tests one complete mapped DPOR product, inserting it only when definitely absent.
     * The entire context is validated before probing. A positive may be a Bloom false positive;
     * VALUE membership alone never deliberately decides this lookup. No exact context map is kept.
     * @param config DynamicState configuration from the certified run
     * @param sleeping original owner/action entries, including stale actions
     * @return true if every product probe is set; false after recording a missing product
     * @throws UnsupportedOperationException if this store is not bound to DPOR
     * @throws IllegalArgumentException for a foreign owner/action, without partial marks or statistics
     * @throws ClassCastException if the configuration does not contain DynamicState
     */
    @Override public boolean sleepCovered(Configuration config, Set<Map.Entry<Integer, Step>> sleeping) {
        if (strategy != Strategy.DPOR) throw new UnsupportedOperationException("Sleep membership requires a DPOR binding");
        int hash = Arrays.hashCode(context.sleepBytes(config,sleeping));
        if (filter.contains(hash)) return true;
        filter.mark(hash);
        marks++;
        return false;
    }
}
