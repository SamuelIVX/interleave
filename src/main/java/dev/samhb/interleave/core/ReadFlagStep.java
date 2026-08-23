package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class ReadFlagStep implements Step {
    private final int readerId;
    private final int flagIndex;

    public ReadFlagStep(int readerId, int flagIndex) {
        this.readerId = readerId;
        this.flagIndex = flagIndex;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("flag[" + flagIndex + "]"));
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
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadFlagStep that)) return false;
        return readerId == that.readerId && flagIndex == that.flagIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(readerId, flagIndex);
    }
}
