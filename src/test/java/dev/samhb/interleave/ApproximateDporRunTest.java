/** Public DPOR admission, all-strategy reporting, collisions and actual resource metadata. */
package dev.samhb.interleave;

import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.report.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApproximateDporRunTest {
    /** Shared dependent increments keep real owner choices and use no sleeping transitions. */
    private static final String SOURCE = "program pair { shared int x = 0; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } invariant final (x == 3); }";

    /** A saturated product filter may hide failures; actual source/root/later stop evidence survives.
     * @throws java.io.IOException if writing the real browser artifact fails
     */
    @Test void dporRunnerKeepsApproximationAndActualPartialPaths() throws java.io.IOException {
        var model = new SourceProgramLoader().load(SOURCE);
        var runner = InterleaveRunner.builder().strategy(Strategy.DPOR).symmetryReduction(true).symmetryBitstate(1,4)
            .invariant(model.invariant().orElseThrow()).build();
        var saturated = runner.run(model.program());
        assertEquals(1,saturated.statesExplored());assertFalse(saturated.hasViolation());
        assertTrue(saturated.completedTraces().isEmpty());assertTrue(saturated.symmetryReduction());
        var json = com.google.gson.JsonParser.parseString(saturated.toJson()).getAsJsonObject();
        assertEquals("BITSTATE",json.get("storeType").getAsString());
        var later = InterleaveRunner.builder().strategy(Strategy.DPOR).symmetryReduction(true).symmetryBitstate(1_000_003,4)
            .maxStates(2).build().run(model.program());
        assertTrue(later.limitExceeded());assertTrue(later.symmetryReduction());
        var prefix = later.resourcePrefix().orElseThrow();
        assertEquals(List.of(0),prefix.threadIds());assertEquals(List.of(1,0),prefix.programCounters());
        assertEquals(1,prefix.terminalState().fields().get("x"));
        assertEquals(2,prefix.replay(model.program(),null).states().size());
        var root = InterleaveRunner.builder().strategy(Strategy.DPOR).symmetryReduction(true).symmetryBitstate(1_000_003,4)
            .maxTime(java.time.Duration.ZERO).build().run(model.program(),() -> 0);
        assertTrue(root.limitExceeded());assertEquals(List.of(),root.resourcePrefix().orElseThrow().threadIds());
        assertEquals(dev.samhb.interleave.core.ResourceLimitReason.MAX_TIME,root.resourceLimitReason());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-dpor-resource.json"),later.toJson());
    }

    /** Separately produced approximate rows are excluded from exact verdict agreement. */
    @Test void allStrategyHarnessKeepsApproximateRowsOutOfExactAgreement() {
        var model = new SourceProgramLoader().load(SOURCE);
        var approximate = new BenchmarkHarness(1,4,Set.of(StoreType.BITSTATE),null,2,false,true).runProgram(model);
        assertEquals(Set.of("DFS","STATIC_POR","DPOR","CONTEXT_BOUNDED"),
            approximate.stream().map(BenchmarkResult::strategy).collect(java.util.stream.Collectors.toSet()));
        for (var row : approximate) {
            assertEquals(StoreType.BITSTATE,row.storeType());assertTrue(row.symmetryReduction());
            assertEquals("APPROXIMATE_PASS",row.verdict());
        }
        var exact = new BenchmarkHarness(1,4,Set.of(StoreType.EXACT),null,2,false,true).runProgram(model);
        for (var row : exact) assertEquals("VIOLATION",row.verdict());
        var together = new ArrayList<>(exact);together.addAll(approximate);
        var report = com.google.gson.JsonParser.parseString(new ReportWriter(together,List.of(model)).writeJson()).getAsJsonObject();
        assertTrue(report.get("soundness").getAsBoolean());assertEquals(8,report.getAsJsonArray("benchmarks").size());
    }
}
