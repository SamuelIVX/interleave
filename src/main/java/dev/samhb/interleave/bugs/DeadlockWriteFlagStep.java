package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class DeadlockWriteFlagStep implements Step {
    private final int writerId;
    private final boolean value;

    public DeadlockWriteFlagStep(int writerId, boolean value) {
        this.writerId = writerId;
        this.value = value;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> writes() {
        // Write to both the specific flag and a shared "control" location
        // to make the two writes dependent in the IndependenceRelation,
        // forcing DPOR to explore both write orderings.
        return Set.of(
            MemoryLocation.of("flag[" + writerId + "]"),
            MemoryLocation.of("control")
        );
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof DeadlockState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        DeadlockState ds = (DeadlockState) state;
        ds.setFlag(writerId, value);
        // Also write to shared control location for DPOR dependency tracking
        ds.setControl(true);
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeadlockWriteFlagStep that)) return false;
        return writerId == that.writerId && value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(writerId, value);
    }
}
