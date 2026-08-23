package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.state.CanonicalEncoder;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.*;

public final class DfsExplorer {
    private final HashingStateStore stateStore;
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
        String key = config.state().toString() + "|" + config.programCounters();
        
        if (stateStore.isVisited(config.state())) {
            return;
        }
        
        stateStore.markVisited(config.state());
        visitedStates.put(key, config);
        statesExplored++;
        
        if (invariant != null && !invariant.holds(config.state(), config)) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.VIOLATION));
            return;
        }
        
        if (config.allTerminated()) {
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.COMPLETED));
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
            traces.add(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK));
        }
    }
}
