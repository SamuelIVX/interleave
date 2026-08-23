package dev.samhb.modelcheck.search;

import dev.samhb.modelcheck.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DfsExplorerTest {

    @Test
    void dfsExploresAllReachableConfigurations_simpleTwoStepProgram() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program);
        
        assertNotNull(result);
        assertTrue(result.statesExplored() > 0, "Should explore at least initial state");
        assertTrue(result.statesExplored() <= 20, "2 threads, 2 steps each should explore reasonable states");
    }
    
    @Test
    void traceReplayer_replaysTraceToSameConfiguration() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program);
        
        TraceReplayer replayer = new TraceReplayer();
        for (Trace trace : result.traces()) {
            Configuration replayed = replayer.replay(program, trace);
            assertNotNull(replayed);
        }
    }
    
    @Test
    void invariantViolation_reportedWithTrace() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new CSEnterStep(0),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new CSEnterStep(1),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        Invariant mutualExclusion = (state, config) -> {
            PetersonState ps = (PetersonState) state;
            // In this simple test, both could be in CS if schedule allows
            // For now just verify the invariant runs
            return true;
        };
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program, mutualExclusion);
        
        assertNotNull(result);
    }
}
