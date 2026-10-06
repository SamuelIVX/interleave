package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import dev.samhb.interleave.state.CanonicalEncoder;
import java.util.*;

/**
 * Partial-order reduction explorer: schedules only dependency-preserving interleavings.
 *
 * <p>Instead of stepping every enabled thread at every configuration, this computes the
 * {@link PersistentSetComputer persistent set} for each configuration — a subset of the enabled threads
 * selected by current-step dependencies — and branches only over that. The reduction relies on
 * {@link IndependenceRelation}: two steps that neither read nor write each other's locations commute,
 * so an interleaving that runs them in one order has an equivalent with the other order removed.
 *
 * <p><b>An invariant disables the reduction entirely.</b> {@link #porDfs} branches over the
 * {@link PersistentSetComputer persistent set} only when no invariant is supplied. With an invariant it
 * branches over every enabled thread, making the traversal identical to
 * {@link dev.samhb.interleave.search.DfsExplorer} and violation detection <b>exhaustive</b>: every
 * configuration that can be reached without first passing through a violating one is visited and checked.
 *
 * <p><b>That is weaker than complete configuration coverage, and the two must not be conflated.</b>
 * {@link #porDfs} returns as soon as a configuration violates, so its successors are never explored and
 * no configuration reachable only <em>through</em> a violating one is ever visited or checked. Measured
 * with no invariant against with, the shortfall is 9 configurations on each of {@code broken-peterson}
 * and {@code broken-peterson-v2}, 9 on {@code double-checked-locking} — where the totals happen to be
 * equal at 17 and only the membership differs, so a count comparison would not reveal it — and 1 on
 * {@code torn-counter}. A caller needing every reachable configuration visited must use
 * {@link dev.samhb.interleave.search.DfsExplorer} with a null invariant; this explorer's unguarded
 * reduction does not provide that coverage either.
 *
 * <p>Note the asymmetry that makes exhaustive detection the property worth having: a missed configuration
 * reachable only past a violation costs nothing, because the violation itself has already been reported.
 * What would be unsound is missing a violation that exists on a path never truncated — and disabling the
 * reduction is what guarantees no such path exists.
 *
 * <p><b>Why the reduction cannot be kept under an invariant.</b> The persistent set computed by
 * {@link PersistentSetComputer} is the <em>acyclic</em> set: a thread is retained only when it is
 * dependent on another enabled thread, otherwise one arbitrary enabled thread is returned. This
 * pairwise rule does not establish preservation of arbitrary state predicates: commuting steps can
 * have different intermediate states, and a predicate can reject one of them. A violation reachable
 * only through pruned interleavings is then never reported. Measured before this guard:
 * {@code broken-peterson-v2} yielded
 * 5 violating configurations under {@link dev.samhb.interleave.search.DfsExplorer} and
 * {@link TraceOutcome#COMPLETED} here — a false pass on a program whose expected verdict is
 * {@link TraceOutcome#VIOLATION} — while {@code broken-peterson} missed 4 of 5 and
 * {@code double-checked-locking} missed its only violating configuration.
 * {@link dev.samhb.interleave.dpor.DporExplorer} takes the same trade-off for the same reason.
 *
 * <p><b>Residual limitation, not a bug and not repairable by a better persistent set.</b> The reduction
 * is unsound for <em>trace completeness</em>: every execution the trace list reports is real, but not
 * every real execution appears. That is what reduction is for — two orderings of independent actions reach
 * the same configuration and POR keeps one — so no sound persistent set, and no sleep-set or DPOR
 * method, restores them. A caller needing a specific schedule in the output must use
 * {@link dev.samhb.interleave.search.DfsExplorer}, with or without an invariant.
 *
 * <p>A future reduction needs a <b>property-preservation proof</b>, not merely a larger pairwise set.
 * Measured with no invariant, the current set is not
 * reachability-complete: {@code broken-peterson-v2} visits 12 of 55 reachable configurations and
 * {@code broken-peterson} 17 of 55, so a configuration can be pruned away entirely. Adding the
 * pairwise "dependent on every other enabled thread" set leaves the existing set unchanged, and
 * persistence alone does not guarantee complete configuration coverage. Spec 13.08 records the
 * restricted no-op proof and an independent-write counterexample. The guard remains until a future
 * design establishes preservation of the supplied invariant.
 *
 * <p>Like the other explorers, this keys visited states through a {@link dev.samhb.interleave.state.HashingStateStore}
 * by default, so its visited set inherits that store's encoding fidelity.
 */
public final class StaticPorExplorer {

    private final IndependenceRelation relation;
    private final CanonicalEncoder encoder = new CanonicalEncoder();
    private final PersistentSetComputer persistentSetComputer;

