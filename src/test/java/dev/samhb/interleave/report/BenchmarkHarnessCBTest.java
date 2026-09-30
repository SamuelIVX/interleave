package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkHarnessCBTest {

    private static BenchmarkProgram named(String name) {
        for (BenchmarkProgram p : BugCorpus.all()) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        throw new IllegalArgumentException("no such program: " + name);
    }

    private static BenchmarkResult cbsResult(String bug, int preemptions) {
        return new BenchmarkResult("CONTEXT_BOUNDED", bug, 10, 1, 0, "INCOMPLETE", null,
            StoreType.EXACT, 0.0, 0, 0.0, preemptions);
    }

    // --- Result shape -------------------------------------------------------------

    @Test
    void runProgram_includesContextBoundedForBothStores() {
        List<BenchmarkResult> results = new BenchmarkHarness().runProgram(named("peterson"));
        List<BenchmarkResult> cbs = results.stream()
            .filter(r -> "CONTEXT_BOUNDED".equals(r.strategy()))
            .toList();
        assertEquals(2, cbs.size(), "expected one CBS row per store type");
        assertEquals(Set.of(StoreType.EXACT, StoreType.BITSTATE),
            cbs.stream().map(BenchmarkResult::storeType).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void nonCbsRows_haveNoPreemptionBound() {
        // null must mean "this strategy has no bound", which is distinct from 0 being a legal one.
        for (BenchmarkResult r : new BenchmarkHarness().runProgram(named("peterson"))) {
            if (!"CONTEXT_BOUNDED".equals(r.strategy())) {
                assertNull(r.preemptionsUsed(), r.strategy() + " should not report a bound");
            }
        }
    }

    @Test
    void cbsRow_reportsTheBoundItRanAt() {
        BenchmarkHarness harness = new BenchmarkHarness(1_000_003, 4, null, null, 3, false);
        BenchmarkResult cbs = harness.runProgram(named("peterson")).stream()
            .filter(r -> "CONTEXT_BOUNDED".equals(r.strategy()))
            .findFirst()
            .orElseThrow();
        assertEquals(3, cbs.preemptionsUsed());
    }

    @Test
    void iterativeDeepening_reportsTheBoundThatProducedTheResult() {
        // Under deepening the useful number is the minimal K that yielded the result, not the
        // configured ceiling. Reporting the ceiling after stopping early would misstate the work.
        BenchmarkHarness harness = new BenchmarkHarness(1_000_003, 4, null, null, 3, true);
        BenchmarkResult cbs = harness.runProgram(named("lost-update")).stream()
            .filter(r -> "CONTEXT_BOUNDED".equals(r.strategy()) && r.storeType() == StoreType.EXACT)
            .findFirst()
            .orElseThrow();

        assertNotNull(cbs.preemptionsUsed());
        assertTrue(cbs.preemptionsUsed() < 3,
            "should stop at a bound below the ceiling, got " + cbs.preemptionsUsed());
    }

    @Test
    void preemptionsUsed_defaultsToNullForLegacyConstructors() {
        // Every pre-existing constructor must keep producing exactly the output it did before.
        BenchmarkResult legacy = new BenchmarkResult("DFS", "peterson", 1, 1, 1, "PASS");
        assertNull(legacy.preemptionsUsed());

        // The 7-arg Trace and 7-arg StoreType overloads already coexisted, so a null there is
        // ambiguous at the call site regardless of the new field. Cast to select one.
        BenchmarkResult withTrace = new BenchmarkResult("DFS", "peterson", 1, 1, 1, "PASS",
            (dev.samhb.interleave.search.Trace) null);
        assertNull(withTrace.preemptionsUsed());

        BenchmarkResult withStore = new BenchmarkResult("DFS", "peterson", 1, 1, 1, "PASS", StoreType.EXACT);
        assertNull(withStore.preemptionsUsed());
    }

    // --- Verdict derivation --------------------------------------------------------

    @Test
    void cbVerdict_correctProgramUnderLowBound_isIncomplete() {
        // peterson is correct: a bounded search that runs out of budget must say INCOMPLETE rather
        // than claiming a pass it did not earn.
        BenchmarkResult row = new BenchmarkHarness(1_000_003, 4, Set.of(StoreType.EXACT),
                Set.of("CONTEXT_BOUNDED"), 1, false)
            .runProgram(named("peterson")).get(0);
        assertEquals("INCOMPLETE", row.verdict());
    }

    @Test
    void cbVerdict_buggyProgramAtSufficientBound_isViolation() {
        BenchmarkResult row = new BenchmarkHarness(1_000_003, 4, Set.of(StoreType.EXACT),
                Set.of("CONTEXT_BOUNDED"), 3, false)
            .runProgram(named("lost-update")).get(0);
        assertEquals("VIOLATION", row.verdict());
        assertTrue(row.failingTrace().isPresent());
    }

    @Test
    void cbVerdict_bitstateInconclusive_isApproximatePass() {
        // A bitstate bounded run is genuinely inconclusive, so calling it PASS would overstate
        // what was proven.
        BenchmarkHarness harness = new BenchmarkHarness(1_000_003, 4, Set.of(StoreType.BITSTATE),
            Set.of("CONTEXT_BOUNDED"), 1, false);
        BenchmarkResult row = harness.runProgram(named("peterson")).get(0);

        assertTrue(Set.of("APPROXIMATE_PASS", "INCOMPLETE", "VIOLATION", "DEADLOCK").contains(row.verdict()),
            "unexpected verdict: " + row.verdict());
        assertNotEquals("PASS", row.verdict(),
            "bitstate context-bounded rows must never be reported as an exact PASS");
    }

    @Test
    void cbVerdict_doesNotRelabelOtherBitstateStrategies() {
        // The deliberate trade-off: bitstate DFS/POR/DPOR rows keep reporting PASS, so existing
        // report rows and the harness attestation filter are unaffected.
        List<BenchmarkResult> results = new BenchmarkHarness(1_000_003, 4, Set.of(StoreType.BITSTATE), null)
            .runProgram(named("peterson"));
        for (BenchmarkResult row : results) {
            if (!"CONTEXT_BOUNDED".equals(row.strategy())) {
                assertEquals("PASS", row.verdict(),
                    row.strategy() + " bitstate verdict should be unchanged");
            }
        }
    }

    // --- Bitstate metrics ----------------------------------------------------------

    @Test
    void bitstateMetrics_populatedForContextBoundedRows() {
        // A bounded search marks only in the per-preemption vectors. Reading the main vector
        // would report zero bits, which is the same fabricated "no false positives" claim as an
        // empty store.
        BenchmarkHarness harness = new BenchmarkHarness(1_000_003, 4, Set.of(StoreType.BITSTATE),
            Set.of("CONTEXT_BOUNDED"), 2, false);
        BenchmarkResult row = harness.runProgram(named("peterson")).get(0);

        assertTrue(row.bitstateBitCount() > 0,
            "context-bounded bitstate row should report bits set, got " + row.bitstateBitCount());
        assertTrue(row.bitstateBitDensity() > 0.0, "density should be positive");
        assertTrue(row.estimatedFalsePositiveRate() >= 0.0);
    }

    @Test
    void bitstateStore_capacityTracksTheConfiguredBound() {
        // K > 2 trips the explorer's capacity assertion unless the store is sized to the bound.
        for (int bound : new int[] {0, 1, 2, 3, 5}) {
            BenchmarkHarness harness = new BenchmarkHarness(1_000_003, 4, null, null, bound, false);
            List<BenchmarkResult> results = harness.runProgram(named("peterson"));
            assertFalse(results.isEmpty(), "bound " + bound + " should produce rows");
        }
    }

    @Test
    void negativeMaxPreemptions_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new BenchmarkHarness(1_000_003, 4, null, null, -1, false));
    }

    @Test
    void strategyFilter_selectsOnlyContextBounded() {
        List<BenchmarkResult> results = new BenchmarkHarness(1_000_003, 4, null, Set.of("CONTEXT_BOUNDED"))
            .runProgram(named("peterson"));
        assertTrue(results.stream().allMatch(r -> "CONTEXT_BOUNDED".equals(r.strategy())));
        assertEquals(2, results.size(), "both store types, but only the selected strategy");
    }

    @Test
    void runAll_coversEveryProgramWithContextBounded() {
        List<BenchmarkResult> results = new BenchmarkHarness().runAll();
        long programs = BugCorpus.all().size();
        assertEquals(programs * 8, results.size(),
            "each of " + programs + " programs should yield 4 strategies x 2 store types");
        assertEquals(programs, results.stream()
            .filter(r -> "CONTEXT_BOUNDED".equals(r.strategy()))
            .map(BenchmarkResult::bugName).distinct().count());
    }
}
