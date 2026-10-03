package dev.samhb.interleave.state;

import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.search.StateStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle and reporting contracts for {@link HashingStateStore}. Spec 12.02.
 *
 * <p>This store is the differential oracle for {@link BitstateStore}. If it reports a configuration as
 * visited that it has not seen, every verdict-level conclusion drawn by comparing the two stores is
 * unsound, which is why the {@code TRUE_RETURNS} mutant on {@code isVisited} is the highest-priority
 * item in the spec despite being one line.
 *
 * <p><b>Two things here are white-box, both unavoidably.</b> R1 mandates verifying each collection
 * <em>individually</em>, and no public method exposes {@code visitedHashes} or
 * {@code preemptionHashes}, so {@link #clear_emptiesEveryCollection()} reads all four privately —
 * without it, the L52 and L54 mutants survive. Separately, {@code Configuration} exposes only
 * {@code initial} and {@code successor}, neither of which can place a thread at an arbitrary
 * position, so the fixtures use its private constructor rather than being pinned to one program's
 * traversal order.
 *
 * <p>Everything else asserts public behaviour only. In particular
 * {@link #hashPrefilterHitOnAnUnseenConfiguration_isStillReportedNotVisited()} reads no private
 * state: it constructs a genuinely colliding pair and marks one, querying the other. R7 forbids
 * asserting on internal key strings or hash values, and nothing here does.
 */
class HashingStateStoreLifecycleTest {

    // ---------------------------------------------------------------- R1, R2 — clear()

    /**
     * R1 — {@code clear()} empties all four collections, verified per collection.
     *
     * <p>Four independent assertions, not one aggregate. A single {@code size() == 0} assertion would
     * pass with three of the four collections still populated, which is precisely the failure this
     * spec exists to catch.
     */
    @Test
    void clear_emptiesEveryCollection() {
        HashingStateStore store = new HashingStateStore();
        Configuration plain = configurationAt(new CounterState(7), 1, 0);
        Configuration costed = configurationAt(new CounterState(9), 1, 1);

        store.markVisited(plain);
        store.markVisited(costed, 1, 4);
        assertTrue(plainCollectionsPopulated(store), "precondition: all four collections should hold data");

        store.clear();

        assertEquals(0, fieldSet(store, "visitedHashes").size(), "visitedHashes not emptied by clear()");
        assertEquals(0, fieldSet(store, "visitedStates").size(), "visitedStates not emptied by clear()");
        assertEquals(0, fieldSet(store, "preemptionHashes").size(), "preemptionHashes not emptied by clear()");
        assertEquals(0, fieldMap(store, "minPreemptions").size(), "minPreemptions not emptied by clear()");
    }

    /**
     * R2 — after {@code clear()} the store behaves as a brand-new one.
     *
     * <p>This is the behavioural half of R1. Note what it deliberately does <em>not</em> cover: no public
     * method can distinguish "prefilter cleared" from "prefilter stale but exact set empty", because a
     * stale prefilter hit only falls through to the exact check, which reads a collection {@code clear()}
     * also emptied. L52 and L54 are killed by {@link #clear_emptiesEveryCollection()}, which reads the
     * two prefilter sets directly — that per-collection white-box check is what R1 asks for, and these
     * assertions pin the part of the contract a caller can actually observe.
     */
    @Test
    void clear_makesTheStoreBehaveAsNew() {
        HashingStateStore store = new HashingStateStore();
        Configuration plain = configurationAt(new CounterState(3), 2, 0);
        Configuration costed = configurationAt(new CounterState(5), 1, 2);

        store.markVisited(plain);
        store.markVisited(costed, 0, 3);

        store.clear();

        assertEquals(0, store.size(), "size() must be 0 after clear()");
        assertEquals(0, store.preemptionEntryCount(), "preemptionEntryCount() must be 0 after clear()");
        assertFalse(store.isVisited(plain),
            "a state marked before clear() must not be reported visited afterwards");
        assertFalse(store.isVisited(costed, 0, 3),
            "a (config, thread) pair marked before clear() must not be reported visited afterwards");

        // And the store must behave as new, not merely report zero: re-marking and re-querying has to
        // work, which fails if clear() left the prefilter and the exact set out of step with each other.
        store.markVisited(plain);
        assertTrue(store.isVisited(plain), "the store must accept new marks after clear()");
        assertEquals(1, store.size());
    }

    // ---------------------------------------------------------------- R3 — freshCopy()

    /**
     * R3 — {@code freshCopy()} returns a usable store, never {@code null}.
     *
     * <p>Targets the {@code NULL_RETURNS} mutant at L123, currently {@code NO_COVERAGE}. A {@code null}
     * store fails at the caller's first {@code isVisited}, far from the line that produced it, so the
     * contract worth pinning is that the copy is immediately usable.
     */
    @Test
    void freshCopy_returnsANonNullUsableStore() {
        HashingStateStore original = new HashingStateStore();
        StateStore copy = original.freshCopy();

        assertNotNull(copy, "freshCopy() must never return null");
        Configuration config = configurationAt(new CounterState(4), 1, 0);

        assertFalse(copy.isVisited(config), "a fresh copy must report nothing visited");
        assertDoesNotThrow(copy::clear, "a fresh copy must be usable, not just non-null");
        copy.markVisited(config);
        assertTrue(copy.isVisited(config), "a fresh copy must accept marks");
    }

    /**
     * R3 — the copy is independent of the original in both directions.
     *
     * <p>"Copy" that shares deduplication state with its source gives two searches the same visited set,
     * which is the cross-run contamination Spec 12.05's L87 test guards from the explorer side.
     */
    @Test
    void freshCopy_isIndependentOfTheOriginal() {
        HashingStateStore original = new HashingStateStore();
        StateStore copy = original.freshCopy();
        Configuration onlyInOriginal = configurationAt(new CounterState(11), 1, 0);
        Configuration onlyInCopy = configurationAt(new CounterState(13), 1, 0);

        original.markVisited(onlyInOriginal);
        copy.markVisited(onlyInCopy);

        assertTrue(original.isVisited(onlyInOriginal), "original lost its own mark");
        assertTrue(copy.isVisited(onlyInCopy), "copy lost its own mark");
        assertFalse(original.isVisited(onlyInCopy), "a mark in the copy leaked into the original");
        assertFalse(copy.isVisited(onlyInOriginal), "a mark in the original leaked into the copy");
        assertEquals(1, original.size(), "the original must hold exactly its own mark");
        // freshCopy() is typed as the StateStore interface, which exposes no size(); the returned
        // instance is a HashingStateStore, and asserting instanceof makes the cast below safe and
        // turns this into a check on the concrete bookkeeping rather than the interface alone.
        assertInstanceOf(HashingStateStore.class, copy, "freshCopy() must return a HashingStateStore");
        assertEquals(1, ((HashingStateStore) copy).size(), "the copy must hold exactly its own mark");
    }

    // ---------------------------------------------------------------- R4 — isVisited exactness

    /**
     * R4 — a prefilter hit on an unseen configuration must still report <em>not</em> visited.
     *
     * <p>This is the whole point of the two-level scheme and the target of the {@code TRUE_RETURNS}
     * mutant at L39: {@code return visitedStates.contains(encoded)} becomes {@code return true}, and
     * every configuration whose hash is already present is reported as visited regardless of whether it
     * was ever marked.
     *
     * <p><b>The colliding pair.</b> Reaching that branch needs two distinct configurations sharing a
     * hash, one marked and the other queried. Enumerating every reachable configuration in the corpus
     * (212 configurations, 128 distinct hashes) produced no such pair — every colliding bucket held the
     * same configuration, distinguished only by object identity, because {@code Configuration} declares
     * no {@code equals}. So the pair is constructed instead.
     *
     * <p>It needs only that {@code List.hashCode()} collide while {@code toString()} does not, since the
     * store hashes {@code counters} and keys on {@code counters.toString()}. List hashing is mixed-radix
     * in base 31, so {@code [0, 31]} and {@code [1, 0]} both hash to 992 ({@code 31·0+31 == 31·1+0}) while
     * printing differently. Over an identical {@code SharedState} that makes the two encodings differ and
     * the two store hashes equal.
     *
     * <p>Counter {@code 31} is not a reachable thread position, which is exactly why the corpus has no
     * colliding pair. That is the finding, not a weakness of the fixture: the store promises exactness
     * for any two configurations, not only reachable ones, and the exact check — not the hash — is what
     * makes that promise hold.
     */
    @Test
    void hashPrefilterHitOnAnUnseenConfiguration_isStillReportedNotVisited() {
        HashingStateStore store = new HashingStateStore();
        Configuration marked = configurationWithCounters(new CounterState(17), List.of(0, 31));
        Configuration unseen = configurationWithCounters(new CounterState(17), List.of(1, 0));

        // The pair must genuinely collide, or the test silently stops reaching L39 and passes for the
        // wrong reason — exactly the failure that makes prefilter-guarded mutants so persistent.
        // List hashing is 961 + 31*t0 + t1, so [0,31] and [1,0] both hash to 992.
        assertEquals(List.of(0, 31).hashCode(), List.of(1, 0).hashCode(),
            "precondition: the two counter lists must collide on hash");
        assertNotEquals(List.of(0, 31).toString(), List.of(1, 0).toString(),
            "precondition: the two counter lists must differ in the key's text");

        store.markVisited(marked);
        assertTrue(store.isVisited(marked), "precondition: the marked configuration must be reported visited");

        // The marked configuration's hash is now in the prefilter, and the unseen one shares it, so
        // isVisited reaches L39 rather than short-circuiting at the prefilter.
        assertFalse(store.isVisited(unseen),
            "a configuration that collides on hash but was never marked must not be reported visited; "
                + "isVisited is exact and the hash is only an optimisation");
    }

    /**
     * R4 — the same property across the whole reachable corpus, both directions.
     *
     * <p>Walks every reachable configuration and asserts the store agrees with its own marks in both
     * directions: before a configuration is marked it must be reported unvisited, and afterwards
     * visited. The "before" half matters independently — it is the only assertion here that would notice
     * a store reporting configurations as visited which it has never seen, across the whole corpus rather
     * than for one hand-picked configuration.
     */
    @Test
    void isVisited_neverReportsAnUnmarkedConfigurationAsVisited() {
        for (var program : BugCorpus.all()) {
            List<Configuration> reachable = reachableConfigurations(program.program().threads(),
                program.program().initialConfiguration());

            HashingStateStore store = new HashingStateStore();
            for (Configuration config : reachable) {
                assertFalse(store.isVisited(config),
                    program.name() + ": " + config.programCounters()
                        + " must not be reported visited before it is marked");
                store.markVisited(config);
                assertTrue(store.isVisited(config),
                    program.name() + ": " + config.programCounters()
                        + " must be reported visited once marked");
            }

            // An unmarked configuration built from a state no walk visited.
            Configuration stranger = configurationAt(new CounterState(101), 1, 0);
            assertFalse(store.isVisited(stranger),
                "a configuration never marked must never be reported visited");
        }
    }

    // ---------------------------------------------------------------- R5 — preemptionEntryCount()

    /**
     * R5 — {@code preemptionEntryCount()} counts distinct {@code (config, lastThreadId)} pairs, taking 0
     * before any mark, 1 after one pair, N after N pairs, and staying at 1 when the same pair is
     * re-marked at a different budget.
     *
     * <p>Targets the two survivors at L98. The merge case is the load-bearing one: the store keeps the
     * minimum budget per pair, so re-marking at a higher or lower count must not create a second entry.
     */
    @Test
    void preemptionEntryCount_countsDistinctConfigThreadPairs() {
        HashingStateStore store = new HashingStateStore();
        Configuration first = configurationAt(new CounterState(1), 1, 0);
        Configuration second = configurationAt(new CounterState(2), 1, 0);

        assertEquals(0, store.preemptionEntryCount(), "must start empty");

        store.markVisited(first, 0, 3);
        assertEquals(1, store.preemptionEntryCount(), "one distinct pair must be one entry");

        store.markVisited(first, 1, 3);
        assertEquals(2, store.preemptionEntryCount(), "a second thread id is a second pair");

        store.markVisited(second, 0, 3);
        assertEquals(3, store.preemptionEntryCount(), "a second configuration is a third pair");

        // Same pair, higher budget: subsumed by the recorded minimum, so still one entry.
        store.markVisited(first, 0, 9);
        assertEquals(3, store.preemptionEntryCount(),
            "re-marking the same (config, thread) at a higher budget must not add an entry");

        // Same pair, lower budget: replaces the minimum, still one entry.
        store.markVisited(first, 0, 1);
        assertEquals(3, store.preemptionEntryCount(),
            "re-marking the same (config, thread) at a lower budget must not add an entry");

        // The single-argument API shares none of this bookkeeping.
        store.markVisited(first);
        assertEquals(3, store.preemptionEntryCount(),
            "markVisited(config) must not touch the preemption-aware entry count");
        assertEquals(1, store.size(), "markVisited(config) must count as one visited state");
    }

    /**
     * R5 — the recorded budget is the minimum, which is what makes the dominance query correct.
     *
     * <p>Marked at 7 then at 2, a query at 5 must report visited (2 <= 5) and a query at 1 must not.
     */
    @Test
    void preemptionAwareness_keepsTheMinimumRecordedBudget() {
        HashingStateStore store = new HashingStateStore();
        Configuration config = configurationAt(new CounterState(21), 1, 0);

        store.markVisited(config, 0, 7);
        assertTrue(store.isVisited(config, 0, 7), "exactly the recorded budget is visited");
        assertTrue(store.isVisited(config, 0, 99), "a larger budget is dominated by a smaller one");
        assertFalse(store.isVisited(config, 0, 6), "a smaller budget is not dominated by a larger one");

        store.markVisited(config, 0, 2);
        assertTrue(store.isVisited(config, 0, 2), "the lowered minimum is now the recorded budget");
        assertTrue(store.isVisited(config, 0, 5), "5 is dominated by the new minimum of 2");
        assertFalse(store.isVisited(config, 0, 1), "1 is below the new minimum");

        assertEquals(1, store.preemptionEntryCount(), "lowering the budget must not add an entry");
    }

    // ---------------------------------------------------------------- helpers

    /** Reachable configurations by value. {@code Configuration} has no {@code equals}. */
    private static List<Configuration> reachableConfigurations(List<ModelThread> threads,
                                                               Configuration initial) {
        List<Configuration> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<Configuration> queue = new ArrayDeque<>();
        queue.add(initial);
        while (!queue.isEmpty()) {
            Configuration c = queue.poll();
            if (!seen.add(configurationValueKey(c))) continue;
            out.add(c);
            for (int t = 0; t < threads.size(); t++) {
                if (c.programCounters().get(t) >= threads.get(t).steps().size()) continue;
                if (!threads.get(t).enabled(c.state())) continue;
                queue.add(c.successor(t, null, threads, c.state().deepCopy()));
            }
        }
        return out;
    }

    private static String configurationValueKey(Configuration c) {
        return c.state().toString() + "|" + c.programCounters();
    }

    private static boolean plainCollectionsPopulated(HashingStateStore store) {
        return !fieldSet(store, "visitedHashes").isEmpty()
            && !fieldSet(store, "visitedStates").isEmpty()
            && !fieldSet(store, "preemptionHashes").isEmpty()
            && !fieldMap(store, "minPreemptions").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> fieldSet(HashingStateStore store, String name) {
        try {
            Field f = HashingStateStore.class.getDeclaredField(name);
            f.setAccessible(true);
            return (Set<Integer>) f.get(store);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read HashingStateStore." + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> fieldMap(HashingStateStore store, String name) {
        try {
            Field f = HashingStateStore.class.getDeclaredField(name);
            f.setAccessible(true);
            return (Map<String, Integer>) f.get(store);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read HashingStateStore." + name, e);
        }
    }

    /**
     * Builds a configuration from a counter value and a per-thread position, reflecting the private
     * constructor. {@code Configuration} exposes only {@code initial} and {@code successor}, neither of
     * which can place a thread at an arbitrary position, and a fixture pinned to one program's traversal
     * order would break the moment that order changes.
     */
    private static Configuration configurationAt(CounterState state, int counter, int threadPosition) {
        List<Integer> counters = new ArrayList<>();
        counters.add(threadPosition);
        counters.add(0);
        return newConfiguration(state, counters, List.of(0, 1), false, false);
    }

    /**
     * Builds a configuration with explicit program counters, for the R4 colliding pair. The counters are
     * deliberately not plausible thread positions — see that test's comment.
     */
    private static Configuration configurationWithCounters(CounterState state, List<Integer> counters) {
        return newConfiguration(state, counters, List.of(0, 1), false, false);
    }

    private static Configuration newConfiguration(SharedState state, List<Integer> counters,
                                                  List<Integer> enabled, boolean allTerminated,
                                                  boolean deadlock) {
        try {
            var ctor = Configuration.class.getDeclaredConstructor(
                SharedState.class, List.class, Map.class, Map.class, List.class,
                boolean.class, boolean.class, StepOutcome.class);
            ctor.setAccessible(true);
            return ctor.newInstance(state, counters, new HashMap<>(), new HashMap<>(), enabled,
                allTerminated, deadlock, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build a Configuration fixture", e);
        }
    }
}