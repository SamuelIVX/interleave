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

    @Test
    void captureSnapshots_recordsNPlusOneSnapshots() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0), new ReadCounterStep(0)))
        ));

        Trace trace = Trace.of(
            List.of(0, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.COMPLETED
        );

        TraceReplayer replayer = new TraceReplayer();
        List<StateSnapshot> snapshots = replayer.captureSnapshots(program, trace);

        assertEquals(3, snapshots.size(), "2-step trace must yield 3 snapshots (c0, c1, c2)");
        assertEquals(0, snapshots.get(0).fields().get("counter"));
        assertEquals(1, snapshots.get(1).fields().get("counter"));
        assertEquals(1, snapshots.get(2).fields().get("counter"));
        assertEquals(1, snapshots.get(2).locals().get("0").get("register"));
    }

    @Test
    void enrich_attachesSnapshotsToTraceRecord() {
        Program program = new Program(CounterState.of(0), List.of(
            new ModelThread(0, List.of(new WriteCounterStep(0)))
        ));

        dev.samhb.interleave.TraceRecord record = new dev.samhb.interleave.TraceRecord(
            List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.COMPLETED, "hash-test"
        );

        TraceReplayer replayer = new TraceReplayer();
        dev.samhb.interleave.TraceRecord enriched = replayer.enrich(program, record);

        assertEquals(2, enriched.states().size());
        assertEquals("hash-test", enriched.programHash());
        assertEquals(0, enriched.states().get(0).fields().get("counter"));
        assertEquals(1, enriched.states().get(1).fields().get("counter"));
    }
}
