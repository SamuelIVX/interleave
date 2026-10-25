/** Literal evidence for visible-step sleep refresh and dependent guard behavior. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static dev.samhb.interleave.core.StepOutcome.*;
import static org.junit.jupiter.api.Assertions.*;

class DporVisibilityContractTest {
    /** A visible write wakes an independent sleeper needed before the owner's later dependent read. */
    @Test
    void visibleWriteRetainsTheConcreteFutureReadViolation() {
        var model = new SourceProgramLoader().load("""
            program visibility { shared int marker = 0; shared int phase = 0;
              shared int input = 0; shared int observed = 0;
              thread 0 { atomic { marker = 1; } }
              thread 1 { atomic { phase = 1; } atomic { observed = input; } }
              thread 2 { atomic { input = 1; } }
              invariant always (phase >= 0 && observed == 0);
            }
            """);
        Invariant property = model.invariant().orElseThrow();
        assertEquals(Set.of(MemoryLocation.of("phase"), MemoryLocation.of("observed")),
            property.observedLocations().orElseThrow());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        List<VisiblePosition> expectedPositions = List.of(
            new VisiblePosition(List.of(0, 0, 0), 0, 0, 0, 0), new VisiblePosition(List.of(1, 0, 0), 1, 0, 0, 0),
            new VisiblePosition(List.of(1, 1, 0), 1, 1, 0, 0), new VisiblePosition(List.of(1, 2, 0), 1, 1, 0, 0),
            new VisiblePosition(List.of(1, 2, 1), 1, 1, 1, 0), new VisiblePosition(List.of(1, 1, 1), 1, 1, 1, 0),
            new VisiblePosition(List.of(1, 2, 1), 1, 1, 1, 1), new VisiblePosition(List.of(0, 1, 0), 0, 1, 0, 0),
            new VisiblePosition(List.of(0, 2, 0), 0, 1, 0, 0), new VisiblePosition(List.of(0, 2, 1), 0, 1, 1, 0),
            new VisiblePosition(List.of(0, 1, 1), 0, 1, 1, 0), new VisiblePosition(List.of(0, 2, 1), 0, 1, 1, 1),
            new VisiblePosition(List.of(0, 0, 1), 0, 0, 1, 0));
        assertEquals(13, result.statesExplored());
        assertEquals(13, result.states().size());
        assertEquals(expectedPositions, observer.positions.stream().map(DporVisibilityContractTest::visiblePosition).toList());
        assertEquals(Set.copyOf(expectedPositions), result.states().values().stream()
            .map(DporVisibilityContractTest::visiblePosition).collect(Collectors.toSet()));
        List<Witness> expected = List.of(
            new Witness(List.of(0, 1, 1, 2), List.of(ADVANCED, ADVANCED, ADVANCED, ADVANCED), TraceOutcome.COMPLETED),
            new Witness(List.of(0, 1, 2, 1), List.of(ADVANCED, ADVANCED, ADVANCED, ADVANCED), TraceOutcome.VIOLATION),
            new Witness(List.of(1, 2, 1), List.of(ADVANCED, ADVANCED, ADVANCED), TraceOutcome.VIOLATION));
        assertEquals(expected, observer.witnesses);
        assertEquals(expected, witnesses(result));
        for (Trace trace : result.traces()) {
            if (trace.outcome() == TraceOutcome.VIOLATION) {
                assertEquals(FailureEvidence.Kind.INVARIANT, trace.failure().kind());
                assertEquals(FailureEvidence.Phase.PROPERTY, trace.failure().phase());
            }
        }
        replay(model.program(), result, property);
    }

    /** Complete guard footprints preserve disabling and modeled guard errors without hidden mutable behavior.
     * @param guard pure source condition reading x
     * @param firstPrefix literal terminal prefix after owner zero's write
     * @param firstOutcomes literal outcomes, including the failed attempt only for an erroring guard
     * @param firstOutcome deadlock or a classified modeled violation
     */
    @ParameterizedTest
    @MethodSource("guardCases")
    void dependentGuardsRetainLiteralEvidence(String guard, List<Integer> firstPrefix,
            List<StepOutcome> firstOutcomes, TraceOutcome firstOutcome) {
        var model = new SourceProgramLoader().load("""
            program guarded { shared int x = 0; shared int y = 0;
              thread 0 { atomic { x = 1; } }
              thread 1 { atomic when (%s) { y = 1; } }
              invariant always (true);
            }
            """.formatted(guard));
        Invariant property = model.invariant().orElseThrow();
        assertTrue(property.observedLocations().orElseThrow().isEmpty());
        assertEquals(Set.of(MemoryLocation.of("x")), model.program().threads().get(0).steps().getFirst().writes());
        assertEquals(Set.of(MemoryLocation.of("x")), model.program().threads().get(1).steps().getFirst().reads());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        List<GuardPosition> expectedPositions = List.of(
            new GuardPosition(List.of(0, 0), 0, 0), new GuardPosition(List.of(1, 0), 1, 0),
            new GuardPosition(List.of(0, 1), 0, 1), new GuardPosition(List.of(1, 1), 1, 1));
        assertEquals(4, result.statesExplored());
        assertEquals(4, result.states().size());
        assertEquals(expectedPositions, observer.positions.stream().map(DporVisibilityContractTest::guardPosition).toList());
        assertEquals(Set.copyOf(expectedPositions), result.states().values().stream()
            .map(DporVisibilityContractTest::guardPosition).collect(Collectors.toSet()));
        List<Witness> expected = List.of(new Witness(firstPrefix, firstOutcomes, firstOutcome),
            new Witness(List.of(1, 0), List.of(ADVANCED, ADVANCED), TraceOutcome.COMPLETED));
        assertEquals(expected, observer.witnesses);
        assertEquals(expected, witnesses(result));
        if (firstOutcome == TraceOutcome.VIOLATION) {
            FailureEvidence failure = result.traces().getFirst().failure();
            assertNotNull(failure);
            assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, failure.kind());
            assertEquals(FailureEvidence.Phase.STEP, failure.phase());
            assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, failure.category());
            assertEquals(new SourceLocation("<source>", 3, 14, 3, 59), failure.source());
        } else {
            assertNull(result.traces().getFirst().failure());
        }
        replay(model.program(), result, property);
    }

    /** Supplies real source guards whose outcomes differ only after the other owner's write.
     * @return two literal guard/outcome cases with complete x-read dependencies
     */
    private static Stream<Arguments> guardCases() {
        return Stream.of(
            Arguments.of("x == 0", List.of(0), List.of(ADVANCED), TraceOutcome.DEADLOCK),
            Arguments.of("x == 0 || 1 % 0 == 0", List.of(0, 1), List.of(ADVANCED, ASSERTION_FAILED), TraceOutcome.VIOLATION));
    }

    /** Captures every shared value in the visibility fixture without canonical-key or map-order assumptions.
     * @param config actual visited source position
     * @return literal-comparable counters and shared values
     */
    private static VisiblePosition visiblePosition(Configuration config) {
        var state = (DynamicState) config.state();
        return new VisiblePosition(config.programCounters(), state.getInt("marker"), state.getInt("phase"),
            state.getInt("input"), state.getInt("observed"));
    }

    /** Captures both shared values in the dependent-guard fixture.
     * @param config actual visited source position
     * @return literal-comparable counters and shared values
     */
    private static GuardPosition guardPosition(Configuration config) {
        var state = (DynamicState) config.state();
        return new GuardPosition(config.programCounters(), state.getInt("x"), state.getInt("y"));
    }

    /** Projects actual returned evidence independently of callback storage.
     * @param result finished public exploration
     * @return recorded choices, outcomes and terminal classifications
     */
    private static List<Witness> witnesses(DfsResult result) {
        return result.traces().stream().map(trace -> new Witness(trace.threadIds(), trace.outcomes(), trace.outcome())).toList();
    }

    /** Replays the actual source evidence without reconstructing or repairing its metadata.
     * @param program compiler-produced source model
     * @param result explorer evidence
     * @param property actual exploration predicate, including its complete observations
     */
    private static void replay(Program program, DfsResult result, Invariant property) {
        for (Trace trace : result.traces()) new TraceReplayer().replay(program, trace, property);
    }

    /** Visibility position oracle.
     * @param counters committed owner counters
     * @param marker invisible prefix value
     * @param phase visible first-write value
     * @param input hidden writer's value
     * @param observed future read's recorded value
     */
    private record VisiblePosition(List<Integer> counters, int marker, int phase, int input, int observed) {}

    /** Guard position oracle.
     * @param counters committed owner counters
     * @param x guard input written by owner zero
     * @param y value written only by a successful guarded action
     */
    private record GuardPosition(List<Integer> counters, int x, int y) {}

    /** Independent terminal witness oracle.
     * @param threads recorded owner choices
     * @param outcomes actual action results
     * @param outcome terminal classification
     */
    private record Witness(List<Integer> threads, List<StepOutcome> outcomes, TraceOutcome outcome) {}

    /** Non-mutating observer collecting visited configurations and independent terminal value tuples. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Visited configurations, never mutated by this observer. */
        private final List<Configuration> positions = new ArrayList<>();
        /** Values extracted from actual terminal callbacks. */
        private final List<Witness> witnesses = new ArrayList<>();

        /** Records the actual visited position without modifying its values.
         * @param config newly visited source configuration
         */
        @Override public void onStateVisited(Configuration config) { positions.add(config); }

        /** Records actual choices, outcomes and classification rather than reconstructing evidence.
         * @param trace newly emitted terminal witness
         */
        @Override public void onTraceCreated(Trace trace) {
            witnesses.add(new Witness(trace.threadIds(), trace.outcomes(), trace.outcome()));
        }
    }
}
