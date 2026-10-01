package dev.samhb.interleave.cb;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.TraceOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential test: the bounded search must agree with the exhaustive one.
 *
 * <p>DFS cannot be unsound by construction -- it prunes nothing. Context-bounded search prunes,
 * and pruning is exactly how a model checker starts missing bugs. Nothing else in the suite
 * constrains that: the one existing cross-strategy test iterates {@code Strategy.values()} but
 * asserts only that a result exists, so it would pass with the pruner arbitrarily broken.
 *
 * <p>Every test here is written so that it goes red if CBS reports a wrong verdict. See
 * {@link #syntheticOracle_actuallyDisagreesWithBoundedSearch_provesTheComparisonHasTeeth}, which
 * fails if the comparison below ever stops being able to detect a disagreement.
 */
class CbsDifferentialTest {

    /**
     * A schedule of length L admits at most L-1 preemptions, so a bound at or above the total
     * step count of every thread is provably non-binding: no execution can be cut off, and the
     * bounded search is exploring the same space as the exhaustive one.
     *
     * <p>Derived rather than hardcoded. A literal bound happens to work for today's corpus and
     * turns a soundness test into a mystery failure the first time a longer program lands -- and
     * a mystery failure in the <em>soundness</em> test is the worst possible failure mode, because
     * the first instinct is to relax the assertion.
     */
    private static int nonBindingBound(Program program) {
        return program.threads().stream()
            .mapToInt(t -> t.steps().size())
            .sum();
    }

    /**
     * Collapses a result to a single verdict, with the precedence the corpus declarations use.
     *
     * <p>INCOMPLETE outranks COMPLETED, and this ordering is load-bearing. A bounded search that
     * exhausts its budget emits both: it completed the schedules it could afford, <em>and</em> it
     * failed to prove anything about the region it pruned. Reporting the COMPLETED traces alone
     * would turn an unsound search into a clean pass -- which is precisely the failure B.1 exists
     * to catch, so the verdict function must not introduce it.
     */
    private static String verdictOf(DfsResult result) {
        if (result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION)) {
            return "VIOLATION";
        }
        if (result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK)) {
            return "DEADLOCK";
        }
        if (result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE)) {
            return "INCOMPLETE";
        }
        if (result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.COMPLETED)) {
            return "PASS";
        }
        return "NO_TRACES";
    }

    private static DfsResult runDfs(BenchmarkProgram p) {
        Invariant invariant = p.invariant().orElse(null);
        return new DfsExplorer().explore(p.program(), invariant);
    }

    private static DfsResult runCbs(BenchmarkProgram p, int bound) {
        Invariant invariant = p.invariant().orElse(null);
        return new ContextBoundedExplorer()
            .explore(p.program(), invariant, null, null, bound);
    }

    // --- B.1: verdict equality at a provably non-binding bound -----------------------

    @Test
    void boundedSearch_matchesExhaustiveSearch_atNonBindingBound() {
        // The core property. Across every verdict class -- violation, deadlock, and pass.
        for (BenchmarkProgram p : BugCorpus.all()) {
            int bound = nonBindingBound(p.program());
            String exhaustive = verdictOf(runDfs(p));
            String bounded = verdictOf(runCbs(p, bound));

            assertVerdictsAgree(p.name(), bound, exhaustive, bounded);
        }
    }

    @Test
    void nonBindingBound_neverReportsIncomplete() {
        // Validates the derivation itself. If the bound were binding, CBS would return
        // INCOMPLETE and the assertion above would be vacuously satisfied by both sides
        // returning "no verdict" -- so this makes the test self-checking against its own
        // precondition rather than trusting the comment that explains it.
        for (BenchmarkProgram p : BugCorpus.all()) {
            int bound = nonBindingBound(p.program());
            DfsResult result = runCbs(p, bound);

            assertTrue(result.statesExplored() > 0,
                p.name() + ": a non-binding bound must still explore states");
            assertTrue(
                result.traces().stream().noneMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE),
                p.name() + ": K=" + bound + " is provably non-binding, so INCOMPLETE means the "
                    + "derivation is wrong and the equality test above proves nothing");
        }
    }

    @Test
    void peterson_transitionsFromIncompleteToPass_asBoundGrows() {
        // Pins the specific transition the corpus documents: peterson is INCOMPLETE at the
        // default K=2 and a genuine pass once the bound is high enough. Without this, the
        // equality test could be satisfied by CBS always returning INCOMPLETE.
        BenchmarkProgram peterson = byName("peterson");

        // Spans the corpus transition band, not just the point where peterson becomes
        // exhaustive: K=2 is where it is still prunable, K=3 is the first bound that proves
        // anything. A bound below the band would make the comparison vacuous (both sides
        // INCOMPLETE), and one above it would stop exercising the INCOMPLETE branch at all.
        assertEquals("INCOMPLETE", verdictOf(runCbs(peterson, 2)),
            "peterson should still be INCOMPLETE at K=2");
        assertEquals("PASS", verdictOf(runCbs(peterson, 3)),
            "and become a genuine pass at K=3");
        assertEquals("PASS", verdictOf(runDfs(peterson)), "DFS should also report a pass");
    }

    @Test
    void corpusVerdicts_matchDeclaredExpectations() {
        // Confirms the differential comparison is anchored to known ground truth, not merely to
        // DFS. If both explorers regressed the same way this would catch it; that is the reason
        // the corpus ships declared verdicts.
        for (BenchmarkProgram p : BugCorpus.all()) {
            String expected = p.expectedVerdict();
            String dfs = verdictOf(runDfs(p));
            assertEquals(expected, dfs,
                p.name() + ": DFS verdict does not match the corpus declaration");

            String cbs = verdictOf(runCbs(p, nonBindingBound(p.program())));
            assertEquals(expected, cbs,
                p.name() + ": CBS verdict does not match the corpus declaration");
        }
    }

    // --- Falsification ---------------------------------------------------------------

    @Test
    void syntheticOracle_actuallyDisagreesWithBoundedSearch_provesTheComparisonHasTeeth() {
        // A differential test is only as good as its ability to fail. If this comparison cannot
        // detect a wrong verdict, the equality test above is manufacturing confidence and nobody
        // would know until a real bug slipped through.
        //
        // Stands in for "make CBS return PASS for a buggy program", which cannot be done here
        // without editing production code. Instead the *comparison itself* is driven with a
        // deliberately wrong CBS verdict -- exactly what a CBS that wrongly prunes would produce:
        // the budget was exhausted, the search stopped early, and it reported a clean pass.
        //
        // Asserting that the comparison throws, rather than restating it in different words, is
        // the whole point. The previous version of this test compared a hardcoded "PASS" against
        // DFS's verdict, which tested nothing -- it could not have failed even if the comparison
        // had been deleted.
        for (BenchmarkProgram p : List.of(byName("lost-update"), byName("torn-counter"))) {
            String exhaustive = verdictOf(runDfs(p));
            assertEquals("VIOLATION", exhaustive,
                p.name() + ": fixture precondition, this program is buggy");

            for (String wrongVerdict : List.of("PASS", "DEADLOCK", "INCOMPLETE", "NO_TRACES")) {
                AssertionError thrown = assertThrows(AssertionError.class,
                    () -> assertVerdictsAgree(p.name(), 99, exhaustive, wrongVerdict),
                    p.name() + ": the comparison accepted a CBS verdict of " + wrongVerdict
                        + " against a DFS verdict of " + exhaustive
                        + ". B.1 could no longer detect a wrong bounded-search result.");
                assertTrue(thrown.getMessage().contains(p.name()),
                    "the failure should name the program so a real regression is diagnosable");
            }
        }

        // And the control: the comparison must still ACCEPT the correct verdict, or the throws
        // above would only prove that assertEquals rejects strings.
        BenchmarkProgram lostUpdate = byName("lost-update");
        assertVerdictsAgree(lostUpdate.name(), 99, verdictOf(runDfs(lostUpdate)),
            verdictOf(runCbs(lostUpdate, nonBindingBound(lostUpdate.program()))));
    }

    @Test
    void droppingTheInvariant_wouldMaskTheViolation() {
        // Second falsification, on the mechanism rather than the verdict. Every differential
        // comparison here passes the invariant through. This asserts that dropping it changes the
        // answer on the buggy corpus programs -- so a future refactor that forgets to pass the
        // invariant produces a visible failure rather than a suite that quietly stops testing
        // anything.
        for (BenchmarkProgram p : BugCorpus.all()) {
            DfsResult withInvariant = runDfs(p);
            DfsResult withoutInvariant = new DfsExplorer().explore(p.program());

            String expected = p.expectedVerdict();
            if ("VIOLATION".equals(expected)) {
                assertEquals("VIOLATION", verdictOf(withInvariant), p.name() + ": precondition");
                assertNotEquals(verdictOf(withInvariant), verdictOf(withoutInvariant),
                    p.name() + ": the invariant must change the verdict, otherwise B.1's "
                        + "comparison is not testing anything on this program");
            }
        }
    }

    /**
     * The comparison the differential test exists to make, extracted so a falsification check can
     * drive it directly.
     *
     * <p>If the comparison lives inline in the test, a falsification check cannot exercise it --
     * it can only restate the assertion in different words, which proves nothing. Keeping it in a
     * method lets {@link #syntheticOracle_actuallyDisagreesWithBoundedSearch_provesTheComparisonHasTeeth()}
     * feed it a deliberately wrong verdict and require it to fail.
     */
    private static void assertVerdictsAgree(String name, int bound, String exhaustive, String bounded) {
        assertEquals(exhaustive, bounded,
            name + ": CBS and DFS disagree at a non-binding bound K=" + bound);
    }

    private static BenchmarkProgram byName(String name) {
        for (BenchmarkProgram p : BugCorpus.all()) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        throw new IllegalArgumentException("no such program: " + name);
    }
}
