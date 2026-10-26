/** Complete report JSON contracts across machine locales and source/legacy payloads. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.search.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.samhb.interleave.testsupport.JsonAssertions.parseObject;
import static org.junit.jupiter.api.Assertions.*;

class ReportJsonContractTest {
    /** JSON numbers retain ASCII syntax and literal values independently of the process format locale.
     * @param tag Latin-digit or Arabic-digit format locale
     */
    @ParameterizedTest
    @ValueSource(strings = {"en-US", "ar-EG"})
    @ResourceLock(Resources.LOCALE)
    void orderedRowsKeepTypedNumbersAcrossLocales(String tag) {
        Locale previous = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag(tag));
            var rows = List.of(new BenchmarkResult("DFS", "first", 17, 23, -31, "PASS"),
                new BenchmarkResult("CONTEXT_BOUNDED", "bounded", 19, 29, 37, "INCOMPLETE", null, StoreType.EXACT, 0.0, 0, 0.0, 0));
            var expected = parseObject("""
                {"benchmarks":[
                  {"bug":"first","strategy":"DFS","storeType":"EXACT","statesExplored":17,
                   "wallTimeMs":23,"heapDeltaBytes":-31,"preemptionsUsed":null,"verdict":"PASS"},
                  {"bug":"bounded","strategy":"CONTEXT_BOUNDED","storeType":"EXACT","statesExplored":19,
                   "wallTimeMs":29,"heapDeltaBytes":37,"preemptionsUsed":0,"verdict":"INCOMPLETE"}],
                 "soundness":true}
                """);
            assertEquals(expected, parseObject(new ReportWriter(rows, List.of()).writeJson()));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }
    }

    /** Empty reports and failed attestation rows expose the complete top-level Boolean contract. */
    @Test
    void emptyAndUnattestedViolationReportsHaveLiteralSoundness() {
        assertEquals(parseObject("{\"benchmarks\":[],\"soundness\":true}"),
            parseObject(new ReportWriter(List.of(), List.of()).writeJson()));
        var invalid = new BenchmarkResult("DFS", "missing", 1, 2, 3, "VIOLATION");
        assertEquals(parseObject("""
            {"benchmarks":[{"bug":"missing","strategy":"DFS","storeType":"EXACT","statesExplored":1,
              "wallTimeMs":2,"heapDeltaBytes":3,"preemptionsUsed":null,"verdict":"VIOLATION"}],
              "soundness":false}
            """), parseObject(new ReportWriter(List.of(invalid), List.of()).writeJson()));
    }

    /** Scientific, fixed and exact-threshold metrics preserve numeric values rather than locale/text style. */
    @Test
    void bitstateMetricsRetainSmallAndFixedNumericValues() {
        var rows = List.of(new BenchmarkResult("DFS", "metrics", 5, 7, 11, "PASS", null,
                StoreType.BITSTATE, 1.25e-8, 7, 0.123456),
            new BenchmarkResult("DFS", "threshold", 13, 17, 19, "PASS", null,
                StoreType.BITSTATE, 0.001, 3, 0.0));
        assertEquals(parseObject("""
            {"benchmarks":[
             {"bug":"metrics","strategy":"DFS","storeType":"BITSTATE","statesExplored":5,
              "wallTimeMs":7,"heapDeltaBytes":11,"preemptionsUsed":null,
              "bitstateMetrics":{"falsePositiveRate":0.0000000125,"bitCount":7,"bitDensity":0.123456},"verdict":"PASS"},
             {"bug":"threshold","strategy":"DFS","storeType":"BITSTATE","statesExplored":13,
              "wallTimeMs":17,"heapDeltaBytes":19,"preemptionsUsed":null,
              "bitstateMetrics":{"falsePositiveRate":0.001,"bitCount":3,"bitDensity":0.0},"verdict":"PASS"}],
             "soundness":true}
            """), parseObject(new ReportWriter(rows, List.of()).writeJson()));
    }

    /** Genuine legacy failures retain their older wire keys and optional complete state history.
     * @param includeStates whether the optional display history is supplied
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyFailureKeepsCompatibilityAndOptionalSnapshots(boolean includeStates) {
        Program program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new WriteFlagStep(1, true)))));
        Invariant property = Invariant.observing(Set.of(MemoryLocation.of("flag[0]"), MemoryLocation.of("flag[1]")),
            state -> !(((PetersonState) state).flag(0) && ((PetersonState) state).flag(1)));
        var model = new BenchmarkProgram("flags", program, "VIOLATION", property);
        DfsResult result = new DfsExplorer().explore(program, property);
        assertEquals(4, result.statesExplored());
        assertEquals(1, result.traces().size());
        Trace trace = result.traces().getFirst();
        assertEquals(List.of(0, 1), trace.threadIds());
        List<StateSnapshot> states = includeStates ? new TraceReplayer().captureSnapshots(program, trace) : List.of();
        var row = new BenchmarkResult("DFS", "flags", result.statesExplored(), 7, -3, "VIOLATION", trace,
            StoreType.EXACT, 0.0, 0, 0.0, null, states);
        var expected = parseObject("""
            {"benchmarks":[{"bug":"flags","strategy":"DFS","storeType":"EXACT","statesExplored":4,
             "wallTimeMs":7,"heapDeltaBytes":-3,"preemptionsUsed":null,"verdict":"VIOLATION",
             "failingTrace":{"threadIds":[0,1],"outcomes":["ADVANCED","ADVANCED"],"states":[
               {"fields":{"flag":[false,false],"turn":0,"inCriticalSection":-1},"locals":{}},
               {"fields":{"flag":[true,false],"turn":0,"inCriticalSection":-1},"locals":{}},
               {"fields":{"flag":[true,true],"turn":0,"inCriticalSection":-1},"locals":{}}]}}],"soundness":true}
            """);
        if (!includeStates) expected.getAsJsonArray("benchmarks").get(0).getAsJsonObject()
            .getAsJsonObject("failingTrace").remove("states");
        assertEquals(expected, parseObject(new ReportWriter(List.of(row), List.of(model)).writeJson()));
    }

    /** Source failure rows retain both choice aliases, structured diagnostics and rollback snapshots. */
    @Test
    void sourceFailureKeepsBothChoiceAliasesAndDiagnostics() {
        var model = new SourceProgramLoader().load(
            "program record { shared int x = 0; thread 0 { atomic { x = 1 % 0; } } }");
        DfsResult result = new DfsExplorer().explore(model.program());
        assertEquals(1, result.statesExplored());
        Trace trace = result.traces().getFirst();
        List<StateSnapshot> states = new TraceReplayer().captureSnapshots(model.program(), trace);
        var row = new BenchmarkResult("DFS", "record", result.statesExplored(), 7, -3, "VIOLATION", trace,
            StoreType.EXACT, 0.0, 0, 0.0, null, states).withExploration(model.program(), result);
        assertEquals(parseObject("""
            {"benchmarks":[{"bug":"record","strategy":"DFS","storeType":"EXACT","statesExplored":1,
              "wallTimeMs":7,"heapDeltaBytes":-3,"preemptionsUsed":null,"programKind":"SOURCE","verdict":"VIOLATION",
              "failingTrace":{"threads":[0],"threadIds":[0],"outcomes":["ASSERTION_FAILED"],
               "outcome":"VIOLATION","programHash":"",
               "states":[{"fields":{"x":0},"locals":{}},{"fields":{"x":0},"locals":{}}],
               "sourceDigest":"7fbbaf463008f20e53ffe0a8a50d5df07ab4d7827d077fed749d3a2587a604f4",
               "executedSteps":[{"instructionIndex":0,
                 "source":{"file":"<source>","line":1,"column":47,"endLine":1,"endColumn":68},
                 "label":"atomic { x = 1 % 0; }","expansionPath":[]}],
               "failure":{"kind":"RUNTIME_ERROR","phase":"STEP",
                 "source":{"file":"<source>","line":1,"column":56,"endLine":1,"endColumn":66},
                 "category":"MODULO_BY_ZERO"}}}],"soundness":true}
            """), parseObject(new ReportWriter(List.of(row), List.of(model)).writeJson()));
    }

    /** Source cutoff rows retain incompleteness reasons and an omitted-action witness without a failure alias. */
    @Test
    void sourceCutoffKeepsReasonsRepresentativeAndSnapshot() {
        var model = new SourceProgramLoader().load(
            "program record { shared int x = 0; thread 0 { while (true) limit 0 {} } }");
        DfsResult result = new DfsExplorer().explore(model.program());
        assertEquals(1, result.statesExplored());
        var row = new BenchmarkResult("DFS", "record", result.statesExplored(), 7, -3, "INCOMPLETE")
            .withExploration(model.program(), result);
        assertEquals(parseObject("""
            {"benchmarks":[{"bug":"record","strategy":"DFS","storeType":"EXACT","statesExplored":1,
              "wallTimeMs":7,"heapDeltaBytes":-3,"preemptionsUsed":null,"programKind":"SOURCE","verdict":"INCOMPLETE",
              "incompletenessReasons":["LOOP_LIMIT"],"incompleteTrace":{"threads":[],"outcomes":[],"outcome":"INCOMPLETE",
                "programHash":"","states":[{"fields":{"x":0},"locals":{}}],
                "sourceDigest":"21e419ad70e03cf280a0e2f7edb700f458663316abd36e4fc7c09a656c56581a",
                "executedSteps":[],"cutoff":{"reason":"LOOP_LIMIT","threadId":0,"instructionIndex":0,
                  "limit":0,"iterations":0,
                  "source":{"file":"<source>","line":1,"column":47,"endLine":1,"endColumn":70},
                  "expansionPath":[{"sourceOffset":46,"iteration":0}]}}}],"soundness":true}
            """), parseObject(new ReportWriter(List.of(row), List.of(model)).writeJson()));
    }

}
