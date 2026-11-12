/** Checked approximate selection, truthful results and per-run ownership regressions. */
package dev.samhb.interleave;

import com.google.gson.JsonParser;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.report.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApproximateSymmetryRunTest {
    /** Stable fixture has three reachable value orbits, independently checked by the store oracle. */
    private static final String SOURCE = "program pair { shared int x = 0; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } invariant final (x == 2); }";

    /** Both algorithms retain approximate metadata, resource evidence and independent concurrent runs.
     * @throws Exception if concurrent verification fails
     */
    @Test void runnersRetainPrecisionLimitsAndIsolation() throws Exception {
        var model = new SourceProgramLoader().load(SOURCE);
        for (var strategy : List.of(Strategy.DFS, Strategy.STATIC_POR)) {
            var runner = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true)
                .symmetryBitstate(1_000_003, 4).invariant(model.invariant().orElseThrow()).build();
            for (int i = 0; i < 2; i++) {
                var result = runner.run(model.program());
                assertEquals(3, result.statesExplored()); assertTrue(result.symmetryReduction());
                var json = JsonParser.parseString(result.toJson()).getAsJsonObject();
                assertEquals("BITSTATE", json.get("storeType").getAsString());
                assertFalse(result.hasViolation());
            }
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var ready = new java.util.concurrent.CountDownLatch(2);
                var start = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<TestResult> run = () -> { ready.countDown(); start.await(); return runner.run(model.program()); };
                var a = executor.submit(run);
                var b = executor.submit(run);
                try { assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)); }
                finally { start.countDown(); }
                assertEquals(3, a.get().statesExplored()); assertEquals(3, b.get().statesExplored());
            }
            var limited = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true)
                .symmetryBitstate(1_000_003,4).maxStates(1).build().run(model.program());
            assertTrue(limited.limitExceeded()); assertTrue(limited.symmetryReduction());
            var json = JsonParser.parseString(limited.toJson()).getAsJsonObject();
            assertEquals("BITSTATE", json.get("storeType").getAsString());
            assertEquals("INCOMPLETE",json.getAsJsonObject("resourcePrefix").get("outcome").getAsString());
            assertEquals(0,json.getAsJsonObject("resourcePrefix").getAsJsonArray("threads").size());
            assertEquals(0,json.getAsJsonObject("resourcePrefix").getAsJsonObject("terminalState").getAsJsonObject("fields").get("x").getAsInt());
            var later = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true)
                .symmetryBitstate(1_000_003,4).maxStates(2).build().run(model.program());
            assertTrue(later.limitExceeded());
            assertEquals(1,later.resourcePrefix().orElseThrow().threadIds().size());
            assertEquals(2,later.resourcePrefix().orElseThrow().replay(model.program(),null).states().size());
            if (strategy == Strategy.DFS) {
                java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
                java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-symmetry-resource-root.json"),limited.toJson());
                java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-symmetry-resource-later.json"),later.toJson());
            }
        }
    }

    /** Forced one-bit collisions may hide failures but cannot invent witnesses or exhaustive PASS. */
    @Test void saturationRemainsApproximateWithoutInventingWitnesses() {
        var model = new SourceProgramLoader().load(SOURCE.replace("x == 2","x == 3"));
        assertTrue(InterleaveRunner.builder().symmetryReduction(true).invariant(model.invariant().orElseThrow())
            .build().run(model.program()).hasViolation());
        for (var strategy : List.of(Strategy.DFS,Strategy.STATIC_POR)) {
            var result = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true).symmetryBitstate(1,4)
                .invariant(model.invariant().orElseThrow()).build().run(model.program());
            assertEquals(1,result.statesExplored()); assertFalse(result.hasViolation());
            assertTrue(result.completedTraces().isEmpty()); assertTrue(result.failingTraces().isEmpty());
            var rows = new BenchmarkHarness(1,4,Set.of(StoreType.BITSTATE),Set.of(strategy.name()),2,false,true).runProgram(model);
            assertEquals("APPROXIMATE_PASS",rows.getFirst().verdict());
        }
    }

    /** Explicit conflicts fail in either setter order and unsupported phases cannot be selected. */
    @Test void builderRejectsConflictsAndFutureStrategies() {
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryBitstate(0,4));
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryBitstate(10,0));
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryBitstate(10,4).build());
        for (boolean first : List.of(true,false)) {
            var b = InterleaveRunner.builder().symmetryReduction(true);
            if (first) b.symmetryBitstate(10,4);
            b.symmetryBackend(SymmetryBackend.HASHED);
            if (!first) b.symmetryBitstate(10,4);
            assertThrows(IllegalArgumentException.class,b::build);
            var c = InterleaveRunner.builder().symmetryReduction(true);
            if (first) c.symmetryBitstate(10,4);
            c.stateStoreFactory(HashingStateStore::new);
            if (!first) c.symmetryBitstate(10,4);
            assertThrows(IllegalArgumentException.class,c::build);
            var d = InterleaveRunner.builder().symmetryReduction(true);
            if (first) d.symmetryBitstate(10,4);
            d.stateStore(new HashingStateStore());
            if (!first) d.symmetryBitstate(10,4);
            assertThrows(IllegalArgumentException.class,d::build);
        }
        for (var strategy : List.of(Strategy.DPOR)) {
            assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryReduction(true)
                .symmetryBitstate(10,4).strategy(strategy).build());
        }
        assertDoesNotThrow(() -> InterleaveRunner.builder().symmetryBitstate(1,1).symmetryReduction(false)
            .symmetryReduction(true).symmetryBitstate(100,2).build());
        var model = new SourceProgramLoader().load(SOURCE);
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().stateStoreFactory(() ->
            BitstateSymmetryStateStore.forRun(model.program(),null,Strategy.DFS,17,4,0)).build().run(model.program()));
    }

    /** Harness singleton bitstate selections are explicit and approximate rows never prove absence. */
    @Test void harnessSupportsOnlyExplicitCurrentStrategies() {
        var model = new SourceProgramLoader().load(SOURCE);
        var rows = new BenchmarkHarness(1_000_003,4,Set.of(StoreType.BITSTATE),Set.of("DFS","STATIC_POR"),2,false,true).runProgram(model);
        assertEquals(2, rows.size());
        for (var row : rows) {
            assertEquals(StoreType.BITSTATE,row.storeType()); assertEquals("APPROXIMATE_PASS",row.verdict());
            assertEquals(3,row.statesExplored()); assertTrue(row.symmetryReduction());
            assertTrue(row.bitstateBitCount()>0); assertTrue(row.bitstateBitDensity()>0); assertTrue(row.estimatedFalsePositiveRate()>0);
        }
        for (var selection : List.of(Set.<String>of(),Set.of("DPOR"))) {
            assertThrows(IllegalArgumentException.class, () -> new BenchmarkHarness(100,4,Set.of(StoreType.BITSTATE),selection,2,false,true));
        }
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkHarness(100,4,Set.of(StoreType.BITSTATE),null,2,false,true));
        for (int[] settings : new int[][]{{0,4},{-1,4},{100,0},{100,-1}}) {
            assertThrows(IllegalArgumentException.class, () -> new BenchmarkHarness(settings[0],settings[1],Set.of(StoreType.BITSTATE),Set.of("DFS"),2,false,true));
        }
    }
}
