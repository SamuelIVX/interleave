package dev.samhb.modelcheck.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class WriteFlagStep implements Step {
    private final int writerId;
    private final boolean value;

    public WriteFlagStep(int writerId, boolean value) {
        this.writerId = writerId;
        this.value = value;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Collections.singleton(MemoryLocation.of("flag[" + writerId + "]"));
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof PetersonState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        PetersonState ps = (PetersonState) state;
        ps.setFlag(writerId, value);
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WriteFlagStep that)) return false;
        return writerId == that.writerId && value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(writerId, value);
    }
}
