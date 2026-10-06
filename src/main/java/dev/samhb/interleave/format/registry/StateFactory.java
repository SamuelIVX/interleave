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
     * <p>The thread count is required rather than inferred from the state JSON because a state's
     * per-thread arrays have to agree with the program's threads — a mismatch is a malformed program,
     * not something to paper over by defaulting. Types whose state has no per-thread component ignore
     * it.
     *
     * @param stateJson the state JSON object (including {@code type} field)
     * @param threadCount the number of threads the program declares
     * @return a new {@link SharedState} instance
     * @throws RegistryException if required fields are missing or have invalid types, or a per-thread
     *     array's length disagrees with {@code threadCount}
     */
    SharedState create(com.google.gson.JsonObject stateJson, int threadCount);
}