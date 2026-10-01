package dev.samhb.interleave.format.model;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * Thread definition parsed from JSON.
 * <p>
 * Each thread has an {@code id} (0-based, must match array index) and a list of steps.
 * Each step is a {@link JsonObject} with a required {@code type} field and optional parameters
 * such as {@code thread}, {@code other}, and {@code value}.
 * <p>
 * The {@code id} field is {@code Integer} (not primitive {@code int}) to distinguish between
 * a missing {@code id} field (Gson leaves it {@code null}) and an explicit {@code 0}.
 * The loader validates that {@code id} is present and matches the array index.
 */
public final class ThreadDefinition {
    private Integer id;
    private List<JsonObject> steps;

    /**
     * No-arg constructor required by Gson, which instantiates reflectively and sets fields directly.
     *
     * <p>Leaving fields unset here keeps a missing JSON entry detectable by the loader rather than
     * defaulting it to a plausible thread id.
     */
    public ThreadDefinition() {
        // No-arg constructor for Gson
    }

    /** @return the thread's id, or null when the JSON omitted it */
    public Integer id() {
        return id;
    }

    /** @return the raw step JSON objects, parsed into steps by the loader in declaration order */
    public List<JsonObject> steps() {
        return steps;
    }
}
