/** Records a pair observation with explicit property-visible writes. */
package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class ReadSnapshotStep implements Step {
    private final int threadId;

    public ReadSnapshotStep(int threadId) {
        this.threadId = threadId;
    }

    @Override
    public Set<MemoryLocation> reads() {
        return Set.of(
            MemoryLocation.of("high"),
            MemoryLocation.of("low"),
            MemoryLocation.of("control")
        );
    }

    /**
     * Includes all observation fields and the existing conservative control dependency.
     * @return stable over-approximated writes footprint
     */
    @Override
    public Set<MemoryLocation> writes() {
        return Set.of(MemoryLocation.of("control"), MemoryLocation.of("observedHigh"),
            MemoryLocation.of("observedLow"), MemoryLocation.of("hasObservation"));
    }

    @Override
    public boolean enabled(SharedState state) {
        return state instanceof PairState;
    }

    @Override
    public StepOutcome execute(SharedState state) {
        PairState ps = (PairState) state;
        ps.recordObservation(ps.high(), ps.low());
        return StepOutcome.ADVANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadSnapshotStep that)) return false;
        return threadId == that.threadId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }
}