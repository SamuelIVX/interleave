package dev.samhb.interleave.format.registry;

import com.google.gson.JsonObject;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.PetersonState;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.DclState;
import dev.samhb.interleave.core.DeadlockState;
import dev.samhb.interleave.core.PairState;
import dev.samhb.interleave.format.registry.JsonHelper;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Registry of built-in state types.
 * <p>
 * Maps state type names (e.g., "peterson", "counter") to factory functions.
 * Each factory knows how to construct the corresponding {@link SharedState}
 * from the JSON parameters.
 */
public final class StateRegistry {
    private final Map<String, StateFactory> factories = new HashMap<>();

    public StateRegistry() {
        registerBuiltins();
    }

    private void registerBuiltins() {
        // peterson: flags [bool, bool], turn int
        register("peterson", json -> {
            com.google.gson.JsonArray flagsArray = JsonHelper.getArray(json, "flags", "peterson");
            if (flagsArray.size() != 2) {
                throw new RegistryException("peterson state 'flags' must have exactly 2 elements");
            }
            boolean flag0 = JsonHelper.getBoolFromArray(flagsArray, 0, "peterson");
            boolean flag1 = JsonHelper.getBoolFromArray(flagsArray, 1, "peterson");
            int turn = JsonHelper.getInt(json, "turn", "peterson");
            return PetersonState.of(flag0, flag1, turn);
        });

        // counter: counter int
        register("counter", json -> {
            int counter = JsonHelper.getInt(json, "counter", "counter");
            return CounterState.of(counter);
        });

        // dcl: initialized bool
        register("dcl", json -> {
            boolean initialized = JsonHelper.getBool(json, "initialized", "dcl");
            return DclState.of(initialized);
        });

        // deadlock: flags [bool, bool]
        register("deadlock", json -> {
            com.google.gson.JsonArray flagsArray = JsonHelper.getArray(json, "flags", "deadlock");
            if (flagsArray.size() != 2) {
                throw new RegistryException("deadlock state 'flags' must have exactly 2 elements");
            }
            boolean flag0 = JsonHelper.getBoolFromArray(flagsArray, 0, "deadlock");
            boolean flag1 = JsonHelper.getBoolFromArray(flagsArray, 1, "deadlock");
            return DeadlockState.of(flag0, flag1);
        });

        // pair: high int, low int
        register("pair", json -> {
            int high = JsonHelper.getInt(json, "high", "pair");
            int low = JsonHelper.getInt(json, "low", "pair");
            return PairState.of(high, low);
        });
    }

    /**
     * Registers a state type.
     *
     * @param typeName the state type name
     * @param factory the factory function
     */
    public void register(String typeName, StateFactory factory) {
        factories.put(typeName, factory);
    }

    /**
     * Returns the set of registered state type names.
     */
    public Set<String> typeNames() {
        return factories.keySet();
    }

    /**
     * Creates a shared state from the JSON object.
     *
     * @param stateJson the JSON object containing {@code type} and type-specific fields
     * @return the constructed {@link SharedState}
     * @throws RegistryException if the type is unknown or parameters are invalid
     */
    public SharedState create(JsonObject stateJson) {
        String type = stateJson.get("type").getAsString();
        StateFactory factory = factories.get(type);
        if (factory == null) {
            throw new RegistryException("Unknown state type '" + type + "'. Registered types: " + factories.keySet());
        }
        return factory.create(stateJson);
    }

    /**
     * Returns the set of state types compatible with the given state type.
     * (For compatibility checking with step types.)
     */
    public boolean isCompatibleWith(String stepType, String stateType) {
        // This is used by StepRegistry for cross-validation
        return true; // Compatibility is enforced by StepRegistry
    }
}