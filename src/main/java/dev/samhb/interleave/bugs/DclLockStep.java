/** Models DCL lock acquisition with complete ownership footprints. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models lock acquisition for double-checked initialization. */
public final class DclLockStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates dcl lock step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public DclLockStep(int threadId) {
        this.threadId = threadId;
    }

    /**
     * Includes lock-enabledness dependencies and the conservative lock alias.
     * @return stable over-approximated reads footprint
     */
    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(MemoryLocation.of("lock"), MemoryLocation.of("locked"), MemoryLocation.of("lockOwner"));
    }

    /**
     * Includes both changed lock fields and the conservative lock alias.
     * @return stable over-approximated writes footprint
     */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(MemoryLocation.of("lock"), MemoryLocation.of("locked"), MemoryLocation.of("lockOwner"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DclState ds)) return false;
        return !ds.locked();
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        DclState ds = (DclState) state;
        ds.lock(threadId);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclLockStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
