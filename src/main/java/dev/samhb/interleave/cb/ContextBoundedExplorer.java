/** Context-bounded search (CHESS-style) for concurrent programs. */
package dev.samhb.interleave.cb;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.state.HashingStateStore;
import dev.samhb.interleave.state.CanonicalEncoder;

import java.util.*;

/**
 * Context-bounded search (CHESS-style) for concurrent programs.
 *
 * <p>Explores interleavings up to a bound {@code K} on <em>preemptive</em> context switches, where
 * a preemption is a scheduler switch away from a thread that could otherwise have continued.
 * Switching away from a blocked or terminated thread is forced, and so costs nothing.
 *
 * <p>The search is cost-aware: a state is remembered together with the least preemption budget at
 * which it was reached, so a deeper bound is never pruned by a shallower one (see
 * {@link StateStore#isVisited(Configuration, int, int)}). Exhausting the budget yields
 * {@link TraceOutcome#INCOMPLETE} rather than a pass, because the search proved nothing about the
 * pruned region.
 */
public final class ContextBoundedExplorer {
    /** Creates context bounded explorer with its default configuration. */
    public ContextBoundedExplorer() {}


    /** The preemption bound used when a caller does not choose one. */
    public static final int DEFAULT_MAX_PREEMPTIONS = 2;

    /** Visited states. */
    private final Map<String, Configuration> visitedStates = new LinkedHashMap<>();
    /** Encoder. */
    private final CanonicalEncoder encoder = new CanonicalEncoder();
    /** Traces. */
    private final List<Trace> traces = new ArrayList<>();
    /** States explored. */
    private long statesExplored;

    // Budget-exceeded state, reset at the start of every top-level search.
    /** Budget exceeded. */
    private boolean budgetExceeded;
    /** Incomplete thread ids. */
    private List<Integer> incompleteThreadIds;
    /** Incomplete outcomes. */
    private List<StepOutcome> incompleteOutcomes;

    /**
     * Explores with the default preemption bound.
     *
     * @param program the program to explore
     * @return visited configurations, recorded traces, and exploration count
     */
    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    /**
     * Explores with the default preemption bound.
     * @param program modeled program whose threads are explored
     * @param invariant property to check, or null when no property is supplied
     * @return visited configurations, recorded traces, and exploration count
     */
    public DfsResult explore(Program program, Invariant invariant) {
        return explore(program, invariant, null, null, DEFAULT_MAX_PREEMPTIONS);
    }

    /**
     * Explores with the default preemption bound and an explicit store.
     * @param program modeled program whose threads are explored
     * @param invariant property to check, or null when no property is supplied
     * @param stateStore visited-position store used for pruning
     * @param stateVisitor optional observer of visited states and emitted traces
     * @return visited configurations, recorded traces, and exploration count
     */
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {
        return explore(program, invariant, stateStore, stateVisitor, DEFAULT_MAX_PREEMPTIONS);
    }

