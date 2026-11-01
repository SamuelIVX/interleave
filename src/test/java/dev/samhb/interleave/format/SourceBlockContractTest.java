/** Inclusive source-block nesting and EOF diagnostics before expansion or execution. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceBlockContractTest {
    /** The program/shared prefix places the owner on line three and its first child on line four. */
    private static final String PREFIX = "program blocks {\nshared int counter = 0;\n";
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /**
     * Each recursive control form must count its braces even when it contributes no body work.
     * @param opening typed control header ending in an opening brace
     * @param diagnostic independent literal location of the rejected innermost opening brace
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        if (true) { | <source>:18:11: limit: Source block nesting exceeds 16
        if (false) {} else { | <source>:18:12: limit: Source block nesting exceeds 16
        repeat 1 { | <source>:18:10: limit: Source block nesting exceeds 16
        while (false) limit 0 { | <source>:18:23: limit: Source block nesting exceeds 16
        """)
    void nestingLimitCountsEveryControlBody(String opening, String diagnostic) {
        assertDoesNotThrow(() -> loader.load(nested(opening, 14)));
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(nested(opening, 15)));
        assertEquals(diagnostic, failure.getMessage());
    }

    /**
     * Empty unfinished global/private bodies fail at actual EOF with the missing-brace category.
     * @param source source ending immediately after an opening body and newline
     * @param diagnostic independent literal EOF diagnostic
     */
    @ParameterizedTest
    @MethodSource("unfinishedBodies")
    void unfinishedBodiesRetainTheirEofDiagnostic(String source, String diagnostic) {
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(source));
        assertEquals(diagnostic, failure.getMessage());
    }

    /**
     * Supplies unfinished owner and atomic bodies without inserting an expression fragment.
     * @return source/diagnostic pairs with EOF at line four or five, column one
     */
    private static Stream<Arguments> unfinishedBodies() {
        return Stream.of(
            Arguments.of(PREFIX + "thread 0 {\n", "<source>:4:1: syntax: Expected '}' before EOF"),
            Arguments.of(PREFIX + "thread 0 {\natomic {\n", "<source>:5:1: syntax: Expected '}' before EOF")
        );
    }

    /**
     * Constructs a fully closed owner containing only nested control bodies and no atomic work.
     * @param opening control header ending in an opening brace
     * @param levels number of nested control bodies, excluding program and owner bodies
     * @return complete bounded source model
     */
    private static String nested(String opening, int levels) {
        return PREFIX + "thread 0 {\n" + (opening + "\n").repeat(levels) + "}\n".repeat(levels) + "}\n}";
    }
}
