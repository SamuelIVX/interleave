/** Publishes the instance value during modeled double-checked initialization. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Publishes the instance value during modeled double-checked initialization. */
public final class DclInitSetInstanceStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates dcl init set instance step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public DclInitSetInstanceStep(int threadId) {
        this.threadId = threadId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("instance"));
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(
            MemoryLocation.of("instance"),
            MemoryLocation.of("control")
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        return state instanceof DclState;
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        DclState ds = (DclState) state;
        ds.setInstance(new Object());
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclInitSetInstanceStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
