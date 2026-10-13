/** Immutable snapshot of state variables at a specific execution configuration. */
package dev.samhb.interleave.core;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of state variables at a specific execution configuration.
 *
 * <p>Captures shared fields and per-thread local variables for inspection, trace replay,
 * and visualizer rendering.
 *
 * @param fields map of field names to values (scalars, boolean/integer arrays)
 * @param locals map of thread id strings (e.g. "0", "1") to maps of local names and values
 */
public record StateSnapshot(
    Map<String, Object> fields,
    Map<String, Map<String, Object>> locals
) implements Serializable {

    /**
     * Compact constructor enforcing deep immutability by copying nested maps and cloning arrays.
     */
    public StateSnapshot {
        fields = deepCopyFields(fields);
        locals = deepCopyLocals(locals);
    }

    /**
     * Performs a deep defensive copy of a field map, cloning primitive arrays (int[], boolean[])
     * and returning an unmodifiable map. Returns an empty map if the input is null or empty.
     *
     * @param map map of field names to values
     * @return unmodifiable defensive copy with cloned arrays
     */
    private static Map<String, Object> deepCopyFields(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof int[] arr) {
                val = arr.clone();
            } else if (val instanceof boolean[] arr) {
                val = arr.clone();
            }
            copy.put(entry.getKey(), val);
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Performs a deep defensive copy of a nested thread locals map, cloning any nested
     * primitive arrays and returning an unmodifiable outer map. Returns an empty map if
     * the input is null or empty.
     *
     * @param map map of thread id strings to local maps
     * @return unmodifiable defensive copy of nested locals
     */
    private static Map<String, Map<String, Object>> deepCopyLocals(Map<String, Map<String, Object>> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
            copy.put(entry.getKey(), deepCopyFields(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Factory for snapshots with fields only.
     *
     * @param fields field mapping
     * @return snapshot with empty locals
     */
    public static StateSnapshot ofFields(Map<String, Object> fields) {
        return new StateSnapshot(fields, Map.of());
    }
}
