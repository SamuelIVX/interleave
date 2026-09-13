package dev.samhb.interleave.format.registry;

import com.google.gson.JsonObject;
import dev.samhb.interleave.core.SharedState;

/**
 * Factory interface for creating {@link SharedState} instances from JSON parameters.
 */
@FunctionalInterface
public interface StateFactory {
    /**
     * Creates a shared state from the given JSON parameters.
     *
     * @param stateJson the state JSON object (including {@code type} field)
     * @return a new {@link SharedState} instance
     * @throws RegistryException if required fields are missing or have invalid types
     */
    SharedState create(com.google.gson.JsonObject stateJson);
}