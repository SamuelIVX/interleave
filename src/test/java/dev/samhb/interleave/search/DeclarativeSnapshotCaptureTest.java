/** Committed-state diagnostics for certified declarative models, independent of legacy replay. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.SourceProgramLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies actual enabled transitions and failed-attempt snapshot alignment through the public capturer. */
class DeclarativeSnapshotCaptureTest {
    /** A root prefix, successful action and failed last attempt have literal committed values. */
    @Test void capturesOnlyActualCommittedBoundaries() {
        var model=model();var replayer=new TraceReplayer();
        var root=new Trace(List.of(),List.of(),TraceOutcome.INCOMPLETE);
        assertEquals(List.of(0),replayer.captureDeclarativeSnapshots(model.program(),root).stream().map(s->s.fields().get("x")).toList());
        var completePrefix=new Trace(List.of(0),List.of(StepOutcome.ADVANCED),TraceOutcome.INCOMPLETE);
        assertEquals(List.of(0,1),replayer.captureDeclarativeSnapshots(model.program(),completePrefix).stream().map(s->s.fields().get("x")).toList());
        var failed=new Trace(List.of(0,0),List.of(StepOutcome.ADVANCED,StepOutcome.ASSERTION_FAILED),TraceOutcome.VIOLATION);
        var states=replayer.captureDeclarativeSnapshots(model.program(),failed);
        assertEquals(List.of(0,1,1),states.stream().map(s->s.fields().get("x")).toList());
        assertThrows(UnsupportedOperationException.class,()->states.clear());
        assertEquals(List.of(0,1,7),replayer.captureSnapshots(model.program(),failed).stream().map(s->s.fields().get("x")).toList());
    }

    /** Unsupported models, disabled owners, mismatched outcomes and trailing attempts reject. */
    @Test void rejectsInvalidModelAndExecutionClaims() {
        var model=model();var replayer=new TraceReplayer();
        var first=new Trace(List.of(0),List.of(StepOutcome.ADVANCED),TraceOutcome.INCOMPLETE);
        assertThrows(NullPointerException.class,()->replayer.captureDeclarativeSnapshots(null,first));
        assertThrows(NullPointerException.class,()->replayer.captureDeclarativeSnapshots(model.program(),null));
        var source=new SourceProgramLoader().load("program source { shared int x = 0; thread 0 { atomic { x = 1; } } thread 1 { atomic { x = 1; } } }");
        assertThrows(IllegalArgumentException.class,()->replayer.captureDeclarativeSnapshots(source.program(),first));
        var wrongState=new Program(new CounterState(0),model.program().threads());
        assertThrows(IllegalArgumentException.class,()->replayer.captureDeclarativeSnapshots(wrongState,first));
        assertThrows(IllegalScheduleException.class,()->replayer.captureDeclarativeSnapshots(model.program(),new Trace(List.of(-1),List.of(StepOutcome.ADVANCED),TraceOutcome.INCOMPLETE)));
        assertThrows(CorruptedTraceException.class,()->replayer.captureDeclarativeSnapshots(model.program(),new Trace(List.of(0),List.of(StepOutcome.ASSERTION_FAILED),TraceOutcome.VIOLATION)));
        assertThrows(CorruptedTraceException.class,()->replayer.captureDeclarativeSnapshots(model.program(),new Trace(List.of(0,0,1),List.of(StepOutcome.ADVANCED,StepOutcome.ASSERTION_FAILED,StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
        var disabled=new ProgramLoader().load("""
            {"format":"declarative","name":"disabled","state":{"fields":[{"name":"x","type":"int","init":0}],"locals":[]},
            "threads":[{"id":0,"steps":[{"guard":"false","effects":["x = 1"]}]},
            {"id":1,"steps":[{"guard":"false","effects":["x = 1"]}]}]}
            """);
        assertThrows(IllegalScheduleException.class,()->replayer.captureDeclarativeSnapshots(disabled.program(),first));
    }

    /** Loads the shared two-action committed-prefix failure fixture.
     * @return certified declarative model
     */
    private static BenchmarkProgram model() {
        return new ProgramLoader().loadFromResource("dsl/symmetry-committed-failure.json");
    }
}
