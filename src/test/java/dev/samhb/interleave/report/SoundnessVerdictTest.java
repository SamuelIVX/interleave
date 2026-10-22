/** Literal verdict-policy oracles for registered benchmark expectations and strategy agreement. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.format.SourceProgramLoader;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SoundnessVerdictTest {
    /** Exact DFS mismatches fail unless the source result is explicitly inconclusive.
     * @param source whether the registered program carries source identity
     * @param expected supplied expectation, or null when checking is disabled
     * @param actual supplied exact DFS verdict
     * @param sound literal expected attestation result
     */
    @ParameterizedTest
    @CsvSource(nullValues = "absent", value = {
        "false, PASS, PASS, true", "false, PASS, DEADLOCK, false",
        "false, DEADLOCK, PASS, false", "false, VIOLATION, PASS, false",
        "false, PASS, INCOMPLETE, false", "false, PASS, APPROXIMATE_PASS, false",
        "true, PASS, INCOMPLETE, true", "true, PASS, APPROXIMATE_PASS, true",
        "true, PASS, DEADLOCK, false", "true, PASS, PASS, true",
        "true, absent, DEADLOCK, true", "false, absent, DEADLOCK, true",
        "false, absent, INCOMPLETE, true"})
    void validatesExactDfsExpectations(boolean source, String expected, String actual, boolean sound) {
        var attestation = new SoundnessAttestation(List.of(row("DFS", actual, StoreType.EXACT)),
            List.of(program(source, expected)));
        assertEquals(sound, attestation.isSound());
        if (sound) assertNull(attestation.failureReason());
        else assertEquals("DFS verdict mismatch for subject: expected " + expected + " but got " + actual,
            attestation.failureReason());
    }

    /** Agreement applies to known non-VIOLATION expectations, without treating unknown/buggy models as correct.
     * @param source whether the registered program carries source identity
     * @param expected registered expectation, or null when absent
     * @param first first supplied exact non-DFS verdict
     * @param second second supplied exact non-DFS verdict
     * @param sound literal expected agreement result
     */
    @ParameterizedTest
    @CsvSource(nullValues = "absent", value = {"false, PASS, PASS, DEADLOCK, false",
        "false, PASS, DEADLOCK, PASS, false", "false, DEADLOCK, DEADLOCK, PASS, false",
        "false, PASS, PASS, PASS, true", "false, VIOLATION, PASS, DEADLOCK, true",
        "false, absent, PASS, DEADLOCK, true", "true, PASS, PASS, DEADLOCK, false"})
    void checksAgreementOnlyForKnownNonViolationExpectations(boolean source, String expected, String first,
            String second, boolean sound) {
        var attestation = new SoundnessAttestation(List.of(row("STATIC_POR", first, StoreType.EXACT),
            row("DPOR", second, StoreType.EXACT)), List.of(program(source, expected)));
        assertEquals(sound, attestation.isSound());
        if (sound) assertNull(attestation.failureReason());
        else assertEquals("Verdict mismatch for correct program subject under DPOR: " + first + " vs " + second,
            attestation.failureReason());
    }

    /** An inconclusive exact row can neither seed nor conflict with the conclusive agreement.
     * @param verdict supported inconclusive label
     * @param before whether it precedes the conclusive DFS row
     */
    @ParameterizedTest
    @CsvSource({"INCOMPLETE, true", "INCOMPLETE, false",
        "APPROXIMATE_PASS, true", "APPROXIMATE_PASS, false"})
    void inconclusiveRowsDoNotSeedOrConflict(String verdict, boolean before) {
        var inconclusive = row("CONTEXT_BOUNDED", verdict, StoreType.EXACT);
        var exact = row("DFS", "PASS", StoreType.EXACT);
        var attestation = new SoundnessAttestation(before ? List.of(inconclusive, exact) : List.of(exact, inconclusive),
            List.of(program(false, "PASS")));
        assertTrue(attestation.isSound(), attestation.failureReason());
        assertNull(attestation.failureReason());
    }

    /** Approximate storage cannot supply an absence-of-bug check against the registered expectation.
     * @param expected registered conclusive expectation
     * @param actual deliberately different bitstate DFS verdict
     */
    @ParameterizedTest
    @CsvSource({"PASS, DEADLOCK", "DEADLOCK, PASS"})
    void bitstateDfsIsExcludedFromExpectationMatching(String expected, String actual) {
        var attestation = new SoundnessAttestation(List.of(row("DFS", actual, StoreType.BITSTATE)),
            List.of(program(false, expected)));
        assertTrue(attestation.isSound(), attestation.failureReason());
        assertNull(attestation.failureReason());
    }

    /** Bitstate verdicts remain outside exact agreement regardless of encounter order.
     * @param before whether the disagreeing bitstate row appears first
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bitstateRowsDoNotSeedOrConflict(boolean before) {
        var bitstate = row("DPOR", "DEADLOCK", StoreType.BITSTATE);
        var exact = row("DFS", "PASS", StoreType.EXACT);
        var attestation = new SoundnessAttestation(before ? List.of(bitstate, exact) : List.of(exact, bitstate),
            List.of(program(false, "PASS")));
        assertTrue(attestation.isSound(), attestation.failureReason());
        assertNull(attestation.failureReason());
    }

    /** Creates a controlled registered program; expectations are supplied test metadata, not search oracles.
     * @param source whether source-specific inconclusive handling applies
     * @param expected optional declared expectation
     * @return registered subject with no invariant needed for these non-VIOLATION rows
     */
    private static BenchmarkProgram program(boolean source, String expected) {
        Program definition = source
            ? new SourceProgramLoader().load("program subject { shared int counter = 0; thread 0 {} }").program()
            : new Program(new CounterState(0), List.of(new ModelThread(0, List.of())));
        return new BenchmarkProgram("subject", definition, expected);
    }

    /** Supplies one row to the attester without conflating verdict-policy tests with explorer correctness.
     * @param strategy reporting strategy label
     * @param verdict supplied non-VIOLATION row label
     * @param store storage mode used by the verdict filter
     * @return benchmark row for the registered subject
     */
    private static BenchmarkResult row(String strategy, String verdict, StoreType store) {
        return new BenchmarkResult(strategy, "subject", 1, 0, 0, verdict, store);
    }
}
