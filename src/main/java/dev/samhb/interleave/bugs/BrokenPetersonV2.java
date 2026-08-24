package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.Invariant;
import java.util.List;

public final class BrokenPetersonV2 {
    public static BenchmarkProgram program() {
        PetersonState initial = PetersonState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));
        Invariant invariant = mutualExclusion();
        return new BenchmarkProgram("broken-peterson-v2", program, "VIOLATION", invariant);
    }

    private static Invariant mutualExclusion() {
        return (state, config) -> {
            PetersonState ps = (PetersonState) state;
            List<Integer> pcs = config.programCounters();
            boolean bothFlagsTrue = ps.flag(0) && ps.flag(1);
            boolean t0InCsZone = pcs.get(0) >= 2;
            boolean t1InCsZone = pcs.get(1) >= 3;
            return !(bothFlagsTrue && t0InCsZone && t1InCsZone);
        };
    }
}
