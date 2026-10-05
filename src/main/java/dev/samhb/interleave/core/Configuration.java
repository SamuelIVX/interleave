package dev.samhb.interleave.core;

import java.util.*;

/**
 * One point in an execution: the shared state, each thread's program counter, and what that implies
 * about what may run next.
 *
 * <h2>Derived facts are derived, not supplied</h2>
 *
 * <p>{@link #enabledThreadIds()}, {@link #allTerminated()} and {@link #isDeadlockCandidate()} are
 * computed from the program counters by {@link #derive} and cannot be set independently. Callers that
 * recompute them from components risk disagreeing with the configuration they are holding — see
 * {@code docs/specs/active/13-deferred-debt/03-canonical-configuration-key.md}, which records two
 * derivations that had already drifted apart.
 *
 * <p>A finished exploration is not a deadlock: {@code isDeadlockCandidate()} is false once every thread
 * has terminated, even with nothing enabled.
 *
 * <h2>On keying a configuration</h2>
 *
 * <p>Several search strategies build an unencoded bookkeeping key by string-concatenating
 * {@code state.toString()} with the program counters, which is a debug aid rather than an identity —
 * see the note on {@link dev.samhb.interleave.format.dsl.DynamicState#toString()}, which documents the
 * same hazard for the same reason. Those renderings may change freely, so a key built from them is
 * only as sound as the agreement between a type's {@code equals}, {@code hashCode} and
 * {@code toString()}.
 *
 * <p>{@link MemoryLocation} is the exemplar of that discipline: all three delegate to the same
 * {@code name}, so its string comparison is provably sound.
 *
 * <p>The concatenating sites are {@code DfsExplorer}, {@code StaticPorExplorer} and two in
 * {@code DporExplorer}, which key on state and counters alone. {@code ContextBoundedExplorer} keys on
 * state, counters <em>and</em> the last scheduled thread id, because under a preemption bound a
 * configuration reached by a different last switch is genuinely a different search state. That
 * difference is deliberate. Widening the four to match it would multiply the state space; narrowing the
 * CB explorer to match them would merge states the search treats as distinct and change what
 * {@code statesExplored} means.
 */
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
        Derived derived = derive(threads, pcs, state);

        return new Configuration(state.deepCopy(), pcs, new LinkedHashMap<>(), new LinkedHashMap<>(),
                derived.enabledThreadIds(), derived.allTerminated(),
                isDeadlockCandidate(derived), null);
    }

    /**
     * Derives the enabled-thread set and the terminated flag from a set of program counters.
     *
     * <p>{@link #initial} and {@link #successor} both need these two facts about the same three
     * inputs, and a thread is enabled exactly when its counter is inside its step list and the step
     * there permits it. Deriving them once is what stops the two factories from disagreeing with each
     * other, and with the counters they are handed, about what is runnable.
     */
    private static Derived derive(List<ModelThread> threads, List<Integer> counters, SharedState state) {
        List<Integer> enabled = new ArrayList<>();
        boolean allTerminated = true;

        for (int i = 0; i < threads.size(); i++) {
            int pc = counters.get(i);
            ModelThread thread = threads.get(i);
            if (pc < thread.steps().size()) {
                allTerminated = false;
                if (thread.steps().get(pc).enabled(state)) {
                    enabled.add(i);
                }
            }
        }

        return new Derived(enabled, allTerminated);
    }

    /**
     * A finished exploration is not a deadlock. With every thread terminated and nothing enabled there
     * is nothing left to run, which is a normal end state; conflating the two makes a search stop early
     * or report a phantom.
     */
    private static boolean isDeadlockCandidate(Derived derived) {
        return !derived.allTerminated() && derived.enabledThreadIds().isEmpty();
    }

    private record Derived(List<Integer> enabledThreadIds, boolean allTerminated) {}

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

        Derived derived = derive(threads, nextPcs, nextState);

        return new Configuration(
                nextState,
                nextPcs,
                this.lockOwnership,
                this.waitQueues,
                derived.enabledThreadIds(),
                derived.allTerminated(),
                isDeadlockCandidate(derived),
                outcome
        );
    }
}
