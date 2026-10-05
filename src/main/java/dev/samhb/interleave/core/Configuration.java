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

        return new Configuration(state.deepCopy(), pcs, lockOwnership, waitQueues, enabled, allTerm, deadlock, null);
    }

    /**
     * Test-visible factory: builds a configuration with threads at arbitrary program counters.
     *
     * <p>Derives {@code allTerminated} and {@code deadlockCandidate} rather than accepting them. The
     * private constructor takes all three independently, so a caller that supplies them can assemble
     * a configuration whose own parts disagree. Callers that wanted an arbitrary position had no
     * other route and had no choice about the booleans — they passed {@code false, false}, which made
     * every fixture they built structurally incapable of being all-terminated or a deadlock
     * candidate.
     *
     * <p>{@code enabledThreadIds} is still supplied. Deciding whether a counter denotes an enabled
     * position means evaluating the DSL step sitting at that counter, which needs the
     * {@link ModelThread}, and a caller placing threads at deliberately implausible positions has no
     * such list. Deriving {@code deadlockCandidate} from it still removes half the freedom to
     * disagree.
     *
     * <p>Every counter is treated as a live, non-terminal position here, so {@code allTerminated} is
     * false. Passing an empty {@code enabledThreadIds} therefore yields a deadlock candidate. Use
     * {@link #forTest(SharedState, List, List, List)} to place threads at or past their end.
     *
     * <p>Lock ownership and wait queues are empty. This is a fixture seam, not a builder.
     */
    public static Configuration forTest(SharedState state, List<Integer> programCounters,
                                        List<Integer> enabledThreadIds) {
        List<Integer> liveSteps = programCounters.stream().map(pc -> pc + 1).toList();
        return forTest(state, programCounters, liveSteps, enabledThreadIds);
    }

    /**
     * As {@link #forTest(SharedState, List, List)}, but with the per-thread step counts supplied so
     * {@code allTerminated} is derived from the counters instead of assumed false. A counter at or
     * beyond its thread's step count is a terminated position.
     *
     * @throws IllegalArgumentException if there is not exactly one step count per counter
     */
    public static Configuration forTest(SharedState state, List<Integer> programCounters,
                                        List<Integer> stepsPerThread, List<Integer> enabledThreadIds) {
        if (programCounters.size() != stepsPerThread.size()) {
            throw new IllegalArgumentException(
                    "expected one step count per thread, got " + programCounters.size()
                            + " counters and " + stepsPerThread.size() + " step counts");
        }

        boolean allTerm = true;
        for (int i = 0; i < programCounters.size(); i++) {
            if (programCounters.get(i) < stepsPerThread.get(i)) {
                allTerm = false;
                break;
            }
        }

        boolean deadlock = !allTerm && enabledThreadIds.isEmpty();

        return new Configuration(state, programCounters, Map.of(), Map.of(), enabledThreadIds,
                allTerm, deadlock, null);
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
