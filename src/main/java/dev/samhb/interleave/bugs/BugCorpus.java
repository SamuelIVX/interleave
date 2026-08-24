package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import java.util.*;

public final class BugCorpus {
    public static List<BenchmarkProgram> all() {
        List<BenchmarkProgram> programs = new ArrayList<>();
        programs.add(peterson());
        programs.add(dekker());
        programs.add(brokenPeterson());
        programs.add(brokenDekker());
        return programs;
    }

    public static BenchmarkProgram peterson() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
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
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        return new BenchmarkProgram("peterson", program, "PASS");
    }

    public static BenchmarkProgram dekker() {
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
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        return new BenchmarkProgram("dekker", program, "PASS");
    }

    public static BenchmarkProgram brokenPeterson() {
        return BrokenPeterson.program();
    }

    public static BenchmarkProgram brokenDekker() {
        return BrokenDekker.program();
    }
}
