package dev.samhb.modelcheck.core;

import java.util.Collections;
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
}
