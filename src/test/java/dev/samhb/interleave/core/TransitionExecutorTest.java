/** Checks isolated execution and forward successor validation through the shared executor. */
package dev.samhb.interleave.core;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransitionExecutorTest {
    /** A successful action commits only its returned copy, never its parent configuration. */
    @Test void isolatesLegacyExecution() {
        Step write = new Step() {
            public Set<MemoryLocation> reads() { return Set.of(); }
            public Set<MemoryLocation> writes() { return Set.of(MemoryLocation.of("counter")); }
            public boolean enabled(SharedState state) { return true; }
            public StepOutcome execute(SharedState state) {
                ((CounterState) state).setCounter(7);
                return StepOutcome.ADVANCED;
            }
        };
        Program program = new Program(new CounterState(0), List.of(new ModelThread(0, List.of(write))));
        Configuration parent = program.initialConfiguration();
        StepTransition result = TransitionExecutor.execute(program, parent, 0);
        assertEquals(0, ((CounterState) parent.state()).counter());
        assertEquals(7, ((CounterState) result.successor().state()).counter());
        assertEquals(List.of(1), result.successor().programCounters());
        assertTrue(result.successor().allTerminated());
    }
}
