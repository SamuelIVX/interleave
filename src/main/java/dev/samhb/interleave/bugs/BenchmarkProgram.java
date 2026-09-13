package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.Invariant;
import java.util.Optional;

/**
 * A benchmark program with its expected verdict and invariant.
 * <p>
 * The {@code expectedVerdict} may be {@code null} for programs loaded from
 * declarative JSON format where no expected verdict was specified. In this
 * case, verdict validation is skipped during benchmarking.
 */
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

    /**
     * Creates a benchmark program with no expected verdict and no invariant.
     * Useful for programs loaded from declarative JSON format where no expected
     * verdict was specified.
     */
    public BenchmarkProgram(String name, Program program) {
        this(name, program, null, null);
    }

    public String name() {
        return name;
    }

    public Program program() {
        return program;
    }

    /**
     * Returns the expected verdict, or {@code null} if no verdict was specified
     * (e.g., for programs loaded from declarative JSON format without an
     * {@code expected_verdict} field). In this case, verdict validation is skipped.
     */
    public String expectedVerdict() {
        return expectedVerdict;
    }

    public Optional<Invariant> invariant() {
        return Optional.ofNullable(invariant);
    }
}
