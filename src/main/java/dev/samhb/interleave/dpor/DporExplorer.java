package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.por.IndependenceRelation;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
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
 * <p><b>An invariant disables the reduction entirely.</b> When {@link #explore(Program, Invariant)}
 * is given an invariant, this explorer deliberately delegates to a plain depth-first search with no
 * sleep set. The reduction prunes schedules by arguing that reordering commuting steps cannot change
 * the reachable outcome — but an invariant is exactly a user-supplied predicate over states, so that
 * argument does not hold for one, and the traces it produces must be complete for it to be meaningful.
 * Passing a null invariant gets the reduced search; passing one gets exhaustive search. This is a real
 * difference in behaviour, not an optimization, and callers comparing the two modes should expect the
 * reduced mode to visit fewer configurations.
 *
 * <p>Deadlock is reported only after the invariant is checked at the same configuration, so a state that
 * both blocks every thread and violates the invariant is recorded as a violation rather than as a
 * deadlock — the more specific finding wins.
 */
public final class DporExplorer {

    private final IndependenceRelation relation;

    /** Creates an explorer with the default independence relation. */
    public DporExplorer() {
        this.relation = new IndependenceRelation();
    }

    /**
     * Explores without an invariant, using dynamic partial-order reduction.
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
     * <p>Supplying an invariant switches this to plain exhaustive depth-first search; see the class
     * Javadoc for why the reduction is disabled rather than applied.
     *
     * @param program the program to explore
     * @param invariant the invariant to check, or null to keep the reduction
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
     * @param invariant the invariant to check, or null to keep the reduction
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
        if (invariant == null) {
            dporDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                    visitedStates, traces, invariant, statesExplored,
                    new SleepSet(), new HappensBefore(), effectiveStateStore, stateVisitor);
        } else {
            dfsDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                    visitedStates, traces, invariant, statesExplored, effectiveStateStore, stateVisitor);
        }

        return new DfsResult(visitedStates, traces, statesExplored[0]);
    }

    private void dporDfs(Program program, Configuration config,
                         List<Integer> currentThreadIds,
                         List<StepOutcome> currentOutcomes,
                         Map<String, Configuration> visitedStates,
                         List<Trace> traces,
                         Invariant invariant,
                         long[] statesExplored,
                         SleepSet sleepSet,
                         HappensBefore happensBefore,
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

        List<Integer> enabled = config.enabledThreadIds();
        if (enabled.isEmpty()) {
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

            HappensBefore nextHappensBefore = happensBefore.copy();
            for (int otherId : enabled) {
                if (otherId != threadId) {
                    nextHappensBefore.record(threadId, otherId, step, pc);
                }
            }

            SleepSet nextSleepSet = sleepSet.copyFiltering(relation, step);
            for (int otherId : enabled) {
                if (otherId != threadId) {
                    ModelThread otherThread = program.threads().get(otherId);
                    int otherPc = config.programCounters().get(otherId);
                    Step otherStep = otherThread.steps().get(otherPc);
                    if (otherStep != null) {
                        boolean independent = relation.areIndependent(step, otherStep);
                        boolean noInterference = !relation.hasEnableDisableInterference(config, threadId, otherId, program.threads());
                        if (independent && noInterference) {
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
                    visitedStates, traces, invariant, statesExplored,
                    nextSleepSet, nextHappensBefore, stateStore, stateVisitor);
        }

        if (!explored) {
            for (int threadId : enabled) {
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

                dporDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                        visitedStates, traces, invariant, statesExplored,
                        new SleepSet(), new HappensBefore(), stateStore, stateVisitor);
            }
        }
    }

    private void dfsDfs(Program program, Configuration config,
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

        List<Integer> enabled = config.enabledThreadIds();
        if (enabled.isEmpty()) {
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
            return;
        }

        for (int threadId : enabled) {
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

            dfsDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                   visitedStates, traces, invariant, statesExplored, stateStore, stateVisitor);
        }
    }
}
