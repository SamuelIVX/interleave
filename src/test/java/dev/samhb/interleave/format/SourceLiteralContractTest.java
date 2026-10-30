/** Literal-range, declaration-type and duplicate-name diagnostics at original source tokens. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceLiteralContractTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /**
     * Counts distinguish malformed literals from decimal values beyond the signed-int maximum.
     * @param count invalid thread-ID literal at line three, column nine
     * @param diagnostic independent literal category, message and source coordinates
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        -1 | <source>:3:9: syntax: Expected a nonnegative literal count
        true | <source>:3:9: syntax: Expected a nonnegative literal count
        2147483648 | <source>:3:9: limit: Count exceeds 2147483647
        """)
    void countsRetainTheirCategoryAndTokenLocation(String count, String diagnostic) {
        assertRejected("program literals {\n shared int counter = 0;\n thread " + count + " {}\n}", diagnostic);
    }

    /**
     * Declaration failures keep their category and original token, including after unary minus.
     * @param declarations declarations beginning at line two, column one
     * @param diagnostic independent literal category, message and source coordinates
     */
    @ParameterizedTest
    @MethodSource("declarationErrors")
    void declarationsRetainTheirCategoryAndTokenLocation(String declarations, String diagnostic) {
        assertRejected("program literals {\n" + declarations + "\nthread 0 {}\n}", diagnostic);
    }

    /**
     * Supplies invalid type, literal and cross-scope uniqueness cases with hand-counted positions.
     * @return otherwise well-formed declaration fragments and exact diagnostic oracles
     */
    private static Stream<Arguments> declarationErrors() {
        return Stream.of(
            Arguments.of("shared float value = 0;", "<source>:2:8: type: Expected int or bool"),
            Arguments.of("shared int counter = 0;\nlocal float scratch = 0;",
                "<source>:3:7: type: Expected int or bool"),
            Arguments.of("shared bool[] flags = [];", "<source>:2:13: type: Only int arrays are supported"),
            Arguments.of("shared int counter = missing;", "<source>:2:22: syntax: Expected integer initializer"),
            Arguments.of("shared int counter = -missing;", "<source>:2:23: syntax: Expected integer initializer"),
            Arguments.of("shared int[] values = [true];", "<source>:2:24: syntax: Expected integer initializer"),
            Arguments.of("shared int counter = 2147483648;",
                "<source>:2:22: type: Integer initializer outside signed int range"),
            Arguments.of("shared int counter = -2147483649;",
                "<source>:2:23: type: Integer initializer outside signed int range"),
            Arguments.of("shared int counter = 0;\nshared int counter = 1;",
                "<source>:3:12: name: Duplicate declaration 'counter'"),
            Arguments.of("shared int counter = 0;\nlocal int counter = 1;",
                "<source>:3:11: name: Duplicate declaration 'counter'"),
            Arguments.of("shared int counter = 0;\nlocal int scratch = 1;\nlocal int scratch = 2;",
                "<source>:4:11: name: Duplicate declaration 'scratch'")
        );
    }

    /**
     * Requires the public categorized diagnostic rather than an incidental runtime exception.
     * @param source input to the source loader
     * @param diagnostic independently counted expected diagnostic
     */
    private void assertRejected(String source, String diagnostic) {
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(source));
        assertEquals(diagnostic, failure.getMessage());
    }
}
