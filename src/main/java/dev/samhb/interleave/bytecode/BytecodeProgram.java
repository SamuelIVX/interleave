/**
 * Immutable internal bytecode artifact and interpreted setup snapshot.
 */
package dev.samhb.interleave.bytecode;

import java.util.*;

/**
 * Closed validated metadata; has no public construction or execution interface in 94A.
 */
final class BytecodeProgram {
    /**
     * Field identity.
     * @param owner declaring internal class
     * @param name field name
     * @param descriptor field type
     */
    record FieldId(String owner, String name, String descriptor) implements Comparable<FieldId> {
        /**
         * Stable field ordering without diagnostic string identity.
         * @param other another field
         * @return lexicographic identity ordering
         */
        @Override public int compareTo(FieldId other) {
            int order = owner.compareTo(other.owner);
            if (order == 0) order = name.compareTo(other.name);
            return order != 0 ? order : descriptor.compareTo(other.descriptor);
        }
    }
    /**
     * Field declaration.
     * @param id complete field identity
     * @param flags declaration flags, including volatile
     * @param initial typed initial value
     */
    record Field(FieldId id, int flags, BytecodeState.Value initial) {}
    /**
     * Declared observer metadata; execution belongs to94B.
     * @param method observer identity
     * @param always initial/transition versus final observation
     */
    record Check(BytecodeMethod.Id method, boolean always) {}
    /**
     * Validated model binary name in internal notation.
     */
    final String model;
    /**
     * Full reachable method metadata.
     */
    final Map<BytecodeMethod.Id, BytecodeMethod> methods;
    /**
     * Validated fields.
     */
    final Map<FieldId, Field> fields;
    /**
     * Dense original actor entry points.
     */
    final List<BytecodeMethod.Id> actors;
    /**
     * Deterministically ordered check declarations.
     */
    final List<Check> checks;
    /**
     * Class-monitor identity order.
     */
    final List<String> classes;
    /**
     * Immutable limits.
     */
    final BytecodeLimits limits;
    /**
     * Frozen exact-byte artifact identity.
     */
    private final String digest;
    /**
     * Never exposed directly; every execution receives its own copy.
     */
    private final BytecodeState setup;

    /**
     * Freezes metadata and the optional construction-time snapshot.
     * @param model selected internal name
     * @param methods validated reachable methods
     * @param fields validated declarations
     * @param actors dense actor entries
     * @param checks ordered observer entries
     * @param classes sorted application names
     * @param limits immutable resource profile
     * @param digest exact artifact digest
     * @param setup interpreted setup snapshot, null only during private loading
     */
    BytecodeProgram(String model, Map<BytecodeMethod.Id,BytecodeMethod> methods,
                    Map<FieldId,Field> fields, List<BytecodeMethod.Id> actors, List<Check> checks,
                    List<String> classes, BytecodeLimits limits, String digest, BytecodeState setup) {
        this.model = model; this.methods = Map.copyOf(methods); this.fields = Map.copyOf(fields);
        this.actors = List.copyOf(actors); this.checks = List.copyOf(checks); this.classes = List.copyOf(classes);
        this.limits = limits; this.digest = digest; this.setup = setup == null ? null : setup.copy();
    }

    /**
     * Returns the exact frozen manifest fingerprint.
     * @return exact frozen class-manifest digest
     */
    String artifactDigest() { return digest; }

    /**
     * Creates an isolated post-setup state with fresh actor frames.
     * @return isolated post-setup state with independent actor frames
     */
    BytecodeState initialState() {
        var state = Objects.requireNonNull(setup, "Setup is not complete").copy();
        for (int id = 0; id < actors.size(); id++) state.start(id, methods.get(actors.get(id)), List.of(BytecodeState.FIXTURE));
        return state;
    }
}
