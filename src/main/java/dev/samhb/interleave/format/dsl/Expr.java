package dev.samhb.interleave.format.dsl;

/**
 * Sealed expression AST for declarative DSL.
 */
public sealed interface Expr permits
        Expr.IntLit, Expr.BoolLit, Expr.VarRef, Expr.LocalRef,
        Expr.ArrayAccess, Expr.TidRef, Expr.UnaryOp, Expr.BinaryOp {

    /** Integer literal. */
    record IntLit(int value) implements Expr {}

    /** Boolean literal. */
    record BoolLit(boolean value) implements Expr {}

    /** Shared field scalar reference. */
    record VarRef(String name) implements Expr {}

    /** Per-thread local scalar reference. */
    record LocalRef(String name) implements Expr {}

    /** Shared array element access. */
    record ArrayAccess(String arrayName, Expr index) implements Expr {}

    /** Thread id keyword. */
    record TidRef() implements Expr {}

    /** Unary operation. */
    record UnaryOp(String op, Expr operand) implements Expr {}

    /** Binary operation. */
    record BinaryOp(Expr left, String op, Expr right) implements Expr {}

    /**
     * Counts nodes in tree.
     *
     * @param e expression
     * @return node count
     */
    static int nodeCount(Expr e) {
        if (e instanceof IntLit || e instanceof BoolLit || e instanceof VarRef || e instanceof LocalRef || e instanceof TidRef) return 1;
        if (e instanceof ArrayAccess a) return 1 + nodeCount(a.index());
        if (e instanceof UnaryOp u) return 1 + nodeCount(u.operand());
        if (e instanceof BinaryOp b) return 1 + nodeCount(b.left()) + nodeCount(b.right());
        throw new IllegalStateException("unknown expr");
    }

    /**
     * Computes depth (max path).
     *
     * @param e expression
     * @return depth
     */
    static int depth(Expr e) {
        if (e instanceof IntLit || e instanceof BoolLit || e instanceof VarRef || e instanceof LocalRef || e instanceof TidRef) return 1;
        if (e instanceof ArrayAccess a) return 1 + depth(a.index());
        if (e instanceof UnaryOp u) return 1 + depth(u.operand());
        if (e instanceof BinaryOp b) return 1 + Math.max(depth(b.left()), depth(b.right()));
        throw new IllegalStateException("unknown expr");
    }
}
