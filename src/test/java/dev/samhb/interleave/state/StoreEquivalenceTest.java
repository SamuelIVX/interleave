package dev.samhb.interleave.state;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.StateStore;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;
import dev.samhb.interleave.search.TraceReplayer;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Store equivalence, asserted <em>asymmetrically</em>.
 *
 * <h2>Why not symmetric equality</h2>
 *
 * An earlier draft of the plan asserted that the exact store and the approximate store must report
 * the same verdict. That is wrong, and building it would have produced either a permanently failing
 * test or a quietly weakened one.
 *
 * <p>{@code BitstateStore} produces false <strong>visited</strong> answers: hashing collapses
 * distinct configurations onto shared bit positions. A false positive on {@code isVisited} prunes
 * exploration, so bitstate can only ever <em>miss</em> bugs. It cannot invent one. The README
 * documents this. So:
 *
 * <ul>
 *   <li>exact reports {@code VIOLATION}, bitstate reports {@code VIOLATION} -- required</li>
 *   <li>exact reports {@code VIOLATION}, bitstate reports {@code PASS} -- <strong>permitted</strong>,
 *       this is bitstate's documented false negative</li>
 *   <li>bitstate reports {@code VIOLATION}, exact reports {@code PASS} -- <strong>a defect</strong></li>
 * </ul>
 *
 * <h2>Why the strong direction is the safety-critical one</h2>
 *
 * A violation is discovered by <em>executing real steps along a real schedule</em>, never as an
 * artifact of deduplication. So a bitstate-only violation means the store fabricated evidence --
 * exactly the defect that makes a reported bug worthless. Replay validation closes that off
 * independently: every violation trace bitstate reports must replay, under the exact store, to a
 * state that genuinely violates the invariant.
 *
 * <h2>Scope</h2>
 *
 * Verdict-level agreement across real programs, not metric arithmetic. Density and FPR are pinned
 * separately by the {@code bitstateStore_metrics_*} tests; duplicating them here would test the
 * same code twice and assert nothing new.
 */
class StoreEquivalenceTest {

    /**
     * Large enough that the bitstate false-positive rate is negligible for a corpus this size, so
     * this test measures the <em>contract</em> rather than the hash quality. Deliberately not
     * small: a small store would make this test fail intermittently, and a flaky test is a test
     * people learn to ignore.
     */
    private static final int BITSTATE_SIZE = 1_000_003;
    private static final int BITSTATE_K = 4;

    private static int nonBindingBound(Program program) {
        return program.threads().stream().mapToInt(t -> t.steps().size()).sum();
    }

    private static DfsResult runWith(Program program, Invariant invariant, StateStore store, int k) {
        return new ContextBoundedExplorer().explore(program, invariant, store, null, k);
    }

    private static List<Trace> tracesWithOutcome(DfsResult result, TraceOutcome outcome) {
        return result.traces().stream().filter(t -> t.outcome() == outcome).toList();
    }

    /**
 * Peterson's correctness condition: mutual exclusion.
     *
     * <p>The corpus ships peterson with {@code expected_verdict: PASS} and <em>no</em> invariant,
     * because a program declared correct has no failing property to declare. Passing {@code null}
     * to the explorer would make this test vacuous: with no invariant there is nothing to violate,
     * so a store could report any trace at all and the VIOLATION list would still read empty.
     *
     * <p>Both threads run the same six-step shape
     * ({@code write_flag, write_turn, busy_wait, cs_enter, cs_exit, write_flag}), so a thread is
     * inside the critical section exactly when its pc is 4: {@code cs_enter} has executed and
     * {@code cs_exit} has not. Mutual exclusion is therefore "not both pcs equal to 4". Verified
     * against the exhaustive search by {@link #petersonMutualExclusion_isTheDeclaredProperty()},
     * so a wrong pc here surfaces there rather than silently making every assertion below vacuous.
     */
    private static boolean mutualExclusionHolds(List<Integer> pcs) {
        return !((pcs.get(0) == 4) && (pcs.get(1) == 4));
    }

    private static Invariant petersonInvariant() {
        return (state, config) -> mutualExclusionHolds(config.programCounters());
    }

