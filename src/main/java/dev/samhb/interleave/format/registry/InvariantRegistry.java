package dev.samhb.interleave.format.registry;

import com.google.gson.JsonObject;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.core.PetersonState;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.DclState;
import dev.samhb.interleave.core.PairState;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.format.registry.JsonHelper;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

/**
 * Registry of built-in invariant types.
 * <p>
 * Maps invariant type names to factory functions that create the corresponding
 * {@link Invariant} lambdas. Each invariant type is tied to a specific state type.
 */
public final class InvariantRegistry {
    private final Map<String, InvariantFactory> factories = new HashMap<>();
    private final Map<String, Set<String>> compatMap = new HashMap<>();

    public InvariantRegistry() {
        registerBuiltins();
    }

    private void registerBuiltins() {
        // mutual_exclusion_peterson: thread0_cs_pc, thread1_cs_pc
        register("mutual_exclusion_peterson", Set.of("peterson"), json -> {
            int t0Pc = JsonHelper.getInt(json, "thread0_cs_pc", "mutual_exclusion_peterson");
            int t1Pc = JsonHelper.getInt(json, "thread1_cs_pc", "mutual_exclusion_peterson");
            return (state, config) -> {
                PetersonState ps = (PetersonState) state;
                List<Integer> pcs = config.programCounters();
                boolean bothFlagsTrue = ps.flag(0) && ps.flag(1);
                boolean t0InCsZone = pcs.get(0) >= t0Pc;
                boolean t1InCsZone = pcs.get(1) >= t1Pc;
                return !(bothFlagsTrue && t0InCsZone && t1InCsZone);
            };
        }, "peterson");

        // counter_equals: expected int
        register("counter_equals", Set.of("counter"), json -> {
            int expected = JsonHelper.getInt(json, "expected", "counter_equals");
            return (state, config) -> {
                CounterState cs = (CounterState) state;
                if (config.allTerminated()) {
                    return cs.counter() == expected;
                }
                return true;
            };
        }, "counter");

        // dcl_uninitialized_observed: no params
        register("dcl_uninitialized_observed", Set.of("dcl"), json -> {
            return (state, config) -> {
                DclState ds = (DclState) state;
                if (ds.observedInstance() != null && !ds.initialized()) {
                    return false;
                }
                return true;
            };
        }, "dcl");

        // torn_read: high_value, low_value
        register("torn_read", Set.of("pair"), json -> {
            int highValue = JsonHelper.getInt(json, "high_value", "torn_read");
            int lowValue = JsonHelper.getInt(json, "low_value", "torn_read");
            return (state, config) -> {
                PairState ps = (PairState) state;
                if (ps.hasObservation()) {
                    if (ps.observedHigh() == highValue && ps.observedLow() != lowValue) {
                        return false;
                    }
                }
                return true;
            };
        }, "pair");
    }

    /**
     * Registers an invariant type.
     *
     * @param typeName the invariant type name
     * @param compatibleStates the set of state types this invariant is compatible with
     * @param factory the factory function
     * @param primaryState the primary state type (for documentation)
     */
    public void register(String typeName, Set<String> compatibleStates, InvariantFactory factory, String primaryState) {
        factories.put(typeName, factory);
        compatMap.put(typeName, compatibleStates);
    }

    /**
     * Returns the set of registered invariant type names.
     */
    public Set<String> typeNames() {
        return factories.keySet();
    }

    /**
     * Creates an invariant from the JSON object.
     *
     * @param invariantJson the JSON object containing {@code type} and type-specific params
     * @return the constructed {@link Invariant}
     * @throws RegistryException if the type is unknown or parameters are invalid
     */
    public Invariant create(JsonObject invariantJson) {
        if (invariantJson.get("type") == null || invariantJson.get("type").isJsonNull()) {
            throw new RegistryException("Invariant 'type' field is required");
        }
        if (!invariantJson.get("type").isJsonPrimitive() || !invariantJson.get("type").getAsJsonPrimitive().isString()) {
            throw new RegistryException("Invariant 'type' must be a string");
        }
        String type = invariantJson.get("type").getAsString();
        InvariantFactory factory = factories.get(type);
        if (factory == null) {
            throw new RegistryException("Unknown invariant type '" + type + "'. Registered types: " + factories.keySet());
        }
        return factory.create(invariantJson);
    }

    /**
     * Validates that the invariant type is compatible with the given state type.
     */
    public void validateCompatibility(String invariantType, String stateType) {
        Set<String> compat = compatMap.get(invariantType);
        if (compat == null) {
            throw new RegistryException("Unknown invariant type '" + invariantType + "'");
        }
        if (!compat.contains(stateType)) {
            throw new RegistryException("Invariant type '" + invariantType + "' (compatible with " + compat + ") is not compatible with state type '" + stateType + "'");
        }
    }
}