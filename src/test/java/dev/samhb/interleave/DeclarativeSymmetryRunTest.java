/** Complete exact declarative strategy support, context routing and result identity. */
package dev.samhb.interleave;

import com.google.gson.JsonParser;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.report.*;
import dev.samhb.interleave.search.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the actual checked store/runner/report seams selected by Spec 92. */
class DeclarativeSymmetryRunTest {
    /** Checked DPOR must register the complete root product, even for non-source models. */
    @Test void checkedDeclarativeDporUsesProductCoverage() {
        var model = semaphore();
        var store = SymmetryStateStore.forRun(model.program(), null, Strategy.DPOR);
        new DporExplorer().explore(model.program(), null, store, null);
        assertTrue(store.sleepCovered(model.program().initialConfiguration(), Set.of()));
    }

    /** Exact strategy/backends keep JSON non-source and expose known storage precision. */
    @Test void exactStrategiesRetainIdentityAndResourceClassification() {
        var model = semaphore();
        for (var strategy : Strategy.values()) for (var backend : SymmetryBackend.values()) {
            var runner = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true)
                .symmetryBackend(backend).invariant(model.invariant().orElseThrow()).build();
            var result = runner.run(model.program());
            assertTrue(result.symmetryReduction());
            assertFalse(result.hasViolation());
            var json = JsonParser.parseString(result.toJson()).getAsJsonObject();
            assertEquals("EXACT", json.get("storeType").getAsString());
            assertFalse(json.has("programKind"));
            assertFalse(json.has("sourceDigest"));
            var limited = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true)
                .symmetryBackend(backend).maxStates(1).build().run(model.program());
            assertTrue(limited.limitExceeded());
            assertTrue(limited.resourcePrefix().isEmpty());
            var root = JsonParser.parseString(limited.toJson()).getAsJsonObject();
            assertEquals("EXACT", root.get("storeType").getAsString());
            assertEquals("MAX_STATES", root.get("resourceLimitReason").getAsString());
        }
    }

    /** Runner reuse and simultaneous callers own independent exact membership and limits.
     * @throws Exception if concurrent verification or artifact writes fail
     */
    @Test void exactRunsRemainIsolatedAndEmitResourceFixtures() throws Exception {
        var model=semaphore();
        java.nio.file.Files.createDirectories(Path.of("build"));
        for(var strategy:Strategy.values()) {
            var runner=InterleaveRunner.builder().strategy(strategy).symmetryReduction(true).build();
            long expected=runner.run(model.program()).statesExplored();
            assertEquals(expected,runner.run(model.program()).statesExplored());
            try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var ready=new java.util.concurrent.CountDownLatch(2);
                var start=new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<TestResult> run=()->{ready.countDown();start.await();return runner.run(model.program());};
                var a=executor.submit(run);var b=executor.submit(run);
                try {assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));} finally {start.countDown();}
                assertEquals(expected,a.get().statesExplored());assertEquals(expected,b.get().statesExplored());
            }
            var deadline=InterleaveRunner.builder().strategy(strategy).symmetryReduction(true)
                .maxTime(java.time.Duration.ZERO).build().run(model.program(),()->0L);
            assertTrue(deadline.limitExceeded());assertEquals(1,deadline.statesExplored());
            assertEquals("MAX_TIME",JsonParser.parseString(deadline.toJson()).getAsJsonObject().get("resourceLimitReason").getAsString());
            for(int limit:List.of(1,2)) {
                var partial=InterleaveRunner.builder().strategy(strategy).symmetryReduction(true).maxStates(limit).build().run(model.program());
                assertTrue(partial.limitExceeded());assertTrue(partial.resourcePrefix().isEmpty());
                assertEquals(limit,partial.statesExplored());
                if(strategy==Strategy.DFS)java.nio.file.Files.writeString(Path.of("build/declarative-symmetry-resource-"+limit+".json"),partial.toJson());
            }
            if(strategy==Strategy.DFS)java.nio.file.Files.writeString(Path.of("build/declarative-symmetry-completed.json"),runner.run(model.program()).toJson());
        }
    }

    /** Iterative bounded search retains a real failure and checked stores cannot cross bindings. */
    @Test void boundedContextsAndStoreCopiesStayOwned() {
        var model=new ProgramLoader().loadFromResource("dsl/lost-update-declarative.json");
        for(var backend:SymmetryBackend.values()) {
            var result=InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).symmetryReduction(true)
                .symmetryBackend(backend).maxPreemptions(2).iterativeDeepening(true)
                .invariant(model.invariant().orElseThrow()).build().run(model.program());
            assertTrue(result.hasViolation());
            var rows=new BenchmarkHarness(1024,4,Set.of(StoreType.EXACT),Set.of("CONTEXT_BOUNDED"),2,true,true).runProgram(model);
            assertTrue(new SoundnessAttestation(rows,List.of(model)).isSound());
            assertEquals("VIOLATION",rows.getFirst().verdict());
            assertEquals(1,rows.getFirst().preemptionsUsed());
            var store=SymmetryStateStore.forRun(model.program(),model.invariant().orElseThrow(),Strategy.DFS,backend);
            var root=model.program().initialConfiguration();store.markVisited(root);
            var copy=store.freshCopy();assertFalse(copy.isVisited(root));
            assertTrue(store.isVisited(root));store.clear();assertFalse(store.isVisited(root));
            var other=new ProgramLoader().loadFromResource("dsl/lost-update-declarative.json");
            assertThrows(IllegalArgumentException.class,()->new DfsExplorer().explore(other.program(),other.invariant().orElseThrow(),store,null));
            assertThrows(IllegalArgumentException.class,()->new DporExplorer().explore(model.program(),model.invariant().orElseThrow(),store,null));
            assertThrows(IllegalArgumentException.class,()->new DfsExplorer().explore(model.program(),null,store,null));
        }
    }

    /** Source interruptions retain their existing strict snapshots rather than using JSON capture. */
    @Test void sourcePartialFailureSnapshotsRetainSourceCapture() {
        var model=new dev.samhb.interleave.format.SourceProgramLoader().load("""
            program sourcepartial {
              shared int x = 0;
              thread 0 { assert(x == 1); }
              thread 1 { assert(x == 1); }
              thread 2 { atomic { x = 1; } }
            }
            """);
        var result=InterleaveRunner.builder().symmetryReduction(true).maxStates(2).build().run(model.program());
        assertTrue(result.limitExceeded());assertTrue(result.hasViolation());
        assertFalse(result.failingTraces().isEmpty());
        for(var trace:result.failingTraces()) {
            assertEquals(2,trace.states().size());
            assertEquals(0,trace.states().getLast().fields().get("x"));
            assertNotNull(trace.evidence().sourceDigest());
        }
    }

    /** Approximation shares exact certification; custom properties cannot gain trust in any strategy. */
    @Test void approximateDeclarativeSelectionRetainsCertificationGuards() {
        var model=semaphore();
        dev.samhb.interleave.search.Invariant untrusted=(s,c)->true;
        for(var strategy:Strategy.values()) {
            assertThrows(IllegalArgumentException.class,()->BitstateSymmetryStateStore.forRun(
                model.program(),untrusted,strategy,1024,4,strategy==Strategy.CONTEXT_BOUNDED?2:0));
            assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().strategy(strategy)
                .symmetryReduction(true).symmetryBitstate(1024,4).invariant(untrusted).build().run(model.program()));
            var invalid=new dev.samhb.interleave.bugs.BenchmarkProgram(model.name(),model.program(),null,untrusted);
            assertThrows(IllegalArgumentException.class,()->new BenchmarkHarness(1024,4,
                Set.of(StoreType.BITSTATE),Set.of(strategy.name()),2,false,true).runProgram(invalid));
        }
    }

    /** Loads the existing bounded semaphore without changing its semantics to fit symmetry.
     * @return trusted declarative fixture
     */
    private static BenchmarkProgram semaphore() {
        return new ProgramLoader().loadFromFile(Path.of("examples/programs/semaphore-declarative.json"));
    }
}
