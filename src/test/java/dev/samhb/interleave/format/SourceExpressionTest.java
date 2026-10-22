/** Literal value and failure oracles for source scalar expressions at the execution boundary. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.ExecutionDriver;
import dev.samhb.interleave.core.FailureEvidence;
import dev.samhb.interleave.core.Schedule;
import dev.samhb.interleave.core.ScheduleExecution;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceExpressionTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** Arithmetic uses signed Java-int values, including wraparound and signed remainder.
     * @param expression arithmetic expression under test
     * @param left initialized left operand
     * @param right initialized right operand, unused for unary expressions
     * @param expected literal committed integer result
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
        left + right;       7;           3;  10
        left - right;       7;           3;   4
        left * right;       7;           3;  21
        left * right;       7;           0;   0
        left % right;       7;           3;   1
        left % right;      -7;           3;  -1
        left % right;       7;          -3;   1
        left % right;      -7;          -3;  -1
        -left;              7;           3;  -7
        -left;             -7;           3;   7
        left + right; 2147483647;        1; -2147483648
        left - right; -2147483648;       1;  2147483647
        left * right; 1073741824;        4;   0
        -left;        -2147483648;       1; -2147483648
        left % right; -2147483648;      -1;   0
        """)
    void arithmeticProducesLiteralResults(String expression, int left, int right, int expected) {
        Configuration end = execute("shared int left = " + left + "; shared int right = " + right
            + "; shared int answer = 99;", "answer = " + expression + ";");
        assertEquals(expected, ((DynamicState) end.state()).getInt("answer"));
    }

    /** Each relation is checked below, at and above equality so boundaries cannot be interchanged.
     * @param expression integer comparison under test
     * @param left initialized left operand
     * @param right initialized right operand
     * @param expected literal committed boolean result
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
        left == right; 2; 3; false
        left == right; 3; 3; true
        left == right; 4; 3; false
        left != right; 2; 3; true
        left != right; 3; 3; false
        left != right; 4; 3; true
        left < right;  2; 3; true
        left < right;  3; 3; false
        left < right;  4; 3; false
        left <= right; 2; 3; true
        left <= right; 3; 3; true
        left <= right; 4; 3; false
        left > right;  2; 3; false
        left > right;  3; 3; false
        left > right;  4; 3; true
        left >= right; 2; 3; false
        left >= right; 3; 3; true
        left >= right; 4; 3; true
        """)
    void comparisonsProduceLiteralResults(String expression, int left, int right, boolean expected) {
        Configuration end = execute("shared int left = " + left + "; shared int right = " + right
            + "; shared bool answer = " + !expected + ";", "answer = " + expression + ";");
        assertEquals(expected, ((DynamicState) end.state()).getBool("answer"));
    }

    /** Boolean operators preserve their full truth tables, including the evaluated right operand.
     * @param expression boolean expression under test
     * @param left initialized left operand
     * @param right initialized right operand, unused for unary expressions
     * @param expected literal committed boolean result
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
        left && right; false; false; false
        left && right; false; true;  false
        left && right; true;  false; false
        left && right; true;  true;  true
        left || right; false; false; false
        left || right; false; true;  true
        left || right; true;  false; true
        left || right; true;  true;  true
        left == right; false; false; true
        left == right; false; true;  false
        left == right; true;  false; false
        left == right; true;  true;  true
        left != right; false; false; false
        left != right; false; true;  true
        left != right; true;  false; true
        left != right; true;  true;  false
        !left;         false; false; true
        !left;         true;  false; false
        """)
    void booleansProduceLiteralResults(String expression, boolean left, boolean right, boolean expected) {
        Configuration end = execute("shared bool left = " + left + "; shared bool right = " + right
            + "; shared bool answer = " + !expected + ";", "answer = " + expression + ";");
        assertEquals(expected, ((DynamicState) end.state()).getBool("answer"));
    }

    /** A skipped RHS cannot fail; a required RHS must fail and roll back earlier tentative writes.
     * @param expression short-circuit expression with a failing right operand
     * @param fails whether evaluation must reach the failing right operand
     * @param expected committed answer, retaining its initial false value on failure
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
        false && (1 % 0 == 0); false; false
        true || (1 % 0 == 0);  false; true
        true && (1 % 0 == 0);  true;  false
        false || (1 % 0 == 0); true;  false
        """)
    void shortCircuitControlsRuntimeErrors(String expression, boolean fails, boolean expected) {
        var model = loader.load("program expression { shared int counter = 0; shared bool answer = false;"
            + " thread 0 { atomic { counter = 9; answer = " + expression + "; } } }");
        ScheduleExecution result = new ExecutionDriver().runDetailed(model.program(), new Schedule(List.of(0)), null);
        assertEquals(fails ? ScheduleExecution.Kind.VIOLATION : ScheduleExecution.Kind.COMPLETED, result.kind());
        assertEquals(List.of(fails ? StepOutcome.ASSERTION_FAILED : StepOutcome.ADVANCED), result.outcomes());
        DynamicState end = (DynamicState) result.configuration().state();
        assertEquals(fails ? 0 : 9, end.getInt("counter"));
        assertEquals(expected, end.getBool("answer"));
        if (fails) {
            assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, result.trace().failure().kind());
            assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, result.trace().failure().category());
            assertEquals(FailureEvidence.Phase.STEP, result.trace().failure().phase());
        }
    }

    /** A boolean local assignment is read by its owner without changing another thread's copy. */
    @Test
    void booleanLocalsKeepTheirAssignedValuesAndThreadOwnership() {
        var model = loader.load("""
            program locals { shared bool first = false; shared bool second = true; local bool ready = false;
              thread 0 { atomic { local.ready = !local.ready; first = local.ready; } }
              thread 1 { atomic { second = local.ready; } }
            }
            """);
        Configuration end = new ExecutionDriver().run(model.program(), new Schedule(List.of(0, 1)));
        assertTrue(end.allTerminated());
        DynamicState state = (DynamicState) end.state();
        assertTrue(state.getBool("first"));
        assertFalse(state.getBool("second"));
        assertTrue(state.getLocalBool(0, "ready"));
        assertFalse(state.getLocalBool(1, "ready"));
    }

    /** Runs one scalar assignment block and requires a successfully terminated source configuration.
     * @param declarations initialized shared declarations
     * @param body validated atomic assignment body
     * @return committed final configuration
     */
    private Configuration execute(String declarations, String body) {
        var model = loader.load("program expression { " + declarations + " thread 0 { atomic { " + body + " } } }");
        Configuration end = new ExecutionDriver().run(model.program(), new Schedule(List.of(0)));
        assertTrue(end.allTerminated());
        return end;
    }
}
