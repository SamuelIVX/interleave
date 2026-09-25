package dev.samhb.interleave.format.dsl;

import java.util.Arrays;
import java.util.Objects;

/**
 * Shared field declaration for declarative state.
 *
 * @param name field name, matches [a-z][a-z0-9_]*
 * @param type field type
 * @param intInit initial int value (for INT)
 * @param boolInit initial bool value (for BOOL)
 * @param arrayInit initial array contents (for INT_ARRAY), defensive copy on construction
 */
public record FieldDecl(String name, FieldType type, int intInit, boolean boolInit, int[] arrayInit) {
    /**
     * Compact constructor: defensive copy of array init to preserve immutability.
     */
    public FieldDecl {
        if (arrayInit != null) arrayInit = Arrays.copyOf(arrayInit, arrayInit.length);
    }

    @Override
    /** arrayInit method. */
    public int[] arrayInit() {
        return arrayInit == null ? null : Arrays.copyOf(arrayInit, arrayInit.length);
    }

    @Override
    /** equals method. */
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldDecl that)) return false;
        return intInit == that.intInit
                && boolInit == that.boolInit
                && Objects.equals(name, that.name)
                && type == that.type
                && Arrays.equals(arrayInit, that.arrayInit);
    }

    @Override
    /** hashCode method. */
    public int hashCode() {
        int h = Objects.hash(name, type, intInit, boolInit);
        h = 31 * h + Arrays.hashCode(arrayInit);
        return h;
    }
    /**
     * Creates declaration for int field.
     *
     * @param name field name
     * @param init initial value
     * @return decl
     */
    public static FieldDecl ofInt(String name, int init) {
        return new FieldDecl(name, FieldType.INT, init, false, null);
    }

    /**
     * Creates declaration for bool field.
     *
     * @param name field name
     * @param init initial value
     * @return decl
     */
    public static FieldDecl ofBool(String name, boolean init) {
        return new FieldDecl(name, FieldType.BOOL, 0, init, null);
    }

    /**
     * Creates declaration for int[] field.
     *
     * @param name field name
     * @param init initial contents (copied)
     * @return decl
     */
    public static FieldDecl ofArray(String name, int[] init) {
        int[] copy = init == null ? new int[0] : java.util.Arrays.copyOf(init, init.length);
        return new FieldDecl(name, FieldType.INT_ARRAY, 0, false, copy);
    }
}
