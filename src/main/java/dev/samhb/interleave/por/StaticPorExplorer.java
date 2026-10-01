package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.*;

/**
 * Partial-order reduction explorer: schedules only dependency-preserving interleavings.
 *
 * <p>Instead of stepping every enabled thread at every configuration, this computes the
 * {@link PersistentSetComputer persistent set} for each configuration — a subset of the enabled threads
 * that can reach the same reachable states — and branches only over that. Soundness rests on
 * {@link IndependenceRelation}: two steps that neither read nor write each other's locations commute,
 * so an interleaving that runs them in one order has an equivalent with the other order removed.
 *
 * <p>Invariants are checked on every visited configuration, so a violation is reported wherever it
 * occurs rather than only along the reduced schedule set.
 *
 * <p><b>Pruning is unsound for trace completeness.</b> Every execution the trace list reports is a
 * real execution, but not every real execution appears, because independence-based reduction removes
 * interleavings that are equivalent up to reordering. Use {@link dev.samhb.interleave.search.DfsExplorer}
 * when a specific schedule must be present in the output.
 *
 * <p>Like the other explorers, this keys visited states through a {@link dev.samhb.interleave.state.HashingStateStore}
 * by default, so its visited set inherits that store's encoding fidelity.
 */
public final class StaticPorExplorer {

    private final IndependenceRelation relation;
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
        String key = config.state().toString() + "|" + config.programCounters();

        if (stateStore.isVisited(config)) {
            return;
        }

        stateStore.markVisited(config);
        visitedStates.put(key, config);
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

        List<Integer> persistentSet = persistentSetComputer.computePersistentSet(
            config, program.threads()
        );

        for (int threadId : persistentSet) {
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
