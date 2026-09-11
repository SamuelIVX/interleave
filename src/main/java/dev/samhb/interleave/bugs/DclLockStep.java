package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class DclLockStep implements Step {
    private final int threadId;

    public DclLockStep(int threadId) {
        this.threadId = threadId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("lock"));
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Collections.singleton(MemoryLocation.of("lock"));
    }

    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DclState ds)) return false;
        return !ds.locked();
    }

    @Override
    public StepOutcome execute(SharedState state) {
        DclState ds = (DclState) state;
        ds.lock(threadId);
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclLockStep that)) return false;
        return threadId == that.threadId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}