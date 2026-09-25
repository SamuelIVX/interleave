package dev.samhb.interleave.format.dsl;

/**
 * Per-thread local declaration for declarative state.
 *
 * @param name local name, matches [a-z][a-z0-9_]*
 * @param type local type (INT or BOOL only)
 * @param intInit initial int value
 * @param boolInit initial bool value
 */
public record LocalDecl(String name, FieldType type, int intInit, boolean boolInit) {
    /**
     * Creates local for int.
     *
     * @param name local name
     * @param init initial value
     * @return decl
     */
    public static LocalDecl ofInt(String name, int init) {
        return new LocalDecl(name, FieldType.INT, init, false);
    }

    /**
     * Creates local for bool.
     *
     * @param name local name
     * @param init initial value
     * @return decl
     */
    public static LocalDecl ofBool(String name, boolean init) {
        return new LocalDecl(name, FieldType.BOOL, 0, init);
    }
}
