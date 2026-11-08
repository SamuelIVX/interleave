/** Nested atomic syntax preserves one outer scheduling, observation and rollback boundary. */
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceNestedAtomicTest {
    /** Existing exact strategies must consume the same flattened private action. */
    enum Mode { DFS, POR, DPOR, CBS }

    /** Helper callers retain owner-local load/store atomicity and complete dependency footprints.
     * @param mode exact strategy
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void nestedHelperIncrementsRemainOneActionPerOwner(Mode mode) {
        String prefix = "shared int counter = 0; shared int[] values = [0, 0]; local int saved = 0; ";
        String body = "counter = local.saved + 1; values[tid] = tid + 10;";
        String tail = " thread 0 { call increment; } thread 1 { call increment; }"
            + " invariant final (counter == 2 && values[0] == 10 && values[1] == 11);";
        var model = load(prefix + "helper increment { atomic { local.saved = counter; atomic { " + body + " } } }" + tail);
        var flat = load(prefix + "helper increment { atomic { local.saved = counter; " + body + " } }" + tail);
        for (int owner = 0; owner < 2; owner++) {
            assertEquals(1, model.program().threads().get(owner).steps().size());
            Step nested = model.program().threads().get(owner).steps().getFirst();
            Step original = flat.program().threads().get(owner).steps().getFirst();
            assertEquals(original.reads(), nested.reads());
            assertEquals(original.writes(), nested.writes());
        }
        var result = search(model, mode);
        assertFalse(result.traces().isEmpty());
        for (Trace trace : result.traces()) {
            assertEquals(TraceOutcome.COMPLETED, trace.outcome());
            assertEquals(2, trace.length());
            DynamicState state = replay(model, trace);
            assertEquals(2, state.getInt("counter"));
            assertArrayEquals(new int[]{10, 11}, state.getArray("values"));
            assertTrue(trace.evidence().executedSteps().stream().allMatch(s -> s.label().startsWith("call increment at ")));
        }
        if (mode == Mode.DFS) assertEquals(5, result.statesExplored());
    }

    /** Private transient writes are invisible even after an inner closing brace.
     * @param mode exact strategy
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void innerCloseDoesNotCommitOrObserveProperties(Mode mode) {
        var model = load("shared int counter = 0; thread 0 { atomic {"
            + " atomic { counter = 9; } if (counter == 9) { atomic { counter = 0; } } } }"
            + " invariant always (counter == 0);");
        var traces = search(model, mode).traces();
        assertFalse(traces.isEmpty());
        for (Trace trace : traces) {
            assertEquals(TraceOutcome.COMPLETED, trace.outcome());
            assertEquals(1, trace.length());
            assertEquals(0, replay(model, trace).getInt("counter"));
        }
        var execution = new ExecutionDriver().runDetailed(model.program(), new Schedule(List.of(0)), model.invariant().orElseThrow());
        assertEquals(ScheduleExecution.Kind.COMPLETED, execution.kind());
        assertEquals(2, execution.states().size());
        assertEquals(execution.states().getFirst(), execution.states().getLast());
    }

    /** Branches and repeated bounded loops remain private and retain statement ordering.
     * @param mode exact strategy
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void nestedControlAndEmptyBlocksAddNoActions(Mode mode) {
        var model = load("shared int counter = 0; thread 0 { atomic { atomic {}"
            + " repeat 2 { atomic { while (counter < 2) limit 2 { atomic { counter = counter + 1; } } } }"
            + " if (false) { atomic { counter = 99; } } else { atomic { counter = counter + 1; } }"
            + " repeat 2147483647 { atomic {} } } } invariant final (counter == 3);");
        assertEquals(1, model.program().threads().getFirst().steps().size());
        var traces = search(model, mode).traces();
        assertFalse(traces.isEmpty());
        for (Trace trace : traces) {
            assertEquals(TraceOutcome.COMPLETED, trace.outcome());
            assertEquals(1, trace.length());
            assertEquals(3, replay(model, trace).getInt("counter"));
        }
    }

    /** Inner failures/cutoffs discard writes made before, inside and after nested block boundaries.
     * @param cutoff whether the terminal construct is a true zero-limit loop or modulo error
     * @param mode exact strategy
     */
    @ParameterizedTest @MethodSource("terminalCases")
    void nestedTerminalEvidenceRollsBackTheWholeOuterAction(boolean cutoff, Mode mode) {
        String terminal = cutoff ? "while (true) limit 0 {}" : "counter = 1 % 0;";
        var model = new SourceProgramLoader().load("program nested_stop {\nshared int counter = 0;\nthread 0 {\natomic {\n"
            + "counter = 7;\natomic {\ncounter = 9;\n" + terminal + "\n}\ncounter = 10;\n}\n}\n}");
        var traces = search(model, mode).traces();
        assertFalse(traces.isEmpty());
        for (Trace trace : traces) {
            assertEquals(cutoff ? TraceOutcome.INCOMPLETE : TraceOutcome.VIOLATION, trace.outcome());
            assertEquals(cutoff ? 0 : 1, trace.length());
            assertEquals(0, replay(model, trace).getInt("counter"));
            SourceLocation terminalSource = cutoff ? trace.cutoff().source() : trace.failure().source();
            assertEquals(8, terminalSource.line());
            assertEquals(1, terminalSource.column());
            if (cutoff) {
                assertEquals(CutoffEvidence.Reason.LOOP_LIMIT, trace.cutoff().reason());
                assertEquals(0, trace.cutoff().instructionIndex());
                assertEquals(0, trace.cutoff().iterations());
                assertTrue(trace.evidence().executedSteps().isEmpty());
            } else {
                assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, trace.failure().category());
                assertEquals(FailureEvidence.Phase.STEP, trace.failure().phase());
                ExecutedStep outer = trace.evidence().executedSteps().getFirst();
                assertEquals(0, outer.instructionIndex());
                assertEquals(new SourceLocation("<source>", 4, 1, 11, 2), outer.source());
                Trace minimal = new DeltaDebugger().minimize(model.program(), trace, TraceOutcome.VIOLATION, null);
                assertEquals(trace.evidence(), minimal.evidence());
                assertEquals(0, replay(model, minimal).getInt("counter"));
            }
        }
    }

    /** Crosses both noncommitting terminal events with each public exact strategy.
     * @return terminal/strategy cases
     */
    static java.util.stream.Stream<Arguments> terminalCases() {
        return java.util.stream.Stream.of(false, true).flatMap(cutoff -> Arrays.stream(Mode.values()).map(mode -> Arguments.of(cutoff, mode)));
    }

    /** The outer guard disables the whole action even with nested unguarded work.
     * @param enabled value of the outer entry guard
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void outerGuardStillControlsTheWholeAction(boolean enabled) {
        var model = load("shared int counter = 0; thread 0 { atomic when (" + enabled + ") { atomic { counter = 1; } } }");
        Trace trace = search(model, Mode.DFS).traces().getFirst();
        assertEquals(enabled ? TraceOutcome.COMPLETED : TraceOutcome.DEADLOCK, trace.outcome());
        assertEquals(enabled ? 1 : 0, trace.length());
        assertEquals(enabled ? 1 : 0, replay(model, trace).getInt("counter"));
    }

    /** Each nested brace counts before flattening, with the existing inclusive depth boundary. */
    @Test void nestingDepthRemainsInclusive() {
        String header = "program depth {\nshared int counter = 0;\nthread 0 {\n";
        assertNotNull(new SourceProgramLoader().load(header + "atomic {\n".repeat(14) + "}\n".repeat(14) + "}\n}"));
        var error = assertThrows(RegistryException.class, () -> new SourceProgramLoader().load(
            header + "atomic {\n".repeat(15) + "}\n".repeat(15) + "}\n}"));
        assertEquals("<source>:18:8: limit: Source block nesting exceeds 16", error.getMessage());
    }

    /** Inner braces add zero private instructions, but assignments across them share one cap. */
    @Test void nestedWorkSharesTheOuter64InstructionBudget() {
        String body = "repeat 32 { atomic { counter = counter + 1; } }";
        var model = load("shared int counter = 0; thread 0 { atomic { " + body + body + " } }");
        Trace trace = search(model, Mode.DFS).traces().getFirst();
        assertEquals(1, trace.length());
        assertEquals(64, replay(model, trace).getInt("counter"));
        var error = assertThrows(RegistryException.class, () -> load("shared int counter = 0; thread 0 { atomic { "
            + body + body + " atomic { counter = 1; } } }"));
        assertTrue(error.getMessage().contains("Atomic internal instructions exceed 64"));
        assertThrows(RegistryException.class, () -> load("shared int counter = 0; helper unused { repeat 0 { atomic { atomic { repeat 65 { counter = 1; } } } } } thread 0 {}"));
    }

    /** Nested statement grouping cannot hide expanded expressions at 5,000/5,001. */
    @Test void nestedExpressionsKeepTheGlobalBudget() {
        String assignment = "counter = " + sum(1250) + ";";
        String source = "shared int counter = 0; thread 0 { atomic { atomic { " + assignment + " } atomic { " + assignment + " } } }";
        assertNotNull(load(source));
        var error = assertThrows(RegistryException.class, () -> load(source + " invariant final (true);"));
        assertTrue(error.getMessage().contains("Expanded expression nodes exceed 5000"));
    }

    /** Guarded inner blocks are parsed and validated even when their containing source is unused.
     * @param body scheduled thread or unused helper source
     */
    @ParameterizedTest @ValueSource(strings = {
        "thread 0 { atomic { atomic when (true) {} } }",
        "thread 0 { atomic { if (false) { atomic when (true) {} } } }",
        "thread 0 { atomic { repeat 0 { atomic when (true) {} } } }",
        "helper unused { atomic { atomic when (true) {} } } thread 0 {}"})
    void guardedNestingIsAcceptedInEveryParsedBody(String body) {
        assertDoesNotThrow(() -> load("shared int counter = 0; " + body));
    }

    /** Builds source input with 1,250 leaves/1,249 operators, plus one target = 2,500 nodes.
     * @param leaves positive number of literal leaves
     * @return balanced source expression within the existing expression-depth bound
     */
    private static String sum(int leaves) {
        if (leaves == 1) return "1";
        int left = leaves / 2;
        return "(" + sum(left) + "+" + sum(leaves - left) + ")";
    }

    /** Loads a complete source fixture through the public bounded loader.
     * @param body declarations, helper/thread source and optional property
     * @return compiled model
     */
    private static BenchmarkProgram load(String body) { return new SourceProgramLoader().load("program nesting { " + body + " }"); }

    /** Runs an existing exact strategy with the fixture's actual property.
     * @param model compiled source
     * @param mode selected strategy
     * @return terminal witnesses
     */
    private static DfsResult search(BenchmarkProgram model, Mode mode) {
        var invariant = model.invariant().orElse(null);
        return switch (mode) {
            case DFS -> new DfsExplorer().explore(model.program(), invariant);
            case POR -> new StaticPorExplorer().explore(model.program(), invariant);
            case DPOR -> new DporExplorer().explore(model.program(), invariant);
            case CBS -> new ContextBoundedExplorer().explore(model.program(), invariant, null, null, 12);
        };
    }

    /** Validates actual source evidence and returns the final committed state.
     * @param model source program with its original property
     * @param trace returned terminal witness
     * @return final state after invariant-aware replay, including noncommitting terminal validation
     */
    private static DynamicState replay(BenchmarkProgram model, Trace trace) {
        return (DynamicState) new TraceReplayer().replay(model.program(), trace, model.invariant().orElse((s, c) -> true)).state();
    }
}
