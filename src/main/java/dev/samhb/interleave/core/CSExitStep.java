/** Models leaving a critical section and declares its shared-state write. */
package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models clearing critical-section ownership. */
public final class CSExitStep implements Step {
    /** Creates csexit step with its default configuration. */
    public CSExitStep() {}

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
        ps.setInCriticalSection(-1);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return 1;
    }
}