    @Test
    void petersonMutualExclusion_rejectsOnlySimultaneousEntry() {
        // Direct controls on the predicate, because the exhaustive search can never feed it the
        // case that matters: a correct peterson never reaches (4, 4), so the rejecting branch is
        // dead code from the search's point of view. Without these controls the invariant could be
        // `pcs -> true` and every assertion using it would pass while testing nothing.
        //
        // (4, 4) is simultaneous entry and must be rejected. Anything else -- including one thread
        // inside while the other is before it, after it, or nowhere near it -- must be accepted.
        assertFalse(mutualExclusionHolds(List.of(4, 4)),
            "both threads inside the critical section at once must be rejected");
        assertTrue(mutualExclusionHolds(List.of(4, 5)), "one inside, the other past it: accepted");
        assertTrue(mutualExclusionHolds(List.of(5, 4)), "one past it, the other inside: accepted");
        assertTrue(mutualExclusionHolds(List.of(0, 0)), "neither inside: accepted");
        assertTrue(mutualExclusionHolds(List.of(3, 4)), "other thread before the CS: accepted");
        assertTrue(mutualExclusionHolds(List.of(4, 3)), "other thread before the CS: accepted");
    }

    @Test
    void petersonMutualExclusion_isTheDeclaredProperty() {
        // Confirms the invariant above is non-vacuous and satisfiable on the corpus's correct
        // program: it must hold everywhere, AND the search must actually reach the inside-the-CS
        // states. Without the second half the invariant could be `state -> true` and pass.
        Invariant invariant = petersonInvariant();
        BenchmarkProgram peterson = byName("peterson");
        DfsResult dfs = new dev.samhb.interleave.search.DfsExplorer()
            .explore(peterson.program(), invariant);

        assertTrue(tracesWithOutcome(dfs, TraceOutcome.VIOLATION).isEmpty(),
            "peterson's declared correctness condition must hold on the exhaustive search");
        assertFalse(dfs.traces().isEmpty(), "precondition: the search must have produced traces");

        boolean reachedInsideCriticalSection = dfs.states().values().stream()
            .anyMatch(c -> c.programCounters().get(0) == 4 || c.programCounters().get(1) == 4);
        assertTrue(reachedInsideCriticalSection,
            "precondition: peterson must actually enter the critical section, otherwise the "
                + "mutual-exclusion invariant never had an opportunity to fail");
    }

    // --- The safety-critical direction -------------------------------------------------

    @Test
    void bitstateViolations_areAlwaysConfirmedByTheExactStore() {
        // A violation bitstate reports must also be reported by the exact store. The converse is
        // deliberately not asserted anywhere in this file.
        for (BenchmarkProgram p : BugCorpus.all()) {
            Invariant invariant = p.invariant().orElse(null);
            int k = nonBindingBound(p.program());

            DfsResult bitstate = runWith(p.program(), invariant,
                new BitstateStore(BITSTATE_SIZE, BITSTATE_K, k), k);
            DfsResult exact = runWith(p.program(), invariant, new HashingStateStore(), k);

            assertTrue(tracesWithOutcome(exact, TraceOutcome.VIOLATION).size()
                    >= tracesWithOutcome(bitstate, TraceOutcome.VIOLATION).size(),
                p.name() + ": bitstate reported " + tracesWithOutcome(bitstate, TraceOutcome.VIOLATION).size()
                    + " violation(s) but the exact store reported only "
                    + tracesWithOutcome(exact, TraceOutcome.VIOLATION).size()
                    + ". Bitstate can miss violations; it must never invent them.");
        }
    }

    @Test
    void bitstateDeadlocks_areAlwaysConfirmedByTheExactStore() {
        // Same argument for deadlock. A deadlock is likewise reached by executing a real schedule,
        // so it cannot be a deduplication artifact.
        for (BenchmarkProgram p : BugCorpus.all()) {
            Invariant invariant = p.invariant().orElse(null);
            int k = nonBindingBound(p.program());

            DfsResult bitstate = runWith(p.program(), invariant,
                new BitstateStore(BITSTATE_SIZE, BITSTATE_K, k), k);
            DfsResult exact = runWith(p.program(), invariant, new HashingStateStore(), k);

            assertTrue(tracesWithOutcome(exact, TraceOutcome.DEADLOCK).size()
                    >= tracesWithOutcome(bitstate, TraceOutcome.DEADLOCK).size(),
                p.name() + ": bitstate reported a deadlock the exact store did not");
        }
    }

