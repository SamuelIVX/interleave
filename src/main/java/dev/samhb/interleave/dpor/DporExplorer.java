/** Dynamic partial-order reduction explorer. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.por.IndependenceRelation;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import dev.samhb.interleave.state.CanonicalEncoder;
import java.util.*;

/**
 * Dynamic partial-order reduction explorer.
 *
 * <p>Extends {@link dev.samhb.interleave.por.StaticPorExplorer}'s approach with a sleep set that
 * evolves along each path. Where static POR recomputes a persistent set per configuration, DPOR threads
 * a {@link SleepSet} through the recursion, remembering which commuting steps have already been
 * explored on the current path, and skips them via {@code sleepSet.contains}. A step is added to the
 * successor's sleep set only when it is independent, does not interfere with enable/disable, and the
 * stepping thread has no later dependent step that could observe a difference.
 *
 * <p><b>Property-aware reduction:</b> When an invariant declares complete {@link Invariant#observedLocations()},
 * dynamic partial-order reduction is applied using property visibility: transitions that write to observed
 * locations are treated as visible and never placed into sleep sets, and executing a visible transition
 * refreshes sleeping transitions. Independent invisible transitions are pruned via sleep sets, reducing
 * explored states while preserving all invariant violations. Invariants with unknown observations (e.g.
 * unannotated callbacks or DSL {@code final} checks) retain exhaustive depth-first search fallback.
 *
 * <p>Deadlock is reported only after the invariant is checked at the same configuration, so a state that
 * both blocks every thread and violates the invariant is recorded as a violation rather than as a
 * deadlock — the more specific finding wins.
 */
public final class DporExplorer {

    /** Relation. */
    private final IndependenceRelation relation;
    /** Encoder. */
    private final CanonicalEncoder encoder = new CanonicalEncoder();

    /** Creates an explorer with the default independence relation. */
    public DporExplorer() {
        this.relation = new IndependenceRelation();
    }

