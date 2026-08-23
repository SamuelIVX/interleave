package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class BusyWaitStep implements Step {
    private final int threadId;
    private final int otherId;

    public BusyWaitStep(int threadId, int otherId) {
        this.threadId = threadId;
        this.otherId = otherId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return java.util.Set.of(
            MemoryLocation.of("flag[" + otherId + "]"),
            MemoryLocation.of("turn")
        );
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Collections.emptySet();
    }

    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof PetersonState ps)) return false;
        return !(ps.flag(otherId) && ps.turn() == otherId);
    }

    @Override
    public StepOutcome execute(SharedState state) {
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BusyWaitStep that)) return false;
        return threadId == that.threadId && otherId == that.otherId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId, otherId);
    }
}
