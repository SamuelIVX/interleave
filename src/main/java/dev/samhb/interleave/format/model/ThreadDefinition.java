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

    public ThreadDefinition() {
        // No-arg constructor for Gson
    }

    public Integer id() {
        return id;
    }

    public List<JsonObject> steps() {
        return steps;
    }
}