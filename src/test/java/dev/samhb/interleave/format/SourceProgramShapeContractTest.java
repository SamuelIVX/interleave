/** Required source sections, inclusive shape limits and expected-verdict metadata contracts. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceProgramShapeContractTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** A thread cannot substitute for the required first shared declaration. */
    @Test void requiresASharedDeclarationBeforeThreads() {
        assertRejected(source("thread 0 {}"), "<source>:2:1: syntax: Expected a shared declaration");
    }

    /** Empty modeled ownership is rejected before Program construction with a source diagnostic. */
    @Test void requiresAtLeastOneThread() {
        assertRejected(source("shared int counter = 0;"), "<source>:3:1: syntax: At least one thread is required");
    }

    /** The 33rd shared declaration must hit the frontend limit rather than a downstream type error. */
    @Test void sharedDeclarationOverflowRetainsItsLimitCategoryAndLocation() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 33; i++) body.append("shared int f").append(i).append(" = 0;\n");
        assertRejected(source(body + "thread 0 {}"), "<source>:34:1: limit: At most 32 shared declarations");
    }

    /** The ninth local declaration must hit the frontend limit at its own keyword. */
    @Test void localDeclarationOverflowRetainsItsLimitCategoryAndLocation() {
        StringBuilder body = new StringBuilder("shared int counter = 0;\n");
        for (int i = 0; i < 9; i++) body.append("local int l").append(i).append(" = 0;\n");
        assertRejected(source(body + "thread 0 {}"), "<source>:11:1: limit: At most 8 local declarations");
    }

    /**
     * The first owner must be zero, and later IDs cannot repeat or leave gaps.
     * @param threads complete owner declarations
     * @param diagnostic independently counted literal location of the offending ID
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        thread 1 {} | <source>:3:8: name: Thread IDs must be sequential from 0
        thread 0 {} thread 0 {} | <source>:3:20: name: Thread IDs must be sequential from 0
        thread 0 {} thread 2 {} | <source>:3:20: name: Thread IDs must be sequential from 0
        """)
    void rejectsNonSequentialIdsAtTheOffendingNumber(String threads, String diagnostic) {
        assertRejected(source("shared int counter = 0;\n" + threads), diagnostic);
    }

    /** A ninth sequential owner is a limit error rather than an invalid-ID error. */
    @Test void rejectsTheNinthThreadAtItsId() {
        StringBuilder body = new StringBuilder("shared int counter = 0;\n");
        for (int i = 0; i < 9; i++) body.append("thread ").append(i).append(" {}\n");
        assertRejected(source(body.toString()), "<source>:11:8: limit: At most 8 threads");
    }

    /**
     * Each supported expectation is preserved as metadata rather than inferred from the model.
     * @param verdict one of the three exact source verdict labels
     */
    @ParameterizedTest
    @ValueSource(strings = {"PASS", "VIOLATION", "DEADLOCK"})
    void retainsDeclaredExpectedVerdicts(String verdict) {
        var model = loader.load(source("shared int counter = 0;\nthread 0 {}\nexpected " + verdict + ";"));
        assertEquals(verdict, model.expectedVerdict());
    }

    /** Omitting the optional expectation leaves its metadata absent. */
    @Test void expectedVerdictIsOptional() {
        assertNull(loader.load(source("shared int counter = 0;\nthread 0 {}")).expectedVerdict());
    }

    /**
     * Unknown and differently cased expectation labels fail at the supplied token.
     * @param verdict unsupported label
     */
    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "pass"})
    void rejectsUnsupportedExpectedVerdictsAtTheirToken(String verdict) {
        assertRejected(source("shared int counter = 0;\nthread 0 {}\nexpected " + verdict + ";"),
            "<source>:4:10: syntax: Expected a verdict");
    }

    /**
     * Places the first body token at line two, column one without altering its spelling.
     * @param body declarations, owners and optional expectation
     * @return complete source model wrapper
     */
    private static String source(String body) {
        return "program shape {\n" + body + "\n}";
    }

    /**
     * Requires the public source failure rather than an incidental runtime exception.
     * @param source complete input to the loader
     * @param diagnostic independently counted literal source diagnostic
     */
    private void assertRejected(String source, String diagnostic) {
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(source));
        assertEquals(diagnostic, failure.getMessage());
    }
}
