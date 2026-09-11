package dev.samhb.interleave.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class UnconditionalWaitStep implements Step {
    private final int threadId;
    private final int otherId;

    public UnconditionalWaitStep(int threadId, int otherId) {
        this.threadId = threadId;
        this.otherId = otherId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("flag[" + otherId + "]"));
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Collections.emptySet();
    }

    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DeadlockState ds)) return false;
        return !ds.flag(otherId);
    }

    @Override
    public StepOutcome execute(SharedState state) {
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnconditionalWaitStep that)) return false;
        return threadId == that.threadId && otherId == that.otherId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId, otherId);
    }
}
