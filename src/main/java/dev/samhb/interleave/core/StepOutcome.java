package dev.samhb.interleave.core;

/**
 * The result of attempting to take one step.
 *
 * <p>Only {@link #ADVANCED} denotes real progress. The remaining three all leave the thread's
 * program counter unmoved, so a scheduler that only chooses among {@code ADVANCED} steps explores
 * every reachable configuration without stalling.
 */
public enum StepOutcome {

    /** The step ran and mutated the shared state. */
    ADVANCED,

    /**
     * The step was attempted but could not proceed -- a lock was held, a condition was false, or a
     * spin loop did not terminate. Retrying it later, after another thread has released what it
     * needs, may succeed.
     */
    BLOCKED,

    /**
     * The thread has run out of steps and is finished. Selecting a terminated thread is never a
     * meaningful scheduling choice, which is why a context switch away from one is free of charge.
     */
    TERMINATED,

    /**
     * The step ran and detected the bug the invariant is looking for. Carries the same severity as
     * {@code ADVANCED} for scheduling purposes -- the schedule is still a real, replayable one.
     */
    ASSERTION_FAILED
}
