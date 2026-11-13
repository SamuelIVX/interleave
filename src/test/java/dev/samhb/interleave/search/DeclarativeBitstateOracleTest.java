/** Independent typed finite positions and complete VALUE/CBS/DPOR shadows for declarative membership. */
package dev.samhb.interleave.search;

import com.google.gson.Gson;
import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeclarativeBitstateOracleTest {
    /** Independent complete owner frame, ordered by the specified PC/int/bool/array contract.
     * @param pc instruction position
     * @param integer local integer
     * @param flag local boolean
     * @param arraySecond second element of a local array whose first element is fixed
     */
    private record Frame(int pc,int integer,boolean flag,int arraySecond) {}
    /** Public test configuration paired with independent original values.
     * @param shared shared integer value
     * @param frames original owner frames
     * @param config actual position passed to the checked store
     */
    private record Position(int shared,List<Frame> frames,Configuration config) {
        /** Tests value-orbit equality by explicitly trying both owner permutations.
         * @param other independent original position
         * @return true when either complete permutation matches
         */
        boolean sameValue(Position other) {
            return shared==other.shared && (frames.equals(other.frames)
                || frames.get(0).equals(other.frames.get(1)) && frames.get(1).equals(other.frames.get(0)));
        }
    }
    /** Cost context without production key construction.
     * @param position full typed position
     * @param last original last owner or -1
     * @param cost actual marked or queried preemption count
     */
    private record Cost(Position position,int last,int cost) {
        /** Tries both whole-frame/last-owner permutations and the mathematical cost dominance rule.
         * @param query candidate to cover
         * @return whether this mark deliberately covers the complete query
         */
        boolean covers(Cost query) {
            if(cost>query.cost || position.shared!=query.position.shared)return false;
            return position.frames.equals(query.position.frames) && last==query.last
                || position.frames.get(0).equals(query.position.frames.get(1))
                    && position.frames.get(1).equals(query.position.frames.get(0))
                    && (last==-1?-1:1-last)==query.last;
        }
    }
    /** Independent complete mapped product under the one stable value-only witness.
     * @param shared shared integer
     * @param frames sorted complete frames
     * @param mask four mapped owner/first-occurrence tokens, retaining stale actions
     */
    private record SleepIdentity(int shared,List<Frame> frames,int mask) {}
    /** Caller sleep set and its independently chosen complete identity.
     * @param position actual original position
     * @param sleeping actual original owner/action entries
     * @param identity independent full product
     */
    private record Sleep(Position position,Set<Map.Entry<Integer,Step>> sleeping,SleepIdentity identity) {}

    /** Independent finite VALUE/CBS universe includes all actual two-increment consumer positions. */
    @Test void declarativeValuesAndCostsMatchIndependentPermutations() {
        var model=model();var positions=positions(model);assertEquals(75,positions.size());
        var values=BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DFS,1_000_003,4,0);
        for(var target:positions) {
            values.clear();
            for(var other:positions)if(!other.sameValue(target))values.markVisited(other.config);
            assertFalse(values.isVisited(target.config));
            values.clear();values.markVisited(target.config);
            for(var other:positions)if(other.sameValue(target))assertTrue(values.isVisited(other.config));
        }
        var products=new ArrayList<Cost>();
        for(var position:positions)for(int last=-1;last<2;last++)for(int cost=0;cost<3;cost++)products.add(new Cost(position,last,cost));
        assertEquals(675,products.size()); // 75 complete positions × three last owners × three costs.
        var store=BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.CONTEXT_BOUNDED,1_000_003,4,2);
        for(var target:products) {
            store.clear();for(var position:positions)store.markVisited(position.config);
            for(var mark:products)if(!mark.covers(target))store.markVisited(mark.position.config,mark.last,mark.cost);
            assertFalse(store.isVisited(target.position.config,target.last,target.cost),"Finite no-false-positive cost shadow");
            store.clear();store.markVisited(target.position.config,target.last,target.cost);
            for(var query:products)if(target.covers(query))assertTrue(store.isVisited(query.position.config,query.last,query.cost));
        }
        // Both shadows include every actual state, last owner and cost used by these fresh traversals.
        for(int bound=0;bound<3;bound++) {
            var result=new dev.samhb.interleave.cb.ContextBoundedExplorer().explore(model.program(),null,store.freshCopy(),null,bound);
            var exact=new dev.samhb.interleave.cb.ContextBoundedExplorer().explore(model.program(),null,
                SymmetryStateStore.forRun(model.program(),null,Strategy.CONTEXT_BOUNDED),null,bound);
            assertEquals(exact.statesExplored(),result.statesExplored());assertConcreteWitnesses(model,result);
        }
        for(var strategy:List.of(Strategy.DFS,Strategy.STATIC_POR)) {
            var fresh=values.freshCopy();
            if(strategy==Strategy.STATIC_POR)fresh=BitstateSymmetryStateStore.forRun(model.program(),null,strategy,1_000_003,4,0);
            var result=strategy==Strategy.DFS?new DfsExplorer().explore(model.program(),null,fresh,null)
                :new dev.samhb.interleave.por.StaticPorExplorer().explore(model.program(),null,fresh,null);
            assertEquals(6,result.statesExplored());assertConcreteWitnesses(model,result);
        }
    }

    /** Every absent full sleep product remains absent after all other products and VALUE marks. */
    @Test void declarativeSleepProductsUseCompleteStableWitnesses() {
        var model=model();var positions=positions(model);var products=new ArrayList<Sleep>();
        // This is a declared finite order, independently enumerated rather than computed by production sorting.
        var order=frameOrder();
        for(var position:positions)for(int mask=0;mask<16;mask++) {
            boolean swap=order.indexOf(position.frames.get(1))<order.indexOf(position.frames.get(0));
            var frames=swap?List.of(position.frames.get(1),position.frames.get(0)):position.frames;
            var sleeping=new HashSet<Map.Entry<Integer,Step>>();int mapped=0;
            for(int token=0;token<4;token++)if((mask&(1<<token))!=0) {
                int owner=token/2,pc=token%2;
                sleeping.add(Map.entry(owner,model.program().threads().get(owner).steps().get(pc)));
                mapped|=1<<((swap?1-owner:owner)*2+pc);
            }
            products.add(new Sleep(position,Set.copyOf(sleeping),new SleepIdentity(position.shared,frames,mapped)));
        }
        assertEquals(1200,products.size()); // 75 complete positions × all sixteen owner/action subsets.
        // One representative per independently equal product avoids repeating identical probe work.
        var representatives=new LinkedHashMap<SleepIdentity,Sleep>();
        for(var product:products)representatives.putIfAbsent(product.identity,product);
        var store=BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DPOR,1_000_003,4,0);
        for(var target:representatives.values()) {
            store.clear();for(var position:positions)store.markVisited(position.config);
            for(var other:representatives.values())if(!other.identity.equals(target.identity))store.sleepCovered(other.position.config,other.sleeping);
            assertFalse(store.sleepCovered(target.position.config,target.sleeping),"Finite no-false-positive full sleep shadow");
        }
        for(var product:products) {
            store.clear();var representative=representatives.get(product.identity);
            assertFalse(store.sleepCovered(representative.position.config,representative.sleeping));
            assertTrue(store.sleepCovered(product.position.config,product.sleeping));
        }
        var result=new dev.samhb.interleave.dpor.DporExplorer().explore(model.program(),null,store.freshCopy(),null);
        assertEquals(6,result.statesExplored());assertConcreteWitnesses(model,result);
        var property=model.invariant().orElseThrow();
        var observed=BitstateSymmetryStateStore.forRun(model.program(),property,Strategy.DPOR,1_000_003,4,0);
        var always=new dev.samhb.interleave.dpor.DporExplorer().explore(model.program(),property,observed,null);
        assertEquals(6,always.statesExplored());assertConcreteWitnesses(model,always);
        assertTrue(observed.sleepCovered(model.program().initialConfiguration(),Set.of()));
    }

    /** Wire domains and sorted first-occurrence tokens remain byte-for-byte the B9 contract.
     * @throws java.io.IOException if reading an in-memory key fails
     */
    @Test void declarativeDomainsAndAliasedStaleTokensKeepWireContract() throws java.io.IOException {
        var model=new ProgramLoader().load("""
            {"format":"declarative","name":"wire","state":{"fields":[{"name":"x","type":"int","init":0}],"locals":[]},
            "threads":[{"id":0,"steps":[{"effects":["x = 1"]},{"effects":["x = 1"]}]},
                       {"id":1,"steps":[{"effects":["x = 1"]},{"effects":["x = 1"]}]}]}
            """);
        var p=model.program();var config=p.initialConfiguration();
        // Independently packed in Python struct, cross-checked against the declared DynamicState fields.
        String prefix="thread-orbit-v1|[[0, 1]]|AAAAAgAAAAEAAXgAAAAAAAAAAAD/////AAAAAAAAAAAAAAAA|[0, 0]|";
        var context=new SymmetryRunContext(p,null,Strategy.DPOR);
        assertWire(context.valueBytes(config),1,90,prefix+"unbounded",List.of());
        var costs=new SymmetryRunContext(p,null,Strategy.CONTEXT_BOUNDED);
        assertWire(costs.costBytes(config,-1,2),2,83,prefix+"-1",List.of(2));
        var tokens=new LinkedHashSet<Map.Entry<Integer,Step>>();
        for(int owner:List.of(1,0))for(int pc:List.of(1,0))tokens.add(Map.entry(owner,p.threads().get(owner).steps().get(pc)));
        assertWire(context.sleepBytes(config,tokens),3,90,prefix+"unbounded",List.of(4,0,0,0,1,1,0,1,1));
        var threads=new ArrayList<ModelThread>();
        for(var thread:p.threads())threads.add(new ModelThread(thread.id(),List.of(thread.steps().getFirst(),thread.steps().getFirst())));
        var aliased=new Program(config.state(),threads);var state=aliased.initialConfiguration();
        state=TransitionExecutor.execute(aliased,state,0).successor();
        var store=BitstateSymmetryStateStore.forRun(aliased,null,Strategy.DPOR,1_000_003,4,0);
        var first=Set.of(Map.entry(0,threads.getFirst().steps().getFirst()));
        var staleAlias=Set.of(Map.entry(0,threads.getFirst().steps().getLast()));
        assertFalse(store.sleepCovered(state,first));assertTrue(store.sleepCovered(state,staleAlias));
        try(var in=new java.io.DataInputStream(new java.io.ByteArrayInputStream(
                new SymmetryRunContext(aliased,null,Strategy.DPOR).sleepBytes(state,staleAlias)))) {
            in.skipNBytes(2);in.skipNBytes(in.readInt());
            assertEquals(1,in.readInt());assertEquals(1,in.readInt());assertEquals(0,in.readInt());assertEquals(-1,in.read());
        }
        var mismatched=new Program(config.state(),List.of(threads.getFirst(),p.threads().get(1)));
        assertThrows(IllegalArgumentException.class,()->BitstateSymmetryStateStore.forRun(mismatched,null,Strategy.DPOR,1024,4,0));
        assertDoesNotThrow(()->BitstateSymmetryStateStore.forRun(mismatched,null,Strategy.DFS,1024,4,0));
    }

    /** Invalid contexts never mutate even a saturated shared filter; copies and clears stay isolated. */
    @Test void validationAndCrossDomainStatisticsRemainAtomic() {
        var model=model();var p=model.program();var root=p.initialConfiguration();var other=model().program();
        var action=p.threads().getFirst().steps().getFirst();
        var dpor=BitstateSymmetryStateStore.forRun(p,null,Strategy.DPOR,1,4,0);
        dpor.markVisited(root);double before=dpor.estimatedFalsePositiveRate();
        assertTrue(dpor.sleepCovered(root,Set.of()));assertEquals(before,dpor.estimatedFalsePositiveRate());
        for(var invalid:List.of(Map.entry(-1,action),Map.entry(2,action),Map.entry(0,other.threads().getFirst().steps().getFirst()))) {
            var incoming=new LinkedHashSet<Map.Entry<Integer,Step>>();incoming.add(Map.entry(0,action));incoming.add(invalid);
            assertThrows(IllegalArgumentException.class,()->dpor.sleepCovered(root,incoming));
            assertEquals(before,dpor.estimatedFalsePositiveRate());assertEquals(1,dpor.bitCount());assertEquals(1.0,dpor.bitDensity());
        }
        var copy=(BitstateSymmetryStateStore)dpor.freshCopy();assertEquals(0,copy.bitCount());
        assertFalse(copy.sleepCovered(root,Set.of()));dpor.clear();assertEquals(0,dpor.bitCount());
        assertEquals(0.0,dpor.estimatedFalsePositiveRate());assertTrue(copy.sleepCovered(root,Set.of()));
        var cbs=BitstateSymmetryStateStore.forRun(p,null,Strategy.CONTEXT_BOUNDED,8,2,2);
        cbs.markVisited(root);cbs.markVisited(root);
        // The n=2,m=8,k=2 single-lookup heuristic was independently derived with Decimal and exp.
        assertEquals(0.15481812174617549,cbs.estimatedFalsePositiveRate(),1e-14);
        int bits=cbs.bitCount();double rate=cbs.estimatedFalsePositiveRate();
        for(int last:List.of(-2,2)) {
            assertThrows(IllegalArgumentException.class,()->cbs.isVisited(root,last,2));
            assertThrows(IllegalArgumentException.class,()->cbs.markVisited(root,last,2));
        }
        for(int cost:List.of(-1,3)) {
            assertThrows(IllegalArgumentException.class,()->cbs.isVisited(root,-1,cost));
            assertThrows(IllegalArgumentException.class,()->cbs.markVisited(root,-1,cost));
        }
        assertEquals(bits,cbs.bitCount());assertEquals(rate,cbs.estimatedFalsePositiveRate());
        cbs.isVisited(root,-1,2);
        // Decimal and an independent floating-point exp route give this capped three-candidate heuristic.
        assertEquals(0.4644543652385265,cbs.estimatedFalsePositiveRate(),1e-14);
        var fresh=(BitstateSymmetryStateStore)cbs.freshCopy();assertEquals(2,fresh.maxPreemptions());
        for(int cost=0;cost<3;cost++)cbs.markVisited(root,-1,cost);
        assertTrue(cbs.bitCount()<=8);cbs.clear();assertEquals(0,cbs.bitCount());assertEquals(0.0,cbs.estimatedFalsePositiveRate());
        assertEquals(0,fresh.bitCount());assertEquals(0.0,fresh.estimatedFalsePositiveRate());
        assertThrows(IllegalArgumentException.class,()->new dev.samhb.interleave.dpor.DporExplorer().explore(other,null,copy,null));
        for(var strategy:Strategy.values()) {
            int bound=strategy==Strategy.CONTEXT_BOUNDED?2:0;
            assertThrows(IllegalArgumentException.class,()->BitstateSymmetryStateStore.forRun(p,(s,c)->true,strategy,1024,4,bound));
            for(int[] settings:new int[][]{{0,4,0},{1,0,0},{1,4,-1}})assertThrows(IllegalArgumentException.class,
                ()->BitstateSymmetryStateStore.forRun(p,null,strategy,settings[0],settings[1],settings[2]));
            if(strategy!=Strategy.CONTEXT_BOUNDED)assertThrows(IllegalArgumentException.class,
                ()->BitstateSymmetryStateStore.forRun(p,null,strategy,1024,4,1));
        }
    }

    /** Reads actual bytes against independent literal fields and requires complete consumption.
     * @param bytes emitted bytes
     * @param domain expected product domain
     * @param length independently counted UTF-8 orbit length
     * @param orbit independently packed whole-orbit literal
     * @param suffix expected complete integer suffix
     * @throws java.io.IOException if the encoded bytes are truncated
     */
    private static void assertWire(byte[] bytes,int domain,int length,String orbit,List<Integer> suffix) throws java.io.IOException {
        try(var in=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            assertEquals(1,in.readUnsignedByte());assertEquals(domain,in.readUnsignedByte());assertEquals(length,in.readInt());
            assertEquals(orbit,new String(in.readNBytes(length),java.nio.charset.StandardCharsets.UTF_8));
            for(int expected:suffix)assertEquals(expected,in.readInt());assertEquals(-1,in.read());
        }
    }

    /** Declared order covers every local type plus root, intermediate, terminal and equal frames.
     * @return finite complete frame order
     */
    private static List<Frame> frameOrder() {
        return List.of(new Frame(0,0,false,2),new Frame(0,0,false,3),new Frame(0,0,true,2),
            new Frame(0,1,false,2),new Frame(1,0,false,2),new Frame(2,0,false,2));
    }
    /** Enumerates original complete values without production grouping or canonicalization.
     * @param model trusted declarative fixture
     * @return all finite positions required by the shadows and actual consumers
     */
    private static List<Position> positions(BenchmarkProgram model) {
        var order=frameOrder();var pairs=new ArrayList<List<Frame>>();
        for(var frame:order) {
            pairs.add(List.of(order.getFirst(),frame));
            if(!frame.equals(order.getFirst()))pairs.add(List.of(frame,order.getFirst()));
        }
        pairs.add(List.of(order.get(4),order.get(4)));pairs.add(List.of(order.get(5),order.get(5)));
        pairs.add(List.of(order.get(4),order.get(5)));pairs.add(List.of(order.get(5),order.get(4)));
        var positions=new ArrayList<Position>();
        for(int shared=0;shared<5;shared++)for(var pair:pairs) {
            var state=(DynamicState)model.program().initialConfiguration().state();state.setInt("x",shared);
            for(int owner=0;owner<2;owner++) {
                var frame=pair.get(owner);state.setLocalInt(owner,"r",frame.integer);
                state.setLocalBool(owner,"flag",frame.flag);state.setLocalArrayElement(owner,"values",1,frame.arraySecond);
            }
            positions.add(new Position(shared,pair,Configuration.forTest(state,List.of(pair.get(0).pc,pair.get(1).pc),List.of(2,2),List.of())));
        }
        return positions;
    }
    /** Executes original choices and distinguishes complete terminal values from bounded prefixes.
     * @param model original program
     * @param result actual approximate result after its finite shadow was checked
     */
    private static void assertConcreteWitnesses(BenchmarkProgram model,DfsResult result) {
        assertFalse(result.traces().isEmpty());
        for(var trace:result.traces()) {
            assertTrue(Set.of(TraceOutcome.COMPLETED,TraceOutcome.INCOMPLETE).contains(trace.outcome()));
            var config=model.program().initialConfiguration();
            for(int index=0;index<trace.length();index++) {
                int owner=trace.threadIds().get(index);assertTrue(config.enabledThreadIds().contains(owner));
                var next=TransitionExecutor.execute(model.program(),config,owner);
                assertEquals(StepOutcome.ADVANCED,next.outcome());assertEquals(trace.outcomes().get(index),next.outcome());config=next.successor();
            }
            if(trace.outcome()==TraceOutcome.COMPLETED) {
                assertTrue(config.allTerminated());assertEquals(4,((DynamicState)config.state()).getInt("x"));
            } else {
                assertFalse(config.allTerminated());
                assertEquals(dev.samhb.interleave.core.CutoffEvidence.Reason.PREEMPTION_BOUND,trace.evidence().cutoff().reason());
            }
        }
    }
    /** Loads immutable certified actions with two distinct occurrences per owner and all local types.
     * @return actual declarative two-owner two-increment model
     */
    private static BenchmarkProgram model() {
        var steps=List.of(Map.of("effects",List.of("x = x + 1")),Map.of("effects",List.of("x = x + 1")));
        return new ProgramLoader().load(new Gson().toJson(Map.of("format","declarative","name","typedshadow",
            "state",Map.of("fields",List.of(Map.of("name","x","type","int","init",0)),"locals",List.of(
                Map.of("name","r","type","int","init",0),Map.of("name","flag","type","bool","init",false),
                Map.of("name","values","type","int[]","init",List.of(1,2)))),
            "invariant",Map.of("when","always","expr","x >= 0"),
            "threads",List.of(Map.of("id",0,"steps",steps),Map.of("id",1,"steps",steps)))));
    }
}
