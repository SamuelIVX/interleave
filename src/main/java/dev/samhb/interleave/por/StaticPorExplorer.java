/** Explores finite model threads with exhaustive fallback or property-aware static reduction. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import dev.samhb.interleave.state.CanonicalEncoder;
import java.util.*;

/**
 * Static partial-order search with conservative support for observed state-only properties.
 *
 * <p>Finite source models also support the explicit {@link Invariant#finalObservedLocations()}
 * contract under Spec 70; termination is its only configuration-dependent observation gate.
 * Ordinary invariant callbacks branch exhaustively. Properties declaring complete
 * {@link Invariant#observedLocations()} may use an invisible component closed over all remaining
 * thread dependencies, provided its executed actions advance their counters. Unknown observations,
 * visible/dependent components and non-progressing actions retain exhaustive branching.
 *
 * <p>With complete stable step footprints, pure value-based predicates, non-mutating visitors,
 * faithful state copying/encoding and an exact store, the observed path preserves violation
 * detection and terminal outcomes. It does not enumerate every configuration or schedule, nor
 * promise the shortest counterexample. Bitstate stores retain their independent false-positive risk.
 * The finite linear-thread proof and its assumptions are recorded in spec 13.08.
 *
 * <p>Without an invariant, source models still use remaining-thread dependency closure with no
 * property observations; legacy models use the current-step selector. DPOR's invariant fallback
 * is separate.
 * A caller requiring full configuration enumeration must use DFS without an invariant; DFS with
 * an invariant still stops each path at its first violation.
 */
public final class StaticPorExplorer {

    /** Relation. */
    private final IndependenceRelation relation;
    /** Encoder. */
    private final CanonicalEncoder encoder = new CanonicalEncoder();
    /** Persistent set computer. */
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
     * @return visited configurations, recorded traces, and exploration count
     */
    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    /**
     * Explores, checking an invariant at every configuration.
     *
     * @param program the program to explore
     * @param invariant the invariant to check, or null; declared state observations permit reduction
     * @return visited configurations, recorded traces, and exploration count
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
     * @param stateVisitor notified of each explored configuration, or null; must not mutate it
     * @return visited configurations, recorded traces, and exploration count
     * @throws IllegalArgumentException if a symmetry store is bound to another program, property or strategy
     */
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {
        StateStore effectiveStateStore = stateStore != null ? stateStore : new HashingStateStore();
        if (effectiveStateStore instanceof dev.samhb.interleave.search.CheckedSymmetryStore symmetry) {
            symmetry.requireRun(program, invariant, dev.samhb.interleave.Strategy.STATIC_POR);
        }
        effectiveStateStore.clear(); // Clear before traversal to avoid pre-populated store issues
        Map<String, Configuration> visitedStates = new LinkedHashMap<>();
        List<Trace> traces = new ArrayList<>();
        Set<CutoffEvidence> emittedCutoffs = new HashSet<>();
        long[] statesExplored = new long[1];

        Optional<Set<MemoryLocation>> observations = invariant != null ? invariant.observedLocations() : Optional.empty();
        if (program.isSource() && invariant != null && observations.isEmpty()) {
            observations = invariant.finalObservedLocations();
        }
        PropertyPersistentSetComputer propertySets = invariant == null ? (program.isSource()
            ? new PropertyPersistentSetComputer(program.threads(), Set.of(), relation) : null)
            : observations
                .map(locations -> new PropertyPersistentSetComputer(program.threads(), locations, relation))
                .orElse(null);
        Configuration initial = program.initialConfiguration();
        porDfs(program, initial, new ArrayList<>(), new ArrayList<>(), TracePrefix.empty(),
               visitedStates, traces, emittedCutoffs, invariant, statesExplored, effectiveStateStore, stateVisitor, propertySets);

        return new DfsResult(visitedStates, traces, statesExplored[0]);
    }

