package dev.samhb.interleave.format.registry;

import com.google.gson.JsonObject;
import dev.samhb.interleave.search.Invariant;

/**
 * Factory interface for creating {@link Invariant} instances from JSON parameters.
 */
@FunctionalInterface
public interface InvariantFactory {
    /**
     * Creates an invariant from the given JSON parameters.
     *
     * @param invariantJson the invariant JSON object (including {@code type} field)
     * @return a new {@link Invariant} instance
     * @throws RegistryException if required parameters are missing or have invalid types
     */
    Invariant create(com.google.gson.JsonObject invariantJson);
}