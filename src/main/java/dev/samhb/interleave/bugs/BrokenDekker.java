package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.Invariant;
import java.util.List;

public final class BrokenDekker {
    public static BenchmarkProgram program() {
        DekkerState initial = DekkerState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));
        Invariant invariant = mutualExclusion();
        return new BenchmarkProgram("broken-dekker", program, "VIOLATION", invariant);
    }

    private static Invariant mutualExclusion() {
        return (state, config) -> {
            DekkerState ds = (DekkerState) state;
            List<Integer> pcs = config.programCounters();
            boolean bothFlagsTrue = ds.flag(0) && ds.flag(1);
            boolean bothPastBusyWait = pcs.get(0) > 2 && pcs.get(1) > 2;
            return !(bothFlagsTrue && bothPastBusyWait);
        };
    }
}
