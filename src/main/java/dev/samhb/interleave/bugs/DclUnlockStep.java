/** Models DCL unlock with complete ownership and control footprints. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models owner-checked lock release after initialization. */
public final class DclUnlockStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates dcl unlock step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public DclUnlockStep(int threadId) {
        this.threadId = threadId;
    }

    /**
     * Includes lockedness and ownership needed by the guard.
     * @return stable over-approximated reads footprint
     */
    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(MemoryLocation.of("lock"), MemoryLocation.of("locked"), MemoryLocation.of("lockOwner"));
    }

    /**
     * Includes changed lock fields, control, and the conservative lock alias.
     * @return stable over-approximated writes footprint
     */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(
            MemoryLocation.of("lock"), MemoryLocation.of("locked"), MemoryLocation.of("lockOwner"),
            MemoryLocation.of("control")
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DclState ds)) return false;
        return ds.locked() && ds.lockOwner() == threadId;
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        DclState ds = (DclState) state;
        ds.unlock(threadId);
        ds.setControl(true);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclUnlockStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
