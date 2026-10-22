/** Replay obligations and exact diagnostics for reported violations across storage modes. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.FailureEvidence;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.CorruptedTraceException;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.core.TraceEvidence;
import dev.samhb.interleave.search.TraceOutcome;
import dev.samhb.interleave.search.TraceReplayer;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SoundnessReplayTest {
    /** After verdict validation succeeds, every strategy/store pair must supply a trace and retain both fields.
     * @param strategy reporting strategy
     * @param store reported storage mode
     */
    @ParameterizedTest
    @MethodSource("strategiesAndStores")
    void missingTraceReportsSubjectStrategyAndStore(String strategy, StoreType store) {
        var model = legacy((state, config) -> ((CounterState) state).counter() == 0);
        var attestation = new SoundnessAttestation(List.of(row(strategy, store, null)), List.of(model));
        assertFalse(attestation.isSound());
        assertEquals("Missing failing trace for subject under " + strategy + " (" + store + ")",
            attestation.failureReason());
    }

    /** Exact DFS expectation mismatches precede replay diagnostics; bitstate skips verdict matching.
     * @param store storage mode controlling participation in exact verdict validation
     * @param expectedReason literal diagnostic for a missing-trace violation with a PASS expectation
     */
    @ParameterizedTest
    @CsvSource({"EXACT, DFS verdict mismatch for subject: expected PASS but got VIOLATION",
        "BITSTATE, Missing failing trace for subject under DFS (BITSTATE)"})
    void missingTraceDiagnosticRespectsVerdictPrecedence(StoreType store, String expectedReason) {
        var model = new BenchmarkProgram("subject", legacy(null).program(), "PASS");
        var attestation = new SoundnessAttestation(List.of(row("DFS", store, null)), List.of(model));
        assertFalse(attestation.isSound());
        assertEquals(expectedReason, attestation.failureReason());
    }

    /** A present trace still requires a registered model, including outside exact verdict checks.
     * @param store reported storage mode
     */
    @ParameterizedTest
    @EnumSource(StoreType.class)
    void unknownProgramReportsSubject(StoreType store) {
        var attestation = new SoundnessAttestation(List.of(row("CONTEXT_BOUNDED", store, counterTrace())), List.of());
        assertFalse(attestation.isSound());
        assertEquals("Unknown program: subject", attestation.failureReason());
    }

    /** The one-step witness changes the counter from safe zero to unsafe one in every reporting context.
     * @param strategy reporting strategy
     * @param store reported storage mode
     */
    @ParameterizedTest
    @MethodSource("strategiesAndStores")
    void genuineLegacyViolationPasses(String strategy, StoreType store) {
        var model = legacy((state, config) -> ((CounterState) state).counter() == 0);
        var attestation = new SoundnessAttestation(List.of(row(strategy, store, counterTrace())), List.of(model));
        assertTrue(attestation.isSound(), attestation.failureReason());
        assertNull(attestation.failureReason());
    }

    /** A trace ending at counter one is counterfeit for a property that holds there, even if initially false.
     * @param strategy reporting strategy
     * @param store reported storage mode
     */
    @ParameterizedTest
    @MethodSource("strategiesAndStores")
    void replayedNonViolationReportsSubjectStrategyAndStore(String strategy, StoreType store) {
        var model = legacy((state, config) -> ((CounterState) state).counter() == 1);
        var attestation = new SoundnessAttestation(List.of(row(strategy, store, counterTrace())), List.of(model));
        assertFalse(attestation.isSound());
        assertEquals("Replayed trace for subject under " + strategy + " (" + store + ") does not violate invariant",
            attestation.failureReason());
    }

    /** A successful first replay must not hide a later counterfeit violation in the same report.
     * @param store storage mode of the later counterfeit row
     */
    @ParameterizedTest
    @EnumSource(StoreType.class)
    void laterCounterfeitViolationStillFails(StoreType store) {
        var model = legacy((state, config) -> ((CounterState) state).counter() == 0);
        Trace counterfeit = Trace.of(List.of(), List.of(), TraceOutcome.VIOLATION);
        var attestation = new SoundnessAttestation(List.of(
            row("CONTEXT_BOUNDED", StoreType.EXACT, counterTrace()),
            row("DPOR", store, counterfeit)), List.of(model));
        assertFalse(attestation.isSound());
        assertEquals("Replayed trace for subject under DPOR (" + store + ") does not violate invariant",
            attestation.failureReason());
    }

    /** Replay corruption propagates instead of being converted into a successful attestation.
     * @param store reported storage mode
     */
    @ParameterizedTest
    @EnumSource(StoreType.class)
    void corruptLegacyOutcomePropagates(StoreType store) {
        var model = legacy((state, config) -> ((CounterState) state).counter() == 0);
        Trace corrupt = Trace.of(List.of(0), List.of(StepOutcome.BLOCKED), TraceOutcome.VIOLATION);
        var error = assertThrows(CorruptedTraceException.class, () -> new SoundnessAttestation(
            List.of(row("CONTEXT_BOUNDED", store, corrupt)), List.of(model)));
        assertEquals("Trace outcome mismatch at step 0 (thread 0): recorded BLOCKED but execution produced ADVANCED",
            error.getMessage());
    }

    /** Legacy replay without a property validates execution only; success does not prove a violation.
     * @param store reported storage mode
     */
    @ParameterizedTest
    @EnumSource(StoreType.class)
    void absentInvariantRetainsExecutionOnlyCompatibility(StoreType store) {
        var model = legacy(null);
        var attestation = new SoundnessAttestation(List.of(row("CONTEXT_BOUNDED", store, counterTrace())), List.of(model));
        assertTrue(attestation.isSound(), attestation.failureReason());
        assertNull(attestation.failureReason());
    }

    /** Runtime errors remain authentic violations even when atomic rollback leaves the property true.
     * @param store reported storage mode
     */
    @ParameterizedTest
    @EnumSource(StoreType.class)
    void authenticSourceRuntimeFailurePassesWithTrueInvariant(StoreType store) {
        var model = sourceRuntimeFailure();
        Invariant invariant = model.invariant().orElseThrow();
        Trace trace = new DfsExplorer().explore(model.program(), invariant).traces().getFirst();
        assertEquals(TraceOutcome.VIOLATION, trace.outcome());
        assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, trace.failure().kind());
        assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, trace.failure().category());
        var end = new TraceReplayer().replay(model.program(), trace, invariant);
        assertEquals(7, ((DynamicState) end.state()).getInt("x"));
        assertTrue(invariant.holds(end.state(), end));
        var attestation = new SoundnessAttestation(List.of(row("CONTEXT_BOUNDED", store, trace)), List.of(model));
        assertTrue(attestation.isSound(), attestation.failureReason());
        assertNull(attestation.failureReason());
    }

    /** Authentic source property failures also pass without relying on the runtime-error exception.
     * @param store reported storage mode
     */
    @ParameterizedTest
    @EnumSource(StoreType.class)
    void authenticSourcePropertyFailurePasses(StoreType store) {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { atomic { x = 1; } } invariant always (x == 0); expected VIOLATION; }");
        Trace trace = new DfsExplorer().explore(model.program(), model.invariant().orElseThrow()).traces().getFirst();
        assertEquals(TraceOutcome.VIOLATION, trace.outcome());
        assertEquals(FailureEvidence.Kind.INVARIANT, trace.failure().kind());
        var attestation = new SoundnessAttestation(List.of(row("CONTEXT_BOUNDED", store, trace)), List.of(model));
        assertTrue(attestation.isSound(), attestation.failureReason());
        assertNull(attestation.failureReason());
    }

    /** A source completion relabeled VIOLATION must reach invariant-aware terminal validation.
     * @param store reported storage mode
     */
    @ParameterizedTest
    @EnumSource(StoreType.class)
    void forgedSourceTerminalVerdictPropagates(StoreType store) {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { atomic { x = 1; } } invariant final (x == 1); }");
        Trace complete = new DfsExplorer().explore(model.program(), model.invariant().orElseThrow()).traces().getFirst();
        assertEquals(TraceOutcome.COMPLETED, complete.outcome());
        Trace forged = new Trace(complete.threadIds(), complete.outcomes(), TraceOutcome.VIOLATION, complete.evidence());
        var error = assertThrows(CorruptedTraceException.class, () -> new SoundnessAttestation(
            List.of(row("CONTEXT_BOUNDED", store, forged)), List.of(model)));
        assertEquals("Source terminal verdict does not reproduce", error.getMessage());
    }

    /** Merely labeling failure evidence as a runtime error cannot bypass replay's evidence check.
     * @param store reported storage mode
     */
    @ParameterizedTest
    @EnumSource(StoreType.class)
    void forgedSourceRuntimeEvidencePropagates(StoreType store) {
        var model = sourceRuntimeFailure();
        Trace trace = new DfsExplorer().explore(model.program(), model.invariant().orElseThrow()).traces().getFirst();
        FailureEvidence actual = trace.failure();
        FailureEvidence forged = new FailureEvidence(actual.kind(), actual.phase(), actual.source(),
            FailureEvidence.Category.ARRAY_BOUNDS);
        Trace wrong = new Trace(trace.threadIds(), trace.outcomes(), trace.outcome(),
            new TraceEvidence(trace.evidence().sourceDigest(), trace.evidence().executedSteps(), forged, null));
        var error = assertThrows(CorruptedTraceException.class, () -> new SoundnessAttestation(
            List.of(row("CONTEXT_BOUNDED", store, wrong)), List.of(model)));
        assertEquals("Source failure classification mismatch", error.getMessage());
    }

    /** Enumerates the existing reporting strategy/storage matrix without introducing new policy.
     * @return all four reporting strategies paired with both storage modes
     */
    private static Stream<Arguments> strategiesAndStores() {
        return Stream.of("DFS", "STATIC_POR", "DPOR", "CONTEXT_BOUNDED")
            .flatMap(strategy -> Stream.of(StoreType.values()).map(store -> Arguments.of(strategy, store)));
    }

    /** Supplies an executable legacy model with an optional property and a violation expectation.
     * @param invariant property to evaluate at the replayed state, or null for execution-only compatibility
     * @return counter model with one real write step
     */
    private static BenchmarkProgram legacy(Invariant invariant) {
        var program = new Program(CounterState.of(0), List.of(new ModelThread(0, List.of(new WriteCounterStep(0)))));
        return new BenchmarkProgram("subject", program, "VIOLATION", invariant);
    }

    /** Builds a real source error after a committed write, followed by a rolled-back tentative write.
     * @return model whose true property remains true when modulo-by-zero aborts the second atomic block
     */
    private static BenchmarkProgram sourceRuntimeFailure() {
        return new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { atomic { x = 7; } atomic { x = 9; x = 1 % 0; } } invariant always (true); expected VIOLATION; }");
    }

    /** Supplies the concrete legacy witness whose recorded write advances counter zero to one.
     * @return aligned one-step trace carrying a claimed violation
     */
    private static Trace counterTrace() {
        return Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION);
    }

    /** Builds a reported violation without coupling this contract to harness row qualification.
     * @param strategy reporting strategy label
     * @param store reporting storage mode
     * @param trace failing trace, or null to exercise the missing-trace diagnostic
     * @return violation row for the registered subject
     */
    private static BenchmarkResult row(String strategy, StoreType store, Trace trace) {
        return new BenchmarkResult(strategy, "subject", 2, 0, 0, "VIOLATION", trace, store);
    }
}
