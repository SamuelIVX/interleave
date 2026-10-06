/** Models reading a Peterson intent flag without changing shared state. */
package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models reading a Peterson intent flag without changing shared state. */
public final class ReadFlagStep implements Step {
    /** Reader id. */
    private final int readerId;
    /** Flag index. */
    private final int flagIndex;

    /**
     * Creates read flag step from the supplied values.
     * @param readerId thread whose read is modeled
     * @param flagIndex shared flag index to read
     */
    public ReadFlagStep(int readerId, int flagIndex) {
        this.readerId = readerId;
        this.flagIndex = flagIndex;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("flag[" + flagIndex + "]"));
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        return Collections.emptySet();
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        return state instanceof PetersonState;
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
        if (!(o instanceof ReadFlagStep that)) return false;
        return readerId == that.readerId && flagIndex == that.flagIndex;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(readerId, flagIndex);
    }
}
