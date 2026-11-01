/** Public expression diagnostics retain their category and original fragment-relative position. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
            arguments("  atomic {\n    counter = true;\n  }", "<source>:5:5: type: Assignment type mismatch")
        );
    }
}
