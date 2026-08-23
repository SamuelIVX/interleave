package dev.samhb.modelcheck.core;

import java.util.Collections;
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
        PetersonState ps = (PetersonState) state;
        boolean value = ps.flag(flagIndex);
        return StepOutcome.ADVANCED;
    }
}
