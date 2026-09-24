package dev.samhb.interleave.corpus;

import dev.samhb.interleave.core.Program;

/**
 * Result of exploring a generated program with the exact oracle.
 */
public final class CorpusResult {
    private final Program program;
    private final String expectedVerdict; // SAFE, VIOLATION, TRUNCATED
    private final long statesExplored;
    private final boolean truncated;

    public CorpusResult(Program program, String expectedVerdict, long statesExplored, boolean truncated) {
        this.program = program;
        this.expectedVerdict = expectedVerdict;
        this.statesExplored = statesExplored;
        this.truncated = truncated;
    }

    public Program program() { return program; }
    public String expectedVerdict() { return expectedVerdict; }
    public long statesExplored() { return statesExplored; }
    public boolean truncated() { return truncated; }
}
