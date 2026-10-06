/** Top-level program definition parsed from JSON. */
package dev.samhb.interleave.format.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Top-level program definition parsed from JSON.
 * <p>
 * Corresponds to the top-level JSON object with fields:
 * <ul>
 *   <li>{@code format} (required) — {@code "typed"} or {@code "declarative"}</li>
 *   <li>{@code name} (required)</li>
 *   <li>{@code state} (required) — a {@link JsonObject} containing {@code type} and type-specific fields (typed) or {@code fields}/{@code locals} (declarative)</li>
 *   <li>{@code threads} (required) — a list of {@link ThreadDefinition}</li>
 *   <li>{@code invariant} (optional) — a {@link JsonObject} containing {@code type} and type-specific params (typed) or {@code expr} (declarative)</li>
 *   <li>{@code expected_verdict} (optional) — one of "PASS", "VIOLATION", "DEADLOCK"</li>
 * </ul>
 */
public final class ProgramDefinition {
    /** Format. */
    private String format;
    /** Name. */
    private String name;
    /** State. */
    private JsonObject state;
    /** Threads. */
    private List<ThreadDefinition> threads;
    /** Invariant. */
    private JsonObject invariant;

    /** Expected verdict. */
    @SerializedName("expected_verdict")
    private String expectedVerdict;

    /**
     * No-arg constructor required by Gson, which instantiates reflectively and sets fields directly.
     *
     * <p>Fields it leaves unset are null or absent rather than defaulted, so
     * {@link dev.samhb.interleave.format.dsl.DslLoader} is responsible for validating them and
     * reporting a JSON path. Defaulting here would hide a missing entry behind a plausible value.
     */
    public ProgramDefinition() {
        // No-arg constructor for Gson
    }

    /**
     * Returns the format discriminator.
     * @return {@code "typed"} or {@code "declarative"}, or {@code null} if missing
     */
    public String format() {
        return format;
    }

    /**
     * Returns name.
     * @return the program's declared name, used in reports and corpus keys
     */
    public String name() {
        return name;
    }

    /**
     * Returns state.
     * @return the raw {@code state} JSON block, parsed into a declaration by the loader
     */
    public JsonObject state() {
        return state;
    }

    /**
     * Returns threads.
     * @return the thread definitions, in declaration order
     */
    public List<ThreadDefinition> threads() {
        return threads;
    }

    /**
     * Returns invariant.
     * @return the raw invariant JSON, or null when the program declares none
     */
    public JsonObject invariant() {
        return invariant;
    }

    /**
     * Returns expected verdict.
     * @return the verdict the oracle is expected to reach, or null when unconstrained
     */
    public String expectedVerdict() {
        return expectedVerdict;
    }
}
