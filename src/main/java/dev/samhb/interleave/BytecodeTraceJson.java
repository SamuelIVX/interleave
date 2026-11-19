/** Bounded, duplicate-free import of the distinct BYTECODE witness schema. */
package dev.samhb.interleave;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.samhb.interleave.core.BytecodeEvidence;
import dev.samhb.interleave.core.StateSnapshot;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.search.TraceOutcome;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** Import limits bound work before allocating reflective DTOs or interpreting any target. */
final class BytecodeTraceJson {
    /** Prevents construction of the schema codec. */
    private BytecodeTraceJson() {}

    /** Reads exactly the producer's BYTECODE schema, preserving string-tagged numeric values.
     * @param json untrusted text
     * @return validated immutable shape, still requiring strict replay authentication
     * @throws IllegalArgumentException for invalid syntax, shape or resource limits */
    static TraceRecord read(String json) {
        if (json == null || json.length() > 16 * 1024 * 1024) throw new IllegalArgumentException("BYTECODE JSON exceeds 16 Mi UTF-16 code units");
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            var nodes = new int[]{0}; charge(nodes);
            var root = value(reader, 0, nodes).getAsJsonObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || !"BYTECODE".equals(root.get("programKind").getAsString()))
                throw new IllegalArgumentException("Expected one BYTECODE record");
            var evidence = ResultJson.GSON.fromJson(root.get("bytecodeEvidence"), BytecodeEvidence.class);
            List<Integer> threads = new ArrayList<>();
            for (var element : root.getAsJsonArray("threads")) threads.add(element.getAsInt());
            List<StepOutcome> outcomes = new ArrayList<>();
            for (var element : root.getAsJsonArray("outcomes")) outcomes.add(StepOutcome.valueOf(element.getAsString()));
            List<StateSnapshot> states = new ArrayList<>();
            for (var element : root.getAsJsonArray("states")) states.add(ResultJson.GSON.fromJson(element, StateSnapshot.class));
            for (var state : states) {
                if (!state.fields().values().stream().allMatch(String.class::isInstance)
                        || state.locals().values().stream().anyMatch(row -> !row.values().stream().allMatch(String.class::isInstance)))
                    throw new IllegalArgumentException("BYTECODE display values must be immutable tagged strings");
            }
            var trace = new TraceRecord(threads, outcomes, TraceOutcome.valueOf(root.get("outcome").getAsString()),
                root.get("programHash").getAsString(), states, null, java.util.Objects.requireNonNull(evidence));
            // Canonical shape comparison also rejects unknown fields and Gson's scalar coercions.
            var canonical = JsonParser.parseString(trace.toJson());
            if (!same(root, canonical)) throw new IllegalArgumentException("Noncanonical or conflicting BYTECODE schema");
            return trace;
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid BYTECODE trace JSON", invalid);
        }
    }

    /** Parses with depth, node and duplicate-name limits before Gson record construction.
     * @param reader strict stream
     * @param depth current nesting depth
     * @param nodes shared node counter
     * @return bounded JSON tree
     * @throws IOException for invalid stream input */
    private static JsonElement value(JsonReader reader, int depth, int[] nodes) throws IOException {
        if (depth > 64) throw new IllegalArgumentException("BYTECODE JSON nesting limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    var name = reader.nextName();
                    if (object.has(name)) throw new IllegalArgumentException("Duplicate JSON member");
                    charge(nodes);
                    object.add(name, value(reader, depth + 1, nodes));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) {
                    charge(nodes);
                    array.add(value(reader, depth + 1, nodes));
                }
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> JsonParser.parseString(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Expected JSON value");
        };
    }

    /** Charges each node before recursion or container growth, keeping the loop's work bound independent
     * of the child's parsing progress.
     * @param nodes shared count including the root
     * @throws IllegalArgumentException before allocating a node above the documented ceiling */
    private static void charge(int[] nodes) {
        if (++nodes[0] > 1_000_000) throw new IllegalArgumentException("BYTECODE JSON node limit");
    }

    /** Compares schema trees without numeric/string coercion or loss of integer spelling.
     * @param left supplied tree
     * @param right canonical producer tree
     * @return whether both trees have exactly the same members and typed leaves */
    private static boolean same(JsonElement left, JsonElement right) {
        if (left.isJsonObject() && right.isJsonObject()) {
            var a = left.getAsJsonObject(); var b = right.getAsJsonObject();
            return a.keySet().equals(b.keySet()) && a.keySet().stream().allMatch(key -> same(a.get(key), b.get(key)));
        }
        if (left.isJsonArray() && right.isJsonArray()) {
            var a = left.getAsJsonArray(); var b = right.getAsJsonArray();
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!same(a.get(i), b.get(i))) return false;
            return true;
        }
        return left.toString().equals(right.toString());
    }
}
