/** Public ordering-graph contracts, independent of the explorer's write-only bookkeeping. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.Step;
import dev.samhb.interleave.core.WriteFlagStep;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HappensBeforeContractTest {
    /** An empty graph is reflexive, disconnected and has explicit absent-edge sentinels. */
    @Test
    void emptyGraphHasReflexivityAndAbsentEdgeSentinels() {
        var graph = new HappensBefore();
        assertTrue(graph.happensBefore(4, 4));
        assertFalse(graph.happensBefore(0, 1));
        assertEquals(Set.of(), graph.getThreadsThatHappenBefore(1));
        assertNull(graph.getStep(0, 1));
        assertEquals(-1, graph.getPcAtRecord(0, 1));
        assertEquals(0, graph.getThreadLocalCounter(0));
    }

    /** Repeated events retain the first edge's exact step and PC while incrementing the source count. */
    @Test
    void repeatedEdgesKeepTheirFirstRecordAndCountEveryEvent() {
        var graph = new HappensBefore();
        Step first = new WriteFlagStep(0, true);
        Step later = new WriteFlagStep(0, false);
        graph.record(0, 1, first, 2);
        graph.record(0, 1, later, 7);
        graph.record(0, 2, later, 8);
        assertSame(first, graph.getStep(0, 1));
        assertEquals(2, graph.getPcAtRecord(0, 1));
        assertSame(later, graph.getStep(0, 2));
        assertEquals(8, graph.getPcAtRecord(0, 2));
        assertEquals(3, graph.getThreadLocalCounter(0));
        assertEquals(0, graph.getThreadLocalCounter(1));
        assertNull(graph.getStep(0, 9));
        assertEquals(-1, graph.getPcAtRecord(0, 9));
        assertEquals(Set.of(0), graph.getThreadsThatHappenBefore(1));
        assertEquals(Set.of(0), graph.getThreadsThatHappenBefore(2));
    }

    /** Reachability includes transitive paths, but predecessor queries return only direct sources. */
    @Test
    void transitivePathsDoNotBroadenDirectPredecessors() {
        var graph = new HappensBefore();
        Step step = new WriteFlagStep(0, true);
        graph.record(0, 1, step, 0);
        graph.record(1, 2, step, 0);
        graph.record(3, 2, step, 0);
        graph.record(3, 4, step, 1);
        assertEquals(Set.of(3), graph.getThreadsThatHappenBefore(4));
        assertTrue(graph.happensBefore(3, 4));
        assertFalse(graph.happensBefore(0, 4));
        assertTrue(graph.happensBefore(0, 2));
        assertTrue(graph.happensBefore(3, 2));
        assertFalse(graph.happensBefore(2, 0));
        assertEquals(Set.of(1, 3), graph.getThreadsThatHappenBefore(2));
        assertEquals(Set.of(0), graph.getThreadsThatHappenBefore(1));
        Set<Integer> returned = graph.getThreadsThatHappenBefore(2);
        graph.record(0, 2, step, 1);
        assertEquals(Set.of(1, 3), returned);
        assertEquals(Set.of(0, 1, 3), graph.getThreadsThatHappenBefore(2));
    }

    /** Cycles and converging paths terminate without inventing reachability to a disconnected node. */
    @Test
    void cyclesAndConvergingPathsRetainExactReachability() {
        var graph = new HappensBefore();
        Step step = new WriteFlagStep(0, true);
        graph.record(0, 1, step, 0);
        graph.record(1, 0, step, 0);
        graph.record(0, 2, step, 1);
        graph.record(2, 3, step, 0);
        graph.record(3, 1, step, 0);
        assertTrue(graph.happensBefore(1, 3));
        assertTrue(graph.happensBefore(3, 2));
        assertFalse(graph.happensBefore(0, 9));
        assertEquals(Set.of(0, 3), graph.getThreadsThatHappenBefore(1));
        assertEquals(Set.of(2), graph.getThreadsThatHappenBefore(3));
    }

    /** A copy retains every public value and isolates existing nested maps and counters in both directions. */
    @Test
    void copiesRetainRecordsAndIsolateParentAndSiblingChanges() {
        var parent = new HappensBefore();
        Step first = new WriteFlagStep(0, true);
        Step second = new WriteFlagStep(1, true);
        parent.record(0, 1, first, 2);
        parent.record(1, 2, second, 4);
        HappensBefore copy = parent.copy();
        HappensBefore sibling = parent.copy();
        assertSame(first, copy.getStep(0, 1));
        assertSame(second, copy.getStep(1, 2));
        assertEquals(2, copy.getPcAtRecord(0, 1));
        assertEquals(4, copy.getPcAtRecord(1, 2));
        assertEquals(1, copy.getThreadLocalCounter(0));
        assertEquals(1, copy.getThreadLocalCounter(1));
        assertTrue(copy.happensBefore(0, 2));
        assertEquals(Set.of(1), copy.getThreadsThatHappenBefore(2));

        copy.record(0, 2, second, 6);
        parent.record(1, 3, first, 8);
        assertNull(parent.getStep(0, 2));
        assertEquals(-1, parent.getPcAtRecord(0, 2));
        assertEquals(1, parent.getThreadLocalCounter(0));
        assertNull(copy.getStep(1, 3));
        assertEquals(-1, copy.getPcAtRecord(1, 3));
        assertEquals(1, copy.getThreadLocalCounter(1));
        assertFalse(copy.happensBefore(1, 3));
        assertSame(second, copy.getStep(0, 2));
        assertEquals(6, copy.getPcAtRecord(0, 2));
        assertEquals(2, copy.getThreadLocalCounter(0));
        assertSame(first, parent.getStep(1, 3));
        assertEquals(8, parent.getPcAtRecord(1, 3));
        assertEquals(2, parent.getThreadLocalCounter(1));
        assertNull(sibling.getStep(0, 2));
        assertEquals(-1, sibling.getPcAtRecord(0, 2));
        assertNull(sibling.getStep(1, 3));
        assertEquals(-1, sibling.getPcAtRecord(1, 3));
        assertEquals(1, sibling.getThreadLocalCounter(0));
        assertEquals(1, sibling.getThreadLocalCounter(1));
        assertEquals(Set.of(0), sibling.getThreadsThatHappenBefore(1));
        assertEquals(Set.of(1), sibling.getThreadsThatHappenBefore(2));
    }
}
