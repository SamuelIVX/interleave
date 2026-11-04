/** Source declaration schema and initialization contracts before shared state validation. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.dsl.*;
import dev.samhb.interleave.format.registry.RegistryException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceDeclarationPipelineContractTest {
    /**
     * All shared/local kinds preserve exact signed integers, booleans, empty and populated arrays.
     * The alternate decimal spelling must normalize before the shared validator reads JSON numbers.
     * @param maximum ordinary or zero-prefixed spelling of the signed-int maximum
     */
    @ParameterizedTest @ValueSource(strings = {"2147483647", "0002147483647"})
    void generatedDeclarationsPreserveLiteralSchemaAndInitialValues(String maximum) {
        var model = new SourceProgramLoader().load("""
            program declarations {
              shared int minimum = -2147483648;
              shared int maximum = %s;
              shared bool enabled = true;
              shared bool disabled = false;
              shared int[] empty = [];
              shared int[] values = [-2147483648, -0, 2147483647];
              local int low = -2147483648;
              local int high = 2147483647;
              local bool active = true;
              local bool inactive = false;
              local int[] vacant = [];
              local int[] items = [-2147483648, 000, 2147483647];
              thread 0 {}
              thread 1 {}
            }
            """.formatted(maximum));
        var initial = (DynamicState) model.program().initialConfiguration().state();
        var expected = new StateDecl(List.of(
            new FieldDecl("minimum", FieldType.INT, -2147483648, false, null),
            new FieldDecl("maximum", FieldType.INT, 2147483647, false, null),
            new FieldDecl("enabled", FieldType.BOOL, 0, true, null),
            new FieldDecl("disabled", FieldType.BOOL, 0, false, null),
            new FieldDecl("empty", FieldType.INT_ARRAY, 0, false, new int[] {}),
            new FieldDecl("values", FieldType.INT_ARRAY, 0, false, new int[] {-2147483648, 0, 2147483647})),
            List.of(
                new LocalDecl("low", FieldType.INT, -2147483648, false, null),
                new LocalDecl("high", FieldType.INT, 2147483647, false, null),
                new LocalDecl("active", FieldType.BOOL, 0, true, null),
                new LocalDecl("inactive", FieldType.BOOL, 0, false, null),
                new LocalDecl("vacant", FieldType.INT_ARRAY, 0, false, new int[] {}),
                new LocalDecl("items", FieldType.INT_ARRAY, 0, false, new int[] {-2147483648, 0, 2147483647})));
        assertEquals(expected, initial.decl());
        assertEquals(-2147483648, initial.getInt("minimum"));
        assertEquals(2147483647, initial.getInt("maximum"));
        assertTrue(initial.getBool("enabled"));
        assertFalse(initial.getBool("disabled"));
        assertArrayEquals(new int[] {}, initial.getArray("empty"));
        assertArrayEquals(new int[] {-2147483648, 0, 2147483647}, initial.getArray("values"));
        for (int owner : List.of(0, 1)) {
            assertEquals(-2147483648, initial.getLocalInt(owner, "low"));
            assertEquals(2147483647, initial.getLocalInt(owner, "high"));
            assertTrue(initial.getLocalBool(owner, "active"));
            assertFalse(initial.getLocalBool(owner, "inactive"));
            assertArrayEquals(new int[] {}, initial.getLocalArray(owner, "vacant"));
            assertArrayEquals(new int[] {-2147483648, 0, 2147483647}, initial.getLocalArray(owner, "items"));
        }
        var direct = new DynamicState(initial.decl(), 2);
        direct.setLocalArrayElement(0, "items", 1, 42);
        assertArrayEquals(new int[] {-2147483648, 42, 2147483647}, direct.getLocalArray(0, "items"));
        assertArrayEquals(new int[] {-2147483648, 0, 2147483647}, direct.getLocalArray(1, "items"));
        initial.setLocalArrayElement(0, "items", 1, 42);
        assertArrayEquals(new int[] {-2147483648, 42, 2147483647}, initial.getLocalArray(0, "items"));
        assertArrayEquals(new int[] {-2147483648, 0, 2147483647}, initial.getLocalArray(1, "items"));
    }

    /**
     * Source type checks reject incompatible initializers before shared-validator remapping.
     * @param declaration one invalid shared or local declaration after a valid shared field
     * @param diagnostic independently located literal frontend diagnostic
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        shared int value = true;  | <source>:3:20: type: Initializer for 'value' must match int
        shared bool value = 0;    | <source>:3:21: type: Initializer for 'value' must match bool
        shared int[] value = 0;   | <source>:3:22: type: Initializer for 'value' must match int[]
        local int value = false;  | <source>:3:19: type: Initializer for 'value' must match int
        local bool value = 0;     | <source>:3:20: type: Initializer for 'value' must match bool
        local int[] value = 1;    | <source>:3:21: type: Initializer for 'value' must match int[]
        """)
    void incompatibleInitializersKeepTheirFrontendDiagnostic(String declaration, String diagnostic) {
        String source = "program declarations {\nshared int seed = 0;\n" + declaration + "\nthread 0 {}\n}";
        var failure = assertThrows(RegistryException.class, () -> new SourceProgramLoader().load(source));
        assertEquals(diagnostic, failure.getMessage());
    }
}
