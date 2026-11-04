/** Public forward-control contracts for empty arms, nested joins and thread-end continuations. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceLoweringContractTest {
    /**
     * Checks literal PCs and committed values before comparing DFS evidence and replay.
     * Every arm shape runs both ways, either followed by a statement or joined directly to thread end.
     * @param shape empty/nonempty arm arrangement, or nested empty-arm join
     * @param outer first condition value
     * @param inner nested condition value, ignored for flat arrangements
     * @param following whether an increment follows the conditional
     * @param size literal compiled instruction count and thread-end sentinel
     * @param positions literal path including the initial and terminal PCs
     * @param counters literal committed counter value at each position
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        none   | true  | false | false | 2 | 0:1:2       | 0:1:1
        none   | false | false | false | 2 | 0:1:2       | 0:1:1
        right  | true  | false | false | 3 | 0:1:3       | 0:1:1
        right  | false | false | false | 3 | 0:1:2:3     | 0:1:1:20
        left   | true  | false | false | 3 | 0:1:2:3     | 0:1:1:10
        left   | false | false | false | 3 | 0:1:3       | 0:1:1
        both   | true  | false | false | 4 | 0:1:2:4     | 0:1:1:10
        both   | false | false | false | 4 | 0:1:3:4     | 0:1:1:20
        none   | true  | false | true  | 3 | 0:1:2:3     | 0:1:1:101
        none   | false | false | true  | 3 | 0:1:2:3     | 0:1:1:101
        right  | true  | false | true  | 4 | 0:1:3:4     | 0:1:1:101
        right  | false | false | true  | 4 | 0:1:2:3:4   | 0:1:1:20:120
        left   | true  | false | true  | 4 | 0:1:2:3:4   | 0:1:1:10:110
        left   | false | false | true  | 4 | 0:1:3:4     | 0:1:1:101
        both   | true  | false | true  | 5 | 0:1:2:4:5   | 0:1:1:10:110
        both   | false | false | true  | 5 | 0:1:3:4:5   | 0:1:1:20:120
        nested | true  | true  | true  | 6 | 0:1:2:5:6   | 0:1:1:1:101
        nested | true  | false | true  | 6 | 0:1:2:3:5:6 | 0:1:1:1:20:120
        nested | false | true  | true  | 6 | 0:1:4:5:6   | 0:1:1:30:130
        nested | false | false | true  | 6 | 0:1:4:5:6   | 0:1:1:30:130
        """)
    void branchesAdvanceDirectlyToTheirContinuation(String shape, boolean outer, boolean inner,
            boolean following, int size, String positions, String counters) {
        List<Integer> pcs = integers(positions), values = integers(counters);
        var model = new SourceProgramLoader().load(source(shape, outer, inner, following));
        var program = model.program();
        assertEquals(size, program.threads().getFirst().steps().size());
        assertEquals(pcs.size(), values.size());
        Configuration current = program.initialConfiguration();
        for (int i = 0; i < pcs.size(); i++) {
            assertEquals(List.of(pcs.get(i)), current.programCounters());
            assertEquals(values.get(i).intValue(), ((DynamicState) current.state()).getInt("counter"));
            if (i + 1 == pcs.size()) break;
            assertEquals(List.of(0), current.enabledThreadIds());
            var transition = TransitionExecutor.execute(program, current, 0);
            assertEquals(StepOutcome.ADVANCED, transition.outcome());
            assertNull(transition.failure());
            assertNull(transition.cutoff());
            assertEquals(List.of(pcs.get(i)), current.programCounters());
            assertEquals(values.get(i).intValue(), ((DynamicState) current.state()).getInt("counter"));
            current = transition.successor();
        }
        assertEquals(List.of(size), current.programCounters());
        assertTrue(current.allTerminated());
        assertEquals(List.of(), current.enabledThreadIds());

        var result = new DfsExplorer().explore(program);
        assertEquals(1, result.traces().size());
        var trace = result.traces().getFirst();
        assertEquals(TraceOutcome.COMPLETED, trace.outcome());
        assertEquals(Collections.nCopies(pcs.size() - 1, 0), trace.threadIds());
        assertEquals(Collections.nCopies(pcs.size() - 1, StepOutcome.ADVANCED), trace.outcomes());
        assertEquals(pcs.subList(0, pcs.size() - 1), trace.evidence().executedSteps().stream()
            .map(ExecutedStep::instructionIndex).toList());
        Configuration replay = new TraceReplayer().replay(program, trace);
        assertEquals(List.of(size), replay.programCounters());
        assertEquals(values.getLast().intValue(), ((DynamicState) replay.state()).getInt("counter"));
        assertTrue(replay.allTerminated());
    }

    /** Empty root fragments start terminated and do not require a dummy instruction or transition. */
    @Test void emptyThreadStartsAtItsEndSentinel() {
        var model = new SourceProgramLoader().load("program empty_join { shared int counter = 0; thread 0 {} }");
        var program = model.program();
        assertEquals(0, program.threads().getFirst().steps().size());
        assertEquals(List.of(0), program.initialConfiguration().programCounters());
        assertTrue(program.initialConfiguration().allTerminated());
        var trace = new DfsExplorer().explore(program).traces().getFirst();
        assertEquals(TraceOutcome.COMPLETED, trace.outcome());
        assertEquals(List.of(), trace.threadIds());
        assertEquals(List.of(), trace.evidence().executedSteps());
        assertTrue(new TraceReplayer().replay(program, trace).allTerminated());
    }

    /** Decodes independent literal tables without deriving expectations from compiler targets.
     * @param text colon-separated integer literals
     * @return parsed expectation in source order
     */
    private static List<Integer> integers(String text) {
        return Arrays.stream(text.split(":")).map(Integer::valueOf).toList();
    }

    /** Builds supported source for the selected arm shape; all expectations live in the CSV table.
     * @param shape flat or nested arrangement
     * @param outer outer boolean condition
     * @param inner nested boolean condition
     * @param following whether to append the continuation action
     * @return complete public source with no synthetic nodes or private target manipulation
     */
    private static String source(String shape, boolean outer, boolean inner, boolean following) {
        String yes = switch (shape) {
            case "none", "right" -> "";
            case "left", "both" -> "atomic { counter = 10; }";
            case "nested" -> "if (" + inner + ") {} else { atomic { counter = 20; } }";
            default -> throw new IllegalArgumentException("Unknown arm shape: " + shape);
        };
        String no = switch (shape) {
            case "none", "left" -> "";
            case "right", "both" -> "atomic { counter = 20; }";
            case "nested" -> "atomic { counter = 30; }";
            default -> throw new IllegalArgumentException("Unknown arm shape: " + shape);
        };
        return "program joins { shared int counter = 0; thread 0 { atomic { counter = 1; } if ("
            + outer + ") { " + yes + " } else { " + no + " } "
            + (following ? "atomic { counter = counter + 100; }" : "") + " } }";
    }
}
