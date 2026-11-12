/** Independent whole-frame orbit shadow and typed byte protocol checks. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApproximateSymmetryOracleTest {
    /** The shared package-owned binding rejects a null strategy independently of factory guards. */
    @Test void runContextRequiresStrategy() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        assertThrows(NullPointerException.class, () -> new SymmetryRunContext(model.program(),null,null));
    }
    /** Whole owner frame axes; record equality is independent of production canonicalization.
     * @param pc program counter
     * @param integer integer local
     * @param flag boolean local
     * @param second second array element
     */
    private record Frame(int pc, int integer, boolean flag, int second) {}
    /** Declarative oracle identity without any production key encoder.
     * @param shared shared integer
     * @param left first whole frame
     * @param right second whole frame
     * @param config corresponding public configuration
     */
    private record Position(int shared, Frame left, Frame right, Configuration config) {
        /** Compares the two possible complete-owner permutations.
         * @param other independent target position
         * @return true exactly for equal shared values and equal frame multisets
         */
        boolean sameOrbit(Position other) {
            return shared == other.shared && ((left.equals(other.left) && right.equals(other.right))
                || (left.equals(other.right) && right.equals(other.left)));
        }
    }

    /** The actual runner/CLI counter fixtures get their own finite collision shadow.
     * @throws Exception if loading the source fixture fails
     */
    @Test void counterFixturesUsedByConsumersHaveNoFalsePositives() throws Exception {
        var loader = new SourceProgramLoader();
        var pair = loader.load("program pair { shared int x = 0; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } invariant final (x == 2); }");
        assertCounterShadow(pair,"x",4);
        assertCounterShadow(loader.loadFromFile(java.nio.file.Path.of("examples/programs/symmetry-counter.ilv")),"counter",16);
    }

    /** Enumerates every reachable mask independently; equal popcounts are exactly the counter orbits.
     * @param model counter fixture with one increment per interchangeable owner
     * @param field shared counter name
     * @param expectedMasks literal two/four-owner mask count
     */
    private static void assertCounterShadow(dev.samhb.interleave.bugs.BenchmarkProgram model, String field, int expectedMasks) {
        int owners = model.program().threadCount();
        for (var thread : model.program().threads()) assertEquals(1,thread.steps().size());
        var positions = new ArrayList<Configuration>();
        var counts = new ArrayList<Integer>();
        for (int mask = 0; mask < (1 << owners); mask++) {
            var state = (DynamicState)model.program().initialConfiguration().state().deepCopy();
            int count = Integer.bitCount(mask);
            state.setInt(field,count);
            var pcs = new ArrayList<Integer>();
            for (int owner = 0; owner < owners; owner++) pcs.add((mask >>> owner) & 1);
            positions.add(Configuration.forTest(state,pcs,Collections.nCopies(owners,1),List.of()));
            counts.add(count);
        }
        assertEquals(expectedMasks,positions.size());
        for (var strategy : List.of(Strategy.DFS,Strategy.STATIC_POR)) {
            var store = BitstateSymmetryStateStore.forRun(model.program(),model.invariant().orElseThrow(),strategy,1_000_003,4,0);
            for (int target = 0; target < positions.size(); target++) {
                store.clear();
                for (int other = 0; other < positions.size(); other++) {
                    if (!counts.get(target).equals(counts.get(other))) store.markVisited(positions.get(other));
                }
                assertFalse(store.isVisited(positions.get(target)),"Consumer fixture shadow found a false positive");
                store.markVisited(positions.get(target));
                for (int other = 0; other < positions.size(); other++) {
                    if (counts.get(target).equals(counts.get(other))) assertTrue(store.isVisited(positions.get(other)));
                }
            }
        }
    }

    /** Saturating every unequal orbit establishes zero false positives over every fixture subset. */
    @Test void fullFrameShadowHasNoFalsePositivesAndPermutationsStayPositive() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; local int r = 0; local bool flag = false; local int[] values = [1, 2]; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } }");
        var frames = List.of(new Frame(0,0,false,2),new Frame(1,0,false,2),new Frame(0,1,false,2),
            new Frame(0,0,true,2),new Frame(0,0,false,3));
        var positions = new ArrayList<Position>();
        for (int shared = 0; shared < 3; shared++) for (var left : frames) for (var right : frames) {
            var state = (DynamicState)model.program().initialConfiguration().state().deepCopy();
            state.setInt("x",shared);
            for (int owner = 0; owner < 2; owner++) {
                var frame = owner == 0 ? left : right;
                state.setLocalInt(owner,"r",frame.integer()); state.setLocalBool(owner,"flag",frame.flag());
                state.setLocalArrayElement(owner,"values",1,frame.second());
            }
            positions.add(new Position(shared,left,right,Configuration.forTest(state,List.of(left.pc(),right.pc()),List.of(1,1),List.of())));
        }
        assertEquals(75,positions.size()); // 3 shared values × 5² ordered complete frames.
        for (var strategy : List.of(Strategy.DFS, Strategy.STATIC_POR)) {
            var store = BitstateSymmetryStateStore.forRun(model.program(),null,strategy,1_000_003,4,0);
            for (var target : positions) {
                store.clear();
                for (var other : positions) if (!target.sameOrbit(other)) store.markVisited(other.config());
                assertFalse(store.isVisited(target.config()),"False positive in fixed full-frame shadow: " + target);
                store.markVisited(target.config());
                for (var other : positions) if (target.sameOrbit(other)) assertTrue(store.isVisited(other.config()));
            }
            store.clear();
            var result = strategy == Strategy.DFS
                ? new DfsExplorer().explore(model.program(),null,store,null)
                : new dev.samhb.interleave.por.StaticPorExplorer().explore(model.program(),null,store,null);
            assertEquals(3,result.statesExplored());
            for (var trace : result.traces()) assertDoesNotThrow(() -> new TraceReplayer().replay(model.program(),trace));
        }
    }

    /** Checks the wire envelope independently of the production ByteBuffer encoder.
     * @throws IOException if the in-memory stream is malformed
     */
    @Test void valueBytesCarryVersionDomainLengthAndCompleteOrbit() throws IOException {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        var context = new SymmetryRunContext(model.program(),null,Strategy.DFS);
        byte[] bytes = context.valueBytes(model.program().initialConfiguration());
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(1,input.readUnsignedByte()); assertEquals(1,input.readUnsignedByte());
            int length = input.readInt(); assertEquals(bytes.length - 6,length);
            String orbit = new String(input.readNBytes(length),StandardCharsets.UTF_8);
            // Literal shared-state payload derived independently via Python struct and hex bytes.
            assertEquals("thread-orbit-v1|[[0, 1]]|AAAAAgAAAAEAAXgAAAAAAAAAAAD/////AAAAAAAAAAAAAAAA|[0, 0]|unbounded",orbit);
            assertEquals(-1,input.read());
        }
    }
}