    @Test
    void everyBitstateViolationTrace_replaysToAGenuineViolation() {
        // Independent confirmation, not a restatement of the count check above. A bitstate-only
        // violation could in principle coincide with a count the exact store also reaches; replay
        // settles it by executing the schedule and asking whether the invariant is actually broken
        // at the end of it. This is the same guarantee SoundnessAttestation already enforces for
        // reported traces, applied to the approximate store.
        TraceReplayer replayer = new TraceReplayer();

        int checked = 0;
        for (BenchmarkProgram p : BugCorpus.all()) {
            Invariant invariant = p.invariant().orElse(null);
            if (invariant == null) {
                continue;
            }
            int k = nonBindingBound(p.program());
            DfsResult bitstate = runWith(p.program(), invariant,
                new BitstateStore(BITSTATE_SIZE, BITSTATE_K, k), k);

            for (Trace trace : tracesWithOutcome(bitstate, TraceOutcome.VIOLATION)) {
                Configuration end = replayer.replay(p.program(), trace);
                assertFalse(invariant.holds(end.state(), end),
                    p.name() + ": bitstate reported a violation whose schedule replays to a state "
                        + "where the invariant HOLDS. The store fabricated this bug.");
                checked++;
            }
        }
        assertTrue(checked > 0,
            "no bitstate violation traces were replayed -- the assertion above proved nothing. "
                + "Either the corpus stopped being buggy or the store changed.");
    }

    @Test
    void exactStoreViolationTraces_replayToAGenuineViolation() {
        // Same replay guarantee on the exact store, so the property is not read as
        // approximate-store-only leniency. Both stores must produce reproducible evidence; that is
        // what makes a reported bug worth replaying.
        TraceReplayer replayer = new TraceReplayer();
        int checked = 0;

        for (BenchmarkProgram p : BugCorpus.all()) {
            Invariant invariant = p.invariant().orElse(null);
            if (invariant == null) {
                continue;
            }
            int k = nonBindingBound(p.program());
            DfsResult exact = runWith(p.program(), invariant, new HashingStateStore(), k);

            for (Trace trace : tracesWithOutcome(exact, TraceOutcome.VIOLATION)) {
                Configuration end = replayer.replay(p.program(), trace);
                assertFalse(invariant.holds(end.state(), end),
                    p.name() + ": the exact store reported a violation whose schedule replays to a "
                        + "state where the invariant holds");
                checked++;
            }
        }
        assertTrue(checked > 0, "no exact-store violation traces were replayed");
    }

    @Test
    void bitstateNeverReportsAViolationForACorrectProgram() {
        // Sharpest form of the safety direction, and the one with the most bite: a correct program
        // has no violations anywhere, so any bitstate violation on it is necessarily fabricated.
        // peterson is the corpus's correct program, and its correctness condition is
        // now supplied rather than nulled -- see petersonInvariant().
        BenchmarkProgram peterson = byName("peterson");
        Invariant invariant = petersonInvariant();
        int k = nonBindingBound(peterson.program());

        for (int run = 0; run < 5; run++) {
            DfsResult bitstate = runWith(peterson.program(), invariant,
                new BitstateStore(BITSTATE_SIZE, BITSTATE_K, k), k);
            assertTrue(tracesWithOutcome(bitstate, TraceOutcome.VIOLATION).isEmpty(),
                "peterson is correct; bitstate reported " + tracesWithOutcome(bitstate, TraceOutcome.VIOLATION).size()
                    + " violation(s), which cannot be a false negative");
        }
    }

    // --- The permitted direction, asserted so it is not "fixed" ------------------------

    @Test
    void exactStoreMayReportViolationsBitstateMisses() {
        // Asserted as LEGAL so a future developer who notices the asymmetry does not treat it as a
        // bug and "fix" the bitstate store into failing. This is the documented false negative,
        // and it is the reason the comparison above is one-directional.
        //
        // Uses a deliberately undersized store to make the false negative happen deterministically
        // rather than waiting for a hash collision. A 1-bit store collapses nearly everything, so
        // bitstate prunes aggressively -- which is exactly the failure mode the contract permits.
        BenchmarkProgram lostUpdate = byName("lost-update");
        int k = nonBindingBound(lostUpdate.program());

        DfsResult exact = runWith(lostUpdate.program(), lostUpdate.invariant().orElse(null),
            new HashingStateStore(), k);
        DfsResult cramped = runWith(lostUpdate.program(), lostUpdate.invariant().orElse(null),
            new BitstateStore(64, 1, k), k);

        assertFalse(tracesWithOutcome(exact, TraceOutcome.VIOLATION).isEmpty(),
            "lost-update must violate under the exact store");
        assertTrue(tracesWithOutcome(cramped, TraceOutcome.VIOLATION).size()
                <= tracesWithOutcome(exact, TraceOutcome.VIOLATION).size(),
            "even a cramped bitstate must not exceed the exact store's violations");
    }

