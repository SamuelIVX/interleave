/** Evaluates declarative expressions against a {@link DynamicState}. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.format.registry.RegistryException;

/**
 * Evaluates declarative expressions against a {@link DynamicState}.
 */
public final class Evaluator {

    /** Prevents instantiation of this utility class. */
    private Evaluator() {}

    /**
     * Evaluation result.
     */
    public sealed interface Value permits IntVal, BoolVal {
        /**
         * Returns int value if this is IntVal.
         * @return integer payload
         */
        int asInt();
        /**
         * Returns bool value if this is BoolVal.
         * @return boolean payload
         */
        boolean asBool();
        /**
         * Returns type.
         * @return INT or BOOL according to the represented payload
         */
        FieldType type();
    }

    /**
     * Integer value.
     * @param value literal payload
     */
    public record IntVal(int value) implements Value {
        /**
         * Returns the integer payload or rejects conversion from a boolean value.
         * @return integer payload
         */
        @Override public int asInt() { return value; }
        /**
         * Returns the boolean payload or rejects conversion from an integer value.
         * @return boolean payload
         * @throws IllegalStateException if the payload has the other DSL value category
         */
        @Override public boolean asBool() { throw new IllegalStateException("not bool"); }
        /**
         * Returns type for this int val.
         * @return INT or BOOL according to the represented payload
         */
        @Override public FieldType type() { return FieldType.INT; }
    }

    /**
     * Boolean value.
     * @param value literal payload
     */
    public record BoolVal(boolean value) implements Value {
        /**
         * Returns the integer payload or rejects conversion from a boolean value.
         * @return integer payload
         * @throws IllegalStateException if the payload has the other DSL value category
         */
        @Override public int asInt() { throw new IllegalStateException("not int"); }
        /**
         * Returns the boolean payload or rejects conversion from an integer value.
         * @return boolean payload
         */
        @Override public boolean asBool() { return value; }
        /**
         * Returns type for this bool val.
         * @return INT or BOOL according to the represented payload
         */
        @Override public FieldType type() { return FieldType.BOOL; }
    }

    /**
     * Thrown for runtime evaluation errors (OOB, % by zero) that should surface as VIOLATION.
     */
    public static final class EvalException extends RuntimeException {
        /**
         * Creates eval exception from the supplied values.
         * @param msg diagnostic explaining the failure
         */
        public EvalException(String msg) { super(msg); }
    }

