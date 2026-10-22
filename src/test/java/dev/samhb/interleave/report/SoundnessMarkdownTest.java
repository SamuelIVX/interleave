/** Complete Markdown contracts for scoped attestation claims, warnings and report integration. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SoundnessMarkdownTest {
    /** Literal public section contract: checks describe their scope without proving whole-checker soundness. */
    private static final String SUCCESS = "## Soundness Attestation\n\n"
        + "All checked EXACT DFS verdicts matched their supplied expectations. "
        + "Source DFS results that were INCOMPLETE or APPROXIMATE_PASS were accepted as "
        + "inconclusive, not matched verdicts; programs without expectations were not expectation-checked. "
        + "Reported VIOLATION traces (including bitstate and context-bounded) passed execution replay. "
        + "Supplied invariants failed at the replayed configuration, or source runtime failures were authenticated. "
        + "Without an invariant, successful replay does not establish a property violation. "
        + "Inconclusive results (INCOMPLETE or APPROXIMATE_PASS) are excluded "
        + "from the cross-strategy verdict agreement above, since they assert nothing about "
        + "the absence of a bug. "
        + "No soundness failures were detected by these checks.\n\n";

    /** Empty evidence must not produce an unconditional claim that the checker is sound. */
    @Test
    void emptyEvidenceUsesQualifiedSuccessSection() {
        assertSuccessReport(List.of(), List.of());
    }

    /** Source inconclusive policy inputs must retain the distinction between accepted and matched verdicts.
     * @param verdict supplied inconclusive label; this fixture checks report policy rather than explorer truth
     */
    @ParameterizedTest
    @ValueSource(strings = {"INCOMPLETE", "APPROXIMATE_PASS"})
    void sourceInconclusiveRowsKeepExemptions(String verdict) {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 {} expected PASS; }");
        var row = new BenchmarkResult("DFS", "subject", 1, 0, 0, verdict, StoreType.EXACT);
        assertSuccessReport(List.of(row), List.of(model));
    }

    /** Unknown expectations are skipped and must not be described as checked matches. */
    @Test
    void absentExpectationKeepsQualification() {
        var row = new BenchmarkResult("DFS", "subject", 1, 0, 0, "DEADLOCK", StoreType.EXACT);
        assertSuccessReport(List.of(row), List.of(legacy(null, null)));
    }

    /** An execution-only accepted row must not be described as an established property violation. */
    @Test
    void absentInvariantKeepsReplayQualification() {
        var row = new BenchmarkResult("CONTEXT_BOUNDED", "subject", 1, 0, 0, "VIOLATION",
            Trace.of(List.of(), List.of(), TraceOutcome.VIOLATION), StoreType.BITSTATE);
        assertSuccessReport(List.of(row), List.of(legacy("VIOLATION", null)));
    }

    /** A false property at the initial configuration supplies an authentic zero-step violation. */
    @Test
    void genuinePropertyViolationUsesQualifiedSuccessSection() {
        var row = new BenchmarkResult("DPOR", "subject", 1, 0, 0, "VIOLATION",
            Trace.of(List.of(), List.of(), TraceOutcome.VIOLATION), StoreType.EXACT);
        assertSuccessReport(List.of(row), List.of(legacy("VIOLATION", (state, config) -> false)));
    }

    /** A genuine source runtime error remains accepted even though its supplied property is true. */
    @Test
    void sourceRuntimeViolationKeepsExceptionQualification() {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { atomic { x = 1 % 0; } } invariant always (true); expected VIOLATION; }");
        Trace trace = new DfsExplorer().explore(model.program(), model.invariant().orElseThrow()).traces().getFirst();
        assertEquals(TraceOutcome.VIOLATION, trace.outcome());
        var row = new BenchmarkResult("CONTEXT_BOUNDED", "subject", 1, 0, 0, "VIOLATION", trace, StoreType.EXACT);
        assertSuccessReport(List.of(row), List.of(model));
    }

    /** Exact DFS expectation failure takes precedence and appears verbatim in the warning. */
    @Test
    void dfsMismatchWarningKeepsDiagnostic() {
        var row = new BenchmarkResult("DFS", "subject", 1, 0, 0, "DEADLOCK", StoreType.EXACT);
        assertFailureReport(List.of(row), List.of(legacy("PASS", null)),
            "DFS verdict mismatch for subject: expected PASS but got DEADLOCK");
    }

    /** Missing evidence retains the subject, bounded strategy and approximate store in rendered output. */
    @Test
    void missingTraceWarningKeepsDiagnostic() {
        var row = new BenchmarkResult("CONTEXT_BOUNDED", "subject", 1, 0, 0, "VIOLATION", StoreType.BITSTATE);
        assertFailureReport(List.of(row), List.of(legacy("VIOLATION", null)),
            "Missing failing trace for subject under CONTEXT_BOUNDED (BITSTATE)");
    }

    /** An unknown model's diagnostic must survive both the attestation and outer report writer. */
    @Test
    void unknownProgramWarningKeepsDiagnostic() {
        var row = new BenchmarkResult("DPOR", "subject", 1, 0, 0, "VIOLATION",
            Trace.of(List.of(), List.of(), TraceOutcome.VIOLATION), StoreType.EXACT);
        assertFailureReport(List.of(row), List.of(), "Unknown program: subject");
    }

    /** Replayed non-violations render the actual diagnostic instead of any success prose. */
    @Test
    void replayedNonViolationWarningKeepsDiagnostic() {
        var row = new BenchmarkResult("DPOR", "subject", 1, 0, 0, "VIOLATION",
            Trace.of(List.of(), List.of(), TraceOutcome.VIOLATION), StoreType.EXACT);
        assertFailureReport(List.of(row), List.of(legacy("VIOLATION", (state, config) -> true)),
            "Replayed trace for subject under DPOR (EXACT) does not violate invariant");
    }

    /** Asserts the entire standalone section and its unchanged inclusion at the end of the full report.
     * @param rows reporting inputs whose checks must succeed
     * @param programs actual replay models and optional expectations/properties
     */
    private static void assertSuccessReport(List<BenchmarkResult> rows, List<BenchmarkProgram> programs) {
        var attestation = new SoundnessAttestation(rows, programs);
        assertTrue(attestation.isSound(), attestation.failureReason());
        assertNull(attestation.failureReason());
        assertEquals(SUCCESS, attestation.formatMarkdown());
        String report = new ReportWriter(rows, programs).writeMarkdown();
        assertTrue(report.endsWith(SUCCESS), report);
    }

    /** Asserts the complete warning template, diagnostic and section boundaries through both entry points.
     * @param rows reporting inputs whose checks must fail
     * @param programs actual replay models and optional expectations/properties
     * @param reason literal expected diagnostic independent of the implementation
     */
    private static void assertFailureReport(List<BenchmarkResult> rows, List<BenchmarkProgram> programs, String reason) {
        var attestation = new SoundnessAttestation(rows, programs);
        assertFalse(attestation.isSound());
        assertEquals(reason, attestation.failureReason());
        String expected = "## Soundness Attestation\n\nWARNING: " + reason
            + ". The model checker may not be sound.\n\n";
        assertEquals(expected, attestation.formatMarkdown());
        String report = new ReportWriter(rows, programs).writeMarkdown();
        assertTrue(report.endsWith(expected), report);
    }

    /** Supplies a zero-step legacy model without conflating rendering checks with exploration coverage.
     * @param expected optional registered expectation
     * @param invariant optional property checked during replay
     * @return registered subject for literal reporting inputs
     */
    private static BenchmarkProgram legacy(String expected, Invariant invariant) {
        var program = new Program(CounterState.of(0), List.of(new ModelThread(0, List.of())));
        return new BenchmarkProgram("subject", program, expected, invariant);
    }
}
