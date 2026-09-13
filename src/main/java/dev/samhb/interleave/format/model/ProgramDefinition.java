package dev.samhb.interleave.format.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Top-level program definition parsed from JSON.
 * <p>
 * Corresponds to the top-level JSON object with fields:
 * <ul>
 *   <li>{@code name} (required)</li>
 *   <li>{@code state} (required) — a {@link JsonObject} containing {@code type} and type-specific fields</li>
 *   <li>{@code threads} (required) — a list of {@link ThreadDefinition}</li>
 *   <li>{@code invariant} (optional) — a {@link JsonObject} containing {@code type} and type-specific params</li>
 *   <li>{@code expected_verdict} (optional) — one of "PASS", "VIOLATION", "DEADLOCK"</li>
 * </ul>
 */
public final class ProgramDefinition {
    private String name;
    private JsonObject state;
    private List<ThreadDefinition> threads;
    private JsonObject invariant;
    
    @SerializedName("expected_verdict")
    private String expectedVerdict;

    public ProgramDefinition() {
        // No-arg constructor for Gson
    }

    public String name() {
        return name;
    }

    public JsonObject state() {
        return state;
    }

    public List<ThreadDefinition> threads() {
        return threads;
    }

    public JsonObject invariant() {
        return invariant;
    }

    public String expectedVerdict() {
        return expectedVerdict;
    }
}