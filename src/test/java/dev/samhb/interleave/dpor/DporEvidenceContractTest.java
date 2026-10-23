/** DPOR public terminal evidence across absent, observed and exhaustive-fallback property modes. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class DporEvidenceContractTest {
    /** Terminal paths preserve literal visits, callback order and evidence in each applicable property mode.
     * @param mode public property-metadata routing mode
     * @param source real one-path source model with an explicit property
     * @param visits hand-enumerated count for this linear fixture, not a general unique-position guarantee
     * @param events literal ordered visit/trace notifications
     * @param terminal literal recorded choices, outcomes and classification
     */
    @ParameterizedTest
    @MethodSource("terminalCases")
    void terminalEvidenceAcrossPropertyModes(PropertyMode mode, String source, long visits,
            List<String> events, Witness terminal) {
        var model = new SourceProgramLoader().load(source);
        Invariant property = property(mode, model.invariant().orElseThrow());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property, null, observer);
        assertEquals(visits, result.statesExplored());
        assertEquals(visits, result.states().size());
        assertEquals(events, observer.events);
        assertEquals(List.of(terminal), observer.witnesses);
        assertEquals(List.of(terminal), result.traces().stream().map(DporEvidenceContractTest::witness).toList());
        new TraceReplayer().replay(model.program(), result.traces().getFirst(),
            property != null ? property : (state, config) -> true);
    }

    /** A caller-supplied visited initial position must not suppress any of the public routing modes.
     * @param mode public property-metadata routing mode
     */
    @ParameterizedTest
    @EnumSource(PropertyMode.class)
    void prepopulatedStoreIsClearedAcrossPropertyModes(PropertyMode mode) {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { atomic { x = 1; } } invariant always (x >= 0); }");
        var store = new HashingStateStore();
        Configuration initial = model.program().initialConfiguration();
        store.markVisited(initial);
        assertTrue(store.isVisited(initial));
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), property(mode, model.invariant().orElseThrow()),
            store, observer);
        assertEquals(2, result.statesExplored());
        assertEquals(2, result.states().size());
        assertEquals(2, store.size());
        assertEquals(Set.of(0, 1), result.states().values().stream()
            .map(config -> ((DynamicState) config.state()).getInt("x")).collect(Collectors.toSet()));
        assertEquals(List.of("visit:[0]", "visit:[1]", "trace:COMPLETED"), observer.events);
        assertEquals(List.of(new Witness(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.COMPLETED)),
            result.traces().stream().map(DporEvidenceContractTest::witness).toList());
    }

    /** A legacy assertion asleep after an independent write must still emit its fallback witness and callback.
     * @param mode reduced traversal mode; unknown observations use a different exhaustive path
     */
    @ParameterizedTest
    @EnumSource(value = PropertyMode.class, names = {"NONE", "OBSERVED"})
    void allSleepingLegacyAssertionRetainsEvidence(PropertyMode mode) {
        var program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(1, true))),
            new ModelThread(1, List.of(new AssertFirstFlag()))));
        Invariant invariant = property(mode, Invariant.observing(Set.of(), state -> true));
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(program, invariant, null, observer);
        List<Witness> expected = List.of(
            new Witness(List.of(0, 1), List.of(StepOutcome.ADVANCED, StepOutcome.ASSERTION_FAILED), TraceOutcome.VIOLATION),
            new Witness(List.of(1), List.of(StepOutcome.ASSERTION_FAILED), TraceOutcome.VIOLATION));
        assertEquals(2, result.statesExplored());
        assertEquals(2, result.states().size());
        assertEquals(List.of("visit:[0, 0]", "visit:[1, 0]", "trace:VIOLATION", "trace:VIOLATION"), observer.events);
        assertEquals(List.of(false, true), observer.positions.stream()
            .map(config -> ((PetersonState) config.state()).flag(1)).toList());
        assertEquals(expected, observer.witnesses);
        assertEquals(expected, result.traces().stream().map(DporEvidenceContractTest::witness).toList());
        for (Trace trace : result.traces()) new TraceReplayer().replay(program, trace);
    }

    /** Unknown-observation fallback must keep distinct loop occurrences even when their prefixes are empty. */
    @Test
    void exhaustiveFallbackPreservesDistinctCutoffWitnesses() {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { while (true) limit 0 {} } thread 1 { while (true) limit 0 {} } invariant always (x >= 0); }");
        Invariant invariant = property(PropertyMode.UNKNOWN, model.invariant().orElseThrow());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), invariant, null, observer);
        Witness cutoff = new Witness(List.of(), List.of(), TraceOutcome.INCOMPLETE);
        assertEquals(1, result.statesExplored());
        assertEquals(List.of(cutoff, cutoff), result.traces().stream().map(DporEvidenceContractTest::witness).toList());
        assertEquals(List.of(cutoff, cutoff), observer.witnesses);
        assertEquals(List.of(0, 1), result.traces().stream().map(trace -> trace.cutoff().threadId()).toList());
        assertEquals(List.of(0, 1), observer.cutoffThreads);
        assertEquals(List.of("visit:[0, 0]", "trace:INCOMPLETE", "trace:INCOMPLETE"), observer.events);
        for (Trace trace : result.traces()) new TraceReplayer().replay(model.program(), trace, invariant);
    }

    /** Reaching one loop occurrence after different schedules retains only its first complete prefix. */
    @Test
    void exhaustiveFallbackDeduplicatesRepeatedCutoffOccurrence() {
        var model = new SourceProgramLoader().load("program subject { shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } } thread 1 { atomic {} while (true) limit 0 {} } invariant always (x >= 0); }");
        Invariant invariant = property(PropertyMode.UNKNOWN, model.invariant().orElseThrow());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(model.program(), invariant, null, observer);
        Witness cutoff = new Witness(List.of(0, 0, 1),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED), TraceOutcome.INCOMPLETE);
        assertEquals(6, result.statesExplored());
        assertEquals(6, result.states().size());
        assertEquals(List.of(cutoff), result.traces().stream().map(DporEvidenceContractTest::witness).toList());
        assertEquals(List.of(cutoff), observer.witnesses);
        assertEquals(List.of(1), observer.cutoffThreads);
        assertEquals(List.of("visit:[0, 0]", "visit:[1, 0]", "visit:[2, 0]", "visit:[2, 1]",
            "trace:INCOMPLETE", "visit:[1, 1]", "visit:[0, 1]"), observer.events);
        new TraceReplayer().replay(model.program(), result.traces().getFirst(), invariant);
    }

    /** Defines four non-property-failing paths in all modes and property violations in both checking modes.
     * @return fourteen source cases with independent literal terminal and callback oracles
     */
    private static Stream<Arguments> terminalCases() {
        Stream<Arguments> ordinary = Stream.of(PropertyMode.values()).flatMap(mode -> Stream.of(
            Arguments.of(mode, "program subject { shared int x = 0; thread 0 { atomic { x = 1; } } invariant always (x >= 0); }", 2L,
                List.of("visit:[0]", "visit:[1]", "trace:COMPLETED"),
                new Witness(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.COMPLETED)),
            Arguments.of(mode, "program subject { shared int x = 0; thread 0 { atomic { x = 7; x = 1 % 0; } } invariant always (x >= 0); }", 1L,
                List.of("visit:[0]", "trace:VIOLATION"),
                new Witness(List.of(0), List.of(StepOutcome.ASSERTION_FAILED), TraceOutcome.VIOLATION)),
            Arguments.of(mode, "program subject { shared int x = 0; thread 0 { atomic when (false) { x = 1; } } invariant always (x >= 0); }", 1L,
                List.of("visit:[0]", "trace:DEADLOCK"),
                new Witness(List.of(), List.of(), TraceOutcome.DEADLOCK)),
            Arguments.of(mode, "program subject { shared int x = 0; thread 0 { while (true) limit 0 {} } invariant always (x >= 0); }", 1L,
                List.of("visit:[0]", "trace:INCOMPLETE"),
                new Witness(List.of(), List.of(), TraceOutcome.INCOMPLETE))));
        Stream<Arguments> violations = Stream.of(PropertyMode.OBSERVED, PropertyMode.UNKNOWN).map(mode ->
            Arguments.of(mode, "program subject { shared int x = 0; thread 0 { atomic { x = 1; } } invariant always (x == 0); }", 2L,
                List.of("visit:[0]", "visit:[1]", "trace:VIOLATION"),
                new Witness(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION)));
        return Stream.concat(ordinary, violations);
    }

    /** Constructs the public metadata shape and checks fixture routing preconditions.
     * @param mode desired absence, known observations or unknown observations
     * @param declared pure property with known observations
     * @return property to supply to exploration, or null in NONE mode
     */
    private static Invariant property(PropertyMode mode, Invariant declared) {
        assertTrue(declared.observedLocations().isPresent(), "fixture must declare known observations");
        Invariant result = switch (mode) {
            case NONE -> null;
            case OBSERVED -> declared;
            case UNKNOWN -> (state, config) -> declared.holds(state, config);
        };
        if (mode == PropertyMode.UNKNOWN) assertTrue(result.observedLocations().isEmpty());
        return result;
    }

    /** Extracts public values without using trace identity as an oracle.
     * @param trace actual explorer or callback evidence
     * @return comparable recorded prefix and outcome
     */
    private static Witness witness(Trace trace) {
        return new Witness(trace.threadIds(), trace.outcomes(), trace.outcome());
    }

    /** Public property metadata shapes used to select the traversal mode. */
    private enum PropertyMode {
        /** No invariant. */ NONE,
        /** Known complete state observations. */ OBSERVED,
        /** Pure callback with unknown observation metadata. */ UNKNOWN
    }

    /** Independent evidence tuple.
     * @param threads recorded choices
     * @param outcomes recorded step outcomes
     * @param outcome terminal classification
     */
    private record Witness(List<Integer> threads, List<StepOutcome> outcomes, TraceOutcome outcome) {}

    /** Non-mutating observer of visited positions and terminal evidence. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Ordered public notifications. */
        private final List<String> events = new ArrayList<>();
        /** Actual visited configurations, never mutated by this observer. */
        private final List<Configuration> positions = new ArrayList<>();
        /** Value tuples from terminal callbacks. */
        private final List<Witness> witnesses = new ArrayList<>();
        /** Source loop owners from cutoff callbacks. */
        private final List<Integer> cutoffThreads = new ArrayList<>();

        /** Records the visited counters for source and legacy configurations.
         * @param config newly visited modeled position
         */
        @Override public void onStateVisited(Configuration config) {
            positions.add(config);
            events.add("visit:" + config.programCounters());
        }

        /** Records terminal notification order and independent prefix values.
         * @param trace newly created evidence
         */
        @Override public void onTraceCreated(Trace trace) {
            events.add("trace:" + trace.outcome());
            witnesses.add(witness(trace));
            if (trace.cutoff() != null) cutoffThreads.add(trace.cutoff().threadId());
        }
    }

    /** Pure legacy assertion with complete error dependencies and no hidden mutable state. */
    private static final class AssertFirstFlag implements Step {
        /** Reports the flag determining the assertion outcome.
         * @return complete modeled read footprint
         */
        @Override public Set<MemoryLocation> reads() { return Set.of(MemoryLocation.of("flag[0]")); }

        /** The assertion changes no modeled state.
         * @return empty write footprint
         */
        @Override public Set<MemoryLocation> writes() { return Set.of(); }

        /** The assertion may be attempted in every fixture state.
         * @param state current modeled state, left unchanged
         * @return true
         */
        @Override public boolean enabled(SharedState state) { return true; }

        /** Checks flag zero without mutating execution state.
         * @param state current modeled flags
         * @return ADVANCED if flag zero is true, ASSERTION_FAILED otherwise
         */
        @Override public StepOutcome execute(SharedState state) {
            return ((PetersonState) state).flag(0) ? StepOutcome.ADVANCED : StepOutcome.ASSERTION_FAILED;
        }
    }
}
