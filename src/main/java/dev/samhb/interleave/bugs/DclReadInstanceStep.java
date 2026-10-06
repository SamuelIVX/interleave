/** Models an instance read through its dependency footprint without mutating shared state. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models an instance read through its dependency footprint without mutating shared state. */
public final class DclReadInstanceStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates dcl read instance step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public DclReadInstanceStep(int threadId) {
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
        return Collections.emptySet();
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        return state instanceof DclState;
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
        if (!(o instanceof DclReadInstanceStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
