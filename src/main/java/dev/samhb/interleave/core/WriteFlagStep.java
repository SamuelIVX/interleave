/** Models publishing a thread’s Peterson intent flag. */
package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models publishing a thread’s Peterson intent flag. */
public final class WriteFlagStep implements Step {
    /** Writer id. */
    private final int writerId;
    /** Value. */
    private final boolean value;

    /**
     * Creates write flag step from the supplied values.
     * @param writerId thread whose write is modeled
     * @param value value to assign
     */
    public WriteFlagStep(int writerId, boolean value) {
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
        return Collections.singleton(MemoryLocation.of("flag[" + writerId + "]"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        return state instanceof PetersonState;
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        PetersonState ps = (PetersonState) state;
        ps.setFlag(writerId, value);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WriteFlagStep that)) return false;
        return writerId == that.writerId && value == that.value;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(writerId, value);
    }
}
