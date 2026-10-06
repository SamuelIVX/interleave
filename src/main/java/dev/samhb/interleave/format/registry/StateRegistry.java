/** Registry of built-in state types. */
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
    /** Factories. */
    private final Map<String, StateFactory> factories = new HashMap<>();

    /** Creates state registry from the supplied values. */
    public StateRegistry() {
        registerBuiltins();
    }

    /** Registers the supported built-in factories by their declarative names. */
    private void registerBuiltins() {
        // peterson: one flag per thread, turn int
        register("peterson", (json, threadCount) -> {
            com.google.gson.JsonArray flagsArray = JsonHelper.getArray(json, "flags", "peterson");
            requireFlagLength(flagsArray.size(), threadCount, "peterson");
            boolean[] flags = new boolean[threadCount];
            for (int i = 0; i < threadCount; i++) {
                flags[i] = JsonHelper.getBoolFromArray(flagsArray, i, "peterson");
            }
            int turn = JsonHelper.getInt(json, "turn", "peterson");
            return PetersonState.of(flags, turn);
        });

        // counter: counter int, one register per thread
        register("counter", (json, threadCount) -> {
            int counter = JsonHelper.getInt(json, "counter", "counter");
            return CounterState.of(counter, threadCount);
        });

        // dcl: initialized bool
        register("dcl", (json, threadCount) -> {
            boolean initialized = JsonHelper.getBool(json, "initialized", "dcl");
            return DclState.of(initialized);
        });

        // deadlock: one flag per thread
        register("deadlock", (json, threadCount) -> {
            com.google.gson.JsonArray flagsArray = JsonHelper.getArray(json, "flags", "deadlock");
            requireFlagLength(flagsArray.size(), threadCount, "deadlock");
            boolean[] flags = new boolean[threadCount];
            for (int i = 0; i < threadCount; i++) {
                flags[i] = JsonHelper.getBoolFromArray(flagsArray, i, "deadlock");
            }
            return DeadlockState.of(flags);
        });

        // pair: high int, low int
        register("pair", (json, threadCount) -> {
            int high = JsonHelper.getInt(json, "high", "pair");
            int low = JsonHelper.getInt(json, "low", "pair");
            return PairState.of(high, low);
        });
    }

    /**
     * A state's per-thread array must have one entry per program thread.
     *
     * <p>Peterson and the deadlock demo are defined for two or more threads, so fewer than two is
     * rejected rather than quietly padded. More is the case this spec opened up.
     * @param flags supplied per-thread intent flags
     * @param threadCount number of modeled threads the flag array must represent
     * @param type state type used in validation diagnostics
     */
    private static void requireFlagLength(int flags, int threadCount, String type) {
        if (threadCount < 2) {
            throw new RegistryException(type + " requires at least 2 threads, got " + threadCount);
        }
        if (flags != threadCount) {
            throw new RegistryException(type + " state 'flags' must have one element per thread: got "
                    + flags + " for " + threadCount + " threads");
        }
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
     * @return live mutable view of registered names; removals also remove their factories
     */
    public Set<String> typeNames() {
        return factories.keySet();
    }

    /**
     * Creates a shared state from the JSON object, sized to the program's thread count.
     *
     * <p>The thread count is a required argument rather than something read from the state JSON. A
     * state's per-thread arrays have to match the threads that will index them, and making the caller
     * state the count means a mismatch is reported instead of defaulted away — this replaced a
     * one-argument form whose absence of the count silently capped peterson, deadlock and counter at
     * two threads.
     *
     * @param stateJson the JSON object containing {@code type} and type-specific fields
     * @param threadCount the number of threads the program declares
     * @return the constructed {@link SharedState}
     * @throws RegistryException if the type is unknown or parameters are invalid
     */
    public SharedState create(JsonObject stateJson, int threadCount) {
        String type = stateJson.get("type").getAsString();
        StateFactory factory = factories.get(type);
        if (factory == null) {
            throw new RegistryException("Unknown state type '" + type + "'. Registered types: " + factories.keySet());
        }
        return factory.create(stateJson, threadCount);
    }

    /**
     * Returns the set of state types compatible with the given state type.
     * (For compatibility checking with step types.)
     * @param stepType registered factory name
     * @param stateType expected shared-state implementation
     * @return true if the step’s expected state type accepts the registered state type
     */
    public boolean isCompatibleWith(String stepType, String stateType) {
        // This is used by StepRegistry for cross-validation
        return true; // Compatibility is enforced by StepRegistry
    }
}
