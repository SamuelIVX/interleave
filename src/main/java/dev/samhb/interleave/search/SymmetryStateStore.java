/* Exact source-orbit membership bound to one validated program, property and strategy. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.*;

/**
 * Checked per-run orbit store for built-in exact strategies. Execution retains original owners.
 * Create with {@link #forRun(Program, Invariant, Strategy)}; explorers verify the bound run before
 * executing callbacks. CBS keys include the mapped last owner; DPOR retains mapped sleep context.
 * Membership methods require configurations from the bound source model, whose state must be
 * {@link DynamicState}; passing foreign configurations violates the run contract.
 * This mutable store must not be shared between concurrent explorations.
 */
public final class SymmetryStateStore implements CheckedSymmetryStore {
    /** Immutable checked run identity and owner/action mapping. */
    private final SymmetryRunContext context;
    /** Certified exact storage configuration retained by copies. */
    private final SymmetryBackend backend;
    /** Recorded value orbits for unbounded traversal. */
    private final Set<String> visited;
    /** Lowest known cost per CBS orbit and mapped last owner. */
    private final Map<String, Integer> minimumCosts;
    /** Exact copied sleep contexts per value orbit. */
    private final Map<String, Set<Set<SymmetryRunContext.SleepingAction>>> sleepCoverage;
    /** Allocates independent exact membership for an immutable checked run context.
     * @param context validated immutable run and mapping
     * @param backend certified exact container selection
     */
    private SymmetryStateStore(SymmetryRunContext context, SymmetryBackend backend) {
        this.context = context;
        this.backend = backend;
        this.visited = backend.newSet();
        this.minimumCosts = backend.newMap();
        this.sleepCoverage = backend.newMap();
    }

    /**
     * Validates source/property eligibility and creates fresh exact orbit membership.
     * For example, pass forRun(program, property, Strategy.DPOR) to a DporExplorer.
     * @param program immutable compiled source program
     * @param invariant null or a compiler-built shared-state property
     * @param strategy built-in strategy to bind
     * @return fresh store bound to these exact objects and strategy
     * @throws IllegalArgumentException for unsupported source semantics or mismatched DPOR alias partitions
     * @throws NullPointerException for a null program or strategy
     */
    public static SymmetryStateStore forRun(Program program, Invariant invariant, Strategy strategy) {
        return forRun(program, invariant, strategy, SymmetryBackend.HASHED);
    }

    /** Validates one run and allocates fresh membership using a certified exact backend.
     * For example, forRun(program, property, Strategy.DPOR, SymmetryBackend.ORDERED) uses tree storage.
     * No user store, comparator or factory is admitted; keys/context and eligibility remain checked.
     * @param program immutable compiled source program
     * @param invariant null or its compiler-built shared-state property
     * @param strategy built-in strategy to bind
     * @param backend exact hash-based or naturally ordered membership
     * @return empty store bound to the exact objects, strategy and backend
     * @throws IllegalArgumentException for unsupported source/property semantics or DPOR alias layout
     * @throws NullPointerException for a null program, strategy or backend
     */
    public static SymmetryStateStore forRun(Program program, Invariant invariant, Strategy strategy, SymmetryBackend backend) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(backend, "backend");
        return new SymmetryStateStore(new SymmetryRunContext(program, invariant, strategy), backend);
    }

    /** Reports the immutable storage choice without exposing live membership containers.
     * @return certified backend used by this store and its fresh copies
     */
    public SymmetryBackend backend() { return backend; }

    /** Rejects reuse with a different program, property or algorithm before any execution.
     * @param program requested program object
     * @param invariant requested property object, or null
     * @param strategy requested built-in algorithm
     * @throws IllegalArgumentException if any bound identity differs
     */
    @Override public void requireRun(Program program, Invariant invariant, Strategy strategy) {
        context.requireRun(program, invariant, strategy);
    }

    /** Tests unbounded value-orbit membership, independently of CBS cost membership.
     * Available for every bound strategy; CBS explorers must use the last-owner/cost overload.
     * @param config original configuration
     * @return whether its value orbit was recorded
     * @throws ClassCastException if the configuration does not contain DynamicState
     */
    @Override public boolean isVisited(Configuration config) { return visited.contains(context.position(config).key()); }

    /** Records a copied unbounded value-orbit key without relabeling execution.
     * Available for every bound strategy; this does not mark any CBS cost/last-owner context.
     * @param config original configuration whose immutable orbit key is retained
     * @throws ClassCastException if the configuration does not contain DynamicState
     */
    @Override public void markVisited(Configuration config) { visited.add(context.position(config).key()); }

    /** Checks whether an equal CBS orbit/last owner was visited with no greater cost.
     * @param config original configuration
     * @param lastThreadId actual last owner, or -1
     * @param preemptions nonnegative cost
     * @return whether an equal orbit/last owner has no greater cost
     * @throws IllegalArgumentException for a negative cost or invalid last owner
     */
    @Override public boolean isVisited(Configuration config, int lastThreadId, int preemptions) {
        requireCost(preemptions);
        Integer previous = minimumCosts.get(context.costKey(config, lastThreadId));
        return previous != null && previous <= preemptions;
    }

    /** Retains the minimum cost for a CBS orbit and mapped last owner.
     * @param config original configuration
     * @param lastThreadId actual last owner, or -1
     * @param preemptions nonnegative cost to retain if better than the prior cost
     * @throws IllegalArgumentException for a negative cost or invalid last owner
     */
    @Override public void markVisited(Configuration config, int lastThreadId, int preemptions) {
        requireCost(preemptions);
        String key = context.costKey(config, lastThreadId);
        minimumCosts.merge(key, preemptions, Math::min);
    }

    /**
     * Records an exact mapped DPOR sleep context, returning true only for a previously recorded one.
     * Call after the explorer's all-asleep normalization. Tokens retain owner and instruction identity,
     * using each object's first occurrence, including stale sleepers. No subset subsumption is inferred
     * across orbit permutations.
     * @param config current original configuration
     * @param sleeping actual owner/action entries, read only and copied before retention
     * @return true if this orbit and mapped sleep context were already recorded
     * @throws IllegalArgumentException if called outside DPOR or with foreign actions
     */
    @Override public boolean sleepCovered(Configuration config, Set<Map.Entry<Integer, Step>> sleeping) {
        var incoming = context.sleepContext(config, sleeping);
        return !sleepCoverage.computeIfAbsent(incoming.key(), ignored -> new HashSet<>()).add(incoming.tokens());
    }

    /** Clears all value, budget and sleep coverage while retaining the validated run binding. */
    @Override public void clear() { visited.clear(); minimumCosts.clear(); sleepCoverage.clear(); }

    /** Creates empty membership for the same run contract and selected backend.
     * @return independent empty membership with the same validated binding
     */
    @Override public StateStore freshCopy() { return new SymmetryStateStore(context, backend); }

    /** Rejects negative public costs before membership lookup or mutation.
     * @param cost preemption count
     * @throws IllegalArgumentException if negative
     */
    private static void requireCost(int cost) {
        if (cost < 0) throw new IllegalArgumentException("Preemption cost must not be negative");
    }
}
