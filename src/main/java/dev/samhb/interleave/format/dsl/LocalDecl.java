/** Per-thread local declaration for declarative state. */
package dev.samhb.interleave.format.dsl;

import java.util.Arrays;
import java.util.Objects;

/**
 * Per-thread local declaration for declarative state.
 *
 * @param name local name, matches [a-z][a-z0-9_]*
 * @param type local type (INT, BOOL, or INT_ARRAY)
 * @param intInit initial int value
 * @param boolInit initial bool value
 * @param arrayInit initial array contents (for INT_ARRAY), defensive copy on construction
 */
public record LocalDecl(String name, FieldType type, int intInit, boolean boolInit, int[] arrayInit) {
    /**
     * Compact constructor: defensive copy of array init to preserve immutability,
     * normalizing null to an empty array for INT_ARRAY type.
     */
    public LocalDecl {
        if (arrayInit != null) {
            arrayInit = Arrays.copyOf(arrayInit, arrayInit.length);
        } else if (type == FieldType.INT_ARRAY) {
            arrayInit = new int[0];
        }
    }

    /**
     * Backward-compatible 4-argument constructor for scalar locals.
     *
     * @param name local name
     * @param type local type
     * @param intInit initial int value
     * @param boolInit initial bool value
     */
    public LocalDecl(String name, FieldType type, int intInit, boolean boolInit) {
        this(name, type, intInit, boolInit, null);
    }

    /**
     * Returns a defensive copy of declared initial array contents.
     * @return fresh copy or null
     */
    @Override
    public int[] arrayInit() {
        return arrayInit == null ? null : Arrays.copyOf(arrayInit, arrayInit.length);
    }

    /**
     * Creates local for int.
     *
     * @param name local name
     * @param init initial value
     * @return decl
     */
    public static LocalDecl ofInt(String name, int init) {
        return new LocalDecl(name, FieldType.INT, init, false, null);
    }

    /**
     * Creates local for bool.
     *
     * @param name local name
     * @param init initial value
     * @return decl
     */
    public static LocalDecl ofBool(String name, boolean init) {
        return new LocalDecl(name, FieldType.BOOL, 0, init, null);
    }

    /**
     * Creates local for int array.
     *
     * @param name local name
     * @param init initial contents (copied, or normalized to empty array if null)
     * @return decl
     */
    public static LocalDecl ofArray(String name, int[] init) {
        int[] copy = init == null ? new int[0] : Arrays.copyOf(init, init.length);
        return new LocalDecl(name, FieldType.INT_ARRAY, 0, false, copy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalDecl that)) return false;
        return intInit == that.intInit
                && boolInit == that.boolInit
                && Objects.equals(name, that.name)
                && type == that.type
                && Arrays.equals(arrayInit, that.arrayInit);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, type, intInit, boolInit);
        result = 31 * result + Arrays.hashCode(arrayInit);
        return result;
    }
}
