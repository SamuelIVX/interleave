package dev.samhb.modelcheck.core;

import java.util.Collections;
import java.util.Set;

public final class WriteTurnStep implements Step {
    private final int turnValue;

    public WriteTurnStep(int turnValue) {
        this.turnValue = turnValue;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Collections.singleton(MemoryLocation.of("turn"));
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof PetersonState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        PetersonState ps = (PetersonState) state;
        ps.setTurn(turnValue);
        return StepOutcome.ADVANCED;
    }
}
