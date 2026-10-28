/** Constructor validation distinguishes a missing thread list from an already completed thread. */
package dev.samhb.interleave.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgramConstructorContractTest {
    /** Both public overloads reject an empty owner list rather than constructing a zero-thread model. */
    @Test
    void constructorsRejectEmptyThreadLists() {
        assertThrows(IllegalArgumentException.class,
            () -> new Program(new CounterState(0), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new Program(new CounterState(0), List.of(), "a".repeat(64)));
    }

    /** A null owner list retains the documented validation exception for both overloads. */
    @Test
    void constructorsRejectNullThreadLists() {
        assertThrows(IllegalArgumentException.class,
            () -> new Program(new CounterState(0), null));
        assertThrows(IllegalArgumentException.class,
            () -> new Program(new CounterState(0), null, "a".repeat(64)));
    }

    /** Initial state validation applies even when the owner list itself is valid. */
    @Test
    void constructorsRejectNullInitialStates() {
        var threads = List.of(new ModelThread(0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Program(null, threads));
        assertThrows(IllegalArgumentException.class,
            () -> new Program(null, threads, "a".repeat(64)));
    }

    /** An existing owner with no actions is valid and initially completed, rather than deadlocked. */
    @Test
    void nonemptyThreadListsMayContainAnAlreadyCompletedThread() {
        var threads = List.of(new ModelThread(0, List.of()));
        var legacy = new Program(new CounterState(0), threads);
        var source = new Program(new CounterState(0), threads, "a".repeat(64));
        for (Program program : List.of(legacy, source)) {
            assertEquals(1, program.threadCount());
            assertEquals(threads, program.threads());
            Configuration initial = program.initialConfiguration();
            assertEquals(List.of(0), initial.programCounters());
            assertEquals(List.of(), initial.enabledThreadIds());
            assertTrue(initial.allTerminated());
            assertFalse(initial.isDeadlockCandidate());
        }
    }
}
