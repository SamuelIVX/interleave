/** Benchmark row qualification, immutable evidence attachment and best-effort snapshot contracts. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkResultTest {
    /** A corrupt optional snapshot cannot remove the incomplete verdict or recorded evidence. */
    @Test void incompleteSnapshotsAreBestEffort() {
        var model = new SourceProgramLoader().load("program bounded { shared int x = 0; thread 0 { while (true) limit 0 {} } }");
        Trace actual = new DfsExplorer().explore(model.program()).traces().getFirst();
        CutoffEvidence cutoff = actual.cutoff();
        var forged = new CutoffEvidence(cutoff.reason(), cutoff.threadId(), cutoff.instructionIndex(), 1, 1,
            cutoff.source(), cutoff.expansionPath());
        Trace invalid = new Trace(actual.threadIds(), actual.outcomes(), actual.outcome(),
            new TraceEvidence(model.program().sourceDigest(), actual.evidence().executedSteps(), null, forged));
        var evidence = new DfsResult(Map.of(), List.of(invalid), 1);
        var row = new BenchmarkResult("DFS", "bounded", 1, 0, 0, "INCOMPLETE")
            .withExploration(model.program(), evidence);
        assertEquals("INCOMPLETE", row.verdict());
        assertEquals(List.of(invalid), row.incompleteTraces());
        assertEquals(List.of(CutoffEvidence.Reason.LOOP_LIMIT), row.incompletenessReasons());
        assertTrue(row.incompleteStates().isEmpty());
        assertFalse(row.withExploration(null, evidence).sourceModel());
    }

    /** Only a source-model PASS with approximate storage acquires an approximate absence label.
     * @param context source, legacy or absent replay context
     * @param store visited-store mode represented by the row
     * @param verdict supplied exploration verdict
     * @param expected literal qualified verdict
     * @param expectedSource literal source-model marker
     */
    @ParameterizedTest
    @CsvSource({"source, EXACT, PASS, PASS, true", "source, BITSTATE, PASS, APPROXIMATE_PASS, true",
        "source, BITSTATE, VIOLATION, VIOLATION, true", "source, BITSTATE, DEADLOCK, DEADLOCK, true",
        "source, BITSTATE, INCOMPLETE, INCOMPLETE, true", "legacy, BITSTATE, PASS, PASS, false",
        "none, BITSTATE, PASS, PASS, false"})
    void qualifiesOnlySourceBitstatePass(String context, StoreType store, String verdict,
            String expected, boolean expectedSource) {
        Program program = switch (context) {
            case "source" -> new SourceProgramLoader().load("program marker { shared int x = 0; thread 0 {} }").program();
            case "legacy" -> dev.samhb.interleave.bugs.BugCorpus.lostUpdate().program();
            case "none" -> null;
            default -> throw new IllegalArgumentException("Unknown fixture context");
        };
        var base = new BenchmarkResult("DFS", "marker", 17, 23, 31, verdict, store);
        var row = base.withExploration(program, new DfsResult(Map.of(), List.of(), 0));
        assertEquals(expected, row.verdict());
        assertEquals(expectedSource, row.sourceModel());
        assertEquals(store, row.storeType());
        assertEquals(verdict, base.verdict());
        assertFalse(base.sourceModel());
        assertTrue(row.incompleteTraces().isEmpty());
        assertTrue(row.incompleteStates().isEmpty());
    }

    /** Attaching mixed terminal evidence preserves measured fields and selects the first bounded prefix. */
    @Test
    void attachesOrderedEvidenceWithoutReplacingMeasurementsOrFailureSnapshots() {
        var model = new SourceProgramLoader().load("""
            program evidence { shared int counter = 0;
              thread 0 { atomic { counter = 7; } atomic { counter = 8; } while (counter == 8) limit 0 {} }
              thread 1 { atomic { counter = 9; } }
              thread 2 { atomic { counter = 1 % 0; } }
            }
            """);
        Program program = model.program();
        var driver = new ExecutionDriver();
        var failed = driver.runDetailed(program, new Schedule(List.of(2)), null);
        var first = driver.runDetailed(program, new Schedule(List.of(0, 0, 0)), null);
        var second = driver.runDetailed(program, new Schedule(List.of(1, 0, 0, 0)), null);
        assertEquals(ScheduleExecution.Kind.VIOLATION, failed.kind());
        assertEquals(ScheduleExecution.Kind.INCOMPLETE, first.kind());
        assertEquals(ScheduleExecution.Kind.INCOMPLETE, second.kind());
        ExecutedStep omitted = ((SourceStep) program.threads().get(1).steps().getFirst()).metadata(0);
        var bound = new CutoffEvidence(CutoffEvidence.Reason.PREEMPTION_BOUND, 1, 0, 0, null,
            omitted.source(), omitted.expansionPath());
        Trace preemption = Trace.create(program, List.of(0), List.of(StepOutcome.ADVANCED),
            TraceOutcome.INCOMPLETE, null, bound);
        var evidence = new DfsResult(Map.of(),
            List.of(failed.trace(), preemption, first.trace(), second.trace()), 999);
        StateSnapshot initial = StateSnapshot.ofFields(Map.of("counter", 0));
        var snapshots = new ArrayList<>(List.of(initial, initial));
        var base = new BenchmarkResult("CB(K=0)", "evidence", 17, 23, 31, "VIOLATION", failed.trace(),
            StoreType.BITSTATE, 0.125, 8, 0.25, 0, snapshots);
        snapshots.clear();
        var row = base.withExploration(program, evidence);

        assertEquals("CB(K=0)", row.strategy());
        assertEquals("evidence", row.bugName());
        assertEquals(17, row.statesExplored());
        assertEquals(23, row.wallTimeMs());
        assertEquals(31, row.heapDeltaBytes());
        assertEquals("VIOLATION", row.verdict());
        assertEquals(StoreType.BITSTATE, row.storeType());
        assertEquals(0.125, row.estimatedFalsePositiveRate());
        assertEquals(8, row.bitstateBitCount());
        assertEquals(0.25, row.bitstateBitDensity());
        assertEquals(Integer.valueOf(0), row.preemptionsUsed());
        assertEquals(Optional.of(failed.trace()), row.failingTrace());
        assertEquals(List.of(initial, initial), row.states());
        assertEquals(List.of(preemption, first.trace(), second.trace()), row.incompleteTraces());
        assertEquals(List.of(initial, StateSnapshot.ofFields(Map.of("counter", 7))), row.incompleteStates());
        assertEquals(List.of(CutoffEvidence.Reason.LOOP_LIMIT, CutoffEvidence.Reason.PREEMPTION_BOUND),
            row.incompletenessReasons());
        assertTrue(row.sourceModel());
        assertThrows(UnsupportedOperationException.class, () -> row.incompleteTraces().add(failed.trace()));
        assertThrows(UnsupportedOperationException.class, () -> row.incompleteStates().clear());
        assertThrows(UnsupportedOperationException.class, () -> row.states().clear());
        assertTrue(base.incompleteTraces().isEmpty());
        assertTrue(base.incompleteStates().isEmpty());
        assertFalse(base.sourceModel());
        assertEquals(List.of(initial, initial), base.states());
    }

    /** Explicitly absent failure snapshots normalize to an immutable empty collection through attachment. */
    @Test
    void nullFailureSnapshotsRemainEmptyAndImmutable() {
        var base = new BenchmarkResult("DFS", "empty", 1, 0, 0, "PASS", null,
            StoreType.EXACT, 0.0, 0, 0.0, null, null);
        var row = base.withExploration(null, new DfsResult(Map.of(), List.of(), 0));
        assertEquals(List.of(), base.states());
        assertEquals(List.of(), row.states());
        assertThrows(UnsupportedOperationException.class,
            () -> row.states().add(StateSnapshot.ofFields(Map.of("counter", 1))));
    }

}
