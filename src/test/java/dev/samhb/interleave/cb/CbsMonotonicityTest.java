package dev.samhb.interleave.cb;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Monotonicity in K, across the whole corpus and the whole meaningful bound range.
 *
 * <p>Non-monotonicity is the signature of a broken minimum-budget index: the mechanism where a
 * state reached cheaply prunes the same state reached expensively. That is precisely how a pruner
 * loses bugs -- not by exploring less than advertised, but by discarding a state whose only cheap
 * path had not yet been fully extended.
 *
 * <p>The pre-existing {@code monotonic_statesExploredGrowsWithBound} covers three names over
 * K=0..4 on the states-explored count alone. This widens it to all seven corpus programs, to the
 * full bound range up to a provably non-binding K, and to the two verdict properties, which is
 * where a lost bug would actually be visible.
 *
 * <p><strong>Framing that matters:</strong> if a corpus program violates any of these, that is a
 * <em>finding to investigate</em>, not a bad expectation to relax. Adjusting an assertion here to
 * make a failure green would destroy the only signal this test can produce.
 */
class CbsMonotonicityTest {

    private static int nonBindingBound(Program program) {
        return program.threads().stream().mapToInt(t -> t.steps().size()).sum();
    }

    private static DfsResult run(BenchmarkProgram p, int k) {
        return new ContextBoundedExplorer()
            .explore(p.program(), p.invariant().orElse(null), null, null, k);
    }

    private static List<Trace> tracesWithOutcome(DfsResult r, TraceOutcome outcome) {
        return r.traces().stream().filter(t -> t.outcome() == outcome).toList();
    }

    private static boolean hasViolation(DfsResult r) {
        return !tracesWithOutcome(r, TraceOutcome.VIOLATION).isEmpty();
    }

    private static boolean hasDeadlock(DfsResult r) {
        return !tracesWithOutcome(r, TraceOutcome.DEADLOCK).isEmpty();
    }

    private static boolean isIncomplete(DfsResult r) {
        return !tracesWithOutcome(r, TraceOutcome.INCOMPLETE).isEmpty();
    }

    // --- Property 1: states explored is non-decreasing in K ----------------------------

    @Test
    void statesExplored_isNonDecreasingInBound() {
        for (BenchmarkProgram p : BugCorpus.all()) {
            long previous = 0;
            for (int k = 0; k <= nonBindingBound(p.program()); k++) {
                long states = run(p, k).statesExplored();
                assertTrue(states >= previous,
                    p.name() + ": states explored decreased at K=" + k
                        + " (" + previous + " -> " + states + "). A deeper bound pruned a state a "
                        + "shallower one explored, which means the minimum-budget index is inverted.");
                previous = states;
            }
        }
    }

    @Test
    void statesExplored_atNonBindingBound_exceedsEverySmallerBound() {
        // Strictness check, so property 1 cannot be satisfied by a constant.
        for (BenchmarkProgram p : BugCorpus.all()) {
            long atZero = run(p, 0).statesExplored();
            long atFull = run(p, nonBindingBound(p.program())).statesExplored();
            assertTrue(atFull > atZero,
                p.name() + ": a non-binding bound should reach strictly further than K=0 ("
                    + atZero + " -> " + atFull + ")");
        }
    }

    // --- Property 2: a violation found at K is still found at K+1 ---------------------

    @Test
    void violation_atBound_impliesViolationAtEveryHigherBound() {
        for (BenchmarkProgram p : BugCorpus.all()) {
            boolean sawViolation = false;
            for (int k = 0; k <= nonBindingBound(p.program()); k++) {
                boolean atK = hasViolation(run(p, k));
                if (sawViolation) {
                    assertTrue(atK,
                        p.name() + ": a violation found at a lower bound disappeared at K=" + k
                            + ". Raising the bound cannot un-find a reachable violation; this is a "
                            + "pruner discarding evidence.");
                }
                sawViolation = atK;
            }
        }
    }

    @Test
    void deadlock_atBound_impliesDeadlockAtEveryHigherBound() {
        // Same argument as the violation property, for the other terminal outcome. Kept separate
        // because a pruner that loses deadlocks and one that loses violations are different bugs.
        for (BenchmarkProgram p : BugCorpus.all()) {
            boolean sawDeadlock = false;
            for (int k = 0; k <= nonBindingBound(p.program()); k++) {
                boolean atK = hasDeadlock(run(p, k));
                if (sawDeadlock) {
                    assertTrue(atK, p.name() + ": a deadlock found at a lower bound disappeared at K=" + k);
                }
                sawDeadlock = atK;
            }
        }
    }

    // --- Property 3: a genuine PASS at K stays a PASS ---------------------------------

    @Test
    void pass_atBound_impliesPassAtEveryHigherBound() {
        // "PASS" here means a completed search with nothing pruned, which is the only kind of pass
        // that carries information. An INCOMPLETE run is not a pass and is excluded explicitly.
        for (BenchmarkProgram p : BugCorpus.all()) {
            boolean sawCleanPass = false;
            for (int k = 0; k <= nonBindingBound(p.program()); k++) {
                DfsResult result = run(p, k);
                boolean complete = !isIncomplete(result) && !hasViolation(result) && !hasDeadlock(result)
                    && !tracesWithOutcome(result, TraceOutcome.COMPLETED).isEmpty();
                if (sawCleanPass) {
                    assertTrue(complete,
                        p.name() + ": a complete pass at a lower bound became inconclusive at K="
                            + k + ". Widening the search cannot invalidate a proof.");
                }
                sawCleanPass = complete;
            }
        }
    }

