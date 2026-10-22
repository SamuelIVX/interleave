/** Direct malformed-AST rejection and stable failure-category contracts for the shared evaluator. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.FailureEvidence.Category;
import dev.samhb.interleave.format.registry.RegistryException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class EvaluatorDefensesTest {
    /** Rejection preserves the diagnostic's actual reference/operator rather than an accidental null.
     * @param expression manually constructed AST rejected by the evaluator
     * @param expected literal diagnostic for the selected defensive branch
     */
    @ParameterizedTest
    @MethodSource("invalidExpressions")
    void rejectsMalformedExpressionsWithOwnedDiagnostics(Expr expression, String expected) {
        var error = assertThrowsExactly(RegistryException.class, () -> Evaluator.eval(expression, state(), 0));
        assertEquals(expected, error.getMessage());
    }

    /** Supplies name, namespace, operator and boolean-operand errors that bypass loader validation.
     * @return malformed ASTs with independently specified literal rejection diagnostics
     */
    private static Stream<Arguments> invalidExpressions() {
        return Stream.of(
            Arguments.of(new Expr.VarRef("missing"), "Unknown field 'missing'"),
            Arguments.of(new Expr.VarRef("values"), "Field 'values' is array; use values[index]"),
            Arguments.of(new Expr.LocalRef("missing"), "Unknown local 'missing'"),
            Arguments.of(new Expr.LocalRef("items"), "Local 'items' is array; use local.items[index]"),
            Arguments.of(new Expr.ArrayAccess("missing", new Expr.IntLit(0)), "Unknown array field 'missing'"),
            Arguments.of(new Expr.ArrayAccess("count", new Expr.IntLit(0)), "Unknown array field 'count'"),
            Arguments.of(new Expr.ArrayAccess("local.missing", new Expr.IntLit(0)), "Unknown local array 'missing'"),
            Arguments.of(new Expr.ArrayAccess("local.scratch", new Expr.IntLit(0)), "Unknown local array 'scratch'"),
            Arguments.of(new Expr.UnaryOp("~", new Expr.IntLit(7)), "Unknown unary op ~"),
            Arguments.of(new Expr.BinaryOp(new Expr.IntLit(7), "/", new Expr.IntLit(2)), "Unknown binary op /"),
            Arguments.of(new Expr.BinaryOp(new Expr.IntLit(1), "&&", new Expr.BoolLit(true)), "&& requires bool"),
            Arguments.of(new Expr.BinaryOp(new Expr.BoolLit(true), "&&", new Expr.IntLit(1)), "&& requires bool"),
            Arguments.of(new Expr.BinaryOp(new Expr.IntLit(1), "||", new Expr.BoolLit(false)), "|| requires bool"),
            Arguments.of(new Expr.BinaryOp(new Expr.BoolLit(false), "||", new Expr.IntLit(1)), "|| requires bool")
        );
    }

    /** A null assignment target is rejected before any declared destination can change. */
    @Test
    void rejectsNullAssignmentTargetWithoutWritingState() {
        DynamicState state = state();
        assertThrowsExactly(NullPointerException.class,
            () -> Evaluator.assign(new Effect(null, new Expr.IntLit(99)), state, 0));
        assertEquals(7, state.getInt("count"));
        assertEquals(3, state.getLocalInt(0, "scratch"));
        assertArrayEquals(new int[] {1, 2}, state.getArray("values"));
        assertArrayEquals(new int[] {4, 5}, state.getLocalArray(0, "items"));
    }

    /** Classification uses owned prefixes and exception types, accepting a null diagnostic message.
     * @param error non-null evaluation failure with the selected type/message combination
     * @param expected literal stable category, independent of the classifier implementation
     */
    @ParameterizedTest
    @MethodSource("classificationCases")
    void classifiesFailureTypesAndMessages(RuntimeException error, Category expected) {
        assertEquals(expected, Evaluator.failureCategory(error));
    }

    /** Supplies prefix, type, null-message, unrelated-text and precedence cases.
     * @return failure instances paired with literal category expectations
     */
    private static Stream<Arguments> classificationCases() {
        return Stream.of(
            Arguments.of(new Evaluator.EvalException("% by zero"), Category.MODULO_BY_ZERO),
            Arguments.of(new ArithmeticException("% by zero"), Category.MODULO_BY_ZERO),
            Arguments.of(new Evaluator.EvalException("Array index out of bounds: values[2] length 2"), Category.ARRAY_BOUNDS),
            Arguments.of(new IndexOutOfBoundsException("unrelated"), Category.ARRAY_BOUNDS),
            Arguments.of(new IndexOutOfBoundsException((String) null), Category.ARRAY_BOUNDS),
            Arguments.of(new RuntimeException("unrelated"), Category.OTHER_EVALUATION_ERROR),
            Arguments.of(new RuntimeException(), Category.OTHER_EVALUATION_ERROR),
            Arguments.of(new RuntimeException("prefix % by zero"), Category.OTHER_EVALUATION_ERROR),
            Arguments.of(new IndexOutOfBoundsException("% by zero"), Category.MODULO_BY_ZERO)
        );
    }

    /** Creates declared shared/private scalar and array namespaces for direct malformed-AST calls.
     * @return fresh state whose initialized values distinguish an unintended write
     */
    private static DynamicState state() {
        return new DynamicState(new StateDecl(
            List.of(FieldDecl.ofInt("count", 7), FieldDecl.ofArray("values", new int[] {1, 2})),
            List.of(LocalDecl.ofInt("scratch", 3), LocalDecl.ofArray("items", new int[] {4, 5}))), 1);
    }
}
