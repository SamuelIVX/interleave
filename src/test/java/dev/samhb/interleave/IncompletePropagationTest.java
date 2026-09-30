package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.minimize.DeltaDebugger;
import dev.samhb.interleave.search.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IncompletePropagationTest {

    private static Program twoThreadProgram() {
        ModelThread t0 = new ModelThread(0, List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)));
        ModelThread t1 = new ModelThread(1, List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)));
        return new Program(PetersonState.of(false, false, 0), List.of(t0, t1));
    }

    private static Trace incompleteTrace() {
        return Trace.incomplete(List.of(0, 1, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED, StepOutcome.ADVANCED));
    }

    private static DfsResult resultWith(Trace... traces) {
        return new DfsResult(Map.of(), List.of(traces), 7);
    }

    @Test
    void verificationResult_incompleteTrace_retained() {
        VerificationResult result = VerificationResult.from(
            resultWith(incompleteTrace()), Strategy.DFS, 1, 2);

        assertEquals(1, result.incompleteTraces().size());
        assertTrue(result.hasIncomplete());
        assertEquals(TraceOutcome.INCOMPLETE, result.incompleteTraces().get(0).outcome());
    }

    @Test
    void verificationResult_incompleteTrace_doesNotLeakIntoOtherBuckets() {
        VerificationResult result = VerificationResult.from(
            resultWith(incompleteTrace()), Strategy.DFS, 1, 2);

        // INCOMPLETE is a statement about completeness, not correctness, so it must never be
        // folded into a failing outcome.
        assertFalse(result.hasViolation());
        assertTrue(result.failingTraces().isEmpty());
        assertTrue(result.deadlockedTraces().isEmpty());
        assertTrue(result.completedTraces().isEmpty());
    }

    @Test
    void verificationResult_noIncomplete_emptyBucket() {
        VerificationResult result = VerificationResult.from(
            resultWith(Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.COMPLETED)),
            Strategy.DFS, 1, 2);

        assertTrue(result.incompleteTraces().isEmpty());
        assertFalse(result.hasIncomplete());
    }

    @Test
    void verificationResult_incompleteTrace_appearsInToJson() {
        String json = VerificationResult.from(resultWith(incompleteTrace()), Strategy.DFS, 1, 2).toJson();
        assertTrue(json.contains("\"incompleteTraces\""), json);
    }

    @Test
    void verificationResult_toTestResult_carriesIncomplete() {
        TestResult converted = VerificationResult.from(
            resultWith(incompleteTrace()), Strategy.DFS, 1, 2).toTestResult();

        assertTrue(converted.hasIncomplete());
        assertEquals(1, converted.incompleteTraces().size());
        assertEquals(TraceOutcome.INCOMPLETE, converted.incompleteTraces().get(0).outcome());
    }

    @Test
    void testResult_toJson_incompleteTracesKeyPresentWhenEmpty() {
        // Emitted unconditionally so the JSON shape does not depend on content; consumers branch
        // on the key rather than on its presence.
        TestResult result = new TestResult(Strategy.DFS, 3, 1, 0,
            List.of(), List.of(), List.of(), List.of(), false);
        assertTrue(result.toJson().contains("\"incompleteTraces\": []"), result.toJson());
    }

    @Test
    void testResult_toJson_incompleteTracesKeyPopulated() {
        TraceRecord record = incompleteTrace().toRecord();
        TestResult result = new TestResult(Strategy.DFS, 3, 1, 0,
            List.of(), List.of(), List.of(), List.of(record), false);
        String json = result.toJson();
        assertTrue(json.contains("\"incompleteTraces\""), json);
        assertTrue(json.contains("INCOMPLETE"), json);
    }

    @Test
    void testResult_roundTrip_existingMembersUnchanged() {
        // Regression guard: adding incompleteTraces must not perturb any pre-existing member.
        TestResult result = new TestResult(Strategy.DFS, 42, 7, 99,
            List.of(Trace.of(List.of(0), List.of(StepOutcome.ADVANCED), TraceOutcome.VIOLATION).toRecord()),
            List.of(), List.of(), List.of(), false);
        String json = result.toJson();

        assertTrue(json.contains("\"strategy\": \"DFS\""), json);
        assertTrue(json.contains("\"statesExplored\": 42"), json);
        assertTrue(json.contains("\"wallTimeMs\": 7"), json);
        assertTrue(json.contains("\"heapDeltaBytes\": 99"), json);
        assertTrue(json.contains("\"hasViolation\": true"), json);
        assertTrue(json.contains("\"limitExceeded\": false"), json);
        assertTrue(json.contains("\"failingTraces\""), json);
        assertTrue(json.contains("\"deadlockedTraces\""), json);
        assertTrue(json.contains("\"completedTraces\""), json);
    }

    @Test
    void testResult_shapeIndependentOfContent() {
        // Two results differing only in whether an INCOMPLETE trace is present must have the same
        // key set, so a future "omit when empty" optimization cannot make the shape content-dependent.
        String withoutIncomplete = new TestResult(Strategy.DFS, 1, 1, 0,
            List.of(), List.of(), List.of(), List.of(), false).toJson();
        String withIncomplete = new TestResult(Strategy.DFS, 1, 1, 0,
            List.of(), List.of(), List.of(), List.of(incompleteTrace().toRecord()), false).toJson();

        assertEquals(jsonKeys(withoutIncomplete), jsonKeys(withIncomplete));
    }

    private static List<String> jsonKeys(String json) {
        // Depth-aware scan for top-level members only. Indentation is not usable here: the trace
        // serializer emits nested fields at the same two-space indent as the top level.
        List<String> keys = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                // A key is a string that is immediately followed by ':' at depth 1.
                if (depth == 1) {
                    int j = i + 1;
                    while (j < json.length() && json.charAt(j) != '"') {
                        j++;
                    }
                    if (j + 1 < json.length() && json.charAt(j + 1) == ':') {
                        keys.add(json.substring(i + 1, j));
                    }
                }
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return keys;
    }

    @Test
    void trace_incomplete_factory_setsOutcome() {
        Trace trace = incompleteTrace();
        assertEquals(TraceOutcome.INCOMPLETE, trace.outcome());
    }

    @Test
    void trace_incomplete_requiresEqualLengthLists() {
        assertThrows(IllegalArgumentException.class,
            () -> Trace.incomplete(List.of(0, 1), List.of(StepOutcome.ADVANCED)));
    }

    @Test
    void deltaDebugger_minimize_incompleteTrace_rejected() {
        DeltaDebugger debugger = new DeltaDebugger();
        assertThrows(IllegalArgumentException.class,
            () -> debugger.minimize(twoThreadProgram(), incompleteTrace(),
                TraceOutcome.INCOMPLETE, null));
    }

    @Test
    void deltaDebugger_minimize_violationTrace_stillMinimizes() {
        // Regression guard: the new case and the new guard must not disturb ddmin.
        BenchmarkProgram buggy = BugCorpus.all().stream()
            .filter(p -> "VIOLATION".equals(p.expectedVerdict()))
            .findFirst()
            .orElseThrow();
        Program program = buggy.program();
        Invariant invariant = buggy.invariant().orElseThrow();

        TestResult result = InterleaveRunner.builder().invariant(invariant).build().run(program);
        assertFalse(result.failingTraces().isEmpty(), "expected a genuine violation to minimize");

        Trace original = new Trace(result.failingTraces().get(0).threadIds(),
            result.failingTraces().get(0).outcomes(), TraceOutcome.VIOLATION);
        Trace minimized = new DeltaDebugger().minimize(program, original,
            TraceOutcome.VIOLATION, invariant);

        assertNotNull(minimized);
        assertTrue(minimized.length() > 0, "minimized trace should not be empty");
        assertTrue(minimized.length() <= original.length(),
            "minimized trace should not grow: " + minimized.length() + " vs " + original.length());
        // A VIOLATION can be established either by an ASSERTION_FAILED step or by the invariant
        // failing on the replayed configuration, so the outcome is the thing to preserve, not a
        // particular step outcome.
        assertEquals(TraceOutcome.VIOLATION, minimized.outcome());
    }
}
