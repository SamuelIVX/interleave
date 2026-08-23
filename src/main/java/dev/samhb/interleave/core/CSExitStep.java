package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class CSExitStep implements Step {
    @Override
    public Set<MemoryLocation> reads() {
        return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Collections.emptySet();
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof PetersonState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        PetersonState ps = (PetersonState) state;
        ps.setInCriticalSection(-1);
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return 1;
    }
}
