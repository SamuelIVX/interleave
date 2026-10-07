/** Strict JSON parsing for wire-contract tests using the project's existing Gson dependency. */
package dev.samhb.interleave.testsupport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

/** Parses the emitted payload rather than treating matching substrings as valid JSON. */
public final class JsonAssertions {
    /** Immutable parser configuration; Gson instances support concurrent calls. */
    private static final Gson STRICT = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    /** Prevents instantiation of this assertion helper. */
    private JsonAssertions() {}

    /**
     * Parses a complete JSON object, rejecting malformed or trailing content.
     * @param json full emitted document
     * @return parsed object for type-sensitive value assertions
     * @throws com.google.gson.JsonSyntaxException if input is malformed, trailing, or not an object
     */
    public static JsonObject parseObject(String json) {
        JsonObject parsed = STRICT.fromJson(json, JsonObject.class);
        if (parsed == null) {
            throw new com.google.gson.JsonSyntaxException("Expected a JSON object, got null or empty input");
        }
        return parsed;
    }
}
