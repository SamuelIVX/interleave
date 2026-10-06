/** Publishes an intent flag in a deadlock benchmark. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Publishes an intent flag in a deadlock benchmark. */
public final class DeadlockWriteFlagStep implements Step {
    /** Writer id. */
    private final int writerId;
    /** Value. */
    private final boolean value;

    /**
     * Creates deadlock write flag step from the supplied values.
     * @param writerId thread whose write is modeled
     * @param value value to assign
     */
    public DeadlockWriteFlagStep(int writerId, boolean value) {
        this.writerId = writerId;
        this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Collections.emptySet();
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        // Write to both the specific flag and a shared "control" location
        // to make the two writes dependent in the IndependenceRelation,
        // forcing DPOR to explore both write orderings.
        return Set.of(
            MemoryLocation.of("flag[" + writerId + "]"),
            MemoryLocation.of("control")
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        return state instanceof DeadlockState;
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        DeadlockState ds = (DeadlockState) state;
        ds.setFlag(writerId, value);
        // Also write to shared control location for DPOR dependency tracking
        ds.setControl(true);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeadlockWriteFlagStep that)) return false;
        return writerId == that.writerId && value == that.value;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(writerId, value);
    }
}
