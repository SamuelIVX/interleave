/** Facade forwarding, known outcomes, and typed JSON contracts. */
package dev.samhb.interleave.api;

import dev.samhb.interleave.testsupport.TestPrograms;
import static dev.samhb.interleave.testsupport.JsonAssertions.parseObject;
import com.google.gson.JsonPrimitive;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InterleaveApiTest {

    /** Repeated runs retain the known reachable count and schedule evidence. */
    @Test
    void interleaveFacade_verifyReturnsDeterministicResult() {
        Program program = TestPrograms.independentFlags();
        VerificationResult first = Interleave.verify(program, Strategy.DFS);
        VerificationResult second = Interleave.verify(program, Strategy.DFS);
        assertEquals(Strategy.DFS, first.strategyUsed());
        assertEquals(4, first.statesExplored());
        assertEquals(4, second.statesExplored());
        assertTrue(first.wallTimeMs() >= 0);
        assertTrue(second.wallTimeMs() >= 0);
        assertTrue(first.heapDeltaBytes() >= 0);
        assertTrue(second.heapDeltaBytes() >= 0);
        assertFalse(first.hasViolation());
        assertFalse(first.completedTraces().isEmpty());
        assertEquals(first.completedTraces().stream().map(Trace::threadIds).toList(),
            second.completedTraces().stream().map(Trace::threadIds).toList());
    }

    /** A facade must actually forward the supplied rejecting property. */
    @Test
    void interleaveFacade_verifyWithInvariant() {
        Program program = TestPrograms.independentFlags();
        Invariant property = (state, config) -> !((PetersonState) state).flag(0);
        VerificationResult result = Interleave.verify(program, Strategy.DFS, property);
        assertEquals(Strategy.DFS, result.strategyUsed());
        assertTrue(result.hasViolation());
        assertEquals(2, result.failingTraces().size());
        for (Trace trace : result.failingTraces()) {
            Configuration end = Interleave.replay(program, trace);
            assertFalse(property.holds(end.state(), end));
        }
    }

    /** A specified schedule produces the expected state, counters, and completion. */
    @Test
    void interleaveFacade_replayReturnsConfiguration() {
        Trace trace = Trace.of(List.of(1, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED), TraceOutcome.COMPLETED);
        Configuration end = Interleave.replay(TestPrograms.independentFlags(), trace);
        assertEquals(PetersonState.of(true, true, 0), end.state());
        assertEquals(List.of(1, 1), end.programCounters());
        assertTrue(end.allTerminated());
        assertEquals(StepOutcome.ADVANCED, end.lastOutcome());
    }

    /** Strict parsing rejects malformed text even when expected field names appear. */
    @Test
    void verificationResult_toJson_producesValidJson() {
        VerificationResult result = Interleave.verify(TestPrograms.independentFlags(), Strategy.DFS);
        var json = parseObject(result.toJson());
        assertEquals(new JsonPrimitive("DFS"), json.get("strategy"));
        assertEquals(new JsonPrimitive(4), json.get("statesExplored"));
        assertEquals(new JsonPrimitive(false), json.get("hasViolation"));
        assertEquals(new JsonPrimitive(result.wallTimeMs()), json.get("wallTimeMs"));
        assertEquals(new JsonPrimitive(result.heapDeltaBytes()), json.get("heapDeltaBytes"));
        assertEquals(0, json.getAsJsonArray("failingTraces").size());
        assertEquals(0, json.getAsJsonArray("deadlockedTraces").size());
        assertEquals(0, json.getAsJsonArray("incompleteTraces").size());
        var completed = json.getAsJsonArray("completedTraces");
        assertEquals(1, completed.size());
        var trace = completed.get(0).getAsJsonObject();
        assertEquals(new JsonPrimitive("COMPLETED"), trace.get("outcome"));
        assertEquals(List.of(0, 1), trace.getAsJsonArray("threads").asList().stream()
            .map(value -> value.getAsInt()).toList());
    }

    /** Each dispatch produces genuine completions whose schedules replay. */
    @Test
    void interleaveFacade_allStrategiesProduceResults() {
        Program program = TestPrograms.independentFlags();
        for (Strategy strategy : Strategy.values()) {
            VerificationResult result = Interleave.verify(program, strategy);
            assertEquals(strategy, result.strategyUsed());
            assertFalse(result.hasViolation());
            assertTrue(result.deadlockedTraces().isEmpty());
            assertTrue(result.incompleteTraces().isEmpty());
            assertFalse(result.completedTraces().isEmpty());
            for (Trace trace : result.completedTraces()) {
                Configuration end = Interleave.replay(program, trace);
                assertEquals(PetersonState.of(true, true, 0), end.state());
                assertTrue(end.allTerminated());
            }
        }
    }

    // NEW: Tests for quickCheck and builder

    /** The convenience entry point defaults to DFS over four reachable positions. */
    @Test
    void quickCheck_returnsTestResult() {
        TestResult result = Interleave.quickCheck(TestPrograms.independentFlags());
        assertEquals(Strategy.DFS, result.strategy());
        assertEquals(4, result.statesExplored());
        assertFalse(result.hasViolation());
        assertFalse(result.limitExceeded());
        assertEquals(1, result.completedTraces().size());
    }

    /** Static POR selection must alter exploration as well as the result label. */
    @Test
    void quickCheck_withStrategy_usesSpecifiedStrategy() {
        TestResult result = Interleave.quickCheck(TestPrograms.independentFlags(), Strategy.STATIC_POR);
        assertEquals(Strategy.STATIC_POR, result.strategy());
        assertEquals(3, result.statesExplored());
        assertFalse(result.hasViolation());
        assertEquals(1, result.completedTraces().size());
    }

    /** Facade-built defaults must run the model rather than merely construct an object. */
    @Test
    void builder_returnsInterleaveRunner() {
        TestResult result = Interleave.builder().build().run(TestPrograms.independentFlags());
        assertEquals(Strategy.DFS, result.strategy());
        assertEquals(4, result.statesExplored());
        assertEquals(1, result.completedTraces().size());
        assertFalse(result.limitExceeded());
    }

    /** Preserves metadata and content in all four trace buckets. */
    @Test
    void verificationResult_toTestResult_conversion() {
        Trace failure = Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION);
        Trace deadlock = Trace.of(List.of(1), List.of(StepOutcome.ADVANCED), TraceOutcome.DEADLOCK);
        Trace complete = Trace.of(List.of(0, 1), List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED), TraceOutcome.COMPLETED);
        Trace incomplete = Trace.incomplete(List.of(1, 0), List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED));
        VerificationResult source = VerificationResult.from(new dev.samhb.interleave.search.DfsResult(
            java.util.Map.of(), List.of(failure, deadlock, complete, incomplete), 7), Strategy.CONTEXT_BOUNDED, 11, 13);
        TestResult converted = source.toTestResult();
        assertEquals(Strategy.CONTEXT_BOUNDED, converted.strategy());
        assertEquals(7, converted.statesExplored());
        assertEquals(11, converted.wallTimeMs());
        assertEquals(13, converted.heapDeltaBytes());
        var originals = List.of(source.failingTraces(), source.deadlockedTraces(), source.completedTraces(), source.incompleteTraces());
        var records = List.of(converted.failingTraces(), converted.deadlockedTraces(), converted.completedTraces(), converted.incompleteTraces());
        for (int i = 0; i < originals.size(); i++) {
            assertEquals(1, records.get(i).size());
            assertEquals(originals.get(i).get(0).threadIds(), records.get(i).get(0).threadIds());
            assertEquals(originals.get(i).get(0).outcomes(), records.get(i).get(0).outcomes());
            assertEquals(originals.get(i).get(0).outcome(), records.get(i).get(0).outcome());
        }
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
