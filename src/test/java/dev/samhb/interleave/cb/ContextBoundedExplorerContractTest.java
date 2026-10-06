package dev.samhb.interleave.cb;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.search.StateVisitor;
import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.state.HashingStateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct contract assertions for {@link ContextBoundedExplorer}.
 *
 * <p>Spec 12.05 originally expected these mutants to be killed by adding a 3-thread corpus program, on
 * the theory that a uniformly 2-thread corpus left too few distinct {@code (config, lastThreadId,
 * preemption)} triples for wrong dominance to be observable. **Measurement refuted that.** See
 * {@code docs/specs/active/12-mutation-hardening/05-corpus-coverage.md}: with and without
 * {@code lost-update-3t}, PIT reports 105 mutants and identical statuses for all of them. So the corpus
 * program is kept as a genuine coverage improvement, and the mutants are closed here by asserting the
 * contracts directly instead.
 *
 * <p><b>Methodological note, because it cost two wrong probes.</b> {@link Configuration} declares no
 * {@code equals} or {@code hashCode}, so it compares by identity. Any set of configurations built from
 * those objects measures object identity, not configurations, and silently reports nonsense — including
 * "every visited configuration is missing from the result". Everything below therefore compares
 * configurations by {@link #position(Configuration)}, an independent state-value/counter record. Tests comparing
 * reachable configurations deliberately project away the last-thread component used by the explorer
 * and store's scheduling keys; preemption-specific tests cover that component separately.
 */
class ContextBoundedExplorerContractTest {

    /** Independent base identity; snapshots values and deliberately projects away scheduling context. */
    private static Position position(Configuration c) {
        return new Position(c.state().deepCopy(), c.programCounters());
    }

    private record Position(SharedState state, List<Integer> counters) {}

    private static BenchmarkProgram lostUpdate3t() {
        return BugCorpus.all().stream()
            .filter(p -> "lost-update-3t".equals(p.name()))
            .findFirst()
            .orElseThrow();
    }

    /**
     * L82 — the undersized-store rejection names the store's <em>configured</em> capacity.
     *
     * <p>The guard is correct: clamping would return a verdict that looks exhaustive at K while being
     * exhaustive only at the store's capacity, which is the silent unsoundness the class exists to avoid.
     * The message exists to let a caller diagnose that, so it has to carry the real number. Removing
     * {@code bitstate.maxPreemptions()} from the concatenation leaves a well-formed message that reports
     * the wrong capacity, and no other test in the suite looks at the text.
     */
    @Test
    void undersizedStore_isRejectedWithAMessageNamingTheConfiguredCapacity() {
        BitstateStore store = new BitstateStore(1024, 4, 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> new ContextBoundedExplorer().explore(
                lostUpdate3t().program(), null, store, null, 3),
            "a store sized for K=1 must be rejected when K=3 is requested");

        String message = thrown.getMessage();
        // Boundary-aware: a plain contains("capacity 1") would also accept "capacity 10" or
        // "capacity 128", so a message reporting the wrong two- or three-digit capacity would pass.
        assertTrue(CAPACITY_ONE.matcher(message).find(),
            "message should name the store's configured capacity of 1 as a standalone number, "
                + "but was: " + message);
        assertTrue(message.contains("maxPreemptions"),
            "message should name the requested bound, but was: " + message);
    }

    /** Matches "capacity 1" only when 1 is not the leading digit of a longer number. */
    private static final Pattern CAPACITY_ONE = Pattern.compile("\\bcapacity 1\\b");

    /**
     * L87 — a pre-populated store is cleared before traversal.
     *
     * <p>Related to, but distinct from, Spec 12.02 §(a): 12.02 asserts that
     * {@code HashingStateStore.clear()} empties its own four collections, which is a fact about the store
     * under {@code interleave/state}. This asserts the caller in {@code cb} invokes it, which is a
     * different fact about a different class. Removing {@code store.clear()} leaves the store's own
     * clear() perfectly correct while every subsequent search on that store prunes against the previous
     * search's contents.
     */
    @Test
    void storeIsClearedBeforeTraversal_soAPrePopulatedStoreDoesNotPrune() {
        BenchmarkProgram program = lostUpdate3t();
        HashingStateStore store = new HashingStateStore();
        ContextBoundedExplorer explorer = new ContextBoundedExplorer();

        long clean = explorer.explore(program.program(), null, store, null, 2).statesExplored();

        // Reuse the very same store. Without store.clear() every configuration is still marked, so the
        // second search prunes at its root and explores nothing.
        long reused = explorer.explore(program.program(), null, store, null, 2).statesExplored();

        assertEquals(clean, reused,
            "reusing a store must not let the previous search's visited set prune the next one; "
                + "first run explored " + clean + ", second explored " + reused);
        assertTrue(clean > 0, "the program must explore something, or this assertion is vacuous");
    }

    /**
     * L88 — {@code visitedStates} is cleared between runs on one explorer instance.
     *
     * <p>{@code visitedStates} is instance state, not local state, so it accumulates across
     * {@code explore} calls unless explicitly cleared. Dropping the clear does not change any single
     * search's verdict, which is why it survived: the damage only shows as a stale or inflated
     * {@code states()} map on the <em>second</em> run.
     */
    @Test
    void visitedStatesIsClearedBetweenRunsOnTheSameExplorer() {
        BenchmarkProgram program = lostUpdate3t();
        ContextBoundedExplorer explorer = new ContextBoundedExplorer();

        var first = explorer.explore(program.program(), null, null, null, 2);
        var second = explorer.explore(program.program(), null, null, null, 2);

        assertEquals(first.statesExplored(), second.statesExplored(),
            "two identical searches on one explorer must explore equally many states");
        assertEquals(first.states().keySet(), second.states().keySet(),
            "the result map must not accumulate keys across runs; second run had "
                + second.states().size() + " entries against the first run's " + first.states().size());

        // Repeating one program cannot detect a missing clear(): the second run re-adds byte-identical
        // keys, so an uncleared map is indistinguishable from a cleared one. Running a DIFFERENT program
        // is what makes the accumulation visible -- the first program's keys survive into the second
        // run's result and are reported as configurations that run never reached.
        BenchmarkProgram other = BugCorpus.all().stream()
            .filter(p -> "torn-counter".equals(p.name()))
            .findFirst()
            .orElseThrow();
        Set<Position> otherConfigurations = new HashSet<>();
        var otherResult = explorer.explore(other.program(), null, null,
            new StateVisitor() {
                @Override
                public void onStateVisited(Configuration c) {
                    otherConfigurations.add(position(c));
                }

                @Override
                public void onStateVisited(Configuration c, int t, int p) {
                    otherConfigurations.add(position(c));
                }
            }, 2);

        Set<Position> reported = new HashSet<>();
        otherResult.states().values().forEach(c -> reported.add(position(c)));
        Set<Position> stale = new HashSet<>(reported);
        stale.removeAll(otherConfigurations);

        assertTrue(stale.isEmpty(),
            "exploring " + other.name() + " after another program reported " + stale.size()
                + " configurations it never reached; visitedStates was not cleared between runs");
        assertEquals(otherConfigurations.size(), reported.size(),
            "every configuration in the result should have been reached during this run: reported "
                + reported.size() + ", reached " + otherConfigurations.size());

        // And the asymmetry that actually matters: a larger bound must strictly enlarge the result map.
        // This is what catches a clear() that empties the map but leaves the counter, or vice versa.
        var wider = explorer.explore(program.program(), null, null, null, 3);
        assertTrue(wider.statesExplored() > first.statesExplored(),
            "raising the bound must explore more, but went from " + first.statesExplored()
                + " at K=2 to " + wider.statesExplored() + " at K=3");
    }

    /**
     * L113 — every distinct configuration the explorer reaches is reported in {@code states()}.
     *
     * <p>The result map is keyed {@code state|counters|lastThreadId}. Dropping any component of that key
     * makes distinct configurations collide, so entries are silently lost and {@code states()}
     * under-reports what was actually explored. The original concatenation produced five mutants; the
     * shared-key delegation now has one. No verdict-level test can see lost map entries because the
     * verdict is unaffected.
     *
     * <p>The ground truth is the {@link StateVisitor} hook, which reports each exploration
     * independently of the map the explorer writes. Configurations are compared by
     * {@link #position(Configuration)} rather than by object identity, because
     * {@link Configuration} has no {@code equals}.
     *
     * <p><b>Deliberately not asserted:</b> {@code states().size() == statesExplored()}. That is false on
     * correct code, and asserting it would enshrine a defect. {@code statesExplored} counts exploration
     * <em>events</em> — the same configuration is legitimately reached at several preemption counts, up to
     * four times measured across the corpus — while {@code states()} reports distinct configurations.
     * Two different quantities, both correct.
     */
    @Test
    void everyDistinctConfigurationReached_isReportedInStates() {
        for (int bound = 1; bound <= 3; bound++) {
            for (BenchmarkProgram program : BugCorpus.all()) {
                Set<Position> reached = new HashSet<>();
                var result = new ContextBoundedExplorer().explore(
                    program.program(), program.invariant().orElse(null), null,
                    new StateVisitor() {
                        @Override
                        public void onStateVisited(Configuration c) {
                            reached.add(position(c));
                        }

                        @Override
                        public void onStateVisited(Configuration c, int lastThreadId, int preemptions) {
                            reached.add(position(c));
                        }
                    },
                    bound);

                Set<Position> reported = new HashSet<>();
                result.states().values().forEach(c -> reported.add(position(c)));

                // Both directions. Reached-minus-reported catches a key that collides distinct
                // configurations and drops them. Reported-minus-reached
                // catches the reverse: a map claiming a configuration the search never reached. Compared
                // as sets of values, so one configuration reached under several lastThreadId values
                // collapses correctly and is not reported as spurious.
                Set<Position> lost = new HashSet<>(reached);
                lost.removeAll(reported);
                // Build the example lazily: JUnit evaluates a message argument eagerly, so calling
                // lost.iterator().next() inline would throw before assertTrue ever ran.
                String example = lost.isEmpty() ? "" : " Example: " + lost.iterator().next();
                assertTrue(lost.isEmpty(),
                    program.name() + " at K=" + bound + ": " + lost.size()
                        + " configurations were reached but never reported in states(); "
                        + "the result map key has lost a component." + example);

                Set<Position> invented = new HashSet<>(reported);
                invented.removeAll(reached);
                String inventedExample = invented.isEmpty() ? "" : " Example: " + invented.iterator().next();
                assertTrue(invented.isEmpty(),
                    program.name() + " at K=" + bound + ": states() reports " + invented.size()
                        + " configurations this run never reached." + inventedExample);
            }
        }
    }

    /**
     * Records the measured relationship between the two counts, so the deliberate omission above cannot
     * be mistaken for an oversight and a future change to either is noticed.
     */
    @Test
    void statesMapReportsDistinctConfigurationsWhileStatesExploredCountsEvents() {
        boolean sawRepeat = false;

        for (int bound = 1; bound <= 3; bound++) {
            BenchmarkProgram program = lostUpdate3t();
            Set<Position> perConfigVisits = new HashSet<>();
            List<Configuration> events = new ArrayList<>();
            var result = new ContextBoundedExplorer().explore(program.program(), null, null,
                new StateVisitor() {
                    @Override
                    public void onStateVisited(Configuration c) {
                        events.add(c);
                        perConfigVisits.add(position(c));
                    }

                    @Override
                    public void onStateVisited(Configuration c, int t, int p) {
                        events.add(c);
                        perConfigVisits.add(position(c));
                    }
                }, bound);

            // Two independent mechanisms account for the same explorations: the visitor fires once per
            // state visited, and statesExplored counts them. Disagreement means one of them dropped or
            // double-counted, which is a real defect and would silently invalidate the comparison below.
            assertEquals(events.size(), result.statesExplored(),
                "at K=" + bound + " the visitor reported " + events.size() + " explorations but the "
                    + "result counted " + result.statesExplored());

            if (events.size() > perConfigVisits.size()) sawRepeat = true;
        }

        // If no bound ever revisited a configuration, events and distinct would coincide everywhere and
        // the deliberate distinction asserted in the test above would be vacuous. This is the premise
        // that makes that omission safe, so it is asserted rather than assumed.
        assertTrue(sawRepeat,
            "the premise of this test is that configurations are revisited at higher preemption counts; "
                + "at no bound from 1 to 3 was any configuration reached twice, so the "
                + "events/distinct distinction needs revisiting");
    }
}
