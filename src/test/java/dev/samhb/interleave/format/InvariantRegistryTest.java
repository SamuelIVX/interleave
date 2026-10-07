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
