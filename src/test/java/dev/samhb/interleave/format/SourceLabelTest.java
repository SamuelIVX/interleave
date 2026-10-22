/** Independent source-label origin, first-line and inclusive truncation-boundary oracles. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.core.ExecutedStep;
import dev.samhb.interleave.core.SourceLocation;
import dev.samhb.interleave.core.SourceStep;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceLabelTest {
    /** Labels at or below the inclusive limit retain the complete action rather than a source prefix.
     * @param padding comment payload length producing the selected boundary
     * @param expectedLength literal full label length
     */
    @ParameterizedTest
    @CsvSource({"227, 255", "228, 256"})
    void preservesLabelsAtAndBelowLimit(int padding, int expectedLength) {
        String action = "atomic { /*" + "x".repeat(padding) + "*/ counter = 1; }";
        ExecutedStep metadata = metadata(action);
        assertEquals(action, metadata.label());
        assertEquals(expectedLength, metadata.label().length());
        assertEquals(0, metadata.instructionIndex());
    }

    /** Labels above the limit retain exactly the independently specified prefix plus one ellipsis.
     * @param padding comment payload length producing an overlong first line
     * @param expected literal fixture fragments for the retained label
     */
    @ParameterizedTest
    @MethodSource("overlongLabels")
    void truncatesOnlyOverlongFirstLines(int padding, String expected) {
        ExecutedStep metadata = metadata("atomic { /*" + "x".repeat(padding) + "*/ counter = 1; }");
        assertEquals(expected, metadata.label());
        assertEquals(257, metadata.label().length());
    }

    /** Supplies immediate-overflow and far-overflow labels without recomputing truncation in Java.
     * @return comment sizes and explicit expected label fragments
     */
    private static Stream<Arguments> overlongLabels() {
        return Stream.of(
            Arguments.of(229, "atomic { /*" + "x".repeat(229) + "*/ counter = 1; …"),
            Arguments.of(300, "atomic { /*" + "x".repeat(245) + "…")
        );
    }

    /** A long later line cannot lengthen the label or replace the action's full multiline span. */
    @Test
    void multilineBlockUsesOnlyItsShortFirstLine() {
        ExecutedStep metadata = metadata("atomic {\n      /*" + "x".repeat(300) + "*/ counter = 1;\n    }");
        assertEquals("atomic {", metadata.label());
        assertEquals(new SourceLocation("<source>", 4, 5, 6, 6), metadata.source());
    }

    /** Loads one action after a distinct source prologue, then reads the real instruction metadata.
     * @param action supported atomic source including any internal newlines
     * @return metadata of the compiled action at PC zero
     */
    private static ExecutedStep metadata(String action) {
        var model = new SourceProgramLoader().load("program labels {\n  shared int counter = 0;\n  thread 0 {\n    "
            + action + "\n  }\n}");
        return ((SourceStep) model.program().threads().getFirst().steps().getFirst()).metadata(0);
    }
}
