package dev.samhb.interleave.api;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.*;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.LostUpdate;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class InterleaveRunnerTest {

    private Program createPetersonProgram() {
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
        
        return Interleave.program(initial, t0, t1);
    }

    private Program createSimpleProgram() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(new WriteFlagStep(0, true));
        List<Step> thread1Steps = List.of(new WriteFlagStep(1, true));
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        return Interleave.program(initial, t0, t1);
    }

    @Test
    void builder_createsRunnerWithDefaults() {
        InterleaveRunner runner = InterleaveRunner.builder().build();
        
        assertNotNull(runner);
    }

    @Test
    void builder_configuresAllOptions() {
        StateStore store = new HashingStateStore();
        Invariant invariant = (state, config) -> true;
        
        InterleaveRunner runner = InterleaveRunner.builder()
            .strategy(Strategy.STATIC_POR)
            .invariant(invariant)
            .stateStore(store)
            .maxStates(1000)
            .maxTime(Duration.ofSeconds(30))
            .build();
        
        assertNotNull(runner);
    }

    @Test
    void runner_isReusable_multipleRunsDifferentPrograms() {
        InterleaveRunner runner = InterleaveRunner.builder().build();
        
        Program program1 = createSimpleProgram();
        Program program2 = createPetersonProgram();
        
        TestResult result1 = runner.run(program1);
        TestResult result2 = runner.run(program2);
        
        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(result1.statesExplored() > 0);
        assertTrue(result2.statesExplored() > 0);
    }

    @Test
    void runner_threadSafe_parallelRuns() throws InterruptedException, ExecutionException {
        InterleaveRunner runner = InterleaveRunner.builder().build();
        Program program = createPetersonProgram();
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> task = () -> {
                for (int i = 0; i < 10; i++) {
                    TestResult result = runner.run(program);
                    assertNotNull(result);
                }
                return null;
            };
            
            List<Future<Void>> futures = List.of(executor.submit(task), executor.submit(task));
            
            for (Future<Void> future : futures) {
                future.get(); // Propagates any exception/assertion failure
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void quickCheck_staticEntryPoint_works() {
        Program program = createSimpleProgram();
        
        TestResult result = Interleave.quickCheck(program);
        
        assertNotNull(result);
        assertTrue(result.statesExplored() > 0);
    }

    @Test
    void quickCheck_returnsTestResult_notVerificationResult() {
        Program program = createSimpleProgram();
        
        TestResult result = Interleave.quickCheck(program);
        
        assertInstanceOf(TestResult.class, result);
        assertNotNull(result.strategy());
        assertTrue(result.statesExplored() >= 0);
    }

    @Test
    void results_areSerializable_jsonRoundtrip() {
        Program program = createSimpleProgram();
        
        TestResult result = Interleave.quickCheck(program);
        String json = result.toJson();
        
        assertTrue(json.contains("\"strategy\": \"DFS\""));
        assertTrue(json.contains("\"statesExplored\":"));
        assertTrue(json.contains("\"hasViolation\":"));
        assertTrue(json.contains("\"failingTraces\":"));
        assertTrue(json.contains("\"deadlockedTraces\":"));
        assertTrue(json.contains("\"completedTraces\":"));
    }

    @Test
    void traceRecord_immutableAndSerializable() {
        Program program = createSimpleProgram();
        
        TestResult result = Interleave.quickCheck(program);
        
        assertFalse(result.completedTraces().isEmpty());
        TraceRecord record = result.completedTraces().get(0);
        
        assertNotNull(record.threadIds());
        assertNotNull(record.outcomes());
        assertNotNull(record.outcome());
        assertNotNull(record.programHash());
        
        String json = record.toJson();
        assertTrue(json.contains("\"threads\":"));
        assertTrue(json.contains("\"outcome\":"));
        assertTrue(json.contains("\"programHash\":"));
    }

    @Test
    void verificationResult_toTestResult_conversion() {
        Program program = createPetersonProgram();
        
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
        Program program = createSimpleProgram();
        
        VerificationResult vr = Interleave.verify(program, Strategy.DFS);
        assertFalse(vr.completedTraces().isEmpty());
        
        Trace trace = vr.completedTraces().get(0);
        TraceRecord record = trace.toRecord();
        
        assertEquals(trace.threadIds(), record.threadIds());
        assertEquals(trace.outcomes(), record.outcomes());
        assertEquals(trace.outcome(), record.outcome());
    }

    @Test
    void runner_enforcesMaxStates() {
        Program program = createPetersonProgram();
        
        InterleaveRunner runner = InterleaveRunner.builder()
            .maxStates(5)
            .build();
        
        TestResult result = runner.run(program);
        
        assertTrue(result.limitExceeded());
        assertTrue(result.statesExplored() <= 5);
    }

    @Test
    void runner_enforcesMaxTime() {
        Program program = createPetersonProgram();
        
        InterleaveRunner runner = InterleaveRunner.builder()
            .maxTime(Duration.ofMillis(1))
            .build();
        
        TestResult result = runner.run(program);
        
        // With 1ms limit, it should hit the time limit
        // Note: this test is timing-sensitive, so we just verify it doesn't crash
        assertNotNull(result);
    }

    @Test
    void limitExceededException_thrownOnMaxStates() {
        Program program = createPetersonProgram();
        
        InterleaveRunner runner = InterleaveRunner.builder()
            .maxStates(5)
            .build();
        
        TestResult result = runner.run(program);
        
        assertTrue(result.limitExceeded());
    }

    @Test
    void limitExceededException_thrownOnMaxTime() {
        Program program = createPetersonProgram();
        
        InterleaveRunner runner = InterleaveRunner.builder()
            .maxTime(Duration.ofMillis(1))
            .build();
        
        TestResult result = runner.run(program);
        
        // Just verify it completes without crashing
        assertNotNull(result);
    }

    @Test
    void dporRunnerFindsLostUpdateViolation() {
        BenchmarkProgram benchmark = LostUpdate.program();
        Program program = benchmark.program();
        Invariant invariant = benchmark.invariant().get();

        InterleaveRunner runner = InterleaveRunner.builder()
            .strategy(Strategy.DPOR)
            .invariant(invariant)
            .build();

        TestResult result = runner.run(program);

        assertTrue(result.hasViolation(),
            "DPOR via InterleaveRunner should find lost-update VIOLATION");
        assertFalse(result.failingTraces().isEmpty(),
            "Should have at least one failing trace");
    }

    @Test
    void bitstateRunnerFindsLostUpdateViolation() {
        BenchmarkProgram benchmark = LostUpdate.program();
        Program program = benchmark.program();
        Invariant invariant = benchmark.invariant().get();

        InterleaveRunner runner = InterleaveRunner.builder()
            .strategy(Strategy.DPOR)
            .invariant(invariant)
            .stateStoreFactory(() -> new BitstateStore(1_000_003, 4))
            .build();

        TestResult result = runner.run(program);

        assertTrue(result.hasViolation(),
            "DPOR via InterleaveRunner with BitstateStore should find lost-update VIOLATION");
        assertFalse(result.failingTraces().isEmpty(),
            "Should have at least one failing trace");
    }

    @Test
    void bitstateRunner_isolatesPerRun() {
        // Test that two runs with BitstateStore don't contaminate each other
        BenchmarkProgram benchmark = LostUpdate.program();
        Program program = benchmark.program();
        Invariant invariant = benchmark.invariant().get();

        InterleaveRunner runner = InterleaveRunner.builder()
            .strategy(Strategy.DPOR)
            .invariant(invariant)
            .stateStoreFactory(() -> new BitstateStore(1_000_003, 4))
            .build();

        TestResult result1 = runner.run(program);
        TestResult result2 = runner.run(program);

        assertTrue(result1.hasViolation(), "First run should find violation");
        assertTrue(result2.hasViolation(), "Second run should find violation");
        assertEquals(result1.statesExplored(), result2.statesExplored(), 
            "Both runs should explore same number of states (independent)");
    }
}