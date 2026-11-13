/** Public approximate declarative selection, producing-pass ownership and diagnostic identity. */
package dev.samhb.interleave;

import com.google.gson.JsonParser;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.report.*;
import dev.samhb.interleave.search.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApproximateDeclarativeSymmetryRunTest {
    /** Every strategy reaches owned checked membership without fabricating source identity. */
    @Test void allDeclarativeStrategiesUseOwnedApproximateMembership() {
        var model=new ProgramLoader().loadFromFile(Path.of("examples/programs/semaphore-declarative.json"));
        var property=model.invariant().orElseThrow();
        for(var strategy:Strategy.values()) {
            var store=BitstateSymmetryStateStore.forRun(model.program(),property,strategy,1_000_003,4,
                strategy==Strategy.CONTEXT_BOUNDED?2:0);
            assertEquals(0,store.bitCount());store.requireRun(model.program(),property,strategy);
            var result=approximate(strategy,1_000_003).invariant(property).build().run(model.program());
            assertFalse(result.hasViolation());assertFalse(result.limitExceeded());assertTrue(result.symmetryReduction());
            var json=JsonParser.parseString(result.toJson()).getAsJsonObject();
            assertEquals("BITSTATE",json.get("storeType").getAsString());
            assertFalse(json.has("programKind"));assertFalse(json.has("sourceDigest"));
            assertTrue(result.resourcePrefix().isEmpty());
        }
    }
    /** Actual deadlocks, root failures and final-property failures keep precedence over approximation. */
    @Test void positiveObservationsRetainActualMeaning() {
        String text="""
            {"format":"declarative","name":"positive","state":{"fields":[{"name":"x","type":"int","init":0}],"locals":[]},
             "threads":[{"id":0,"steps":[{"guard":"false","effects":["x = 1"]}]},
                        {"id":1,"steps":[{"guard":"false","effects":["x = 1"]}]}]}
            """;
        var base=JsonParser.parseString(text).getAsJsonObject();
        for(String timing:List.of("deadlock","always","final")) {
            var document=base.deepCopy();
            if(!timing.equals("deadlock")) {
                var property=new com.google.gson.JsonObject();property.addProperty("when",timing);
                property.addProperty("expr",timing.equals("always")?"false":"x == 2");document.add("invariant",property);
                if(timing.equals("final"))for(var thread:document.getAsJsonArray("threads"))
                    thread.getAsJsonObject().getAsJsonArray("steps").get(0).getAsJsonObject().remove("guard");
            }
            var model=new ProgramLoader().load(document.toString());
            var rows=new BenchmarkHarness(1_000_003,4,Set.of(StoreType.BITSTATE),null,2,false,true).runProgram(model);
            assertEquals(4,rows.size());
            assertTrue(new SoundnessAttestation(rows,List.of(model)).isSound());
            for(var row:rows) {
                assertEquals(timing.equals("deadlock")?"DEADLOCK":"VIOLATION",row.verdict());
                assertEquals(StoreType.BITSTATE,row.storeType());assertFalse(row.sourceModel());
                var trace=row.failingTrace().orElseThrow();
                if(timing.equals("always"))assertEquals(0,trace.length());
                if(timing.equals("deadlock"))assertTrue(model.program().initialConfiguration().enabledThreadIds().isEmpty());
                if(timing.equals("final"))assertFalse(trace.threadIds().isEmpty());
            }
        }
        var model=new ProgramLoader().loadFromFile(Path.of("examples/programs/semaphore-declarative.json"));
        var ordinaryDefault=InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(1024,4).build().run(model.program());
        assertEquals("DFS",JsonParser.parseString(ordinaryDefault.toJson()).getAsJsonObject().get("strategy").getAsString());
    }

    /** Saturation can hide real errors, but approximate absence never participates in exact agreement.
     * @throws java.io.IOException if writing fresh consumer artifacts fails
     */
    @Test void collisionsAndMixedReportsRetainTruthfulPrecision() throws java.io.IOException {
        var model=new ProgramLoader().loadFromResource("dsl/symmetry-committed-failure.json");
        var approximate=new BenchmarkHarness(1,4,Set.of(StoreType.BITSTATE),null,2,false,true).runProgram(model);
        var exact=new BenchmarkHarness(1,4,Set.of(StoreType.EXACT),null,2,false,true).runProgram(model);
        assertEquals(4,approximate.size());assertEquals(4,exact.size());
        for(var row:approximate) {
            assertEquals("APPROXIMATE_PASS",row.verdict());assertEquals(StoreType.BITSTATE,row.storeType());
            assertTrue(row.symmetryReduction());assertFalse(row.sourceModel());assertTrue(row.failingTrace().isEmpty());
            assertEquals(1,row.bitstateBitCount());assertEquals(1.0,row.bitstateBitDensity());
            assertTrue(row.estimatedFalsePositiveRate()>0);
        }
        for(var row:exact)assertEquals("VIOLATION",row.verdict());
        var mixed=new ArrayList<>(exact);mixed.addAll(approximate);
        var report=new ReportWriter(mixed,List.of(model));
        var json=JsonParser.parseString(report.writeJson()).getAsJsonObject();assertTrue(json.get("soundness").getAsBoolean());
        assertTrue(report.writeMarkdown().contains("APPROXIMATE_PASS"));
        Files.createDirectories(Path.of("build"));
        for(var strategy:Strategy.values()) {
            var runner=approximate(strategy,1).invariant(model.invariant().orElseThrow()).build();
            var result=runner.run(model.program());
            assertFalse(result.hasViolation());assertTrue(result.completedTraces().isEmpty());assertEquals(1,result.statesExplored());
            assertTrue(result.resourcePrefix().isEmpty());
            if(strategy==Strategy.DPOR)Files.writeString(Path.of("build/declarative-symmetry-approximate-no-trace.json"),result.toJson());
            for(int limit:List.of(0,3)) {
            var real=approximate(strategy,1_000_003).maxStates(limit).invariant(model.invariant().orElseThrow()).build().run(model.program());
            assertEquals(limit>0,real.limitExceeded());
            assertTrue(real.hasViolation());assertFalse(real.failingTraces().isEmpty());
            for(var trace:real.failingTraces()) {
                assertEquals(trace.threadIds().size()+1,trace.states().size());
                assertEquals(1,trace.states().getLast().fields().get("x"));
                assertTrue(trace.states().stream().noneMatch(state->Integer.valueOf(7).equals(state.fields().get("x"))));
            }
            if(strategy==Strategy.DPOR && limit==0)Files.writeString(Path.of("build/declarative-symmetry-approximate-failure-library.json"),real.toJson());
            }
        }
    }

    /** Reuse, concurrency and actual root/later resource stops keep run-owned filters and JSON identity.
     * @throws Exception if concurrent execution or fixture writes fail
     */
    @Test void repeatedConcurrentAndInterruptedRunsStayIsolated() throws Exception {
        var model=new ProgramLoader().loadFromFile(Path.of("examples/programs/semaphore-declarative.json"));
        Files.createDirectories(Path.of("build"));
        for(var strategy:Strategy.values()) {
            var runner=approximate(strategy,1_000_003).invariant(model.invariant().orElseThrow()).build();
            var completed=runner.run(model.program());long expected=completed.statesExplored();
            assertEquals(expected,runner.run(model.program()).statesExplored());assertFalse(completed.completedTraces().isEmpty());
            try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<TestResult> run=()->{ready.countDown();start.await();return runner.run(model.program());};
                var a=executor.submit(run);var b=executor.submit(run);
                try {assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));} finally {start.countDown();}
                assertEquals(expected,a.get().statesExplored());assertEquals(expected,b.get().statesExplored());
            }
            for(int limit:List.of(1,2)) {
                var partial=approximate(strategy,1_000_003).maxStates(limit).build().run(model.program());
                assertTrue(partial.limitExceeded());assertEquals(limit,partial.statesExplored());assertTrue(partial.resourcePrefix().isEmpty());
                var json=JsonParser.parseString(partial.toJson()).getAsJsonObject();
                assertEquals("MAX_STATES",json.get("resourceLimitReason").getAsString());
                assertEquals("BITSTATE",json.get("storeType").getAsString());assertFalse(json.has("programKind"));
                if(strategy==Strategy.DPOR)Files.writeString(Path.of("build/declarative-symmetry-approximate-resource-"+limit+".json"),partial.toJson());
            }
            var deadline=approximate(strategy,1_000_003).maxTime(java.time.Duration.ZERO).build().run(model.program(),()->0L);
            assertTrue(deadline.limitExceeded());assertEquals(1,deadline.statesExplored());assertTrue(deadline.resourcePrefix().isEmpty());
            assertEquals("MAX_TIME",JsonParser.parseString(deadline.toJson()).getAsJsonObject().get("resourceLimitReason").getAsString());
            if(strategy==Strategy.DPOR)Files.writeString(Path.of("build/declarative-symmetry-approximate-completed.json"),completed.toJson());
        }
    }

    /** Hidden cutoffs cannot stop approximate deepening early or aggregate discarded-pass metrics. */
    @Test void iterativeDeclarativeRunsRetainProducingBoundAndMetrics() {
        var model=new ProgramLoader().loadFromResource("dsl/symmetry-committed-failure.json");
        var iterative=new BenchmarkHarness(1,4,Set.of(StoreType.BITSTATE),Set.of("CONTEXT_BOUNDED"),2,true,true).runProgram(model).getFirst();
        var single=new BenchmarkHarness(1,4,Set.of(StoreType.BITSTATE),Set.of("CONTEXT_BOUNDED"),2,false,true).runProgram(model).getFirst();
        assertEquals(2,iterative.preemptionsUsed());assertEquals("APPROXIMATE_PASS",iterative.verdict());
        assertEquals(single.statesExplored(),iterative.statesExplored());assertEquals(1,iterative.statesExplored());
        assertEquals(single.bitstateBitCount(),iterative.bitstateBitCount());
        assertEquals(single.bitstateBitDensity(),iterative.bitstateBitDensity());
        assertEquals(single.estimatedFalsePositiveRate(),iterative.estimatedFalsePositiveRate());
        var limited=approximate(Strategy.CONTEXT_BOUNDED,1).iterativeDeepening(true).maxStates(2).build().run(model.program());
        assertTrue(limited.limitExceeded());assertEquals(2,limited.statesExplored());assertFalse(limited.hasViolation());
        assertTrue(limited.resourcePrefix().isEmpty());
        var failure=new ProgramLoader().loadFromResource("dsl/lost-update-declarative.json");
        var observed=new BenchmarkHarness(1_000_003,4,Set.of(StoreType.BITSTATE),Set.of("CONTEXT_BOUNDED"),2,true,true).runProgram(failure).getFirst();
        assertEquals("VIOLATION",observed.verdict());assertEquals(1,observed.preemptionsUsed());
        assertTrue(new SoundnessAttestation(List.of(observed),List.of(failure)).isSound());
        assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(0,4).build());
        assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(8,0).build());
        assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(8,4)
            .symmetryBackend(SymmetryBackend.ORDERED).build());
        assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(8,4)
            .stateStore(new dev.samhb.interleave.state.HashingStateStore()).build());
        assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(8,4)
            .stateStoreFactory(dev.samhb.interleave.state.HashingStateStore::new).build());
    }

    /** Selects owned approximation with a CBS bound only when that strategy accepts it.
     * @param strategy built-in strategy
     * @param bits one shared logical membership budget
     * @return builder awaiting property/resource options
     */
    private static InterleaveRunner.Builder approximate(Strategy strategy,int bits) {
        var builder=InterleaveRunner.builder().strategy(strategy).symmetryReduction(true).symmetryBitstate(bits,4);
        if(strategy==Strategy.CONTEXT_BOUNDED)builder.maxPreemptions(2);
        return builder;
    }

}
