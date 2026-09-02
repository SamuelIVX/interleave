package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class DclUseInstanceStep implements Step {
    private final int threadId;

    public DclUseInstanceStep(int threadId) {
        this.threadId = threadId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(
            MemoryLocation.of("instance"),
            MemoryLocation.of("control")
        );
    }

    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(MemoryLocation.of("observedInstance"));
    }

    @Override
    public boolean enabled(SharedState state) {
        if (!(state instanceof DclState ds)) return false;
        return ds.instance() != null;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        DclState ds = (DclState) state;
        ds.setObservedInstance(ds.instance());
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclUseInstanceStep that)) return false;
        return threadId == that.threadId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}