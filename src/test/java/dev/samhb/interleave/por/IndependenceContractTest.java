/** Literal alias and source-probe contracts without synthetic enabledness or hidden execution state. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class IndependenceContractTest {
    /** Alias symmetry applies to visibility and either direction of a write/read dependency.
     * @param left scalar, array base or literal element
     * @param right comparison location
     * @param overlap literal alias oracle, independently enumerated from the names
     */
    @ParameterizedTest
    @CsvSource({"x,x,true", "x,y,false", "arr,arr[0],true", "arr[0],arr[1],false", "arr,array[0],false"})
    void aliasDirectionsUseTheSameLiteralConflict(String left, String right, boolean overlap) {
        Set<MemoryLocation> a = Set.of(MemoryLocation.of(left));
        Set<MemoryLocation> b = Set.of(MemoryLocation.of(right));
        var relation = new IndependenceRelation();
        assertEquals(overlap, relation.writesOverlap(a, b));
        assertEquals(overlap, relation.writesOverlap(b, a));
        assertEquals(!overlap, relation.areIndependent(Set.of(), a, b, Set.of()));
        assertEquals(!overlap, relation.areIndependent(b, Set.of(), Set.of(), a));
        assertEquals(!overlap, relation.areIndependent(Set.of(), a, Set.of(), b));
        assertTrue(relation.areIndependent(a, Set.of(), b, Set.of()));
        assertFalse(relation.writesOverlap(Set.of(), b));
        assertFalse(relation.writesOverlap(a, Set.of()));
    }

    /** Source successors must provide actual values for both enabling and disabling comparisons.
     * @param guard condition that changes truth after x becomes one
     * @param beforeEnabled literal initial guard truth
     */
    @ParameterizedTest
    @CsvSource({"x == 0,true", "x == 1,false"})
    void sourceProbeUsesTheSuccessorForGuardChanges(String guard, boolean beforeEnabled) {
        Program program = source("thread 0 { atomic { x = 1; } } "
            + "thread 1 { atomic when (" + guard + ") { y = 1; } }");
        Configuration initial = program.initialConfiguration();
        assertEquals(beforeEnabled, initial.enabledThreadIds().contains(1));
        assertTrue(new IndependenceRelation().hasEnableDisableInterference(initial, 0, 1, program.threads()));
        assertPosition(initial, List.of(0, 0), 0, 0);
    }

    /** A consumed guard is false, but the selected owner's actual next action enables the other guard. */
    @Test
    void selectedOwnerProbeUsesItsNonzeroCurrentPc() {
        Program program = source("thread 0 { atomic when (x == 0) { x = 1; } atomic { x = 2; } } "
            + "thread 1 { atomic when (x == 2) { y = 1; } }");
        Configuration position = advance(program, program.initialConfiguration(), 0);
        assertEquals(List.of(0), position.enabledThreadIds());
        assertTrue(new IndependenceRelation().hasEnableDisableInterference(position, 0, 1, program.threads()));
        assertPosition(position, List.of(1, 0), 1, 0);
    }

    /** The other owner's consumed unconditional action cannot replace its current guarded action. */
    @Test
    void otherOwnerProbeUsesItsNonzeroCurrentPc() {
        Program program = source("thread 0 { atomic { x = 1; } } "
            + "thread 1 { atomic {} atomic when (x == 1) { y = 1; } }");
        Configuration position = advance(program, program.initialConfiguration(), 1);
        assertEquals(List.of(0), position.enabledThreadIds());
        assertTrue(new IndependenceRelation().hasEnableDisableInterference(position, 0, 1, program.threads()));
        assertPosition(position, List.of(0, 1), 0, 0);
    }

    /** Self pairs and either completed owner are rejected before attempting a transition. */
    @Test
    void selfAndCompletedOwnersDoNotInterfere() {
        Program program = source("thread 0 { atomic { x = 1; } } "
            + "thread 1 { atomic when (x == 0) { y = 1; } }");
        Configuration position = advance(program, program.initialConfiguration(), 1);
        var relation = new IndependenceRelation();
        assertFalse(relation.hasEnableDisableInterference(position, 0, 0, program.threads()));
        assertFalse(relation.hasEnableDisableInterference(position, 0, 1, program.threads()));
        assertFalse(relation.hasEnableDisableInterference(position, 1, 0, program.threads()));
        assertPosition(position, List.of(0, 1), 0, 1);
    }

    /** A disabled selected owner cannot change another action's enabledness. */
    @Test
    void disabledSelectedOwnerDoesNotExecute() {
        Program program = source("thread 0 { atomic when (x == 1) { x = 2; } } "
            + "thread 1 { atomic { y = 1; } }");
        Configuration initial = program.initialConfiguration();
        assertEquals(List.of(1), initial.enabledThreadIds());
        assertFalse(new IndependenceRelation().hasEnableDisableInterference(initial, 0, 1, program.threads()));
        assertPosition(initial, List.of(0, 0), 0, 0);
    }

    /** Failed/cut-off source attempts have no committed successor and conservatively interfere.
     * @param body source atomic body containing a tentative write before terminal evidence
     * @param cutoff whether this fixture hits a loop limit rather than a runtime error
     */
    @ParameterizedTest
    @MethodSource("terminalBodies")
    void sourceTerminalAttemptsConservativelyInterfere(String body, boolean cutoff) {
        Program program = source("thread 0 { atomic { " + body + " } } "
            + "thread 1 { atomic when (x == 0) { y = 1; } }");
        Configuration initial = program.initialConfiguration();
        StepTransition attempt = TransitionExecutor.execute(program, initial, 0);
        assertNull(attempt.successor());
        if (cutoff) {
            assertNotNull(attempt.cutoff());
            assertEquals(CutoffEvidence.Reason.LOOP_LIMIT, attempt.cutoff().reason());
        } else {
            assertNotNull(attempt.failure());
            assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, attempt.failure().kind());
            assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, attempt.failure().category());
        }
        assertTrue(new IndependenceRelation().hasEnableDisableInterference(initial, 0, 1, program.threads()));
        assertPosition(initial, List.of(0, 0), 0, 0);
    }

    /** Supplies two real source terminal paths after a tentative x write.
     * @return runtime-error and zero-limit cutoff bodies with literal classifications
     */
    private static Stream<Arguments> terminalBodies() {
        return Stream.of(Arguments.of("x = 1; x = 1 % 0;", false),
            Arguments.of("x = 1; while (true) limit 0 { x = 2; }", true));
    }

    /** Compiles the shared two-field probe fixture through the real source frontend.
     * @param threads complete source thread declarations
     * @return validated source program with immutable definitions
     */
    private static Program source(String threads) {
        return new SourceProgramLoader().load("program probe { shared int x = 0; shared int y = 0; "
            + threads + " }").program();
    }

    /** Commits a real enabled source action to establish a nonzero or completed PC.
     * @param program actual compiled source model
     * @param position unmodified parent configuration
     * @param owner selected enabled owner
     * @return derived successful successor
     */
    private static Configuration advance(Program program, Configuration position, int owner) {
        StepTransition transition = TransitionExecutor.execute(program, position, owner);
        assertEquals(StepOutcome.ADVANCED, transition.outcome());
        assertNotNull(transition.successor());
        return transition.successor();
    }

    /** Checks every shared value and owner counter against independent literal inputs.
     * @param position actual parent after a probe
     * @param counters expected committed counters
     * @param x expected shared x
     * @param y expected shared y
     */
    private static void assertPosition(Configuration position, List<Integer> counters, int x, int y) {
        assertEquals(counters, position.programCounters());
        assertEquals(x, ((DynamicState) position.state()).getInt("x"));
        assertEquals(y, ((DynamicState) position.state()).getInt("y"));
    }
}
