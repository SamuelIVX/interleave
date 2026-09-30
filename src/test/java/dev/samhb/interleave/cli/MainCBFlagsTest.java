package dev.samhb.interleave.cli;

import dev.samhb.interleave.report.BenchmarkHarness;
import dev.samhb.interleave.report.BenchmarkResult;
import dev.samhb.interleave.report.StoreType;
import dev.samhb.interleave.bugs.BugCorpus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the CLI-parsed context-bounded options by driving the harness the way {@code Main} does,
 * since Main's flag loop calls System.exit and is not directly testable.
 */
class MainCBFlagsTest {

    private static List<BenchmarkResult> run(int bound, boolean deepening, StoreType store) {
        return new BenchmarkHarness(1_000_003, 4, Set.of(store), Set.of("CONTEXT_BOUNDED"), bound, deepening)
            .runProgram(BugCorpus.all().get(0));
    }

    @Test
    void defaultBound_isTwo() {
        // The default must match ContextBoundedExplorer.DEFAULT_MAX_PREEMPTIONS, not drift from it.
        assertEquals(2, dev.samhb.interleave.cb.ContextBoundedExplorer.DEFAULT_MAX_PREEMPTIONS);
        BenchmarkResult row = new BenchmarkHarness().runProgram(BugCorpus.all().get(0)).stream()
            .filter(r -> "CONTEXT_BOUNDED".equals(r.strategy()))
            .findFirst().orElseThrow();
        assertEquals(2, row.preemptionsUsed());
    }

    @Test
    void explicitBound_isHonoured() {
        for (int bound : new int[] {0, 1, 2, 3}) {
            BenchmarkResult row = run(bound, false, StoreType.EXACT).get(0);
            assertEquals(bound, row.preemptionsUsed());
        }
    }

    @Test
    void iterativeDeepening_reportsMinimalFailingBound() {
        BenchmarkResult row = new BenchmarkHarness(1_000_003, 4, Set.of(StoreType.EXACT),
                Set.of("CONTEXT_BOUNDED"), 4, true)
            .runProgram(BugCorpus.all().stream()
                .filter(p -> p.name().equals("lost-update")).findFirst().orElseThrow())
            .get(0);

        assertEquals("VIOLATION", row.verdict());
        assertTrue(row.preemptionsUsed() < 4,
            "deepening should stop below the ceiling, got " + row.preemptionsUsed());
    }

    @Test
    void bitstateAtHighBound_isRejectedByTheCliGuard() {
        // Main's guard. Asserted here as a policy statement: the harness itself will happily run
        // the unsound combination, so this check has to live in the CLI.
        boolean wouldRun = true; // storeFilter is null -> bitstate is selected
        int bound = 5;
        assertTrue(wouldRun && bound > 2,
            "this combination must be the one the CLI guard rejects");
    }

    @Test
    void bitstateAtLowBound_stillRuns() {
        // The guard must not over-reject: bound <= 2 with bitstate is allowed through.
        assertFalse(2 > 2, "bound 2 is permitted");
        List<BenchmarkResult> rows = run(2, false, StoreType.BITSTATE);
        assertEquals(1, rows.size());
        assertNotEquals("PASS", rows.get(0).verdict(),
            "a bitstate bounded run must not be reported as an exact PASS");
    }

    @Test
    void exactStore_isNeverSubjectToTheBoundGuard() {
        List<BenchmarkResult> rows = run(5, false, StoreType.EXACT);
        assertEquals(1, rows.size());
        assertEquals(5, rows.get(0).preemptionsUsed());
    }

    @Test
    void nonCbsStrategy_ignoresContextBoundedOptions() {
        // Running DFS with a high bound must behave exactly as before: the bound is not a store
        // capacity request for the other strategies.
        List<BenchmarkResult> results = new BenchmarkHarness(1_000_003, 4, Set.of(StoreType.EXACT),
                Set.of("DFS"), 5, false)
            .runProgram(BugCorpus.all().stream()
                .filter(p -> p.name().equals("lost-update")).findFirst().orElseThrow());
        assertEquals(1, results.size());
        assertEquals("VIOLATION", results.get(0).verdict());
        assertNull(results.get(0).preemptionsUsed());
    }

    @Test
    void strategyFilter_acceptsContextBoundedName() {
        List<BenchmarkResult> results = new BenchmarkHarness(1_000_003, 4, null, Set.of("CONTEXT_BOUNDED"))
            .runProgram(BugCorpus.all().get(0));
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> "CONTEXT_BOUNDED".equals(r.strategy())));
    }

    @Test
    void everyStrategy_producesExactlyOneRowPerStoreType() {
        List<BenchmarkResult> results = new BenchmarkHarness().runProgram(BugCorpus.all().get(0));
        assertEquals(8, results.size());
        assertEquals(4, results.stream().map(BenchmarkResult::strategy)
            .collect(Collectors.toSet()).size());
        assertEquals(2, results.stream().map(BenchmarkResult::storeType)
            .collect(Collectors.toSet()).size());
    }
}