    // --- Coverage of the property: the corpus must actually exercise the bands --------

    @Test
    void corpus_coversBothTheIncompleteBandAndTheExhaustiveBand() {
        // Otherwise properties 2 and 3 above could pass vacuously over a corpus that only ever
        // returned INCOMPLETE, and the test would look green while constraining nothing.
        int incompletePrograms = 0;
        int exhaustivePrograms = 0;

        for (BenchmarkProgram p : BugCorpus.all()) {
            if (isIncomplete(run(p, 0))) {
                incompletePrograms++;
            }
            DfsResult atFull = run(p, nonBindingBound(p.program()));
            if (!isIncomplete(atFull)) {
                exhaustivePrograms++;
            }
        }

        assertEquals(BugCorpus.all().size(), exhaustivePrograms,
            "every corpus program must become exhaustive at its non-binding bound");
        assertTrue(incompletePrograms > 0,
            "at least one corpus program must be INCOMPLETE at K=0, or the band properties are vacuous");
    }

    @Test
    void everyCorpusProgramIsIncompleteAtZero() {
        // Pins the lower edge. K=0 admits only continuation-only schedules, so every program with
        // real blocking behaviour must be pruned there. If this ever fails, the lower bound of the
        // range under test is no longer exercising the prune path.
        for (BenchmarkProgram p : BugCorpus.all()) {
            assertTrue(isIncomplete(run(p, 0)),
                p.name() + ": K=0 should prune; a clean pass there means forced switches are being "
                    + "charged as preemptions, or the prune site is unreachable");
        }
    }

    // --- The converse, asserted NOT to hold -------------------------------------------

    @Test
    void statesExplored_neverFallsBelowDfs_atANonBindingBound() {
        // Recorded because the intuition is natural and wrong in the other direction. CBS's
        // visited key is (config, lastThreadId, preemption), and those extra dimensions are
        // required for its cost-aware correctness -- so it generally explores MORE states than DFS
        // and stays above indefinitely.
        //
        // The assertion is >=, not >. The extra dimensions only cost extra states where the search
        // actually revisits a configuration from a different last thread or budget; on a program
        // whose schedules never produce that, the two coincide. On this corpus that is
        // `torn-counter` alone -- every other program explores strictly more states under CBS.
        //
        // Recorded without hardcoded counts on purpose: the figures drift whenever the corpus or
        // the visited key changes, and a stale number in a comment reads as a live specification.
        // Re-derive with `./gradlew pitest` and the `statesExplored` column of
        // `build/reports/pitest/index.html` if the shape ever needs rechecking.
        //
        // Written as a test so nobody re-adds "CBS should converge on DFS" and then "fixes" a
        // correct implementation to match it. Going BELOW DFS at a non-binding bound would be the
        // real defect -- it would mean pruning something the exhaustive search reaches.
        for (BenchmarkProgram p : BugCorpus.all()) {
            long cbs = run(p, nonBindingBound(p.program())).statesExplored();
            long dfs = new dev.samhb.interleave.search.DfsExplorer()
                .explore(p.program(), p.invariant().orElse(null))
                .statesExplored();

            assertTrue(cbs >= dfs,
                p.name() + ": CBS explored fewer states than DFS at a non-binding bound ("
                    + cbs + " vs " + dfs + "), so it pruned something the exhaustive search reaches");
        }
    }

    @Test
    void statesExplored_exceedsDfs_onProgramsWithScheduleInterleaving() {
        // The stronger half of the record, where the extra key dimensions demonstrably cost
        // states. torn-counter is deliberately excluded rather than asserted equal: at nb=3 its
        // schedules never revisit a configuration from a different thread, so the two explorers
        // coincide, and pinning equality there would assert an accident of this corpus.
        for (BenchmarkProgram p : BugCorpus.all()) {
            if (p.name().equals("torn-counter")) {
                continue;
            }
            long cbs = run(p, nonBindingBound(p.program())).statesExplored();
            long dfs = new dev.samhb.interleave.search.DfsExplorer()
                .explore(p.program(), p.invariant().orElse(null))
                .statesExplored();

            assertTrue(cbs > dfs,
                p.name() + ": expected CBS above DFS (" + cbs + " vs " + dfs + ")");
        }
    }

    @Test
    void corpusProgramNames_areStable() {
        // If the corpus silently shrinks, every corpus-wide assertion above gets weaker without
        // failing. Cheap guard against that.
        List<String> names = BugCorpus.all().stream().map(BenchmarkProgram::name).toList();
        assertTrue(names.size() >= 7, "expected the full corpus, got " + names);
        assertTrue(names.contains("peterson") && names.contains("deadlock"),
            "corpus should include both a correct and a deadlocking program, got " + names);
    }
}
