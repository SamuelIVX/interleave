/** Models entering a critical section and declares its shared-state write. */
package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models assigning critical-section ownership to the executing thread. */
public final class CSEnterStep implements Step {
    /** Thread id. */
    private final int threadId;

    /**
     * Creates csenter step from the supplied values.
     * @param threadId zero-based modeled thread ID
     */
    public CSEnterStep(int threadId) {
        this.threadId = threadId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Collections.emptySet();
    }

    /**
     * Reports the critical-section owner as a modeled write.
     * @return stable over-approximated writes footprint
     */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(MemoryLocation.of("inCriticalSection"));
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
        ps.setInCriticalSection(threadId);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CSEnterStep that)) return false;
        return threadId == that.threadId;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}
