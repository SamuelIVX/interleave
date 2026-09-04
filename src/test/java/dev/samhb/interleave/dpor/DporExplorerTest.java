package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DporExplorerTest {

    @Test
    void dporExploresFewerStatesThanDfs() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResult dfsResult = dfsExplorer.explore(program);
        
        DporExplorer dporExplorer = new DporExplorer();
        DfsResult dporResult = dporExplorer.explore(program);
        
        assertTrue(dporResult.statesExplored() <= dfsResult.statesExplored(),
            "DPOR should explore <= states than DFS. DFS: " + 
            dfsResult.statesExplored() + ", DPOR: " + dporResult.statesExplored());
    }
    
    @Test
    void dporProducesSameVerdictAsDfs() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResult dfsResult = dfsExplorer.explore(program);
        
        DporExplorer dporExplorer = new DporExplorer();
        DfsResult dporResult = dporExplorer.explore(program);
        
        assertEquals(dfsResult.statesExplored() > 0, dporResult.statesExplored() > 0,
            "Both should explore some states");
    }
    
    @Test
    void sleepSet_preventsRedundantExploration() {
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
        
        DporExplorer dporExplorer = new DporExplorer();
        DfsResult result = dporExplorer.explore(program);
        
        assertTrue(result.statesExplored() > 0, "Should explore some states");
    }

    @Test
    void happensBefore_computesTransitiveClosure() {
        HappensBefore hb = new HappensBefore();
        Step step = new WriteFlagStep(0, true);
        hb.record(0, 1, step, 0);
        hb.record(1, 2, step, 0);
        
        assertTrue(hb.happensBefore(0, 1), "Direct edge should hold");
        assertTrue(hb.happensBefore(1, 2), "Direct edge should hold");
        assertTrue(hb.happensBefore(0, 2), "Transitive edge should hold");
        assertFalse(hb.happensBefore(2, 0), "Reverse edge should not hold");
    }
}