    @Test
    void bothStoresAgreeOnCorrectPrograms() {
        // The complement: where neither store is under pressure, and the program is correct, the
        // two must reach identical conclusions. This is not the general symmetry claim -- it is
        // scoped to a case where a false negative is impossible to observe.
        //
        // PASS is asserted explicitly. Two NO_TRACES results also "agree", so without it this test
        // would pass on a store that reported nothing whatsoever.
        for (String name : List.of("peterson")) {
            BenchmarkProgram p = byName(name);
            Invariant invariant = petersonInvariant();
            int k = nonBindingBound(p.program());

            DfsResult bitstate = runWith(p.program(), invariant,
                new BitstateStore(BITSTATE_SIZE, BITSTATE_K, k), k);
            DfsResult exact = runWith(p.program(), invariant, new HashingStateStore(), k);

            assertEquals(verdict(bitstate), verdict(exact),
                name + ": a correct program with headroom should not expose store asymmetry");
            assertEquals("PASS", verdict(exact),
                name + ": both stores must reach a genuine pass, not an empty result");
        }
    }

    // --- IsVisited-level agreement ----------------------------------------------------

    @Test
    void exactStoreNeverReportsFalsePositives_bitstateMay() {
        // The mechanism behind the asymmetry, asserted directly on the primitive. This documents
        // WHY the verdict comparison must be one-directional: the exact store's answers are
        // ground truth, bitstate's are not.
        //
        // Assert directly that both stores answer correctly on a state they were shown, then that
        // the exact store rejects an unmarked one. Split into separate assertions rather than one
        // combined count, because a single counter can read 0 both when the stores agree and when
        // the exact store's half of the comparison is never exercised.
        BenchmarkProgram p = byName("lost-update");
        Configuration start = p.program().initialConfiguration();
        Configuration stepped = start.successor(0, StepOutcome.ADVANCED, p.program().threads(),
            start.state().deepCopy());

        HashingStateStore exactStore = new HashingStateStore();
        BitstateStore bitstateStore = new BitstateStore(BITSTATE_SIZE, BITSTATE_K, 0);
        exactStore.clear();
        bitstateStore.clear();
        exactStore.markVisited(start);
        bitstateStore.markVisited(start);

        // (1) Both recognise what they were shown. Without this, assertion (2) would be satisfied
        // by "both stores always answer false".
        assertTrue(exactStore.isVisited(start), "the exact store must recognise a marked state");
        assertTrue(bitstateStore.isVisited(start), "the bitstate store must recognise a marked state");

        // (2) The exact store must reject an unmarked, demonstrably distinct state. This is the
        // assertion that grounds the whole file: every verdict-level claim elsewhere depends on
        // the exact store's answers being ground truth rather than merely authoritative.
        assertFalse(exactStore.isVisited(stepped),
            "precondition: the exact store must not claim to have seen an unmarked state");

        // The bitstate store's answer on that same state is deliberately not asserted in either
        // direction. A false positive there is its documented lossy behaviour; a false negative
        // only costs wasted work. Pinning either would encode the lossy behaviour as a
        // requirement, which is the mistake the asymmetric contract exists to avoid.
    }

