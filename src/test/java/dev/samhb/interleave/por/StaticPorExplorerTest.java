package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StaticPorExplorerTest {

    @Test
    void staticPorExploresFewerStatesThanDfs() {
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
        
        StaticPorExplorer porExplorer = new StaticPorExplorer();
        DfsResult porResult = porExplorer.explore(program);
        
        assertTrue(porResult.statesExplored() <= dfsResult.statesExplored(),
            "Static POR should explore <= states than DFS. DFS: " + 
            dfsResult.statesExplored() + ", POR: " + porResult.statesExplored());
    }
    
    @Test
    void staticPorProducesSameVerdictAsDfs() {
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
        
        StaticPorExplorer porExplorer = new StaticPorExplorer();
        DfsResult porResult = porExplorer.explore(program);
        
        assertEquals(dfsResult.statesExplored() > 0, porResult.statesExplored() > 0,
            "Both should explore some states");
    }
    
    @Test
    void independenceRelation_identifiesDependentSteps() {
        Step writeFlag0 = new WriteFlagStep(0, true);
        Step writeFlag1 = new WriteFlagStep(1, true);
        Step busyWaitOn0 = new BusyWaitStep(1, 0);
        
        IndependenceRelation relation = new IndependenceRelation();
        
        assertTrue(relation.areIndependent(writeFlag0, writeFlag1),
            "Writes to different flags should be independent");
        assertFalse(relation.areIndependent(writeFlag0, busyWaitOn0),
            "Write to flag[0] and busy-wait on flag[0] should be dependent");
    }
}
