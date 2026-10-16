/** Schedule minimization with a known removable operation and independent failure replay. */
package dev.samhb.interleave.minimize;

import dev.samhb.interleave.bugs.BrokenPeterson;
import dev.samhb.interleave.bugs.DeadlockWriteFlagStep;
import dev.samhb.interleave.bugs.ReadCounterStep;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DeltaDebugger} schedule minimization and boundary conditions.
 */
class DeltaDebuggerTest {

    /** Returns the modeled BrokenPeterson benchmark program. */
    private Program buggyProgram() {
        return BrokenPeterson.program().program();
    }

    /** Returns the invariant for the BrokenPeterson benchmark program. */
    private Invariant buggyInvariant() {
        return BrokenPeterson.program().invariant().orElseThrow();
    }

    /** Discovers and returns a failing VIOLATION trace from the BrokenPeterson program. */
    private Trace failingTrace() {
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(buggyProgram(), buggyInvariant());
        List<Trace> violations = result.traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.VIOLATION)
            .toList();
        assertFalse(violations.isEmpty(), "Should find at least one VIOLATION trace");
        return violations.get(0);
    }

    /** Permanently blocked step used to model non-terminating blocked threads. */
    private static final class BlockingStep implements Step {
        /** Creates a blocking step. */
        BlockingStep() {}

        @Override
        public boolean enabled(SharedState state) {
            return false;
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

    /** Step enabled only when CounterState counter is strictly greater than zero. */
    private static final class PositiveCounterGuardStep implements Step {
        /** Creates a positive counter guard step. */
        PositiveCounterGuardStep() {}

        @Override
        public boolean enabled(SharedState state) {
            return ((CounterState) state).counter() > 0;
        }

        @Override
        public StepOutcome execute(SharedState state) {
            return StepOutcome.ADVANCED;
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

    /**
     * Verifies that DeltaDebugger reduces a two-step trace containing an irrelevant read
     * to a single-step minimal violation trace.
     *
     * <p>The fixture executes T1's read before T0's write and read. T1's read does not
     * affect T0's counter mutation, allowing the minimizer to remove T1's step.
     */
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

    /**
     * Verifies that minimizing a complex BrokenPeterson violation produces a non-empty
     * trace that still violates the mutual exclusion invariant upon replay.
     */
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

    /**
     * Verifies that the minimized trace returned by DeltaDebugger is a strict subsequence
     * of the original failing trace's scheduling decisions.
     */
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

    /**
     * Verifies that an invariant violation occurring in a deadlocked configuration is
     * recognized and minimized.
     *
     * <p>T0 executes a counter write and blocks, while T1 executes an irrelevant read.
     * Even though the terminal configuration is deadlocked, the invariant violation is
     * detected and the irrelevant read is pruned.
     */
    @Test
    void minimize_invariantViolationInDeadlockedConfiguration_succeeds() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(
                new WriteCounterStep(0),
                new BlockingStep()
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

    /**
     * Verifies the base case where an empty trace is supplied, ensuring an empty trace
     * is returned without attempting chunk partitioning.
     */
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

    /**
     * Verifies the base case where a single-step trace is supplied, returning the trace
     * unchanged without entering the multi-step reduction loop.
     */
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

    /**
     * Verifies that attempting to minimize a trace with INCOMPLETE outcome immediately throws
     * an {@link IllegalArgumentException}, as incomplete search budgets cannot be replayed.
     */
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

    /**
     * Verifies that DeltaDebugger partitions odd-length traces without dropping trailing elements.
     *
     * <p>The fixture executes a 3-step schedule [T0(write), T0(read), T1(read)] where T1's read
     * is the trailing step and redundant. Continuous interval partitioning ensures the trailing
     * element is tested and pruned.
     */
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

    /**
     * Verifies that DeltaDebugger partitions prime-length traces and prunes scattered irrelevant steps.
     *
     * <p>The fixture executes a 5-step schedule [T1, T1, T0, T1, T1] where only T0's write causes
     * the invariant violation. Fractional interval partitioning allows all four T1 steps to be removed.
     */
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

    /**
     * Verifies that a trace ending in deadlock is reduced to its minimal deadlocking subset.
     *
     * <p>The fixture models circular wait between T0 and T1 on intent flags, with an independent
     * write by T2. The initial 3-step schedule [T0, T2, T1] is legal and produces a deadlock candidate.
     * DeltaDebugger prunes T2's independent step, returning the minimal 2-step deadlock trace [T0, T1].
     */
    @Test
    void minimize_deadlockTrace_shrinksToMinimalDeadlock() {
        Program program = new Program(DeadlockState.of(new boolean[]{false, false, false}), List.of(
            new ModelThread(0, List.of(new DeadlockWriteFlagStep(0, true), new UnconditionalWaitStep(0, 1))),
            new ModelThread(1, List.of(new DeadlockWriteFlagStep(1, true), new UnconditionalWaitStep(1, 0))),
            new ModelThread(2, List.of(new UnconditionalWaitStep(2, 1)))
        ));

        // Schedule: T0(flag0=true), T2(wait on !flag1 -> advances & terminates), T1(flag1=true) -> deadlock
        Trace failing = Trace.of(
            List.of(0, 2, 1),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.DEADLOCK
        );

        Configuration originalConfig = new ExecutionDriver().run(program, new Schedule(failing.threadIds()));
        assertTrue(originalConfig.isDeadlockCandidate(), "Original trace must be a legal deadlock candidate");

        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(program, failing, TraceOutcome.DEADLOCK, null);

        assertEquals(TraceOutcome.DEADLOCK, minimized.outcome());
        assertEquals(List.of(0, 1), minimized.threadIds(), "Should shrink by removing T2's irrelevant step");
        assertEquals(2, minimized.length());
        Configuration config = new TraceReplayer().replay(program, minimized);
        assertTrue(config.isDeadlockCandidate(), "Minimized trace must still be a deadlock candidate");
    }

    /**
     * Verifies that DeltaDebugger can minimize traces with COMPLETED expected outcome,
     * pruning redundant steps while ensuring all threads still successfully terminate.
     */
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

    /**
     * Verifies that when no explicit invariant is provided, DeltaDebugger uses the default
     * non-termination/non-deadlock check to minimize a VIOLATION trace.
     */
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

    /**
     * Verifies that isStillFailing correctly rejects terminated and deadlocked configurations
     * when checking VIOLATION without an explicit invariant, and unconditionally rejects INCOMPLETE.
     */
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
        Program deadProg = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), new BlockingStep())),
            new ModelThread(1, List.of(new BlockingStep()))
        ));
        Trace deadTrace = Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION);
        assertFalse(debugger.isStillFailing(deadProg, deadTrace, TraceOutcome.VIOLATION, null),
            "A deadlocked trace is not an invariant violation when invariant is null");

        // 3. INCOMPLETE expectedOutcome must return false unconditionally
        assertFalse(debugger.isStillFailing(termProg, termTrace, TraceOutcome.INCOMPLETE, null),
            "INCOMPLETE outcome must always return false in isStillFailing");

        // 4. Illegal schedule throws IllegalScheduleException -> must return false
        Program blockedProg = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new BlockingStep()))
        ));
        Trace illegalTrace = Trace.of(List.of(0), List.of(StepOutcome.BLOCKED), TraceOutcome.VIOLATION);
        assertFalse(debugger.isStillFailing(blockedProg, illegalTrace, TraceOutcome.VIOLATION, null),
            "Illegal schedule must be caught and return false");
    }

    /**
     * Verifies that intermediate candidate sub-schedules that violate step preconditions
     * and trigger {@link IllegalScheduleException} are safely rejected while exploration continues.
     */
    @Test
    void minimize_illegalIntermediateSchedule_rejectedAndContinues() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), new PositiveCounterGuardStep())),
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

    /**
     * Verifies that partition boundary calculations handle large trace lengths
     * up to 50,000 steps without 32-bit signed integer overflow.
     *
     * <p>With {@code i = 49_999} and {@code n = 50_000}, {@code (i + 1) * n = 2_500_000_000},
     * which exceeds {@link Integer#MAX_VALUE} (2,147,483,647). Long arithmetic ensures the final
     * partition boundary evaluates cleanly to {@code 50_000} rather than wrapping to a negative int.
     */
    @Test
    void minimize_largeTraceBoundaryArithmetic_doesNotOverflowInt() {
        int n = 50_000;
        int m = 50_000;
        int lastI = m - 1;
        long expectedStart = (long) lastI * n / m;
        long expectedEnd = (long) (lastI + 1) * n / m;
        int computedStart = (int) ((long) lastI * n / m);
        int computedEnd = (int) ((long) (lastI + 1) * n / m);

        assertEquals(49_999, computedStart);
        assertEquals(50_000, computedEnd);
        assertEquals(expectedStart, computedStart);
        assertEquals(expectedEnd, computedEnd);
    }
}
