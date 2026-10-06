/** Models waiting for another thread’s intent flag to clear. */
package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models waiting for another thread’s intent flag to clear. */
public final class UnconditionalWaitStep implements Step {
    /** Thread id. */
    private final int threadId;
    /** Other id. */
    private final int otherId;

    /**
     * Creates unconditional wait step from the supplied values.
     * @param threadId zero-based modeled thread ID
     * @param otherId other thread involved in the wait condition
     */
    public UnconditionalWaitStep(int threadId, int otherId) {
        this.threadId = threadId;
        this.otherId = otherId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("flag[" + otherId + "]"));
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        return Collections.emptySet();
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DeadlockState ds)) return false;
        return !ds.flag(otherId);
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnconditionalWaitStep that)) return false;
        return threadId == that.threadId && otherId == that.otherId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId, otherId);
    }
}
