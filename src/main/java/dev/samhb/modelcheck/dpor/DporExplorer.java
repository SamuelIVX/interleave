package dev.samhb.modelcheck.dpor;

import dev.samhb.modelcheck.core.*;
import dev.samhb.modelcheck.search.*;
import java.util.*;

public final class DporExplorer {
    private final HappensBefore happensBefore;
    private final SleepSet sleepSet;

    public DporExplorer() {
        this.happensBefore = new HappensBefore();
        this.sleepSet = new SleepSet();
    }

    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    public DfsResult explore(Program program, Invariant invariant) {
        Map<String, Configuration> visitedStates = new LinkedHashMap<>();
        List<Trace> traces = new ArrayList<>();
        long[] statesExplored = new long[1];

        Configuration initial = program.initialConfiguration();
        dporDfs(program, initial, new ArrayList<>(), new ArrayList<>(),
                visitedStates, traces, invariant, statesExplored, 
                new HappensBefore(), new SleepSet());

        return new DfsResult(visitedStates, traces, statesExplored[0]);
    }

    private void dporDfs(Program program, Configuration config,
                         List<Integer> currentThreadIds,
                         List<StepOutcome> currentOutcomes,
                         Map<String, Configuration> visitedStates,
                         List<Trace> traces,
                         Invariant invariant,
                         long[] statesExplored,
                         HappensBefore hb,
                         SleepSet sleepSet) {
        String key = config.state().toString();
        
        if (visitedStates.containsKey(key)) {
            return;
        }
        
        visitedStates.put(key, config);
        statesExplored[0]++;
        
        if (invariant != null && !invariant.holds(config.state(), config)) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes)));
            return;
        }
        
        if (config.allTerminated()) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes)));
            return;
        }
        
        List<Integer> enabled = config.enabledThreadIds();
        if (enabled.isEmpty()) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes)));
            return;
        }
        
        for (int threadId : enabled) {
            ModelThread thread = program.threads().get(threadId);
            Step step = thread.nextStep();
            if (step == null || sleepSet.contains(step)) {
                continue;
            }
            
            SharedState nextState = config.state().deepCopy();
            StepOutcome outcome = step.execute(nextState);
            
            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);
            
            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);
            
            Configuration nextConfig = config.successor(threadId, outcome, program.threads());
            
            HappensBefore nextHb = hb.copy();
            for (int otherId : enabled) {
                if (otherId != threadId) {
                    nextHb.record(threadId, otherId);
                }
            }
            
            SleepSet nextSleepSet = sleepSet.copy();
            nextSleepSet.clear();
            
            dporDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                    visitedStates, traces, invariant, statesExplored,
                    nextHb, nextSleepSet);
        }
    }
}
