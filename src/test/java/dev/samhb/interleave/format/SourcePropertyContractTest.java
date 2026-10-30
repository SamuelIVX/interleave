/** Property-admission and source-span contracts for shared-state-only source predicates. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.core.FailureEvidence;
import dev.samhb.interleave.core.SourceLocation;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.DiagnosticInvariant;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SourcePropertyContractTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /**
     * Both property timings reject private reads at every supported expression nesting position.
     * @param timing always or final observation mode
     * @param expression type-correct predicate that reads locals or thread identity
     */
    @ParameterizedTest
    @MethodSource("privateProperties")
    void rejectsPrivateReadsAtThePropertyKeyword(String timing, String expression) {
        RegistryException failure = assertThrows(RegistryException.class,
            () -> loader.load(model(timing, expression)));
        assertEquals("<source>:7:1: name: Properties cannot reference locals or tid", failure.getMessage());
    }

    /**
     * Boolean type failures point inside the condition rather than at the property's keyword.
     * @param timing property observation mode
     * @param diagnostic independent literal expression-start diagnostic
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        always | <source>:7:19: type: Expression must be bool, got INT
        final | <source>:7:18: type: Expression must be bool, got INT
        """)
    void rejectsNonBooleanPropertiesAtTheExpression(String timing, String diagnostic) {
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.load(model(timing, "1")));
        assertEquals(diagnostic, failure.getMessage());
    }

    /**
     * An admitted false predicate retains the entire original property span in failure evidence.
     * @param timing property observation mode
     * @param endColumn literal exclusive end after the semicolon on line seven
     */
    @ParameterizedTest
    @CsvSource({"always, 26", "final, 25"})
    void admittedPropertiesRetainTheirFailureSpan(String timing, int endColumn) {
        var model = loader.load(model(timing, "false"));
        var initial = model.program().initialConfiguration();
        assertTrue(initial.allTerminated());
        DiagnosticInvariant property = assertInstanceOf(DiagnosticInvariant.class, model.invariant().orElseThrow());
        FailureEvidence failure = property.failure(initial.state(), initial);
        assertNotNull(failure);
        assertEquals(FailureEvidence.Kind.INVARIANT, failure.kind());
        assertEquals(FailureEvidence.Phase.PROPERTY, failure.phase());
        assertEquals(new SourceLocation("<source>", 7, 1, 7, endColumn), failure.source());
    }

    /**
     * Crosses both timings with direct, unary, binary and array-index private reads.
     * @return otherwise valid predicates whose private dependencies forbid global properties
     */
    private static Stream<Arguments> privateProperties() {
        return Stream.of("always", "final").flatMap(timing -> Stream.of(
            "local.ready", "tid == 0", "!local.ready", "local.ready && true", "true && local.ready",
            "local.items[0] == 0", "values[local.index] == 0", "values[tid] == 0"
        ).map(expression -> Arguments.of(timing, expression)));
    }

    /**
     * Supplies declared locals so rejection tests reach property admission rather than name lookup.
     * @param timing property observation mode
     * @param expression candidate property body
     * @return source with one terminated owner and the property keyword at line seven, column one
     */
    private static String model(String timing, String expression) {
        return "program properties {\nshared int[] values = [0];\nlocal int[] items = [0];\n"
            + "local int index = 0;\nlocal bool ready = false;\nthread 0 {}\ninvariant "
            + timing + " (" + expression + ");\n}";
    }
}
