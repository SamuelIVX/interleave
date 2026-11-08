// Scalar helper capture, finite lifetime, return timing and source-evidence contracts.
package dev.samhb.interleave.format;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.dsl.*;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.minimize.DeltaDebugger;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies scalar helper values through real compilation, search and replay. */
class SourceHelperValuesTest {
    /** Two calls have six literal actions, result five, and cleared formal slots.
     * @param strategy exact strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void scalarCaptureBodyReturnAndCleanupAreOrdinaryActions(Strategy strategy) {
        var model = load("shared int x = 0; local int result = 0; helper add(int amount) -> int { "
            + "atomic { x = x + amount; } return x; } thread 0 { call add(2) -> local.result; "
            + "call add(3) -> local.result; } invariant final (x == 5);");
        assertEquals(6,model.program().threads().getFirst().steps().size());
        var result = search(model,strategy,false);
        assertEquals(7,result.statesExplored());
        assertFalse(result.traces().isEmpty());
        for (var trace : result.traces()) {
            assertEquals(TraceOutcome.COMPLETED,trace.outcome());
            assertEquals(List.of(0,1,2,3,4,5),trace.evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
            var end = (DynamicState)new TraceReplayer().replay(model.program(),trace,model.invariant().orElseThrow()).state();
            assertEquals(5,end.getInt("x")); assertEquals(5,end.getLocalInt(0,"result"));
            assertCleared(end,Set.of("result"));
        }
    }

    /** Arguments read entry values while return expressions read completion-time state.
     * @param strategy exact strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void valuesAreCapturedOnceAndReturnsEvaluateAtCompletion(Strategy strategy) {
        var model = load("shared int x = 1; shared int result = 0; helper calculate(int value) -> int { "
            + "atomic { x = 9; } return x + value; } thread 0 { call calculate(x) -> result; } invariant final (result == 10);");
        var traces = search(model,strategy,false).traces();
        assertFalse(traces.isEmpty());
        for (var trace : traces) {
            assertEquals(TraceOutcome.COMPLETED,trace.outcome());
            var end = (DynamicState)new TraceReplayer().replay(model.program(),trace,model.invariant().orElseThrow()).state();
            assertEquals(9,end.getInt("x")); assertEquals(10,end.getInt("result")); assertCleared(end,Set.of());
        }
    }

    /** Nested acyclic calls retain outer values and reused frames reset between loop iterations. */
    @Test void nestedAndRepeatedCallsHaveDisjointFiniteSlots() {
        var model = load("shared int x = 0; local int temp = 0; local int result = 0; "
            + "helper increment(int amount) -> int { return amount + 1; } "
            + "helper twice(int value) -> int { call increment(value) -> local.temp; "
            + "call increment(local.temp) -> local.temp; return local.temp; } "
            + "thread 0 { repeat 2 { call twice(local.result) -> local.result; } }");
        assertEquals(12,model.program().threads().getFirst().steps().size());
        var trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        assertEquals(TraceOutcome.COMPLETED,trace.outcome());
        assertEquals(12,trace.length());
        assertTrue(trace.evidence().executedSteps().get(1).label().contains("call twice"));
        assertTrue(trace.evidence().executedSteps().get(1).label().contains("call increment"));
        var end = (DynamicState)new TraceReplayer().replay(model.program(),trace).state();
        assertEquals(4,end.getLocalInt(0,"result")); assertEquals(4,end.getLocalInt(0,"temp"));
        assertCleared(end,Set.of("result","temp"));
    }

    /** Boolean values, contextual return identifiers, empty calls and scalar targets remain typed. */
    @Test void booleanAndEmptyCallsPreserveLegacyAndContextualNames() {
        var model = load("shared bool ready = false; shared int return = 0; local bool result = false; "
            + "helper none {} helper negate(bool flag) -> bool { return !flag; } "
            + "helper forty() -> int { return 40; } thread 0 { call none(); call negate(true) -> ready; call negate(false) -> ready; "
            + "call negate(ready && true) -> local.result; call forty() -> return; } invariant final (ready && return == 40);");
        assertEquals(7,model.program().threads().getFirst().steps().size());
        var trace = new DfsExplorer().explore(model.program(),model.invariant().orElseThrow()).traces().getFirst();
        var end = (DynamicState)new TraceReplayer().replay(model.program(),trace,model.invariant().orElseThrow()).state();
        assertTrue(end.getBool("ready")); assertFalse(end.getLocalBool(0,"result")); assertEquals(40,end.getInt("return"));
        assertCleared(end,Set.of("result"));
    }

