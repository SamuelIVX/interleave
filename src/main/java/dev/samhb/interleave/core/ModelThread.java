/** A thread's id and its ordered steps. */
package dev.samhb.interleave.core;

import java.util.List;

/**
 * A thread's id and its ordered steps.
 *
 * <p>Position is deliberately not tracked here. Every program counter lives in {@link Configuration},
 * which is the only thing that knows which thread just stepped, so a counter kept on this class would
 * be a second source of truth with nothing able to keep the two in sync. Callers that need to know
 * where a thread is read it from the configuration's counters.
 *
 * <p>This class previously carried a mutable {@code pc} with {@code advance()}, {@code terminated()},
 * {@code nextStep()} and {@code enabled()}. None had a caller outside this file. See
 * {@code docs/specs/active/13-deferred-debt/02-model-thread-dead-pc.md}.
 */
public final class ModelThread {
    /** Id. */
    private final int id;
    /** Steps. */
    private final List<Step> steps;

    /**
     * Creates model thread from the supplied values.
     * @param id zero-based modeled thread ID
     * @param steps ordered atomic steps for this thread
     */
    public ModelThread(int id, List<Step> steps) {
        if (steps == null) throw new IllegalArgumentException("steps must not be null");
        this.id = id;
        this.steps = List.copyOf(steps);
    }

    /**
     * Returns id for this model thread.
     * @return zero-based thread identifier
     */
    public int id() {
        return id;
    }

    /**
     * Returns steps for this model thread.
     * @return immutable ordered atomic steps
     */
    public List<Step> steps() {
        return steps;
    }
}
