package dev.samhb.interleave.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link Configuration#forTest}, the fixture seam that replaced reflection into
 * {@code Configuration}'s private constructor.
 *
 * <p>The property worth protecting is not that the factory fills in fields, it is that the two
 * derived booleans cannot be supplied independently of the counters and enabled set they are derived
 * from. Before this existed, callers reached past the API and passed all three, which is how both
 * reflection helpers ended up hardcoding {@code false, false} and building fixtures that were
 * structurally incapable of being terminal or deadlocked.
 */
class ConfigurationFactoryTest {

    /** Counts as a state; the factory stores the reference and never inspects it. */
    private static SharedState state() {
        return new CounterState(0);
    }

    @Test
    void livePositionsAreNotAllTerminated() {
        Configuration c = Configuration.forTest(state(), List.of(0, 3), List.of(0, 1));

        assertFalse(c.allTerminated());
        assertFalse(c.isDeadlockCandidate());
        assertEquals(List.of(0, 3), c.programCounters());
    }

    /**
     * Empty enabled set with live threads is exactly the deadlock shape, and it is now reachable
     * without asserting {@code deadlock = true} by hand. Previously this combination was
     * unconstructible through the reflection helpers, which always passed a non-empty enabled list.
     */
    @Test
    void noEnabledThreadWithLivePositionsIsADeadlockCandidate() {
        Configuration c = Configuration.forTest(state(), List.of(0, 3), List.of());

        assertFalse(c.allTerminated());
        assertTrue(c.isDeadlockCandidate());
    }

    @Test
    void counterAtOrPastStepCountIsATerminatedPosition() {
        Configuration c = Configuration.forTest(state(), List.of(4, 7), List.of(4, 4), List.of(0));

        assertTrue(c.allTerminated());
    }

    /**
     * The {@code !allTerminated} half of {@code deadlock = !allTerminated && enabled.isEmpty()}. With
     * every thread terminated and nothing enabled, this is a finished exploration rather than a
     * deadlock, and conflating the two is what makes a search stop early or report a phantom.
     */
    @Test
    void allTerminatedWithNothingEnabledIsNotADeadlockCandidate() {
        Configuration c = Configuration.forTest(state(), List.of(4, 7), List.of(4, 4), List.of());

        assertTrue(c.allTerminated());
        assertFalse(c.isDeadlockCandidate());
    }

    @Test
    void oneLiveThreadAmongTerminatedOnesIsNeither() {
        Configuration c = Configuration.forTest(state(), List.of(1, 4), List.of(4, 4), List.of());

        assertFalse(c.allTerminated());
        assertTrue(c.isDeadlockCandidate());
    }

    @Test
    void zeroThreadsIsVacuouslyAllTerminated() {
        Configuration c = Configuration.forTest(state(), List.of(), List.of());

        assertTrue(c.allTerminated());
        assertFalse(c.isDeadlockCandidate());
    }

    @Test
    void mismatchedStepCountCountIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Configuration.forTest(state(), List.of(0, 1), List.of(4), List.of(0)));

        assertTrue(e.getMessage().contains("2 counters"), e.getMessage());
        assertTrue(e.getMessage().contains("1 step counts"), e.getMessage());
    }

    @Test
    void lockOwnershipAndWaitQueuesAreEmpty() {
        Configuration c = Configuration.forTest(state(), List.of(0), List.of(0));

        assertTrue(c.lockOwnership().isEmpty());
        assertTrue(c.waitQueues().isEmpty());
    }

    /** A fixture must not be mutable through the caller's list after the fact. */
    @Test
    void countersAreDefensivelyCopied() {
        List<Integer> counters = new ArrayList<>(List.of(0, 1));
        Configuration c = Configuration.forTest(state(), counters, List.of(0));
        counters.set(0, 99);

        assertEquals(List.of(0, 1), c.programCounters());
    }

    /**
     * The factory sets no last outcome, because it describes a starting position rather than a step.
     * A fixture that reported an outcome would let a store test pass for the wrong reason.
     */
    @Test
    void factoryLeavesLastOutcomeUnset() {
        assertEquals(null, Configuration.forTest(state(), List.of(0), List.of(0)).lastOutcome());
    }
}
