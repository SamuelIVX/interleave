/** Schedule minimization with a known removable operation and independent failure replay. */
package dev.samhb.interleave.minimize;

import dev.samhb.interleave.bugs.BrokenPeterson;
import dev.samhb.interleave.bugs.ReadCounterStep;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeltaDebuggerTest {

    private Program buggyProgram() {
        return BrokenPeterson.program().program();
    }

    private Invariant buggyInvariant() {
        return BrokenPeterson.program().invariant().orElseThrow();
    }

    private Trace failingTrace() {
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(buggyProgram(), buggyInvariant());
        List<Trace> violations = result.traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.VIOLATION)
            .toList();
        assertFalse(violations.isEmpty(), "Should find at least one VIOLATION trace");
        return violations.get(0);
    }

    @Test
    void minimize_reducesFailingTrace() {
        // T1's register read is irrelevant to T0's counter write. T0 has a remaining read,
        // keeping the violation live rather than relying on the minimizer's terminal limitation.
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), new ReadCounterStep(0))),
            new ModelThread(1, List.of(new ReadCounterStep(1)))));
        Invariant invariant = (state, config) -> ((CounterState) state).counter() != 1;
        Trace failing = Trace.of(List.of(1, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED), TraceOutcome.VIOLATION);
        Configuration original = new TraceReplayer().replay(program, failing);
        assertFalse(invariant.holds(original.state(), original), "the input must be a genuine failure");
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failing, failing.outcome(), invariant);

        assertEquals(List.of(0), minimized.threadIds(), "remove T1's irrelevant read");
        assertEquals(List.of(StepOutcome.ADVANCED), minimized.outcomes());
        assertEquals(1, minimized.length());
        Configuration replayed = new TraceReplayer().replay(program, minimized);
        assertEquals(1, ((CounterState) replayed.state()).counter());
        assertFalse(invariant.holds(replayed.state(), replayed));
    }

    @Test
    void minimize_preservesViolation() {
        Trace failing = failingTrace();
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(buggyProgram(), failing, failing.outcome(), buggyInvariant());

        assertNotNull(minimized);
        assertTrue(minimized.length() > 0, "Minimized trace should not be empty");
        assertEquals(TraceOutcome.VIOLATION, minimized.outcome(),
            "Minimized trace should still be a VIOLATION");

        TraceReplayer replayer = new TraceReplayer();
        Configuration replayed = replayer.replay(buggyProgram(), minimized);
        assertFalse(buggyInvariant().holds(replayed.state(), replayed),
            "Replayed minimized trace must still violate the invariant");
    }

    @Test
    void minimize_returnsSubsequence() {
        Trace failing = failingTrace();
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(buggyProgram(), failing, failing.outcome(), buggyInvariant());

        List<Integer> originalThreadIds = failing.threadIds();
        List<Integer> minimizedThreadIds = minimized.threadIds();

        int originalIndex = 0;
        int minimizedIndex = 0;

        while (originalIndex < originalThreadIds.size() && minimizedIndex < minimizedThreadIds.size()) {
            if (originalThreadIds.get(originalIndex).equals(minimizedThreadIds.get(minimizedIndex))) {
                minimizedIndex++;
            }
            originalIndex++;
        }

        assertTrue(minimizedIndex == minimizedThreadIds.size(),
            "Minimized trace should be a subsequence of original");
    }
}
