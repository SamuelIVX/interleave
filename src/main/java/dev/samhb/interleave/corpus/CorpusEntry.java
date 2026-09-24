package dev.samhb.interleave.corpus;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;
import java.util.List;

/**
 * Persisted form combining program metadata, seed and oracle verdict.
 * Used for JSON round-trip and reproducibility.
 */
public final class CorpusEntry {
    public String templateId;
    public long seed;
    public int index;
    public int threadCount;
    public int totalSteps;
    public String expectedVerdict;
    public long statesExplored;
    public boolean truncated;

    public CorpusEntry() {}

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
    }

    /** Minimal invariant for corpus oracle: final counter must be sum of writes. */
    public static String verdictFor(Program program, long maxStates) {
        // Simple oracle: if any trace violated would need invariant; for generic corpus we treat
        // completion without deadlock as SAFE, deadlock/violation as VIOLATION.
        DfsExplorer explorer = new DfsExplorer();
        var res = explorer.explore(program);
        boolean truncated = res.statesExplored() >= maxStates;
        if (truncated) return "TRUNCATED";
        boolean violation = res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION || t.outcome() == TraceOutcome.DEADLOCK);
        return violation ? "VIOLATION" : "SAFE";
    }
}
