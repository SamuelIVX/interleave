package dev.samhb.interleave.corpus;

import dev.samhb.interleave.core.Program;

/**
 * Result of exploring a generated program with the exact oracle.
 *
 * @param program generated program
 * @param expectedVerdict SAFE, VIOLATION, or TRUNCATED
 * @param statesExplored number of states explored
 * @param truncated whether maxStates budget was hit
 */
public record CorpusResult(Program program, String expectedVerdict, long statesExplored, boolean truncated) {
    /**
     * @return generated program
     */
    @Override public Program program() { return program; }
    /**
     * @return verdict
     */
    @Override public String expectedVerdict() { return expectedVerdict; }
    /**
     * @return states explored
     */
    @Override public long statesExplored() { return statesExplored; }
    /**
     * @return true if truncated
     */
    @Override public boolean truncated() { return truncated; }
}
