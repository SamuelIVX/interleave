package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class WriteCounterStep implements Step {
    private final int threadId;

    public WriteCounterStep(int threadId) {
        this.threadId = threadId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(MemoryLocation.of("control"));
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(
            MemoryLocation.of("counter"),
            MemoryLocation.of("control")
        );
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof CounterState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        CounterState cs = (CounterState) state;
        int localValue = cs.getRegister(threadId);
        cs.setCounter(localValue + 1);
        cs.setControl(true);
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WriteCounterStep that)) return false;
        return threadId == that.threadId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}