/** Exhaustive coverage, replay values, and real invariant failures on hand-enumerated models. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.testsupport.TestPrograms;
import java.util.stream.Collectors;

import dev.samhb.interleave.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DfsExplorerTest {

    /** Hand enumeration distinguishes both single-write orders, not just a nonzero count. */
    @Test
    void dfsExploresAllReachableConfigurations_simpleTwoStepProgram() {
        DfsResult result = new DfsExplorer().explore(TestPrograms.independentFlags());
        assertEquals(4, result.statesExplored());
        Set<Position> reached = result.states().values().stream()
            .map(DfsExplorerTest::position).collect(Collectors.toSet());
        assertEquals(Set.of(new Position(false, false, List.of(0, 0)),
            new Position(true, false, List.of(1, 0)), new Position(false, true, List.of(0, 1)),
            new Position(true, true, List.of(1, 1))), reached);
    }

    /** Recorded completions must execute both writes and reach the expected counters. */
    @Test
    void traceReplayer_replaysTraceToSameConfiguration() {
        Program program = TestPrograms.independentFlags();
        DfsResult result = new DfsExplorer().explore(program);
        assertFalse(result.traces().isEmpty(), "the replay loop must execute");
        for (Trace trace : result.traces()) {
            assertEquals(TraceOutcome.COMPLETED, trace.outcome());
            assertEquals(List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED), trace.outcomes());
            Configuration replayed = new TraceReplayer().replay(program, trace);
            assertEquals(new Position(true, true, List.of(1, 1)), position(replayed));
            assertTrue(replayed.allTerminated());
            assertFalse(replayed.isDeadlockCandidate());
        }
    }

    /** A rejecting predicate produces two distinct, replayable counterexamples. */
    @Test
    void invariantViolation_reportedWithTrace() {
        Program program = TestPrograms.independentFlags();
        Invariant invariant = (state, config) -> !((PetersonState) state).flag(0);
        DfsResult result = new DfsExplorer().explore(program, invariant);
        List<Trace> failures = result.traces().stream()
            .filter(trace -> trace.outcome() == TraceOutcome.VIOLATION).toList();
        assertEquals(2, failures.size(), "T0 can violate before or after T1's write");
        assertEquals(Set.of(List.of(0), List.of(1, 0)), failures.stream()
            .map(Trace::threadIds).collect(Collectors.toSet()));
        for (Trace failure : failures) {
            Configuration replayed = new TraceReplayer().replay(program, failure);
            assertTrue(((PetersonState) replayed.state()).flag(0));
            assertFalse(invariant.holds(replayed.state(), replayed));
        }
    }
    /**
     * Reads values without canonical keys or diagnostic text.
     * @param config reached configuration
     * @return independent flag/counter identity
     */
    private static Position position(Configuration config) {
        PetersonState state = (PetersonState) config.state();
        return new Position(state.flag(0), state.flag(1), config.programCounters());
    }

    /**
     * Independent value oracle for the two-write fixture.
     * @param flag0 first flag
     * @param flag1 second flag
     * @param counters ordered counters
     */
    private record Position(boolean flag0, boolean flag1, List<Integer> counters) {}
}
