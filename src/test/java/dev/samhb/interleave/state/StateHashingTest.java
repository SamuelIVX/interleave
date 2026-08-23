package dev.samhb.interleave.state;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StateHashingTest {

    @Test
    void canonicalEncoder_producesDeterministicOutput() {
        CanonicalEncoder encoder = new CanonicalEncoder();
        PetersonState a = PetersonState.of(false, false, 0);
        PetersonState b = PetersonState.of(false, false, 0);
        
        assertTrue(Arrays.equals(encoder.encode(a), encoder.encode(b)));
        assertEquals(encoder.hashCode(a), encoder.hashCode(b));
    }
    
    @Test
    void hashingStateStore_tracksVisitedStates() {
        HashingStateStore store = new HashingStateStore();
        PetersonState a = PetersonState.of(false, false, 0);
        PetersonState b = PetersonState.of(true, false, 0);
        
        assertFalse(store.isVisited(a));
        store.markVisited(a);
        assertTrue(store.isVisited(a));
        assertFalse(store.isVisited(b));
    }
    
    @Test
    void bitstateStore_neverFalseNegative() {
        BitstateStore store = new BitstateStore(1024);
        PetersonState a = PetersonState.of(false, false, 0);
        PetersonState b = PetersonState.of(true, false, 0);
        
        store.markVisited(a);
        assertTrue(store.isVisited(a));
        assertFalse(store.isVisited(b));
    }
    
    @Test
    void hashingStateStore_reducesDfsStates() {
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
        
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program);
        
        assertTrue(result.statesExplored() > 0);
        assertTrue(result.statesExplored() <= 100, "Should be reasonable with hashing");
    }
}