    /** Scalar indexed reads and formal-bound shared/local array indices retain existing semantics. */
    @Test void scalarArgumentsBindInsideOrdinaryArrayExpressionsAndTargets() {
        var model = load("shared int[] data = [4,5]; local int[] own = [0,0]; local int result = 0; "
            + "helper put(int index, int amount) -> int { atomic { data[index] = amount; "
            + "local.own[index] = data[index] + 1; } return local.own[index]; } "
            + "thread 0 { call put(1,data[0] + 3) -> local.result; }");
        for (var strategy : Strategy.values()) {
            var trace = search(model,strategy,false).traces().getFirst();
            assertEquals(TraceOutcome.COMPLETED,trace.outcome());
            var end = (DynamicState)new TraceReplayer().replay(model.program(),trace).state();
            assertArrayEquals(new int[]{4,7},end.getArray("data"));
            assertArrayEquals(new int[]{0,8},end.getLocalArray(0,"own"));
            assertEquals(8,end.getLocalInt(0,"result")); assertCleared(end,Set.of("own","result"));
        }
    }

    /** Parameterized void calls retain both actual callsite spans, including their cleanup exit. */
    @Test void voidCaptureAndCleanupRetainLiteralCallsiteSpans() {
        var model = new SourceProgramLoader().load("program spans {\nshared int x = 0;\nhelper take(int value) {}\nthread 0 {\ncall take(7);\n}\n}");
        var trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        assertEquals(TraceOutcome.COMPLETED,trace.outcome()); assertEquals(List.of(0,0),trace.threadIds());
        assertEquals(List.of(0,1),trace.evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
        for (var step : trace.evidence().executedSteps()) assertEquals(new SourceLocation("<source>",5,1,5,14),step.source());
        var end = (DynamicState)new TraceReplayer().replay(model.program(),trace).state();
        assertCleared(end,Set.of());
    }

    /** An unused second argument still evaluates; failure rolls back the entire capture action. */
    @Test void failingCaptureRetainsArgumentSpanAndNoPartialValues() {
        var model = new SourceProgramLoader().load("program capture {\nshared int x = 0;\nhelper ignore(int first, int second) {}\n"
            + "thread 0 { call ignore(7,\n1 % 0); }\n}");
        var trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        assertEquals(TraceOutcome.VIOLATION,trace.outcome()); assertEquals(List.of(0),trace.threadIds());
        assertEquals(FailureEvidence.Category.MODULO_BY_ZERO,trace.failure().category());
        assertEquals(5,trace.failure().source().line()); assertEquals(1,trace.failure().source().column());
        var end = (DynamicState)new TraceReplayer().replay(model.program(),trace).state();
        assertEquals(0,end.getInt("x")); assertCleared(end,Set.of());
    }

    /** Return errors retain definition/call context and committed capture, without clearing early. */
    @Test void failingReturnRetainsCommittedFrameAndAuthenticReplay() {
        var model = new SourceProgramLoader().load("program failure {\nshared int x = 0;\nhelper stop(int value) -> int {\n"
            + "return 1 % (value - value);\n}\nthread 0 { call stop(7) -> x; }\n}");
        var trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        assertEquals(List.of(0,0),trace.threadIds());
        assertEquals(List.of(StepOutcome.ADVANCED,StepOutcome.ASSERTION_FAILED),trace.outcomes());
        assertEquals(4,trace.failure().source().line()); assertEquals(1,trace.failure().source().column());
        assertTrue(trace.evidence().executedSteps().getLast().label().contains("call stop"));
        var minimal = new DeltaDebugger().minimize(model.program(),trace,TraceOutcome.VIOLATION,null);
        assertEquals(trace.evidence(),minimal.evidence());
        var end = (DynamicState)new TraceReplayer().replay(model.program(),minimal).state();
        assertEquals(0,end.getInt("x"));
        assertEquals(1,end.decl().locals().size());
        assertEquals(7,end.getLocalInt(0,end.decl().locals().getFirst().name()));
    }

    /** A body cutoff retains the committed capture and omits the actual next helper PC. */
    @Test void bodyCutoffsRetainActiveValuesAndOmittedActionIdentity() {
        var model = new SourceProgramLoader().load("program cutoff {\nshared int x = 0;\nhelper bounded(int allowance) {\n"
            + "while (true) limit 0 {}\n}\nthread 0 { call bounded(7); }\n}");
        var trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        assertEquals(TraceOutcome.INCOMPLETE,trace.outcome()); assertEquals(List.of(0),trace.threadIds());
        assertEquals(List.of(StepOutcome.ADVANCED),trace.outcomes());
        assertEquals(CutoffEvidence.Reason.LOOP_LIMIT,trace.cutoff().reason());
        assertEquals(1,trace.cutoff().instructionIndex()); assertEquals(4,trace.cutoff().source().line());
        var end = (DynamicState)new TraceReplayer().replay(model.program(),trace).state();
        assertEquals(7,end.getLocalInt(0,end.decl().locals().getFirst().name()));
    }

    /** A captured value remains fixed while its guarded helper body waits for another owner.
     * @param strategy exact strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void waitingBodiesRetainBothEntryValuesInStateKeys(Strategy strategy) {
        var model = load("shared int x = 1; shared bool ready = false; local int result = 0; "
            + "helper wait(int value) -> int { atomic when (ready) {} return value; } "
            + "thread 0 { call wait(x) -> local.result; } thread 1 { atomic { x = 9; ready = true; } }");
        var result = search(model,strategy,false);
        var values = new HashSet<Integer>();
        for (var trace : result.traces()) {
            assertEquals(TraceOutcome.COMPLETED,trace.outcome());
            var end = (DynamicState)new TraceReplayer().replay(model.program(),trace).state();
            values.add(end.getLocalInt(0,"result")); assertCleared(end,Set.of("result"));
        }
        assertEquals(Set.of(1,9),values);
    }

    /** Simultaneous frames use per-owner storage and exact/symmetry keys retain distinct outcomes.
     * @param strategy exact strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void concurrentCapturesPreserveAllResultOrbits(Strategy strategy) {
        var model = load("shared int x = 0; local int result = 0; helper identity(int value) -> int { return value; } "
            + "thread 0 { call identity(x) -> local.result; } thread 1 { call identity(x) -> local.result; } "
            + "thread 2 { atomic { x = 1; } atomic { x = 0; } }");
        for (boolean symmetry : List.of(false,true)) {
            var pairs = new HashSet<List<Integer>>();
            for (var trace : search(model,strategy,symmetry).traces()) {
                assertEquals(TraceOutcome.COMPLETED,trace.outcome());
                var end = (DynamicState)new TraceReplayer().replay(model.program(),trace).state();
                var pair = new ArrayList<>(List.of(end.getLocalInt(0,"result"),end.getLocalInt(1,"result")));
                if (symmetry) Collections.sort(pair);
                pairs.add(List.copyOf(pair)); assertCleared(end,Set.of("result"));
            }
            assertEquals(symmetry ? Set.of(List.of(0,0),List.of(0,1),List.of(1,1))
                : Set.of(List.of(0,0),List.of(0,1),List.of(1,0),List.of(1,1)),pairs);
        }
    }

    /** User spellings cannot collide with or forge generated slots, and helper scopes do not leak. */
    @Test void generatedNamesAreHygienicAndFormalsAreReadOnly() {
        var model = load("shared int x = 0; local int helper_identity_value_0 = 99; "
            + "helper identity(int value) -> int { return value; } thread 0 { call identity(7) -> x; }");
        var trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        var end = (DynamicState)new TraceReplayer().replay(model.program(),trace).state();
        assertEquals(7,end.getInt("x")); assertEquals(99,end.getLocalInt(0,"helper_identity_value_0"));
        assertCleared(end,Set.of("helper_identity_value_0"));
        var scalarTarget = assertThrows(RegistryException.class,() -> load("shared int[] items = [0]; "
            + "helper id(int value) -> int { return value; } thread 0 { call id(1) -> items[0]; }"));
        assertTrue(scalarTarget.getMessage().contains("Helper results require scalar targets"));
        var readOnly = assertThrows(RegistryException.class,() -> load("shared int x = 0; helper fixed(int value) { "
            + "atomic { value = 1; } } thread 0 {}"));
        assertTrue(readOnly.getMessage().contains("Helper parameters are read-only"));
        String helperName = "h" + "a".repeat(63), formalName = "p" + "b".repeat(63);
        var longNames = load("shared int x = 0; helper " + helperName + "(int " + formalName + ") -> int { return "
            + formalName + "; } thread 0 { call " + helperName + "(7) -> x; }");
        var longTrace = new DfsExplorer().explore(longNames.program()).traces().getFirst();
        assertCleared((DynamicState)new TraceReplayer().replay(longNames.program(),longTrace).state(),Set.of());
        assertThrows(RegistryException.class,() -> load("shared int x = 0; helper id(int value) -> int { return value; } "
            + "thread 0 { atomic { x = local.helper_id_value_0; } }"));
    }

    /** Frame, action and argument-expression costs retain the existing inclusive boundaries. */
    @Test void generatedActionsAndSlotsCountTowardExistingBudgets() {
        String locals = java.util.stream.IntStream.range(0,7).mapToObj(i -> "local int r" + i + " = 0;").collect(Collectors.joining());
        String helper = "helper id(int value) -> int { return value; } thread 0 { call id(7) -> x; }";
        assertEquals(8,((DynamicState)load("shared int x = 0; " + locals + helper).program().initialConfiguration().state()).decl().locals().size());
        assertThrows(RegistryException.class,() -> load("shared int x = 0; " + locals + "local int extra = 0; " + helper));
        assertEquals(64,load("shared int x = 0; helper maximum(int value) -> int { " + "atomic {}".repeat(62)
            + "return value; } thread 0 { call maximum(1) -> x; }").program().threads().getFirst().steps().size());
        assertThrows(RegistryException.class,() -> load("shared int x = 0; helper maximum(int value) -> int { "
            + "atomic {}".repeat(63) + "return value; } thread 0 {}"));
        String calls = "shared int x = 0; helper action(int value) { atomic {} } thread 0 { repeat ";
        assertEquals(63,load(calls + "21 { call action(1); } }").program().threads().getFirst().steps().size());
        assertThrows(RegistryException.class,() -> load(calls + "22 { call action(1); } }"));
        String expression = sum(1248);
        String twice = "shared int x = 0; helper id(int value) -> int { return value; } thread 0 { call id("
            + expression + ") -> x; call id(" + expression + ") -> x; }";
        assertNotNull(load(twice));
        assertThrows(RegistryException.class,() -> load(twice + " invariant final (true);"));
        assertTrue(load("shared int x = 0; helper none {} thread 0 { repeat 2147483647 { call none(); } }")
            .program().threads().getFirst().steps().isEmpty());
    }

    /** Invalid definitions/calls fail before expansion even when no invocation is reachable.
     * @param body source after an initialized shared int x
     */
    @ParameterizedTest @ValueSource(strings = {
        "helper f(int a, int a) {} thread 0 {}",
        "helper f(int x) {} thread 0 {}",
        "helper f(int a) { atomic { a = 1; } } thread 0 {}",
        "helper f(int a) -> bool { return a; } thread 0 {}",
        "helper f(int a) -> int {} thread 0 {}",
        "helper f(int a) -> int { if (true) { return a; } } thread 0 {}",
        "helper f(int a) -> int { return a; atomic {} } thread 0 {}",
        "helper f { return 1; } thread 0 {}",
        "helper f(int[] a) {} thread 0 {}",
        "helper f() -> int[] { return 1; } thread 0 {}",
        "helper f(int a) {} thread 0 { call f; }",
        "helper f(int a) {} thread 0 { call f(1,2); }",
        "helper f(int a) {} thread 0 { call f(true); }",
        "helper f(int a) -> int { return a; } thread 0 { call f(1); }",
        "helper f(int a) {} thread 0 { call f(1) -> x; }",
        "helper f(int a) -> bool { return true; } thread 0 { call f(1) -> x; }",
        "helper f(int a) -> int { return a; } thread 0 { atomic { call f(1) -> x; } }",
        "helper f(int a) { call f(a); } thread 0 {}",
        "helper f(int a) { call later(a); } helper later(int b) {} thread 0 {}",
        "helper f(int a) {} thread 0 { repeat 0 { call f(false); } }",
        "helper f(int a) {} thread 0 { atomic { x = a; } }",
        "shared int[] array = [0]; helper f(int a) -> int { return a; } thread 0 { call f(1) -> array[0]; }"})
    void invalidValueHelpersAreRejectedEvenWhenUnused(String body) {
        assertThrows(RegistryException.class,() -> load("shared int x = 0; " + body));
    }

    /** Verifies typed helper-owned slots reset for every original owner, ignoring user locals.
     * @param state final real committed state
     * @param visible declared user-local names
     */
    private static void assertCleared(DynamicState state, Set<String> visible) {
        for (var local : state.decl().locals()) if (!visible.contains(local.name())) {
            assertTrue(local.name().matches("[a-z][a-z0-9_]{0,63}"),"Generated slots retain valid bounded identifiers");
            for (int owner = 0; owner < state.threadCount(); owner++) {
                if (local.type() == FieldType.BOOL) assertFalse(state.getLocalBool(owner,local.name()));
                else assertEquals(0,state.getLocalInt(owner,local.name()));
            }
        }
    }

    /** Builds a balanced sum with independently counted leaves and binary operators.
     * @param leaves positive literal count
     * @return balanced sum using only literal ones
     */
    private static String sum(int leaves) {
        if (leaves == 1) return "1";
        int left = leaves / 2;
        return "(" + sum(left) + "+" + sum(leaves - left) + ")";
    }

    /** Compiles declarations, helpers and owners into a fresh finite source model.
     * @param body initialized declarations and helper/thread/property clauses
     * @return trusted source model
     * @throws RegistryException for invalid or over-budget source
     */
    private static BenchmarkProgram load(String body) {
        return new SourceProgramLoader().load("program values { " + body + " }");
    }

    /** Executes the actual compiler property under one built-in exact strategy.
     * @param model trusted program/property
     * @param strategy selected strategy
     * @param symmetry whether to use certified orbit membership
     * @return real positions and trace evidence
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
