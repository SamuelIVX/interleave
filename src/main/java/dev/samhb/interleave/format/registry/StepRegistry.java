package dev.samhb.interleave.format.registry;

import com.google.gson.JsonObject;
import dev.samhb.interleave.core.Step;
import dev.samhb.interleave.core.WriteFlagStep;
import dev.samhb.interleave.core.WriteTurnStep;
import dev.samhb.interleave.core.BusyWaitStep;
import dev.samhb.interleave.core.ReadFlagStep;
import dev.samhb.interleave.core.CSEnterStep;
import dev.samhb.interleave.core.CSExitStep;
import dev.samhb.interleave.core.UnconditionalWaitStep;
import dev.samhb.interleave.bugs.ReadCounterStep;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.bugs.WriteHighStep;
import dev.samhb.interleave.bugs.WriteLowStep;
import dev.samhb.interleave.bugs.ReadSnapshotStep;
import dev.samhb.interleave.bugs.DeadlockWriteFlagStep;
import dev.samhb.interleave.bugs.DclLockStep;
import dev.samhb.interleave.bugs.DclUnlockStep;
import dev.samhb.interleave.bugs.DclInitSetInitializedStep;
import dev.samhb.interleave.bugs.DclInitSetInstanceStep;
import dev.samhb.interleave.bugs.DclReadInstanceStep;
import dev.samhb.interleave.bugs.DclUseInstanceStep;
import dev.samhb.interleave.format.registry.JsonHelper;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Registry of built-in step types.
 * <p>
 * Maps step type names to factory functions. Each factory knows how to construct
 * the corresponding {@link Step} from JSON parameters. Each step type declares
 * which state types it is compatible with.
 */
public final class StepRegistry {
    private final Map<String, StepFactory> factories = new HashMap<>();
    private final Map<String, Set<String>> compatMap = new HashMap<>();

    public StepRegistry() {
        registerBuiltins();
    }

    private void registerBuiltins() {
        // Peterson steps (compatible with peterson state)
        register("write_flag", Set.of("peterson"), (json, tid) -> new WriteFlagStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "write_flag"),
            JsonHelper.getBool(json, "value", "write_flag")
        ));

        register("write_turn", Set.of("peterson"), (json, tid) -> new WriteTurnStep(
            JsonHelper.getInt(json, "value", "write_turn")
        ));

        register("busy_wait", Set.of("peterson"), (json, tid) -> new BusyWaitStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "busy_wait"),
            JsonHelper.getInt(json, "other", "busy_wait")
        ));

        register("read_flag", Set.of("peterson"), (json, tid) -> new ReadFlagStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "read_flag"),
            JsonHelper.getInt(json, "other", "read_flag")
        ));

        register("cs_enter", Set.of("peterson"), (json, tid) -> new CSEnterStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "cs_enter")
        ));

        register("cs_exit", Set.of("peterson"), (json, tid) -> new CSExitStep());

        // Counter steps (compatible with counter state)
        register("read_counter", Set.of("counter"), (json, tid) -> new ReadCounterStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "read_counter")
        ));

        register("write_counter", Set.of("counter"), (json, tid) -> new WriteCounterStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "write_counter")
        ));

        // Pair steps (compatible with pair state)
        register("write_high", Set.of("pair"), (json, tid) -> new WriteHighStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "write_high"),
            JsonHelper.getInt(json, "value", "write_high")
        ));

        register("write_low", Set.of("pair"), (json, tid) -> new WriteLowStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "write_low"),
            JsonHelper.getInt(json, "value", "write_low")
        ));

        register("read_snapshot", Set.of("pair"), (json, tid) -> new ReadSnapshotStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "read_snapshot")
        ));

        // Deadlock steps (compatible with deadlock state)
        register("deadlock_write_flag", Set.of("deadlock"), (json, tid) -> new DeadlockWriteFlagStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "deadlock_write_flag"),
            JsonHelper.getBool(json, "value", "deadlock_write_flag")
        ));

        register("unconditional_wait", Set.of("deadlock"), (json, tid) -> new UnconditionalWaitStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "unconditional_wait"),
            JsonHelper.getInt(json, "other", "unconditional_wait")
        ));

        // DCL steps (compatible with dcl state)
        register("dcl_lock", Set.of("dcl"), (json, tid) -> new DclLockStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "dcl_lock")
        ));

        register("dcl_unlock", Set.of("dcl"), (json, tid) -> new DclUnlockStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "dcl_unlock")
        ));

        register("dcl_init", Set.of("dcl"), (json, tid) -> new DclInitSetInitializedStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "dcl_init")
        ));

        register("dcl_create_instance", Set.of("dcl"), (json, tid) -> new DclInitSetInstanceStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "dcl_create_instance")
        ));

        register("dcl_read_instance", Set.of("dcl"), (json, tid) -> new DclReadInstanceStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "dcl_read_instance")
        ));

        register("dcl_use_instance", Set.of("dcl"), (json, tid) -> new DclUseInstanceStep(
            JsonHelper.getThreadIdOrDefault(json, tid, "dcl_use_instance")
        ));
    }

    /**
     * Registers a step type.
     *
     * @param typeName the step type name
     * @param compatibleStates the set of state types this step is compatible with
     * @param factory the factory function
     */
    public void register(String typeName, Set<String> compatibleStates, StepFactory factory) {
        factories.put(typeName, factory);
        compatMap.put(typeName, new HashSet<>(compatibleStates));
    }

    /**
     * Returns the set of registered step type names.
     */
    public Set<String> typeNames() {
        return factories.keySet();
    }

    /**
     * Creates a step from the JSON object.
     *
     * @param stepJson the JSON object containing {@code type} and params
     * @param owningThreadId the ID of the thread that owns this step
     * @return the constructed {@link Step}
     * @throws RegistryException if the type is unknown or parameters are invalid
     */
    public Step create(JsonObject stepJson, int owningThreadId) {
        String type = stepJson.get("type").getAsString();
        StepFactory factory = factories.get(type);
        if (factory == null) {
            throw new RegistryException("Unknown step type '" + type + "'. Registered types: " + factories.keySet());
        }
        return factory.create(stepJson, owningThreadId);
    }

    /**
     * Validates that the step type is compatible with the given state type.
     */
    public void validateCompatibility(String stepType, String stateType) {
        Set<String> compat = compatMap.get(stepType);
        if (compat == null) {
            throw new RegistryException("Unknown step type '" + stepType + "'");
        }
        if (!compat.contains(stateType)) {
            throw new RegistryException("Step type '" + stepType + "' (compatible with " + compat + ") is not compatible with state type '" + stateType + "'");
        }
    }
}