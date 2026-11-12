/** Public checked CBS capacity, complete cost keys, shared membership and diagnostics. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.format.SourceProgramLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitstateSymmetryCbsTest {
    /** Literal probe unions independently derived in Python must not combine across candidate costs. */
    @Test void partialProbesFromDifferentCostsNeverFormCoverage() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var c = model.program().initialConfiguration();
        var store = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.CONTEXT_BOUNDED,17,4,8);
        // Costs 4/8 set {0,2,4,6,7,14}; queries 0/1 need {13,7,1,12}/{14,10,6,2}.
        // Every probe ordinal has some candidate's bit set, but neither full candidate matches.
        store.markVisited(c,-1,4);store.markVisited(c,-1,8);
        assertEquals(6,store.bitCount());
        assertFalse(store.isVisited(c,-1,0));assertFalse(store.isVisited(c,-1,1));
        store.markVisited(c,-1,0);assertTrue(store.isVisited(c,-1,1));
    }

    /** The mapped owner is part of the complete typed product before Bloom hashing.
     * @throws java.io.IOException if the in-memory key is malformed
     */
    @Test void cbsBytesEncodeMappedOwnerDomainAndCost() throws java.io.IOException {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var c = model.program().initialConfiguration();
        var context = new SymmetryRunContext(model.program(),null,Strategy.CONTEXT_BOUNDED);
        try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(context.costBytes(c,-1,2)))) {
            assertEquals(1,in.readUnsignedByte());assertEquals(2,in.readUnsignedByte());
            int length = in.readInt();
            assertEquals("thread-orbit-v1|[[0, 1]]|AAAAAgAAAAEAAXgAAAAAAAAAAAD/////AAAAAAAAAAAAAAAA|[0, 0]|-1",
                new String(in.readNBytes(length),java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(2,in.readInt());assertEquals(-1,in.read());
        }
        assertArrayEquals(context.costBytes(c,0,2),context.costBytes(c,1,2));
        assertFalse(java.util.Arrays.equals(context.costBytes(c,-1,2),context.costBytes(c,0,2)));
    }
    /** Independently derived n=2,m=8,k=2 F and pMax=2 capped union-model values. */
    @Test void configuredCapacityAndQueryHistorySurviveCopiesButNotClear() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var config = model.program().initialConfiguration();
        var store = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.CONTEXT_BOUNDED,8,2,3);
        assertEquals(3,store.maxPreemptions());
        assertEquals(0,store.bitCount()); assertEquals(0.0,store.estimatedFalsePositiveRate());
        store.markVisited(config,-1,1); store.markVisited(config,-1,1);
        assertEquals(0.15481812174617549,store.estimatedFalsePositiveRate(),1e-14);
        assertTrue(store.isVisited(config,-1,2));
        assertEquals(0.46445436523852646,store.estimatedFalsePositiveRate(),1e-14);
        var copy = (BitstateSymmetryStateStore)store.freshCopy();
        assertEquals(3,copy.maxPreemptions()); assertEquals(0,copy.bitCount());
        copy.markVisited(config,-1,1);copy.markVisited(config,-1,1);
        assertEquals(0.15481812174617549,copy.estimatedFalsePositiveRate(),1e-14);
        copy.clear();
        assertFalse(copy.isVisited(config,-1,2)); assertEquals(0.0,copy.estimatedFalsePositiveRate());
        store.clear(); assertEquals(0,store.bitCount()); assertEquals(0.0,store.bitDensity());
        store.markVisited(config,-1,1);store.markVisited(config,-1,1);
        assertEquals(0.15481812174617549,store.estimatedFalsePositiveRate(),1e-14);
    }

    /** Invalid costs/owners reject before changing either bits or the query diagnostic history. */
    @Test void rejectsInvalidCostsAndOwnersWithoutPartialWrites() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var c = model.program().initialConfiguration();
        assertThrows(IllegalArgumentException.class, () -> BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.CONTEXT_BOUNDED,8,2,-1));
        var store = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.CONTEXT_BOUNDED,8,2,2);
        store.markVisited(c,-1,0);
        int bits = store.bitCount(); double rate = store.estimatedFalsePositiveRate();
        for (int cost : List.of(-1,3)) {
            assertThrows(IllegalArgumentException.class, () -> store.isVisited(c,-1,cost));
            assertThrows(IllegalArgumentException.class, () -> store.markVisited(c,-1,cost));
        }
        for (int owner : List.of(-2,2)) {
            assertThrows(IllegalArgumentException.class, () -> store.isVisited(c,owner,2));
            assertThrows(IllegalArgumentException.class, () -> store.markVisited(c,owner,2));
        }
        assertEquals(bits,store.bitCount()); assertEquals(rate,store.estimatedFalsePositiveRate());
    }

    /** A saturated single budget remains bounded, including Integer.MAX_VALUE capacity arithmetic. */
    @Test void costsShareOneBudgetAndMaximumCapacityDoesNotOverflow() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var c = model.program().initialConfiguration();
        var store = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.CONTEXT_BOUNDED,1,4,Integer.MAX_VALUE);
        store.markVisited(c,-1,Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE,store.maxPreemptions());
        assertTrue(store.isVisited(c,-1,Integer.MAX_VALUE)); assertEquals(1,store.bitCount());
        assertEquals(1.0,store.bitDensity()); assertEquals(1.0,store.estimatedFalsePositiveRate());
        store.markVisited(c,-1,0);store.markVisited(c);
        assertEquals(1,store.bitCount());
    }
}
