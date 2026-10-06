/** The result of attempting to take one step. */
package dev.samhb.interleave.core;

/**
 * The result of attempting to take one step.
 *
 * <p>{@link #BLOCKED} is the only outcome that leaves the thread's program counter unmoved;
 * {@link Configuration#successor} advances it for every other result, {@link #TERMINATED}
 * included. {@link #ASSERTION_FAILED} never actually reaches {@code successor} -- every explorer
 * records the violation trace and ends that path as soon as it sees the outcome -- so the counter
 * behaviour for that case is defined but unexercised. A scheduler that only chooses among
 * {@code ADVANCED} steps still explores every reachable configuration without stalling.
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
