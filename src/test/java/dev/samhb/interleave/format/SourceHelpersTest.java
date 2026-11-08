/** Public source-helper execution, owner binding, source identity and diagnostic contracts. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.minimize.DeltaDebugger;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceHelpersTest {
    /** Existing strategies all consume the same ordinary lowered instructions. */
    enum Mode { DFS, POR, DPOR, CBS }

    /** Atomic versus split helper bodies have independently enumerated final values.
     * @param split whether the helper contains two scheduled load/store actions
     * @param mode exact exploration strategy
     */
    @ParameterizedTest @MethodSource("increments")
    void helpersPreserveAtomicAndRacingIncrementOracles(boolean split, Mode mode) {
        String action = split ? "atomic { local.saved = counter; } atomic { counter = local.saved + 1; }"
            : "atomic { counter = counter + 1; }";
        String declarations = "shared int counter = 0; local int saved = 0; ";
        var model = load(declarations + "helper increment { " + action + " }"
            + " thread 0 { call increment; } thread 1 { call increment; } invariant final (counter == 2);");
        var inline = load(declarations + "thread 0 { " + action + " } thread 1 { " + action + " } invariant final (counter == 2);");
        assertEquals(split ? 2 : 1, model.program().threads().getFirst().steps().size());
        for (int owner = 0; owner < 2; owner++) for (int pc = 0; pc < (split ? 2 : 1); pc++) {
            Step helper = model.program().threads().get(owner).steps().get(pc);
            Step expanded = inline.program().threads().get(owner).steps().get(pc);
            assertEquals(expanded.reads(), helper.reads());
            assertEquals(expanded.writes(), helper.writes());
        }
        var result = search(model, mode);
        assertEquals(split ? Set.of(1, 2) : Set.of(2), terminalValues(model, result));
        assertEquals(split ? Set.of(TraceOutcome.COMPLETED, TraceOutcome.VIOLATION) : Set.of(TraceOutcome.COMPLETED), outcomes(result));
        assertEquals(terminalValues(inline, search(inline, mode)), terminalValues(model, result));
        if (mode == Mode.DFS) assertEquals(split ? 13 : 4, result.statesExplored());
    }

    /** Crosses independent body choices with all public strategies.
     * @return fixture/strategy cases
     */
    static Stream<Arguments> increments() {
        return Stream.of(false, true).flatMap(split -> Arrays.stream(Mode.values()).map(mode -> Arguments.of(split, mode)));
    }

    /** Owner-local references and tid resolve at the callsite rather than at declaration time.
     * @param mode exact strategy
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void helperLocalsAndTidBelongToEachCaller(Mode mode) {
        var model = load("shared int counter = 0; shared int[] values = [0, 0]; local int saved = 0;"
            + " helper publish { atomic { local.saved = tid; values[tid] = local.saved + 10; } }"
            + " thread 0 { call publish; } thread 1 { call publish; }"
            + " invariant final (values[0] == 10 && values[1] == 11);");
        var traces = search(model, mode).traces();
        assertFalse(traces.isEmpty());
        for (Trace trace : traces) {
            assertEquals(TraceOutcome.COMPLETED, trace.outcome());
            var state = (DynamicState) replay(model, trace).state();
            assertArrayEquals(new int[]{10, 11}, state.getArray("values"));
            assertEquals(0, state.getLocalInt(0, "saved"));
            assertEquals(1, state.getLocalInt(1, "saved"));
        }
    }

    /** Definition spans and literal callsite columns survive composition and repeated invocation. */
    @Test void nestedCallerLabelsRetainDefinitionAndLoopIdentity() {
        var model = new SourceProgramLoader().load("""
            program helpers {
            shared int counter = 0;
            helper increment { atomic { counter = counter + 1; } }
            helper pair { call increment; call increment; }
            thread 0 { call pair; repeat 2 { call increment; } }
            invariant final (counter == 4);
            }
            """);
        Trace trace = new DfsExplorer().explore(model.program(), model.invariant().orElseThrow()).traces().getFirst();
        assertEquals(List.of(0, 0, 0, 0), trace.threadIds());
        var steps = trace.evidence().executedSteps();
        assertEquals(List.of(0, 1, 2, 3), steps.stream().map(ExecutedStep::instructionIndex).toList());
        assertTrue(steps.get(0).label().startsWith("call pair at 5:12 -> call increment at 4:15 -> atomic"));
        assertTrue(steps.get(1).label().startsWith("call pair at 5:12 -> call increment at 4:31 -> atomic"));
        assertTrue(steps.get(2).label().startsWith("call increment at 5:34 -> atomic"));
        for (ExecutedStep step : steps) assertEquals(new SourceLocation("<source>", 3, 20, 3, 53), step.source());
        assertTrue(steps.get(0).expansionPath().isEmpty());
        assertEquals(List.of(0, 1), steps.subList(2, 4).stream().map(s -> s.expansionPath().getFirst().iteration()).toList());
        assertEquals(4, ((DynamicState) replay(model, trace).state()).getInt("counter"));
    }

    /** Empty helpers preserve direct branch joins and add no scheduled action. */
    @Test void emptyCallsAndBranchesPatchTheSameForwardContinuations() {
        var model = load("shared int counter = 0; helper none {} helper increment { atomic { counter = counter + 1; } }"
            + " helper choose { if (counter == 0) { call none; call increment; } else { call none; } }"
            + " thread 0 { atomic { counter = 0; } call choose; call none; atomic { counter = counter + 2; } }"
            + " invariant final (counter == 3);");
        for (Mode mode : Mode.values()) {
            var result = search(model, mode);
            assertEquals(Set.of(3), terminalValues(model, result));
            assertEquals(4, result.traces().getFirst().length());
        }
    }

    /** A helper in a bounded-loop body retains the final condition check and caller owner. */
    @Test void callsInsideBoundedLoopsHaveNoExtraProgramCounter() {
        var model = load("shared int counter = 0; helper increment { atomic { counter = counter + 1; } }"
            + " thread 0 { while (counter < 2) limit 2 { call increment; } } invariant final (counter == 2);");
        for (Mode mode : Mode.values()) {
            Trace trace = search(model, mode).traces().getFirst();
            assertEquals(5, trace.length());
            assertEquals(2, ((DynamicState) replay(model, trace).state()).getInt("counter"));
        }
    }

    /** Failure and omitted cutoff evidence keep their actual definition locations.
     * @param cutoff whether the helper stops at a loop bound instead of a runtime error
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void helperTerminalEvidenceReplaysAndRollsBack(boolean cutoff) {
        String body = cutoff ? "while (true) limit 0 {}" : "atomic { counter = 9; counter = 1 % 0; }";
        var model = new SourceProgramLoader().load("program helper_terminal {\nshared int counter = 0;\nhelper stop {\n"
            + body + "\n}\nthread 0 { call stop; }\n}");
        for (Mode mode : Mode.values()) {
            Trace trace = search(model, mode).traces().getFirst();
            assertEquals(cutoff ? TraceOutcome.INCOMPLETE : TraceOutcome.VIOLATION, trace.outcome());
            assertEquals(cutoff ? 0 : 1, trace.length());
            assertEquals(0, ((DynamicState) replay(model, trace).state()).getInt("counter"));
            if (cutoff) {
                assertEquals(CutoffEvidence.Reason.LOOP_LIMIT, trace.cutoff().reason());
                assertEquals(4, trace.cutoff().source().line());
                assertEquals(1, trace.cutoff().source().column());
                assertEquals(0, trace.cutoff().instructionIndex());
                assertEquals(0, trace.cutoff().iterations());
            } else {
                assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, trace.failure().category());
                assertEquals(FailureEvidence.Phase.STEP, trace.failure().phase());
                assertEquals(4, trace.failure().source().line());
                assertEquals(23, trace.failure().source().column());
                assertTrue(trace.evidence().executedSteps().getFirst().label().startsWith("call stop at "));
                Trace minimal = new DeltaDebugger().minimize(model.program(), trace, TraceOutcome.VIOLATION, null);
                assertEquals(1, minimal.length());
                replay(model, minimal);
            }
        }
    }

    /** New contextual words preserve existing scalar/array assignment and identifier behavior.
     * @param array whether the existing field named call is an array
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void contextualKeywordsPreserveExistingFieldNames(boolean array) {
        String declaration = array ? "shared int[] call = [0];" : "shared int call = 0;";
        String target = array ? "call[0]" : "call";
        var model = new SourceProgramLoader().load("program helper { shared int helper = 0; " + declaration
            + " helper increment { atomic { helper = helper + 1; " + target + " = " + target + " + 1; } }"
            + " thread 0 { call increment; } invariant final (helper == 1 && " + target + " == 1); }");
        assertEquals(Set.of(TraceOutcome.COMPLETED), outcomes(search(model, Mode.DFS)));
    }

    /** Public loader invocations own separate helper maps and source identities. */
    @Test void helpersDoNotLeakAcrossLoaderCallsAndEditedBodiesRejectStaleTraces() {
        var loader = new SourceProgramLoader();
        String source = "program independent { shared int counter = 0; helper increment { atomic { counter = 1; } } thread 0 { call increment; } }";
        var first = loader.load(source);
        Trace trace = search(first, Mode.DFS).traces().getFirst();
        var second = loader.load(source.replace("counter = 1;", "counter = 2;"));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(second.program(), trace));
        assertThrows(RegistryException.class, () -> loader.load("program unknown { shared int counter = 0; thread 0 { call increment; } }"));
    }

    /** Invalid calls/definitions are rejected before scheduling or expansion.
     * @return deliberately invalid source bodies and relevant diagnostic text
     */
    static Stream<Arguments> rejectedBodies() {
        return Stream.of(
            Arguments.of("thread 0 { call missing; }", "must be declared before use"),
            Arguments.of("helper again { call again; } thread 0 {}", "recursive calls are unsupported"),
            Arguments.of("helper first { call second; } helper second { call first; } thread 0 {}", "must be declared before use"),
            Arguments.of("helper counter {} thread 0 {}", "Duplicate declaration"),
            Arguments.of("helper first {} helper first {} thread 0 {}", "Duplicate declaration"),
            Arguments.of("helper first {} thread 0 { call first(1); }", "Expected 0 helper arguments"),
            Arguments.of("helper first(value) {} thread 0 {}", "Helper scalar type must be int or bool"),
            Arguments.of("helper first {} thread 0 { atomic { call first; } }", "Helper calls inside atomic blocks are unsupported"),
            Arguments.of("helper invalid { atomic { missing = 1; } } thread 0 {}", "Unknown"),
            Arguments.of("thread 0 {} helper late {}", "Expected '}'"));
    }

    /** Requires controlled source diagnostics rather than incidental JVM failures.
     * @param body invalid declaration/thread text
     * @param message diagnostic substring identifying the rejected contract
     */
    @ParameterizedTest @MethodSource("rejectedBodies")
    void rejectsUnsupportedOrUnresolvedHelperForms(String body, String message) {
        var error = assertThrows(RegistryException.class, () -> load("shared int counter = 0; " + body));
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    /** Loads one complete public source fixture.
     * @param body declarations, helpers, threads and optional property
     * @return compiled model
     */
    private static BenchmarkProgram load(String body) { return new SourceProgramLoader().load("program helpers { " + body + " }"); }

    /** Selects an exact existing strategy without changing helper execution semantics.
     * @param model compiled fixture
     * @param mode selected explorer
     * @return complete search evidence
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

    /** Replays all actual terminal observations against independent literal value oracles.
     * @param model original source/property
     * @param result emitted witnesses
     * @return distinct terminal counter values
     */
    private static Set<Integer> terminalValues(BenchmarkProgram model, DfsResult result) {
        return result.traces().stream().map(t -> ((DynamicState) replay(model, t).state()).getInt("counter")).collect(Collectors.toSet());
    }

    /** Validates complete source evidence and terminal classification.
     * @param model original program
     * @param trace returned witness
     * @return last committed configuration
     */
    private static Configuration replay(BenchmarkProgram model, Trace trace) {
        return new TraceReplayer().replay(model.program(), trace, model.invariant().orElse((s, c) -> true));
    }

    /** Projects terminal classifications independently of state-store keys.
     * @param result finished search
     * @return recorded terminal classes
     */
    private static Set<TraceOutcome> outcomes(DfsResult result) {
        return result.traces().stream().map(Trace::outcome).collect(Collectors.toSet());
    }
}
