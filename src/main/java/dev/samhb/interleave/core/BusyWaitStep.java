/** Models a Peterson wait whose guard depends on intent flags and turn. */
package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models a Peterson wait whose guard depends on intent flags and turn. */
public final class BusyWaitStep implements Step {
    /** Thread id. */
    private final int threadId;
    /** Other id. */
    private final int otherId;

    /**
     * Creates busy wait step from the supplied values.
     * @param threadId zero-based modeled thread ID
     * @param otherId other thread involved in the wait condition
     */
    public BusyWaitStep(int threadId, int otherId) {
        this.threadId = threadId;
        this.otherId = otherId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return java.util.Set.of(
            MemoryLocation.of("flag[" + otherId + "]"),
            MemoryLocation.of("turn")
        );
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        return Collections.emptySet();
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof PetersonState ps)) return false;
        return !(ps.flag(otherId) && ps.turn() == otherId);
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
        if (!(o instanceof BusyWaitStep that)) return false;
        return threadId == that.threadId && otherId == that.otherId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId, otherId);
    }
}
