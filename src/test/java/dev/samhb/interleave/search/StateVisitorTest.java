package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.state.HashingStateStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StateVisitorTest {

    private Program createPetersonProgram() {
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
        
        return Interleave.program(PetersonState.of(false, false, 0), t0, t1);
    }

    @Test
    void dfsExplorer_callsVisitorOnEachState() {
        Program program = createPetersonProgram();
        int[] callCount = {0};
        
        StateVisitor visitor = config -> callCount[0]++;
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program, null, null, visitor);
        
        assertTrue(callCount[0] > 0, "Visitor should be called at least once");
        assertEquals(result.statesExplored(), callCount[0], "Visitor should be called once per state");
    }

    @Test
    void staticPorExplorer_callsVisitorOnEachState() {
        Program program = createPetersonProgram();
        int[] callCount = {0};
        
        StateVisitor visitor = config -> callCount[0]++;
        
        StaticPorExplorer explorer = new StaticPorExplorer();
        DfsResult result = explorer.explore(program, null, null, visitor);
        
        assertTrue(callCount[0] > 0, "Visitor should be called at least once");
        assertEquals(result.statesExplored(), callCount[0], "Visitor should be called once per state");
    }

    @Test
    void dporExplorer_callsVisitorOnEachState() {
        Program program = createPetersonProgram();
        int[] callCount = {0};
        
        StateVisitor visitor = config -> callCount[0]++;
        
        DporExplorer explorer = new DporExplorer();
        DfsResult result = explorer.explore(program, null, null, visitor);
        
        assertTrue(callCount[0] > 0, "Visitor should be called at least once");
        assertEquals(result.statesExplored(), callCount[0], "Visitor should be called once per state");
    }

    @Test
    void nullVisitor_doesNotThrow() {
        Program program = createPetersonProgram();
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program, null, null, null);
        
        assertNotNull(result);
        assertTrue(result.statesExplored() > 0);
    }

    @Test
    void dfsExplorer_usesProvidedStateStore() {
        Program program = createPetersonProgram();
        HashingStateStore store = new HashingStateStore();
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program, null, store, null);
        
        assertEquals(store.size(), result.statesExplored());
    }

    @Test
    void staticPorExplorer_usesProvidedStateStore() {
        Program program = createPetersonProgram();
        HashingStateStore store = new HashingStateStore();
        
        StaticPorExplorer explorer = new StaticPorExplorer();
        DfsResult result = explorer.explore(program, null, store, null);
        
        assertEquals(store.size(), result.statesExplored());
    }

    @Test
    void dporExplorer_usesProvidedStateStore() {
        Program program = createPetersonProgram();
        HashingStateStore store = new HashingStateStore();
        
        DporExplorer explorer = new DporExplorer();
        DfsResult result = explorer.explore(program, null, store, null);
        
        assertEquals(store.size(), result.statesExplored());
    }
}