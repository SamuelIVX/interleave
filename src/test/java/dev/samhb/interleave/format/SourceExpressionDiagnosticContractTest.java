/** Public expression diagnostics retain their category and original fragment-relative position. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.TraceOutcome;
import dev.samhb.interleave.search.TraceReplayer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SourceExpressionDiagnosticContractTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /**
     * Exercises both diagnostic entry points with bounded, delimiter-complete source fragments.
     * @param body malformed condition or atomic effect inside an otherwise valid source program
     * @param diagnostic independently enumerated literal category, message and source coordinates
     */
    @ParameterizedTest
    @MethodSource("diagnostics")
    void expressionFailuresKeepTheirPublicDiagnostic(String body, String diagnostic) {
        String source = "program diagnostics {\nshared int counter = 0;\nthread 0 {\n" + body + "\n}\n}";
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(source));
        assertEquals(diagnostic, failure.getMessage());
    }

    /**
     * Supplies syntax/name/type cases without missing source delimiters or private parser inputs.
     * @return complete public-source bodies paired with independent literal diagnostic oracles
     */
    private static Stream<Arguments> diagnostics() {
        return Stream.of(
            arguments("  if (true && @) {}", "<source>:4:15: syntax: Unexpected character '@' at pos 8"),
            arguments("  if (local.0) {}", "<source>:4:13: syntax: Expected identifier at pos 6"),
            arguments("  if (local) {}", "<source>:4:7: syntax: Expected '.' after 'local'"),
            arguments("  if () {}", "<source>:4:7: syntax: Empty expression"),
            arguments("  if (missing) {}", "<source>:4:7: name: Unknown field 'missing'"),
            arguments("  if (1) {}", "<source>:4:7: type: Expression must be bool, got INT"),
            arguments("  atomic {\n    tid = 0;\n  }", "<source>:5:5: name: Reserved keyword cannot be used as identifier: tid"),
            arguments("  atomic {\n    counter;\n  }", "<source>:5:5: syntax: Effect must contain '=': 'counter'"),
            arguments("  atomic {\n    ;\n  }", "<source>:5:5: syntax: Empty effect"),
            arguments("  atomic {\n    counter = true;\n  }", "<source>:5:5: type: Assignment type mismatch"),
            // Characterizes the existing whole-message classifier, including echoed malformed text.
            arguments("  atomic {\n    x Duplicate = 0;\n  }",
                "<source>:5:5: name: Invalid identifier 'x Duplicate': must match [a-z][a-z0-9_]*"),
            arguments("  atomic {\n    x exceed = 0;\n  }",
                "<source>:5:5: limit: Invalid identifier 'x exceed': must match [a-z][a-z0-9_]*"),
            arguments("  atomic {\n    values[<=]tail = 0;\n  }",
                "<source>:5:5: limit: Invalid array lhs: 'values[<=]tail'"),
            arguments("  atomic {\n    x 1.. = 0;\n  }",
                "<source>:5:5: limit: Invalid identifier 'x 1..': must match [a-z][a-z0-9_]*"),
            arguments("  atomic {\n    x depth = 0;\n  }",
                "<source>:5:5: limit: Invalid identifier 'x depth': must match [a-z][a-z0-9_]*"),
            arguments("  atomic {\n    x trailing = 0;\n  }",
                "<source>:5:5: syntax: Invalid identifier 'x trailing': must match [a-z][a-z0-9_]*")
        );
    }

    /**
     * A reusable loader isolates successful compilations from EOF and trailing-input failures.
     * @param source malformed full source, including an empty input
     * @param diagnostic independently located literal frontend error
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        '' | <source>:1:1: syntax: Expected 'program'
        program eof { shared int counter = 0; thread 0 { | <source>:1:49: syntax: Expected '}' before EOF
        program eof { shared int counter = 0; thread 0 {} } extra | <source>:1:53: syntax: Expected 'EOF'
        """)
    void loaderRemainsFreshAcrossEofAndInputFailures(String source, String diagnostic) {
        assertFreshCompilation();
        var failure = assertThrows(RegistryException.class, () -> loader.load(source));
        assertEquals(diagnostic, failure.getMessage());
        assertFreshCompilation();
    }

    /** Checks initial values and one scheduled atomic containing original IF/ASSIGN nodes. */
    private void assertFreshCompilation() {
        var model = loader.load("program fresh { shared int counter = 0; thread 0 { atomic { "
            + "if (true) { counter = 7; } else { counter = 9; } } } }");
        assertEquals(0, ((DynamicState) model.program().initialConfiguration().state()).getInt("counter"));
        assertEquals(1, model.program().threads().getFirst().steps().size());
        var result = new DfsExplorer().explore(model.program());
        assertEquals(1, result.traces().size());
        var trace = result.traces().getFirst();
        assertEquals(TraceOutcome.COMPLETED, trace.outcome());
        assertEquals(7, ((DynamicState) new TraceReplayer().replay(model.program(), trace).state()).getInt("counter"));
    }
}
