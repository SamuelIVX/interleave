/** Exact and approximate store contracts with independent numeric expectations. */
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

    /** Checks merging against four hand-enumerated positions, rather than a loose upper bound. */
    @Test
    void hashingStateStoreMergesConvergingSchedules() {
        HashingStateStore store = new HashingStateStore();
        DfsResult result = new DfsExplorer().explore(
            dev.samhb.interleave.testsupport.TestPrograms.independentFlags(), null, store, null);
        assertEquals(4, result.statesExplored());
        assertEquals(4, store.size());
        assertEquals(1, result.traces().size());
        assertEquals(TraceOutcome.COMPLETED, result.traces().get(0).outcome());
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

    /** Checks the estimate for a fixed insertion workload with independently derived literals. */
    @Test
    void bitstateStore_kHash_reducesFalsePositives() {
        BitstateStore oneHash = new BitstateStore(10000, 1);
        BitstateStore fourHashes = new BitstateStore(10000, 4);
        for (int i = 0; i < 100; i++) {
            Configuration config = Configuration.initial(new CounterState(i), List.of());
            oneHash.markVisited(config);
            fourHashes.markVisited(config);
        }
        // Python Decimal.exp (50 digits) and a separately summed exponential series agree.
        assertEquals(0.009950166250831946, oneHash.estimatedFalsePositiveRate(), 1e-15);
        assertEquals(0.000002363808103136054, fourHashes.estimatedFalsePositiveRate(), 1e-18);
        assertTrue(fourHashes.estimatedFalsePositiveRate() < oneHash.estimatedFalsePositiveRate());
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
