/** Independent value/schedule oracles for readable bounded concurrent models. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.*;
import dev.samhb.interleave.report.*;
import dev.samhb.interleave.minimize.DeltaDebugger;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceProgramLoaderTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** Separate read/write boundaries permit a lost update; grouped increments do not. */
    @Test void splitAndAtomicIncrementsHaveIndependentFinalValueOracles() {
        String split = "atomic { local.r = counter; } atomic { counter = local.r + 1; }";
        BenchmarkProgram model = loader.load(twoThreads(split));
        assertEquals(Set.of(1, 2), finalValues(model));
        BenchmarkProgram atomic = loader.load(twoThreads("atomic { counter = counter + 1; }"));
        assertEquals(Set.of(2), finalValues(atomic));
    }

    /** @return a two-thread source model with the same body in each thread */
    private static String twoThreads(String body) {
        return "program test { shared int counter = 0; local int r = 0; thread 0 { " + body
            + " } thread 1 { " + body + " } }";
    }

    /** @return independently observable terminal counter values, not encoder keys */
    private static Set<Integer> finalValues(BenchmarkProgram model) {
        return new DfsExplorer().explore(model.program()).states().values().stream()
            .filter(Configuration::allTerminated).map(c -> ((DynamicState) c.state()).getInt("counter"))
            .collect(Collectors.toSet());
    }

    /** Branch choice persists after a concurrent write between decision and body. */
    @Test void branchChoosesOnceAndSkipsTheOtherArm() {
        BenchmarkProgram model = loader.load("""
            program branch { shared int counter = 0;
              thread 0 { if (counter == 0) { atomic { counter = 2; } }
                         else { atomic { counter = 3; } } }
              thread 1 { atomic { counter = 1; } }
            }
            """);
        Configuration end = new ExecutionDriver().run(model.program(), new Schedule(List.of(0, 1, 0)));
        assertEquals(2, ((DynamicState) end.state()).getInt("counter"));
        assertTrue(end.allTerminated());
    }

    /** Exactly N successful iterations require a final false check, not unconditional truncation. */
    @Test void exactLimitExitAndTrueFinalConditionAreDifferent() {
        BenchmarkProgram exit = loader.load("""
            program exit { shared int counter = 0; thread 0 {
              while (counter < 2) limit 2 { atomic { counter = counter + 1; } }
            } }
            """);
        DfsResult complete = new DfsExplorer().explore(exit.program());
        assertEquals(Set.of(TraceOutcome.COMPLETED), outcomes(complete));
        assertEquals(Set.of(2), finalValues(exit));
        BenchmarkProgram cut = loader.load("""
            program cut { shared int counter = 0; thread 0 {
              while (true) limit 2 { atomic { counter = counter + 1; } }
            } }
            """);
        assertEquals(Set.of(TraceOutcome.INCOMPLETE), outcomes(new DfsExplorer().explore(cut.program())));
    }

    /** A cutoff inside an atomic action cannot publish its tentative assignments. */
    @Test void atomicCutoffRollsBackAndKeepsAnEmptyCommittedPrefix() {
        BenchmarkProgram cut = loader.load("""
            program cut { shared int counter = 0; thread 0 { atomic {
              counter = 9; while (true) limit 0 { counter = 10; }
            } } }
            """);
        DfsResult result = new DfsExplorer().explore(cut.program());
        Trace trace = result.traces().getFirst();
        assertEquals(TraceOutcome.INCOMPLETE, trace.outcome());
        assertEquals(List.of(), trace.threadIds());
        assertEquals(CutoffEvidence.Reason.LOOP_LIMIT, trace.cutoff().reason());
        assertTrue(result.states().values().stream().allMatch(c -> ((DynamicState) c.state()).getInt("counter") == 0));
        assertThrows(SourceExecutionException.class,
            () -> new ExecutionDriver().run(cut.program(), new Schedule(List.of(0))));
    }

    /** @return actual reached terminal classes */
    private static Set<TraceOutcome> outcomes(DfsResult result) {
        return result.traces().stream().map(Trace::outcome).collect(Collectors.toSet());
    }

    /** Parsing rejects unsupported constructs and resource expansion before execution. */
    @Test void rejectsUnknownNamesAndHugeExpansionWithSourceLocations() {
        RegistryException name = assertThrows(RegistryException.class,
            () -> loader.load("program bad { shared int counter = 0; thread 0 { atomic { missing = 1; } } }"));
        assertTrue(name.getMessage().startsWith("<source>:1:"));
        assertThrows(RegistryException.class, () -> loader.load(
            "program huge { shared int counter = 0; thread 0 { repeat 2147483647 { atomic {} } } }"));
    }

    /** All exact strategies preserve a true final loop condition and its replayable witness. */
    @Test void allStrategiesRetainAndValidateLoopCutoffs() {
        BenchmarkProgram model = loader.load("""
            program bounded { shared int counter = 0;
              thread 0 { while (true) limit 1 { atomic { counter = counter + 1; } } }
              thread 1 { atomic {} }
              invariant always (counter >= 0);
            }
            """);
        Invariant property = model.invariant().orElseThrow();
        List<DfsResult> results = List.of(new DfsExplorer().explore(model.program(), property),
            new StaticPorExplorer().explore(model.program(), property),
            new DporExplorer().explore(model.program(), property),
            new ContextBoundedExplorer().explore(model.program(), property));
        for (DfsResult result : results) {
            assertTrue(outcomes(result).contains(TraceOutcome.INCOMPLETE));
            for (Trace trace : result.traces()) {
                if (trace.outcome() == TraceOutcome.INCOMPLETE) {
                    assertNotNull(trace.cutoff());
                    assertEquals(CutoffEvidence.Reason.LOOP_LIMIT, trace.cutoff().reason());
                }
                Configuration replayed = new TraceReplayer().replay(model.program(), trace, property);
                assertEquals(1, ((DynamicState) replayed.state()).getInt("counter"));
            }
        }
    }

    /** Runtime failure is real even when rollback leaves a true safety property. */
    @Test void replayPreservesRollbackAndClassifiesRuntimeFailure() {
        BenchmarkProgram model = loader.load("""
            program failure { shared int counter = 0; thread 0 { atomic {
              counter = 9; counter = counter % 0;
            } } invariant always (counter == 0); }
            """);
        Trace trace = new DfsExplorer().explore(model.program(), model.invariant().orElseThrow()).traces().getFirst();
        assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, trace.failure().kind());
        assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, trace.failure().category());
        assertEquals(0, ((DynamicState) new TraceReplayer().replay(model.program(), trace).state()).getInt("counter"));
        assertEquals(2, new TraceReplayer().captureSnapshots(model.program(), trace).size());
        assertEquals(TraceOutcome.VIOLATION, new DeltaDebugger().minimize(model.program(), trace,
            TraceOutcome.VIOLATION, model.invariant().orElseThrow()).outcome());
    }

    /** Source reports never call a truncated loop PASS, including approximate stores. */
    @Test void reportsPreserveLoopReasonsAcrossEveryStrategy() {
        BenchmarkProgram model = loader.load("program bounded { shared int counter = 0; thread 0 { while (true) limit 0 {} } }");
        List<BenchmarkResult> rows = new BenchmarkHarness().runProgram(model);
        assertFalse(rows.isEmpty());
        for (BenchmarkResult row : rows) assertEquals("INCOMPLETE", row.verdict());
        String json = new ReportWriter(rows, List.of(model)).writeJson();
        assertTrue(json.contains("LOOP_LIMIT"));
        assertTrue(json.contains("incompleteTrace"));
        assertTrue(json.contains("\"soundness\": true"));
    }

    /** Full source traces retain metadata through every public result wrapper. */
    @Test void runnerAndResultSerializationKeepSourceIdentity() {
        BenchmarkProgram model = loader.load("program identity { shared int counter = 0; thread 0 { if (false) { atomic {} } else { atomic {} } } }");
        TestResult result = InterleaveRunner.builder().strategy(Strategy.DFS).build().run(model.program());
        assertTrue(result.toJson().contains("executedSteps"));
        assertTrue(result.completedTraces().getFirst().toJson().contains("sourceDigest"));
        assertTrue(Interleave.verify(model.program(), Strategy.DFS).toJson().contains("executedSteps"));
    }

    /** Produces a fresh Java report for the browser contract, with real skipped PCs and a cutoff. */
    @Test void writesFreshSourceVisualizerArtifact() throws java.io.IOException {
        BenchmarkProgram model = loader.load("""
            program browser { shared int counter = 0; thread 0 {
              if (false) { atomic { counter = 100; } }
              else { repeat 2 { atomic { counter = counter + 1; } } }
              while (true) limit 0 {}
            } }
            """);
        List<BenchmarkResult> rows = new BenchmarkHarness(1000003, 4, Set.of(StoreType.EXACT), Set.of("DFS")).runProgram(model);
        Trace trace = rows.getFirst().incompleteTraces().getFirst();
        assertEquals(List.of(0, 2, 3), trace.evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-visualizer.json"), new ReportWriter(rows, List.of(model)).writeJson());
    }

    /** Long left-associative expressions fail at the source boundary without JVM stack exhaustion. */
    @Test void rejectsHostileExpressionChainsAndReportsTheRightCategory() {
        RegistryException depth = assertThrows(RegistryException.class, () -> loader.load(
            "program deep { shared int counter = 0; thread 0 { atomic { counter = " + "1+".repeat(20000) + "1; } } }"));
        assertTrue(depth.getMessage().matches("<source>:[0-9]+:[0-9]+: limit: .*"), depth.getMessage());
        RegistryException unknown = assertThrows(RegistryException.class, () -> loader.load(
            "program bad { shared int counter = 0; thread 0 { atomic { counter = missing; } } }"));
        assertTrue(unknown.getMessage().matches("<source>:[0-9]+:[0-9]+: name: .*"), unknown.getMessage());
    }

    /** Zero work is eliminated without iterating a huge repeat count, but bodies still type-check. */
    @Test void zeroRepeatAndEmptyModelsHaveNoArtificialSteps() {
        BenchmarkProgram empty = loader.load("program empty { shared int counter = 0; thread 0 { repeat 2147483647 {} } }");
        DfsResult result = new DfsExplorer().explore(empty.program());
        assertEquals(1, result.statesExplored());
        assertEquals(List.of(), result.traces().getFirst().threadIds());
        assertThrows(RegistryException.class, () -> loader.load("program bad { shared int counter = 0; thread 0 { repeat 0 { atomic { missing = 1; } } } }"));
    }

    /** Branch decisions read their predicate but perform no shared writes. */
    @Test void branchFootprintDoesNotPretendToExecuteItsFutureArms() {
        BenchmarkProgram model = loader.load("program footprint { shared int counter = 0; thread 0 { if (counter == 0) { atomic { counter = 2; } } } }");
        Step decision = model.program().threads().getFirst().steps().getFirst();
        assertEquals(Set.of(MemoryLocation.of("counter")), decision.reads());
        assertEquals(Set.of(), decision.writes());
    }
}
