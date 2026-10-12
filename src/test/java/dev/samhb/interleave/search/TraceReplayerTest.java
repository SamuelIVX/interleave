/** Verifies trace replay against actual step execution and detects corrupted outcomes. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.bugs.ReadCounterStep;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraceReplayerTest {

    @Test
    void replay_matchingTrace_succeeds() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), new ReadCounterStep(0)))
        ));

        Trace trace = Trace.of(
            List.of(0, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.COMPLETED
        );

        TraceReplayer replayer = new TraceReplayer();
        Configuration end = replayer.replay(program, trace);

        assertEquals(1, ((CounterState) end.state()).counter());
        assertTrue(end.allTerminated());
    }

    @Test
    void replay_divergentOutcome_throwsCorruptedTraceException() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0)))
        ));

        // The step produces StepOutcome.ADVANCED, but the trace erroneously claims BLOCKED
        Trace corruptedTrace = Trace.of(
            List.of(0),
            List.of(StepOutcome.BLOCKED),
            TraceOutcome.VIOLATION
        );

        TraceReplayer replayer = new TraceReplayer();
        CorruptedTraceException ex = assertThrows(
            CorruptedTraceException.class,
            () -> replayer.replay(program, corruptedTrace)
        );

        assertTrue(ex.getMessage().contains("recorded BLOCKED but execution produced ADVANCED"));
    }
}
