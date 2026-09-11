package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class ReadCounterStep implements Step {
    private final int threadId;

    public ReadCounterStep(int threadId) {
        this.threadId = threadId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(MemoryLocation.of("counter"));
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Collections.emptySet();
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof CounterState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        CounterState cs = (CounterState) state;
        cs.setRegister(threadId, cs.counter());
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadCounterStep that)) return false;
        return threadId == that.threadId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}