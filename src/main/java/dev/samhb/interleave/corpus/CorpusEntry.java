package dev.samhb.interleave.corpus;

import dev.samhb.interleave.bugs.ReadCounterStep;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.TraceOutcome;
import java.util.List;
import java.util.ArrayList;

/**
 * Persisted corpus entry combining generation metadata, a losslessly
 * serializable program representation, and the oracle verdict.
 * <p>
 * JSON round-trips via Gson; {@link #toProgram()} reconstructs the program
 * for replay without re-running the generator. This fixes data-integrity
 * gap where only counts were stored.
 */
public final class CorpusEntry {
    /** Template that generated this entry. */
    public String templateId;
    /** RNG seed used for generation (reproducibility). */
    public long seed;
    /** Index within the generation batch. */
    public int index;
    /** Number of threads in the program. */
    public int threadCount;
    /** Total step count across all threads. */
    public int totalSteps;
    /** Oracle verdict: SAFE, VIOLATION, or TRUNCATED. */
    public String expectedVerdict;
    /** Number of states explored by oracle. */
    public long statesExplored;
    /** Whether exploration hit maxStates budget. */
    public boolean truncated;
    /** Initial counter value for lossless replay. */
    public int initialCounter;
    /**
     * Per-thread step types as simple names (e.g., "ReadCounterStep").
     * Losslessly captures ordering; used by {@link #toProgram()}.
     */
    public List<List<String>> stepsByThread;

    /** No-arg constructor for Gson. */
    public CorpusEntry() {}

    /**
     * Creates an entry from a generator result, capturing a lossless program representation.
     *
     * @param templateId template id
     * @param seed RNG seed
     * @param index index in batch
     * @param result oracle result
     */
    public CorpusEntry(String templateId, long seed, int index, CorpusResult result) {
        this.templateId = templateId;
        this.seed = seed;
        this.index = index;
        Program p = result.program();
        this.threadCount = p.threadCount();
        this.totalSteps = p.threads().stream().mapToInt(t -> t.steps().size()).sum();
        this.expectedVerdict = result.expectedVerdict();
        this.statesExplored = result.statesExplored();
        this.truncated = result.truncated();
        // capture lossless representation
        Configuration init = p.initialConfiguration();
        CounterState cs = (CounterState) init.state();
        this.initialCounter = cs.counter();
        this.stepsByThread = new ArrayList<>();
        for (ModelThread mt : p.threads()) {
            List<String> names = new ArrayList<>();
            for (Step s : mt.steps()) names.add(s.getClass().getSimpleName());
            this.stepsByThread.add(List.copyOf(names));
        }
    }

    /**
     * Reconstructs the program from the persisted representation.
     *
     * @return program identical in initial state and step ordering
     */
    public Program toProgram() {
        CounterState initial = CounterState.of(initialCounter, threadCount);
        List<ModelThread> threads = new ArrayList<>();
        for (int tid = 0; tid < stepsByThread.size(); tid++) {
            List<String> names = stepsByThread.get(tid);
            List<Step> steps = new ArrayList<>();
            for (String n : names) {
                if ("ReadCounterStep".equals(n)) steps.add(new ReadCounterStep(tid));
                else if ("WriteCounterStep".equals(n)) steps.add(new WriteCounterStep(tid));
                else throw new IllegalStateException("Unknown step: " + n);
            }
            threads.add(new ModelThread(tid, steps));
        }
        return new Program(initial, threads);
    }

    /**
     * Computes verdict using budget-aware exploration.
     *
     * @param program program to check
     * @param maxStates state budget for truncation
     * @return TRUNCATED, VIOLATION, or SAFE
     */
    public static String verdictFor(Program program, long maxStates) {
        DfsExplorer explorer = new DfsExplorer();
        DfsResult res = explorer.explore(program, maxStates);
        boolean truncated = res.statesExplored() >= maxStates;
        if (truncated) return "TRUNCATED";
        boolean violation = res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION || t.outcome() == TraceOutcome.DEADLOCK);
        return violation ? "VIOLATION" : "SAFE";
    }
}
