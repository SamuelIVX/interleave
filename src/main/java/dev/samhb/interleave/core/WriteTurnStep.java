/** Models publishing the Peterson turn variable. */
package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Models publishing the Peterson turn variable. */
public final class WriteTurnStep implements Step {
    /** Turn value. */
    private final int turnValue;

    /**
     * Creates write turn step from the supplied values.
     * @param turnValue turn value to publish
     */
    public WriteTurnStep(int turnValue) {
        this.turnValue = turnValue;
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> reads() {
        return Collections.emptySet();
    }

    /** {@inheritDoc} */
    @Override
    public Set<MemoryLocation> writes() {
        return Collections.singleton(MemoryLocation.of("turn"));
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
        ps.setTurn(turnValue);
        return StepOutcome.ADVANCED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WriteTurnStep that)) return false;
        return turnValue == that.turnValue;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(turnValue);
    }
}
