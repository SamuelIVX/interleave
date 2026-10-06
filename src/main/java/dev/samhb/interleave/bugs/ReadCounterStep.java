/** Copies the shared counter into a modeled per-thread register. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Copies the shared counter into one thread’s local register. */
public final class ReadCounterStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates read counter step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public ReadCounterStep(int threadId) {
        this.threadId = threadId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(MemoryLocation.of("counter"));
    }

    /**
     * Reports the destination register as a modeled write.
     * @return stable over-approximated writes footprint
     */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(MemoryLocation.of("registers[" + threadId + "]"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean enabled(SharedState state) {
        return state instanceof CounterState;
    }

    /** {@inheritDoc} */
    @Override
    public StepOutcome execute(SharedState state) {
        CounterState cs = (CounterState) state;
        cs.setRegister(threadId, cs.counter());
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadCounterStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
