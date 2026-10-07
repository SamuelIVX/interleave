/** Runner configuration, per-run isolation, and result immutability and wire contracts. */
package dev.samhb.interleave.api;

import dev.samhb.interleave.testsupport.TestPrograms;
import static dev.samhb.interleave.testsupport.JsonAssertions.parseObject;
import com.google.gson.JsonPrimitive;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.*;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.LostUpdate;
import org.junit.jupiter.api.Test;
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


    /** Strategy and invariant options must change actual exploration, not just builder construction. */
    @Test
    void builderHonorsStrategyAndInvariant() {
        Invariant property = (state, config) -> !((PetersonState) state).flag(0);
        TestResult result = InterleaveRunner.builder().strategy(Strategy.STATIC_POR)
            .invariant(property).build().run(TestPrograms.independentFlags());
        assertEquals(Strategy.STATIC_POR, result.strategy());
        assertEquals(2, result.failingTraces().size());
        assertTrue(result.hasViolation());
        for (TraceRecord trace : result.failingTraces()) {
            var end = new ExecutionDriver().run(TestPrograms.independentFlags(), new Schedule(trace.threadIds()));
            assertFalse(property.holds(end.state(), end));
        }
    }

    /** A supplied legacy store that suppresses every position must actually be cleared and queried. */
    @Test
    void builderHonorsSuppliedStore() {
        var clears = new java.util.concurrent.atomic.AtomicInteger();
        StateStore store = new StateStore() {
            /** {@inheritDoc} */
            @Override public boolean isVisited(Configuration config) { return true; }
            /** {@inheritDoc} */
            @Override public void markVisited(Configuration config) { fail("every position is already visited"); }
            /** {@inheritDoc} */
            @Override public void clear() { clears.incrementAndGet(); }
        };
        TestResult result = InterleaveRunner.builder().stateStore(store).build()
            .run(TestPrograms.independentFlags());
        assertEquals(1, clears.get());
        assertEquals(0, result.statesExplored());
        assertTrue(result.completedTraces().isEmpty());
    }

    /** Alternating known four- and two-position models cannot inherit previous search state. */
    @Test
    void runner_isReusable_multipleRunsDifferentPrograms() {
        InterleaveRunner runner = InterleaveRunner.builder().build();
        Program single = new Program(PetersonState.of(false, false, 0),
            List.of(new ModelThread(0, List.of(new WriteFlagStep(0, true)))));
        assertEquals(4, runner.run(TestPrograms.independentFlags()).statesExplored());
        assertEquals(2, runner.run(single).statesExplored());
        TestResult repeated = runner.run(TestPrograms.independentFlags());
        assertEquals(4, repeated.statesExplored());
        assertEquals(List.of(0, 1), repeated.completedTraces().getFirst().threadIds());
        assertFalse(repeated.limitExceeded());
    }

    /** Concurrent reuse must preserve known results and trace contents, not merely avoid a crash. */
    @Test
    void runner_threadSafe_parallelRuns() throws Exception {
        InterleaveRunner runner = InterleaveRunner.builder().build();
        Program two = TestPrograms.independentFlags();
        Program one = new Program(PetersonState.of(false, false, 0),
            List.of(new ModelThread(0, List.of(new WriteFlagStep(0, true)))));
        TestResult expectedTwo = InterleaveRunner.builder().build().run(two);
        TestResult expectedOne = InterleaveRunner.builder().build().run(one);
        assertEquals(4, expectedTwo.statesExplored());
        assertEquals(2, expectedOne.statesExplored());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                for (int i = 0; i < 10; i++) assertSameSearch(expectedTwo, runner.run(two));
                return null;
            });
            var second = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                for (int i = 0; i < 10; i++) assertSameSearch(expectedOne, runner.run(one));
                return null;
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS), "both workers must reach the start gate");
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }


    /** Strict JSON parsing protects both the document and typed result values. */
    @Test
    void resultsAreSerializedWithTypedFieldsAndTraceContents() {
        TestResult result = Interleave.quickCheck(TestPrograms.independentFlags());
        var json = parseObject(result.toJson());
        assertEquals(new JsonPrimitive("DFS"), json.get("strategy"));
        assertEquals(new JsonPrimitive(4), json.get("statesExplored"));
        assertEquals(new JsonPrimitive(false), json.get("hasViolation"));
        assertEquals(new JsonPrimitive(false), json.get("limitExceeded"));
        assertEquals(new JsonPrimitive(result.wallTimeMs()), json.get("wallTimeMs"));
        assertEquals(new JsonPrimitive(result.heapDeltaBytes()), json.get("heapDeltaBytes"));
        assertEquals(0, json.getAsJsonArray("failingTraces").size());
        assertEquals(0, json.getAsJsonArray("deadlockedTraces").size());
        assertEquals(0, json.getAsJsonArray("incompleteTraces").size());
        var completed = json.getAsJsonArray("completedTraces");
        assertEquals(1, completed.size());
        assertEquals(new JsonPrimitive("COMPLETED"), completed.get(0).getAsJsonObject().get("outcome"));
        assertEquals(List.of(0, 1), completed.get(0).getAsJsonObject().getAsJsonArray("threads")
            .asList().stream().map(value -> value.getAsInt()).toList());
    }

    /** Construction snapshots lists, getters reject mutation, and JSON preserves the same values. */
    @Test
    void traceRecord_immutableAndSerializable() {
        var threads = new ArrayList<>(List.of(1, 0));
        var outcomes = new ArrayList<>(List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED));
        TraceRecord record = new TraceRecord(threads, outcomes, TraceOutcome.COMPLETED, "fixture-hash");
        threads.clear();
        outcomes.clear();
        assertEquals(List.of(1, 0), record.threadIds());
        assertEquals(List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED), record.outcomes());
        assertThrows(UnsupportedOperationException.class, () -> record.threadIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> record.outcomes().clear());
        var json = parseObject(record.toJson());
        assertEquals(List.of(1, 0), json.getAsJsonArray("threads").asList().stream()
            .map(value -> value.getAsInt()).toList());
        assertEquals(List.of("ADVANCED", "ADVANCED"), json.getAsJsonArray("outcomes").asList().stream()
            .map(value -> value.getAsString()).toList());
        assertEquals(new JsonPrimitive("COMPLETED"), json.get("outcome"));
        assertEquals(new JsonPrimitive("fixture-hash"), json.get("programHash"));
    }


    @Test
    void runner_enforcesMaxStates() {
        Program program = createPetersonProgram();

        InterleaveRunner runner = InterleaveRunner.builder()
            .maxStates(5)
            .build();

        TestResult result = runner.run(program);

        assertTrue(result.limitExceeded());
        assertEquals(5, result.statesExplored());
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
    /**
     * Compares deterministic search evidence while excluding wall-clock and memory measurements.
     * @param expected independently executed serial run
     * @param actual run on the reused runner
     */
    private static void assertSameSearch(TestResult expected, TestResult actual) {
        assertEquals(expected.strategy(), actual.strategy());
        assertEquals(expected.statesExplored(), actual.statesExplored());
        assertEquals(expected.limitExceeded(), actual.limitExceeded());
        assertEquals(expected.hasViolation(), actual.hasViolation());
        assertEquals(expected.hasIncomplete(), actual.hasIncomplete());
        assertEquals(signatures(expected.failingTraces()), signatures(actual.failingTraces()));
        assertEquals(signatures(expected.deadlockedTraces()), signatures(actual.deadlockedTraces()));
        assertEquals(signatures(expected.completedTraces()), signatures(actual.completedTraces()));
        assertEquals(signatures(expected.incompleteTraces()), signatures(actual.incompleteTraces()));
    }

    /**
     * Snapshots every immutable trace member for comparisons without object-identity equality.
     * @param traces categorized trace records
     * @return value signatures in recorded order
     */
    private static List<List<?>> signatures(List<TraceRecord> traces) {
        return traces.stream().<List<?>>map(trace -> Arrays.asList(
            trace.threadIds(), trace.outcomes(), trace.outcome(), trace.programHash())).toList();
    }
}
