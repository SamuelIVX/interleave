/** Public checked approximate membership, validation, statistics and copy contracts. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.format.SourceProgramLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitstateSymmetryStoreTest {
    /** Forced collisions stay approximate; clear/copy retain fresh independent membership. */
    @Test
    void saturationMetricsAndFreshCopiesAreHonest() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var config = model.program().initialConfiguration();
        var store = BitstateSymmetryStateStore.forRun(model.program(), null, Strategy.DFS, 1, 4, 0);
        assertInstanceOf(CheckedSymmetryStore.class, store);
        assertInstanceOf(ApproximateStateStore.class, store);
        assertEquals(0, store.bitCount()); assertEquals(0.0, store.bitDensity());
        assertEquals(0.0, store.estimatedFalsePositiveRate()); assertEquals(0, store.maxPreemptions());
        assertFalse(store.isVisited(config));
        assertDoesNotThrow(() -> store.markVisited(config));
        assertTrue(store.isVisited(config)); assertEquals(1, store.bitCount()); assertEquals(1.0, store.bitDensity());
        var copy = (BitstateSymmetryStateStore)store.freshCopy();
        assertNotSame(copy, store); assertFalse(copy.isVisited(config)); assertEquals(0, copy.bitCount());
        assertDoesNotThrow(() -> copy.requireRun(model.program(), null, Strategy.DFS));
        copy.markVisited(config); store.clear();
        assertFalse(store.isVisited(config)); assertTrue(copy.isVisited(config));
        assertEquals(0.0, store.estimatedFalsePositiveRate()); assertEquals(0.0, store.bitDensity());
    }

    /** The declared n=2,m=8,k=2 heuristic has a literal independently checked in Python/Decimal. */
    @Test
    void diagnosticsCountInsertionsRatherThanDistinctStates() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var store = BitstateSymmetryStateStore.forRun(model.program(), null, Strategy.DFS, 8, 2, 0);
        store.markVisited(model.program().initialConfiguration());
        store.markVisited(model.program().initialConfiguration());
        assertEquals(0.15481812174617549, store.estimatedFalsePositiveRate(), 1e-14);
        // Independent Python signed-byte hashing/rotation puts this VALUE key at positions 3 and 6.
        assertEquals(2,store.bitCount()); assertEquals(0.25,store.bitDensity());
        var copy = (BitstateSymmetryStateStore)store.freshCopy();
        copy.markVisited(model.program().initialConfiguration()); copy.markVisited(model.program().initialConfiguration());
        assertEquals(0.15481812174617549, copy.estimatedFalsePositiveRate(), 1e-14);
        assertEquals(store.bitCount(), copy.bitCount()); assertEquals(store.bitDensity(), copy.bitDensity());
    }

    /** Invalid settings and foreign bindings reject rather than silently selecting another mode. */
    @Test
    void rejectsInvalidSettingsAndForeignBindings() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        for (int[] settings : new int[][]{{0,4,0},{1,0,0},{-1,4,0},{1,-1,0},{1,4,-1},{1,4,1}}) {
            assertThrows(IllegalArgumentException.class, () -> BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DFS,settings[0],settings[1],settings[2]));
        }
        // Owned settings reject before source certification, rather than relying on filter allocation.
        assertThrows(IllegalArgumentException.class, () -> BitstateSymmetryStateStore.forRun(null,null,Strategy.DFS,0,4,0));
        assertThrows(IllegalArgumentException.class, () -> BitstateSymmetryStateStore.forRun(null,null,Strategy.DFS,1,0,0));
        assertDoesNotThrow(() -> BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DPOR,17,4,0));
        assertThrows(NullPointerException.class, () -> BitstateSymmetryStateStore.forRun(model.program(),null,null,17,4,0));
        assertThrows(NullPointerException.class, () -> BitstateSymmetryStateStore.forRun(null,null,Strategy.DFS,17,4,0));
        assertThrows(IllegalArgumentException.class, () -> BitstateSymmetryStateStore.forRun(model.program(),(s,c)->true,Strategy.DFS,17,4,0));
        var store = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DFS,17,4,0);
        assertThrows(IllegalArgumentException.class, () -> store.requireRun(new SourceProgramLoader().load("program other { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }").program(),null,Strategy.DFS));
        assertThrows(IllegalArgumentException.class, () -> store.requireRun(model.program(),(s,c)->true,Strategy.DFS));
        assertThrows(IllegalArgumentException.class, () -> store.requireRun(model.program(),null,Strategy.STATIC_POR));
        assertThrows(UnsupportedOperationException.class, () -> store.sleepCovered(model.program().initialConfiguration(),Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> store.isVisited(model.program().initialConfiguration(),-1,0));
        assertThrows(UnsupportedOperationException.class, () -> store.markVisited(model.program().initialConfiguration(),-1,0));
        assertEquals(0,store.bitCount());
    }
}
