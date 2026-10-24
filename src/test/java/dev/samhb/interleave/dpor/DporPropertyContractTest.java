/** Public DPOR metadata dispatch and violation-before-deadlock property contracts. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.search.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class DporPropertyContractTest {
    /** Equal constant truth values still require different traversal when observation metadata differs.
     * @param mode absent, known-empty or unknown property observations
     */
    @ParameterizedTest
    @EnumSource(PropertyMode.class)
    void constantPropertiesRespectObservationMetadata(PropertyMode mode) {
        var model = new SourceProgramLoader().load(independentSource());
        Invariant property = property(mode, true);
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        // The independent product has nine counter pairs. Reduced traversal omits only [1,1].
        assertEquals(mode == PropertyMode.UNKNOWN ? 9 : 8, result.statesExplored());
        assertEquals(mode == PropertyMode.UNKNOWN ? 9 : 8, result.states().size());
        assertEquals(mode == PropertyMode.UNKNOWN,
            observer.positions.contains(List.of(1, 1)));
        assertEquals(List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED)), observer.witnesses);
        assertEquals(List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED)), witnesses(result));
        replay(model.program(), result, property);
    }

    /** A pure configuration-sensitive callback must retain the middle position that reduced traversal omits. */
    @Test
    void unknownConfigurationPropertyRetainsItsConcreteViolation() {
        var model = new SourceProgramLoader().load(independentSource());
        Invariant property = (state, config) -> !config.programCounters().equals(List.of(1, 1));
        assertTrue(property.observedLocations().isEmpty());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        assertEquals(9, result.statesExplored());
        assertEquals(9, result.states().size());
        List<Witness> expected = List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED),
            new Witness(List.of(0, 1), TraceOutcome.VIOLATION));
        assertEquals(expected, observer.witnesses);
        assertEquals(expected, witnesses(result));
        assertEquals(List.of(List.of(0, 0), List.of(1, 0), List.of(2, 0), List.of(2, 1),
            List.of(2, 2), List.of(1, 1), List.of(0, 1), List.of(0, 2), List.of(1, 2)), observer.positions);
        replay(model.program(), result, property);
    }

    /** A false property wins at a blocked position; a true property preserves the genuine deadlock.
     * @param mode known-empty or unknown metadata, selecting reduced or exhaustive traversal
     * @param holds deterministic constant truth value
     * @param outcome independently specified terminal classification
     */
    @ParameterizedTest
    @MethodSource("blockedCases")
    void propertyPrecedesDeadlockWithoutStatefulPredicates(PropertyMode mode, boolean holds, TraceOutcome outcome) {
        var model = new SourceProgramLoader().load("""
            program blocked { shared int x = 0;
              thread 0 { atomic when (false) { x = 1; } }
            }
            """);
        Invariant property = property(mode, holds);
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        assertEquals(1, result.statesExplored());
        assertEquals(List.of(List.of(0)), observer.positions);
        assertEquals(List.of(new Witness(List.of(), outcome)), observer.witnesses);
        assertEquals(List.of(new Witness(List.of(), outcome)), witnesses(result));
        replay(model.program(), result, property);
    }

    /** Enumerates both deadlock outcomes for both property-checking traversal modes.
     * @return four literal property-truth/terminal-classification cases
     */
    private static Stream<Arguments> blockedCases() {
        return Stream.of(PropertyMode.KNOWN_EMPTY, PropertyMode.UNKNOWN).flatMap(mode -> Stream.of(
            Arguments.of(mode, true, TraceOutcome.DEADLOCK),
            Arguments.of(mode, false, TraceOutcome.VIOLATION)));
    }

    /** Builds a three-by-three independent counter product without property-derived footprints.
     * @return real source with two two-action owners
     */
    private static String independentSource() {
        return "program product { shared int x = 0; shared int y = 0;"
            + " thread 0 { atomic { x = 1; } atomic { x = 2; } }"
            + " thread 1 { atomic { y = 1; } atomic { y = 2; } } }";
    }

    /** Constructs pure properties with honest metadata and checks each fixture's public routing shape.
     * @param mode absence, known-empty observations or unknown observations
     * @param holds immutable truth value, independent of state and configuration
     * @return selected property, or null for NONE
     */
    private static Invariant property(PropertyMode mode, boolean holds) {
        Invariant result = switch (mode) {
            case NONE -> null;
            case KNOWN_EMPTY -> Invariant.observing(Set.of(), state -> holds);
            case UNKNOWN -> (state, config) -> holds;
        };
        if (mode == PropertyMode.KNOWN_EMPTY) assertTrue(result.observedLocations().orElseThrow().isEmpty());
        if (mode == PropertyMode.UNKNOWN) assertTrue(result.observedLocations().isEmpty());
        return result;
    }

    /** Projects actual returned traces to values compared against literal witnesses.
     * @param result completed public exploration
     * @return recorded schedules and classifications
     */
    private static List<Witness> witnesses(DfsResult result) {
        return result.traces().stream().map(trace -> new Witness(trace.threadIds(), trace.outcome())).toList();
    }

    /** Checks recorded step outcomes and source terminal classification using the actual property.
     * @param program original source program
     * @param result actual explorer evidence
     * @param property exploration property, or null for a replay-only true fallback
     */
    private static void replay(Program program, DfsResult result, Invariant property) {
        for (Trace trace : result.traces()) {
            assertTrue(trace.outcomes().stream().allMatch(outcome -> outcome == StepOutcome.ADVANCED));
            new TraceReplayer().replay(program, trace, property != null ? property : (state, config) -> true);
        }
    }

    /** Honest metadata modes; known-empty properties are constant, unknown ones may inspect counters. */
    private enum PropertyMode {
        /** No property. */ NONE,
        /** Present empty observation set. */ KNOWN_EMPTY,
        /** Absent observation metadata. */ UNKNOWN
    }

    /** Independent public evidence tuple.
     * @param threads recorded successful choices
     * @param outcome terminal classification
     */
    private record Witness(List<Integer> threads, TraceOutcome outcome) {}

    /** Non-mutating visitor retaining positions and callback evidence independently of returned traces. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Actual visited counter vectors. */
        private final List<List<Integer>> positions = new ArrayList<>();
        /** Value tuples extracted from terminal notifications. */
        private final List<Witness> witnesses = new ArrayList<>();

        /** Captures an immutable copy without mutating the modeled configuration.
         * @param config newly explored position
         */
        @Override public void onStateVisited(Configuration config) {
            positions.add(List.copyOf(config.programCounters()));
        }

        /** Records literal-comparable public terminal evidence.
         * @param trace emitted terminal witness
         */
        @Override public void onTraceCreated(Trace trace) {
            witnesses.add(new Witness(trace.threadIds(), trace.outcome()));
        }
    }
}
