package dev.samhb.interleave.state;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.StateStore;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StateStorePreemptionTest {

    private static Program twoThreadProgram() {
        ModelThread t0 = new ModelThread(0, List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)));
        ModelThread t1 = new ModelThread(1, List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)));
        return new Program(PetersonState.of(false, false, 0), List.of(t0, t1));
    }

    // Distinct probe configurations derived from a shared program, so that marking one and
    // probing another exercises the prefilter rather than always hitting the exact key.
    private static List<Configuration> distinctConfigs(Program program) {
        List<Configuration> configs = new ArrayList<>();
        configs.add(program.initialConfiguration());
        SharedState s1 = program.initialConfiguration().state().deepCopy();
        Configuration c = program.initialConfiguration().successor(0, StepOutcome.ADVANCED, program.threads(), s1);
        configs.add(c);
        SharedState s2 = c.state().deepCopy();
        configs.add(c.successor(1, StepOutcome.ADVANCED, program.threads(), s2));
        SharedState s3 = configs.get(2).state().deepCopy();
        configs.add(configs.get(2).successor(0, StepOutcome.ADVANCED, program.threads(), s3));
        return configs;
    }

    @Test
    void legacyStore_preemptionAwareCalls_failFast() {
        StateStore legacy = new StateStore() {
            @Override public boolean isVisited(Configuration config) { return false; }
            @Override public void markVisited(Configuration config) { }
            @Override public void clear() { }
        };
        Program program = twoThreadProgram();
        Configuration config = program.initialConfiguration();

        // Delegating to the single-argument form would prune states reached at a different budget,
        // so a store that cannot represent preemption counts must fail loudly instead.
        assertThrows(UnsupportedOperationException.class,
            () -> legacy.isVisited(config, 0, 1));
        assertThrows(UnsupportedOperationException.class,
            () -> legacy.markVisited(config, 0, 1));
    }

    @Test
    void hashingStore_markThenProbe_sameBudget_returnsTrue() {
        HashingStateStore store = new HashingStateStore();
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        store.markVisited(config, 0, 2);
        assertTrue(store.isVisited(config, 0, 2));
    }

    @Test
    void hashingStore_key_includesLastThreadId() {
        HashingStateStore store = new HashingStateStore();
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        store.markVisited(config, 0, 1);
        assertFalse(store.isVisited(config, 1, 1),
            "Same configuration reached by a different last thread must not be pruned");
    }

    @Test
    void hashingStore_stateAtHigherBudgetDoesNotBlockLowerBudget() {
        HashingStateStore store = new HashingStateStore();
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        // Reached using 2 preemptions, so it was explored with *less* remaining budget than a
        // query at 1 would have. That earlier search is not a superset of ours, so we must not
        // prune -- otherwise a higher-K iteration would silently explore less than a lower one.
        store.markVisited(config, 0, 2);
        assertFalse(store.isVisited(config, 0, 1));
    }

    @Test
    void hashingStore_stateAtLowerBudgetBlocksHigherBudget() {
        HashingStateStore store = new HashingStateStore();
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        // Reached using 1 preemption, so it was explored with *more* remaining budget than a
        // query at 2 has. That search already covered everything we could still reach, so prune.
        store.markVisited(config, 0, 1);
        assertTrue(store.isVisited(config, 0, 2));
    }

    @Test
    void hashingStore_prefilterMiss_doesNotBuildExactKey() {
        HashingStateStore store = new HashingStateStore();
        List<Configuration> configs = distinctConfigs(twoThreadProgram());

        // A pair never marked must answer from the hash prefilter alone. Exercised indirectly:
        // the prefilter and the exact key are populated by the same markVisited call, so a probe
        // that misses the prefilter while the exact key contains the entry would mean the two
        // structures had desynchronized.
        store.markVisited(configs.get(1), 0, 1);
        assertTrue(store.isVisited(configs.get(1), 0, 1), "prefilter hit must reach the exact key");
        assertFalse(store.isVisited(configs.get(2), 0, 1), "unmarked pair must miss the prefilter");
    }

    @Test
    void hashingStore_singleArgAndPreemptionPaths_doNotDesynchronize() {
        HashingStateStore store = new HashingStateStore();
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        // The two APIs hash different things, so they must keep separate prefilter sets. If they
        // shared one, marking through one overload would poison or invalidate the other's misses.
        store.markVisited(config);
        store.markVisited(config, 1, 2);

        assertTrue(store.isVisited(config), "single-argument mark still visible");
        assertTrue(store.isVisited(config, 1, 2), "preemption mark still visible");
        assertFalse(store.isVisited(config, 0, 2), "different last thread unaffected");
    }

    @Test
    void hashingStore_clear_clearsBothPaths() {
        HashingStateStore store = new HashingStateStore();
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        store.markVisited(config);
        store.markVisited(config, 0, 1);
        store.clear();

        assertEquals(0, store.size());
        assertEquals(0, store.preemptionEntryCount());
        assertFalse(store.isVisited(config));
        assertFalse(store.isVisited(config, 0, 1));
    }

    @Test
    void hashingStore_minCountMap_matchesReferenceSet_randomized() {
        // The store answers "visited at budget p" with a single stored minimum. This asserts that
        // is equivalent to the semantics it replaces: "some recorded q satisfies q <= p".
        Random random = new Random(20260930L);
        Program program = twoThreadProgram();
        List<Configuration> configs = distinctConfigs(program);

        for (int trial = 0; trial < 200; trial++) {
            HashingStateStore store = new HashingStateStore();
            // Reference: every (config, lastThreadId, preemption) triple actually marked.
            Set<List<Integer>> reference = new HashSet<>();

            int ops = 1 + random.nextInt(25);
            for (int op = 0; op < ops; op++) {
                int ci = random.nextInt(configs.size());
                int tid = random.nextInt(2);
                int p = random.nextInt(4);
                Configuration config = configs.get(ci);

                if (random.nextBoolean()) {
                    store.markVisited(config, tid, p);
                    reference.add(List.of(ci, tid, p));
                }

                for (int probeP = 0; probeP < 4; probeP++) {
                    final int budget = probeP;
                    boolean expected = reference.stream()
                        .anyMatch(e -> e.get(0) == ci && e.get(1) == tid && e.get(2) <= budget);
                    assertEquals(expected, store.isVisited(config, tid, budget),
                        "trial " + trial + ": config=" + ci + " lastThread=" + tid
                        + " probeBudget=" + budget + " recorded=" + reference);
                }
            }
        }
    }

    @Test
    void bitstateStore_constructor_allocatesOneArrayPerPreemptionLevel() {
        BitstateStore store = new BitstateStore(1_000, 4, 3);
        assertEquals(3, store.maxPreemptions());
    }

    @Test
    void bitstateStore_negativeMaxPreemptions_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BitstateStore(1_000, 4, -1));
    }

    @Test
    void bitstateStore_keepsPreemptionLevelsIndependent() {
        BitstateStore store = new BitstateStore(1_000_003, 4, 3);
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        store.markVisited(config, 0, 1);
        assertTrue(store.isVisited(config, 0, 1));

        // Reached using 1 preemption, so it was explored with more budget remaining than a query
        // at 2 has. That search already covered everything reachable from here, so prune -- the
        // same min<=p rule HashingStateStore implements with a stored minimum.
        assertTrue(store.isVisited(config, 0, 2),
            "a mark at a lower level must be visible at a higher budget");

        // The reverse must not hold: a state only seen at 2 says nothing about a query at 1,
        // which has less budget than the earlier search had remaining.
        BitstateStore other = new BitstateStore(1_000_003, 4, 3);
        other.markVisited(config, 0, 2);
        assertFalse(other.isVisited(config, 0, 1),
            "a mark at a higher level must not be visible at a lower budget");
    }

    @Test
    void bitstateStore_matchesHashingStoreSemantics() {
        // The two stores must answer the same interface method the same way. Anything else makes
        // a search's behaviour depend on which store the caller happened to pass.
        BitstateStore bitstate = new BitstateStore(1_000_003, 4, 3);
        HashingStateStore hashing = new HashingStateStore();
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        for (int[] mark : new int[][] {{0, 0}, {1, 0}, {1, 1}, {2, 0}}) {
            bitstate.markVisited(config, mark[1], mark[0]);
            hashing.markVisited(config, mark[1], mark[0]);
        }
        for (int probe = 0; probe <= 3; probe++) {
            assertEquals(hashing.isVisited(config, 0, probe), bitstate.isVisited(config, 0, probe),
                "stores disagree at budget " + probe);
        }
    }

    @Test
    void bitstateStore_isVisited_aboveCapacity_throws() {
        // The range check applies to reads as well as writes: silently answering false for an
        // unrepresentable budget would turn a capacity mismatch into a bogus "not visited".
        BitstateStore store = new BitstateStore(1_000, 4, 2);
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);
        assertThrows(IllegalArgumentException.class, () -> store.isVisited(config, 0, 3));
    }

    @Test
    void bitstateStore_preemptionCountAboveCapacity_throws() {
        BitstateStore store = new BitstateStore(1_000, 4, 2);
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> store.markVisited(config, 0, 3));
        assertTrue(e.getMessage().contains("maxPreemptions"),
            "message should point at the constructor that fixes this: " + e.getMessage());
    }

    @Test
    void bitstateStore_freshCopy_preservesMaxPreemptions() {
        // Losing capacity here would under-report states explored for K > 2 and then trip the
        // explorer's capacity assertion -- silent in the metrics, loud only in the exception.
        BitstateStore original = new BitstateStore(1_000, 4, 5);
        StateStore copy = original.freshCopy();
        assertInstanceOf(BitstateStore.class, copy);
        assertEquals(5, ((BitstateStore) copy).maxPreemptions());
    }

    @Test
    void bitstateStore_twoArgConstructor_defaultsCapacityToTwo() {
        assertEquals(2, new BitstateStore(1_000, 4).maxPreemptions());
    }

    @Test
    void bitstateStore_clear_resetsPreemptionBitsets() {
        BitstateStore store = new BitstateStore(1_000_003, 4, 2);
        Configuration config = distinctConfigs(twoThreadProgram()).get(1);

        store.markVisited(config, 0, 0);
        assertEquals(1, store.preemptionStatesMarked());
        store.clear();
        assertEquals(0, store.preemptionStatesMarked());
        assertFalse(store.isVisited(config, 0, 0));
    }
}
