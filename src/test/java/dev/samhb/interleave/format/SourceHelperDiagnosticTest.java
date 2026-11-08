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
        String values = header;
        return Stream.of(
            Arguments.of(header + "helper noop {}\nthread 0 { call missing; }\n}",
                "<source>:4:17: name: Helper 'missing' must be declared before use; recursive calls are unsupported"),
            Arguments.of(header + "helper noop {}\nthread 0 { call noop(1); }\n}",
                "<source>:4:12: type: Expected 0 helper arguments"),
            Arguments.of(header + "helper large { repeat 65 { atomic {} } }\nthread 0 {}\n}",
                "<source>:3:1: limit: Expanded helper instructions exceed 64"),
            Arguments.of(header + "helper invalid {\nif (true) {} else {\natomic { repeat 65 { counter = counter + 1; } }\n}\n}\nthread 0 {}\n}",
                "<source>:5:1: limit: Atomic internal instructions exceed 64"),
            Arguments.of(values + "helper f(int counter) {}\nthread 0 {}\n}",
                "<source>:3:14: name: Duplicate or shadowing helper parameter 'counter'"),
            Arguments.of(values + "helper f(string value) {}\nthread 0 {}\n}",
                "<source>:3:10: type: Helper scalar type must be int or bool"),
            Arguments.of(values + "helper f(int[] value) {}\nthread 0 {}\n}",
                "<source>:3:13: unsupported: Helper parameters and results must be scalar"),
            Arguments.of(values + "helper f(int value) {}\nthread 0 { call f(true); }\n}",
                "<source>:4:19: type: Helper argument type mismatch"),
            Arguments.of(values + "helper f(int value) {}\nthread 0 { call f(missing); }\n}",
                "<source>:4:19: name: Unknown field 'missing'"),
            Arguments.of(values + "helper f(int value) {}\nthread 0 { call f(); }\n}",
                "<source>:4:12: type: Expected 1 helper arguments"),
            Arguments.of(values + "helper f() -> bool { return true; }\nthread 0 { call f() -> counter; }\n}",
                "<source>:4:24: type: Helper result target type mismatch"),
            Arguments.of(values + "helper f(int value) -> int { return value; }\nthread 0 { call f(1); }\n}",
                "<source>:4:21: type: Value helpers require a return target"),
            Arguments.of(values + "helper f() -> int {\nreturn true;\n}\nthread 0 {}\n}",
                "<source>:4:8: type: Helper return type mismatch"),
            Arguments.of(values + "helper f() -> int {\nreturn 1;\natomic {}\n}\nthread 0 {}\n}",
                "<source>:5:1: syntax: Return must be the helper's final statement"));
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
