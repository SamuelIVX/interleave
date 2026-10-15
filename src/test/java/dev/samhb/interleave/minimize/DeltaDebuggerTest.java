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

    @Test
    void minimize_invariantViolationInDeadlockedConfiguration_succeeds() {
        // T0 writes counter=1 then blocks on a condition that never becomes true.
        // T1 performs an irrelevant read then terminates.
        // Invariant asserts counter != 1.
        // The terminal configuration is deadlocked (T0 live and blocked; T1 terminated),
        // yet violates the invariant. DeltaDebugger should accept and minimize this trace.
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(
                new WriteCounterStep(0),
                new Step() {
                    @Override
                    public boolean enabled(SharedState state) {
                        return false; // permanently blocked -> deadlock candidate
                    }

                    @Override
                    public StepOutcome execute(SharedState state) {
                        return StepOutcome.BLOCKED;
                    }

                    @Override
                    public Set<MemoryLocation> reads() {
                        return Set.of();
                    }

                    @Override
                    public Set<MemoryLocation> writes() {
                        return Set.of();
                    }
                }
            )),
            new ModelThread(1, List.of(new ReadCounterStep(1)))
        ));

        Invariant invariant = (state, config) -> ((CounterState) state).counter() != 1;
        Trace failing = Trace.of(
            List.of(1, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.VIOLATION
        );

        Configuration end = new TraceReplayer().replay(program, failing);
        assertTrue(end.isDeadlockCandidate(), "Replayed configuration must be a deadlock candidate");
        assertFalse(invariant.holds(end.state(), end), "Invariant must fail");

        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failing, TraceOutcome.VIOLATION, invariant);

        assertEquals(List.of(0), minimized.threadIds(), "Should shrink away T1's irrelevant read");
        assertEquals(List.of(StepOutcome.ADVANCED), minimized.outcomes());
    }

    @Test
    void minimize_traceLengthZero_returnsEmptyTrace() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0)))
        ));
        Trace emptyTrace = Trace.of(List.of(), List.of(), TraceOutcome.VIOLATION);
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, emptyTrace, TraceOutcome.VIOLATION, null);
        assertEquals(0, minimized.length());
        assertTrue(minimized.threadIds().isEmpty());
    }

    @Test
    void minimize_traceLengthOne_returnsSingleElementTrace() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0)))
        ));
        Invariant invariant = (state, config) -> ((CounterState) state).counter() != 1;
        Trace singleStep = Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION);
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, singleStep, TraceOutcome.VIOLATION, invariant);
        assertEquals(1, minimized.length());
        assertEquals(List.of(0), minimized.threadIds());
    }

    @Test
    void minimize_incompleteOutcome_throwsIllegalArgumentException() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0)))
        ));
        Trace trace = Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.INCOMPLETE);
        DeltaDebugger debugger = new DeltaDebugger();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> debugger.minimize(program, trace, TraceOutcome.INCOMPLETE, null));
        assertTrue(ex.getMessage().contains("Cannot minimize an INCOMPLETE trace"));
    }

    @Test
    void minimize_oddLengthTrace_removesTrailingIrrelevantStep() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), new ReadCounterStep(0))),
            new ModelThread(1, List.of(new ReadCounterStep(1)))
        ));
        Invariant invariant = (state, config) -> ((CounterState) state).counter() != 1;
        // Schedule: T0(write), T0(read), T1(read) -> length 3, trailing element is T1
        Trace failing = Trace.of(
            List.of(0, 0, 1),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.VIOLATION
        );
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failing, TraceOutcome.VIOLATION, invariant);

        assertEquals(List.of(0), minimized.threadIds(), "Must remove trailing element T1 and redundant read");
        assertEquals(1, minimized.length());
        Configuration replayed = new TraceReplayer().replay(program, minimized);
        assertEquals(1, ((CounterState) replayed.state()).counter());
        assertFalse(invariant.holds(replayed.state(), replayed));
    }

    @Test
    void minimize_primeLengthTrace_removesDistributedIrrelevantSteps() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0))),
            new ModelThread(1, List.of(
                new ReadCounterStep(1),
                new ReadCounterStep(1),
                new ReadCounterStep(1),
                new ReadCounterStep(1)
            ))
        ));
        Invariant invariant = (state, config) -> ((CounterState) state).counter() != 1;
        // Schedule: T1, T1, T0, T1, T1 -> length 5
        Trace failing = Trace.of(
            List.of(1, 1, 0, 1, 1),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.VIOLATION
        );
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failing, TraceOutcome.VIOLATION, invariant);

        assertEquals(List.of(0), minimized.threadIds(), "Should remove all scattered T1 steps");
        assertEquals(1, minimized.length());
        Configuration replayed = new TraceReplayer().replay(program, minimized);
        assertEquals(1, ((CounterState) replayed.state()).counter());
        assertFalse(invariant.holds(replayed.state(), replayed));
    }

    @Test
    void minimize_deadlockTrace_shrinksToMinimalDeadlock() {
        Step blockingStep = new Step() {
            @Override
            public boolean enabled(SharedState state) { return false; }
            @Override
            public StepOutcome execute(SharedState state) { return StepOutcome.BLOCKED; }
            @Override
            public Set<MemoryLocation> reads() { return Set.of(); }
            @Override
            public Set<MemoryLocation> writes() { return Set.of(); }
        };

        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), blockingStep)),
            new ModelThread(1, List.of(blockingStep))
        ));

        // Schedule: T0(write), T1(block), T0(block) -> length 3
        Trace failing = Trace.of(
            List.of(0, 1, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.BLOCKED, StepOutcome.BLOCKED),
            TraceOutcome.DEADLOCK
        );

        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failing, TraceOutcome.DEADLOCK, null);

        assertEquals(TraceOutcome.DEADLOCK, minimized.outcome());
        assertEquals(List.of(0), minimized.threadIds(), "Should shrink to minimal deadlock trace");
        assertEquals(1, minimized.length());
        Configuration config = new TraceReplayer().replay(program, minimized);
        assertTrue(config.isDeadlockCandidate());
    }

    @Test
    void minimize_completedTrace_shrinksToMinimalCompletion() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0)))
        ));

        Trace completedTrace = Trace.of(
            List.of(0, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.COMPLETED
        );

        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, completedTrace, TraceOutcome.COMPLETED, null);

        assertEquals(TraceOutcome.COMPLETED, minimized.outcome());
        assertEquals(List.of(0), minimized.threadIds(), "Should shrink duplicate completion step");
        Configuration config = new TraceReplayer().replay(program, minimized);
        assertTrue(config.allTerminated());
    }

    @Test
    void minimize_violationWithoutExplicitInvariant_usesDefaultCheck() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), new WriteCounterStep(0))),
            new ModelThread(1, List.of(new ReadCounterStep(1)))
        ));

        Trace failing = Trace.of(
            List.of(0, 1, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.VIOLATION
        );

        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failing, TraceOutcome.VIOLATION, null);

        assertEquals(TraceOutcome.VIOLATION, minimized.outcome());
        assertTrue(minimized.length() < failing.length(), "Should minimize trace using default non-termination check");
        Configuration config = new TraceReplayer().replay(program, minimized);
        assertFalse(config.allTerminated());
        assertFalse(config.isDeadlockCandidate());
    }

    @Test
    void isStillFailing_rejectsTerminatedAndDeadlockedTraces_whenInvariantNull() {
        DeltaDebugger debugger = new DeltaDebugger();

        // 1. Terminated trace: allTerminated() is true -> must return false for VIOLATION with null invariant
        Program termProg = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0)))
        ));
        Trace termTrace = Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION);
        assertFalse(debugger.isStillFailing(termProg, termTrace, TraceOutcome.VIOLATION, null),
            "A fully terminated trace is not an invariant violation when invariant is null");

        // 2. Deadlocked trace: isDeadlockCandidate() is true -> must return false for VIOLATION with null invariant
        Step blockingStep = new Step() {
            @Override
            public boolean enabled(SharedState state) { return false; }
            @Override
            public StepOutcome execute(SharedState state) { return StepOutcome.BLOCKED; }
            @Override
            public Set<MemoryLocation> reads() { return Set.of(); }
            @Override
            public Set<MemoryLocation> writes() { return Set.of(); }
        };
        Program deadProg = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), blockingStep)),
            new ModelThread(1, List.of(blockingStep))
        ));
        Trace deadTrace = Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION);
        assertFalse(debugger.isStillFailing(deadProg, deadTrace, TraceOutcome.VIOLATION, null),
            "A deadlocked trace is not an invariant violation when invariant is null");

        // 3. INCOMPLETE expectedOutcome must return false unconditionally
        assertFalse(debugger.isStillFailing(termProg, termTrace, TraceOutcome.INCOMPLETE, null),
            "INCOMPLETE outcome must always return false in isStillFailing");
    }

    @Test
    void minimize_illegalIntermediateSchedule_rejectedAndContinues() {
        Step guardStep = new Step() {
            @Override
            public boolean enabled(SharedState state) {
                return ((CounterState) state).counter() > 0;
            }

            @Override
            public StepOutcome execute(SharedState state) {
                return StepOutcome.ADVANCED;
            }

            @Override
            public Set<MemoryLocation> reads() { return Set.of(); }

            @Override
            public Set<MemoryLocation> writes() { return Set.of(); }
        };

        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), guardStep)),
            new ModelThread(1, List.of(new ReadCounterStep(1)))
        ));

        Invariant invariant = (state, config) -> config.programCounters().get(0) != 2;
        Trace failing = Trace.of(
            List.of(0, 1, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.VIOLATION
        );

        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failing, TraceOutcome.VIOLATION, invariant);

        assertEquals(List.of(0, 0), minimized.threadIds(), "Must not accept illegal schedule [1, 0]");
        assertEquals(2, minimized.length());
    }
}

