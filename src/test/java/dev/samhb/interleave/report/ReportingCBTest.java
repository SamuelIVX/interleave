package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.search.TraceOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReportingCBTest {

    private static List<BenchmarkResult> corpusRows() {
        return new BenchmarkHarness().runAll();
    }

    // --- SoundnessAttestation ------------------------------------------------------

    @Test
    void soundnessAttestation_correctProgram_cbsIncomplete_doesNotFail() {
        // The real-world case, run through the actual harness. peterson is PASS under exhaustive
        // DFS and INCOMPLETE under a bounded search; both are EXACT, so the failure here is a
        // disagreement between two exact results rather than a check against expectedVerdict.
        // It only reproduces when real CBS verdicts are produced, which is why this does not use
        // hand-built rows.
        List<BenchmarkResult> results = new BenchmarkHarness(1_000_003, 4, null, null, 1, false)
            .runAll();
        SoundnessAttestation attestation = new SoundnessAttestation(results, BugCorpus.all());

        assertTrue(attestation.isSound(),
            "bounded INCOMPLETE must not be read as a disagreement: "
            + attestation.failureReason());
    }

    @Test
    void soundnessAttestation_fullCorpusWithAllStrategies_isSound() {
        SoundnessAttestation attestation = new SoundnessAttestation(corpusRows(), BugCorpus.all());
        assertTrue(attestation.isSound(), String.valueOf(attestation.failureReason()));
    }

    @Test
    void soundnessAttestation_incompleteResult_presentInCorpus() {
        // Guard for the test above: if CBS never reports INCOMPLETE for a correct program, the
        // attestation test is vacuous.
        boolean anyIncomplete = corpusRows().stream()
            .anyMatch(r -> "INCOMPLETE".equals(r.verdict()));
        assertTrue(anyIncomplete, "expected at least one INCOMPLETE row from the corpus");
    }

    @Test
    void soundnessAttestation_cbsViolation_replayValidated() {
        // The exclusion of INCOMPLETE/APPROXIMATE_PASS from verdict agreement must not extend to
        // the replay check: a bounded search reporting a violation must still be held to it.
        dev.samhb.interleave.bugs.BenchmarkProgram lostUpdate = BugCorpus.all().stream()
            .filter(p -> p.name().equals("lost-update")).findFirst().orElseThrow();

        BenchmarkResult genuine = new BenchmarkResult("CONTEXT_BOUNDED", "lost-update",
            10, 1, 0, "VIOLATION", null, StoreType.EXACT);
        // A violation row with no failing trace cannot be replayed, so it must be reported.
        SoundnessAttestation attestation = new SoundnessAttestation(
            List.of(genuine), BugCorpus.all());
        assertFalse(attestation.isSound(),
            "a VIOLATION row lacking a replayable trace must fail the attestation");
        assertTrue(attestation.failureReason().contains("lost-update"),
            String.valueOf(attestation.failureReason()));

        assertNotNull(lostUpdate.invariant().orElse(null));
    }

    @Test
    void soundnessAttestation_cbsViolation_withRealTrace_isSound() {
        // The positive counterpart: a real CBS violation whose trace replays cleanly passes.
        List<BenchmarkResult> cbs = new BenchmarkHarness(1_000_003, 4, Set.of(StoreType.EXACT),
                Set.of("CONTEXT_BOUNDED"), 3, false)
            .runAll();
        BenchmarkResult row = cbs.stream()
            .filter(r -> r.bugName().equals("lost-update"))
            .findFirst().orElseThrow();
        assertEquals("VIOLATION", row.verdict());
        assertTrue(row.failingTrace().isPresent());

        SoundnessAttestation attestation = new SoundnessAttestation(
            List.of(row), BugCorpus.all());
        assertTrue(attestation.isSound(), String.valueOf(attestation.failureReason()));
    }

    @Test
    void soundnessAttestation_cbsApproximatePass_excludedFromAgreement() {
        // A hand-built APPROXIMATE_PASS row must not conflict with another strategy's PASS.
        BenchmarkResult approximate = new BenchmarkResult("CONTEXT_BOUNDED", "peterson",
            10, 1, 0, "APPROXIMATE_PASS", null, StoreType.EXACT);
        BenchmarkResult exactPass = new BenchmarkResult("DFS", "peterson",
            10, 1, 0, "PASS", null, StoreType.EXACT);

        SoundnessAttestation attestation = new SoundnessAttestation(
            List.of(exactPass, approximate), BugCorpus.all());
        assertTrue(attestation.isSound(),
            "APPROXIMATE_PASS must not be read as disagreeing with PASS: "
            + attestation.failureReason());
    }

    @Test
    void soundnessAttestation_markdownAcknowledgesTheExclusion() {
        // A reader must not mistake the pass for coverage of every strategy.
        String markdown = new SoundnessAttestation(corpusRows(), BugCorpus.all()).formatMarkdown();
        assertTrue(markdown.contains("INCOMPLETE"), markdown);
    }

    // --- StatesExploredTable -------------------------------------------------------

    @Test
    void statesExploredTable_containsContextBoundedRow() {
        // Guards the hardcoded strategyOrder array. Dropping CONTEXT_BOUNDED there would
        // silently omit the strategy from the table, which is the kind of quiet gap that
        // survives review because the table still renders.
        String table = new StatesExploredTable(corpusRows()).formatReductionTable();
        assertTrue(table.contains("CONTEXT_BOUNDED (exact)"),
            "reduction table is missing context-bounded rows");
    }

    @Test
    void statesExploredTable_allPresentStrategiesAppear() {
        String table = new StatesExploredTable(corpusRows()).formatMarkdown();
        for (String strategy : List.of("DFS", "STATIC_POR", "DPOR", "CONTEXT_BOUNDED")) {
            assertTrue(table.contains("| " + strategy + " |"),
                "detailed table missing strategy " + strategy);
        }
    }

    @Test
    void statesExploredTable_cbsBeyondBaseline_rendersWithoutNegativeArrow() {
        // A bounded search is not guaranteed to explore fewer states than DFS, so a count above
        // the baseline must print bare rather than as "-23%↓", which is both a negative
        // percentage and a downward arrow on a number that went up.
        BenchmarkResult cbs = new BenchmarkResult("CONTEXT_BOUNDED", "synthetic", 200, 1, 0,
            "INCOMPLETE", null, StoreType.EXACT);
        BenchmarkResult dfs = new BenchmarkResult("DFS", "synthetic", 100, 1, 0, "PASS", null,
            StoreType.EXACT);

        String table = new StatesExploredTable(List.of(cbs, dfs)).formatReductionTable();
        assertTrue(table.contains("CONTEXT_BOUNDED (exact): 200 states\n"),
            "expected a bare count with no percentage: " + table);
        assertFalse(table.contains("-50%"), table);
        assertFalse(table.contains("200 states ("), table);
    }

    @Test
    void statesExploredTable_genuineReduction_stillRendersPercentage() {
        // Guards against the negative case above being "fixed" by dropping percentages entirely.
        BenchmarkResult cbs = new BenchmarkResult("CONTEXT_BOUNDED", "synthetic", 50, 1, 0,
            "INCOMPLETE", null, StoreType.EXACT);
        BenchmarkResult dfs = new BenchmarkResult("DFS", "synthetic", 100, 1, 0, "PASS", null,
            StoreType.EXACT);

        String table = new StatesExploredTable(List.of(cbs, dfs)).formatReductionTable();
        assertTrue(table.contains("50 states (50%↓)"), table);
    }

    // --- ReportWriter --------------------------------------------------------------

    @Test
    void writeJson_incomplete_verdictRoundTrips() {
        String json = new ReportWriter(corpusRows()).writeJson();
        assertTrue(json.contains("\"verdict\": \"INCOMPLETE\""), "INCOMPLETE verdict missing from JSON");
    }

    @Test
    void writeJson_approximatePass_verdictRoundTrips() {
        List<BenchmarkResult> rows = new BenchmarkHarness(1_000_003, 4,
            Set.of(StoreType.BITSTATE), Set.of("CONTEXT_BOUNDED"), 1, false).runAll();
        String json = new ReportWriter(rows).writeJson();
        assertTrue(json.contains("APPROXIMATE_PASS") || json.contains("INCOMPLETE"),
            "expected a bounded verdict on bitstate rows");
    }

    @Test
    void writeJson_preemptionsUsed_presentForEveryRow() {
        String json = new ReportWriter(corpusRows()).writeJson();
        long rows = corpusRows().size();
        long emitted = json.lines().filter(l -> l.contains("\"preemptionsUsed\"")).count();
        assertEquals(rows, emitted,
            "every row should carry a preemptionsUsed member, even when null");
    }

    @Test
    void writeJson_preemptionsUsed_nullForNonCbsAndBoundForCbs() {
        String json = new ReportWriter(corpusRows()).writeJson();
        assertTrue(json.contains("\"preemptionsUsed\": null"),
            "non-context-bounded rows should report null");
        assertTrue(json.contains("\"preemptionsUsed\": 2"),
            "context-bounded rows at the default bound should report 2");
    }

    @Test
    void writeMarkdown_incomplete_verdictColumn() {
        String markdown = new ReportWriter(corpusRows()).writeMarkdown();
        assertTrue(markdown.contains("| INCOMPLETE |"),
            "detailed table should render INCOMPLETE as a verdict");
    }

    @Test
    void writeMarkdown_incompleteOutcomeNeverReachesFailingColumn() {
        // A bounded search that found nothing must not appear as a failure anywhere in the report.
        String markdown = new ReportWriter(corpusRows()).writeMarkdown();
        for (String line : markdown.lines().toList()) {
            if (line.contains("INCOMPLETE")) {
                assertFalse(line.contains("| VIOLATION |"),
                    "INCOMPLETE row must not be rendered as a violation: " + line);
            }
        }
    }

    @Test
    void writeJson_shapeIndependentOfVerdict() {
        // A report with and one without context-bounded rows must share a key set, so a consumer
        // can read preemptionsUsed without checking whether CBS ran.
        String without = new ReportWriter(new BenchmarkHarness(1_000_003, 4, null,
            Set.of("DFS")).runAll()).writeJson();
        String with = new ReportWriter(corpusRows()).writeJson();
        assertEquals(keySet(without), keySet(with));
    }

    private static Set<String> keySet(String json) {
        return json.lines()
            .map(String::trim)
            .filter(l -> l.startsWith("\""))
            .map(l -> l.substring(1, l.indexOf('"', 1)))
            .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void traceOutcome_incomplete_isNotAFailingOutcome() {
        // The enum-level statement behind all of the above.
        assertNotEquals(TraceOutcome.VIOLATION, TraceOutcome.INCOMPLETE);
        assertNotEquals(TraceOutcome.DEADLOCK, TraceOutcome.INCOMPLETE);
        assertNotEquals(TraceOutcome.COMPLETED, TraceOutcome.INCOMPLETE);
    }
}
