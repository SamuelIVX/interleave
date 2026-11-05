/** Public Parser AST, assignment, malformed-input and nesting-boundary contracts. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.format.registry.RegistryException;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ParserContractTest {
    /** Checks exact syntax trees independently of type checking or evaluation.
     * @param input supported expression, including deliberate whitespace
     * @param expected literal AST preserving signed literals, identifiers and precedence
     */
    @ParameterizedTest @MethodSource("expressions")
    void expressionsRetainTheirLiteralAst(String input, Expr expected) {
        assertEquals(expected, new Parser(input).parseExpr());
    }

    /** Supplies signed-literal dispatch, lexical names, spacing and mixed precedence cases.
     * @return public expression inputs and independently stated ASTs
     */
    private static Stream<Arguments> expressions() {
        Expr comparison = new Expr.BinaryOp(new Expr.BinaryOp(new Expr.IntLit(1), "+",
            new Expr.BinaryOp(new Expr.IntLit(2), "*", new Expr.IntLit(3))), "<", new Expr.IntLit(8));
        return Stream.of(
            arguments("-1", new Expr.IntLit(-1)),
            arguments("-2147483648", new Expr.IntLit(-2147483648)),
            arguments("-alpha", new Expr.UnaryOp("-", new Expr.VarRef("alpha"))),
            arguments("a1", new Expr.VarRef("a1")),
            arguments("alpha_beta9", new Expr.VarRef("alpha_beta9")),
            arguments("local.alpha_beta9", new Expr.LocalRef("alpha_beta9")),
            arguments("local \t.alpha", new Expr.LocalRef("alpha")),
            arguments("local.alpha [0]", new Expr.ArrayAccess("local.alpha", new Expr.IntLit(0))),
            arguments("alpha [0]", new Expr.ArrayAccess("alpha", new Expr.IntLit(0))),
            arguments("( \t!true)", new Expr.UnaryOp("!", new Expr.BoolLit(true))),
            arguments(" 1 + 2 * 3 < 8 && true || false ",
                new Expr.BinaryOp(new Expr.BinaryOp(comparison, "&&", new Expr.BoolLit(true)),
                    "||", new Expr.BoolLit(false))));
    }

    /** All four assignment targets preserve their parsed index/value syntax.
     * @param input supported complete assignment
     * @param expected independently stated target and value
     */
    @ParameterizedTest @MethodSource("effects")
    void effectsRetainTheirLiteralAst(String input, Effect expected) {
        assertEquals(expected, new Parser(input).parseEffect());
    }

    /** Supplies scalar and indexed shared/local assignments, including nested index expressions.
     * @return complete assignments with literal ASTs
     */
    private static Stream<Arguments> effects() {
        Expr index = new Expr.BinaryOp(new Expr.IntLit(1), "+", new Expr.IntLit(2));
        return Stream.of(
            arguments(" counter = -2147483648 ",
                new Effect(new Lhs.FieldLhs("counter"), new Expr.IntLit(-2147483648))),
            arguments("local.alpha = -1", new Effect(new Lhs.LocalLhs("alpha"), new Expr.IntLit(-1))),
            arguments("values [1 + 2] = 3", new Effect(new Lhs.ArrayLhs("values", index), new Expr.IntLit(3))),
            arguments("local.items [1 + 2] = 3",
                new Effect(new Lhs.LocalArrayLhs("items", index), new Expr.IntLit(3))),
            arguments("values[local.items[0]] = local.alpha",
                new Effect(new Lhs.ArrayLhs("values", new Expr.ArrayAccess("local.items", new Expr.IntLit(0))),
                    new Expr.LocalRef("alpha"))));
    }

    /** Malformed expressions raise their deliberate diagnostic rather than incidental exceptions.
     * @param input malformed expression
     * @param message exact public diagnostic
     */
    @ParameterizedTest @MethodSource("expressionErrors")
    void expressionsKeepTheirExactErrors(String input, String message) {
        var failure = assertThrows(RegistryException.class, () -> new Parser(input).parseExpr());
        assertEquals(message, failure.getMessage());
    }

    /** Supplies EOF, missing operands/brackets, invalid names and unsupported division cases.
     * @return malformed expressions paired with literal messages
     */
    private static Stream<Arguments> expressionErrors() {
        String oversized = "a".repeat(65);
        return Stream.of(
            arguments("", "Empty expression"),
            arguments(" \t\r\n", "Empty expression"),
            arguments("-", "Unexpected end of expression"),
            arguments("(", "Unexpected end of expression"),
            arguments("1 +", "Unexpected end of expression"),
            arguments("values[", "Unexpected end of expression"),
            arguments("local.items[", "Unexpected end of expression"),
            arguments("(1", "Missing closing ')'"),
            arguments("values[0", "Missing ']' for array access"),
            arguments("local.items[0", "Missing ']' for local array access"),
            arguments("local.", "Expected identifier at pos 6"),
            arguments("local. alpha", "Expected identifier at pos 6"),
            arguments("local", "Expected '.' after 'local'"),
            arguments("BAD", "Invalid identifier 'BAD': must match [a-z][a-z0-9_]*"),
            arguments("local.BAD", "Invalid identifier 'BAD': must match [a-z][a-z0-9_]*"),
            arguments("a / b", "Unexpected trailing characters at pos 2: '/ b'"),
            arguments(oversized, "Identifier too long (>64): " + oversized),
            arguments("local." + oversized, "Identifier too long (>64): " + oversized));
    }

    /** Malformed assignments preserve the earliest target/value diagnostic.
     * @param input malformed complete assignment
     * @param message exact public diagnostic
     */
    @ParameterizedTest @MethodSource("effectErrors")
    void effectsKeepTheirExactErrors(String input, String message) {
        var failure = assertThrows(RegistryException.class, () -> new Parser(input).parseEffect());
        assertEquals(message, failure.getMessage());
    }

    /** Supplies empty sides/indices, malformed array targets and reserved/invalid target names.
     * @return malformed assignments paired with literal messages
     */
    private static Stream<Arguments> effectErrors() {
        return Stream.of(
            arguments("", "Empty effect"),
            arguments(" \t\r\n", "Empty effect"),
            arguments("counter", "Effect must contain '=': 'counter'"),
            arguments("=1", "Effect must be 'lhs = expr': '=1'"),
            arguments("counter =", "Effect must be 'lhs = expr': 'counter ='"),
            arguments("[0] = 1", "Empty identifier"),
            arguments("local.[0] = 1", "Empty identifier"),
            arguments("values[] = 1", "Array index empty: 'values[]'"),
            arguments("local.items[] = 1", "Array index empty: 'local.items[]'"),
            arguments("values[0]tail = 1", "Invalid array lhs: 'values[0]tail'"),
            arguments("local.items[0]tail = 1", "Invalid local array lhs: 'local.items[0]tail'"),
            arguments("BAD[0] = 1", "Invalid identifier 'BAD': must match [a-z][a-z0-9_]*"),
            arguments("local.BAD[0] = 1", "Invalid identifier 'BAD': must match [a-z][a-z0-9_]*"),
            arguments("local.BAD = 1", "Invalid identifier 'BAD': must match [a-z][a-z0-9_]*"),
            arguments("local = 1", "Reserved keyword cannot be used as identifier: local"),
            arguments("tid = 1", "Reserved keyword cannot be used as identifier: tid"));
    }

    /** Both lexical entry paths accept names immediately below and at the inclusive limit.
     * @param length literal identifier length
     */
    @ParameterizedTest @ValueSource(ints = {63, 64})
    void acceptsIdentifierLengthBoundary(int length) {
        String name = "a".repeat(length);
        assertEquals(new Expr.VarRef(name), new Parser(name).parseExpr());
        assertEquals(new Expr.LocalRef(name), new Parser("local." + name).parseExpr());
    }

    /** Every recursive grammar path accepts 64 wrappers and rejects the 65th deliberately.
     * @param kind parenthesis, unary or array wrapper
     * @param nodes independently counted AST nodes at the accepted boundary
     * @param depth independently counted AST depth at the accepted boundary
     */
    @ParameterizedTest
    @CsvSource({"parentheses, 1, 1", "not, 65, 65", "negative, 65, 65", "shared, 65, 65", "local, 65, 65"})
    void nestingLimitIsInclusiveForEveryRecursivePath(String kind, int nodes, int depth) {
        Expr accepted = new Parser(nested(kind, 64)).parseExpr();
        assertEquals(nodes, Expr.nodeCount(accepted));
        assertEquals(depth, Expr.depth(accepted));
        var failure = assertThrows(RegistryException.class, () -> new Parser(nested(kind, 65)).parseExpr());
        assertEquals("Expression nesting depth exceeds 64", failure.getMessage());
    }

    /** Independent siblings do not inherit a completed operand's nesting allowance.
     * @param atom one unary or indexed expression
     * @param operator operator joining the independent operands
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        !true          | &&
        -counter       | +
        values[0]      | +
        local.items[0] | +
        """)
    void completedOperandsRestoreNestingDepth(String atom, String operator) {
        String input = String.join(" " + operator + " ", Collections.nCopies(65, atom));
        Expr expression = new Parser(input).parseExpr();
        assertEquals(194, Expr.nodeCount(expression));
        assertEquals(66, Expr.depth(expression));
    }

    /** Builds only public input text; no private parser cursor or synthetic AST is injected.
     * @param kind recursive grammar wrapper
     * @param count number of wrappers around a literal or identifier
     * @return syntactically complete expression
     * @throws IllegalArgumentException if the wrapper is unknown
     */
    private static String nested(String kind, int count) {
        return switch (kind) {
            case "parentheses" -> "(".repeat(count) + "0" + ")".repeat(count);
            case "not" -> "!".repeat(count) + "true";
            case "negative" -> "-".repeat(count) + "counter";
            case "shared" -> "values[".repeat(count) + "0" + "]".repeat(count);
            case "local" -> "local.items[".repeat(count) + "0" + "]".repeat(count);
            default -> throw new IllegalArgumentException("Unknown nesting wrapper: " + kind);
        };
    }
}
