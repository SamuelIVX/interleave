package dev.samhb.interleave.format.model;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * Thread definition parsed from JSON.
 * <p>
 * Each thread has an {@code id} (0-based, must match array index) and a list of steps.
 * Each step is a {@link JsonObject} with a required {@code type} field and optional parameters
 * such as {@code thread}, {@code other}, and {@code value}.
 */
public final class ThreadDefinition {
    private int id;
    private List<JsonObject> steps;

    public ThreadDefinition() {
        // No-arg constructor for Gson
    }

    public int id() {
        return id;
    }

    public List<JsonObject> steps() {
        return steps;
    }
}