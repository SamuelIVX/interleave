/** Checks whether a previously read instance can be used safely. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Checks whether a previously read instance can be used safely. */
public final class DclUseInstanceStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates dcl use instance step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public DclUseInstanceStep(int threadId) {
        this.threadId = threadId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(
            MemoryLocation.of("instance"),
            MemoryLocation.of("control")
        );
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(MemoryLocation.of("observedInstance"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DclState ds)) return false;
        return ds.instance() != null;
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        DclState ds = (DclState) state;
        ds.setObservedInstance(ds.instance());
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclUseInstanceStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
