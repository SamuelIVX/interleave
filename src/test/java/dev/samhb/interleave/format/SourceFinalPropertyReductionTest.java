/** Independent terminal-observation oracles and conservative fallback for final source reduction. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.dsl.DynamicState;
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

class SourceFinalPropertyReductionTest {
    /** Source property metadata distinguishes timing and retains every array/error input.
     * @param always which observation boundary the source requests
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void metadataSeparatesFinalGateFromStateOnlyObservations(boolean always) {
        var model = load("shared int index = 0; shared int[] values = [1]; thread 0 {}",
            always ? "always" : "final", "values[index] == 1 && (index == 0 || 1 % index == 0)");
        var property = model.invariant().orElseThrow();
        var expected = Optional.of(Set.of(MemoryLocation.of("values"), MemoryLocation.of("index")));
        assertEquals(always ? expected : Optional.empty(), property.observedLocations());
        assertEquals(always ? Optional.empty() : expected, property.finalObservedLocations());
        Set<MemoryLocation> locations = (always ? property.observedLocations() : property.finalObservedLocations()).orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> locations.add(MemoryLocation.of("other")));
        Invariant opaque = (s, c) -> property.holds(s, c);
        assertTrue(opaque.observedLocations().isEmpty());
        assertTrue(opaque.finalObservedLocations().isEmpty());
    }

    /** A terminal writer and two unrelated owners have 18 exact positions and a six-position serial path.
     * @param expression final postcondition, including a root-time error that must stay unobserved
     */
    @ParameterizedTest @ValueSource(strings = {"x == 1", "x == 2", "1 % x == 0", "false"})
    void finalPostconditionsReduceWithoutPrematureObservation(String expression) {
        var model = independent(expression);
        var property = model.invariant().orElseThrow();
        var dfs = new DfsExplorer().explore(model.program(), property);
        var por = new StaticPorExplorer().explore(model.program(), property);
        var dpor = new DporExplorer().explore(model.program(), property);
        assertEquals(18, dfs.statesExplored());
        assertEquals(6, por.statesExplored());
        assertTrue(dpor.statesExplored() < 18);
        Set<TraceOutcome> expected = Set.of((expression.equals("x == 2") || expression.equals("false")) ? TraceOutcome.VIOLATION : TraceOutcome.COMPLETED);
        for (DfsResult result : List.of(dfs, por, dpor)) {
            assertEquals(expected, outcomes(result));
            for (Trace trace : result.traces()) {
                Configuration end = replay(model, trace);
                assertTrue(end.allTerminated());
                assertEquals(1, ((DynamicState) end.state()).getInt("x"));
                assertEquals(5, trace.length());
            }
        }
        Invariant opaque = (s, c) -> property.holds(s, c);
        assertEquals(18, new StaticPorExplorer().explore(model.program(), opaque).statesExplored());
        assertEquals(18, new DporExplorer().explore(model.program(), opaque).statesExplored());
    }

    /** Competing last writes determine x=1 or x=2 independently of the unrelated owner. */
    @Test void bothFinalObservationsAndFailureWitnessesSurvive() {
        var model = load("shared int x = 0; shared int noise = 0;"
            + " thread 0 { atomic { x = 1; } } thread 1 { atomic { x = 2; } }"
            + " thread 2 { atomic { noise = 1; } atomic { noise = 2; } }", "final", "x == 1");
        for (DfsResult result : searches(model)) {
            assertEquals(Set.of(TraceOutcome.COMPLETED, TraceOutcome.VIOLATION), outcomes(result));
            var values = new HashSet<Integer>();
            for (Trace trace : result.traces()) values.add(((DynamicState) replay(model, trace).state()).getInt("x"));
            assertEquals(Set.of(1, 2), values);
        }
        Trace failure = new DporExplorer().explore(model.program(), model.invariant().orElseThrow()).traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.VIOLATION).findFirst().orElseThrow();
        Trace minimal = new DeltaDebugger().minimize(model.program(), failure, TraceOutcome.VIOLATION, model.invariant().orElseThrow());
        assertEquals(4, minimal.length());
        assertTrue(replay(model, minimal).allTerminated());
    }

    /** Error inputs are observations even when one possible final value succeeds.
     * @param expression array bounds or modulo error predicate
     */
    @ParameterizedTest @ValueSource(strings = {"values[index] == 0", "1 % index == 0"})
    void finalRuntimeErrorsPreserveBothLastWriterOutcomes(String expression) {
        var model = load("shared int index = 0; shared int[] values = [0]; shared int noise = 0;"
            + " thread 0 { atomic { index = 1; } } thread 1 { atomic { index = 0; } }"
            + " thread 2 { atomic { noise = 1; } atomic { noise = 2; } }", "final", expression);
        var category = expression.startsWith("values") ? FailureEvidence.Category.ARRAY_BOUNDS : FailureEvidence.Category.MODULO_BY_ZERO;
        for (DfsResult result : searches(model)) {
            assertEquals(Set.of(TraceOutcome.COMPLETED, TraceOutcome.VIOLATION), outcomes(result));
            for (Trace trace : result.traces()) {
                assertTrue(replay(model, trace).allTerminated());
                if (trace.outcome() == TraceOutcome.VIOLATION) {
                    assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, trace.failure().kind());
                    assertEquals(FailureEvidence.Phase.PROPERTY, trace.failure().phase());
                    assertEquals(category, trace.failure().category());
                }
            }
        }
    }

    /** A concurrent guard writer selects either the repeated success arm or a failing final value. */
    @Test void finalObservationSurvivesForwardBranchesAndRepeatExpansion() {
        var model = load("shared int flag = 0; shared int x = 0; shared int noise = 0;"
            + " thread 0 { if (flag == 0) { repeat 2 { atomic { x = x + 1; } } } else { atomic { x = 4; } } }"
            + " thread 1 { atomic { flag = 1; } } thread 2 { atomic { noise = 1; } }", "final", "x == 2");
        for (DfsResult result : searches(model)) {
            assertEquals(Set.of(TraceOutcome.COMPLETED, TraceOutcome.VIOLATION), outcomes(result));
            var values = new HashSet<Integer>();
            for (Trace trace : result.traces()) values.add(((DynamicState) replay(model, trace).state()).getInt("x"));
            assertEquals(Set.of(2, 4), values);
        }
    }

    /** Empty final predicates can succeed, fail or throw only at the terminal observation point.
     * @return literal predicates and independent expected classifications/categories
     */
    static Stream<Arguments> emptyPredicates() {
        return Stream.of(Arguments.of("true", TraceOutcome.COMPLETED, null),
            Arguments.of("false", TraceOutcome.VIOLATION, null),
            Arguments.of("1 % 0 == 0", TraceOutcome.VIOLATION, FailureEvidence.Category.MODULO_BY_ZERO),
            Arguments.of("true || 1 % 0 == 0", TraceOutcome.COMPLETED, null));
    }

    /** Constant final predicates retain the completion gate even with empty read footprints.
     * @param expression final predicate
     * @param expected terminal class
     * @param category expected runtime category, or null for success/falsehood
     */
    @ParameterizedTest @MethodSource("emptyPredicates")
    void emptyCompletionRetainsFinalDiagnostics(String expression, TraceOutcome expected, FailureEvidence.Category category) {
        var model = load("shared int x = 0; thread 0 {}", "final", expression);
        assertEquals(Optional.of(Set.of()), model.invariant().orElseThrow().finalObservedLocations());
        for (DfsResult result : searches(model)) {
            assertEquals(Set.of(expected), outcomes(result));
            for (Trace trace : result.traces()) {
                assertEquals(0, trace.length());
                assertTrue(replay(model, trace).allTerminated());
                if (expected == TraceOutcome.VIOLATION) {
                    assertEquals(FailureEvidence.Phase.PROPERTY, trace.failure().phase());
                    assertEquals(category, trace.failure().category());
                }
            }
        }
    }

    /** A non-completing outcome must not evaluate even a false or throwing final predicate.
     * @param expression predicate that would fail if observed prematurely
     */
    @ParameterizedTest @ValueSource(strings = {"false", "1 % 0 == 0"})
    void deadlockAndCutoffDoNotBecomeFinalPropertyFailures(String expression) {
        var bodies = Map.of(TraceOutcome.DEADLOCK, "thread 0 { atomic when (false) { x = 1; } }",
            TraceOutcome.INCOMPLETE, "thread 0 { while (true) limit 0 {} }");
        for (var entry : bodies.entrySet()) {
            var model = load("shared int x = 0; shared int noise = 0; " + entry.getValue()
                + " thread 1 { atomic { noise = 1; } atomic { noise = 2; } }", "final", expression);
            for (DfsResult result : searches(model)) {
                assertEquals(Set.of(entry.getKey()), outcomes(result));
                for (Trace trace : result.traces()) {
                    assertNull(trace.failure());
                    assertFalse(replay(model, trace).allTerminated());
                }
            }
        }
    }

    /** Hand schedules put the competing reset before the body, after exit or before the limit test. */
    @Test void boundedLoopRetainsSuccessFinalFailureAndCutoff() {
        var model = load("shared int x = 0; shared int noise = 0;"
            + " thread 0 { while (x == 0) limit 1 { atomic { x = 1; } } }"
            + " thread 1 { atomic { x = 0; } } thread 2 { atomic { noise = 1; } atomic { noise = 2; } }", "final", "x == 1");
        for (DfsResult result : searches(model)) {
            assertEquals(Set.of(TraceOutcome.COMPLETED, TraceOutcome.VIOLATION, TraceOutcome.INCOMPLETE), outcomes(result));
            for (Trace trace : result.traces()) replay(model, trace);
        }
    }

    /** Both callbacks remain opaque when they depend on configurations beyond final expression reads. */
    @Test void unknownConfigurationCallbackStillMatchesExactTraversal() {
        var model = independent("true");
        Invariant callback = (s, c) -> !c.programCounters().equals(List.of(0, 1, 0));
        var exact = new DfsExplorer().explore(model.program(), callback);
        for (DfsResult reduced : List.of(new StaticPorExplorer().explore(model.program(), callback),
                new DporExplorer().explore(model.program(), callback))) {
            assertEquals(exact.states().keySet(), reduced.states().keySet());
            assertEquals(outcomes(exact), outcomes(reduced));
            for (Trace trace : reduced.traces()) new TraceReplayer().replay(model.program(), trace, callback);
        }
    }

    /** The separate final contract does not opt legacy programs into a new reduction proof. */
    @Test void legacyFinalMetadataStillBranchesExhaustively() {
        var program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true), new WriteFlagStep(0, false))),
            new ModelThread(1, List.of(new WriteFlagStep(1, true), new WriteFlagStep(1, false)))));
        Invariant property = new Invariant() {
            /** A constant true final predicate satisfies the gate contract. */
            @Override public boolean holds(SharedState state, Configuration config) { return true; }
            /** Explicit final metadata must still be ignored outside source programs. */
            @Override public Optional<Set<MemoryLocation>> finalObservedLocations() { return Optional.of(Set.of()); }
        };
        assertEquals(9, new DfsExplorer().explore(program, property).statesExplored());
        assertEquals(9, new StaticPorExplorer().explore(program, property).statesExplored());
        assertEquals(9, new DporExplorer().explore(program, property).statesExplored());
    }

    /** A source fixture with one postcondition writer and two independent two-action owners.
     * @param expression final predicate over x
     * @return public compiled source model
     */
    private static BenchmarkProgram independent(String expression) {
        return load("shared int x = 0; shared int y = 0; shared int z = 0;"
            + " thread 0 { atomic { x = 1; } } thread 1 { atomic { y = 1; } atomic { y = 2; } }"
            + " thread 2 { atomic { z = 1; } atomic { z = 2; } }", "final", expression);
    }

    /** Loads a complete source model through the public compiler entry point.
     * @param declarationsAndThreads supported source body
     * @param timing always or final
     * @param expression boolean predicate
     * @return compiled model and diagnostic property
     */
    private static BenchmarkProgram load(String declarationsAndThreads, String timing, String expression) {
        return new SourceProgramLoader().load("program final_contract { " + declarationsAndThreads
            + " invariant " + timing + " (" + expression + "); }");
    }

    /** Returns all exact strategy results relevant to this new source contract.
     * @param model compiled source fixture
     * @return DFS and both reduced searches
     */
    private static List<DfsResult> searches(BenchmarkProgram model) {
        var property = model.invariant().orElseThrow();
        return List.of(new DfsExplorer().explore(model.program(), property),
            new StaticPorExplorer().explore(model.program(), property), new DporExplorer().explore(model.program(), property));
    }

    /** Projects actual terminal classifications without relying on implementation state keys.
     * @param result completed exact search
     * @return observed terminal classes
     */
    private static Set<TraceOutcome> outcomes(DfsResult result) {
        return result.traces().stream().map(Trace::outcome).collect(Collectors.toSet());
    }

    /** Validates source identities, outcomes, terminal classification and failure/cutoff evidence.
     * @param model original source model
     * @param trace returned witness
     * @return last committed configuration
     */
    private static Configuration replay(BenchmarkProgram model, Trace trace) {
        return new TraceReplayer().replay(model.program(), trace, model.invariant().orElseThrow());
    }
}