    /**
     * Explores without an invariant, using dynamic partial-order reduction.
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
     * <p>When the invariant declares complete {@link Invariant#observedLocations()}, dynamic reduction
     * prunes independent invisible schedules. Invariants with unknown observations fall back to exhaustive search.
     *
     * @param program the program to explore
     * @param invariant the invariant to check, or null to keep the reduction
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
     * @param invariant the invariant to check, or null to keep the reduction
     * @param stateStore the visited store, or null for a fresh {@link HashingStateStore}
     * @param stateVisitor notified of each visited configuration, or null
     * @return visited configurations, recorded traces, and exploration count
     */
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {
        StateStore effectiveStateStore = stateStore != null ? stateStore : new HashingStateStore();
        effectiveStateStore.clear(); // Clear before traversal to avoid pre-populated store issues
        Map<String, Configuration> visitedStates = new LinkedHashMap<>();
        List<Trace> traces = new ArrayList<>();
        Set<CutoffEvidence> emittedCutoffs = new HashSet<>();
        long[] statesExplored = new long[1];
        Map<String, List<SleepSet>> sleepCoverage = new HashMap<>();

        Configuration initial = program.initialConfiguration();
        Optional<Set<MemoryLocation>> maybeObs = invariant != null ? invariant.observedLocations() : Optional.empty();
        if (invariant == null) {
            dporDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                    visitedStates, traces, emittedCutoffs, invariant, statesExplored,
                    new SleepSet(), new HappensBefore(), effectiveStateStore, stateVisitor, null, sleepCoverage);
        } else if (maybeObs.isPresent()) {
            dporDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                    visitedStates, traces, emittedCutoffs, invariant, statesExplored,
                    new SleepSet(), new HappensBefore(), effectiveStateStore, stateVisitor, maybeObs.get(), sleepCoverage);
        } else {
            dfsDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                    visitedStates, traces, emittedCutoffs, invariant, statesExplored, effectiveStateStore, stateVisitor);
        }

        return new DfsResult(visitedStates, traces, statesExplored[0]);
    }

    /**
     * Explores dependent schedules using sleep sets and future-dependency checks.
     * @param program modeled program whose threads are explored
     * @param config current search configuration
     * @param currentThreadIds mutable thread-choice prefix, restored after recursive exploration
     * @param currentOutcomes mutable step-outcome prefix, restored after recursive exploration
     * @param visitedStates configurations indexed by canonical value key
     * @param emittedCutoffs run-local loop witnesses already emitted, preserving the first prefix
     * @param traces recorded executions
     * @param invariant property to check, or null when no property is supplied
     * @param statesExplored number of explored configurations
     * @param sleepSet run-local sleeping transitions used to suppress redundant schedules
     * @param happensBefore ordering information for the current execution prefix
     * @param stateStore visited-position store used for pruning
     * @param stateVisitor optional observer of visited states and emitted traces
     * @param sleepCoverage source-only antichains of previously explored sleeping transitions
     * @param observations known state-observation locations, or null when exploring without an observed property
     */
    private void dporDfs(Program program, Configuration config,
                         List<Integer> currentThreadIds,
                         List<StepOutcome> currentOutcomes,
                         Map<String, Configuration> visitedStates,
                         List<Trace> traces,
                        Set<CutoffEvidence> emittedCutoffs,
                         Invariant invariant,
                         long[] statesExplored,
                         SleepSet sleepSet,
                         HappensBefore happensBefore,
                         StateStore stateStore,
                         StateVisitor stateVisitor,
                         Set<MemoryLocation> observations,
                         Map<String, List<SleepSet>> sleepCoverage) {
        boolean seen = stateStore.isVisited(config);
        if (program.isSource()) {
            List<Integer> enabledHere = config.enabledThreadIds();
            boolean allSleeping = !enabledHere.isEmpty();
            for (int id : enabledHere) {
                Step action = program.threads().get(id).steps().get(config.programCounters().get(id));
                if (!sleepSet.contains(id, action)) allSleeping = false;
            }
            // Normalize the exhaustive all-asleep fallback before comparing coverage contexts.
            if (allSleeping) sleepSet = new SleepSet();
            String key = encoder.configurationKey(config);
            // Preserve approximate-store collision pruning; exact revisits require sleep-set coverage.
            if (seen && !sleepCoverage.containsKey(key)) return;
            List<SleepSet> covered = sleepCoverage.computeIfAbsent(key, ignored -> new ArrayList<>());
            Set<Map.Entry<Integer, Step>> incoming = sleepSet.entries();
            if (covered.stream().anyMatch(previous -> incoming.containsAll(previous.entries()))) return;
            covered.removeIf(previous -> previous.entries().containsAll(incoming));
            covered.add(sleepSet.copy());
        } else if (seen) {
            return;
        }

        stateStore.markVisited(config);
        visitedStates.put(encoder.configurationKey(config), config);
        statesExplored[0]++;

        if (stateVisitor != null) {
            stateVisitor.onStateVisited(config);
        }

        if (invariant != null && !invariant.holds(config.state(), config)) {
            Trace trace = Trace.create(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION, invariant);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
            return;
        }

        if (config.allTerminated()) {
            Trace trace = Trace.create(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.COMPLETED, invariant);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
            return;
        }

        List<Integer> enabled = config.enabledThreadIds();
        if (enabled.isEmpty()) {
            // Check invariant before reporting deadlock
            if (invariant != null && !invariant.holds(config.state(), config)) {
                Trace trace = Trace.create(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION, invariant);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
            } else {
                Trace trace = Trace.create(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK, invariant);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
            }
            return;
        }

        boolean explored = false;
        for (int threadId : enabled) {
            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);
            if (step == null || sleepSet.contains(threadId, step)) {
                continue;
            }
            explored = true;

            StepTransition execution = TransitionExecutor.execute(program, config, threadId);
            if (execution.cutoff() != null) {
                if (emittedCutoffs.add(execution.cutoff())) {
                    Trace cutoff = Trace.create(program, currentThreadIds, currentOutcomes, TraceOutcome.INCOMPLETE, invariant, execution.cutoff());
                    traces.add(cutoff);
                    if (stateVisitor != null) stateVisitor.onTraceCreated(cutoff);
                }
                continue;
            }
            StepOutcome outcome = execution.outcome();

            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);

            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);

            if (outcome == StepOutcome.ASSERTION_FAILED) {
                Trace trace = Trace.create(program, List.copyOf(nextThreadIds), List.copyOf(nextOutcomes), TraceOutcome.VIOLATION, invariant);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
                continue;
            }

            Configuration nextConfig = execution.successor();

            HappensBefore nextHappensBefore = happensBefore.copy();
            for (int otherId : enabled) {
                if (otherId != threadId) {
                    nextHappensBefore.record(threadId, otherId, step, pc);
                }
            }

            boolean stepVisible = observations != null && relation.writesOverlap(step.writes(), observations);
            SleepSet nextSleepSet = stepVisible ? new SleepSet() : sleepSet.copyFiltering(relation, step);
            for (int otherId : enabled) {
                if (otherId != threadId) {
                    ModelThread otherThread = program.threads().get(otherId);
                    int otherPc = config.programCounters().get(otherId);
                    Step otherStep = otherThread.steps().get(otherPc);
                    if (otherStep != null) {
                        boolean independent = relation.areIndependent(step, otherStep);
                        boolean noInterference = !relation.hasEnableDisableInterference(config, threadId, otherId, program.threads());
                        boolean otherInvisible = observations == null || !relation.writesOverlap(otherStep.writes(), observations);
                        if (independent && noInterference && otherInvisible) {
                            boolean hasFutureDependent = false;
                            List<Step> currentThreadSteps = thread.steps();
                            for (int futurePc = pc + 1; futurePc < currentThreadSteps.size(); futurePc++) {
                                Step futureStep = currentThreadSteps.get(futurePc);
                                if (futureStep != null && !relation.areIndependent(futureStep, otherStep)) {
                                    hasFutureDependent = true;
                                    break;
                                }
                            }
                            if (!hasFutureDependent) {
                                nextSleepSet.add(otherId, otherStep);
                            }
                        }
                    }
                }
            }

            dporDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                    visitedStates, traces, emittedCutoffs, invariant, statesExplored,
                    nextSleepSet, nextHappensBefore, stateStore, stateVisitor, observations, sleepCoverage);
        }

        if (!explored) {
            for (int threadId : enabled) {
                ModelThread thread = program.threads().get(threadId);
                int pc = config.programCounters().get(threadId);
                Step step = thread.steps().get(pc);
                if (step == null) continue;

                StepTransition execution = TransitionExecutor.execute(program, config, threadId);
                if (execution.cutoff() != null) {
                    if (emittedCutoffs.add(execution.cutoff())) {
                        Trace cutoff = Trace.create(program, currentThreadIds, currentOutcomes, TraceOutcome.INCOMPLETE, invariant, execution.cutoff());
                        traces.add(cutoff);
                        if (stateVisitor != null) stateVisitor.onTraceCreated(cutoff);
                    }
                    continue;
                }
                StepOutcome outcome = execution.outcome();

                List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
                nextThreadIds.add(threadId);

                List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
                nextOutcomes.add(outcome);

                if (outcome == StepOutcome.ASSERTION_FAILED) {
                    Trace trace = Trace.create(program, List.copyOf(nextThreadIds), List.copyOf(nextOutcomes), TraceOutcome.VIOLATION, invariant);
                    traces.add(trace);
                    if (stateVisitor != null) {
                        stateVisitor.onTraceCreated(trace);
                    }
                    continue;
                }

                Configuration nextConfig = execution.successor();

                dporDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                        visitedStates, traces, emittedCutoffs, invariant, statesExplored,
                        new SleepSet(), new HappensBefore(), stateStore, stateVisitor, observations, sleepCoverage);
            }
        }
    }

    /**
     * Explores every enabled successor when property checking requires exhaustive search.
     * @param program modeled program whose threads are explored
     * @param config current search configuration
     * @param currentThreadIds mutable thread-choice prefix, restored after recursive exploration
     * @param currentOutcomes mutable step-outcome prefix, restored after recursive exploration
     * @param visitedStates configurations indexed by canonical value key
     * @param emittedCutoffs run-local loop witnesses already emitted, preserving the first prefix
     * @param traces recorded executions
     * @param invariant property to check, or null when no property is supplied
     * @param statesExplored number of explored configurations
     * @param stateStore visited-position store used for pruning
     * @param stateVisitor optional observer of visited states and emitted traces
     */
    private void dfsDfs(Program program, Configuration config,
                        List<Integer> currentThreadIds,
                        List<StepOutcome> currentOutcomes,
                        Map<String, Configuration> visitedStates,
                        List<Trace> traces,
                        Set<CutoffEvidence> emittedCutoffs,
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
            Trace trace = Trace.create(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION, invariant);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
            return;
        }

        if (config.allTerminated()) {
            Trace trace = Trace.create(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.COMPLETED, invariant);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
            return;
        }

        List<Integer> enabled = config.enabledThreadIds();
        if (enabled.isEmpty()) {
            // Check invariant before reporting deadlock
            if (invariant != null && !invariant.holds(config.state(), config)) {
                Trace trace = Trace.create(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION, invariant);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
            } else {
                Trace trace = Trace.create(program, List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK, invariant);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
            }
            return;
        }

        for (int threadId : enabled) {
            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);
            if (step == null) continue;

            StepTransition execution = TransitionExecutor.execute(program, config, threadId);
            if (execution.cutoff() != null) {
                if (emittedCutoffs.add(execution.cutoff())) {
                    Trace cutoff = Trace.create(program, currentThreadIds, currentOutcomes, TraceOutcome.INCOMPLETE, invariant, execution.cutoff());
                    traces.add(cutoff);
                    if (stateVisitor != null) stateVisitor.onTraceCreated(cutoff);
                }
                continue;
            }
            StepOutcome outcome = execution.outcome();

            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);

            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);

            if (outcome == StepOutcome.ASSERTION_FAILED) {
                Trace trace = Trace.create(program, List.copyOf(nextThreadIds), List.copyOf(nextOutcomes), TraceOutcome.VIOLATION, invariant);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
                continue;
            }

            Configuration nextConfig = execution.successor();

            dfsDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                   visitedStates, traces, emittedCutoffs, invariant, statesExplored, stateStore, stateVisitor);
        }
    }
}
