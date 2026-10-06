package dev.samhb.interleave.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Covers how {@link Configuration} derives {@code enabledThreadIds}, {@code allTerminated} and
 * {@code deadlockCandidate} in {@code initial} and {@code successor}.
 *
 * <p>Both factories now route through one private derivation, so these tests are as much about the
 * rule that survived the refactor as about the two entry points: a thread is enabled exactly when its
 * counter sits inside its step list and the step found there permits it, and a configuration is a
 * deadlock candidate when at least one thread is still live <em>and</em> nothing is enabled.
 */
class ConfigurationDerivationTest {

    private static SharedState state() {
        return new CounterState(0);
    }

    /** Enabled-ness fixed at construction, so the counters are the only variable under test. */
    private record FixedStep(boolean canRun) implements Step {
        @Override
        public Set<MemoryLocation> reads() {
            return Set.of();
        }

        @Override
        public Set<MemoryLocation> writes() {
            return Set.of();
        }

        @Override
        public boolean enabled(SharedState state) {
            return canRun;
        }

        @Override
        public StepOutcome execute(SharedState state) {
            return StepOutcome.ADVANCED;
        }
    }

    private static ModelThread thread(int id, boolean... stepsEnabled) {
        Step[] steps = new Step[stepsEnabled.length];
        for (int i = 0; i < stepsEnabled.length; i++) {
            steps[i] = new FixedStep(stepsEnabled[i]);
        }
        return new ModelThread(id, List.of(steps));
    }

    @Test
    void initialPlacesEveryThreadAtZero() {
        Configuration c = Configuration.initial(state(), List.of(thread(0, true), thread(1, true)));

        assertEquals(List.of(0, 0), c.programCounters());
    }

    @Test
    void initialDerivesEnabledFromTheStepAtZero() {
        Configuration c = Configuration.initial(state(), List.of(thread(0, false), thread(1, true)));

        assertEquals(List.of(1), c.enabledThreadIds());
    }

    /**
     * A thread with no steps is terminated from the outset. This is the edge the removed
     * {@code terminated()} also handled, and the reason "terminated is always false" would have been
     * the wrong way to put it.
     */
    @Test
    void initialTreatsAThreadWithNoStepsAsAlreadyTerminated() {
        Configuration c = Configuration.initial(state(), List.of(thread(0)));

        assertTrue(c.allTerminated());
        assertFalse(c.isDeadlockCandidate());
        assertEquals(List.of(), c.enabledThreadIds());
    }

    @Test
    void anEmptyStepThreadDoesNotHideALiveThread() {
        Configuration c = Configuration.initial(state(), List.of(thread(0), thread(1, true)));

        assertFalse(c.allTerminated());
        assertEquals(List.of(1), c.enabledThreadIds());
    }

    @Test
    void advancedSuccessorAdvancesOnlyTheSteppingThread() {
        List<ModelThread> threads = List.of(thread(0, true, true), thread(1, true, true));
        Configuration start = Configuration.initial(state(), threads);

        Configuration next = start.successor(0, StepOutcome.ADVANCED, threads, state());

        assertEquals(List.of(1, 0), next.programCounters());
        assertEquals(StepOutcome.ADVANCED, next.lastOutcome());
    }

    @Test
    void blockedSuccessorAdvancesNothing() {
        List<ModelThread> threads = List.of(thread(0, false, true), thread(1, true));
        Configuration start = Configuration.initial(state(), threads);

        Configuration next = start.successor(0, StepOutcome.BLOCKED, threads, state());

        assertEquals(List.of(0, 0), next.programCounters());
        assertEquals(StepOutcome.BLOCKED, next.lastOutcome());
    }

