package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.por.IndependenceRelation;
import dev.samhb.interleave.search.*;
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
        Map<String, Configuration> visitedStates = new LinkedHashMap<>();
        List<Trace> traces = new ArrayList<>();
        long[] statesExplored = new long[1];

        Configuration initial = program.initialConfiguration();
        // Always use dporDfs — invariant is just another parameter
        dporDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                visitedStates, traces, invariant, statesExplored, 
                new SleepSet(), new HappensBefore());

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
                         HappensBefore happensBefore) {
        String key = config.state().toString() + "|" + config.programCounters();
        
        if (visitedStates.containsKey(key)) {
            wakeUp(program, config, currentThreadIds, currentOutcomes,
                   visitedStates, traces, invariant, statesExplored,
                   sleepSet, happensBefore);
            return;
        }
        
        visitedStates.put(key, config);
        statesExplored[0]++;
        
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
                            nextSleepSet.add(otherId, otherStep);
                        }
                    }
                }
            }
            
            dporDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                    visitedStates, traces, invariant, statesExplored,
                    nextSleepSet, nextHappensBefore);
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
                        new SleepSet(), new HappensBefore());
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
                        HappensBefore happensBefore) {
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
                    newSleepSet, happensBefore);
        }
    }

    }
