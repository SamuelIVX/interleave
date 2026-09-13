package dev.samhb.interleave.format.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Helper methods for extracting typed values from JSON objects with clear error messages.
 * <p>
 * All methods throw {@link RegistryException} with context including the type name
 * and parameter key for actionable error messages.
 */
public final class JsonHelper {
    private JsonHelper() {
        // Utility class
    }

    /**
     * Gets a required integer value.
     *
     * @param obj the JSON object
     * @param key the parameter key
     * @param typeContext the type context for error messages (e.g., step type name)
     * @return the integer value
     * @throws RegistryException if the key is missing, not an integer, or not a valid number
     */
    public static int getInt(JsonObject obj, String key, String typeContext) {
        JsonElement elem = obj.get(key);
        if (elem == null) {
            throw new RegistryException("Missing required parameter '" + key + "' for " + typeContext);
        }
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isNumber()) {
            throw new RegistryException("Parameter '" + key + "' for " + typeContext + " must be a number, got: " + elem);
        }
        return elem.getAsInt();
    }

    /**
     * Gets a required boolean value.
     *
     * @param obj the JSON object
     * @param key the parameter key
     * @param typeContext the type context for error messages
     * @return the boolean value
     * @throws RegistryException if the key is missing or not a boolean
     */
    public static boolean getBool(JsonObject obj, String key, String typeContext) {
        JsonElement elem = obj.get(key);
        if (elem == null) {
            throw new RegistryException("Missing required parameter '" + key + "' for " + typeContext);
        }
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isBoolean()) {
            throw new RegistryException("Parameter '" + key + "' for " + typeContext + " must be a boolean, got: " + elem);
        }
        return elem.getAsBoolean();
    }

    /**
     * Gets an optional integer value with a default.
     *
     * @param obj the JSON object
     * @param key the parameter key
     * @param defaultValue the default value if key is missing
     * @param typeContext the type context for error messages
     * @return the integer value or default
     * @throws RegistryException if the key is present but not an integer
     */
    public static int getIntOrDefault(JsonObject obj, String key, int defaultValue, String typeContext) {
        JsonElement elem = obj.get(key);
        if (elem == null) {
            return defaultValue;
        }
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isNumber()) {
            throw new RegistryException("Parameter '" + key + "' for " + typeContext + " must be a number, got: " + elem);
        }
        return elem.getAsInt();
    }

    /**
     * Gets the {@code thread} parameter or defaults to the owning thread ID.
     * <p>
     * If the {@code thread} field is present, it must match the owning thread ID.
     * If absent, defaults to {@code owningThreadId}.
     *
     * @param obj the step JSON object
     * @param owningThreadId the owning thread's ID (default)
     * @param typeContext the step type for error messages
     * @return the thread ID to use
     * @throws RegistryException if {@code thread} is present but doesn't match owningThreadId
     */
    public static int getThreadIdOrDefault(JsonObject obj, int owningThreadId, String typeContext) {
        JsonElement elem = obj.get("thread");
        if (elem == null) {
            return owningThreadId;
        }
        int threadId = elem.getAsInt();
        if (threadId != owningThreadId) {
            throw new RegistryException(
                "Parameter 'thread' for step type '" + typeContext + "' is " + threadId +
                " but must match owning thread ID " + owningThreadId
            );
        }
        return threadId;
    }

    /**
     * Gets a required string value.
     *
     * @param obj the JSON object
     * @param key the parameter key
     * @param typeContext the type context for error messages
     * @return the string value
     * @throws RegistryException if the key is missing or not a string
     */
    public static String getString(JsonObject obj, String key, String typeContext) {
        JsonElement elem = obj.get(key);
        if (elem == null) {
            throw new RegistryException("Missing required parameter '" + key + "' for " + typeContext);
        }
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isString()) {
            throw new RegistryException("Parameter '" + key + "' for " + typeContext + " must be a string, got: " + elem);
        }
        return elem.getAsString();
    }

    /**
     * Gets a required JSON array.
     *
     * @param obj the JSON object
     * @param key the parameter key
     * @param typeContext the type context for error messages
     * @return the JSON array
     * @throws RegistryException if the key is missing or not an array
     */
    public static com.google.gson.JsonArray getArray(JsonObject obj, String key, String typeContext) {
        JsonElement elem = obj.get(key);
        if (elem == null) {
            throw new RegistryException("Missing required parameter '" + key + "' for " + typeContext);
        }
        if (!elem.isJsonArray()) {
            throw new RegistryException("Parameter '" + key + "' for " + typeContext + " must be an array, got: " + elem);
        }
        return elem.getAsJsonArray();
    }
}