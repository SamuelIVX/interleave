package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.*;

/**
 * Exhaustive depth-first search oracle for concurrent programs.
 *
 * <p>Explores every reachable <em>configuration</em> up to an optional state budget, with no partial-order
 * reduction. Note that configurations and executions are different things: a configuration reached by
 * several schedules is expanded once, and the second arrival returns at the {@code isVisited} check
 * before its own suffix is explored. So coverage is complete over the configuration space subject to the
 * store and budget — the property that justifies using this as the oracle for the reduced
 * {@link dev.samhb.interleave.por.StaticPorExplorer} and
 * {@link dev.samhb.interleave.dpor.DporExplorer} results — while the trace list is <em>not</em> a
 * per-execution record. Measured on the corpus, {@code peterson} visits 42 configurations and reports 2
 * traces. The trace list is also not simply one entry per terminal configuration: a step returning
 * {@link dev.samhb.interleave.core.StepOutcome#ASSERTION_FAILED} records a violation trace and abandons
 * that edge without ever creating a successor, so traces can exist for edges that reach no terminal
 * configuration. Use the trace list to find a counterexample, not to count executions. Cost is
 * exponential in the number of threads, which is why the budget exists.
 *
 * <p><b>An invariant limits configuration coverage here too.</b> A configuration that violates the
 * invariant is recorded and abandoned without exploring its successors, so no configuration reachable only
 * through a violating one is visited. Measured against a null-invariant run, that is 9 unvisited
 * configurations on {@code broken-peterson} and {@code broken-peterson-v2}, 9 on
 * {@code double-checked-locking} — where the totals match at 17 and only the membership differs — and 1 on
 * {@code torn-counter}. Violation <em>detection</em> stays exhaustive: any violation on an untruncated path
 * is found, and a truncation only happens after one has already been reported. Complete configuration
 * coverage therefore requires passing a null invariant.
 *
 * <p><b>Visited states are keyed through an encoder.</b> The default store is a
 * {@link dev.samhb.interleave.state.HashingStateStore}, so a lossy encoding does not fail loudly — it
 * prunes configurations it has not seen, and a reachable violation can disappear silently. That makes
 * this class the wrong instrument for measuring an encoder, and the reason
 * {@code CanonicalEncoderContractTest} supplies its own value-keyed store instead of using this default:
 * a sample filtered by the encoder under test cannot measure it. See Spec 12.01 §R7.
 *
 * <p>Instance state is reused across calls, so an explorer is not safe for concurrent use.
 */
public final class DfsExplorer {
    private final HashingStateStore defaultStateStore;
    private final Map<String, Configuration> visitedStates;
    private final List<Trace> traces;
    private long statesExplored;
    private StateStore stateStore;
    private StateVisitor stateVisitor;
    private long maxStatesBudget;

    /** Creates an explorer with an empty visited set and no budget limit. */
    public DfsExplorer() {
        this.defaultStateStore = new HashingStateStore();
        this.visitedStates = new LinkedHashMap<>();
        this.traces = new ArrayList<>();
        this.statesExplored = 0;
        this.maxStatesBudget = Long.MAX_VALUE;
    }

    /**
     * Explores all reachable configurations without invariant.
     *
     * @param program program to explore
     * @return result containing visited states and traces
     */
    public DfsResult explore(Program program) {
        return explore(program, null);
    }

    /**
     * Explores with invariant.
     *
     * @param program program to explore
     * @param invariant invariant to check, or null
     * @return result
     */
    public DfsResult explore(Program program, Invariant invariant) {
        return explore(program, invariant, null, null);
    }

    /**
     * Budget-aware exploration: stops early when budget exhausted.
     *
     * @param program program to explore
     * @param maxStates state budget (truncation threshold)
     * @return result (statesExplored capped at budget)
     */
    public DfsResult explore(Program program, long maxStates) {
        return explore(program, null, null, null, maxStates);
    }

    /**
     * Full exploration with all options.
     *
     * @param program program
     * @param invariant invariant or null
     * @param stateStore visited store or null
     * @param stateVisitor visitor or null
     * @return result
     */
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {
        return explore(program, invariant, stateStore, stateVisitor, Long.MAX_VALUE);
    }

    /**
     * Full exploration with budget.
     *
     * @param program program
     * @param invariant invariant or null
     * @param stateStore visited store or null
     * @param stateVisitor visitor or null
     * @param maxStates budget; Long.MAX_VALUE for no limit
     * @return result
     */
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor, long maxStates) {
        this.stateStore = stateStore != null ? stateStore : defaultStateStore;
        this.stateVisitor = stateVisitor;
        this.maxStatesBudget = maxStates;
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
        if (statesExplored >= maxStatesBudget) return;
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
            if (statesExplored >= maxStatesBudget) return;
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
                Trace trace = Trace.of(List.copyOf(nextThreadIds), List.copyOf(nextOutcomes), TraceOutcome.VIOLATION);
                traces.add(trace);
                if (stateVisitor != null) {
                    stateVisitor.onTraceCreated(trace);
                }
                continue;
            }

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
