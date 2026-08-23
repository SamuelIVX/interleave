import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.*;
import java.util.*;

public class BasicUsage {
    public static void main(String[] args) {
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
        
        Program program = Interleave.program(initial, t0, t1);
        
        VerificationResult result = Interleave.verify(program, Strategy.DFS);
        
        System.out.println("Strategy: " + result.strategyUsed());
        System.out.println("States explored: " + result.statesExplored());
        System.out.println("Wall time: " + result.wallTimeMs() + " ms");
        System.out.println("Heap delta: " + result.heapDeltaBytes() + " bytes");
        System.out.println("Has violation: " + result.hasViolation());
        System.out.println("Completed traces: " + result.completedTraces().size());
        System.out.println("Deadlocked traces: " + result.deadlockedTraces().size());
        System.out.println("Failing traces: " + result.failingTraces().size());
        
        if (!result.completedTraces().isEmpty()) {
            Trace trace = result.completedTraces().get(0);
            System.out.println("First completed trace: " + trace);
            
            Configuration replayed = Interleave.replay(program, trace);
            System.out.println("Replayed config PCs: " + replayed.programCounters());
        }
        
        System.out.println("\nJSON output:");
        System.out.println(result.toJson());
    }
}
