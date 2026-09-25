package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.format.registry.RegistryException;

/**
 * Type-checks declarative expressions at load time.
 */
public final class TypeChecker {
    private TypeChecker() {}

    /**
     * Checks expression type.
     *
     * @param expr expression
     * @param decl state declaration for field/local lookup
     * @return type of expression (INT or BOOL)
     * @throws RegistryException on type error or unknown name
     */
    public static FieldType check(Expr expr, StateDecl decl) {
        if (expr instanceof Expr.IntLit) return FieldType.INT;
        if (expr instanceof Expr.BoolLit) return FieldType.BOOL;
        if (expr instanceof Expr.TidRef) return FieldType.INT;
        if (expr instanceof Expr.VarRef v) {
            FieldDecl f = decl.findField(v.name());
            if (f == null) throw new RegistryException("Unknown field '" + v.name() + "'");
            if (f.type() == FieldType.INT_ARRAY) throw new RegistryException("Array field '" + v.name() + "' requires index: use " + v.name() + "[expr]");
            return f.type();
        }
        if (expr instanceof Expr.LocalRef l) {
            LocalDecl ld = decl.findLocal(l.name());
            if (ld == null) throw new RegistryException("Unknown local '" + l.name() + "'");
            return ld.type();
        }
        if (expr instanceof Expr.ArrayAccess a) {
            if (a.arrayName().startsWith("local.")) throw new RegistryException("Local array access not supported: " + a.arrayName());
            FieldDecl f = decl.findField(a.arrayName());
            if (f == null) throw new RegistryException("Unknown array field '" + a.arrayName() + "'");
            if (f.type() != FieldType.INT_ARRAY) throw new RegistryException("Field '" + a.arrayName() + "' is not an array");
            FieldType idxT = check(a.index(), decl);
            if (idxT != FieldType.INT) throw new RegistryException("Array index must be int");
            return FieldType.INT;
        }
        if (expr instanceof Expr.UnaryOp u) {
            FieldType sub = check(u.operand(), decl);
            return switch (u.op()) {
                case "!" -> {
                    if (sub != FieldType.BOOL) throw new RegistryException("! requires bool, got " + sub);
                    yield FieldType.BOOL;
                }
                case "-" -> {
                    if (sub != FieldType.INT) throw new RegistryException("- requires int, got " + sub);
                    yield FieldType.INT;
                }
                default -> throw new RegistryException("Unknown unary op " + u.op());
            };
        }
        if (expr instanceof Expr.BinaryOp b) {
            FieldType lt = check(b.left(), decl);
            FieldType rt = check(b.right(), decl);
            return switch (b.op()) {
                case "+", "-", "*", "%" -> {
                    if (lt != FieldType.INT || rt != FieldType.INT) throw new RegistryException(b.op() + " requires int, got " + lt + " and " + rt);
                    yield FieldType.INT;
                }
                case "==", "!=" -> {
                    if (lt != rt) throw new RegistryException(b.op() + " requires same type, got " + lt + " and " + rt);
                    // allow int==int and bool==bool, but not array
                    if (lt == FieldType.INT_ARRAY) throw new RegistryException(b.op() + " cannot compare arrays");
                    yield FieldType.BOOL;
                }
                case "<", "<=", ">", ">=" -> {
                    if (lt != FieldType.INT || rt != FieldType.INT) throw new RegistryException(b.op() + " requires int");
                    yield FieldType.BOOL;
                }
                case "&&", "||" -> {
                    if (lt != FieldType.BOOL || rt != FieldType.BOOL) throw new RegistryException(b.op() + " requires bool");
                    yield FieldType.BOOL;
                }
                default -> throw new RegistryException("Unknown binary op " + b.op());
            };
        }
        throw new RegistryException("Unknown expr type");
    }

    /**
     * Checks that expression is bool.
     *
     * @param expr expression
     * @param decl decl
     * @throws RegistryException if not bool
     */
    public static void requireBool(Expr expr, StateDecl decl) {
        FieldType t = check(expr, decl);
        if (t != FieldType.BOOL) throw new RegistryException("Expression must be bool, got " + t);
    }

    /**
     * Checks that expression is int.
     *
     * @param expr expression
     * @param decl decl
     * @throws RegistryException if not int
     */
    public static void requireInt(Expr expr, StateDecl decl) {
        FieldType t = check(expr, decl);
        if (t != FieldType.INT) throw new RegistryException("Expression must be int, got " + t);
    }
}
