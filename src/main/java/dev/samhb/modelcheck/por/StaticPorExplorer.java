package dev.samhb.modelcheck.por;

import dev.samhb.modelcheck.core.*;
import dev.samhb.modelcheck.search.*;
import java.util.*;

public final class StaticPorExplorer {
    private final IndependenceRelation relation;
    private final PersistentSetComputer persistentSetComputer;

    public StaticPorExplorer() {
        this.relation = new IndependenceRelation();
        this.persistentSetComputer = new PersistentSetComputer(relation);
    }

    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    public DfsResult explore(Program program, Invariant invariant) {
        Map<String, Configuration> visitedStates = new LinkedHashMap<>();
        List<Trace> traces = new ArrayList<>();
        long[] statesExplored = new long[1];

        Configuration initial = program.initialConfiguration();
        porDfs(program, initial, new ArrayList<>(), new ArrayList<>(), 
               visitedStates, traces, invariant, statesExplored);

        return new DfsResult(visitedStates, traces, statesExplored[0]);
    }

    private void porDfs(Program program, Configuration config,
                        List<Integer> currentThreadIds,
                        List<StepOutcome> currentOutcomes,
                        Map<String, Configuration> visitedStates,
                        List<Trace> traces,
                        Invariant invariant,
                        long[] statesExplored) {
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
        
        List<Integer> persistentSet = persistentSetComputer.computePersistentSet(
            config, program.threads()
        );
        
        for (int threadId : persistentSet) {
            ModelThread thread = program.threads().get(threadId);
            Step step = thread.nextStep();
            if (step == null) {
                continue;
            }
            
            SharedState nextState = config.state().deepCopy();
            StepOutcome outcome = step.execute(nextState);
            
            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);
            
            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);
            
            Configuration nextConfig = config.successor(threadId, outcome, program.threads());
            
            porDfs(program, nextConfig, nextThreadIds, nextOutcomes,
                   visitedStates, traces, invariant, statesExplored);
        }
        
        if (config.enabledThreadIds().isEmpty() && !config.allTerminated()) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes)));
        }
    }
}
