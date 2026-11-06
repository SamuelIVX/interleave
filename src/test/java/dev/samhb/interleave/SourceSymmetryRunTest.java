/* Library/harness symmetry selection, isolation, limits, and explicit report identity. */
package dev.samhb.interleave;

import com.google.gson.JsonParser;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.ResourceLimitReason;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.report.*;
import dev.samhb.interleave.state.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceSymmetryRunTest {
    /** A reusable immutable runner owns fresh orbit membership and preserves result metadata.
     * @throws Exception if concurrent verification fails
     */
    @Test void runnerRetainsConcreteTracesAndConcurrentIsolation() throws Exception {
        var model = model();
        var runner = InterleaveRunner.builder().symmetryReduction(true).invariant(model.invariant().orElseThrow()).build();
        var first = runner.run(model.program());
        assertEquals(3, first.statesExplored());
        assertFalse(first.hasViolation());
        assertTrue(first.symmetryReduction());
        assertEquals(3, runner.run(model.program()).statesExplored());
        var json = JsonParser.parseString(first.toJson()).getAsJsonObject();
        assertTrue(json.get("symmetryReduction").getAsBoolean());
        assertEquals("DFS", json.get("strategy").getAsString());
        assertEquals("EXACT", json.get("storeType").getAsString());
        assertTrue(first.withRunMetadata(true, null).symmetryReduction());
        assertTrue(first.withRunMetadata(true, null, false).symmetryReduction());
        assertEquals(List.of(0, 1), first.completedTraces().getFirst().threadIds());
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var left = executor.submit(() -> runner.run(model.program()));
            var right = executor.submit(() -> runner.run(model.program()));
            assertEquals(3, left.get().statesExplored());
            assertEquals(3, right.get().statesExplored());
        }
        var ordinary = InterleaveRunner.builder().invariant(model.invariant().orElseThrow()).build().run(model.program());
        assertEquals(4, ordinary.statesExplored());
        assertFalse(ordinary.symmetryReduction());
        assertFalse(JsonParser.parseString(ordinary.toJson()).getAsJsonObject().has("symmetryReduction"));
    }

    /** Representative budgets and time limits retain explicit partial-result classification. */
    @Test void limitsKeepSymmetryIdentityAndIncompleteVerdict() {
        var model = model();
        var limited = InterleaveRunner.builder().symmetryReduction(true).maxStates(2).build().run(model.program());
        assertEquals(2, limited.statesExplored());
        assertTrue(limited.limitExceeded());
        assertTrue(limited.symmetryReduction());
        assertEquals(ResourceLimitReason.MAX_STATES, limited.resourceLimitReason());
        var time = InterleaveRunner.builder().symmetryReduction(true).maxTime(Duration.ZERO).build().run(model.program(), () -> 0);
        assertEquals(1, time.statesExplored());
        assertEquals(ResourceLimitReason.MAX_TIME, time.resourceLimitReason());
        var json = JsonParser.parseString(time.toJson()).getAsJsonObject();
        assertTrue(json.get("symmetryReduction").getAsBoolean());
        assertTrue(json.get("limitExceeded").getAsBoolean());
    }

    /** Unsupported compositions reject rather than ignoring caller strategy/store contracts. */
    @Test void rejectsUnsupportedOptionsAndCustomProperties() {
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryReduction(true).strategy(null).build());
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryReduction(true).strategy(Strategy.DPOR)
            .stateStoreFactory(HashingStateStore::new).build());
        for (Strategy strategy : Strategy.values()) {
            assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryReduction(true)
                .strategy(strategy).stateStore(new HashingStateStore()).build());
        }
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryReduction(true).stateStore(new BitstateStore(100, 2)).build());
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryReduction(true).stateStoreFactory(HashingStateStore::new).build());
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder().symmetryReduction(true).invariant((s,c) -> true).build().run(model().program()));
        assertDoesNotThrow(() -> InterleaveRunner.builder().symmetryReduction(true).symmetryReduction(false).strategy(Strategy.DPOR).build());
        assertThrows(IllegalArgumentException.class, () -> harness(null, Set.of("DFS")));
        assertDoesNotThrow(() -> harness(Set.of(StoreType.EXACT), null));
        assertThrows(IllegalArgumentException.class, () -> harness(Set.of(StoreType.BITSTATE), Set.of("DFS")));
        assertThrows(IllegalArgumentException.class, () -> harness(Set.of(StoreType.EXACT), Set.of("UNKNOWN")));
        assertThrows(IllegalArgumentException.class, () -> harness(Set.of(StoreType.EXACT), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkHarness(100,2,Set.of(StoreType.EXACT),Set.of("DFS"),2,true,true));
    }

    /** Machine strategy/store identity stays stable; mixed Markdown rows never overwrite ordinary DFS. */
    @Test void harnessAndReportsDistinguishOrbitRepresentatives() {
        var model = model();
        var stores = new HashSet<>(Set.of(StoreType.EXACT));
        var strategies = new HashSet<>(Set.of("DFS"));
        var checked = harness(stores, strategies);
        stores.clear(); stores.add(StoreType.BITSTATE);
        strategies.clear(); strategies.add("DPOR");
        var checkedRows = checked.runProgram(model);
        assertEquals(1, checkedRows.size());
        var reduced = checkedRows.getFirst();
        assertEquals("DFS", reduced.strategy());
        assertEquals(StoreType.EXACT, reduced.storeType());
        var ordinary = new BenchmarkHarness(100,2,Set.of(StoreType.EXACT),Set.of("DFS")).runProgram(model).getFirst();
        assertEquals(3, reduced.statesExplored());
        assertEquals("PASS", reduced.verdict());
        assertTrue(reduced.symmetryReduction());
        assertFalse(ordinary.symmetryReduction());
        var writer = new ReportWriter(List.of(ordinary, reduced), List.of(model));
        var json = JsonParser.parseString(writer.writeJson()).getAsJsonObject();
        assertTrue(json.get("soundness").getAsBoolean());
        var rows = json.getAsJsonArray("benchmarks");
        assertFalse(rows.get(0).getAsJsonObject().has("symmetryReduction"));
        assertTrue(rows.get(1).getAsJsonObject().get("symmetryReduction").getAsBoolean());
        assertEquals("DFS", rows.get(1).getAsJsonObject().get("strategy").getAsString());
        String markdown = writer.writeMarkdown();
        assertTrue(markdown.contains("representative visits"));
        assertTrue(markdown.contains("DFS (exact): 4 states"));
        assertTrue(markdown.contains("DFS + symmetry (exact): 3 states"));
        assertTrue(markdown.contains("| DFS + symmetry | EXACT | 3 |"));
    }

    /** Returns the opt-in filtered harness, including unsupported combinations for validation tests.
     * @param stores explicit stores, or null
     * @param strategies explicit strategies, or null
     * @return configured checked source-symmetry harness
     */
    private static BenchmarkHarness harness(Set<StoreType> stores, Set<String> strategies) {
        return new BenchmarkHarness(100, 2, stores, strategies, 2, false, true);
    }

    /** Two one-increment owners have four concrete masks and three count-based orbits.
     * @return immutable compiled source and its final shared-state property
     */
    private static BenchmarkProgram model() {
        return new SourceProgramLoader().load("program two { shared int x = 0; thread 0 { atomic { x = x + 1; } }"
            + "thread 1 { atomic { x = x + 1; } } invariant final (x == 2); }");
    }
}
