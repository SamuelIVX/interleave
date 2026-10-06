/** Verifies explorer result maps retain value-distinct positions despite identical diagnostics. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.MemoryLocation;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.Step;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.bugs.BugCorpus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationIdentityIntegrationTest {
    private static final MemoryLocation VALUE = MemoryLocation.of("value");

    /**
     * Two writes and one read-only step yield 1 initial + 3 one-step + 4 two-step + 2 final
     * configurations. The two write orders differ in value at the same counters, so diagnostic
     * text plus counters must not overwrite one of them in the result map.
     */
    @Test
    void dfsRetainsDistinctValuesWithIdenticalDiagnostics() {
        Set<Position> reached = new HashSet<>();
        DfsResult result = new DfsExplorer().explore(program(), null, null,
            config -> reached.add(position(config)));

        assertEquals(10, reached.size(), "independent value identities observed through the visitor");
        assertEquals(10, result.statesExplored());
        assertEquals(10, result.states().size(), "diagnostic collisions must not overwrite visited values");
        assertEquals(reached, result.states().values().stream()
            .map(ConfigurationIdentityIntegrationTest::position).collect(java.util.stream.Collectors.toSet()));
    }

    /** Both reduced and invariant-guarded paths must retain every value they actually visit. */
    @ParameterizedTest
    @CsvSource({"STATIC_POR,false", "STATIC_POR,true", "DPOR,false", "DPOR,true"})
    void porResultMapsRetainDistinctValuesWithIdenticalDiagnostics(String strategy, boolean checked) {
        Set<Position> reached = new HashSet<>();
        StateVisitor visitor = config -> reached.add(position(config));
        Invariant invariant = checked ? (state, config) -> true : null;
        DfsResult result = strategy.equals("STATIC_POR")
            ? new StaticPorExplorer().explore(program(), invariant, null, visitor)
            : new DporExplorer().explore(program(), invariant, null, visitor);

        assertEquals(10, reached.size(), "the conflicting steps prevent reduction of this fixture");
        assertEquals(reached.size(), result.states().size(), "the result map must not overwrite values");
        assertEquals(reached, result.states().values().stream()
            .map(ConfigurationIdentityIntegrationTest::position).collect(java.util.stream.Collectors.toSet()));
    }

    /**
     * One-step threads make every switch forced. CBS reaches 1 + 3 + 6 + 4 = 14 scheduling positions,
     * including two final values reached with thread 2 last; those must coexist in the result map.
     */
    @Test
    void cbsRetainsValuesWithTheSameLastThreadDespiteIdenticalDiagnostics() {
        Set<SchedulingPosition> reached = new HashSet<>();
        StateVisitor visitor = new StateVisitor() {
            @Override
            public void onStateVisited(Configuration config) {
                throw new AssertionError("CBS must report scheduling identity");
            }

            @Override
            public void onStateVisited(Configuration config, int lastThreadId, int preemptions) {
                reached.add(new SchedulingPosition(position(config), lastThreadId));
            }
        };
        DfsResult result = new ContextBoundedExplorer().explore(program(), null, null, visitor, 2);

        assertEquals(14, reached.size(), "independent value/counter/last-thread identities");
        assertEquals(14, result.statesExplored());
        assertEquals(14, result.states().size(), "same-last-thread values must not overwrite each other");
    }

    /** Lower-cost revisits with new DCL sentinel objects update one value-keyed result entry. */
    @Test
    void cbsRevisitsDoNotDuplicateValueEqualDclPositions() {
        Set<ValuePosition> reached = new HashSet<>();
        StateVisitor visitor = new StateVisitor() {
            @Override
            public void onStateVisited(Configuration config) {
                throw new AssertionError("CBS must report scheduling identity");
            }

            @Override
            public void onStateVisited(Configuration config, int lastThreadId, int preemptions) {
                reached.add(new ValuePosition(config.state().deepCopy(),
                    config.programCounters(), lastThreadId));
            }
        };
        DfsResult result = new ContextBoundedExplorer().explore(
            BugCorpus.doubleCheckedLocking().program(), null, null, visitor, 2);

        assertEquals(reached.size(), result.states().size(),
            "object-reference diagnostics must not split value-equal scheduling positions");
        assertTrue(result.statesExplored() > reached.size(),
            "the fixture must exercise repeated visits, not only unique positions");
    }

    /** Creates two conflicting writes plus a read-only step whose ordering adds no value. */
    private static Program program() {
        return new Program(new DiagnosticState(0), List.of(
            new ModelThread(0, List.of(new ValueStep(1))),
            new ModelThread(1, List.of(new ValueStep(2))),
            new ModelThread(2, List.of(new ValueStep(null)))
        ));
    }

    /** Value identity independent of the encoder/key under test. */
    private static Position position(Configuration config) {
        return new Position(((DiagnosticState) config.state()).value, config.programCounters());
    }

    private record Position(int value, List<Integer> counters) {}
    private record SchedulingPosition(Position position, int lastThreadId) {}
    private record ValuePosition(SharedState state, List<Integer> counters, int lastThreadId) {}

    /** A legitimate state whose diagnostic rendering deliberately omits its value. */
    private static final class DiagnosticState implements SharedState {
        private int value;

        private DiagnosticState(int value) {
            this.value = value;
        }

        @Override
        public SharedState deepCopy() {
            return new DiagnosticState(value);
        }

        @Override
        public void encodeTo(DataOutput out) throws IOException {
            out.writeInt(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DiagnosticState state && value == state.value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(value);
        }

        @Override
        public String toString() {
            return "diagnostic only";
        }
    }

    /** A null value denotes a read-only action; non-null values overwrite the modeled cell. */
    private record ValueStep(Integer value) implements Step {
        @Override
        public Set<MemoryLocation> reads() {
            return Set.of(VALUE);
        }

        @Override
        public Set<MemoryLocation> writes() {
            return value == null ? Set.of() : Set.of(VALUE);
        }

        @Override
        public boolean enabled(SharedState state) {
            return true;
        }

        @Override
        public StepOutcome execute(SharedState state) {
            if (value != null) ((DiagnosticState) state).value = value;
            return StepOutcome.ADVANCED;
        }
    }
}
