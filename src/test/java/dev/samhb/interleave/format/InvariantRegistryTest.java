package dev.samhb.interleave.format.registry;

import dev.samhb.interleave.search.Invariant;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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
    void createMutualExclusionPeterson_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "mutual_exclusion_peterson");
        json.addProperty("thread0_cs_pc", 3);
        json.addProperty("thread1_cs_pc", 2);
        
        Invariant invariant = registry.create(json);
        assertNotNull(invariant);
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

    @Test
    void createCounterEquals_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "counter_equals");
        json.addProperty("expected", 2);
        
        Invariant invariant = registry.create(json);
        assertNotNull(invariant);
    }

    @Test
    void createDclUninitializedObserved_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "dcl_uninitialized_observed");
        
        Invariant invariant = registry.create(json);
        assertNotNull(invariant);
    }

    @Test
    void createTornRead_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "torn_read");
        json.addProperty("high_value", 2);
        json.addProperty("low_value", 2);
        
        Invariant invariant = registry.create(json);
        assertNotNull(invariant);
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