    /**
     * A blocked thread stays on the same counter, so the derivation must read the step at that
     * unchanged counter. This is the case the old {@code successor} ternary special-cased, choosing
     * between the old and new counter for the stepping thread; the counter it settled on is the same
     * one either way, so the branch was noise and this pins the result that removing it preserved.
     */
    @Test
    void blockedThreadIsStillJudgedByTheStepItRemainsOn() {
        List<ModelThread> threads = List.of(thread(0, false, true), thread(1, true));
        Configuration start = Configuration.initial(state(), threads);
        assertEquals(List.of(1), start.enabledThreadIds());

        Configuration blocked = start.successor(0, StepOutcome.BLOCKED, threads, state());
        assertEquals(List.of(1), blocked.enabledThreadIds());

        // Stepping past the disabled first step is what finally enables thread 0.
        Configuration advanced = start.successor(0, StepOutcome.ADVANCED, threads, state());
        assertEquals(List.of(0, 1), advanced.enabledThreadIds());
    }

    @Test
    void advancingOntoADisabledStepRemovesTheThreadFromEnabled() {
        List<ModelThread> threads = List.of(thread(0, true, false), thread(1, true));
        Configuration start = Configuration.initial(state(), threads);
        assertEquals(List.of(0, 1), start.enabledThreadIds());

        Configuration next = start.successor(0, StepOutcome.ADVANCED, threads, state());

        assertEquals(List.of(1), next.enabledThreadIds());
    }

    @Test
    void steppingPastTheLastStepTerminatesTheThread() {
        List<ModelThread> threads = List.of(thread(0, true), thread(1, true));
        Configuration start = Configuration.initial(state(), threads);

        Configuration next = start.successor(0, StepOutcome.ADVANCED, threads, state());

        assertEquals(List.of(1, 0), next.programCounters());
        assertFalse(next.allTerminated());
        assertFalse(next.isDeadlockCandidate());
    }

    /**
     * All threads terminated and none enabled is a finished exploration, not a deadlock. Getting this
     * wrong in the other direction reports a phantom; getting it wrong here stops a search early.
     */
    @Test
    void everyThreadTerminatedIsNotADeadlockCandidate() {
        List<ModelThread> threads = List.of(thread(0, false), thread(1, false));
        Configuration start = Configuration.initial(state(), threads);
        assertFalse(start.allTerminated());

        // Both threads have to step off their only step; advancing one leaves the other live.
        Configuration next = start.successor(0, StepOutcome.ADVANCED, threads, state())
                .successor(1, StepOutcome.ADVANCED, threads, state());

        assertEquals(List.of(1, 1), next.programCounters());
        assertEquals(List.of(), next.enabledThreadIds());
        assertTrue(next.allTerminated());
        assertFalse(next.isDeadlockCandidate());
    }

    @Test
    void liveThreadsWithNothingEnabledIsADeadlockCandidate() {
        List<ModelThread> threads = List.of(thread(0, false), thread(1, true));
        Configuration start = Configuration.initial(state(), threads);
        assertFalse(start.allTerminated());
        assertEquals(List.of(1), start.enabledThreadIds());

        Configuration next = start.successor(1, StepOutcome.ADVANCED, threads, state());

        assertFalse(next.allTerminated());
        assertEquals(List.of(), next.enabledThreadIds());
        assertTrue(next.isDeadlockCandidate());
    }

    /**
     * The two factories must agree about a set of counters, since they now share one derivation.
     * Advancing a disabled first step and starting a thread whose only step is the second step both
     * mean "this thread sits on a step that will not run".
     */
    @Test
    void theTwoFactoriesAgreeAboutTheSamePosition() {
        List<ModelThread> threads = List.of(thread(0, false, true), thread(1, true));

        Configuration advanced = Configuration.initial(state(), threads)
                .successor(0, StepOutcome.ADVANCED, threads, state());
        Configuration direct = Configuration.initial(state(),
                List.of(thread(0, true), thread(1, true)));

        assertEquals(direct.enabledThreadIds(), advanced.enabledThreadIds());
        assertEquals(direct.allTerminated(), advanced.allTerminated());
        assertEquals(direct.isDeadlockCandidate(), advanced.isDeadlockCandidate());
    }
}
