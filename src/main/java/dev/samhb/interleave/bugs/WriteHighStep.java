package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class WriteHighStep implements Step {
    private final int threadId;
    private final int value;

    public WriteHighStep(int threadId, int value) {
        this.threadId = threadId;
        this.value = value;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(MemoryLocation.of("control"));
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(
            MemoryLocation.of("high"),
            MemoryLocation.of("control")
        );
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof PairState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        PairState ps = (PairState) state;
        ps.setHigh(value);
        ps.setControl(true);
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WriteHighStep that)) return false;
        return threadId == that.threadId && value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId, value);
    }
}