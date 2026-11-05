/** Literal source-offset oracles for helper resolution, limits and private else-branch validation. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceHelperDiagnosticTest {
    /** Specifies complete diagnostics using original source lines/columns counted outside Java.
     * @return controlled invalid source and exact source-facing error
     */
    static Stream<Arguments> cases() {
        String header = "program diagnostics {\nshared int counter = 0;\n";
        return Stream.of(
            Arguments.of(header + "helper noop {}\nthread 0 { call missing; }\n}",
                "<source>:4:17: name: Helper 'missing' must be declared before use; recursive calls are unsupported"),
            Arguments.of(header + "helper noop {}\nthread 0 { call noop(1); }\n}",
                "<source>:4:21: unsupported: Helper arguments are unsupported; use call name;"),
            Arguments.of(header + "helper large { repeat 65 { atomic {} } }\nthread 0 {}\n}",
                "<source>:3:1: limit: Expanded helper instructions exceed 64"),
            Arguments.of(header + "helper invalid {\nif (true) {} else {\natomic { repeat 65 { counter = counter + 1; } }\n}\n}\nthread 0 {}\n}",
                "<source>:5:1: limit: Atomic internal instructions exceed 64"));
    }

    /** Requires deliberate validation at the correct definition/call token, including unused else work.
     * @param source full invalid source
     * @param expected exact independently counted error
     */
    @ParameterizedTest @MethodSource("cases")
    void helperErrorsRetainOriginalLocations(String source, String expected) {
        RegistryException error = assertThrows(RegistryException.class, () -> new SourceProgramLoader().load(source));
        assertEquals(expected, error.getMessage());
    }
}
