/** Literal selection contracts for source suffix components and complete property observations. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class PropertyPersistentSetContractTest {
    /** The selected component is sorted even when its dependency path discovers owners as 1,3,2. */
    @Test
    void componentOrderIsIndependentOfDiscoveryOrder() {
        Program program = new SourceProgramLoader().load("""
            program order { shared int observed = 0; shared int x = 0;
              shared int y = 0; shared int z = 0;
              thread 0 { atomic { observed = 1; } }
              thread 1 { atomic { x = 1; } }
              thread 2 { atomic { y = 1; } }
              thread 3 { atomic { z = x + y; } }
            }
            """).program();
        assertEquals(Set.of(MemoryLocation.of("x"), MemoryLocation.of("y")),
            program.threads().get(3).steps().getFirst().reads());
        var selector = new PropertyPersistentSetComputer(program.threads(),
            Set.of(MemoryLocation.of("observed")), new IndependenceRelation());
        assertEquals(List.of(1, 2, 3), selector.compute(program.initialConfiguration()));
    }

    /** Visibility excludes components before size and sorted owner IDs select the remaining candidate.
     * @param observations complete observed fields for this source model
     * @param expected literal sorted owner selection
     */
    @ParameterizedTest
    @MethodSource("selectionCases")
    void smallestInvisibleComponentWinsWithDeterministicTies(Set<MemoryLocation> observations,
            List<Integer> expected) {
        Program program = new SourceProgramLoader().load("""
            program choices { shared int x = 0; shared int y = 0; shared int z = 0;
              thread 0 { atomic { x = 1; } }
              thread 1 { atomic { x = 2; } }
              thread 2 { atomic { y = 1; } }
              thread 3 { atomic { z = 1; } }
            }
            """).program();
        var selector = new PropertyPersistentSetComputer(program.threads(), observations, new IndependenceRelation());
        assertEquals(expected, selector.compute(program.initialConfiguration()));
    }

    /** Supplies visibility sets with two singleton candidates, one, a larger component, or no candidate.
     * @return four independent literal selection oracles for the same dependency graph
     */
    private static Stream<Arguments> selectionCases() {
        return Stream.of(
            Arguments.of(Set.of(), List.of(2)),
            Arguments.of(Set.of(MemoryLocation.of("y")), List.of(3)),
            Arguments.of(Set.of(MemoryLocation.of("y"), MemoryLocation.of("z")), List.of(0, 1)),
            Arguments.of(Set.of(MemoryLocation.of("x"), MemoryLocation.of("y"), MemoryLocation.of("z")),
                List.of(0, 1, 2, 3)));
    }

    /** Actual source transitions remove consumed dependencies and reach singleton and completed boundaries. */
    @Test
    void consumedFootprintsDoNotConnectRemainingComponents() {
        Program program = new SourceProgramLoader().load("""
            program suffix { shared int x = 0; shared int y = 0; shared int z = 0;
              thread 0 { atomic { x = 1; } }
              thread 1 { atomic { x = 2; } atomic { y = 1; } }
              thread 2 { atomic { z = 1; } }
            }
            """).program();
        var selector = new PropertyPersistentSetComputer(program.threads(), Set.of(), new IndependenceRelation());
        Configuration position = advance(program, program.initialConfiguration(), 1);
        assertEquals(List.of(0, 1, 0), position.programCounters());
        assertEquals(2, ((DynamicState) position.state()).getInt("x"));
        assertEquals(List.of(0), selector.compute(position));
        position = advance(program, position, 1);
        assertEquals(List.of(0, 2, 0), position.programCounters());
        assertEquals(List.of(0), selector.compute(position));
        position = advance(program, position, 0);
        assertEquals(List.of(1, 2, 0), position.programCounters());
        assertEquals(List.of(2), selector.compute(position));
        position = advance(program, position, 2);
        assertTrue(position.allTerminated());
        assertEquals(List.of(), selector.compute(position));
    }

    /** The selected seed's own nonzero PC excludes its consumed write before testing dependencies. */
    @Test
    void selectedOwnerUsesItsCurrentSuffix() {
        Program program = new SourceProgramLoader().load("""
            program seed { shared int x = 0; shared int y = 0; shared int z = 0;
              thread 0 { atomic { x = 2; } atomic { y = 1; } }
              thread 1 { atomic { x = 1; } }
              thread 2 { atomic { z = 1; } }
            }
            """).program();
        var selector = new PropertyPersistentSetComputer(program.threads(), Set.of(), new IndependenceRelation());
        Configuration position = advance(program, program.initialConfiguration(), 0);
        assertEquals(List.of(1, 0, 0), position.programCounters());
        assertEquals(2, ((DynamicState) position.state()).getInt("x"));
        assertEquals(List.of(0), selector.compute(position));
    }

    /** A disabled but live guard belongs to the suffix graph and disqualifies its connected component. */
    @Test
    void disabledLiveMembersPreventSelectingTheirComponent() {
        Program program = new SourceProgramLoader().load("""
            program disabled { shared int x = 0; shared int y = 0; shared int z = 0;
              thread 0 { atomic { x = 1; } }
              thread 1 { atomic when (x == 2) { y = 1; } }
              thread 2 { atomic { z = 1; } }
            }
            """).program();
        Configuration initial = program.initialConfiguration();
        assertEquals(List.of(0, 2), initial.enabledThreadIds());
        var selector = new PropertyPersistentSetComputer(program.threads(), Set.of(), new IndependenceRelation());
        assertEquals(List.of(2), selector.compute(initial));
    }

    /** Constructor observations are a value snapshot, and metadata selection does not execute source actions. */
    @Test
    void observationSnapshotDoesNotFollowCallerMutations() {
        Program program = new SourceProgramLoader().load("""
            program snapshot { shared int x = 0; shared int y = 0;
              thread 0 { atomic { x = 1; } }
              thread 1 { atomic { y = 1; } }
            }
            """).program();
        var observations = new HashSet<>(Set.of(MemoryLocation.of("x")));
        var selector = new PropertyPersistentSetComputer(program.threads(), observations, new IndependenceRelation());
        Configuration initial = program.initialConfiguration();
        assertEquals(List.of(1), selector.compute(initial));
        observations.clear();
        observations.add(MemoryLocation.of("y"));
        assertEquals(List.of(1), selector.compute(initial));
        assertEquals(List.of(0, 0), initial.programCounters());
        assertEquals(0, ((DynamicState) initial.state()).getInt("x"));
        assertEquals(0, ((DynamicState) initial.state()).getInt("y"));
    }

    /** Commits a genuine successful source action without fabricating counters or enabledness.
     * @param program immutable compiled source model
     * @param position actual parent configuration
     * @param owner enabled action owner
     * @return derived committed successor
     */
    private static Configuration advance(Program program, Configuration position, int owner) {
        StepTransition transition = TransitionExecutor.execute(program, position, owner);
        assertEquals(StepOutcome.ADVANCED, transition.outcome());
        assertNull(transition.cutoff());
        assertNotNull(transition.successor());
        return transition.successor();
    }
}
