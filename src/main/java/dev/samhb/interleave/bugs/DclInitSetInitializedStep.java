/** Models DCL initialization with explicit property-visible state writes. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Marks the modeled instance as initialized. */
public final class DclInitSetInitializedStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates dcl init set initialized step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public DclInitSetInitializedStep(int threadId) {
        this.threadId = threadId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("instance"));
    }

    /**
     * Includes initialization and the existing conservative DCL dependencies.
     * @return stable over-approximated writes footprint
     */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(
            MemoryLocation.of("initialized"),
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
        ds.setInitialized(true);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclInitSetInitializedStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
