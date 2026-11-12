/** Independent deterministic whole-frame witnesses and full mapped sleep-product equality. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitstateSymmetryDporOracleTest {
    /** Actual visible counter consumers have empty sleep sets and independently enumerated masks.
     * @throws Exception if loading the checked-in four-owner source fails
     */
    @Test void visibleCounterConsumersHaveTheirOwnProductShadow() throws Exception {
        var loader = new SourceProgramLoader();
        verifyVisibleCounter(loader.load("program pair { shared int x = 0; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } invariant final (x == 2); }"),"x",3);
        verifyVisibleCounter(loader.loadFromFile(java.nio.file.Path.of("examples/programs/symmetry-counter.ilv")),"counter",5);
    }

    /** Checks every mask against every different population before comparing actual counts.
     * @param model one dependent shared increment per owner, with shared final property
     * @param field fixed shared counter name
     * @param expectedVisits independent count of population ranks, zero through owner count
     */
    private static void verifyVisibleCounter(dev.samhb.interleave.bugs.BenchmarkProgram model,String field,int expectedVisits) {
        var p = model.program();int owners = p.threadCount();
        for (var thread : p.threads()) assertEquals(1,thread.steps().size());
        var configs = new ArrayList<Configuration>();var counts = new ArrayList<Integer>();
        for (int mask = 0; mask < (1 << owners); mask++) {
            int count = Integer.bitCount(mask);var state = (DynamicState)p.initialConfiguration().state().deepCopy();state.setInt(field,count);
            var pcs = new ArrayList<Integer>();for (int owner = 0; owner < owners; owner++) pcs.add((mask >>> owner) & 1);
            configs.add(Configuration.forTest(state,pcs,Collections.nCopies(owners,1),List.of()));counts.add(count);
        }
        var store = BitstateSymmetryStateStore.forRun(p,model.invariant().orElseThrow(),Strategy.DPOR,1_000_003,4,0);
        for (int target = 0; target < configs.size(); target++) {
            store.clear();for (var config : configs) store.markVisited(config);
            for (int other = 0; other < configs.size(); other++) if (!counts.get(target).equals(counts.get(other))) store.sleepCovered(configs.get(other),Set.of());
            assertFalse(store.sleepCovered(configs.get(target),Set.of()));
            store.clear();assertFalse(store.sleepCovered(configs.get(target),Set.of()));
            for (int other = 0; other < configs.size(); other++) if (counts.get(target).equals(counts.get(other))) assertTrue(store.sleepCovered(configs.get(other),Set.of()));
        }
        var result = new dev.samhb.interleave.dpor.DporExplorer().explore(p,model.invariant().orElseThrow(),store.freshCopy(),null);
        assertEquals(expectedVisits,result.statesExplored());
        for (var trace : result.traces()) assertDoesNotThrow(() -> new TraceReplayer().replay(p,trace,model.invariant().orElseThrow()));
    }
    /** Whole-frame value, independent of production encoding.
     * @param pc instruction position
     * @param integer integer local
     * @param flag boolean local
     * @param second second array element
     */
    private record Frame(int pc, int integer, boolean flag, int second) {}
    /** Original action occurrence used by the finite oracle.
     * @param owner original owner
     * @param pc original first occurrence
     */
    private record Token(int owner, int pc) {}
    /** Expected complete product under one independently chosen stable witness.
     * @param shared fixed shared integer
     * @param frames complete ordered owner frames
     * @param tokens mapped sleepers; equality, not subset coverage
     */
    private record Identity(int shared, List<Frame> frames, Set<Token> tokens) {}
    /** One actual public-interface call and its independent expected identity.
     * @param config original configuration
     * @param sleeping original owner/action entries
     * @param identity independent whole product
     */
    private record Product(Configuration config, Set<Map.Entry<Integer,Step>> sleeping, Identity identity) {}

    /** All absent products stay absent after every other product and VALUE position is marked. */
    @Test void fullProductsMatchStableWitnessOracleWithoutValueOrSubsetPruning() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; local int r = 0; local bool flag = false; local int[] values = [1,2]; thread 0 { atomic {} atomic {} } thread 1 { atomic {} atomic {} } }");
        var program = model.program();
        // Independently enumerated value order: PC, then integer, boolean, array. Ties keep ID.
        var order = List.of(new Frame(0,0,false,2),new Frame(0,0,false,3),new Frame(0,0,true,2),
            new Frame(0,1,false,2),new Frame(1,0,false,2),new Frame(2,0,false,2));
        var pairs = new ArrayList<List<Frame>>();
        for (var frame : order) {
            pairs.add(List.of(order.getFirst(),frame));
            if (!frame.equals(order.getFirst())) pairs.add(List.of(frame,order.getFirst()));
        }
        pairs.add(List.of(order.get(4),order.get(4)));pairs.add(List.of(order.get(5),order.get(5)));
        pairs.add(List.of(order.get(4),order.get(5)));pairs.add(List.of(order.get(5),order.get(4)));
        var products = new ArrayList<Product>();
        for (int shared = 0; shared < 2; shared++) for (var pair : pairs) {
            var state = (DynamicState)program.initialConfiguration().state().deepCopy();state.setInt("x",shared);
            for (int owner = 0; owner < 2; owner++) {
                var frame = pair.get(owner);
                state.setLocalInt(owner,"r",frame.integer);state.setLocalBool(owner,"flag",frame.flag);
                state.setLocalArrayElement(owner,"values",1,frame.second);
            }
            var config = Configuration.forTest(state,List.of(pair.get(0).pc,pair.get(1).pc),List.of(2,2),List.of());
            boolean swap = order.indexOf(pair.get(1)) < order.indexOf(pair.get(0));
            var canonicalFrames = swap ? List.of(pair.get(1),pair.get(0)) : pair;
            for (int mask = 0; mask < 16; mask++) {
                var sleeping = new HashSet<Map.Entry<Integer,Step>>();var mapped = new HashSet<Token>();
                for (int action = 0; action < 4; action++) if ((mask & (1 << action)) != 0) {
                    int owner = action / 2, pc = action % 2;
                    sleeping.add(Map.entry(owner,program.threads().get(owner).steps().get(pc)));
                    mapped.add(new Token(swap ? 1-owner : owner,pc));
                }
                products.add(new Product(config,Set.copyOf(sleeping),new Identity(shared,List.copyOf(canonicalFrames),Set.copyOf(mapped))));
            }
        }
        assertEquals(480,products.size()); // 2 shared × 15 full-frame pairs × 16 sleep subsets.
        var store = BitstateSymmetryStateStore.forRun(program,null,Strategy.DPOR,1_000_003,4,0);
        for (var target : products) {
            store.clear();
            for (var other : products) store.markVisited(other.config);
            for (var other : products) if (!other.identity.equals(target.identity)) store.sleepCovered(other.config,other.sleeping);
            // A positive lookup already has every probe set, so skipping its mark cannot change bits.
            assertFalse(store.sleepCovered(target.config,target.sleeping),"False positive in fixed full-product shadow: " + target.identity);
            store.clear();assertFalse(store.sleepCovered(target.config,target.sleeping));
            for (var other : products) if (other.identity.equals(target.identity)) assertTrue(store.sleepCovered(other.config,other.sleeping));
        }
        var exact = new dev.samhb.interleave.dpor.DporExplorer().explore(program,null,SymmetryStateStore.forRun(program,null,Strategy.DPOR),null);
        var approximate = new dev.samhb.interleave.dpor.DporExplorer().explore(program,null,store.freshCopy(),null);
        assertEquals(exact.statesExplored(),approximate.statesExplored());
        assertEquals(outcomes(exact),outcomes(approximate));
        for (var trace : approximate.traces()) assertDoesNotThrow(() -> new TraceReplayer().replay(program,trace));
    }

    /** Extracts observable terminal categories without depending on trace order.
     * @param result completed traversal
     * @return observed categories
     */
    private static Set<TraceOutcome> outcomes(DfsResult result) {
        var outcomes = new HashSet<TraceOutcome>();
        for (var trace : result.traces()) outcomes.add(trace.outcome());
        return outcomes;
    }
}
