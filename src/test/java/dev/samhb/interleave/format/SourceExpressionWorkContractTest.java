/** Aggregate expression occurrences across properties, guards, branches and bounded repetition. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceExpressionWorkContractTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /**
     * Independently chosen property/body counts reach exactly5,000 and then5,001 occurrences.
     * @param body bounded typed source body
     * @param acceptedPropertyNodes literal property-node count filling the inclusive aggregate cap
     * @param rejectedPropertyNodes literal property-node count exceeding the aggregate cap by one
     */
    @ParameterizedTest
    @MethodSource("bodyBudgets")
    void propertiesAndBodiesShareOneAggregateBudget(String body, int acceptedPropertyNodes, int rejectedPropertyNodes) {
        assertDoesNotThrow(() -> loader.load(source(body, booleanExpression(acceptedPropertyNodes))));
        assertRejected(source(body, booleanExpression(rejectedPropertyNodes)));
    }

    /**
     * Repeated immutable expressions count per occurrence rather than by unique AST identity.
     * @param copies literal repetition factor
     * @param leaves literal integer-tree leaf count making copies times body work exactly5,000
     */
    @ParameterizedTest
    @CsvSource({"1, 2500", "2, 1250"})
    void repeatedAssignmentsRetainEveryTargetAndRhsOccurrence(int copies, int leaves) {
        String body = "repeat " + copies + " { atomic { counter = " + balanced("counter", "+", leaves) + "; } }";
        assertDoesNotThrow(() -> loader.load(source(body, null)));
        assertRejected(source(body, "true"));
    }

    /**
     * Supplies independently counted contributions, including final loop checks and unchosen arms.
     * @return body/property-count triples with adjacent accepted/rejected aggregate totals
     */
    private static Stream<Arguments> bodyBudgets() {
        return Stream.of(
            Arguments.of("atomic { counter = 0; }", 4998, 4999),
            Arguments.of("atomic when (true) { counter = 0; }", 4997, 4998),
            Arguments.of("if (true) { atomic { counter = 0; } } else { atomic { counter = 1; } }", 4995, 4996),
            Arguments.of("while (true) limit 0 {}", 4999, 5000),
            Arguments.of("while (true) limit 1 { atomic { counter = 0; } }", 4996, 4997),
            Arguments.of("repeat 64 { if (true) {} }", 4936, 4937)
        );
    }

    /**
     * Builds an odd full binary tree, adding one unary node for an even requested count.
     * @param nodes positive fixture count no greater than5,000
     * @return typed boolean source with exactly the requested node count
     */
    private static String booleanExpression(int nodes) {
        String tree = balanced("true", "&&", (nodes + 1) / 2);
        return nodes % 2 == 0 ? "!" + tree : tree;
    }

    /**
     * Creates full balanced trees so all fixtures stay below the separate expression-depth limit.
     * @param leaf typed literal or declared variable spelling
     * @param operator matching boolean/integer binary operator
     * @param leaves positive number of leaves
     * @return source with leaves literals/references and leaves-1 binary operations
     */
    private static String balanced(String leaf, String operator, int leaves) {
        if (leaves == 1) return leaf;
        int left = leaves / 2;
        return "(" + balanced(leaf, operator, left) + operator + balanced(leaf, operator, leaves - left) + ")";
    }

    /**
     * Keeps the modeled owner keyword at line three, column one for aggregate-budget diagnostics.
     * @param body bounded owner body
     * @param property optional shared boolean predicate, or null to omit it
     * @return complete source model with one shared integer and one owner
     */
    private static String source(String body, String property) {
        return "program work {\nshared int counter = 0;\nthread 0 {\n" + body + "\n}\n"
            + (property == null ? "" : "invariant always (" + property + ");\n") + "}";
    }

    /**
     * Requires aggregate rejection at the owner, not a local expression/expansion failure.
     * @param source complete otherwise valid source model
     */
    private void assertRejected(String source) {
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(source));
        assertEquals("<source>:3:1: limit: Expanded expression nodes exceed 5000", failure.getMessage());
    }
}
