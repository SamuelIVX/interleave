/** Source-array value, rollback and thread-ownership contracts with literal state oracles. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceArrayTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** Both array namespaces read the first and last valid elements.
     * @param target declared shared or local array reference
     * @param index valid endpoint
     * @param expected literal initialized element
     */
    @ParameterizedTest
    @CsvSource({"values, 0, 11", "values, 1, 22", "local.items, 0, 31", "local.items, 1, 42"})
    void readsBothArrayEndpoints(String target, int index, int expected) {
        var result = execute("answer = " + target + "[" + index + "];");
        assertEquals(ScheduleExecution.Kind.COMPLETED, result.kind());
        assertEquals(expected, ((DynamicState) result.configuration().state()).getInt("answer"));
    }

    /** Shared writes change exactly the selected endpoint and leave private arrays untouched.
     * @param index valid target endpoint
     * @param first literal expected first shared element
     * @param second literal expected second shared element
     */
    @ParameterizedTest
    @CsvSource({"0, 81, 22", "1, 11, 81"})
    void writesSharedEndpoints(int index, int first, int second) {
        var result = execute("values[" + index + "] = 81;");
        assertEquals(ScheduleExecution.Kind.COMPLETED, result.kind());
        DynamicState state = (DynamicState) result.configuration().state();
        assertArrayEquals(new int[] {first, second}, state.getArray("values"));
        assertArrayEquals(new int[] {31, 42}, state.getLocalArray(0, "items"));
        assertArrayEquals(new int[] {31, 42}, state.getLocalArray(1, "items"));
    }

    /** Local writes change only the owner's selected endpoint.
     * @param index valid target endpoint
     * @param first literal expected first private element
     * @param second literal expected second private element
     */
    @ParameterizedTest
    @CsvSource({"0, 81, 42", "1, 31, 81"})
    void writesLocalEndpoints(int index, int first, int second) {
        var result = execute("local.items[" + index + "] = 81;");
        assertEquals(ScheduleExecution.Kind.COMPLETED, result.kind());
        DynamicState state = (DynamicState) result.configuration().state();
        assertArrayEquals(new int[] {first, second}, state.getLocalArray(0, "items"));
        assertArrayEquals(new int[] {31, 42}, state.getLocalArray(1, "items"));
        assertArrayEquals(new int[] {11, 22}, state.getArray("values"));
    }

    /** Bounds failures roll back all earlier scalar, shared-array and private-array writes.
     * @param operation invalid shared/local read/write at a negative or length index
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
        answer = values[-1]
        answer = values[2]
        answer = local.items[-1]
        answer = local.items[2]
        values[-1] = 77
        values[2] = 77
        local.items[-1] = 77
        local.items[2] = 77
        """)
    void invalidIndicesProduceStepFailuresAndRollback(String operation) {
        var result = execute("answer = 9; values[0] = 99; local.items[0] = 88; " + operation + ";");
        assertEquals(ScheduleExecution.Kind.VIOLATION, result.kind());
        assertEquals(List.of(StepOutcome.ASSERTION_FAILED), result.outcomes());
        assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, result.trace().failure().kind());
        assertEquals(FailureEvidence.Phase.STEP, result.trace().failure().phase());
        assertEquals(FailureEvidence.Category.ARRAY_BOUNDS, result.trace().failure().category());
        DynamicState state = (DynamicState) result.configuration().state();
        assertEquals(99, state.getInt("answer"));
        assertArrayEquals(new int[] {11, 22}, state.getArray("values"));
        assertArrayEquals(new int[] {31, 42}, state.getLocalArray(0, "items"));
        assertArrayEquals(new int[] {31, 42}, state.getLocalArray(1, "items"));
    }

    /** A nonzero owner uses its own tid and private array even when scheduled before thread zero. */
    @Test
    void tidIndexesSharedResultsAndIsolatedLocalArrays() {
        var model = loader.load("""
            program owners { shared int[] results = [-1, -1]; shared int[] owners = [-1, -1];
              local int[] items = [11, 22];
              thread 0 { atomic { results[tid] = local.items[tid]; owners[tid] = tid; local.items[tid] = 80 + tid; } }
              thread 1 { atomic { results[tid] = local.items[tid]; owners[tid] = tid; local.items[tid] = 80 + tid; } }
            }
            """);
        Configuration end = new ExecutionDriver().run(model.program(), new Schedule(List.of(1, 0)));
        assertTrue(end.allTerminated());
        DynamicState state = (DynamicState) end.state();
        assertArrayEquals(new int[] {11, 22}, state.getArray("results"));
        assertArrayEquals(new int[] {0, 1}, state.getArray("owners"));
        assertArrayEquals(new int[] {80, 22}, state.getLocalArray(0, "items"));
        assertArrayEquals(new int[] {11, 81}, state.getLocalArray(1, "items"));
    }

    /** Runs one atomic action with distinct initialized shared/private arrays and an idle peer.
     * @param body atomic statements to execute as thread zero
     * @return detailed terminal result retaining committed state and failure evidence
     */
    private ScheduleExecution execute(String body) {
        var model = loader.load("program arrays { shared int answer = 99; shared int[] values = [11, 22];"
            + " local int[] items = [31, 42]; thread 0 { atomic { " + body + " } } thread 1 {} }");
        return new ExecutionDriver().runDetailed(model.program(), new Schedule(List.of(0)), null);
    }
}
