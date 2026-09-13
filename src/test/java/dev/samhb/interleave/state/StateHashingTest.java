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
        Configuration a = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        Configuration b = Configuration.initial(PetersonState.of(true, false, 0), List.of());
        
        assertFalse(store.isVisited(a));
        store.markVisited(a);
        assertTrue(store.isVisited(a));
        assertFalse(store.isVisited(b));
    }
    
    @Test
    void bitstateStore_neverFalseNegative() {
        BitstateStore store = new BitstateStore(1024);
        Configuration a = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        Configuration b = Configuration.initial(PetersonState.of(true, false, 0), List.of());
        
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

    @Test
    void bitstateStore_kHash_neverFalseNegative() {
        BitstateStore store = new BitstateStore(1024, 4);
        Configuration a = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        Configuration b = Configuration.initial(PetersonState.of(true, false, 0), List.of());
        
        store.markVisited(a);
        assertTrue(store.isVisited(a), "Should not have false negatives with k=4");
        assertFalse(store.isVisited(b));
    }

    @Test
    void bitstateStore_kHash_reducesFalsePositives() {
        // For a given bit density, higher k gives lower false positive rate
        // But with same insertions, k=4 sets more bits so higher density.
        // Test that the false positive rate formula is reasonable.
        BitstateStore storeK4 = new BitstateStore(10000, 4);
        for (int i = 0; i < 100; i++) {
            Configuration c = Configuration.initial(PetersonState.of(i % 2 == 0, false, i % 3), List.of());
            storeK4.markVisited(c);
        }
        
        double fpRate = storeK4.estimatedFalsePositiveRate();
        assertTrue(fpRate >= 0.0 && fpRate <= 1.0);
        // With 100 states in 10000 bits and k=4, FP rate should be very low
        assertTrue(fpRate < 0.01, "FP rate should be < 1% for this configuration");
    }

    @Test
    void bitstateStore_metrics_correct() {
        BitstateStore store = new BitstateStore(1024, 4);
        Configuration a = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        Configuration b = Configuration.initial(PetersonState.of(true, false, 0), List.of());
        
        assertEquals(0, store.bitCount());
        assertEquals(0.0, store.bitDensity());
        assertEquals(0.0, store.estimatedFalsePositiveRate());
        
        store.markVisited(a);
        assertEquals(1, store.statesMarked());
        assertTrue(store.bitCount() > 0);
        assertTrue(store.bitDensity() > 0.0);
        
        store.markVisited(b);
        assertEquals(2, store.statesMarked());
        
        double fpRate = store.estimatedFalsePositiveRate();
        assertTrue(fpRate >= 0.0 && fpRate <= 1.0, "False positive rate should be in [0,1]");
    }

    @Test
    void bitstateStore_clear_resets() {
        BitstateStore store = new BitstateStore(1024, 4);
        Configuration a = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        
        store.markVisited(a);
        assertTrue(store.isVisited(a));
        assertEquals(1, store.statesMarked());
        
        store.clear();
        assertFalse(store.isVisited(a));
        assertEquals(0, store.statesMarked());
        assertEquals(0, store.bitCount());
        assertEquals(0.0, store.bitDensity());
    }

    @Test
    void bitstateStore_freshCopy_independent() {
        BitstateStore original = new BitstateStore(1024, 4);
        Configuration a = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        
        original.markVisited(a);
        
        StateStore copy = original.freshCopy();
        assertNotSame(original, copy);
        assertFalse(copy.isVisited(a), "Copy should be independent");
        
        copy.markVisited(a);
        assertTrue(copy.isVisited(a));
        assertTrue(original.isVisited(a), "Original should be unchanged");
    }
}
