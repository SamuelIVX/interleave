/** Assignment subparser diagnostics retain their origin within the complete effect and source. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.dsl.Parser;
import dev.samhb.interleave.format.dsl.Effect;
import dev.samhb.interleave.format.dsl.Expr;
import dev.samhb.interleave.format.dsl.Lhs;
import dev.samhb.interleave.format.registry.RegistryException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SourceEffectDiagnosticOffsetTest {
    /**
     * Checks literal source coordinates across RHS and shared/local assignment-index fragments.
     * @param effect indented malformed assignment, with all source delimiters present
     * @param diagnostic independently enumerated original-source location and complete message
     */
    @ParameterizedTest
    @MethodSource("sourceCases")
    void sourceErrorsPointAtTheActualCharacter(String effect, String diagnostic) {
        String source = "program offsets {\nshared int counter = 0;\nshared int[] values = [0];\n"
            + "local int[] items = [0];\nthread 0 {\n  atomic {\n" + effect + "\n  }\n}\n}";
        RegistryException failure = assertThrows(RegistryException.class,
            () -> new SourceProgramLoader().load(source));
        assertEquals(diagnostic, failure.getMessage());
    }

    /**
     * Supplies positive offsets with whitespace, multiline fragments and a masked source comment.
     * @return complete assignments paired with literal source diagnostics
     */
    private static Stream<Arguments> sourceCases() {
        return Stream.of(
            arguments("    counter = true && @;", "<source>:7:23: syntax: Unexpected character '@' at pos 18"),
            arguments("    counter =   true && @;", "<source>:7:25: syntax: Unexpected character '@' at pos 20"),
            arguments("    values[true && @] = 0;", "<source>:7:20: syntax: Unexpected character '@' at pos 15"),
            arguments("    local.items[  true && @ ] = 0;", "<source>:7:27: syntax: Unexpected character '@' at pos 22"),
            arguments("    values[values[0] + @] = 0;", "<source>:7:24: syntax: Unexpected character '@' at pos 19"),
            arguments("    counter =\n      true && @;", "<source>:8:15: syntax: Unexpected character '@' at pos 24"),
            arguments("    counter = /* note */ true && @;", "<source>:7:34: syntax: Unexpected character '@' at pos 29"),
            arguments("    local.items[\n      true && @] = 0;", "<source>:8:15: syntax: Unexpected character '@' at pos 27"),
            arguments("    counter = local.0;", "<source>:7:21: syntax: Expected identifier at pos 16"),
            arguments("    counter = true false;", "<source>:7:20: syntax: Unexpected trailing characters at pos 15: 'false'")
        );
    }

    /**
     * Direct parser callers, including the JSON loader, receive positions in their whole input.
     * @param effect complete effect text, retaining leading whitespace or trim-stripped control prefixes
     * @param diagnostic literal message with a zero-based offset into that complete text
     */
    @ParameterizedTest
    @MethodSource("parserCases")
    void parserOffsetsIncludeTheWholeEffect(String effect, String diagnostic) {
        RegistryException failure = assertThrows(RegistryException.class, () -> new Parser(effect).parseEffect());
        assertEquals(diagnostic, failure.getMessage());
    }

    /**
     * Supplies leading-prefix cases independent of SourceCompiler's token-start slicing.
     * @return whole parser inputs paired with literal whole-input positions
     */
    private static Stream<Arguments> parserCases() {
        return Stream.of(
            arguments("   counter = true && @", "Unexpected character '@' at pos 21"),
            arguments("   values[ true && @ ] = 0", "Unexpected character '@' at pos 19"),
            arguments("   local.items[ true && @ ] = 0", "Unexpected character '@' at pos 24"),
            arguments("\0values[ true && @ ] = 0", "Unexpected character '@' at pos 17"),
            arguments("\0local.items[ true && @ ] = 0", "Unexpected character '@' at pos 22"),
            arguments("counter = \0 true && @ \0", "Unexpected character '@' at pos 20"),
            arguments("values[\0 true && @ \0] = 0", "Unexpected character '@' at pos 17"),
            arguments("local.items[\0 true && @ \0] = 0", "Unexpected character '@' at pos 22")
        );
    }

    /**
     * Boundary controls trimmed by the old parser must retain the same successful AST.
     * @param effect input with NUL/space boundaries around an RHS or assignment index
     * @param expected independent literal target/value structure
     */
    @ParameterizedTest
    @MethodSource("acceptedBoundaryCases")
    void boundaryTrimmingKeepsItsExistingAcceptance(String effect, Effect expected) {
        assertEquals(expected, new Parser(effect).parseEffect());
    }

    /**
     * Supplies valid RHS/shared/local fragments that depend on legacy String.trim behavior.
     * @return complete effect inputs and their literal expected ASTs
     */
    private static Stream<Arguments> acceptedBoundaryCases() {
        return Stream.of(
            arguments("counter = \0 1 \0", new Effect(new Lhs.FieldLhs("counter"), new Expr.IntLit(1))),
            arguments("values[\0 0 \0] = 1", new Effect(new Lhs.ArrayLhs("values", new Expr.IntLit(0)), new Expr.IntLit(1))),
            arguments("local.items[\0 0 \0] = 1", new Effect(new Lhs.LocalArrayLhs("items", new Expr.IntLit(0)), new Expr.IntLit(1)))
        );
    }

}
