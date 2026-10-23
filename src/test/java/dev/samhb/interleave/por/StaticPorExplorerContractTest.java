/** Static-POR terminal notifications, source dependency preservation and full-branch failure evidence. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class StaticPorExplorerContractTest {
    /** Each terminal branch retains its literal evidence and visitor order with the original optional property.
     * @param source real one-path source model
     * @param visits hand-enumerated visited count
     * @param events literal visits and terminal notification order
     * @param terminal literal expected witness
     */
    @ParameterizedTest
    @MethodSource("terminalCases")
    void terminalEvidenceAndCallbacks(String source, long visits, List<String> events, Witness terminal) {
        var model = new SourceProgramLoader().load(source);
        Invariant property = model.invariant().orElse(null);
        var observer = new RecordingVisitor();
        DfsResult result = new StaticPorExplorer().explore(model.program(), property, null, observer);
        assertEquals(visits, result.statesExplored());
        assertEquals(visits, result.states().size());
        assertEquals(events, observer.events);
        assertEquals(List.of(terminal), observer.witnesses);
        assertEquals(List.of(terminal), result.traces().stream().map(StaticPorExplorerContractTest::witness).toList());
        new TraceReplayer().replay(model.program(), result.traces().getFirst(),
            property != null ? property : (state, config) -> true);
    }

    /** The caller's previously visited initial position must not suppress the new traversal. */
    @Test
    void prepopulatedStoreIsClearedBeforeExploration() {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { atomic { x = 1; } } }");
        var store = new HashingStateStore();
        Configuration initial = model.program().initialConfiguration();
        store.markVisited(initial);
        assertTrue(store.isVisited(initial));
        var observer = new RecordingVisitor();
        DfsResult result = new StaticPorExplorer().explore(model.program(), null, store, observer);
        assertEquals(2, result.statesExplored());
        assertEquals(2, result.states().size());
        assertEquals(2, store.size());
        assertEquals(List.of("state:0", "state:1", "trace:COMPLETED"), observer.events);
        assertEquals(List.of(new Witness(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.COMPLETED)),
            result.traces().stream().map(StaticPorExplorerContractTest::witness).toList());
    }

    /** Without a property, source future-dependency closure must retain a failure before a disabling write. */
    @Test
    void invariantFreeSourcePreservesFutureGuardFailure() {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; shared int y = 0; thread 0 { atomic { x = 1; } } thread 1 { atomic { y = 1; } atomic when (x == 0) { y = 1 % 0; } } }");
        List<Witness> expected = List.of(
            new Witness(List.of(0, 1), List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED), TraceOutcome.DEADLOCK),
            new Witness(List.of(1, 1), List.of(StepOutcome.ADVANCED, StepOutcome.ASSERTION_FAILED), TraceOutcome.VIOLATION));
        for (DfsResult result : List.of(new DfsExplorer().explore(model.program()),
                new StaticPorExplorer().explore(model.program()))) {
            assertEquals(4, result.statesExplored());
            assertEquals(4, result.states().size());
            assertEquals(expected, result.traces().stream().map(StaticPorExplorerContractTest::witness).toList());
            for (Trace trace : result.traces()) {
                new TraceReplayer().replay(model.program(), trace, (state, config) -> true);
                if (trace.outcome() == TraceOutcome.VIOLATION) {
                    assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, trace.failure().kind());
                    assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, trace.failure().category());
                }
            }
        }
    }

    /** Full branching must retain depth-first exception evidence rather than prepare a later root edge first. */
    @Test
    void fullBranchingPreservesFirstExceptionAndVisitedPrefix() {
        var program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new FailingRead()))));
        Invariant property = Invariant.observing(Set.of(), state -> true);
        List<Boolean> dfsVisits = new ArrayList<>();
        List<Boolean> porVisits = new ArrayList<>();
        var dfsError = assertThrows(IllegalStateException.class, () -> new DfsExplorer().explore(program, property,
            null, config -> dfsVisits.add(((PetersonState) config.state()).flag(0))));
        var porError = assertThrows(IllegalStateException.class, () -> new StaticPorExplorer().explore(program, property,
            null, config -> porVisits.add(((PetersonState) config.state()).flag(0))));
        assertEquals("failure after flag write", dfsError.getMessage());
        assertEquals("failure after flag write", porError.getMessage());
        assertEquals(List.of(false, true), dfsVisits);
        assertEquals(List.of(false, true), porVisits);
    }

    /** Enumerates the five terminal paths with literal counts, event order and prefixes.
     * @return real source programs with independent terminal evidence oracles
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

    /** Extracts comparable public trace values rather than object identity.
     * @param trace actual explorer or callback trace
     * @return immutable evidence value used by the literal oracle
     */
    private static Witness witness(Trace trace) {
        return new Witness(trace.threadIds(), trace.outcomes(), trace.outcome());
    }

    /** Independent terminal prefix and classification.
     * @param threads recorded choices
     * @param outcomes recorded step outcomes
     * @param outcome terminal classification
     */
    private record Witness(List<Integer> threads, List<StepOutcome> outcomes, TraceOutcome outcome) {}

    /** Non-mutating observer of source values, terminal ordering and evidence. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Ordered public visit/trace notifications. */
        private final List<String> events = new ArrayList<>();
        /** Value snapshots of notified terminal evidence. */
        private final List<Witness> witnesses = new ArrayList<>();

        /** Records the source value at each visited position.
         * @param config actual visited source configuration
         */
        @Override public void onStateVisited(Configuration config) {
            events.add("state:" + ((DynamicState) config.state()).getInt("x"));
        }

        /** Records terminal notification order and public prefix values.
         * @param trace newly recorded evidence
         */
        @Override public void onTraceCreated(Trace trace) {
            events.add("trace:" + trace.outcome());
            witnesses.add(witness(trace));
        }
    }

    /** Pure deterministic failing read with complete flag dependency and no hidden execution state. */
    private static final class FailingRead implements Step {
        /** Reports the flag determining the propagated failure diagnostic.
         * @return complete error dependency
         */
        @Override public Set<MemoryLocation> reads() { return Set.of(MemoryLocation.of("flag[0]")); }

        /** This failing read publishes no modeled writes.
         * @return empty write footprint
         */
        @Override public Set<MemoryLocation> writes() { return Set.of(); }

        /** The modeled failing action is enabled in every fixture state.
         * @param state current modeled state, left unchanged
         * @return true
         */
        @Override public boolean enabled(SharedState state) { return true; }

        /** Propagates a deterministic error describing the flag value without modifying state.
         * @param state current modeled flag state
         * @return never returns normally
         * @throws IllegalStateException with the flag-dependent failure diagnostic
         */
        @Override public StepOutcome execute(SharedState state) {
            throw new IllegalStateException(((PetersonState) state).flag(0)
                ? "failure after flag write" : "failure before flag write");
        }
    }
}
