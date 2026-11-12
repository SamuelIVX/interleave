/** Independent whole-frame permutations, last-owner products and cost-dominance shadow. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitstateSymmetryCbsOracleTest {
    /** Complete owner frame identity.
     * @param pc program counter
     * @param integer integer local
     * @param flag boolean local
     * @param arraySecond second local-array element
     */
    private record Frame(int pc, int integer, boolean flag, int arraySecond) {}
    /** Full position, with public configuration only for invoking the store.
     * @param shared shared integer value
     * @param left original owner-zero frame
     * @param right original owner-one frame
     * @param config independent public test configuration
     */
    private record Position(int shared, Frame left, Frame right, Configuration config) {}
    /** Complete scheduling product tested against both independent owner permutations.
     * @param position full typed position
     * @param last last physical owner, or initial -1
     * @param cost insertion/query cost
     */
    private record Product(Position position, int last, int cost) {
        /** Checks intended minimum-cost coverage without production canonicalization or hashing.
         * @param target queried product
         * @return whether this exact mark deliberately covers the query under either permutation
         */
        boolean covers(Product target) {
            if (cost > target.cost || position.shared != target.position.shared) return false;
            return (position.left.equals(target.position.left) && position.right.equals(target.position.right) && last == target.last)
                || (position.left.equals(target.position.right) && position.right.equals(target.position.left)
                    && (last == -1 ? -1 : 1-last) == target.last);
        }
    }

    /** Every noncovering key and VALUE domain is inserted before each absent target is probed. */
    @Test void completeProductsMatchIndependentPermutationAndDominanceOracle() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; local int r = 0; local bool flag = false; local int[] values = [1,2]; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } }");
        var base = new Frame(0,0,false,2);
        var variants = List.of(base,new Frame(1,0,false,2),new Frame(0,1,false,2),new Frame(0,0,true,2),new Frame(0,0,false,3));
        var pairs = new ArrayList<List<Frame>>();
        for (var variant : variants) {
            pairs.add(List.of(base,variant));
            if (!variant.equals(base)) pairs.add(List.of(variant,base));
        }
        pairs.add(List.of(variants.get(1),variants.get(1))); // Includes the actual terminal position.
        var positions = new ArrayList<Position>();
        for (int shared = 0; shared < 3; shared++) for (var pair : pairs) {
            var state = (DynamicState)model.program().initialConfiguration().state().deepCopy();
            state.setInt("x",shared);
            for (int owner = 0; owner < 2; owner++) {
                var frame = pair.get(owner);
                state.setLocalInt(owner,"r",frame.integer);state.setLocalBool(owner,"flag",frame.flag);
                state.setLocalArrayElement(owner,"values",1,frame.arraySecond);
            }
            positions.add(new Position(shared,pair.get(0),pair.get(1),Configuration.forTest(state,
                List.of(pair.get(0).pc,pair.get(1).pc),List.of(1,1),List.of())));
        }
        var store = verifyShadow(model.program(),positions,270);
        for (int bound = 0; bound < 3; bound++) {
            var fresh = (BitstateSymmetryStateStore)store.freshCopy();
            var result = new dev.samhb.interleave.cb.ContextBoundedExplorer().explore(model.program(),null,fresh,null,bound);
            assertEquals(3,result.statesExplored());
            for (var trace : result.traces()) assertDoesNotThrow(() -> new TraceReplayer().replay(model.program(),trace));
        }
    }
    /** Checks the no-local counter fixture used by iterative resource consumer assertions. */
    @Test void consumerCounterHasAnIndependentCbsShadow() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } }");
        var positions = new ArrayList<Position>();
        for (int mask = 0; mask < 4; mask++) {
            int left = mask & 1, right = (mask >>> 1) & 1;
            var state = (DynamicState)model.program().initialConfiguration().state().deepCopy();
            int shared = Integer.bitCount(mask);state.setInt("x",shared);
            positions.add(new Position(shared,new Frame(left,0,false,2),new Frame(right,0,false,2),
                Configuration.forTest(state,List.of(left,right),List.of(1,1),List.of())));
        }
        verifyShadow(model.program(),positions,36);
    }

    /** Inserts every noncovering complete key before testing each negative decision.
     * @param program bound source model
     * @param positions independently enumerated full positions
     * @param expectedProducts independently counted size of the cost/last-owner product
     * @return checked store, usable through an empty independent copy
     */
    private static BitstateSymmetryStateStore verifyShadow(dev.samhb.interleave.core.Program program,
            List<Position> positions, int expectedProducts) {
        var products = new ArrayList<Product>();
        for (var position : positions) for (int last = -1; last < 2; last++) for (int cost = 0; cost < 3; cost++) {
            products.add(new Product(position,last,cost));
        }
        assertEquals(expectedProducts,products.size());
        var store = BitstateSymmetryStateStore.forRun(program,null,Strategy.CONTEXT_BOUNDED,1_000_003,4,2);
        for (var target : products) {
            store.clear();
            for (var position : positions) store.markVisited(position.config);
            for (var mark : products) if (!mark.covers(target)) store.markVisited(mark.position.config,mark.last,mark.cost);
            assertFalse(store.isVisited(target.position.config,target.last,target.cost),"False positive in fixed complete-product shadow: " + target);
            store.clear();
            store.markVisited(target.position.config,target.last,target.cost);
            for (var query : products) if (target.covers(query)) assertTrue(store.isVisited(query.position.config,query.last,query.cost));
        }
        return store;
    }

}