    /**
     * Evaluates expression.
     *
     * @param expr expression
     * @param state dynamic state
     * @param tid owning thread id
     * @return value
     * @throws EvalException on OOB or % by zero
     */
    public static Value eval(Expr expr, DynamicState state, int tid) {
        if (expr instanceof Expr.IntLit i) return new IntVal(i.value());
        if (expr instanceof Expr.BoolLit b) return new BoolVal(b.value());
        if (expr instanceof Expr.TidRef) return new IntVal(tid);
        if (expr instanceof Expr.VarRef v) {
            FieldDecl f = state.decl().findField(v.name());
            if (f == null) throw new RegistryException("Unknown field '" + v.name() + "'");
            if (f.type() == FieldType.INT) return new IntVal(state.getInt(v.name()));
            if (f.type() == FieldType.BOOL) return new BoolVal(state.getBool(v.name()));
            throw new RegistryException("Field '" + v.name() + "' is array; use " + v.name() + "[index]");
        }
        if (expr instanceof Expr.LocalRef l) {
            LocalDecl ld = state.decl().findLocal(l.name());
            if (ld == null) throw new RegistryException("Unknown local '" + l.name() + "'");
            if (ld.type() == FieldType.INT) return new IntVal(state.getLocalInt(tid, l.name()));
            return new BoolVal(state.getLocalBool(tid, l.name()));
        }
        if (expr instanceof Expr.ArrayAccess a) {
            // local array access is not allowed (locals scalar only) — will have been rejected at type-check,
            // but handle "local.name" case for robustness
            if (a.arrayName().startsWith("local.")) {
                throw new EvalException("Local array access not supported: " + a.arrayName());
            }
            FieldDecl f = state.decl().findField(a.arrayName());
            if (f == null || f.type() != FieldType.INT_ARRAY) throw new RegistryException("Unknown array field '" + a.arrayName() + "'");
            Value idxVal = eval(a.index(), state, tid);
            if (idxVal.type() != FieldType.INT) throw new RegistryException("Array index must be int");
            int idx = idxVal.asInt();
            int[] arr = state.getArray(a.arrayName());
            if (idx < 0 || idx >= arr.length) throw new EvalException("Array index out of bounds: " + a.arrayName() + "[" + idx + "] length " + arr.length);
            return new IntVal(arr[idx]);
        }
        if (expr instanceof Expr.UnaryOp u) {
            Value v = eval(u.operand(), state, tid);
            return switch (u.op()) {
                case "!" -> {
                    if (v.type() != FieldType.BOOL) throw new RegistryException("! requires bool");
                    yield new BoolVal(!v.asBool());
                }
                case "-" -> {
                    if (v.type() != FieldType.INT) throw new RegistryException("- requires int");
                    yield new IntVal(-v.asInt());
                }
                default -> throw new RegistryException("Unknown unary op " + u.op());
            };
        }
        if (expr instanceof Expr.BinaryOp b) {
            // short-circuit for && ||
            if ("&&".equals(b.op()) || "||".equals(b.op())) {
                Value left = eval(b.left(), state, tid);
                if (left.type() != FieldType.BOOL) throw new RegistryException(b.op() + " requires bool");
                boolean lv = left.asBool();
                if ("&&".equals(b.op()) && !lv) return new BoolVal(false);
                if ("||".equals(b.op()) && lv) return new BoolVal(true);
                Value right = eval(b.right(), state, tid);
                if (right.type() != FieldType.BOOL) throw new RegistryException(b.op() + " requires bool");
                boolean rv = right.asBool();
                return new BoolVal("&&".equals(b.op()) ? (lv && rv) : (lv || rv));
            }
            Value left = eval(b.left(), state, tid);
            Value right = eval(b.right(), state, tid);
            return switch (b.op()) {
                case "+" -> {
                    if (left.type() != FieldType.INT || right.type() != FieldType.INT) throw new RegistryException("+ requires int");
                    yield new IntVal(left.asInt() + right.asInt());
                }
                case "-" -> {
                    if (left.type() != FieldType.INT || right.type() != FieldType.INT) throw new RegistryException("- requires int");
                    yield new IntVal(left.asInt() - right.asInt());
                }
                case "*" -> {
                    if (left.type() != FieldType.INT || right.type() != FieldType.INT) throw new RegistryException("* requires int");
                    yield new IntVal(left.asInt() * right.asInt());
                }
                case "%" -> {
                    if (left.type() != FieldType.INT || right.type() != FieldType.INT) throw new RegistryException("% requires int");
                    int rv = right.asInt();
                    if (rv == 0) throw new EvalException("% by zero");
                    yield new IntVal(left.asInt() % rv);
                }
                case "==" -> {
                    if (left.type() != right.type()) throw new RegistryException("== requires same type");
                    if (left.type() == FieldType.INT) yield new BoolVal(left.asInt() == right.asInt());
                    yield new BoolVal(left.asBool() == right.asBool());
                }
                case "!=" -> {
                    if (left.type() != right.type()) throw new RegistryException("!= requires same type");
                    if (left.type() == FieldType.INT) yield new BoolVal(left.asInt() != right.asInt());
                    yield new BoolVal(left.asBool() != right.asBool());
                }
                case "<" -> {
                    if (left.type() != FieldType.INT || right.type() != FieldType.INT) throw new RegistryException("< requires int");
                    yield new BoolVal(left.asInt() < right.asInt());
                }
                case "<=" -> {
                    if (left.type() != FieldType.INT || right.type() != FieldType.INT) throw new RegistryException("<= requires int");
                    yield new BoolVal(left.asInt() <= right.asInt());
                }
                case ">" -> {
                    if (left.type() != FieldType.INT || right.type() != FieldType.INT) throw new RegistryException("> requires int");
                    yield new BoolVal(left.asInt() > right.asInt());
                }
                case ">=" -> {
                    if (left.type() != FieldType.INT || right.type() != FieldType.INT) throw new RegistryException(">= requires int");
                    yield new BoolVal(left.asInt() >= right.asInt());
                }
                default -> throw new RegistryException("Unknown binary op " + b.op());
            };
        }
        throw new RegistryException("Unknown expr type");
    }
}
