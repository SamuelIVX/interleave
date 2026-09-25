package dev.samhb.interleave.format.dsl;

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
