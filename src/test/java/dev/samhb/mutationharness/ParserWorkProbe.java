/** Child-JVM oracles for real parser behavior and exact PIT-only progress assertions. */
package dev.samhb.mutationharness;

import dev.samhb.interleave.format.dsl.Effect;
import dev.samhb.interleave.format.dsl.Expr;
import dev.samhb.interleave.format.dsl.Lhs;
import dev.samhb.interleave.format.dsl.Parser;
import dev.samhb.interleave.format.registry.RegistryException;

/** Isolated process fixture outside PIT's application-test filter. */
public final class ParserWorkProbe {
    /** Prevents construction of this process entry point. */
    private ParserWorkProbe() {}

    /**
     * Checks valid parser boundaries or an exact logical failure under an actual bytecode mutant.
     * @param args one supported boundary or mutation mode
     * @throws IllegalArgumentException for an unsupported mode
     * @throws IllegalStateException if a requested logical assertion does not fire
     * @throws AssertionError if behavior or the logical diagnostic differs
     */
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Expected one parser probe mode");
        if (args[0].equals("boundaries")) {
            boundaries();
            System.out.println("PARSER_BOUNDARIES");
            return;
        }
        String input = switch (args[0]) {
            case "parseIdent" -> "local.alpha";
            case "parseWord" -> "alpha";
            case "parseIntLit" -> "123";
            case "match" -> "1==2";
            default -> throw new IllegalArgumentException("Unknown parser probe mode");
        };
        String expected = args[0].equals("match") ? "Parser operator matching moved cursor backwards"
            : "Parser lexical scan exceeded twice input units plus three";
        try {
            new Parser(input).parseExpr();
            throw new IllegalStateException("Expected logical parser assertion");
        } catch (AssertionError failure) {
            if (!expected.equals(failure.getMessage())) throw failure;
            System.out.println("BOUND_EXCEEDED");
        }
    }

    /**
     * Confirms inclusive identifiers, literal extremes, operator alternatives and trim-compatible effects.
     * @throws AssertionError if the actual production parser changes an independently stated AST
     */
    private static void boundaries() {
        String identifier = "a" + "_".repeat(63);
        if (identifier.length() != 64) throw new AssertionError("Wrong identifier fixture length");
        expect(identifier, new Expr.VarRef(identifier));
        expect("local." + identifier, new Expr.LocalRef(identifier));
        expect("-2147483648", new Expr.IntLit(-2147483648));
        expect("2147483647", new Expr.IntLit(2147483647));
        expect("0".repeat(128) + "1", new Expr.IntLit(1));
        expect("a_1", new Expr.VarRef("a_1"));
        expect("false", new Expr.BoolLit(false));
        expect("1==2", new Expr.BinaryOp(new Expr.IntLit(1), "==", new Expr.IntLit(2)));
        expect("true&&false", new Expr.BinaryOp(new Expr.BoolLit(true), "&&", new Expr.BoolLit(false)));
        expect("1+2*3", new Expr.BinaryOp(new Expr.IntLit(1), "+",
            new Expr.BinaryOp(new Expr.IntLit(2), "*", new Expr.IntLit(3))));
        Effect actual = new Parser("values[\0 0 \0] = \0 1 \0").parseEffect();
        Effect expected = new Effect(new Lhs.ArrayLhs("values", new Expr.IntLit(0)), new Expr.IntLit(1));
        if (!expected.equals(actual)) throw new AssertionError("Wrong boundary-control effect");
        for (String invalid : new String[] {"", "@", identifier + "_", "2147483648"}) {
            try {
                new Parser(invalid).parseExpr();
                throw new AssertionError("Invalid parser input was accepted");
            } catch (RegistryException expectedFailure) { /* Original validation remains in charge. */ }
        }
    }

    /**
     * Compares the actual parser result with an independent literal expression structure.
     * @param input complete expression text
     * @param expected literal expected AST
     * @throws AssertionError if parsing changes the expected value or structure
     */
    private static void expect(String input, Expr expected) {
        if (!expected.equals(new Parser(input).parseExpr())) throw new AssertionError("Wrong parser AST");
    }
}
