package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class DclReadInstanceStep implements Step {
    private final int threadId;

    public DclReadInstanceStep(int threadId) {
        this.threadId = threadId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("instance"));
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Collections.emptySet();
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof DclState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclReadInstanceStep that)) return false;
        return threadId == that.threadId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}