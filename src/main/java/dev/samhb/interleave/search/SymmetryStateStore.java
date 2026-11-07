/* Exact source-orbit membership bound to one validated program, property and strategy. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.dsl.SourceSymmetry;
import dev.samhb.interleave.state.ThreadSymmetryCanonicalizer;
import java.util.*;

/**
 * Checked per-run orbit store for built-in exact strategies. Execution retains original owners.
 * Create with {@link #forRun(Program, Invariant, Strategy)}; explorers verify the bound run before
 * executing callbacks. CBS keys include the mapped last owner; DPOR retains mapped sleep context.
 * Membership methods require configurations from the bound source model, whose state must be
 * {@link DynamicState}; passing foreign configurations violates the run contract.
 * This mutable store must not be shared between concurrent explorations.
 */
public final class SymmetryStateStore implements StateStore {
    /** Exact immutable model certified at construction. */
    private final Program program;
    /** Exact pure compiler property, or null. */
    private final Invariant invariant;
    /** Algorithm whose context this membership represents. */
    private final Strategy strategy;
    /** Whole-frame permutation and exact value encoder for the certified groups. */
    private final ThreadSymmetryCanonicalizer canonicalizer;
    /** Recorded value orbits for unbounded traversal. */
    private final Set<String> visited = new HashSet<>();
    /** Lowest known cost per CBS orbit and mapped last owner. */
    private final Map<String, Integer> minimumCosts = new HashMap<>();
    /** Exact copied sleep contexts per value orbit. */
    private final Map<String, Set<Set<SleepingAction>>> sleepCoverage = new HashMap<>();
    /** First owner-local occurrence of each instruction identity for DPOR action transport. */
    private final List<IdentityHashMap<Step, Integer>> occurrences = new ArrayList<>();

    /** Constructs an empty capability after source eligibility is checked.
     * @param program bound immutable source program
     * @param invariant bound compiler property, or null
     * @param strategy built-in search using this store
     * @param groups certified interchangeable owners
     * @throws IllegalArgumentException if a DPOR group has inconsistent instruction-alias partitions
     */
    private SymmetryStateStore(Program program, Invariant invariant, Strategy strategy, List<List<Integer>> groups) {
        this.program = program;
        this.invariant = invariant;
        this.strategy = strategy;
        this.canonicalizer = new ThreadSymmetryCanonicalizer(program.threadCount(), groups);
        if (strategy == Strategy.DPOR) {
            var partitions = new ArrayList<List<Integer>>();
            for (var thread : program.threads()) {
                var indices = new IdentityHashMap<Step, Integer>();
                var partition = new ArrayList<Integer>();
                for (int pc = 0; pc < thread.steps().size(); pc++) {
                    Step action = thread.steps().get(pc);
                    indices.putIfAbsent(action, pc);
                    partition.add(indices.get(action));
                }
                occurrences.add(indices);
                partitions.add(List.copyOf(partition));
            }
            for (var group : groups) {
                for (int owner : group) {
                    if (!partitions.get(group.getFirst()).equals(partitions.get(owner))) {
                        throw new IllegalArgumentException("Symmetry DPOR requires matching instruction-alias partitions within each group");
                    }
                }
            }
        }
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
        Objects.requireNonNull(strategy, "strategy");
        return new SymmetryStateStore(program, invariant, strategy, SourceSymmetry.groups(program, invariant));
    }

    /** Rejects reuse with a different program, property or algorithm before any execution.
     * @param program requested program object
     * @param invariant requested property object, or null
     * @param strategy requested built-in algorithm
     * @throws IllegalArgumentException if any bound identity differs
     */
    public void requireRun(Program program, Invariant invariant, Strategy strategy) {
        if (this.program != program || this.invariant != invariant || this.strategy != strategy) {
            throw new IllegalArgumentException("Symmetry store belongs to a different program, property or strategy");
        }
    }

    /** Tests unbounded value-orbit membership, independently of CBS cost membership.
     * Available for every bound strategy; CBS explorers must use the last-owner/cost overload.
     * @param config original configuration
     * @return whether its value orbit was recorded
     * @throws ClassCastException if the configuration does not contain DynamicState
     */
    @Override public boolean isVisited(Configuration config) { return visited.contains(position(config).key()); }

    /** Records a copied unbounded value-orbit key without relabeling execution.
     * Available for every bound strategy; this does not mark any CBS cost/last-owner context.
     * @param config original configuration whose immutable orbit key is retained
     * @throws ClassCastException if the configuration does not contain DynamicState
     */
    @Override public void markVisited(Configuration config) { visited.add(position(config).key()); }

    /** Checks whether an equal CBS orbit/last owner was visited with no greater cost.
     * @param config original configuration
     * @param lastThreadId actual last owner, or -1
     * @param preemptions nonnegative cost
     * @return whether an equal orbit/last owner has no greater cost
     * @throws IllegalArgumentException for a negative cost or invalid last owner
     */
    @Override public boolean isVisited(Configuration config, int lastThreadId, int preemptions) {
        requireCost(preemptions);
        Integer previous = minimumCosts.get(canonicalizer.canonicalize((DynamicState) config.state(), config.programCounters(), lastThreadId).key());
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
        String key = canonicalizer.canonicalize((DynamicState) config.state(), config.programCounters(), lastThreadId).key();
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
    public boolean sleepCovered(Configuration config, Set<Map.Entry<Integer, Step>> sleeping) {
        if (strategy != Strategy.DPOR) throw new IllegalArgumentException("Sleep contexts require symmetry DPOR");
        var position = position(config);
        var tokens = new HashSet<SleepingAction>();
        for (var entry : sleeping) {
            int owner = entry.getKey();
            if (owner < 0 || owner >= occurrences.size()) throw new IllegalArgumentException("Invalid sleeping owner");
            Integer pc = occurrences.get(owner).get(entry.getValue());
            if (pc == null) throw new IllegalArgumentException("Foreign sleeping action");
            tokens.add(new SleepingAction(position.canonicalThreadId(owner), pc));
        }
        return !sleepCoverage.computeIfAbsent(position.key(), ignored -> new HashSet<>()).add(Set.copyOf(tokens));
    }

    /** Canonical owner and representative action position, with no metadata or object identity in equality.
     * @param owner owner after the same frame permutation used by the value key
     * @param pc first lexical occurrence of the instruction object
     */
    private record SleepingAction(int owner, int pc) {}

    /** Clears all value, budget and sleep coverage while retaining the validated run binding. */
    @Override public void clear() { visited.clear(); minimumCosts.clear(); sleepCoverage.clear(); }

    /** Creates empty membership for the same run contract.
     * @return independent empty membership with the same validated binding
     */
    @Override public StateStore freshCopy() { return forRun(program, invariant, strategy); }

    /** Encodes a whole-frame value orbit with a reversible owner map.
     * @param config actual source configuration
     * @return exact value key and reversible owner map
     */
    private ThreadSymmetryCanonicalizer.CanonicalPosition position(Configuration config) {
        return canonicalizer.canonicalize((DynamicState) config.state(), config.programCounters());
    }

    /** Rejects negative public costs before membership lookup or mutation.
     * @param cost preemption count
     * @throws IllegalArgumentException if negative
     */
    private static void requireCost(int cost) {
        if (cost < 0) throw new IllegalArgumentException("Preemption cost must not be negative");
    }
}