    @Test
    void bitstateFalsePositives_areTheDocumentedFailureMode() {
        // The converse, observed rather than asserted as a rule: a cramped bitstate store does
        // report states as visited that the exact store has not seen. This false positive is what
        // makes it a lossy filter, and pinning that it still happens means the one-directional
        // contract in this file remains necessary rather than vestigial.
        //
        // A 1-bit store with k=1 maps every configuration onto bit 0, so any second configuration
        // is guaranteed to collide. Deterministic rather than probabilistic: a larger store would
        // need to wait for a hash collision, and a test that only sometimes proves its point is a
        // test that gets ignored.
        BenchmarkProgram p = byName("lost-update");
        Configuration start = p.program().initialConfiguration();

        // Step thread 0 once to get a genuinely different configuration.
        Configuration other = start.successor(0, StepOutcome.ADVANCED, p.program().threads(),
            start.state().deepCopy());

        HashingStateStore exact = new HashingStateStore();
        BitstateStore cramped = new BitstateStore(1, 1, 0);
        exact.clear();
        cramped.clear();
        exact.markVisited(start);
        cramped.markVisited(start);

        assertFalse(exact.isVisited(other), "precondition: the exact store has not seen this state");
        assertTrue(cramped.isVisited(other),
            "a 1-bit store should report a false positive for any unseen state; if this no longer "
                + "holds, the asymmetric contract above needs re-deriving");
    }

    // --- Shared-state invariants across store choice -----------------------------------

    @Test
    void storeChoice_neverChangesAPositiveFinding() {
        // The user-visible property: switching --store from bitstate to the exact store must never
        // turn a found bug into a clean run. This is what a user depending on --store bitstate
        // actually relies on.
        for (BenchmarkProgram p : BugCorpus.all()) {
            Invariant invariant = p.invariant().orElse(null);
            if (invariant == null) {
                continue;
            }
            int k = nonBindingBound(p.program());

            DfsResult bitstate = runWith(p.program(), invariant,
                new BitstateStore(BITSTATE_SIZE, BITSTATE_K, k), k);
            DfsResult exact = runWith(p.program(), invariant, new HashingStateStore(), k);

            if (!tracesWithOutcome(bitstate, TraceOutcome.VIOLATION).isEmpty()) {
                assertFalse(tracesWithOutcome(exact, TraceOutcome.VIOLATION).isEmpty(),
                    p.name() + ": bitstate found a bug and the exact store did not. That direction "
                        + "is never permitted.");
            }
            assertTrue(hasViolation(exact),
                p.name() + ": precondition -- the exact store must find the known bug");
        }
    }

    // --- Falsification ------------------------------------------------------------------

    @Test
    void aStoreReportingEveryStateAsVisited_producesNoViolations() {
        // Confirms the safety assertion has teeth: a maximally-collapsing store prunes
        // everything, finds nothing, and is correctly classified as "missed everything" rather than
        // "fabricated nothing". If the strong-direction assertion were vacuous, this store would
        // pass it too.
        //
        // Asserted as empty rather than as "<= the exact store's count". A count comparison is
        // satisfied by 0 <= 0, which is also what an exact store that found nothing would give --
        // so it cannot distinguish "collapsed store missed everything" from "nothing is buggy".
        BenchmarkProgram lostUpdate = byName("lost-update");
        Invariant invariant = lostUpdate.invariant().orElse(null);
        int k = nonBindingBound(lostUpdate.program());

        // Non-vacuity first: the exact store must find the known bug, otherwise "the collapsed
        // store found nothing" is trivially true.
        DfsResult exact = runWith(lostUpdate.program(), invariant, new HashingStateStore(), k);
        assertTrue(hasViolation(exact),
            "precondition: the exact store must find lost-update's violation");

        DfsResult collapsed = runWith(lostUpdate.program(), invariant,
            new BitstateStore(1, 1, k), k);
        assertTrue(tracesWithOutcome(collapsed, TraceOutcome.VIOLATION).isEmpty(),
            "a 1-bit store collapses every state onto one bit, prunes everything immediately, and "
                + "must report no violations at all. Reporting any would mean it fabricated one.");
    }

    private static boolean hasViolation(DfsResult result) {
        return !tracesWithOutcome(result, TraceOutcome.VIOLATION).isEmpty();
    }

    private static String verdict(DfsResult result) {
        if (hasViolation(result)) {
            return "VIOLATION";
        }
        if (!tracesWithOutcome(result, TraceOutcome.DEADLOCK).isEmpty()) {
            return "DEADLOCK";
        }
        if (!tracesWithOutcome(result, TraceOutcome.INCOMPLETE).isEmpty()) {
            return "INCOMPLETE";
        }
        if (!tracesWithOutcome(result, TraceOutcome.COMPLETED).isEmpty()) {
            return "PASS";
        }
        return "NO_TRACES";
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
