package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.por.IndependenceRelation;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.*;

public final class DporExplorer {
    private final IndependenceRelation relation;

    /** DporExplorer method. */
    public DporExplorer() {
        this.relation = new IndependenceRelation();
    }

    /** explore method. */
    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    /** explore method. */
    public DfsResult explore(Program program, Invariant invariant) {
        return explore(program, invariant, null, null);
    }

    /** explore method. */
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
