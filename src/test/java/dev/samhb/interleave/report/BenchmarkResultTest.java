/** Benchmark evidence survives unavailable or invalid optional snapshot replay. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
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
}
