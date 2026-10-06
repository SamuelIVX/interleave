/** Left-hand side of an effect assignment. */
package dev.samhb.interleave.format.dsl;

/**
 * Left-hand side of an effect assignment.
 */
public sealed interface Lhs permits Lhs.FieldLhs, Lhs.LocalLhs, Lhs.ArrayLhs {
    /**
     * Shared field scalar assignment.
     * @param name declared field or local name
     */
    record FieldLhs(String name) implements Lhs {}
    /**
     * Per-thread local assignment.
     * @param name declared field or local name
     */
    record LocalLhs(String name) implements Lhs {}
    /**
     * Shared array element assignment.
     * @param arrayName declared shared-array name
     * @param index expression selecting an array element
     */
    record ArrayLhs(String arrayName, Expr index) implements Lhs {}
}
