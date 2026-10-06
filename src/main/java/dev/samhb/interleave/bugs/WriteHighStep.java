/** Writes the high half of a modeled pair. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Writes the high half of a modeled pair. */
public final class WriteHighStep implements Step {
    /** Thread id. */
    private final int threadId;
    /** Value. */
    private final int value;

    /**
     * Creates write high step from the supplied values.
     * @param threadId zero-based modeled thread ID
     * @param value value to assign
     */
    public WriteHighStep(int threadId, int value) {
        this.threadId = threadId;
        this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(MemoryLocation.of("control"));
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(
            MemoryLocation.of("high"),
            MemoryLocation.of("control")
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        return state instanceof PairState;
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        PairState ps = (PairState) state;
        ps.setHigh(value);
        ps.setControl(true);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WriteHighStep that)) return false;
        return threadId == that.threadId && value == that.value;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId, value);
    }
}
