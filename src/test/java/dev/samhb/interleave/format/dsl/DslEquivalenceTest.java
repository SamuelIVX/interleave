package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.Interleave;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential equivalence test for declarative re-encoding of lost-update.
 * Verifies that the declarative JSON produces identical verdict and exploration
 * metrics to the typed Java version across all explorers, and that failing
 * traces replay to a violating configuration.
 */
class DslEquivalenceTest {
    private final ProgramLoader loader = new ProgramLoader();

    private BenchmarkProgram typed() {
        return BugCorpus.lostUpdate();
    }

    private BenchmarkProgram declarativeViaResource() {
        return loader.loadFromResource("dsl/lost-update-declarative.json");
    }

    private BenchmarkProgram declarativeViaFile() {
        Path p = Paths.get("src/test/resources/dsl/lost-update-declarative.json");
        return loader.loadFromFile(p);
    }

    @Test
    /** Verdict matches typed and equals expected_verdict. */
    void verdictMatchesTypedAndExpected() {
        BenchmarkProgram typedProg = typed();
        BenchmarkProgram dslProg = declarativeViaResource();

        // typed should be VIOLATION per corpus
        DfsResult typedRes = new DfsExplorer().explore(typedProg.program(), typedProg.invariant().orElse(null));
        DfsResult dslRes = new DfsExplorer().explore(dslProg.program(), dslProg.invariant().orElse(null));

        boolean typedViolation = typedRes.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
        boolean dslViolation = dslRes.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);

        assertTrue(typedViolation, "typed lost-update should have VIOLATION");
        assertTrue(dslViolation, "declarative lost-update should have VIOLATION");
        assertEquals("VIOLATION", dslProg.expectedVerdict(), "declarative expected_verdict should be VIOLATION");
        assertEquals(typedProg.expectedVerdict(), dslProg.expectedVerdict());
    }

    @Test
    /** statesExplored exactly equal across typed vs declarative for each explorer. */
    void statesExploredExactlyEqualPerExplorer() {
        BenchmarkProgram typedProg = typed();
        BenchmarkProgram dslProg = declarativeViaResource();

        // DFS
        DfsResult typedDfs = new DfsExplorer().explore(typedProg.program(), typedProg.invariant().orElse(null));
        DfsResult dslDfs = new DfsExplorer().explore(dslProg.program(), dslProg.invariant().orElse(null));
        assertEquals(typedDfs.statesExplored(), dslDfs.statesExplored(), "DFS statesExplored should be exactly equal");

        // Static POR
        DfsResult typedPor = new StaticPorExplorer().explore(typedProg.program(), typedProg.invariant().orElse(null));
        DfsResult dslPor = new StaticPorExplorer().explore(dslProg.program(), dslProg.invariant().orElse(null));
        assertEquals(typedPor.statesExplored(), dslPor.statesExplored(), "StaticPor statesExplored should be exactly equal");

        // DPOR
        DfsResult typedDpor = new DporExplorer().explore(typedProg.program(), typedProg.invariant().orElse(null));
        DfsResult dslDpor = new DporExplorer().explore(dslProg.program(), dslProg.invariant().orElse(null));
        assertEquals(typedDpor.statesExplored(), dslDpor.statesExplored(), "DPOR statesExplored should be exactly equal");
    }

    @Test
    /** Failing trace replays to a violating configuration. */
    void failingTraceReplaysToViolation() {
        BenchmarkProgram dslProg = declarativeViaResource();
        Program program = dslProg.program();
        var invariant = dslProg.invariant().orElse(null);
        assertNotNull(invariant, "declarative program should have invariant");

        DfsResult res = new DfsExplorer().explore(program, invariant);
        Trace failing = res.traces().stream().filter(t -> t.outcome() == TraceOutcome.VIOLATION).findFirst().orElse(null);
        assertNotNull(failing, "should have at least one VIOLATION trace");

        Configuration replayed = Interleave.replay(program, failing);
        assertFalse(invariant.holds(replayed.state(), replayed), "replayed configuration should violate invariant");
    }

    @Test
    /** Load via ProgramLoader.loadFromFile produces same verdict as typed. */
    void loadFromFileProducesSameVerdict() {
        BenchmarkProgram typedProg = typed();
        BenchmarkProgram fileProg = declarativeViaFile();

        DfsResult typedRes = new DfsExplorer().explore(typedProg.program(), typedProg.invariant().orElse(null));
        DfsResult fileRes = new DfsExplorer().explore(fileProg.program(), fileProg.invariant().orElse(null));

        boolean typedViol = typedRes.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
        boolean fileViol = fileRes.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
        assertEquals(typedViol, fileViol, "loadFromFile should produce same verdict as typed");
        assertEquals(typedRes.statesExplored(), fileRes.statesExplored(), "loadFromFile statesExplored should equal typed");
    }
}
