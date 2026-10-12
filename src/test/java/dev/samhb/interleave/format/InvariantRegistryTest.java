/** Built-in invariant factory behavior at live, terminal, and critical-section boundaries. */
package dev.samhb.interleave.format.registry;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.core.Configuration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;;;

class InvariantRegistryTest {

    private final InvariantRegistry registry = new InvariantRegistry();

    @Test
    void allFourInvariantTypesRegistered() {
        assertEquals(4, registry.typeNames().size());
        assertTrue(registry.typeNames().contains("mutual_exclusion_peterson"));
        assertTrue(registry.typeNames().contains("counter_equals"));
        assertTrue(registry.typeNames().contains("dcl_uninitialized_observed"));
        assertTrue(registry.typeNames().contains("torn_read"));
    }

    @Test
    void createInvariant_unknownType_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "unknown_invariant");

        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json));
        assertTrue(ex.getMessage().contains("Unknown invariant type"));
    }


    @Test
    void createMutualExclusionPeterson_missingParams_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "mutual_exclusion_peterson");
        // missing thread0_cs_pc

        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json));
        assertTrue(ex.getMessage().contains("thread0_cs_pc"));
    }

    /** False flags remain safe even with both counters in the critical-section zone. */
    @Test
    void mutualExclusionPeterson_holds_whenFlagsFalse() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "mutual_exclusion_peterson");
        json.addProperty("thread0_cs_pc", 3);
        json.addProperty("thread1_cs_pc", 2);
        Invariant invariant = registry.create(json);
        Configuration config = Configuration.forTest(PetersonState.of(false, false, 0),
            List.of(3, 2), List.of(4, 3), List.of(0, 1));
        assertTrue(invariant.holds(config.state(), config));
    }

    /** Each false flag independently prevents a violation inside both critical-section zones. */
    @Test
    void mutualExclusionPeterson_holds_whenOneFlagFalse() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "mutual_exclusion_peterson");
        json.addProperty("thread0_cs_pc", 3);
        json.addProperty("thread1_cs_pc", 2);
        Invariant invariant = registry.create(json);
        for (PetersonState state : List.of(PetersonState.of(true, false, 0), PetersonState.of(false, true, 0))) {
            Configuration config = Configuration.forTest(state, List.of(3, 2), List.of(4, 3), List.of(0, 1));
            assertTrue(invariant.holds(state, config));
        }
    }

    /** The exact threshold for both counters must expose simultaneous entry. */
    @Test
    void mutualExclusionPeterson_violation_whenBothFlagsTrueAndBothInCS() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "mutual_exclusion_peterson");
        json.addProperty("thread0_cs_pc", 3);
        json.addProperty("thread1_cs_pc", 2);
        Invariant invariant = registry.create(json);
        Configuration config = Configuration.forTest(PetersonState.of(true, true, 0),
            List.of(3, 2), List.of(4, 3), List.of(0, 1));
        assertFalse(invariant.holds(config.state(), config));
    }

    @Test
    void mutualExclusionPeterson_holds_whenBothFlagsTrueButNotBothInCS() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "mutual_exclusion_peterson");
        json.addProperty("thread0_cs_pc", 3);
        json.addProperty("thread1_cs_pc", 2);

        Invariant invariant = registry.create(json);
        for (List<Integer> pcs : List.of(List.of(2, 2), List.of(3, 1))) {
            Configuration config = Configuration.forTest(PetersonState.of(true, true, 0),
                pcs, List.of(5, 4), List.of(0, 1));
            assertTrue(invariant.holds(config.state(), config));
        }
        Configuration after = Configuration.forTest(PetersonState.of(true, true, 0),
            List.of(4, 3), List.of(5, 4), List.of(0, 1));
        assertFalse(invariant.holds(after.state(), after), "the CS zone includes counters past its boundary");
    }

    /**
     * Verifies that invalid shapes for the {@code cs_pcs} array are rejected:
     * non-array parameters, arrays with fewer than 2 entries, non-numeric values,
     * fractional floats, and out-of-range integers.
     */
    @Test
    void mutualExclusionPeterson_csPcs_validation() {
        var notArr = new com.google.gson.JsonObject();
        notArr.addProperty("type", "mutual_exclusion_peterson");
        notArr.addProperty("cs_pcs", 123);
        RegistryException ex1 = assertThrows(RegistryException.class, () -> registry.create(notArr));
        assertTrue(ex1.getMessage().contains("must be an array"));

        var tooShort = new com.google.gson.JsonObject();
        tooShort.addProperty("type", "mutual_exclusion_peterson");
        var shortArr = new com.google.gson.JsonArray();
        shortArr.add(1);
        tooShort.add("cs_pcs", shortArr);
        RegistryException ex2 = assertThrows(RegistryException.class, () -> registry.create(tooShort));
        assertTrue(ex2.getMessage().contains("at least 2 entries"));

        var notNum = new com.google.gson.JsonObject();
        notNum.addProperty("type", "mutual_exclusion_peterson");
        var badArr = new com.google.gson.JsonArray();
        badArr.add(1);
        badArr.add("not-a-number");
        notNum.add("cs_pcs", badArr);
        RegistryException ex3 = assertThrows(RegistryException.class, () -> registry.create(notNum));
        assertTrue(ex3.getMessage().contains("must be an integer at index 1"));

        var fractional = new com.google.gson.JsonObject();
        fractional.addProperty("type", "mutual_exclusion_peterson");
        var fracArr = new com.google.gson.JsonArray();
        fracArr.add(1);
        fracArr.add(1.9);
        fractional.add("cs_pcs", fracArr);
        RegistryException ex4 = assertThrows(RegistryException.class, () -> registry.create(fractional));
        assertTrue(ex4.getMessage().contains("must be an in-range integer at index 1"));

        var overflow = new com.google.gson.JsonObject();
        overflow.addProperty("type", "mutual_exclusion_peterson");
        var overArr = new com.google.gson.JsonArray();
        overArr.add(1);
        overArr.add(new java.math.BigDecimal("-2147483649"));
        overflow.add("cs_pcs", overArr);
        RegistryException ex5 = assertThrows(RegistryException.class, () -> registry.create(overflow));
        assertTrue(ex5.getMessage().contains("must be an in-range integer at index 1"));
    }

    /**
     * Verifies mutual exclusion evaluation for three threads configured with {@code cs_pcs}:
     * holds when 0 or 1 thread is in the critical section, and violates when two threads
     * (including thread 2) or all three threads are in their critical sections simultaneously.
     */
    @Test
    void mutualExclusionPeterson_threeThreads_withCsPcs() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "mutual_exclusion_peterson");
        var arr = new com.google.gson.JsonArray();
        arr.add(2);
        arr.add(2);
        arr.add(2);
        json.add("cs_pcs", arr);
        Invariant invariant = registry.create(json);

        // PetersonState for 3 threads: flags [t0, t1, t2]
        PetersonState sNone = PetersonState.of(new boolean[]{false, false, false}, 0);
        Configuration cNone = Configuration.forTest(sNone, List.of(2, 2, 2), List.of(3, 3, 3), List.of(0, 1, 2));
        assertTrue(invariant.holds(sNone, cNone));

        PetersonState sOne = PetersonState.of(new boolean[]{true, false, false}, 0);
        Configuration cOne = Configuration.forTest(sOne, List.of(2, 2, 2), List.of(3, 3, 3), List.of(0, 1, 2));
        assertTrue(invariant.holds(sOne, cOne));

        // Two in CS -> violation (threads 1 and 2 in CS, ensuring thread 2 is checked)
        PetersonState sTwo = PetersonState.of(new boolean[]{false, true, true}, 0);
        Configuration cTwo = Configuration.forTest(sTwo, List.of(1, 2, 2), List.of(3, 3, 3), List.of(0, 1, 2));
        assertFalse(invariant.holds(sTwo, cTwo));

        // All three in CS -> violation
        PetersonState sThree = PetersonState.of(new boolean[]{true, true, true}, 0);
        Configuration cThree = Configuration.forTest(sThree, List.of(2, 2, 2), List.of(3, 3, 3), List.of(0, 1, 2));
        assertFalse(invariant.holds(sThree, cThree));
    }


    @Test
    void counterEquals_holds_whenNotTerminated() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "counter_equals");
        json.addProperty("expected", 2);

        Invariant invariant = registry.create(json);

        CounterState state = CounterState.of(5);
        // Create a configuration with non-terminated threads
        Configuration config = Configuration.initial(state, List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0))),
            new ModelThread(1, List.of(new WriteCounterStep(1)))
        ));

        assertTrue(invariant.holds(state, config));
    }

    /** Compares the actual counter against the configured expectation after both threads terminate. */
    @Test
    void counterEquals_holds_whenTerminatedAndCounterMatches() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "counter_equals");
        json.addProperty("expected", 2);
        Invariant invariant = registry.create(json);
        CounterState state = CounterState.of(2);
        Configuration config = Configuration.forTest(state, List.of(1, 1), List.of(1, 1), List.of());
        assertTrue(config.allTerminated(), "fixture must reach the terminal-only comparison");
        assertTrue(invariant.holds(state, config));
    }

    /** Compares the actual counter against the configured expectation after both threads terminate. */
    @Test
    void counterEquals_violation_whenTerminatedAndCounterMismatch() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "counter_equals");
        json.addProperty("expected", 2);
        Invariant invariant = registry.create(json);
        CounterState state = CounterState.of(1);
        Configuration config = Configuration.forTest(state, List.of(1, 1), List.of(1, 1), List.of());
        assertTrue(config.allTerminated(), "fixture must reach the terminal-only comparison");
        assertFalse(invariant.holds(state, config));
    }


    @Test
    void dclUninitializedObserved_holds_whenNotInitializedAndObservedNull() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "dcl_uninitialized_observed");

        Invariant invariant = registry.create(json);

        DclState state = DclState.of(false); // initialized=false, instance=null, observedInstance=null
        Configuration config = Configuration.initial(state, List.of(
            new ModelThread(0, List.of()),
            new ModelThread(1, List.of())
        ));

        assertTrue(invariant.holds(state, config));
    }

    @Test
    void dclUninitializedObserved_holds_whenInitialized() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "dcl_uninitialized_observed");

        Invariant invariant = registry.create(json);

        // Create a DclState with initialized=true
        DclState state = new DclState(true);
        state.setInstance(new Object());
        state.setObservedInstance(new Object());

        Configuration config = Configuration.initial(state, List.of(
            new ModelThread(0, List.of()),
            new ModelThread(1, List.of())
        ));

        assertTrue(invariant.holds(state, config));
    }

    @Test
    void dclUninitializedObserved_violation_whenObservedNonNullAndNotInitialized() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "dcl_uninitialized_observed");

        Invariant invariant = registry.create(json);

        // Create a DclState with initialized=false but observedInstance != null
        DclState state = new DclState(false);
        state.setInitialized(false);
        state.setInstance(null);
        state.setObservedInstance(new Object()); // T1 observed instance before initialization

        Configuration config = Configuration.initial(state, List.of(
            new ModelThread(0, List.of()),
            new ModelThread(1, List.of())
        ));

        assertFalse(invariant.holds(state, config));
    }


    @Test
    void tornRead_holds_whenNoObservation() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "torn_read");
        json.addProperty("high_value", 2);
        json.addProperty("low_value", 2);

        Invariant invariant = registry.create(json);

        PairState state = PairState.of(0, 0); // No observation yet
        Configuration config = Configuration.initial(state, List.of(
            new ModelThread(0, List.of()),
            new ModelThread(1, List.of())
        ));

        assertTrue(invariant.holds(state, config));
    }

    @Test
    void tornRead_holds_whenObservationMatches() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "torn_read");
        json.addProperty("high_value", 2);
        json.addProperty("low_value", 2);

        Invariant invariant = registry.create(json);

        // Use recordObservation to set observed values
        PairState state = new PairState(2, 2);
        state.recordObservation(2, 2);

        Configuration config = Configuration.initial(state, List.of(
            new ModelThread(0, List.of()),
            new ModelThread(1, List.of())
        ));

        assertTrue(invariant.holds(state, config));
    }

    @Test
    void tornRead_violation_whenHighMatchesButLowDoesNot() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "torn_read");
        json.addProperty("high_value", 2);
        json.addProperty("low_value", 2);

        Invariant invariant = registry.create(json);

        // Torn read: observed high=2 (matches), observed low=0 (doesn't match)
        PairState state = new PairState(2, 2);
        state.recordObservation(2, 0); // Torn!

        Configuration config = Configuration.initial(state, List.of(
            new ModelThread(0, List.of()),
            new ModelThread(1, List.of())
        ));

        assertFalse(invariant.holds(state, config));
    }

    @Test
    void validateCompatibility_passesForMatchingState() {
        registry.validateCompatibility("mutual_exclusion_peterson", "peterson");
        registry.validateCompatibility("counter_equals", "counter");
        registry.validateCompatibility("dcl_uninitialized_observed", "dcl");
        registry.validateCompatibility("torn_read", "pair");
    }

    @Test
    void validateCompatibility_throwsForMismatch() {
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.validateCompatibility("counter_equals", "peterson"));
        assertTrue(ex.getMessage().contains("not compatible"));
    }

    @Test
    void validateCompatibility_unknownInvariantType_throws() {
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.validateCompatibility("unknown_type", "peterson"));
        assertTrue(ex.getMessage().contains("Unknown invariant type"));
    }
}
