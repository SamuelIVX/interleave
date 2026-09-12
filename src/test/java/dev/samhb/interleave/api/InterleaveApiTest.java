package dev.samhb.interleave.api;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InterleaveApiTest {

    @Test
    void interleaveFacade_verifyReturnsDeterministicResult() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        VerificationResult result = Interleave.verify(program, Strategy.DFS);
        
        assertNotNull(result);
        assertEquals(Strategy.DFS, result.strategyUsed());
        assertTrue(result.statesExplored() > 0);
        assertTrue(result.wallTimeMs() >= 0);
        assertTrue(result.heapDeltaBytes() >= 0);
    }

    @Test
    void interleaveFacade_verifyWithInvariant() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        Invariant mutualExclusion = (state, config) -> {
            PetersonState ps = (PetersonState) state;
            return ps.inCriticalSection() == -1 || 
                   (ps.inCriticalSection() == 0 || ps.inCriticalSection() == 1);
        };
        
        VerificationResult result = Interleave.verify(program, Strategy.DFS, mutualExclusion);
        
        assertNotNull(result);
        assertEquals(Strategy.DFS, result.strategyUsed());
    }

    @Test
    void interleaveFacade_replayReturnsConfiguration() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        VerificationResult result = Interleave.verify(program, Strategy.DFS);
        assertFalse(result.completedTraces().isEmpty());
        
        Trace trace = result.completedTraces().get(0);
        Configuration replayed = Interleave.replay(program, trace);
        
        assertNotNull(replayed);
    }

    @Test
    void verificationResult_toJson_producesValidJson() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        VerificationResult result = Interleave.verify(program, Strategy.DFS);
        String json = result.toJson();
        
        assertTrue(json.contains("\"strategy\": \"DFS\""));
        assertTrue(json.contains("\"statesExplored\":"));
        assertTrue(json.contains("\"heapDeltaBytes\":"));
        assertTrue(json.contains("\"hasViolation\":"));
        assertTrue(json.contains("\"failingTraces\":"));
        assertTrue(json.contains("\"deadlockedTraces\":"));
        assertTrue(json.contains("\"completedTraces\":"));
    }

    @Test
    void interleaveFacade_allStrategiesProduceResults() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        for (Strategy strategy : Strategy.values()) {
            VerificationResult result = Interleave.verify(program, strategy);
            assertNotNull(result);
            assertEquals(strategy, result.strategyUsed());
            assertTrue(result.statesExplored() > 0);
        }
    }

    // NEW: Tests for quickCheck and builder
    
    @Test
    void quickCheck_returnsTestResult() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(new WriteFlagStep(0, true));
        List<Step> thread1Steps = List.of(new WriteFlagStep(1, true));
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        TestResult result = Interleave.quickCheck(program);
        
        assertNotNull(result);
        assertInstanceOf(TestResult.class, result);
        assertEquals(Strategy.DFS, result.strategy());
        assertTrue(result.statesExplored() > 0);
    }

    @Test
    void quickCheck_withStrategy_usesSpecifiedStrategy() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(new WriteFlagStep(0, true));
        List<Step> thread1Steps = List.of(new WriteFlagStep(1, true));
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        TestResult result = Interleave.quickCheck(program, Strategy.STATIC_POR);
        
        assertNotNull(result);
        assertEquals(Strategy.STATIC_POR, result.strategy());
    }

    @Test
    void builder_returnsInterleaveRunner() {
        InterleaveRunner runner = Interleave.builder().build();
        
        assertNotNull(runner);
    }

    @Test
    void verificationResult_toTestResult_conversion() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        VerificationResult vr = Interleave.verify(program, Strategy.DFS);
        TestResult tr = vr.toTestResult();
        
        assertEquals(vr.strategyUsed(), tr.strategy());
        assertEquals(vr.statesExplored(), tr.statesExplored());
        assertEquals(vr.wallTimeMs(), tr.wallTimeMs());
        assertEquals(vr.heapDeltaBytes(), tr.heapDeltaBytes());
        assertEquals(vr.failingTraces().size(), tr.failingTraces().size());
        assertEquals(vr.deadlockedTraces().size(), tr.deadlockedTraces().size());
        assertEquals(vr.completedTraces().size(), tr.completedTraces().size());
    }

    @Test
    void trace_toRecord_conversion() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(new WriteFlagStep(0, true));
        List<Step> thread1Steps = List.of(new WriteFlagStep(1, true));
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = Interleave.program(initial, t0, t1);
        
        VerificationResult vr = Interleave.verify(program, Strategy.DFS);
        assertFalse(vr.completedTraces().isEmpty());
        
        Trace trace = vr.completedTraces().get(0);
        TraceRecord record = trace.toRecord();
        
        assertEquals(trace.threadIds(), record.threadIds());
        assertEquals(trace.outcomes(), record.outcomes());
        assertEquals(trace.outcome(), record.outcome());
    }
}