    /**
     * Creates an explorer with the default independence relation and persistent-set computer.
     *
     * <p>Both are derived from the standard read/write and enable/disable rules rather than being
     * configurable, so every instance computes the same relation.
     */
    public StaticPorExplorer() {
        this.relation = new IndependenceRelation();
        this.persistentSetComputer = new PersistentSetComputer(relation);
    }

    /**
     * Explores without an invariant.
     *
     * @param program the program to explore
     * @return the visited configurations and the traces reached
     */
    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    /**
     * Explores, checking an invariant at every configuration.
     *
     * @param program the program to explore
     * @param invariant the invariant to check, or null
     * @return the visited configurations and the traces reached
     */
    public DfsResult explore(Program program, Invariant invariant) {
        return explore(program, invariant, null, null);
    }

    /**
     * Explores with an explicit visited store and visitor.
     *
     * <p>The supplied store is cleared before traversal, so a caller passing a store shared with another
     * explorer gets an empty starting point rather than inheriting its visited set and silently
     * pruning configurations it never examined.
     *
     * @param program the program to explore
     * @param invariant the invariant to check, or null
     * @param stateStore the visited store, or null for a fresh {@link HashingStateStore}
     * @param stateVisitor notified of each visited configuration, or null
     * @return the visited configurations and the traces reached
     */
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {
        StateStore effectiveStateStore = stateStore != null ? stateStore : new HashingStateStore();
        effectiveStateStore.clear(); // Clear before traversal to avoid pre-populated store issues
        Map<String, Configuration> visitedStates = new LinkedHashMap<>();
        List<Trace> traces = new ArrayList<>();
        long[] statesExplored = new long[1];

        Configuration initial = program.initialConfiguration();
        porDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
               visitedStates, traces, invariant, statesExplored, effectiveStateStore, stateVisitor);

        return new DfsResult(visitedStates, traces, statesExplored[0]);
    }

    private void porDfs(Program program, Configuration config,
                        List<Integer> currentThreadIds,
                        List<StepOutcome> currentOutcomes,
                        Map<String, Configuration> visitedStates,
                        List<Trace> traces,
                        Invariant invariant,
                        long[] statesExplored,
                        StateStore stateStore,
                        StateVisitor stateVisitor) {
        if (stateStore.isVisited(config)) {
            return;
        }

        stateStore.markVisited(config);
        visitedStates.put(encoder.configurationKey(config), config);
        statesExplored[0]++;

        if (stateVisitor != null) {
            stateVisitor.onStateVisited(config);
        }

        if (invariant != null && !invariant.holds(config.state(), config)) {
            Trace trace = Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
            return;
        }

        if (config.allTerminated()) {
            Trace trace = Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.COMPLETED);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
            return;
        }

        // Soundness guard: an invariant is a user-supplied predicate over states, so the reduction
        // cannot be sound for it. The persistent set computed here is the *acyclic* set, which keeps a
        // thread only when it is dependent on another enabled thread and otherwise returns one
        // arbitrary enabled thread. That does not establish arbitrary invariant preservation,
        // so configurations reachable only through pruned interleavings are never
        // visited and a violation there is never reported. Measured before this guard:
        // broken-peterson-v2 reported COMPLETED where DfsExplorer reported VIOLATION.
        //
        // So branch over every enabled thread whenever an invariant is present, which makes this
        // traversal identical to DfsExplorer's and the invariant check exhaustive over the
        // reachable configurations. DporExplorer takes the same trade-off for the same reason.
        // See spec 13.08 for why a pairwise source-set union does not justify removing the guard.
        List<Integer> branchSet = invariant == null
            ? persistentSetComputer.computePersistentSet(config, program.threads())
            : config.enabledThreadIds();

        for (int threadId : branchSet) {
            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);
            if (step == null) continue;

            SharedState nextState = config.state().deepCopy();
            StepOutcome outcome = step.execute(nextState);

            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);

            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);

            if (outcome == StepOutcome.ASSERTION_FAILED) {
                Trace trace = Trace.of(List.copyOf(nextThreadIds), List.copyOf(nextOutcomes), TraceOutcome.VIOLATION);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
                continue;
            }

            Configuration nextConfig = config.successor(threadId, outcome, program.threads(), nextState);

            porDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                   visitedStates, traces, invariant, statesExplored, stateStore, stateVisitor);
        }

        if (config.enabledThreadIds().isEmpty() && !config.allTerminated()) {
            // Check invariant before reporting deadlock
            if (invariant != null && !invariant.holds(config.state(), config)) {
                Trace trace = Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
            } else {
                Trace trace = Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
            }
        }
    }
}
