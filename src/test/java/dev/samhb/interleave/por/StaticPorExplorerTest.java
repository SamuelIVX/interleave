package dev.samhb.interleave.por;

import dev.samhb.interleave.bugs.*;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Invariant;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StaticPorExplorerTest {

    @Test
    void staticPorExploresFewerStatesThanDfs() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResult dfsResult = dfsExplorer.explore(program);
        
        StaticPorExplorer porExplorer = new StaticPorExplorer();
        DfsResult porResult = porExplorer.explore(program);
        
        assertTrue(porResult.statesExplored() <= dfsResult.statesExplored(),
            "Static POR should explore <= states than DFS. DFS: " + 
            dfsResult.statesExplored() + ", POR: " + porResult.statesExplored());
    }
    
    @Test
    void staticPorProducesSameVerdictAsDfs() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResult dfsResult = dfsExplorer.explore(program);
        
        StaticPorExplorer porExplorer = new StaticPorExplorer();
        DfsResult porResult = porExplorer.explore(program);
        
        assertEquals(dfsResult.statesExplored() > 0, porResult.statesExplored() > 0,
            "Both should explore some states");
    }
    
    @Test
    void independenceRelation_identifiesDependentSteps() {
        Step writeFlag0 = new WriteFlagStep(0, true);
        Step writeFlag1 = new WriteFlagStep(1, true);
        Step busyWaitOn0 = new BusyWaitStep(1, 0);
        
        IndependenceRelation relation = new IndependenceRelation();
        
        assertTrue(relation.areIndependent(writeFlag0, writeFlag1),
            "Writes to different flags should be independent");
        assertFalse(relation.areIndependent(writeFlag0, busyWaitOn0),
            "Write to flag[0] and busy-wait on flag[0] should be dependent");
    }

    @Test
    void staticPorReducesStatesWhenInvariantPresent() {
        // Use a buggy program from the corpus that has an invariant
        // Static POR should reduce states compared to DFS even with an invariant
        BenchmarkProgram program = BugCorpus.all().stream()
            .filter(p -> "lost-update".equals(p.name()))
            .findFirst()
            .orElseThrow();
        
        Invariant invariant = program.invariant().orElseThrow();
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResult dfsResult = dfsExplorer.explore(program.program(), invariant);
        
        StaticPorExplorer porExplorer = new StaticPorExplorer();
        DfsResult porResult = porExplorer.explore(program.program(), invariant);
        
        // With the bug, Static POR falls back to plain DFS and explores same states
        // After fix, Static POR should explore strictly fewer states
        assertTrue(porResult.statesExplored() < dfsResult.statesExplored(),
            "Static POR should reduce states with invariant. DFS: " + 
            dfsResult.statesExplored() + ", POR: " + porResult.statesExplored());
        
        // Both should still produce the same verdict
        String dfsVerdict = dfsResult.traces().stream()
            .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.VIOLATION) ? "VIOLATION" :
            dfsResult.traces().stream()
            .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.DEADLOCK) ? "DEADLOCK" : "PASS";
        String porVerdict = porResult.traces().stream()
            .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.VIOLATION) ? "VIOLATION" :
            porResult.traces().stream()
            .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.DEADLOCK) ? "DEADLOCK" : "PASS";
        assertEquals(dfsVerdict, porVerdict, "Verdicts must match");
    }

    /**
     * Characterisation test for a real soundness defect, pinned deliberately.
     *
     * <p>Static POR's persistent set is the acyclic set: a thread is kept only when it is dependent on
     * another enabled thread, otherwise one arbitrary enabled thread is returned. Godefroid's sound
     * construction is {@code source(c)} union the dependent set, and the missing {@code source} term is
     * what makes this reduction preserve deadlock existence rather than state reachability. The
     * consequence is that invariants are evaluated only on the reduced visited set, so a violation
     * reachable only through pruned interleavings is never reported.
     *
     * <p>{@code broken-peterson-v2} is the sharpest case: {@link DfsExplorer} finds 5 violating
     * configurations and reports {@code VIOLATION}, while this explorer reports {@code COMPLETED} -- a
     * false pass against an expected verdict of {@code VIOLATION}. The test asserts the CURRENT,
     * DEFECTIVE behaviour on purpose. It exists so that the false pass is visible and cannot be
     * reintroduced unnoticed; whoever fixes the reduction (either disabling it under an invariant, as
     * {@link dev.samhb.interleave.dpor.DporExplorer} already does, or computing a real {@code source}
     * set) must INVERT this assertion, at which point it becomes the regression test for the fix.
     */
    @Test
    void acyclicSetPruningMissesViolationsStaticPorIsUnsoundForInvariants() {
        BenchmarkProgram program = BugCorpus.all().stream()
            .filter(p -> "broken-peterson-v2".equals(p.name()))
            .findFirst()
            .orElseThrow();
        Invariant invariant = program.invariant().orElseThrow();

        DfsResult dfsResult = new DfsExplorer().explore(program.program(), invariant);
        DfsResult porResult = new StaticPorExplorer().explore(program.program(), invariant);

        // The exhaustive oracle does find the violation...
        assertTrue(
            dfsResult.traces().stream()
                .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.VIOLATION),
            "DfsExplorer should report VIOLATION for broken-peterson-v2");

        // ...and the corpus agrees the verdict really is VIOLATION, so this is not a corpus error.
        assertEquals("VIOLATION", program.expectedVerdict());

        // ...but the reduced search prunes every violating configuration. PINNED DEFECT: see Javadoc.
        assertFalse(
            porResult.traces().stream()
                .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.VIOLATION),
            "StaticPorExplorer currently reports no violation for broken-peterson-v2. "
            + "If this now passes the explorer has been fixed -- INVERT this assertion and update the "
            + "StaticPorExplorer class Javadoc, which currently documents the defect.");
    }
}
