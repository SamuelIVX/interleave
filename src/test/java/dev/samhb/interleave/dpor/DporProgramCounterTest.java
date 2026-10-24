/** Nonzero-counter metadata and execution evidence across DPOR traversal paths. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.samhb.interleave.core.StepOutcome.*;
import static org.junit.jupiter.api.Assertions.*;

class DporProgramCounterTest {
    /** Different first/current footprints must not fabricate an extra visit to one canonical position.
     * @param observed whether to supply the compiler's known-empty observation set
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void currentFootprintRetainsExactlyTheEnumeratedPositions(boolean observed) {
        var model = new SourceProgramLoader().load("""
            program counters { shared int x = 0; shared int y = 0;
              thread 0 { atomic { x = 1; } atomic { y = 1; } }
              thread 1 { atomic { if (x == 0) { y = 1; } } atomic {} }
              invariant always (true);
            }
            """);
        Invariant property = observed ? model.invariant().orElseThrow() : null;
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        // Nine counter pairs, with two y values at each of [1,1] and [1,2], give eleven positions.
        List<Position> expected = List.of(
            new Position(List.of(0, 0), 0, 0), new Position(List.of(1, 0), 1, 0),
            new Position(List.of(2, 0), 1, 1), new Position(List.of(2, 1), 1, 1),
            new Position(List.of(2, 2), 1, 1), new Position(List.of(1, 1), 1, 0),
            new Position(List.of(1, 2), 1, 0), new Position(List.of(0, 1), 0, 1),
            new Position(List.of(1, 1), 1, 1), new Position(List.of(0, 2), 0, 1),
            new Position(List.of(1, 2), 1, 1));
        assertEquals(11, result.statesExplored());
        assertEquals(11, result.states().size());
        assertEquals(expected, observer.positions.stream().map(DporProgramCounterTest::position).toList());
        assertEquals(Set.copyOf(expected), Set.copyOf(result.states().values().stream()
            .map(DporProgramCounterTest::position).toList()));
        Witness completed = new Witness(List.of(0, 0, 1, 1), List.of(ADVANCED, ADVANCED, ADVANCED, ADVANCED),
            TraceOutcome.COMPLETED);
        assertEquals(List.of(completed), observer.witnesses);
        assertEquals(List.of(completed), witnesses(result));
        verifyAgainstSource(model.program(), model.program(), result, property);
    }

    /** Source replay must reject altered explorer evidence rather than silently reconstructing it. */
    @Test
    void alteredSourceEvidenceIsRejectedWithoutReconstruction() {
        var model = new SourceProgramLoader().load("program identity { shared int x = 0; thread 0 { atomic { x = 1; } } invariant always (true); }");
        Invariant property = model.invariant().orElseThrow();
        DfsResult actual = new DporExplorer().explore(model.program(), property);
        Trace trace = actual.traces().getFirst();
        Trace altered = new Trace(trace.threadIds(), trace.outcomes(), trace.outcome(),
            new TraceEvidence("0".repeat(64), trace.evidence().executedSteps(), trace.failure(), trace.cutoff()));
        DfsResult corrupted = new DfsResult(actual.states(), List.of(altered), actual.statesExplored());
        assertThrows(CorruptedTraceException.class,
            () -> verifyAgainstSource(model.program(), model.program(), corrupted, property));
    }

    /** Exhaustive fallback executes the failing action at PC one rather than the successful action at zero. */
    @Test
    void unknownPropertyExecutesTheActualNonzeroCounter() {
        var model = new SourceProgramLoader().load("""
            program exhaustive { shared int x = 0;
              thread 0 { atomic { x = 1; } atomic { x = 1 % 0; } }
            }
            """);
        Invariant property = (state, config) -> true;
        assertTrue(property.observedLocations().isEmpty());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        Witness failure = new Witness(List.of(0, 0), List.of(ADVANCED, ASSERTION_FAILED), TraceOutcome.VIOLATION);
        assertEquals(2, result.statesExplored());
        assertEquals(List.of(List.of(0, 0), List.of(1, 1)), observer.positions.stream()
            .map(config -> List.of(config.programCounters().getFirst(), ((DynamicState) config.state()).getInt("x"))).toList());
        assertEquals(Set.of(List.of(0, 0), List.of(1, 1)), Set.copyOf(result.states().values().stream()
            .map(config -> List.of(config.programCounters().getFirst(), ((DynamicState) config.state()).getInt("x"))).toList()));
        assertEquals(List.of("visit:[0]", "visit:[1]", "trace:VIOLATION"), observer.events);
        assertEquals(List.of(failure), observer.witnesses);
        assertEquals(List.of(failure), witnesses(result));
        verifyAgainstSource(model.program(), model.program(), result, property);
    }

    /** A sleeping failing action at PC one retains both ordinary and all-asleep failure prefixes.
     * @param observed whether to supply the compiler's known-empty observation set
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allSleepingFallbackExecutesTheActualNonzeroCounter(boolean observed) {
        var model = new SourceProgramLoader().load("""
            program sleeping { shared int x = 0; shared int y = 0; shared int z = 0;
              thread 0 { atomic { x = 1; } atomic { y = 1 % 0; } }
              thread 1 { atomic { x = 1; } atomic { z = 1; } }
              invariant always (true);
            }
            """);
        Program wrapped = new Program(model.program().initialConfiguration().state(), model.program().threads());
        assertFalse(wrapped.isSource());
        assertTrue(wrapped.threads().stream().flatMap(thread -> thread.steps().stream())
            .allMatch(step -> step instanceof SourceStep));
        Invariant property = observed ? model.invariant().orElseThrow() : null;
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(wrapped, property, null, observer);
        List<Witness> expected = List.of(
            new Witness(List.of(0, 0), List.of(ADVANCED, ASSERTION_FAILED), TraceOutcome.VIOLATION),
            new Witness(List.of(0, 1, 1, 0), List.of(ADVANCED, ADVANCED, ADVANCED, ASSERTION_FAILED),
                TraceOutcome.VIOLATION));
        assertEquals(6, result.statesExplored());
        assertEquals(6, result.states().size());
        assertEquals(List.of("visit:[0, 0]", "visit:[1, 0]", "trace:VIOLATION", "visit:[1, 1]",
            "visit:[1, 2]", "trace:VIOLATION", "visit:[0, 1]", "visit:[0, 2]"), observer.events);
        List<RuntimePosition> expectedPositions = List.of(
            new RuntimePosition(List.of(0, 0), 0, 0, 0), new RuntimePosition(List.of(1, 0), 1, 0, 0),
            new RuntimePosition(List.of(1, 1), 1, 0, 0), new RuntimePosition(List.of(1, 2), 1, 0, 1),
            new RuntimePosition(List.of(0, 1), 1, 0, 0), new RuntimePosition(List.of(0, 2), 1, 0, 1));
        assertEquals(expectedPositions, observer.positions.stream().map(DporProgramCounterTest::runtimePosition).toList());
        assertEquals(Set.copyOf(expectedPositions), Set.copyOf(result.states().values().stream()
            .map(DporProgramCounterTest::runtimePosition).toList()));
        assertEquals(expected, observer.witnesses);
        assertEquals(expected, witnesses(result));
        verifyAgainstSource(wrapped, model.program(), result, property);
    }

    /** Captures modeled values and counters without canonical-encoder or object-identity oracles.
     * @param config actual visited source position
     * @return independent counter/value tuple
     */
    private static Position position(Configuration config) {
        var state = (DynamicState) config.state();
        return new Position(config.programCounters(), state.getInt("x"), state.getInt("y"));
    }

    /** Captures all shared values in the runtime-failure fixture, including rollback and the other writer.
     * @param config actual visited source-step position
     * @return literal-comparable counters and shared values
     */
    private static RuntimePosition runtimePosition(Configuration config) {
        var state = (DynamicState) config.state();
        return new RuntimePosition(config.programCounters(), state.getInt("x"), state.getInt("y"), state.getInt("z"));
    }

    /** Projects actual returned traces for comparison against literal expected witnesses.
     * @param result finished public exploration
     * @return recorded schedules, step outcomes and terminal classifications
     */
    private static List<Witness> witnesses(DfsResult result) {
        return result.traces().stream().map(trace -> new Witness(trace.threadIds(), trace.outcomes(), trace.outcome())).toList();
    }

    /** Preserves source evidence during replay, enriching only digest-free wrapper traces.
     * Plain legacy replay bypasses SourceStep execution; wrapper evidence uses the original source model.
     * @param explored program actually supplied to exploration, selecting the trusted evidence path
     * @param original compiler-produced program with source identity
     * @param result observed source or digest-free exploration evidence
     * @param property exploration property, or null for a true replay-only fallback
     */
    private static void verifyAgainstSource(Program explored, Program original, DfsResult result, Invariant property) {
        Invariant replayProperty = property != null ? property : (state, config) -> true;
        for (Trace trace : result.traces()) {
            Trace replayed = explored.isSource() ? trace
                : Trace.create(original, trace.threadIds(), trace.outcomes(), trace.outcome(), replayProperty);
            new TraceReplayer().replay(original, replayed, replayProperty);
        }
    }

    /** Independent position oracle.
     * @param counters committed owner counters
     * @param x shared x value
     * @param y shared y value
     */
    private record Position(List<Integer> counters, int x, int y) {}

    /** Runtime-failure position oracle.
     * @param counters committed owner counters
     * @param x shared x value
     * @param y rolled-back failure target value
     * @param z independent writer value
     */
    private record RuntimePosition(List<Integer> counters, int x, int y, int z) {}

    /** Independent execution evidence oracle.
     * @param threads recorded owner choices
     * @param outcomes recorded action results
     * @param outcome terminal classification
     */
    private record Witness(List<Integer> threads, List<StepOutcome> outcomes, TraceOutcome outcome) {}

    /** Non-mutating observer collecting actual public notifications. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Visited configurations, never mutated by this observer. */
        private final List<Configuration> positions = new ArrayList<>();
        /** Ordered visits and terminal callbacks. */
        private final List<String> events = new ArrayList<>();
        /** Value tuples extracted from callbacks independently of returned traces. */
        private final List<Witness> witnesses = new ArrayList<>();

        /** Captures the position and notification order without modifying the model.
         * @param config newly visited configuration
         */
        @Override public void onStateVisited(Configuration config) {
            positions.add(config);
            events.add("visit:" + config.programCounters());
        }

        /** Captures authentic recorded action results and classification.
         * @param trace emitted terminal evidence
         */
        @Override public void onTraceCreated(Trace trace) {
            events.add("trace:" + trace.outcome());
            witnesses.add(new Witness(trace.threadIds(), trace.outcomes(), trace.outcome()));
        }
    }
}
