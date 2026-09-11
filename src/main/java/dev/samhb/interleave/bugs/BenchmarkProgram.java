package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.Invariant;
import java.util.Optional;

public final class BenchmarkProgram {
    private final String name;
    private final Program program;
    private final String expectedVerdict;
    private final Invariant invariant;

    public BenchmarkProgram(String name, Program program, String expectedVerdict) {
        this(name, program, expectedVerdict, null);
    }

    public BenchmarkProgram(String name, Program program, String expectedVerdict, Invariant invariant) {
        this.name = name;
        this.program = program;
        this.expectedVerdict = expectedVerdict;
        this.invariant = invariant;
    }

    public String name() {
        return name;
    }

    public Program program() {
        return program;
    }

    public String expectedVerdict() {
        return expectedVerdict;
    }

    public Optional<Invariant> invariant() {
        return Optional.ofNullable(invariant);
    }
}
