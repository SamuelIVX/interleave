package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.*;

public final class DfsExplorer {
    private final HashingStateStore defaultStateStore;
    private final Map<String, Configuration> visitedStates;
    private final List<Trace> traces;
    private long statesExplored;
    private StateStore stateStore;
    private StateVisitor stateVisitor;

    public DfsExplorer() {
        this.defaultStateStore = new HashingStateStore();
        this.visitedStates = new LinkedHashMap<>();
        this.traces = new ArrayList<>();
        this.statesExplored = 0;
    }

    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    public DfsResult explore(Program program, Invariant invariant) {
        return explore(program, invariant, null, null);
    }

    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {
        this.stateStore = stateStore != null ? stateStore : defaultStateStore;
        this.stateVisitor = stateVisitor;
        this.stateStore.clear();
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

        if (stateStore.isVisited(config)) {
            return;
        }

        stateStore.markVisited(config);
        visitedStates.put(key, config);
        statesExplored++;

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
            Trace trace = Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes), TraceOutcome.DEADLOCK);
            traces.add(trace);
            if (stateVisitor != null) {
                stateVisitor.onTraceCreated(trace);
            }
        }
    }
}
