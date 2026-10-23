/** Public source DPOR contracts for sleep coverage, property visibility and collision pruning. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.BitstateStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DporSleepContractTest {
    /** Independent invisible actions reduce the nine-position product while retaining one full witness.
     * @param observed whether to pass a known empty observation set instead of no property
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentInvisibleWorkRetainsCanonicalCompletion(boolean observed) {
        var model = new SourceProgramLoader().load(independentSource("true"));
        Invariant property = property(observed);
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        assertEquals(8, result.statesExplored());
        assertEquals(8, result.states().size());
        assertEquals(List.of("[0, 0]", "[1, 0]", "[2, 0]", "[2, 1]", "[2, 2]",
            "[0, 1]", "[0, 2]", "[1, 2]"), observer.positions);
        assertEquals(List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED)), observer.witnesses);
        assertEquals(List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED)), witnesses(result));
        replay(model.program(), result, property);
    }

    /** Incomparable sleepers at the same values/counters both require traversal, rather than value-only pruning.
     * @param observed whether to pass a known empty observation set instead of no property
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void incomparableSleepContextsRevisitTheSamePosition(boolean observed) {
        var model = new SourceProgramLoader().load("""
            program contexts { shared int x = 0; shared int y = 0;
              thread 0 { atomic { y = 1; } atomic {} }
              thread 1 { atomic { y = 1; } atomic { x = 1; } }
            }
            """);
        Invariant property = property(observed);
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        // Nine distinct counter pairs; [1,1] is revisited with the other action awake.
        assertEquals(10, result.statesExplored());
        assertEquals(9, result.states().size());
        assertEquals(List.of("[0, 0]", "[1, 0]", "[2, 0]", "[2, 1]", "[2, 2]",
            "[1, 1]", "[1, 2]", "[0, 1]", "[1, 1]", "[0, 2]"), observer.positions);
        assertEquals(List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED)), observer.witnesses);
        assertEquals(List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED)), witnesses(result));
        replay(model.program(), result, property);
    }

    /** A real one-bit Bloom store collides on every successor; source context tracking must honor that boundary.
     * @param observed whether to pass a known empty observation set instead of no property
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void approximateCollisionWithoutCoverageEntryStillPrunes(boolean observed) {
        var model = new SourceProgramLoader().load(independentSource("true"));
        var store = new BitstateStore(1, 1);
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property(observed), store, observer);
        assertEquals(1, result.statesExplored());
        assertEquals(1, result.states().size());
        assertEquals(List.of("[0, 0]"), observer.positions);
        assertEquals(List.of(), result.traces());
        assertEquals(List.of(), observer.witnesses);
        assertEquals(1, store.statesMarked());
        assertEquals(1, store.bitCount());
    }

    /** An independent empty prefix cannot put the write needed by the owner's later read to sleep.
     * @param observed whether to pass a known empty observation set instead of no property
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void futureReadPreservesBothRecordedObservations(boolean observed) {
        var model = new SourceProgramLoader().load("""
            program future { shared int x = 0; shared int y = 0;
              thread 0 { atomic {} atomic { y = x; } }
              thread 1 { atomic { x = 1; } }
            }
            """);
        Invariant property = property(observed);
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        assertEquals(Set.of(0, 1), result.states().values().stream().filter(Configuration::allTerminated)
            .map(config -> ((DynamicState) config.state()).getInt("y")).collect(Collectors.toSet()));
        List<Witness> expected = List.of(new Witness(List.of(0, 0, 1), TraceOutcome.COMPLETED),
            new Witness(List.of(0, 1, 0), TraceOutcome.COMPLETED));
        assertEquals(expected, observer.witnesses);
        assertEquals(expected, witnesses(result));
        replay(model.program(), result, property);
    }

    /** Complete observations keep the independent writers awake at every position of the three-by-three grid. */
    @Test
    void observedWritesRetainTheWholePositionGrid() {
        var model = new SourceProgramLoader().load(independentSource("x >= 0 && y >= 0"));
        Invariant property = model.invariant().orElseThrow();
        assertEquals(Set.of(MemoryLocation.of("x"), MemoryLocation.of("y")),
            property.observedLocations().orElseThrow());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        assertEquals(9, result.statesExplored());
        assertEquals(9, result.states().size());
        assertEquals(Set.of("[0, 0]", "[0, 1]", "[0, 2]", "[1, 0]", "[1, 1]", "[1, 2]",
            "[2, 0]", "[2, 1]", "[2, 2]"), Set.copyOf(observer.positions));
        assertEquals(List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED)), observer.witnesses);
        assertEquals(List.of(new Witness(List.of(0, 0, 1, 1), TraceOutcome.COMPLETED)), witnesses(result));
        replay(model.program(), result, property);
    }

    /** Visibility protects a concrete intermediate violation as well as the safe completed ordering. */
    @Test
    void visibleIndependentWriteRetainsTheIntermediateViolation() {
        var model = new SourceProgramLoader().load("""
            program visible { shared int x = 0; shared int y = 0;
              thread 0 { atomic { x = 1; } } thread 1 { atomic { y = 1; } }
              invariant always (x == 1 || y == 0);
            }
            """);
        Invariant property = model.invariant().orElseThrow();
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        List<Witness> expected = List.of(new Witness(List.of(0, 1), TraceOutcome.COMPLETED),
            new Witness(List.of(1), TraceOutcome.VIOLATION));
        assertEquals(expected, observer.witnesses);
        assertEquals(expected, witnesses(result));
        assertEquals(List.of("[0, 0]", "[1, 0]", "[1, 1]", "[0, 1]"), observer.positions);
        replay(model.program(), result, property);
    }

    /** Supplies a source model with two independent two-action owners.
     * @param expression complete state-only property expression
     * @return bounded source model with no hidden state or step callbacks
     */
    private static String independentSource(String expression) {
        return "program independent { shared int x = 0; shared int y = 0;"
            + " thread 0 { atomic { x = 1; } atomic { x = 2; } }"
            + " thread 1 { atomic { y = 1; } atomic { y = 2; } } invariant always (" + expression + "); }";
    }

    /** Selects absent versus known-empty observations, both of which use reduced exploration.
     * @param observed whether to supply a pure always-true observed property
     * @return a property with known empty observations, or null
     */
    private static Invariant property(boolean observed) {
        return observed ? Invariant.observing(Set.of(), state -> true) : null;
    }

    /** Projects returned traces to independent public evidence values.
     * @param result completed search
     * @return recorded schedules and classifications
     */
    private static List<Witness> witnesses(DfsResult result) {
        return result.traces().stream().map(trace -> new Witness(trace.threadIds(), trace.outcome())).toList();
    }

    /** Replays every recorded step and source terminal classification.
     * @param program source program that produced the evidence
     * @param result completed search
     * @param property exploration property, or null for a replay-only true fallback
     */
    private static void replay(Program program, DfsResult result, Invariant property) {
        for (Trace trace : result.traces()) {
            assertTrue(trace.outcomes().stream().allMatch(outcome -> outcome == StepOutcome.ADVANCED));
            new TraceReplayer().replay(program, trace, property != null ? property : (state, config) -> true);
        }
    }

    /** Value-based terminal oracle.
     * @param threads recorded owner choices
     * @param outcome terminal classification
     */
    private record Witness(List<Integer> threads, TraceOutcome outcome) {}

    /** Non-mutating visitor collecting actual public notifications, independently of the returned lists. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Ordered visited counters, including any legitimate context revisits. */
        private final List<String> positions = new ArrayList<>();
        /** Recorded schedules and classifications from trace notifications. */
        private final List<Witness> witnesses = new ArrayList<>();

        /** Records a newly explored position without mutating its values.
         * @param config explored source configuration
         */
        @Override public void onStateVisited(Configuration config) {
            positions.add(config.programCounters().toString());
        }

        /** Records a terminal notification independently of the result list.
         * @param trace emitted witness
         */
        @Override public void onTraceCreated(Trace trace) {
            witnesses.add(new Witness(trace.threadIds(), trace.outcome()));
        }
    }
}
