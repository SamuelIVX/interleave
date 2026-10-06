/** Pins modeled writes and guard/outcome reads that property-aware reduction must see. */
package dev.samhb.interleave.core;

import dev.samhb.interleave.bugs.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StepFootprintTest {
    @ParameterizedTest
    @MethodSource("modeledEffects")
    void footprintsCoverChangedValuesAndExecutionDependencies(
            Step step, SharedState state, Set<MemoryLocation> reads, Set<MemoryLocation> writes) {
        SharedState before = state.deepCopy();
        assertEquals(StepOutcome.ADVANCED, step.execute(state));
        assertNotEquals(before, state, "the fixture must exercise a modeled change");
        assertTrue(step.reads().containsAll(reads), "missing execution/guard dependency: " + reads);
        assertTrue(step.writes().containsAll(writes), "missing modeled write: " + writes);
    }

    private static Stream<Arguments> modeledEffects() {
        DclState locked = DclState.of(false);
        locked.lock(0);
        return Stream.of(
            Arguments.of(new CSEnterStep(0), PetersonState.of(false, false, 0), Set.of(), locations("inCriticalSection")),
            Arguments.of(new CSExitStep(), new PetersonState(new boolean[2], 0, 0), Set.of(), locations("inCriticalSection")),
            Arguments.of(new DclInitSetInitializedStep(0), DclState.of(false), Set.of(), locations("initialized")),
            Arguments.of(new DclLockStep(0), DclState.of(false), locations("locked"), locations("locked", "lockOwner")),
            Arguments.of(new DclUnlockStep(0), locked, locations("locked", "lockOwner"), locations("locked", "lockOwner", "control")),
            Arguments.of(new ReadCounterStep(0), CounterState.of(7), locations("counter"), locations("registers[0]")),
            Arguments.of(new WriteCounterStep(0), CounterState.of(7), locations("registers[0]"), locations("counter", "control")),
            Arguments.of(new ReadSnapshotStep(0), PairState.of(1, 2), locations("high", "low"), locations("observedHigh", "observedLow", "hasObservation"))
        );
    }

    private static Set<MemoryLocation> locations(String... names) {
        return Stream.of(names).map(MemoryLocation::of).collect(java.util.stream.Collectors.toSet());
    }
}
