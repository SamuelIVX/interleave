package dev.samhb.interleave.format.registry;

import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.PetersonState;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.DclState;
import dev.samhb.interleave.core.DeadlockState;
import dev.samhb.interleave.core.PairState;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StateRegistryTest {

    private final StateRegistry registry = new StateRegistry();

    @Test
    void allFiveStateTypesRegistered() {
        assertEquals(5, registry.typeNames().size());
        assertTrue(registry.typeNames().contains("peterson"));
        assertTrue(registry.typeNames().contains("counter"));
        assertTrue(registry.typeNames().contains("dcl"));
        assertTrue(registry.typeNames().contains("deadlock"));
        assertTrue(registry.typeNames().contains("pair"));
    }

    @Test
    void createState_unknownType_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "unknown_state");
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json));
        assertTrue(ex.getMessage().contains("Unknown state type"));
    }

    @Test
    void createPetersonState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "peterson");
        com.google.gson.JsonArray flags = new com.google.gson.JsonArray();
        flags.add(false);
        flags.add(false);
        json.add("flags", flags);
        json.addProperty("turn", 0);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json);
        assertTrue(state instanceof dev.samhb.interleave.core.PetersonState);
        dev.samhb.interleave.core.PetersonState ps = (dev.samhb.interleave.core.PetersonState) state;
        assertFalse(ps.flag(0));
        assertFalse(ps.flag(1));
        assertEquals(0, ps.turn());
    }

    @Test
    void createPetersonState_missingFlags_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "peterson");
        json.addProperty("turn", 0);
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json));
        assertTrue(ex.getMessage().contains("flags"));
    }

    @Test
    void createPetersonState_wrongFlagsSize_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "peterson");
        com.google.gson.JsonArray flags = new com.google.gson.JsonArray();
        flags.add(false);
        json.add("flags", flags);
        json.addProperty("turn", 0);
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json));
        assertTrue(ex.getMessage().contains("exactly 2 elements"));
    }

    @Test
    void createCounterState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "counter");
        json.addProperty("counter", 5);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json);
        assertTrue(state instanceof dev.samhb.interleave.core.CounterState);
        dev.samhb.interleave.core.CounterState cs = (dev.samhb.interleave.core.CounterState) state;
        assertEquals(5, cs.counter());
    }

    @Test
    void createDclState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "dcl");
        json.addProperty("initialized", true);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json);
        assertTrue(state instanceof dev.samhb.interleave.core.DclState);
        dev.samhb.interleave.core.DclState ds = (dev.samhb.interleave.core.DclState) state;
        assertTrue(ds.initialized());
    }

    @Test
    void createDeadlockState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "deadlock");
        com.google.gson.JsonArray flags = new com.google.gson.JsonArray();
        flags.add(true);
        flags.add(false);
        json.add("flags", flags);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json);
        assertTrue(state instanceof dev.samhb.interleave.core.DeadlockState);
        dev.samhb.interleave.core.DeadlockState ds = (dev.samhb.interleave.core.DeadlockState) state;
        assertTrue(ds.flag(0));
        assertFalse(ds.flag(1));
    }

    @Test
    void createPairState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "pair");
        json.addProperty("high", 10);
        json.addProperty("low", 20);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json);
        assertTrue(state instanceof dev.samhb.interleave.core.PairState);
        dev.samhb.interleave.core.PairState ps = (dev.samhb.interleave.core.PairState) state;
        assertEquals(10, ps.high());
        assertEquals(20, ps.low());
    }
}