    /**
     * Explores up to {@code maxPreemptions} preemptive context switches.
     *
     * @param program the program to explore
     * @param invariant the invariant to check, or null
     * @param stateStore the visited store, or null for a fresh exact store
     * @param stateVisitor the visitor, or null
     * @param maxPreemptions the preemption bound; must not be negative
     * @return visited configurations, recorded traces, and exploration count
     * @throws IllegalArgumentException if {@code maxPreemptions} is negative, or the supplied
     *         store cannot represent it
     */
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore,
                             StateVisitor stateVisitor, int maxPreemptions) {
        if (maxPreemptions < 0) {
            throw new IllegalArgumentException("maxPreemptions must not be negative");
        }

        StateStore store = stateStore != null ? stateStore : new HashingStateStore();
        // A bitstate store sized for a lower bound cannot represent this search. Clamping would
        // return a verdict that looks exhaustive at K but is exhaustive only at the store's
        // capacity, which is exactly the silent unsoundness this class exists to avoid.
        if (store instanceof BitstateStore bitstate && bitstate.maxPreemptions() < maxPreemptions) {
            throw new IllegalArgumentException(
                "State store capacity " + bitstate.maxPreemptions()
                + " is below requested maxPreemptions " + maxPreemptions
                + "; construct the store as new BitstateStore(size, k, maxPreemptions)");
        }

        store.clear();
        visitedStates.clear();
        traces.clear();
        statesExplored = 0;
        budgetExceeded = false;
        incompleteThreadIds = null;
        incompleteOutcomes = null;

        dfs(program, program.initialConfiguration(), new ArrayList<>(), new ArrayList<>(),
            -1, 0, maxPreemptions, invariant, store, stateVisitor);

        emitIncompleteTraceIfNeeded(stateVisitor);

        return new DfsResult(visitedStates, traces, statesExplored);
    }

    /**
     * Explores successors recursively, restoring the mutable execution prefix on return.
     * @param program modeled program whose threads are explored
     * @param config current search configuration
     * @param currentThreadIds mutable thread-choice prefix, restored after recursive exploration
     * @param currentOutcomes mutable step-outcome prefix, restored after recursive exploration
     * @param lastThreadId previously scheduled thread, or the initial sentinel
     * @param currentPreemptions preemption cost accumulated along this path
     * @param maxPreemptions nonnegative preemption bound
     * @param invariant property to check, or null when no property is supplied
     * @param stateStore visited-position store used for pruning
     * @param stateVisitor optional observer of visited states and emitted traces
     */
    private void dfs(Program program, Configuration config,
                     List<Integer> currentThreadIds,
                     List<StepOutcome> currentOutcomes,
                     int lastThreadId, int currentPreemptions, int maxPreemptions,
                     Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {

        if (stateStore.isVisited(config, lastThreadId, currentPreemptions)) {
            return;
        }
        stateStore.markVisited(config, lastThreadId, currentPreemptions);
        visitedStates.put(encoder.configurationKey(config, lastThreadId), config);
        statesExplored++;

        if (stateVisitor != null) {
            stateVisitor.onStateVisited(config, lastThreadId, currentPreemptions);
        }

        if (invariant != null && !invariant.holds(config.state(), config)) {
            addTrace(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes),
                TraceOutcome.VIOLATION), stateVisitor);
            return;
        }

        if (config.allTerminated()) {
            addTrace(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes),
                TraceOutcome.COMPLETED), stateVisitor);
            return;
        }

        List<Integer> enabled = config.enabledThreadIds();
        // Whether the previously scheduled thread could have continued. If it could not, any
        // switch away from it was forced and therefore free.
        boolean lastStillEnabled = (lastThreadId != -1) && enabled.contains(lastThreadId);

        for (int threadId : enabled) {
            int nextPreemptions;
            if (lastThreadId == -1) {
                nextPreemptions = 0;          // initial thread selection
            } else if (threadId == lastThreadId) {
                nextPreemptions = currentPreemptions;  // continuation
            } else if (lastStillEnabled) {
                nextPreemptions = currentPreemptions + 1;  // paid preemption
            } else {
                nextPreemptions = currentPreemptions;      // forced switch
            }

            if (nextPreemptions > maxPreemptions) {
                // The single prune site. Snapshot the live path here: these lists are mutated in
                // place and popped as DFS unwinds, so reading them after the search returns would
                // yield an empty -- technically valid but useless -- schedule. First site wins,
                // which keeps the emitted trace a function of the search alone rather than of
                // iteration order over a hash-based collection.
                if (!budgetExceeded) {
                    budgetExceeded = true;
                    incompleteThreadIds = List.copyOf(currentThreadIds);
                    incompleteOutcomes = List.copyOf(currentOutcomes);
                }
                continue;
            }

            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);

            SharedState nextState = config.state().deepCopy();
            StepOutcome outcome = step.execute(nextState);

            List<Integer> nextThreadIds = new ArrayList<>(currentThreadIds);
            nextThreadIds.add(threadId);
            List<StepOutcome> nextOutcomes = new ArrayList<>(currentOutcomes);
            nextOutcomes.add(outcome);

            if (outcome == StepOutcome.ASSERTION_FAILED) {
                addTrace(Trace.of(List.copyOf(nextThreadIds), List.copyOf(nextOutcomes),
                    TraceOutcome.VIOLATION), stateVisitor);
                continue;
            }

            Configuration nextConfig = config.successor(threadId, outcome, program.threads(), nextState);

            dfs(program, nextConfig, nextThreadIds, nextOutcomes,
                threadId, nextPreemptions, maxPreemptions, invariant, stateStore, stateVisitor);
        }

        if (config.isDeadlockCandidate()) {
            addTrace(Trace.of(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes),
                TraceOutcome.DEADLOCK), stateVisitor);
        }
    }

    /**
     * Emits at most one INCOMPLETE trace per search, and only when the budget was actually the
     * reason the search stopped. A run that found a violation reports the violation: the pruned
     * region is irrelevant once there is something to reproduce.
     * @param stateVisitor optional observer of visited states and emitted traces
     */
    private void emitIncompleteTraceIfNeeded(StateVisitor stateVisitor) {
        if (!budgetExceeded || incompleteThreadIds == null) {
            return;
        }
        boolean foundFailure = traces.stream()
            .anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION
                || t.outcome() == TraceOutcome.DEADLOCK);
        if (foundFailure) {
            return;
        }
        addTrace(Trace.incomplete(incompleteThreadIds, incompleteOutcomes), stateVisitor);
    }

    /**
     * Records a terminal trace and notifies the optional visitor.
     * @param trace recorded execution to inspect or replay
     * @param stateVisitor optional observer of visited states and emitted traces
     */
    private void addTrace(Trace trace, StateVisitor stateVisitor) {
        traces.add(trace);
        if (stateVisitor != null) {
            stateVisitor.onTraceCreated(trace);
        }
    }
}
