package dev.samhb.interleave.minimize;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeltaDebuggerTest {

    @Test
    void minimize_reducesFailingTrace() {
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
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program);
        
        Trace failingTrace = result.traces().get(0);
        
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failingTrace, failingTrace.outcome());
        
        assertTrue(minimized.length() <= failingTrace.length(),
            "Minimized trace should be <= original length");
    }
    
    @Test
    void minimize_preservesViolation() {
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
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program);
        
        Trace failingTrace = result.traces().get(0);
        
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failingTrace, failingTrace.outcome());
        
        assertNotNull(minimized);
        assertTrue(minimized.length() > 0, "Minimized trace should not be empty");
    }
    
    @Test
    void minimize_returnsSubsequence() {
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
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program);
        
        Trace failingTrace = result.traces().get(0);
        
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failingTrace, failingTrace.outcome());
        
        List<Integer> originalThreadIds = failingTrace.threadIds();
        List<Integer> minimizedThreadIds = minimized.threadIds();
        
        int originalIndex = 0;
        int minimizedIndex = 0;
        
        while (originalIndex < originalThreadIds.size() && minimizedIndex < minimizedThreadIds.size()) {
            if (originalThreadIds.get(originalIndex).equals(minimizedThreadIds.get(minimizedIndex))) {
                minimizedIndex++;
            }
            originalIndex++;
        }
        
        assertTrue(minimizedIndex == minimizedThreadIds.size(),
            "Minimized trace should be a subsequence of original");
    }
}
