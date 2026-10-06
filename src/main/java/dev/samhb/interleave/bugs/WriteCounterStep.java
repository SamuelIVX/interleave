/** Writes the counter from a modeled register with complete dependencies. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Publishes a thread-local counter register plus one, modeling a lost update. */
public final class WriteCounterStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates write counter step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public WriteCounterStep(int threadId) {
        this.threadId = threadId;
    }

    /**
     * Includes the source register and the existing conservative control dependency.
     * @return stable over-approximated reads footprint
     */
    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(MemoryLocation.of("control"), MemoryLocation.of("registers[" + threadId + "]"));
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(
            MemoryLocation.of("counter"),
            MemoryLocation.of("control")
        );
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
        int localValue = cs.getRegister(threadId);
        cs.setCounter(localValue + 1);
        cs.setControl(true);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WriteCounterStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
