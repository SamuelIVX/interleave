package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.Invariant;
import java.util.List;

public final class TornCounter {
    public static BenchmarkProgram program() {
        PairState initial = PairState.of(0, 0);

        // T0 writes high then low
        List<Step> thread0Steps = List.of(
            new WriteHighStep(0, 2),
            new WriteLowStep(0, 2)
        );

        // T1 reads both fields (torn read if interleaved between T0's writes)
        List<Step> thread1Steps = List.of(
            new ReadSnapshotStep(1)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        // Invariant: if high == 2 (T0 has written high), then low must also be 2
        // The torn write bug: T1 reads high=2, low=0 (observed high without low)
        // Gate on T1 actually performing the read and observing the torn state
        Invariant invariant = (state, config) -> {
            PairState ps = (PairState) state;
            // Only check if T1 has performed the read
            if (ps.hasObservation()) {
                if (ps.observedHigh() == 2 && ps.observedLow() != 2) {
                    return false; // VIOLATION: T1 observed torn write
                }
            }
            return true;
        };

        return new BenchmarkProgram("torn-counter", program, "VIOLATION", invariant);
    }
}