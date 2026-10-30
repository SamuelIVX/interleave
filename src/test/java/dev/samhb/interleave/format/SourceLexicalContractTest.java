/** Independent source-name, comment-boundary and end-of-input diagnostic contracts. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceLexicalContractTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** Program names do not pass through the declaration loader's separate identifier check. */
    @Test void programNameLengthIsInclusive() {
        assertEquals("a", loader.load(model("a")).name());
        String maximum = "a".repeat(64);
        assertEquals(maximum, loader.load(model(maximum)).name());
        assertRejected(model(maximum + "a"),
            "<source>:1:9: name: Expected a nonreserved lowercase identifier");
    }

    /**
     * Every keyword in the language specification remains reserved as a program name.
     * @param keyword reserved source word from Spec16.01
     */
    @ParameterizedTest
    @ValueSource(strings = {"program", "shared", "local", "thread", "atomic", "when", "if", "else",
        "repeat", "while", "limit", "invariant", "always", "final", "expected", "int", "bool", "true", "false", "tid"})
    void rejectsReservedProgramNames(String keyword) {
        assertRejected(model(keyword), "<source>:1:9: name: Expected a nonreserved lowercase identifier");
    }

    /**
     * Tokenizable names still obey the lowercase ASCII identifier grammar.
     * @param identifier invalid candidate that starts at the ninth source column
     */
    @ParameterizedTest
    @ValueSource(strings = {"Bad", "_bad", "9bad", "é"})
    void rejectsInvalidProgramNamesAtTheirOriginalColumn(String identifier) {
        assertRejected(model(identifier), "<source>:1:9: name: Expected a nonreserved lowercase identifier");
    }

    /**
     * Consecutive comments cannot expose the second comment when the first reaches a newline.
     * @param newline supported single line break, including CRLF as one break
     */
    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r", "\r\n"})
    void lineCommentsRetainTheNextCommentBoundary(String newline) {
        assertEquals("lexical", loader.load("// first" + newline + "// second" + newline + model("lexical")).name());
    }

    /**
     * Truncated block comments report the opening span, including a final unmatched star.
     * @param comment unterminated block comment appended at the first column of line two
     */
    @ParameterizedTest
    @ValueSource(strings = {"/*", "/* unfinished *"})
    void unterminatedCommentsKeepTheirOpeningLocation(String comment) {
        assertRejected(model("lexical") + "\n" + comment,
            "<source>:2:1: syntax: Unterminated block comment");
    }

    /** A final lone slash is a trailing token, without an out-of-bounds comment lookahead. */
    @Test void trailingPunctuationReportsExpectedEofAtTheToken() {
        assertRejected(model("lexical") + "\n/", "<source>:2:1: syntax: Expected 'EOF'");
    }

    /** A missing program brace reports the actual EOF after the final newline. */
    @Test void truncatedProgramRetainsTheEofLocation() {
        assertRejected("program lexical {\n shared int counter = 0;\n thread 0 {}\n",
            "<source>:4:1: syntax: Expected '}'");
    }

    /** Equality cannot be split to consume half of an unexpected declaration operator. */
    @Test void declarationEqualityRemainsOneUnexpectedToken() {
        assertRejected("program lexical { shared int counter == 0; thread 0 {} }",
            "<source>:1:38: syntax: Expected '='");
    }

    /**
     * Wraps an identifier in an otherwise valid one-owner source model.
     * @param identifier candidate program name
     * @return source with no scheduled actions
     */
    private static String model(String identifier) {
        return "program " + identifier + " { shared int counter = 0; thread 0 {} }";
    }

    /**
     * Requires a categorized source failure rather than an incidental runtime exception.
     * @param source complete input to the public loader
     * @param diagnostic independent literal diagnostic with original one-based coordinates
     */
    private void assertRejected(String source, String diagnostic) {
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(source));
        assertEquals(diagnostic, failure.getMessage());
    }
}
