package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.Program;

public final class BenchmarkProgram {
    private final String name;
    private final Program program;
    private final String expectedVerdict;

    public BenchmarkProgram(String name, Program program, String expectedVerdict) {
        this.name = name;
        this.program = program;
        this.expectedVerdict = expectedVerdict;
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
}
