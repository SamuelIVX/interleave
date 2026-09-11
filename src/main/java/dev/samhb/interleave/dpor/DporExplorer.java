package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.por.IndependenceRelation;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.*;

public final class DporExplorer {
    private final IndependenceRelation relation;

    public DporExplorer() {
        this.relation = new IndependenceRelation();
    }

    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    public DfsResult explore(Program program, Invariant invariant) {
        return explore(program, invariant, null, null);
    }

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
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION));
            return;
        }

        if (config.allTerminated()) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.COMPLETED));
            return;
        }

        List<Integer> enabled = config.enabledThreadIds();
        if (enabled.isEmpty()) {
            // Check invariant before reporting deadlock
            if (invariant != null && !invariant.holds(config.state(), config)) {
                traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION));
            } else {
                traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK));
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

                Configuration nextConfig = config.successor(threadId, outcome, program.threads(), nextState);

                dporDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                        visitedStates, traces, invariant, statesExplored,
                        new SleepSet(), new HappensBefore(), stateStore, stateVisitor);
            }
        }
    }

    private void wakeUp(Program program, Configuration config,
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
        List<Integer> toWake = new ArrayList<>();
        Map<Integer, Step> toWakeSteps = new LinkedHashMap<>();

        for (Map.Entry<Integer, Step> entry : sleepSet.entries()) {
            int threadId = entry.getKey();
            Step step = entry.getValue();

            Set<Integer> predecessors = happensBefore.getThreadsThatHappenBefore(threadId);
            boolean dependent = false;
            for (int pred : predecessors) {
                Step predStep = happensBefore.getStep(pred, threadId);
                if (predStep == null) {
                    int recordedPc = happensBefore.getPcAtRecord(pred, threadId);
                    if (recordedPc < 0) {
                        continue; // truly no record
                    }
                    ModelThread predThread = program.threads().get(pred);
                    if (recordedPc >= predThread.steps().size()) {
                        continue; // predecessor already finished
                    }
                    predStep = predThread.steps().get(recordedPc);
                }
                if (!relation.areIndependent(step, predStep)) {
                    dependent = true;
                    break;
                }
            }

            if (dependent) {
                toWake.add(threadId);
                toWakeSteps.put(threadId, step);
            }
        }

        if (toWake.isEmpty()) {
            return;
        }

        SleepSet newSleepSet = sleepSet.copy();
        for (int threadId : toWake) {
            newSleepSet.remove(threadId);
        }

        for (int threadId : toWake) {
            Step step = toWakeSteps.get(threadId);
            if (step == null) continue;

            SharedState nextState = config.state().deepCopy();
            StepOutcome outcome = step.execute(nextState);

            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);

            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);

            Configuration nextConfig = config.successor(threadId, outcome, program.threads(), nextState);

            dporDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                    visitedStates, traces, invariant, statesExplored,
                    newSleepSet, happensBefore, stateStore, stateVisitor);
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
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION));
            return;
        }

        if (config.allTerminated()) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.COMPLETED));
            return;
        }

        List<Integer> enabled = config.enabledThreadIds();
        if (enabled.isEmpty()) {
            // Check invariant before reporting deadlock
            if (invariant != null && !invariant.holds(config.state(), config)) {
                traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION));
            } else {
                traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK));
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

            Configuration nextConfig = config.successor(threadId, outcome, program.threads(), nextState);

            dfsDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                   visitedStates, traces, invariant, statesExplored, stateStore, stateVisitor);
        }
    }
}