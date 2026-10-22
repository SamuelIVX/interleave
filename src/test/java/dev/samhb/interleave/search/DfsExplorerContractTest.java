/** Literal DFS terminal evidence, callback ordering, state-budget boundaries and per-run isolation. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class DfsExplorerContractTest {
    /** Every terminal branch reports its literal prefix and callback after the corresponding visits.
     * @param source real one-path source model
     * @param visits hand-enumerated visited configuration count
     * @param events literal visitor sequence, including observed shared values
     * @param terminal literal expected trace evidence
     */
    @ParameterizedTest
    @MethodSource("terminalCases")
    void terminalEvidenceAndCallbacks(String source, long visits, List<String> events, Witness terminal) {
        var model = new SourceProgramLoader().load(source);
        var observer = new RecordingVisitor();
        Invariant invariant = model.invariant().orElse(null);
        DfsResult result = new DfsExplorer().explore(model.program(), invariant, null, observer);
        assertEquals(visits, result.statesExplored());
        assertEquals(visits, result.states().size());
        assertEquals(events, observer.events);
        assertEquals(List.of(terminal), observer.witnesses);
        assertEquals(List.of(terminal), result.traces().stream().map(DfsExplorerContractTest::witness).toList());
        new TraceReplayer().replay(model.program(), result.traces().getFirst(), invariant);
    }

    /** A failure edge creates no successor, so an exhausted budget must stop before executing it.
     * @param budget literal state limit, including negative and zero direct-explorer compatibility
     * @param visits literal expected visits
     * @param traceCount literal expected failure count
     */
    @ParameterizedTest
    @CsvSource({"-1, 0, 0", "0, 0, 0", "1, 1, 0", "2, 1, 1"})
    void budgetOverloadAndVisitorStopAtExactBoundary(long budget, long visits, int traceCount) {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { atomic { x = 1 % 0; } } }");
        var explorer = new DfsExplorer();
        DfsResult direct = explorer.explore(model.program(), budget);
        var observer = new RecordingVisitor();
        DfsResult observed = explorer.explore(model.program(), null, null, observer, budget);
        List<Witness> expected = traceCount == 0 ? List.of()
            : List.of(new Witness(List.of(0), List.of(StepOutcome.ASSERTION_FAILED), TraceOutcome.VIOLATION));
        for (DfsResult result : List.of(direct, observed)) {
            assertEquals(visits, result.statesExplored());
            assertEquals(visits, result.states().size());
            assertEquals(expected, result.traces().stream().map(DfsExplorerContractTest::witness).toList());
        }
        assertEquals(expected, observer.witnesses);
        List<String> events = visits == 0 ? List.of()
            : traceCount == 0 ? List.of("state:0") : List.of("state:0", "trace:VIOLATION");
        assertEquals(events, observer.events);
    }

    /** Different runs must replace visited positions and observers without altering earlier result snapshots. */
    @Test
    void reuseClearsVisitedPositionsAndKeepsPreviousResults() {
        var loader = new SourceProgramLoader();
        var first = loader.load("program first { shared int x = 0; thread 0 { atomic { x = 1; } } }");
        var second = loader.load("program second { shared int x = 9; thread 0 {} }");
        var explorer = new DfsExplorer();
        var firstObserver = new RecordingVisitor();
        var secondObserver = new RecordingVisitor();
        DfsResult before = explorer.explore(first.program(), null, null, firstObserver);
        DfsResult after = explorer.explore(second.program(), null, null, secondObserver);
        assertEquals(1, after.statesExplored());
        assertEquals(1, after.states().size());
        assertEquals(Set.of(9), values(after));
        assertEquals(List.of("state:9", "trace:COMPLETED"), secondObserver.events);
        assertEquals(List.of(new Witness(List.of(), List.of(), TraceOutcome.COMPLETED)),
            after.traces().stream().map(DfsExplorerContractTest::witness).toList());
        assertEquals(2, before.statesExplored());
        assertEquals(2, before.states().size());
        assertEquals(Set.of(0, 1), values(before));
        assertEquals(List.of("state:0", "state:1", "trace:COMPLETED"), firstObserver.events);
        assertEquals(List.of(new Witness(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.COMPLETED)),
            before.traces().stream().map(DfsExplorerContractTest::witness).toList());
    }

    /** Enumerates completion, property, step-failure, deadlock and cutoff paths with independent literal oracles.
     * @return five real source models and their hand-enumerated visits, callback sequence and prefix
     */
    private static Stream<Arguments> terminalCases() {
        return Stream.of(
            Arguments.of("program subject { shared int x = 0; thread 0 { atomic { x = 1; } } }", 2L,
                List.of("state:0", "state:1", "trace:COMPLETED"),
                new Witness(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.COMPLETED)),
            Arguments.of("program subject { shared int x = 0; thread 0 { atomic { x = 1; } } invariant always (x == 0); }", 2L,
                List.of("state:0", "state:1", "trace:VIOLATION"),
                new Witness(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION)),
            Arguments.of("program subject { shared int x = 0; thread 0 { atomic { x = 7; x = 1 % 0; } } }", 1L,
                List.of("state:0", "trace:VIOLATION"),
                new Witness(List.of(0), List.of(StepOutcome.ASSERTION_FAILED), TraceOutcome.VIOLATION)),
            Arguments.of("program subject { shared int x = 0; thread 0 { atomic when (false) { x = 1; } } }", 1L,
                List.of("state:0", "trace:DEADLOCK"),
                new Witness(List.of(), List.of(), TraceOutcome.DEADLOCK)),
            Arguments.of("program subject { shared int x = 0; thread 0 { while (true) limit 0 {} } }", 1L,
                List.of("state:0", "trace:INCOMPLETE"),
                new Witness(List.of(), List.of(), TraceOutcome.INCOMPLETE)));
    }

    /** Captures public trace values without relying on object identity or explorer-generated expected data.
     * @param trace actual recorded execution
     * @return immutable comparable prefix and terminal classification
     */
    private static Witness witness(Trace trace) {
        return new Witness(trace.threadIds(), trace.outcomes(), trace.outcome());
    }

    /** Reads modeled values without using canonical keys or diagnostic text as an oracle.
     * @param result one completed run
     * @return observed shared values in its visited configurations
     */
    private static Set<Integer> values(DfsResult result) {
        return result.states().values().stream().map(config -> ((DynamicState) config.state()).getInt("x"))
            .collect(Collectors.toSet());
    }

    /** Independent terminal evidence shape.
     * @param threads recorded choices
     * @param outcomes recorded step outcomes
     * @param outcome terminal classification
     */
    private record Witness(List<Integer> threads, List<StepOutcome> outcomes, TraceOutcome outcome) {}

    /** Observer of public events; it never mutates configurations or execution state. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Ordered visits and terminal notifications. */
        private final List<String> events = new ArrayList<>();
        /** Value snapshots of notified traces. */
        private final List<Witness> witnesses = new ArrayList<>();

        /** Captures the current shared value at every newly visited configuration.
         * @param config visited source configuration
         */
        @Override public void onStateVisited(Configuration config) {
            events.add("state:" + ((DynamicState) config.state()).getInt("x"));
        }

        /** Captures terminal notification ordering and literal evidence values.
         * @param trace newly recorded trace
         */
        @Override public void onTraceCreated(Trace trace) {
            events.add("trace:" + trace.outcome());
            witnesses.add(witness(trace));
        }
    }
}
