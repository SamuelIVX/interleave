package dev.samhb.modelcheck.search;

import dev.samhb.modelcheck.core.*;
import java.util.*;

public final class DfsExplorer {
    private final StateStore stateStore;
    private final Map<String, Configuration> visitedStates;
    private final List<Trace> traces;
    private long statesExplored;

    public DfsExplorer() {
        this.stateStore = new HashingStateStore();
        this.visitedStates = new LinkedHashMap<>();
        this.traces = new ArrayList<>();
        this.statesExplored = 0;
    }

    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    public DfsResult explore(Program program, Invariant invariant) {
        stateStore.clear();
        visitedStates.clear();
        traces.clear();
        statesExplored = 0;

        Configuration initial = program.initialConfiguration();
        dfs(program, initial, new ArrayList<>(), new ArrayList<>(), invariant);

        return new DfsResult(visitedStates, traces, statesExplored);
    }

    private void dfs(Program program, Configuration config, 
                     List<Integer> currentThreadIds, 
                     List<StepOutcome> currentOutcomes,
                     Invariant invariant) {
        String key = canonicalKey(config);
        
        if (stateStore.isVisited(config.state())) {
            // Also check if we've seen this exact configuration before
            if (visitedStates.containsKey(key)) {
                return;
            }
        }
        
        stateStore.markVisited(config.state());
        visitedStates.put(key, config);
        statesExplored++;
        
        if (invariant != null && !invariant.holds(config.state(), config)) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes)));
            return;
        }
        
        if (config.allTerminated()) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes)));
            return;
        }
        
        for (int threadId : config.enabledThreadIds()) {
            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);
            
            SharedState nextState = config.state().deepCopy();
            StepOutcome outcome = step.execute(nextState);
            
            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);
            
            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);
            
            Configuration nextConfig = config.successor(threadId, outcome, program.threads(), nextState);
            
            dfs(program, nextConfig, nextThreadIds, nextOutcomes, invariant);
        }
        
        if (config.enabledThreadIds().isEmpty() && !config.allTerminated()) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes)));
        }
    }
    
    private String canonicalKey(Configuration config) {
        return config.state().toString() + "|" + config.programCounters();
    }
}
