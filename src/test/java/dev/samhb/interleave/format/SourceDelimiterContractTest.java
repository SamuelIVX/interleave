/** Public source delimiter diagnostics and balanced nested-expression execution contracts. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.search.TraceOutcome;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.TraceReplayer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceDelimiterContractTest {
    /** Keeps scanner diagnostic locations independent of generated wrapper text. */
    private static final String PREFIX = "program delimiters {\nshared int counter = 0;\nthread 0 {\n";

    /** Malformed public input preserves the exact source location and scanner diagnostic.
     * @param tail unfinished or unbalanced thread source
     * @param message literal independently located error
     */
    @ParameterizedTest @MethodSource("invalidDelimiters")
    void rejectsInvalidDelimitersAtTheirOriginalLocation(String tail, String message) {
        var failure = assertThrows(RegistryException.class, () -> new SourceProgramLoader().load(PREFIX + tail));
        assertEquals(message, failure.getMessage());
    }

    /** Covers EOF, both braces and negative nesting in condition/effect scanners.
     * @return malformed tails and exact original-source diagnostic messages
     */
    private static Stream<Arguments> invalidDelimiters() {
        return Stream.of(
            Arguments.of("  if (true", "<source>:4:11: syntax: Expected ')' before EOF"),
            Arguments.of("  atomic {\n    counter = 1", "<source>:5:16: syntax: Expected ';' before EOF"),
            Arguments.of("  if (true { }\n}\n}", "<source>:4:12: syntax: Expected ')'"),
            Arguments.of("  if (true }\n}", "<source>:4:12: syntax: Expected ')'"),
            Arguments.of("  atomic {\n    counter = 1 { }\n  }\n}\n}", "<source>:5:17: syntax: Expected ';'"),
            Arguments.of("  atomic {\n    counter = 1 }\n}\n}", "<source>:5:17: syntax: Expected ';'"),
            Arguments.of("  atomic {\n    counter = 1);\n  }\n}\n}", "<source>:5:16: syntax: Unmatched expression delimiter"),
            Arguments.of("  if (true]) {}\n}\n}", "<source>:4:11: syntax: Unmatched expression delimiter"));
    }

    /** Balanced parentheses and index brackets preserve both parsing and replayed effects. */
    @Test void executesNestedBalancedDelimiters() {
        var model = new SourceProgramLoader().load("""
            program nested_delimiters {
              shared int counter = 0;
              shared int[] values = [1, 2];
              thread 0 {
                if ((values[(0)] == 1)) {
                  atomic { values[(0)] = (values[(1)] + (2)); counter = values[(0)]; }
                }
              }
            }
            """);
        assertEquals(2, model.program().threads().getFirst().steps().size());
        var result = new DfsExplorer().explore(model.program());
        assertEquals(1, result.traces().size());
        var trace = result.traces().getFirst();
        assertEquals(TraceOutcome.COMPLETED, trace.outcome());
        var end = (DynamicState) new TraceReplayer().replay(model.program(), trace).state();
        assertEquals(4, end.getInt("counter"));
        assertArrayEquals(new int[] {4, 2}, end.getArray("values"));
    }
}
