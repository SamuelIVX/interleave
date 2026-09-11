package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.List;

public final class DeadlockProgram {
    public static BenchmarkProgram program() {
        DeadlockState initial = DeadlockState.of(false, false);

        List<Step> thread0Steps = List.of(
            new DeadlockWriteFlagStep(0, true),
            new UnconditionalWaitStep(0, 1),
            new DeadlockWriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new DeadlockWriteFlagStep(1, true),
            new UnconditionalWaitStep(1, 0),
            new DeadlockWriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));
        return new BenchmarkProgram("deadlock", program, "DEADLOCK");
    }
}
