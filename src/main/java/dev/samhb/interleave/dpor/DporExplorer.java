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
        if (invariant == null) {
            dporDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                    visitedStates, traces, invariant, statesExplored, 
                    new SleepSet());
        } else {
            dfsDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                    visitedStates, traces, invariant, statesExplored);
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
                         SleepSet sleepSet) {
        String key = config.state().toString() + "|" + config.programCounters();
        
        if (visitedStates.containsKey(key)) {
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
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK));
            return;
        }
        
        boolean explored = false;
        for (int threadId : enabled) {
            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);
            if (step == null || sleepSet.contains(step)) {
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
            
            SleepSet nextSleepSet = sleepSet.copy();
            for (int otherId : enabled) {
                if (otherId != threadId) {
                    ModelThread otherThread = program.threads().get(otherId);
                    int otherPc = config.programCounters().get(otherId);
                    Step otherStep = otherThread.steps().get(otherPc);
                    if (otherStep != null) {
                        boolean independent = relation.areIndependent(step, otherStep);
                        boolean noInterference = !relation.hasEnableDisableInterference(config, threadId, otherId, program.threads());
                        if (independent && noInterference) {
                            nextSleepSet.add(otherStep);
                        }
                    }
                }
            }
            
            dporDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                    visitedStates, traces, invariant, statesExplored,
                    nextSleepSet);
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
                        new SleepSet());
            }
        }
    }

    private void dfsDfs(Program program, Configuration config,
                        List<Integer> currentThreadIds,
                        List<StepOutcome> currentOutcomes,
                        Map<String, Configuration> visitedStates,
                        List<Trace> traces,
                        Invariant invariant,
                        long[] statesExplored) {
        String key = config.state().toString() + "|" + config.programCounters();
        
        if (visitedStates.containsKey(key)) {
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
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK));
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
                   visitedStates, traces, invariant, statesExplored);
        }
    }
}
