/** Collects complete DSL expression reads for steps and property observation footprints. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.MemoryLocation;
import java.util.Set;

/** Shared alias and local-name rules; both operands are included even for short-circuit expressions. */
final class ExpressionReads {
    /** Prevents instantiation of this utility class. */
    private ExpressionReads() {}

    /**
     * Adds every location an expression may inspect to the supplied collection.
     * @param expr expression tree
     * @param owner owning thread for local locations; DSL invariants use thread zero
     * @param out mutable destination for the over-approximated reads
     */
    static void collect(Expr expr, int owner, Set<MemoryLocation> out) {
        if (expr instanceof Expr.VarRef v) out.add(MemoryLocation.of(v.name()));
        else if (expr instanceof Expr.LocalRef l) out.add(MemoryLocation.of("t" + owner + "." + l.name()));
        else if (expr instanceof Expr.ArrayAccess a) {
            if (a.arrayName().startsWith("local.")) {
                out.add(MemoryLocation.of("t" + owner + "." + a.arrayName().substring(6)));
            } else if (a.index() instanceof Expr.IntLit literal) {
                out.add(MemoryLocation.of(a.arrayName() + "[" + literal.value() + "]"));
            } else {
                out.add(MemoryLocation.of(a.arrayName()));
                collect(a.index(), owner, out);
            }
        } else if (expr instanceof Expr.UnaryOp unary) {
            collect(unary.operand(), owner, out);
        } else if (expr instanceof Expr.BinaryOp binary) {
            collect(binary.left(), owner, out);
            collect(binary.right(), owner, out);
        }
        // Literals and tid are constants with respect to modeled memory.
    }
}
