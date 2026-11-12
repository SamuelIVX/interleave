/** Checked full-product DPOR admission, insertion accounting and invalid-context atomicity. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitstateSymmetryDporTest {
    /** Fixed owners stay fixed while a noncontiguous original owner maps into its certified group.
     * @throws java.io.IOException if reading the complete product fails
     */
    @Test void fixedOwnersAndMappedGroupTokensStayDistinct() throws java.io.IOException {
        var model = new SourceProgramLoader().load("program fixed { shared int x = 0; thread 0 { atomic {} atomic {} } thread 1 { atomic {} } thread 2 { atomic {} } }");
        var p = model.program();var initial = p.initialConfiguration();
        var left = TransitionExecutor.execute(p,initial,1).successor();
        var right = TransitionExecutor.execute(p,initial,2).successor();
        var context = new SymmetryRunContext(p,null,Strategy.DPOR);
        var fixed = Map.entry(0,p.threads().get(0).steps().getFirst());
        var movingLeft = Map.entry(1,p.threads().get(1).steps().getFirst());
        var movingRight = Map.entry(2,p.threads().get(2).steps().getFirst());
        byte[] bytes = context.sleepBytes(left,Set.of(fixed,movingLeft));
        assertArrayEquals(bytes,context.sleepBytes(right,Set.of(movingRight,fixed)));
        try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            in.skipNBytes(2);in.skipNBytes(in.readInt());assertEquals(2,in.readInt());
            assertEquals(0,in.readInt());assertEquals(0,in.readInt());
            assertEquals(2,in.readInt());assertEquals(0,in.readInt());assertEquals(-1,in.read());
        }
    }

    /** Sealed explorer dispatch rejects foreign bindings before invoking any visitor. */
    @Test void dporBindingRejectsForeignProgramAndPropertyBeforeCallbacks() {
        var loader = new SourceProgramLoader();
        String source = "program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }";
        var model = loader.load(source);var other = loader.load(source);
        var store = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DPOR,17,4,0);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        StateVisitor visitor = c -> calls.incrementAndGet();
        assertThrows(IllegalArgumentException.class, () -> new dev.samhb.interleave.dpor.DporExplorer().explore(other.program(),null,store,visitor));
        assertThrows(IllegalArgumentException.class, () -> new dev.samhb.interleave.dpor.DporExplorer().explore(model.program(),(s,c)->true,store,visitor));
        assertEquals(0,calls.get());assertEquals(0,store.bitCount());
    }
    /** Full tokens are sorted independently of caller iteration order and retain stale PCs.
     * @throws java.io.IOException if the in-memory wire layout is malformed
     */
    @Test void encodedProductsRetainVersionDomainCountAndSortedPairs() throws java.io.IOException {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} atomic {} } thread 1 { atomic {} atomic {} } }");
        var p = model.program();var c = p.initialConfiguration();
        var context = new SymmetryRunContext(p,null,Strategy.DPOR);
        var incoming = new LinkedHashSet<Map.Entry<Integer,Step>>();
        for (int owner : List.of(1,0)) for (int pc : List.of(1,0)) incoming.add(Map.entry(owner,p.threads().get(owner).steps().get(pc)));
        byte[] bytes = context.sleepBytes(c,incoming);
        try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            assertEquals(1,in.readUnsignedByte());assertEquals(3,in.readUnsignedByte());
            int length = in.readInt();
            assertEquals("thread-orbit-v1|[[0, 1]]|AAAAAgAAAAEAAXgAAAAAAAAAAAD/////AAAAAAAAAAAAAAAA|[0, 0]|unbounded",
                new String(in.readNBytes(length),java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(4,in.readInt());
            for (var expected : List.of(List.of(0,0),List.of(0,1),List.of(1,0),List.of(1,1))) {
                assertEquals(expected.get(0).intValue(),in.readInt());assertEquals(expected.get(1).intValue(),in.readInt());
            }
            assertEquals(-1,in.read());
        }
        var forward = new LinkedHashSet<Map.Entry<Integer,Step>>();
        for (int owner : List.of(0,1)) for (int pc : List.of(0,1)) forward.add(Map.entry(owner,p.threads().get(owner).steps().get(pc)));
        assertArrayEquals(bytes,context.sleepBytes(c,forward));
        assertFalse(Arrays.equals(context.sleepBytes(c,Set.of()),context.valueBytes(c)));
    }

    /** Matching aliases use first lexical occurrence; mismatched partitions still fail admission.
     * @throws java.io.IOException if reading a complete product fails
     */
    @Test void aliasedActionsKeepFirstOccurrenceAndCertification() throws java.io.IOException {
        String body = "if (local.r == 0) { atomic {} } else { atomic {} } atomic {}";
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; local int r = 0; thread 0 { "+body+" } thread 1 { "+body+" } }");
        var p = aliasBranches(model,Set.of(0,1));
        var store = BitstateSymmetryStateStore.forRun(p,null,Strategy.DPOR,1_000_003,4,0);
        var c = p.initialConfiguration();
        assertFalse(store.sleepCovered(c,Set.of(Map.entry(0,p.threads().get(0).steps().get(1)))));
        assertTrue(store.sleepCovered(c,Set.of(Map.entry(0,p.threads().get(0).steps().get(2)))));
        var context = new SymmetryRunContext(p,null,Strategy.DPOR);
        try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(context.sleepBytes(c,Set.of(Map.entry(0,p.threads().get(0).steps().get(2))))))) {
            in.skipNBytes(2);in.skipNBytes(in.readInt());
            assertEquals(1,in.readInt());assertEquals(0,in.readInt());assertEquals(1,in.readInt());assertEquals(-1,in.read());
        }
        var mismatch = aliasBranches(model,Set.of(0));
        assertThrows(IllegalArgumentException.class, () -> BitstateSymmetryStateStore.forRun(mismatch,null,Strategy.DPOR,17,4,0));
        assertDoesNotThrow(() -> BitstateSymmetryStateStore.forRun(mismatch,null,Strategy.DFS,17,4,0));
    }

    /** Copies trusted source instructions while aliasing the two identical branch arms.
     * @param model compiled source fixture
     * @param owners owners whose matching branch arms share one object
     * @return recomposed program with original source metadata
     */
    private static Program aliasBranches(dev.samhb.interleave.bugs.BenchmarkProgram model, Set<Integer> owners) {
        var threads = new ArrayList<ModelThread>();
        for (var thread : model.program().threads()) {
            var steps = new ArrayList<>(thread.steps());
            if (owners.contains(thread.id())) steps.set(1,steps.get(2));
            threads.add(new ModelThread(thread.id(),steps));
        }
        return new Program(model.program().initialConfiguration().state(),threads,model.program().sourceDigest());
    }
    /** Positive product lookups do not insert; accepted VALUE marks remain separate insertions. */
    @Test void fullProductLookupCountsOnlyMissingProductsAndCopiesIndependently() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} atomic {} } thread 1 { atomic {} atomic {} } }");
        var c = model.program().initialConfiguration();
        var store = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DPOR,8,2,0);
        assertFalse(store.sleepCovered(c,Set.of()));
        assertTrue(store.sleepCovered(c,Set.of()));
        store.markVisited(c);
        assertEquals(0.15481812174617549,store.estimatedFalsePositiveRate(),1e-14);
        var copy = (BitstateSymmetryStateStore)store.freshCopy();
        assertEquals(0,copy.bitCount());assertEquals(0.0,copy.estimatedFalsePositiveRate());
        assertFalse(copy.sleepCovered(c,Set.of()));
        store.clear();assertEquals(0,store.bitCount());assertEquals(0.0,store.estimatedFalsePositiveRate());
        assertTrue(copy.sleepCovered(c,Set.of()));
        assertFalse(store.sleepCovered(c,Set.of()));
    }

    /** Even saturated membership must validate every incoming token before reporting coverage. */
    @Test void invalidContextsDoNotPartiallyMarkOrChangeStatistics() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} atomic {} } thread 1 { atomic {} atomic {} } }");
        var other = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} atomic {} } thread 1 { atomic {} atomic {} } }");
        var c = model.program().initialConfiguration();
        var action = model.program().threads().getFirst().steps().getFirst();
        var store = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DPOR,17,4,0);
        for (var invalid : List.of(Map.entry(-1,action),Map.entry(2,action),Map.entry(0,other.program().threads().getFirst().steps().getFirst()))) {
            var incoming = new LinkedHashSet<Map.Entry<Integer,Step>>();
            incoming.add(Map.entry(0,action));incoming.add(invalid);
            assertThrows(IllegalArgumentException.class, () -> store.sleepCovered(c,incoming));
            assertEquals(0,store.bitCount());assertEquals(0.0,store.estimatedFalsePositiveRate());
        }
        var tiny = BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DPOR,1,4,0);
        tiny.markVisited(c);
        double before = tiny.estimatedFalsePositiveRate();
        assertTrue(tiny.sleepCovered(c,Set.of())); // Complete-product Bloom collision, not exact coverage.
        assertEquals(before,tiny.estimatedFalsePositiveRate());assertEquals(1,tiny.bitCount());
        assertThrows(IllegalArgumentException.class, () -> tiny.sleepCovered(c,Set.of(Map.entry(2,action))));
        assertEquals(before,tiny.estimatedFalsePositiveRate());
        assertThrows(IllegalArgumentException.class, () -> BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DPOR,17,4,1));
        // Retain the public factory's eager null-strategy rejection before budget validation.
        assertThrows(NullPointerException.class, () -> BitstateSymmetryStateStore.forRun(model.program(),null,null,0,4,0));
    }
}
