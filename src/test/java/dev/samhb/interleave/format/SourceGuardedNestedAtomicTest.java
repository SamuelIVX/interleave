/** Guarded private blocks preserve whole-action enabledness, isolation and original evidence. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.*;
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

class SourceGuardedNestedAtomicTest {
    /** A disabled tentative write stays hidden until a separate committed action enables the owner.
     * @param strategy built-in exact strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void handoffRetainsOnlyCommittedValuesAndOriginalSchedule(Strategy strategy) {
        var model = load("shared int x = 0; shared bool ready = false; thread 0 { atomic { x = 1; "
            + "atomic when (ready) { x = 2; } x = 3; } } thread 1 { atomic { ready = true; } } "
            + "invariant always (x == 0 || x == 3);");
        var program = model.program();
        assertEquals(List.of(1),program.initialConfiguration().enabledThreadIds());
        assertThrows(IllegalScheduleException.class,() -> new ExecutionDriver().run(program,new Schedule(List.of(0))));
        var result = search(model,strategy,false);
        assertEquals(3,result.statesExplored());
        assertEquals(Set.of("0:false","0:true","3:true"),result.states().values().stream().map(c -> {
            var state = (DynamicState)c.state(); return state.getInt("x") + ":" + state.getBool("ready");
        }).collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of(List.of(1,0)),result.traces().stream().map(Trace::threadIds).toList());
        for (var trace : result.traces()) {
            assertEquals(TraceOutcome.COMPLETED,trace.outcome());
            assertDoesNotThrow(() -> new TraceReplayer().replay(program,trace,model.invariant().orElseThrow()));
        }
    }

    /** An initially true private guard can be disabled by a competing writer; both orders matter.
     * @param strategy built-in exact strategy with known-empty property observations
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void privateGuardDependenciesPreserveBothEnablednessOrders(Strategy strategy) {
        var model = load("shared int x = 0; shared bool ready = true; thread 0 { atomic { x = 1; "
            + "atomic when (ready) { x = 3; } } } thread 1 { atomic { ready = false; } } invariant always (true);");
        Step waiting = model.program().threads().getFirst().steps().getFirst();
        assertTrue(waiting.reads().contains(MemoryLocation.of("ready")));
        assertEquals(List.of(0,1),model.program().initialConfiguration().enabledThreadIds());
        var expected = Set.of(TraceOutcome.COMPLETED,TraceOutcome.DEADLOCK);
        assertEquals(expected,new DfsExplorer().explore(model.program(),model.invariant().orElseThrow()).traces()
            .stream().map(Trace::outcome).collect(java.util.stream.Collectors.toSet()));
        var result = search(model,strategy,false);
        assertEquals(expected,result.traces().stream().map(Trace::outcome).collect(java.util.stream.Collectors.toSet()));
        for (var trace : result.traces()) {
            var end = new TraceReplayer().replay(model.program(),trace,model.invariant().orElseThrow());
            assertEquals(trace.outcome() == TraceOutcome.COMPLETED ? List.of(0,1) : List.of(1),trace.threadIds());
            assertEquals(trace.outcome() == TraceOutcome.COMPLETED ? 3 : 0,((DynamicState)end.state()).getInt("x"));
        }
    }

    /** Disabled interior guards discard shared, local and array writes even for direct attempts. */
    @Test void disabledPreviewAndDefensiveTransitionNeverPublishScratch() {
        var model = load("shared int x = 0; shared int[] a = [0]; local int r = 0; local int[] b = [0]; "
            + "thread 0 { atomic { x = 1; a[0] = 2; local.r = 3; local.b[0] = 4; "
            + "atomic when (false) { x = 5; } x = 6; } }");
        var program = model.program();
        var parent = program.initialConfiguration();
        assertEquals(List.of(),parent.enabledThreadIds());
        Step step = program.threads().getFirst().steps().getFirst();
        for (int repeat = 0; repeat < 2; repeat++) assertFalse(step.enabled(parent.state()));
        var attempt = TransitionExecutor.execute(program,parent,0);
        assertEquals(StepOutcome.BLOCKED,attempt.outcome());
        assertNull(attempt.failure()); assertNull(attempt.cutoff());
        assertEquals(List.of(0),attempt.successor().programCounters());
        for (var config : List.of(parent,attempt.successor())) {
            var state = (DynamicState)config.state();
            assertEquals(0,state.getInt("x")); assertArrayEquals(new int[]{0},state.getArray("a"));
            assertEquals(0,state.getLocalInt(0,"r")); assertArrayEquals(new int[]{0},state.getLocalArray(0,"b"));
        }
    }

    /** Guards use reached tentative values, including conditions, repetition and nested propagation.
     * @param body one outer private body
     * @param enabled expected enabledness after a bounded preview
     * @param value committed final x, or zero when the whole action blocks
     */
    @ParameterizedTest @CsvSource(delimiter = '|',value = {
        "x = 1; atomic when (x == 1) { x = 2; } | true | 2",
        "x = 1; atomic when (x == 0) { x = 2; } | false | 0",
        "if (false) { atomic when (false) {} } x = 2; | true | 2",
        "if (true) { atomic when (false) {} } x = 2; | false | 0",
        "if (false) {} else { atomic when (false) {} } x = 2; | false | 0",
        "repeat 0 { atomic when (false) {} } x = 2; | true | 2",
        "repeat 2 { atomic when (x == 0) { x = x + 1; } } | false | 0",
        "while (x < 2) limit 2 { atomic when (x < 1) { x = x + 1; } } | false | 0",
        "atomic when (true) { atomic when (false) {} } x = 2; | false | 0",
        "local.r = 2; atomic when (local.r == 2) { x = local.r; } | true | 2",
        "a[0] = 1; local.b[0] = 2; atomic when (a[0] + local.b[0] == 3) { x = 2; } | true | 2",
        "repeat 2 { atomic when (x < 2) { x = x + 1; } } | true | 2",
        "while (x < 2) limit 2 { atomic when (x < 2) { x = x + 1; } } | true | 2",
        "atomic when (true) { atomic when (true) { x = 2; } } | true | 2"})
    void tentativeControlHasWholeActionSemantics(String body, boolean enabled, int value) {
        var model = load("shared int x = 0; shared int[] a = [0]; local int r = 0; local int[] b = [0]; thread 0 { atomic { " + body + " } }");
        var program = model.program(); var parent = program.initialConfiguration();
        assertEquals(enabled,program.threads().getFirst().steps().getFirst().enabled(parent.state()));
        var attempt = TransitionExecutor.execute(program,parent,0);
        assertEquals(enabled ? StepOutcome.ADVANCED : StepOutcome.BLOCKED,attempt.outcome());
        assertEquals(value,((DynamicState)attempt.successor().state()).getInt("x"));
        assertEquals(0,((DynamicState)parent.state()).getInt("x"));
        assertEquals(List.of(enabled ? 1 : 0),attempt.successor().programCounters());
    }

    /** Earlier failure/cutoff stays observable; a false guard masks only subsequent work.
     * @param strategy exact search strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void terminalOrderingAndRollbackArePreserved(Strategy strategy) {
        for (String terminal : List.of("assert(false);","x = 1 % 0;","while (true) limit 0 {}")) {
            for (boolean before : List.of(false,true)) {
                String guard = "atomic when (false) {}";
                String body = "x = 7; " + (before ? terminal + guard : guard + terminal);
                var model = load("shared int x = 0; thread 0 { atomic { " + body + " } }");
                var result = search(model,strategy,false);
                var expected = !before ? TraceOutcome.DEADLOCK : terminal.startsWith("while")
                    ? TraceOutcome.INCOMPLETE : TraceOutcome.VIOLATION;
                assertFalse(result.traces().isEmpty());
                for (var trace : result.traces()) {
                    assertEquals(expected,trace.outcome());
                    assertEquals(expected == TraceOutcome.VIOLATION ? List.of(0) : List.of(),trace.threadIds());
                    var end = new TraceReplayer().replay(model.program(),trace);
                    assertEquals(0,((DynamicState)end.state()).getInt("x"));
                    if (expected == TraceOutcome.INCOMPLETE) {
                        assertEquals(CutoffEvidence.Reason.LOOP_LIMIT,trace.cutoff().reason());
                        assertEquals(0,trace.cutoff().instructionIndex());
                    } else if (expected == TraceOutcome.VIOLATION) {
                        assertEquals(terminal.startsWith("assert") ? FailureEvidence.Kind.ASSERTION
                            : FailureEvidence.Kind.RUNTIME_ERROR,trace.failure().kind());
                        var minimal = new DeltaDebugger().minimize(model.program(),trace,TraceOutcome.VIOLATION,null);
                        assertEquals(trace.evidence(),minimal.evidence());
                    }
                }
            }
        }
    }

    /** Reached guard errors retain their inner span while false outer guards mask the whole body. */
    @Test void guardErrorsRetainOriginalLocationAndOuterGuardPriority() {
        var model = new SourceProgramLoader().load("program error {\nshared int x = 0;\nthread 0 { atomic { x = 7;\n"
            + "atomic when (1 % (x - x) == 0) {}\n} } }");
        assertEquals(List.of(0),model.program().initialConfiguration().enabledThreadIds());
        var trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        assertEquals(FailureEvidence.Category.MODULO_BY_ZERO,trace.failure().category());
        assertEquals(FailureEvidence.Phase.STEP,trace.failure().phase());
        assertEquals(4,trace.failure().source().line()); assertEquals(1,trace.failure().source().column());
        assertEquals(0,trace.evidence().executedSteps().getFirst().instructionIndex());
        assertEquals(0,((DynamicState)new TraceReplayer().replay(model.program(),trace).state()).getInt("x"));
        var masked = load("shared int x = 0; thread 0 { atomic when (false) { atomic when (1 % x == 0) {} } }");
        assertEquals(List.of(),masked.program().initialConfiguration().enabledThreadIds());
    }

    /** Complete private guard dependencies and semantic shapes support all exact symmetry strategies.
     * @param strategy selected built-in search
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void certifiedSymmetryPreservesGuardedOutcomes(Strategy strategy) {
        for (String condition : List.of("local.r < 2","local.r == 0")) {
            String body = "atomic { local.r = x; atomic when (" + condition + ") { x = local.r + 1; } }";
            var model = load("shared int x = 0; local int r = 0; thread 0 { " + body + " } thread 1 { " + body
                + " } invariant always (x <= 2);");
            var expected = condition.contains("<") ? TraceOutcome.COMPLETED : TraceOutcome.DEADLOCK;
            var plain = search(model,strategy,false); var reduced = search(model,strategy,true);
            for (var result : List.of(plain,reduced)) {
                assertFalse(result.traces().isEmpty());
                for (var trace : result.traces()) {
                    assertEquals(expected,trace.outcome());
                    var end = new TraceReplayer().replay(model.program(),trace,model.invariant().orElseThrow());
                    assertEquals(expected == TraceOutcome.COMPLETED ? 2 : 1,((DynamicState)end.state()).getInt("x"));
                }
            }
            Step action = model.program().threads().getFirst().steps().getFirst();
            assertTrue(action.reads().contains(MemoryLocation.of("x")));
            assertTrue(action.writes().contains(MemoryLocation.of("x")));
        }
    }

    /** Private guard checks share the existing inclusive work, expression and source-depth budgets. */
    @Test void guardedBodiesCannotHideWorkOrExpressionBudget() {
        String prefix = "shared int x = 0; thread 0 { atomic { atomic when (true) { ";
        assertNotNull(load(prefix + "x = 1;".repeat(63) + " } } }"));
        assertThrows(RegistryException.class,() -> load(prefix + "x = 1;".repeat(64) + " } } }"));
        String expression = "(" + sum(1249) + " == " + sum(1249) + ")";
        String body = "atomic when (" + expression + ") { x = 1; }";
        String program = "shared int x = 0; thread 0 { atomic { ";
        assertNotNull(load(program + body + " } } invariant final (x == 1);"));
        assertThrows(RegistryException.class,() -> load(program + "atomic when (true) { " + body
            + " } } } invariant final (x == 1);"));
        assertThrows(RegistryException.class,() -> load("shared int x = 0; helper unused { atomic { atomic when (true) { "
            + "x = 1;".repeat(65) + " } } } thread 0 {}"));
        assertNotNull(load("shared int x = 0; thread 0 { " + "atomic when (true) {".repeat(14) + "}".repeat(14) + " }"));
        assertThrows(RegistryException.class,() -> load("shared int x = 0; thread 0 { "
            + "atomic when (true) {".repeat(15) + "}".repeat(15) + " }"));
        assertEquals(64,load("shared int x = 0; thread 0 { "
            + "atomic { atomic when (true) {} }".repeat(64) + " }").program().threads().getFirst().steps().size());
        assertThrows(RegistryException.class,() -> load("shared int x = 0; helper unused { atomic { atomic when (1) {} } } thread 0 {}"));
    }

    /** Builds a balanced sum without exceeding expression depth; its literal leaves drive the budget.
     * @param leaves positive number of literal leaves
     * @return sum with leaves literals and leaves minus one binary operators
     */
    private static String sum(int leaves) {
        if (leaves == 1) return "1";
        int left = leaves / 2;
        return "(" + sum(left) + "+" + sum(leaves - left) + ")";
    }

    /** Wraps initialized declarations and owners into one bounded source input.
     * @param body declarations, helper/owner definitions and optional property
     * @return compiled trusted source model
     * @throws RegistryException for invalid source
     */
    private static BenchmarkProgram load(String body) {
        return new SourceProgramLoader().load("program guarded { " + body + " }");
    }

    /** Runs a fresh built-in strategy with its actual compiler property and optional orbit store.
     * @param model immutable compiled program/property
     * @param strategy exact search
     * @param symmetry whether to bind checked source orbit membership
     * @return real traversal evidence
     * @throws IllegalArgumentException if symmetry eligibility fails
     */
    private static DfsResult search(BenchmarkProgram model, Strategy strategy, boolean symmetry) {
        var program = model.program(); var invariant = model.invariant().orElse(null);
        StateStore store = symmetry ? SymmetryStateStore.forRun(program,invariant,strategy) : null;
        return switch (strategy) {
            case DFS -> new DfsExplorer().explore(program,invariant,store,null);
            case STATIC_POR -> new StaticPorExplorer().explore(program,invariant,store,null);
            case DPOR -> new DporExplorer().explore(program,invariant,store,null);
            case CONTEXT_BOUNDED -> new ContextBoundedExplorer().explore(program,invariant,store,null,20);
        };
    }
}
