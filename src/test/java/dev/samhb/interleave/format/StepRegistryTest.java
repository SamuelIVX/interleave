package dev.samhb.interleave.format.registry;

import dev.samhb.interleave.core.Step;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StepRegistryTest {

    private final StepRegistry registry = new StepRegistry();

    @Test
    void allNineteenStepTypesRegistered() {
        assertEquals(19, registry.typeNames().size());
        
        // Peterson steps
        assertTrue(registry.typeNames().contains("write_flag"));
        assertTrue(registry.typeNames().contains("write_turn"));
        assertTrue(registry.typeNames().contains("busy_wait"));
        assertTrue(registry.typeNames().contains("read_flag"));
        assertTrue(registry.typeNames().contains("cs_enter"));
        assertTrue(registry.typeNames().contains("cs_exit"));
        
        // Counter steps
        assertTrue(registry.typeNames().contains("read_counter"));
        assertTrue(registry.typeNames().contains("write_counter"));
        
        // Pair steps
        assertTrue(registry.typeNames().contains("write_high"));
        assertTrue(registry.typeNames().contains("write_low"));
        assertTrue(registry.typeNames().contains("read_snapshot"));
        
        // Deadlock steps
        assertTrue(registry.typeNames().contains("deadlock_write_flag"));
        assertTrue(registry.typeNames().contains("unconditional_wait"));
        
        // DCL steps
        assertTrue(registry.typeNames().contains("dcl_lock"));
        assertTrue(registry.typeNames().contains("dcl_unlock"));
        assertTrue(registry.typeNames().contains("dcl_init"));
        assertTrue(registry.typeNames().contains("dcl_create_instance"));
        assertTrue(registry.typeNames().contains("dcl_read_instance"));
        assertTrue(registry.typeNames().contains("dcl_use_instance"));
    }

    @Test
    void createStep_unknownType_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "unknown_step");
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json, 0));
        assertTrue(ex.getMessage().contains("Unknown step type"));
    }

    @Test
    void createStep_validatesRequiredParams() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "write_turn");
        // missing required "value" param
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json, 0));
        assertTrue(ex.getMessage().contains("value"));
    }

    @Test
    void validateCompatibility_petersonStepsWithPetersonState_passes() {
        registry.validateCompatibility("write_flag", "peterson");
        registry.validateCompatibility("write_turn", "peterson");
        registry.validateCompatibility("busy_wait", "peterson");
        registry.validateCompatibility("read_flag", "peterson");
        registry.validateCompatibility("cs_enter", "peterson");
        registry.validateCompatibility("cs_exit", "peterson");
    }

    @Test
    void validateCompatibility_counterStepsWithCounterState_passes() {
        registry.validateCompatibility("read_counter", "counter");
        registry.validateCompatibility("write_counter", "counter");
    }

    @Test
    void validateCompatibility_pairStepsWithPairState_passes() {
        registry.validateCompatibility("write_high", "pair");
        registry.validateCompatibility("write_low", "pair");
        registry.validateCompatibility("read_snapshot", "pair");
    }

    @Test
    void validateCompatibility_deadlockStepsWithDeadlockState_passes() {
        registry.validateCompatibility("deadlock_write_flag", "deadlock");
        registry.validateCompatibility("unconditional_wait", "deadlock");
    }

    @Test
    void validateCompatibility_dclStepsWithDclState_passes() {
        registry.validateCompatibility("dcl_lock", "dcl");
        registry.validateCompatibility("dcl_unlock", "dcl");
        registry.validateCompatibility("dcl_init", "dcl");
        registry.validateCompatibility("dcl_create_instance", "dcl");
        registry.validateCompatibility("dcl_read_instance", "dcl");
        registry.validateCompatibility("dcl_use_instance", "dcl");
    }

    @Test
    void validateCompatibility_crossCompatibility_throws() {
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.validateCompatibility("write_counter", "peterson"));
        assertTrue(ex.getMessage().contains("not compatible"));
    }

    @Test
    void createStep_threadParamDefaultsToOwner() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "read_counter");
        
        // Create step with owning thread ID 1
        dev.samhb.interleave.core.Step step = registry.create(json, 1);
        assertNotNull(step);
        
        // Verify the step reads from the correct MemoryLocation
        // ReadCounterStep reads from "counter" MemoryLocation
        assertEquals("counter", step.reads().iterator().next().toString());
    }

    @Test
    void createStep_explicitThreadParamMatchesOwner() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "write_flag");
        json.addProperty("value", true);
        json.addProperty("thread", 1); // Explicit thread param matching owning thread ID
        
        dev.samhb.interleave.core.Step step = registry.create(json, 1);
        assertNotNull(step);
        
        // Verify the step uses the correct thread ID
        // WriteFlagStep writes to flag[threadId]
        assertTrue(step.writes().iterator().next().toString().contains("[1]"));
    }

    @Test
    void createStep_threadParamDefaultsToOwner_writeFlag() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "write_flag");
        json.addProperty("value", true);
        // No explicit thread param - should default to owning thread ID
        
        dev.samhb.interleave.core.Step step = registry.create(json, 1);
        assertNotNull(step);
        
        // Verify the step uses the owning thread ID (1) as writer ID
        assertTrue(step.writes().iterator().next().toString().contains("[1]"));
    }

    @Test
    void createStep_threadParamMismatch_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "read_counter");
        json.addProperty("thread", 5); // Doesn't match owning thread ID 0
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json, 0));
        assertTrue(ex.getMessage().contains("thread"));
    }

    @Test
    void createStep_otherParam_outOfRange() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "busy_wait");
        json.addProperty("other", 5); // Only 2 threads in typical programs
        
        // Note: other validation happens at load time, not in StepRegistry directly
        // The registry doesn't know thread count, so it doesn't validate 'other' here
        dev.samhb.interleave.core.Step step = registry.create(json, 0);
        assertNotNull(step);
    }
}