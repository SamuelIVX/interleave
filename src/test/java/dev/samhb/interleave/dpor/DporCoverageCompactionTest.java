/** Public source coverage contracts for incomparable contexts and empty-enabled boundaries. */
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.samhb.interleave.core.StepOutcome.ADVANCED;
import static org.junit.jupiter.api.Assertions.*;

class DporCoverageCompactionTest {
    /** A later incomparable context must not erase coverage needed by a third arrival.
     * @param observed whether to supply the compiler's known-empty property
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void incomparableCoverageSurvivesCompaction(boolean observed) {
        var model = new SourceProgramLoader().load("""
            program coverage { shared int y = 0; shared int z = 0;
              thread 0 { atomic { z = 1; } atomic { y = 1; } }
              thread 1 { atomic { z = 1; } atomic {} }
              thread 2 { atomic { if (y == 0) { z = 1; } } }
              invariant always (true);
            }
            """);
        Invariant property = property(observed, model.invariant().orElseThrow());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        Set<Position> expected = Set.of(
            new Position(List.of(0, 0, 0), 0, 0), new Position(List.of(0, 0, 1), 0, 1),
            new Position(List.of(0, 1, 0), 0, 1), new Position(List.of(0, 1, 1), 0, 1),
            new Position(List.of(0, 2, 0), 0, 1), new Position(List.of(0, 2, 1), 0, 1),
            new Position(List.of(1, 0, 0), 0, 1), new Position(List.of(1, 0, 1), 0, 1),
            new Position(List.of(1, 1, 0), 0, 1), new Position(List.of(1, 1, 1), 0, 1),
            new Position(List.of(1, 2, 0), 0, 1), new Position(List.of(1, 2, 1), 0, 1),
            new Position(List.of(2, 0, 0), 1, 1), new Position(List.of(2, 0, 1), 1, 1),
            new Position(List.of(2, 1, 0), 1, 1), new Position(List.of(2, 1, 1), 1, 1),
            new Position(List.of(2, 2, 0), 1, 1), new Position(List.of(2, 2, 1), 1, 1));
        assertEquals(21, result.statesExplored());
        assertEquals(18, result.states().size());
        assertEquals(21, observer.positions.size());
        assertEquals(expected, Set.copyOf(observer.positions));
        assertEquals(expected, result.states().values().stream().map(DporCoverageCompactionTest::position)
            .collect(Collectors.toSet()));
        var counts = observer.positions.stream().collect(Collectors.groupingBy(Position::counters, Collectors.counting()));
        assertEquals(Set.of(List.of(1, 1, 0), List.of(1, 1, 1), List.of(0, 1, 1)), counts.entrySet().stream()
            .filter(entry -> entry.getValue() > 1).map(java.util.Map.Entry::getKey).collect(Collectors.toSet()));
        assertEquals(2L, counts.get(List.of(1, 1, 0)));
        assertEquals(2L, counts.get(List.of(1, 1, 1)));
        assertEquals(2L, counts.get(List.of(0, 1, 1)));
        Witness completed = new Witness(List.of(0, 0, 2, 1, 1),
            List.of(ADVANCED, ADVANCED, ADVANCED, ADVANCED, ADVANCED), TraceOutcome.COMPLETED);
        assertEquals(List.of(completed), observer.witnesses);
        assertEquals(List.of(completed), witnesses(result));
        replay(model.program(), result, property);
    }

    /** Reaching no enabled work after a committed write retains either completion or genuine deadlock.
     * @param observed whether to supply known-empty observations
     * @param source finite one-write model, optionally with a disabled second owner
     * @param positions literal visited counter/value positions
     * @param outcome independently specified terminal class
     */
    @ParameterizedTest
    @MethodSource("terminalCases")
    void emptyEnabledBoundaryRetainsEvidence(boolean observed, String source,
            List<Position> positions, TraceOutcome outcome) {
        var model = new SourceProgramLoader().load(source);
        Invariant property = property(observed, model.invariant().orElseThrow());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        assertEquals(2, result.statesExplored());
        assertEquals(2, result.states().size());
        assertEquals(positions, observer.positions);
        assertEquals(Set.copyOf(positions), result.states().values().stream().map(DporCoverageCompactionTest::position)
            .collect(Collectors.toSet()));
        Witness expected = new Witness(List.of(0), List.of(ADVANCED), outcome);
        assertEquals(List.of(expected), observer.witnesses);
        assertEquals(List.of(expected), witnesses(result));
        replay(model.program(), result, property);
    }

