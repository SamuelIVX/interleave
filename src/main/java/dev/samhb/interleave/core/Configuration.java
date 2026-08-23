package dev.samhb.interleave.core;

import java.util.*;

public final class Configuration {
    private final SharedState state;
    private final List<Integer> programCounters;
    private final Map<MemoryLocation, Integer> lockOwnership;
    private final Map<MemoryLocation, List<Integer>> waitQueues;
    private final List<Integer> enabledThreadIds;
    private final boolean allTerminated;
    private final boolean deadlockCandidate;
    private final StepOutcome lastOutcome;

    private Configuration(
            SharedState state,
            List<Integer> programCounters,
            Map<MemoryLocation, Integer> lockOwnership,
            Map<MemoryLocation, List<Integer>> waitQueues,
            List<Integer> enabledThreadIds,
            boolean allTerminated,
            boolean deadlockCandidate,
            StepOutcome lastOutcome) {
        this.state = state;
        this.programCounters = List.copyOf(programCounters);
        this.lockOwnership = Map.copyOf(lockOwnership);
        this.waitQueues = waitQueues.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), List.copyOf(e.getValue())),
                        Map::putAll);
        this.enabledThreadIds = List.copyOf(enabledThreadIds);
        this.allTerminated = allTerminated;
        this.deadlockCandidate = deadlockCandidate;
        this.lastOutcome = lastOutcome;
    }

    public static Configuration initial(SharedState state, List<ModelThread> threads) {
        List<Integer> pcs = new ArrayList<>(Collections.nCopies(threads.size(), 0));
        Map<MemoryLocation, Integer> lockOwnership = new LinkedHashMap<>();
        Map<MemoryLocation, List<Integer>> waitQueues = new LinkedHashMap<>();
        List<Integer> enabled = new ArrayList<>();

        for (int i = 0; i < threads.size(); i++) {
            if (threads.get(i).enabled(state)) {
                enabled.add(i);
            }
        }

        boolean allTerm = threads.stream().allMatch(ModelThread::terminated);
        boolean deadlock = !allTerm && enabled.isEmpty();

        return new Configuration(state, pcs, lockOwnership, waitQueues, enabled, allTerm, deadlock, null);
    }

    public SharedState state() {
        return state;
    }

    public List<Integer> programCounters() {
        return programCounters;
    }

    public Map<MemoryLocation, Integer> lockOwnership() {
        return lockOwnership;
    }

    public Map<MemoryLocation, List<Integer>> waitQueues() {
        return waitQueues;
    }

    public List<Integer> enabledThreadIds() {
        return enabledThreadIds;
    }

    public boolean allTerminated() {
        return allTerminated;
    }

    public boolean isDeadlockCandidate() {
        return deadlockCandidate;
    }

    public StepOutcome lastOutcome() {
        return lastOutcome;
    }

    public Configuration successor(int threadId, StepOutcome outcome, List<ModelThread> threads, SharedState nextState) {
        List<Integer> nextPcs = new ArrayList<>(this.programCounters);
        if (outcome != StepOutcome.BLOCKED) {
            nextPcs.set(threadId, nextPcs.get(threadId) + 1);
        }

        List<Integer> nextEnabled = new ArrayList<>();
        for (int i = 0; i < threads.size(); i++) {
            ModelThread t = threads.get(i);
            int currentPc = (i == threadId && outcome != StepOutcome.BLOCKED) 
                ? nextPcs.get(i) 
                : this.programCounters.get(i);
            
            if (currentPc < t.steps().size()) {
                Step nextStep = t.steps().get(currentPc);
                if (nextStep.enabled(nextState)) {
                    nextEnabled.add(i);
                }
            }
        }

        boolean allTerm = true;
        for (int i = 0; i < threads.size(); i++) {
            if (nextPcs.get(i) < threads.get(i).steps().size()) {
                allTerm = false;
                break;
            }
        }

        boolean deadlock = !allTerm && nextEnabled.isEmpty();

        return new Configuration(
                nextState,
                nextPcs,
                this.lockOwnership,
                this.waitQueues,
                nextEnabled,
                allTerm,
                deadlock,
                outcome
        );
    }
}