    /**
     * Visits a position and recursively explores the selected real transitions.
     * @param program immutable model
     * @param config current position
     * @param currentThreadIds schedule prefix
     * @param currentOutcomes executed-outcome prefix
     * @param prefix immutable source evidence for actual traversal edges
     * @param visitedStates value-keyed explored positions
     * @param emittedCutoffs run-local loop witnesses already emitted, preserving the first prefix
     * @param traces emitted evidence
     * @param invariant safety predicate, or null
     * @param statesExplored mutable visit-event counter
     * @param stateStore duplicate suppression for this run
     * @param stateVisitor non-mutating observer, or null
     * @param propertySets run-local observed-property analysis, or null for the existing paths
     */
    private void porDfs(Program program, Configuration config,
                        List<Integer> currentThreadIds,
                        List<StepOutcome> currentOutcomes,
                        TracePrefix prefix,
                        Map<String, Configuration> visitedStates,
                        List<Trace> traces,
                        Set<CutoffEvidence> emittedCutoffs,
                        Invariant invariant,
                        long[] statesExplored,
                        StateStore stateStore,
                        StateVisitor stateVisitor,
                        PropertyPersistentSetComputer propertySets) {
        if (stateStore.isVisited(config)) {
            return;
        }

        stateStore.markVisited(config);
        visitedStates.put(encoder.configurationKey(config), config);
        statesExplored[0]++;

        if (stateVisitor != null) {
            try {
                stateVisitor.onStateVisited(config);
            } catch (dev.samhb.interleave.LimitExceededException interrupted) {
                if (program.isSource()) {
                    stateVisitor.onInterruptedPrefix(config, prefix.trace(program, currentThreadIds, currentOutcomes,
                        TraceOutcome.INCOMPLETE, null, config, null));
                }
                throw interrupted;
            }
        }

        if (invariant != null && !invariant.holds(config.state(), config)) {
            Trace trace = prefix.trace(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION, invariant, config, null);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
            return;
        }

        if (config.allTerminated()) {
            Trace trace = prefix.trace(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.COMPLETED, invariant, config, null);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
            return;
        }

        List<Integer> enabled = config.enabledThreadIds();
        List<Integer> branchSet = propertySets != null ? propertySets.compute(config)
            : invariant == null ? persistentSetComputer.computePersistentSet(config, program.threads()) : enabled;

        // Footprints prove invisibility and persistence, not progress. Execute candidates once on
        // isolated copies and reuse them, even if a blocked/assertion result requires full fallback.
        Map<Integer, StepTransition> prepared = new HashMap<>();
        if (propertySets != null && branchSet.size() < enabled.size()) {
            boolean progresses = true;
            for (int id : branchSet) {
                StepTransition transition = prepare(program, config, id);
                prepared.put(id, transition);
                if (transition.outcome() != StepOutcome.ADVANCED
                        && transition.outcome() != StepOutcome.TERMINATED) progresses = false;
            }
            if (!progresses) branchSet = enabled;
        }

        for (int threadId : branchSet) {
            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);
            if (step == null) continue;

            StepTransition transition = prepared.get(threadId);
            if (transition == null) transition = prepare(program, config, threadId);
            CutoffEvidence evidence = transition.cutoff();
            if (evidence != null) {
                if (emittedCutoffs.add(evidence)) {
                    Trace cutoff = prefix.trace(program, currentThreadIds, currentOutcomes, TraceOutcome.INCOMPLETE, invariant, config, evidence);
                    traces.add(cutoff);
                    if (stateVisitor != null) stateVisitor.onTraceCreated(cutoff);
                }
                continue;
            }
            StepOutcome outcome = transition.outcome();
            TracePrefix nextPrefix = prefix.append(program, config, threadId, transition);

            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);

            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);

            if (outcome == StepOutcome.ASSERTION_FAILED) {
                Trace trace = nextPrefix.trace(program, List.copyOf(nextThreadIds), List.copyOf(nextOutcomes), TraceOutcome.VIOLATION, invariant, config, null);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
                continue;
            }

            Configuration nextConfig = transition.successor();

            porDfs(program, nextConfig, nextThreadIds, nextOutcomes, nextPrefix,
                   visitedStates, traces, emittedCutoffs, invariant, statesExplored, stateStore, stateVisitor, propertySets);
        }

        if (config.enabledThreadIds().isEmpty() && !config.allTerminated()) {
            // Check invariant before reporting deadlock
            if (invariant != null && !invariant.holds(config.state(), config)) {
                Trace trace = prefix.trace(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION, invariant, config, null);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
            } else {
                Trace trace = prefix.trace(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK, invariant, config, null);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
            }
        }
    }

    /**
     * Executes one transition on a copy, without mutating the visited configuration.
     * @param program model containing the selected step
     * @param config position before execution
     * @param threadId selected thread
     * @return the outcome and isolated successor state, reused by traversal
     */
    private StepTransition prepare(Program program, Configuration config, int threadId) {
        return TransitionExecutor.execute(program, config, threadId);
    }


}