    /** Enumerates both no-enabled outcomes under both reduced property modes.
     * @return four source cases with literal committed positions and terminal outcomes
     */
    private static Stream<Arguments> terminalCases() {
        return Stream.of(false, true).flatMap(observed -> Stream.of(
            Arguments.of(observed, "program complete { shared int y = 0; shared int z = 0; thread 0 { atomic { y = 1; } } invariant always (true); }",
                List.of(new Position(List.of(0), 0, 0), new Position(List.of(1), 1, 0)), TraceOutcome.COMPLETED),
            Arguments.of(observed, "program blocked { shared int y = 0; shared int z = 0; thread 0 { atomic { y = 1; } } thread 1 { atomic when (false) { z = 1; } } invariant always (true); }",
                List.of(new Position(List.of(0, 0), 0, 0), new Position(List.of(1, 0), 1, 0)), TraceOutcome.DEADLOCK)));
    }

    /** Selects reduced exploration with honest absence or known-empty metadata.
     * @param observed whether to supply the declared pure property
     * @param declared compiler-produced constant property
     * @return declared property or null
     */
    private static Invariant property(boolean observed, Invariant declared) {
        assertTrue(declared.observedLocations().orElseThrow().isEmpty());
        return observed ? declared : null;
    }

    /** Captures public modeled values without opaque canonical keys or map-order assumptions.
     * @param config actual explored source position
     * @return independent counter/value tuple
     */
    private static Position position(Configuration config) {
        var state = (DynamicState) config.state();
        return new Position(config.programCounters(), state.getInt("y"), state.getInt("z"));
    }

    /** Projects returned evidence for comparison to a literal oracle independent of callbacks.
     * @param result finished public exploration
     * @return recorded schedules, outcomes and classifications
     */
    private static List<Witness> witnesses(DfsResult result) {
        return result.traces().stream().map(trace -> new Witness(trace.threadIds(), trace.outcomes(), trace.outcome())).toList();
    }

    /** Replays original source evidence without reconstructing or repairing its metadata.
     * @param program compiler-produced model with source identity
     * @param result actual explorer evidence
     * @param property exploration property, or null for a replay-only true fallback
     */
    private static void replay(Program program, DfsResult result, Invariant property) {
        for (Trace trace : result.traces()) {
            new TraceReplayer().replay(program, trace, property != null ? property : (state, config) -> true);
        }
    }

    /** Public source position oracle.
     * @param counters committed owner counters
     * @param y shared y value
     * @param z shared z value
     */
    private record Position(List<Integer> counters, int y, int z) {}

    /** Independent public evidence oracle.
     * @param threads recorded choices
     * @param outcomes recorded action outcomes
     * @param outcome terminal classification
     */
    private record Witness(List<Integer> threads, List<StepOutcome> outcomes, TraceOutcome outcome) {}

    /** Non-mutating observer retaining actual visited values and terminal evidence. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Visited value tuples, including legitimate context revisits. */
        private final List<Position> positions = new ArrayList<>();
        /** Terminal value tuples extracted independently of returned traces. */
        private final List<Witness> witnesses = new ArrayList<>();

        /** Captures the modeled position without mutating it.
         * @param config newly visited configuration
         */
        @Override public void onStateVisited(Configuration config) { positions.add(position(config)); }

        /** Captures the emitted execution evidence without rebuilding it.
         * @param trace actual terminal witness
         */
        @Override public void onTraceCreated(Trace trace) {
            witnesses.add(new Witness(trace.threadIds(), trace.outcomes(), trace.outcome()));
        }
    }
}
