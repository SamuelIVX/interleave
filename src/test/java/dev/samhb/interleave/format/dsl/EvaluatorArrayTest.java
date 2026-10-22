/** Direct evaluator array-error contracts distinguish modeled failures from host exceptions. */
package dev.samhb.interleave.format.dsl;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class EvaluatorArrayTest {
    /** Invalid reads preserve the evaluator's exception type and array/index/length diagnostic.
     * @param target declared shared or local array reference
     * @param index invalid negative, length or empty-array index
     * @param length declared array length
     */
    @ParameterizedTest
    @CsvSource({"values, -1, 2", "values, 2, 2", "values, 0, 0",
        "local.items, -1, 2", "local.items, 2, 2", "local.items, 0, 0"})
    void invalidReadsThrowModeledBoundsErrors(String target, int index, int length) {
        DynamicState state = state(length);
        Expr expression = new Parser(target + "[" + index + "]").parseExpr();
        var error = assertThrowsExactly(Evaluator.EvalException.class, () -> Evaluator.eval(expression, state, 1));
        assertEquals("Array index out of bounds: " + target + "[" + index + "] length " + length, error.getMessage());
    }

    /** Invalid writes preserve the modeled exception and cannot modify either array namespace.
     * @param local whether the target is the owner's private array
     * @param index invalid negative, length or empty-array index
     * @param length declared array length
     */
    @ParameterizedTest
    @CsvSource({"false, -1, 2", "false, 2, 2", "false, 0, 0",
        "true, -1, 2", "true, 2, 2", "true, 0, 0"})
    void invalidWritesThrowModeledBoundsErrors(boolean local, int index, int length) {
        DynamicState state = state(length);
        int[] sharedBefore = state.getArray("values");
        int[] ownerBefore = state.getLocalArray(1, "items");
        Lhs target = local ? new Lhs.LocalArrayLhs("items", new Expr.IntLit(index))
            : new Lhs.ArrayLhs("values", new Expr.IntLit(index));
        var error = assertThrowsExactly(Evaluator.EvalException.class,
            () -> Evaluator.assign(new Effect(target, new Expr.IntLit(77)), state, 1));
        assertEquals("Array index out of bounds", error.getMessage());
        assertArrayEquals(sharedBefore, state.getArray("values"));
        assertArrayEquals(ownerBefore, state.getLocalArray(1, "items"));
        assertArrayEquals(ownerBefore, state.getLocalArray(0, "items"));
    }

    /** Creates distinct namespaces with equal per-thread initial copies.
     * @param length two elements or an empty array for boundary checks
     * @return fresh state with two modeled owners
     */
    private static DynamicState state(int length) {
        int[] contents = length == 0 ? new int[0] : new int[] {11, 22};
        return new DynamicState(new StateDecl(List.of(FieldDecl.ofArray("values", contents)),
            List.of(LocalDecl.ofArray("items", contents))), 2);
    }
}
