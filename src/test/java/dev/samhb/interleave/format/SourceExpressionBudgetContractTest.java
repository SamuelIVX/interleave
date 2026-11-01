/** Inclusive expression-depth/node limits before semantic visitors and aggregate work checks. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceExpressionBudgetContractTest {
    /** Shared integer/array declarations place the first owner on line four. */
    private static final String PREFIX = "program budgets {\nshared int counter = 0;\nshared int[] values = [0];\n";
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /**
     * Each expression edge kind accepts depth16 and rejects depth17 at the assignment start.
     * @param shape left/right binary spine, unary spine or nested array indices
     */
    @ParameterizedTest
    @ValueSource(strings = {"left", "right", "unary", "array"})
    void rhsDepthLimitTraversesEveryEdgeKind(String shape) {
        assertDoesNotThrow(() -> loader.load(assignment("counter", integerExpression(shape, 15))));
        assertRejected(assignment("counter", integerExpression(shape, 16)),
            "<source>:6:1: limit: Expression depth exceeds 16");
    }

    /** Assignment targets need their own depth check independently of a shallow RHS. */
    @Test void arrayTargetDepthLimitIncludesTheOuterAccess() {
        assertDoesNotThrow(() -> loader.load(assignment(arrayAccess(15), "0")));
        assertRejected(assignment(arrayAccess(16), "0"), "<source>:6:1: limit: Expression depth exceeds 16");
    }

    /** Conditions validate their own expression before recursive boolean type checking. */
    @Test void conditionDepthLimitIsInclusiveAndKeepsItsOriginalDiagnostic() {
        assertDoesNotThrow(() -> loader.load(condition("!".repeat(15) + "true")));
        assertRejected(condition("!".repeat(16) + "true"), "<source>:5:5: limit: Expression depth exceeds 16");
    }

    /** Balanced trees isolate the inclusive node limit from both depth and aggregate rejection. */
    @Test void perExpressionNodeLimitAccepts5000AndRejects5001AtTheExpression() {
        var accepted = assertDoesNotThrow(() -> loader.load(property("!" + balancedBoolean(2500))));
        assertTrue(accepted.invariant().isPresent());
        assertRejected(property(balancedBoolean(2501)), "<source>:5:19: limit: Expression nodes exceed 5000");
    }

    /**
     * Creates a typed integer expression with exactly one more depth level than its edge count.
     * @param shape expression edge kind
     * @param edges number of nested binary/unary/index edges
     * @return source expression rooted at a declared field, or nested zero-valued array indices
     */
    private static String integerExpression(String shape, int edges) {
        return switch (shape) {
            case "left" -> "counter+".repeat(edges) + "counter";
            case "right" -> "counter+(".repeat(edges) + "counter" + ")".repeat(edges);
            case "unary" -> "-".repeat(edges) + "counter";
            case "array" -> arrayAccess(edges);
            default -> throw new IllegalArgumentException("Unknown fixture shape: " + shape);
        };
    }

    /**
     * Constructs nested accesses that remain well typed as either an expression or assignment LHS.
     * @param accesses number of array-access nodes around the zero leaf
     * @return source with depth accesses+1 and no independent arithmetic growth
     */
    private static String arrayAccess(int accesses) {
        return "values[".repeat(accesses) + "0" + "]".repeat(accesses);
    }

    /**
     * Builds a full balanced binary tree without exceeding the depth limit at the chosen sizes.
     * @param leaves positive number of true literals
     * @return boolean expression with leaves literals and leaves-1 binary nodes
     */
    private static String balancedBoolean(int leaves) {
        if (leaves == 1) return "true";
        int left = leaves / 2;
        return "(" + balancedBoolean(left) + "&&" + balancedBoolean(leaves - left) + ")";
    }

    /**
     * Places the assignment token at line six, column one inside one atomic action.
     * @param target scalar or array LHS
     * @param rhs integer-valued RHS
     * @return complete source program
     */
    private static String assignment(String target, String rhs) {
        return PREFIX + "thread 0 {\natomic {\n" + target + " = " + rhs + ";\n}\n}\n}";
    }

    /**
     * Places a scheduled boolean expression at line five, column five.
     * @param expression boolean condition
     * @return complete source program with an empty conditional body
     */
    private static String condition(String expression) {
        return PREFIX + "thread 0 {\nif (" + expression + ") {}\n}\n}";
    }

    /**
     * Isolates one property expression without any instruction expressions in the global budget.
     * @param expression boolean property body
     * @return complete source with the expression at line five, column nineteen
     */
    private static String property(String expression) {
        return PREFIX + "thread 0 {}\ninvariant always (" + expression + ");\n}";
    }

    /**
     * Requires the exact frontend limit error, not a later aggregate-budget rejection.
     * @param source complete source program
     * @param diagnostic independent literal category/message/position
     */
    private void assertRejected(String source, String diagnostic) {
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(source));
        assertEquals(diagnostic, failure.getMessage());
    }
}
