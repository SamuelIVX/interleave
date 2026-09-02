package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class DclUnlockStep implements Step {
    private final int threadId;

    public DclUnlockStep(int threadId) {
        this.threadId = threadId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Collections.singleton(MemoryLocation.of("lock"));
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(
            MemoryLocation.of("lock"),
            MemoryLocation.of("control")
        );
    }

    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DclState ds)) return false;
        return ds.locked() && ds.lockOwner() == threadId;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        DclState ds = (DclState) state;
        ds.unlock(threadId);
        ds.setControl(true);
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclUnlockStep that)) return false;
        return threadId == that.threadId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}