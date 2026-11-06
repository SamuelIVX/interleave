/** Source assertions observe scheduled or tentative values with authenticated terminal evidence. */
package dev.samhb.interleave.format;

import com.google.gson.*;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.minimize.DeltaDebugger;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.report.*;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceAssertionTest {
    /** Existing exact exploration routes share the assertion execution contract. */
    enum Mode { DFS, POR, DPOR, CBS }

    /** A scheduled assertion races with a writer and observes the value at its own action.
     * @param mode exact strategy
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void scheduledAssertionsPreserveBothInterleavingOutcomes(Mode mode) {
        var model = load("shared int x = 0; shared int done = 0; thread 0 { assert (x == 0); atomic { done = 1; } }"
            + " thread 1 { atomic { x = 1; } } invariant always (true);");
        var result = search(model, mode);
        assertEquals(Set.of(TraceOutcome.COMPLETED, TraceOutcome.VIOLATION),
            result.traces().stream().map(Trace::outcome).collect(java.util.stream.Collectors.toSet()));
        for (Trace trace : result.traces()) {
            DynamicState state = replay(model, trace);
            if (trace.outcome() == TraceOutcome.VIOLATION) {
                assertEquals("ASSERTION", trace.failure().kind().name());
                assertEquals(FailureEvidence.Phase.STEP, trace.failure().phase());
                assertNull(trace.failure().category());
                assertEquals(List.of(1, 0), trace.threadIds());
                assertEquals(List.of(StepOutcome.ADVANCED, StepOutcome.ASSERTION_FAILED), trace.outcomes());
                assertEquals(0, state.getInt("done"));
            } else {
                assertEquals(3, trace.length());
                assertEquals(1, state.getInt("done"));
                assertNull(trace.failure());
            }
            assertEquals(1, state.getInt("x"));
        }
    }

    /** A private assertion can fail even when rollback leaves the supplied global property true.
     * @param mode exact strategy
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void privateFalseAssertionRollsBackAndKeepsInnerLocation(Mode mode) {
        var model = privateFailure();
        var traces = search(model, mode).traces();
        assertFalse(traces.isEmpty());
        for (Trace trace : traces) {
            assertEquals(TraceOutcome.VIOLATION, trace.outcome());
            assertEquals(1, trace.length());
            assertEquals("ASSERTION", trace.failure().kind().name());
            assertEquals(new SourceLocation("<source>", 6, 1, 6, 17), trace.failure().source());
            assertEquals(new SourceLocation("<source>", 4, 1, 8, 2), trace.evidence().executedSteps().getFirst().source());
            assertEquals(0, replay(model, trace).getInt("x"));
        }
        var execution = new ExecutionDriver().runDetailed(model.program(), new Schedule(List.of(0)), model.invariant().orElseThrow());
        assertEquals(2, execution.states().size());
        assertEquals(execution.states().getFirst(), execution.states().getLast());
    }

    /** Existing short-circuit and runtime-error distinctions apply in both statement contexts.
     * @param atomic whether both assertions execute in one private action
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void assertionExpressionErrorsRemainRuntimeErrors(boolean atomic) {
        String checks = "assert (true || 1 % 0 == 0); assert (1 % 0 == 0);";
        var model = load("shared int x = 0; thread 0 { " + (atomic ? "atomic { x = 9; " + checks + " }" : checks) + " }");
        for (Mode mode : Mode.values()) {
            var traces = search(model, mode).traces();
            assertFalse(traces.isEmpty());
            for (Trace trace : traces) {
                assertEquals(TraceOutcome.VIOLATION, trace.outcome());
                assertEquals(atomic ? 1 : 2, trace.length());
                assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, trace.failure().kind());
                assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, trace.failure().category());
                assertEquals(0, replay(model, trace).getInt("x"));
            }
        }
    }

    /** Helpers, nested atomic blocks, local/tid/array reads and loop occurrences retain owner binding.
     * @param mode exact strategy
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void assertionsComposeWithExistingSourceControl(Mode mode) {
        var model = load("shared int[] values = [10, 11]; local int saved = 0;"
            + " helper check { repeat 2 { assert (values[tid] == tid + 10); }"
            + " atomic { local.saved = tid; atomic { assert (local.saved == tid); }"
            + " if (false) { assert (false); } else { assert (true); }"
            + " while (false) limit 0 { assert (false); } } } thread 0 { call check; } thread 1 { call check; }");
        assertEquals(3, model.program().threads().getFirst().steps().size());
        var traces = search(model, mode).traces();
        assertFalse(traces.isEmpty());
        for (Trace trace : traces) {
            assertEquals(TraceOutcome.COMPLETED, trace.outcome());
            DynamicState state = replay(model, trace);
            assertEquals(0, state.getLocalInt(0, "saved"));
            assertEquals(1, state.getLocalInt(1, "saved"));
            assertTrue(trace.evidence().executedSteps().stream().allMatch(s -> s.label().startsWith("call check at ")));
        }
    }

    /** Terminal events retain source order, including cutoff versus false assertion precedence.
     * @param body private statement sequence
     * @param outcome expected terminal outcome
     * @param kind expected failure kind, or NONE for a cutoff
     */
    @ParameterizedTest @CsvSource(delimiter = '|', textBlock = """
        assert (false); while (true) limit 0 {} | VIOLATION | ASSERTION
        while (true) limit 0 {} assert (false); | INCOMPLETE | NONE
        x = 1 % 0; assert (false); | VIOLATION | RUNTIME_ERROR
        assert (false); x = 1 % 0; | VIOLATION | ASSERTION
        """)
    void firstPrivateTerminalEventWins(String body, String outcome, String kind) {
        var model = load("shared int x = 0; thread 0 { atomic { " + body + " } }");
        Trace trace = search(model, Mode.DFS).traces().getFirst();
        assertEquals(outcome, trace.outcome().name());
        assertEquals(kind, trace.failure() == null ? "NONE" : trace.failure().kind().name());
        assertEquals(0, replay(model, trace).getInt("x"));
    }

    /** Initial/post-commit global failures still preempt later statements without a false choice. */
    @Test void globalPropertiesKeepTheirExistingTiming() {
        for (String body : List.of("thread 0 { assert (false); } invariant always (false);",
                "thread 0 { atomic { x = 1; } assert (false); } invariant always (x == 0);")) {
            var model = load("shared int x = 0; " + body);
            Trace trace = search(model, Mode.DFS).traces().getFirst();
            assertEquals(FailureEvidence.Kind.INVARIANT, trace.failure().kind());
            assertEquals(FailureEvidence.Phase.PROPERTY, trace.failure().phase());
            assertTrue(trace.outcomes().stream().allMatch(o -> o == StepOutcome.ADVANCED));
            replay(model, trace);
        }
    }

    /** Assertion evidence cannot be forged into runtime error; minimization regenerates all evidence. */
    @Test void assertionWitnessesReplayMinimizeAndRejectTampering() {
        var model = load("shared int x = 0; shared int noise = 0; thread 0 { atomic { x = 7; assert (x == 0); } }"
            + " thread 1 { atomic { noise = 1; } } invariant always (x == 0);");
        Trace original = new ExecutionDriver().runDetailed(model.program(), new Schedule(List.of(1, 0)), model.invariant().orElseThrow()).trace();
        for (Invariant property : Arrays.asList(null, model.invariant().orElseThrow())) {
            Trace minimal = new DeltaDebugger().minimize(model.program(), original, TraceOutcome.VIOLATION, property);
            assertEquals(List.of(0), minimal.threadIds());
            DynamicState state = (DynamicState) new TraceReplayer().replay(model.program(), minimal, property).state();
            assertEquals(0, state.getInt("x"));
            assertEquals(0, state.getInt("noise"));
            assertEquals(original.failure(), minimal.failure());
        }
        FailureEvidence forged = new FailureEvidence(FailureEvidence.Kind.RUNTIME_ERROR, FailureEvidence.Phase.STEP,
            original.failure().source(), FailureEvidence.Category.OTHER_EVALUATION_ERROR);
        Trace wrong = new Trace(original.threadIds(), original.outcomes(), original.outcome(),
            new TraceEvidence(original.evidence().sourceDigest(), original.evidence().executedSteps(), forged, null));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), wrong));
        Trace wrongDigest = new Trace(original.threadIds(), original.outcomes(), original.outcome(),
            new TraceEvidence("0".repeat(64), original.evidence().executedSteps(), original.failure(), null));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), wrongDigest));
    }

    /** All reporting strategies/stores authenticate the false private assertion despite a true property.
     * @throws java.io.IOException if the fresh browser-integration artifact cannot be written
     */
    @Test void reportAttestationAndSerializationKeepAssertionEvidence() throws java.io.IOException {
        var model = privateFailure();
        var rows = new BenchmarkHarness().runProgram(model);
        assertEquals(8, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.verdict().equals("VIOLATION")));
        var report = JsonParser.parseString(new ReportWriter(rows, List.of(model)).writeJson()).getAsJsonObject();
        assertTrue(report.get("soundness").getAsBoolean());
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-assertion-visualizer.json"), report.toString());
        Trace actual = search(model, Mode.DFS).traces().getFirst();
        assertEquals(actual.failure(), actual.toRecord().withStates(List.of()).evidence().failure());
        Trace missing = new Trace(actual.threadIds(), actual.outcomes(), actual.outcome(),
            new TraceEvidence(actual.evidence().sourceDigest(), actual.evidence().executedSteps(), null, null));
        var unauthenticated = new BenchmarkResult("DFS", "assertions", 1, 0, 0, "VIOLATION", missing, StoreType.EXACT);
        assertFalse(new SoundnessAttestation(List.of(unauthenticated), List.of(model)).isSound());
        for (var item : report.getAsJsonArray("benchmarks")) {
            var trace = item.getAsJsonObject().getAsJsonObject("failingTrace");
            assertEquals("ASSERTION", trace.getAsJsonObject("failure").get("kind").getAsString());
            assertFalse(trace.getAsJsonObject("failure").has("category"));
            var evidence = new Gson().fromJson(trace.getAsJsonObject("failure"), FailureEvidence.class);
            assertEquals("ASSERTION", evidence.kind().name());
        }
    }

    /** Compact evidence validation permits only STEP/no-category for the new kind. */
    @Test void assertionEvidenceRejectsInvalidPhaseOrCategory() {
        var kind = FailureEvidence.Kind.valueOf("ASSERTION");
        assertNotNull(new FailureEvidence(kind, FailureEvidence.Phase.STEP, null, null));
        assertThrows(NullPointerException.class, () -> new FailureEvidence(null, FailureEvidence.Phase.STEP, null, null));
        assertThrows(NullPointerException.class, () -> new FailureEvidence(kind, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FailureEvidence(kind, FailureEvidence.Phase.PROPERTY, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FailureEvidence(kind, FailureEvidence.Phase.STEP, null, FailureEvidence.Category.ARRAY_BOUNDS));
    }

    /** The new word stays valid as a declaration/program identifier and assignment target. */
    @Test void assertIsContextual() {
        var model = new SourceProgramLoader().load("program assert { shared int assert = 0; local int[] assert_values = [0];"
            + " thread 0 { atomic { assert = 1; local.assert_values[0] = assert; assert (assert == local.assert_values[0]); } } }");
        assertEquals(1, replay(model, search(model, Mode.DFS).traces().getFirst()).getInt("assert"));
        var helper = load("shared int x = 0; helper assert { assert (true); } thread 0 { call assert; }");
        assertEquals(TraceOutcome.COMPLETED, search(helper, Mode.DFS).traces().getFirst().outcome());
    }

    /** One assertion consumes one instruction regardless of whether scheduled or private.
     * @param atomic whether assertions execute privately
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void assertionInstructionBudgetsAreInclusive(boolean atomic) {
        String before = atomic ? "atomic {" : "";
        String after = atomic ? "}" : "";
        var model = load("shared int x = 0; thread 0 { " + before + "repeat 64 { assert (true); }" + after + " }");
        Trace trace = search(model, Mode.DFS).traces().getFirst();
        assertEquals(atomic ? 1 : 64, trace.length());
        assertThrows(RegistryException.class, () -> load("shared int x = 0; thread 0 { " + before + "repeat 65 { assert (true); }" + after + " }"));
    }

    /** A 4,999-node conjunction plus a true assertion fits 5,000; a true property crosses it.
     * @param atomic whether expressions occur privately
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void assertionExpressionBudgetsAreInclusive(boolean atomic) {
        String checks = "assert (" + conjunction(2500) + "); assert (true);";
        String source = "shared int x = 0; thread 0 { " + (atomic ? "atomic { " + checks + " }" : checks) + " }";
        assertNotNull(load(source));
        assertThrows(RegistryException.class, () -> load(source + " invariant final (true);"));
    }

    /** Type/name/syntax checking also applies to assertions in unused helper and zero-repeat bodies.
     * @param body deliberately invalid scheduled source
     */
    @ParameterizedTest @ValueSource(strings = {"thread 0 { assert (1); }", "thread 0 { assert (missing); }",
        "thread 0 { assert (true) }", "thread 0 { assert (true, false); }",
        "helper unused { repeat 0 { assert (local.missing == 0); } } thread 0 {}"})
    void rejectsMalformedAssertions(String body) {
        assertThrows(RegistryException.class, () -> load("shared int x = 0; " + body));
    }

    /** Supplies a private assertion whose exact inner span is counted directly from fixed text.
     * @return source model with a true global property after atomic rollback
     */
    private static BenchmarkProgram privateFailure() {
        return new SourceProgramLoader().load("program assertions {\nshared int x = 0;\nthread 0 {\natomic {\nx = 7;\nassert (x == 0);\nx = 9;\n}\n}\ninvariant always (x == 0);\n}");
    }

    /** Builds balanced source with 2,500 true leaves and 2,499 conjunction nodes.
     * @param leaves positive number of boolean leaves
     * @return depth-bounded source expression
     */
    private static String conjunction(int leaves) {
        if (leaves == 1) return "true";
        int left = leaves / 2;
        return "(" + conjunction(left) + "&&" + conjunction(leaves - left) + ")";
    }

    /** Loads a complete public source fixture.
     * @param body declarations, helper/thread source and optional property
     * @return compiled model
     */
    private static BenchmarkProgram load(String body) { return new SourceProgramLoader().load("program assertions { " + body + " }"); }

    /** Uses the fixture's real property with an existing exact strategy.
     * @param model source model
     * @param mode exact strategy
     * @return terminal witnesses
     */
    private static DfsResult search(BenchmarkProgram model, Mode mode) {
        var property = model.invariant().orElse(null);
        return switch (mode) {
            case DFS -> new DfsExplorer().explore(model.program(), property);
            case POR -> new StaticPorExplorer().explore(model.program(), property);
            case DPOR -> new DporExplorer().explore(model.program(), property);
            case CBS -> new ContextBoundedExplorer().explore(model.program(), property, null, null, 12);
        };
    }

    /** Replays complete source identity/failure evidence against its property.
     * @param model original source/property
     * @param trace emitted witness
     * @return final committed state
     */
    private static DynamicState replay(BenchmarkProgram model, Trace trace) {
        return (DynamicState) new TraceReplayer().replay(model.program(), trace, model.invariant().orElse((s, c) -> true)).state();
    }
}
