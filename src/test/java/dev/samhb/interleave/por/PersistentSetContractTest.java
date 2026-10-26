/** Legacy current-step selection and pre-execution probe-error contracts. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PersistentSetContractTest {
    /** Initial selections retain enabled IDs, independent fallback and only the dependent group.
     * @param threads immutable definitions indexed by owner ID
     * @param expected literal selected owners
     */
    @ParameterizedTest
    @MethodSource("initialSelections")
    void initialSelectionsRespectEnabledOwnersAndCurrentConflicts(List<ModelThread> threads,
            List<Integer> expected) {
        Program program = new Program(PetersonState.of(false, false, 0), threads);
        Configuration initial = program.initialConfiguration();
        assertEquals(expected, new PersistentSetComputer(new IndependenceRelation())
            .computePersistentSet(initial, threads));
        PetersonState state = (PetersonState) initial.state();
        assertFalse(state.flag(0));
        assertFalse(state.flag(1));
        assertEquals(0, state.turn());
    }

    /** Supplies completed, singleton, nonzero independent and mixed-dependency configurations.
     * @return four literal current-step selection cases over genuine legacy models
     */
    private static Stream<Arguments> initialSelections() {
        return Stream.of(
            Arguments.of(List.of(new ModelThread(0, List.of()), new ModelThread(1, List.of())), List.of()),
            Arguments.of(List.of(new ModelThread(0, List.of()),
                new ModelThread(1, List.of(new ReadFlagStep(1, 0)))), List.of(1)),
            Arguments.of(List.of(new ModelThread(0, List.of()),
                new ModelThread(1, List.of(new ReadFlagStep(1, 0))),
                new ModelThread(2, List.of(new ReadFlagStep(2, 1)))), List.of(1)),
            Arguments.of(List.of(new ModelThread(0, List.of(new WriteFlagStep(0, true))),
                new ModelThread(1, List.of(new WriteFlagStep(0, false))),
                new ModelThread(2, List.of(new WriteFlagStep(1, true)))), List.of(0, 1)));
    }

    /** Both sides of a pair must use the actual nonzero PC rather than a consumed conflicting write.
     * @param advancedOwner owner zero or one, separating a spurious dependency from fallback owner zero
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void pairLookupsExcludeTheConsumedWrite(int advancedOwner) {
        Program program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, advancedOwner == 0
                ? List.of(new WriteFlagStep(0, true), new WriteFlagStep(1, true))
                : List.of(new WriteFlagStep(0, false))),
            new ModelThread(1, advancedOwner == 1
                ? List.of(new WriteFlagStep(0, true), new WriteFlagStep(1, true))
                : List.of(new WriteFlagStep(0, false))),
            new ModelThread(2, List.of(new WriteTurnStep(1)))));
        StepTransition transition = TransitionExecutor.execute(program, program.initialConfiguration(), advancedOwner);
        assertEquals(StepOutcome.ADVANCED, transition.outcome());
        assertNull(transition.cutoff());
        Configuration position = transition.successor();
        assertNotNull(position);
        assertEquals(advancedOwner == 0 ? List.of(1, 0, 0) : List.of(0, 1, 0), position.programCounters());
        assertEquals(List.of(0, 1, 2), position.enabledThreadIds());
        assertEquals(List.of(0), new PersistentSetComputer(new IndependenceRelation())
            .computePersistentSet(position, program.threads()));
        PetersonState state = (PetersonState) position.state();
        assertTrue(state.flag(0));
        assertFalse(state.flag(1));
        assertEquals(0, state.turn());
    }

    /** An independent enabled action can throw during the probe before any action is selected. */
    @Test
    void independentProbePreservesDeterministicErrorsAndParentValues() {
        List<ModelThread> threads = List.of(
            new ModelThread(0, List.of(new ReadFlagStep(0, 1))),
            new ModelThread(1, List.of(new ConditionalReadError())));
        var selector = new PersistentSetComputer(new IndependenceRelation());
        Program failing = new Program(PetersonState.of(false, false, 0), threads);
        Configuration initial = failing.initialConfiguration();
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> selector.computePersistentSet(initial, threads));
        assertEquals("flag[0] is false", error.getMessage());
        assertEquals(List.of(0, 0), initial.programCounters());
        assertFalse(((PetersonState) initial.state()).flag(0));
        assertFalse(((PetersonState) initial.state()).flag(1));

        Program successful = new Program(PetersonState.of(true, false, 0), threads);
        Configuration allowed = successful.initialConfiguration();
        assertEquals(List.of(0), selector.computePersistentSet(allowed, threads));
        assertTrue(((PetersonState) allowed.state()).flag(0));
        assertFalse(((PetersonState) allowed.state()).flag(1));
        assertEquals(List.of(0, 0), allowed.programCounters());
    }

    /** Pure legacy read whose execution error depends only on its declared modeled flag. */
    private static final class ConditionalReadError implements Step {
        /** Includes the only value controlling the deterministic error.
         * @return the complete flag-zero read footprint
         */
        @Override public Set<MemoryLocation> reads() { return Set.of(MemoryLocation.of("flag[0]")); }
        /** No successful or failing execution changes modeled values.
         * @return an empty write footprint
         */
        @Override public Set<MemoryLocation> writes() { return Set.of(); }
        /** Both flag values permit an actual attempt on the supported state type.
         * @param state candidate modeled state
         * @return whether this is a Peterson model
         */
        @Override public boolean enabled(SharedState state) { return state instanceof PetersonState; }
        /** Reads the modeled flag and either advances or reports its deterministic error.
         * @param state supported Peterson model state, left unchanged
         * @return ADVANCED when flag zero is true
         * @throws IllegalStateException when flag zero is false
         */
        @Override public StepOutcome execute(SharedState state) {
            if (!((PetersonState) state).flag(0)) throw new IllegalStateException("flag[0] is false");
            return StepOutcome.ADVANCED;
        }
    }
}
