package dev.samhb.interleave.format.registry;

import com.google.gson.JsonObject;
import dev.samhb.interleave.core.Step;

/**
 * Factory interface for creating {@link Step} instances from JSON parameters.
 * <p>
 * The factory receives the full step JSON object (including the {@code type} field)
 * and the owning thread's ID (derived from the thread definition). The owning thread ID
 * is used as the default value for the optional {@code thread} parameter.
 */
@FunctionalInterface
public interface StepFactory {
    /**
     * Creates a step from the given JSON parameters.
     *
     * @param stepJson the full step JSON object (including {@code type} field)
     * @param owningThreadId the ID of the thread that owns this step (used as default for {@code thread} param)
     * @return a new {@link Step} instance
     * @throws RegistryException if required parameters are missing or have invalid types
     */
    Step create(com.google.gson.JsonObject stepJson, int owningThreadId);
}