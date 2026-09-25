package dev.samhb.interleave.format.dsl;

/**
 * Left-hand side of an effect assignment.
 */
public sealed interface Lhs permits Lhs.FieldLhs, Lhs.LocalLhs, Lhs.ArrayLhs {
    /** Shared field scalar assignment. */
    record FieldLhs(String name) implements Lhs {}
    /** Per-thread local assignment. */
    record LocalLhs(String name) implements Lhs {}
    /** Shared array element assignment. */
    record ArrayLhs(String arrayName, Expr index) implements Lhs {}
}
