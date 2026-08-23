package dev.samhb.modelcheck.core;

import java.util.Collections;
import java.util.Set;

public final class CSEnterStep implements Step {
    private final int threadId;

    public CSEnterStep(int threadId) {
        this.threadId = threadId;
    }

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
        ps.setInCriticalSection(threadId);
        return StepOutcome.ADVANCED;
    }
}
