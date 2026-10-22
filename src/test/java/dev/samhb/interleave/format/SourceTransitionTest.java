/** Direct source transition contracts for blocked attempts and noncommitting inner-loop cutoffs. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceTransitionTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** A defensive direct call at a false guard preserves the selected owner's nonzero PC and state. */
    @Test
    void disabledGuardAttemptPreservesNonzeroParentPosition() {
        var model = loader.load("""
            program blocked { shared int counter = 0;
              thread 0 {}
              thread 1 { atomic { counter = 7; } atomic when (false) { counter = 99; } }
            }
            """);
        var program = model.program();
        Configuration parent = TransitionExecutor.execute(program, program.initialConfiguration(), 1).successor();
        assertEquals(List.of(0, 1), parent.programCounters());
        assertEquals(List.of(), parent.enabledThreadIds());
        SourceStep guarded = (SourceStep) program.threads().get(1).steps().get(1);
        assertFalse(guarded.enabled(parent.state()));

        // Schedulers skip disabled choices; the direct protocol still promises a defensive BLOCKED edge.
        StepTransition result = guarded.transition(program.threads(), parent, 1, parent.state().deepCopy());
        assertEquals(StepOutcome.BLOCKED, result.outcome());
        assertNull(result.failure());
        assertNull(result.cutoff());
        Configuration blocked = result.successor();
        assertNotNull(blocked);
        assertEquals(List.of(0, 1), blocked.programCounters());
        assertEquals(7, ((DynamicState) blocked.state()).getInt("counter"));
        assertEquals(List.of(), blocked.enabledThreadIds());
        assertFalse(blocked.allTerminated());
        assertTrue(blocked.isDeadlockCandidate());
        assertEquals(StepOutcome.BLOCKED, blocked.lastOutcome());
        assertEquals(List.of(0, 1), parent.programCounters());
        assertEquals(7, ((DynamicState) parent.state()).getInt("counter"));
        assertEquals(StepOutcome.ADVANCED, parent.lastOutcome());
    }

    /** An atomic cutoff identifies the inner loop while publishing neither tentative state nor a successor. */
    @Test
    void innerCutoffRetainsExactLocationAndCommittedParent() {
        var model = loader.load("""
            program cut {
              shared int counter = 0;
              thread 0 {}
              thread 1 {
                atomic { counter = 7; }
                atomic {
                  counter = 9;
                  while (true) limit 0 { counter = 11; }
                }
              }
            }
            """);
        var program = model.program();
        Configuration parent = TransitionExecutor.execute(program, program.initialConfiguration(), 1).successor();
        StepTransition result = TransitionExecutor.execute(program, parent, 1);
        assertNull(result.outcome());
        assertNull(result.successor());
        assertNull(result.failure());
        assertEquals(new CutoffEvidence(CutoffEvidence.Reason.LOOP_LIMIT, 1, 1, 0, 0,
            new SourceLocation("<source>", 8, 7, 8, 45),
            List.of(new ExecutedStep.Expansion(133, 0))), result.cutoff());
        assertEquals(List.of(0, 1), parent.programCounters());
        assertEquals(7, ((DynamicState) parent.state()).getInt("counter"));
        assertEquals(List.of(1), parent.enabledThreadIds());
        assertEquals(StepOutcome.ADVANCED, parent.lastOutcome());
    }
}
