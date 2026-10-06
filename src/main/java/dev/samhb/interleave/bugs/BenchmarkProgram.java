/** A benchmark program with its expected verdict and invariant. */
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
    /** Name. */
    private final String name;
    /** Program. */
    private final Program program;
    /** Expected verdict. */
    private final String expectedVerdict;
    /** Invariant. */
    private final Invariant invariant;

    /**
     * Creates benchmark program from the supplied values.
     * @param name stable diagnostic or modeled-location name
     * @param program modeled program whose threads are explored
     * @param expectedVerdict expected verdict, or null to skip expectation checks
     */
    public BenchmarkProgram(String name, Program program, String expectedVerdict) {
        this(name, program, expectedVerdict, null);
    }

    /**
     * Creates benchmark program from the supplied values.
     * @param name stable diagnostic or modeled-location name
     * @param program modeled program whose threads are explored
     * @param expectedVerdict expected verdict, or null to skip expectation checks
     * @param invariant property to check, or null when no property is supplied
     */
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
     * @param name diagnostic benchmark name
     * @param program modeled program whose threads are explored
     */
    public BenchmarkProgram(String name, Program program) {
        this(name, program, null, null);
    }

    /**
     * Returns name for this benchmark program.
     * @return benchmark’s diagnostic name
     */
    public String name() {
        return name;
    }

    /**
     * Returns this benchmark’s modeled threads and initial shared state.
     * @return modeled program represented by this benchmark
     */
    public Program program() {
        return program;
    }

    /**
     * Returns the expected verdict, or {@code null} if no verdict was specified
     * (e.g., for programs loaded from declarative JSON format without an
     * {@code expected_verdict} field). In this case, verdict validation is skipped.
     * @return expected verdict, or null when verdict validation is disabled
     */
    public String expectedVerdict() {
        return expectedVerdict;
    }

    /**
     * Returns invariant for this benchmark program.
     * @return optional property attached to this program
     */
    public Optional<Invariant> invariant() {
        return Optional.ofNullable(invariant);
    }
}
