/** Static reduction comparisons and property-preservation counterexamples. */
package dev.samhb.interleave.por;

import java.util.stream.Collectors;

import dev.samhb.interleave.bugs.*;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;
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

    /** Compare actual outcome categories, anchored to a declared buggy corpus model. */
    @Test
    void staticPorProducesSameVerdictAsDfs() {
        var model = dev.samhb.interleave.bugs.LostUpdate.program();
        var invariant = model.invariant().orElseThrow();
        var dfs = new DfsExplorer().explore(model.program(), invariant);
        var por = new StaticPorExplorer().explore(model.program(), invariant);
        var expected = dfs.traces().stream().map(Trace::outcome).collect(Collectors.toSet());
        assertTrue(expected.contains(TraceOutcome.VIOLATION));
        assertEquals(expected, por.traces().stream().map(Trace::outcome).collect(Collectors.toSet()));
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

    /**
     * The reduction still applies when no invariant is supplied.
     *
     * <p>This is the case where {@link PersistentSetComputer} is sound for what is actually being asked
     * of it: the acyclic set preserves the existence of a deadlock, so tracing for a deadlock rather than
     * a user predicate is sound even though it is not reachability-complete.
     */
    @Test
    void staticPorReducesStatesWhenNoInvariantIsSupplied() {
        BenchmarkProgram program = BugCorpus.all().stream()
            .filter(p -> "lost-update".equals(p.name()))
            .findFirst()
            .orElseThrow();

        DfsResult dfsResult = new DfsExplorer().explore(program.program());
        DfsResult porResult = new StaticPorExplorer().explore(program.program());

        assertTrue(porResult.statesExplored() < dfsResult.statesExplored(),
            "Static POR should reduce states without an invariant. DFS: "
            + dfsResult.statesExplored() + ", POR: " + porResult.statesExplored());
    }

    /**
     * The guard, stated as a test: supplying an invariant removes the reduction entirely, so the
     * explorer explores exactly what the exhaustive oracle explores.
     *
     * <p>This is the property the old {@code staticPorReducesStatesWhenInvariantPresent} asserted in
     * reverse -- it required the reduction to survive an invariant, which is precisely the unsound
     * behaviour. Preserving the reduction there is what produced the false pass on
     * {@code broken-peterson-v2}.
     */
    @Test
    void supplyingAnInvariantDisablesTheReduction() {
        BenchmarkProgram program = BugCorpus.all().stream()
            .filter(p -> "lost-update".equals(p.name()))
            .findFirst()
            .orElseThrow();
        Invariant invariant = program.invariant().orElseThrow();

        DfsResult dfsResult = new DfsExplorer().explore(program.program(), invariant);
        DfsResult porResult = new StaticPorExplorer().explore(program.program(), invariant);

        assertEquals(dfsResult.statesExplored(), porResult.statesExplored(),
            "With an invariant the reduction must be disabled, so both explorers explore equally many "
            + "configurations. DFS: " + dfsResult.statesExplored() + ", POR: " + porResult.statesExplored());
    }

    /**
     * Independent flag writes commute but their intermediate states need not satisfy the same
     * invariant. Pins spec 13.08's counterexample: a reduced run misses flags [false, true], while
     * the invariant guard must visit it and report the violation just as the DFS oracle does.
     */
    @Test
    void independentWritesStillNeedTheInvariantGuard() {
        Program program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new WriteFlagStep(1, true)))
        ));
        Invariant invariant = (state, config) -> {
            PetersonState flags = (PetersonState) state;
            return flags.flag(0) || !flags.flag(1);
        };

        DfsResult exhaustive = new DfsExplorer().explore(program);
        DfsResult reduced = new StaticPorExplorer().explore(program);
        assertEquals(4, exhaustive.statesExplored(), "both orders reach four distinct configurations");
        assertEquals(3, reduced.statesExplored(), "the independent-thread fallback keeps one order");
        assertTrue(exhaustive.states().values().stream()
            .anyMatch(config -> !invariant.holds(config.state(), config)),
            "DFS reaches flags [false, true]");
        assertTrue(reduced.states().values().stream()
            .allMatch(config -> invariant.holds(config.state(), config)),
            "the reduced run omits exactly the state the invariant rejects");

        DfsResult checked = new StaticPorExplorer().explore(program, invariant);
        DfsResult oracle = new DfsExplorer().explore(program, invariant);
        assertEquals(oracle.statesExplored(), checked.statesExplored());
        assertEquals("VIOLATION", verdictOf(oracle));
        assertEquals("VIOLATION", verdictOf(checked), "the guard must preserve the DFS violation");
    }

    /**
     * Regression test for a soundness defect this explorer used to have.
     *
     * <p>Static POR retains threads with a current dependency, otherwise one arbitrary enabled thread.
     * That pairwise rule does not establish preservation of arbitrary state predicates. Invariants
     * were previously checked on the reduced visited set, so a violation reachable only through pruned
     * interleavings was never reported. See spec 13.08 for the distinction between a pairwise no-op
     * and a future property-preserving reduction.
     *
     * <p>{@code broken-peterson-v2} was the sharpest case: {@link DfsExplorer} found 5 violating
     * configurations and reported {@code VIOLATION}, while this explorer reported {@code COMPLETED} -- a
     * false pass against an expected verdict of {@code VIOLATION}.
     *
     * <p>The fix is the guard {@link dev.samhb.interleave.dpor.DporExplorer} already applies: an
     * invariant disables the reduction, so the traversal visits every configuration reachable without
     * first passing through a violating one, which makes violation detection exhaustive. That is the
     * property asserted here, and it is deliberately not stated as complete configuration coverage — a
     * violating configuration's successors are never explored, so configurations reachable only beyond a
     * violation are never checked. That shortfall is harmless for detection, since the violation has
     * already been reported by the time the search truncates.
     */
    @Test
    void anInvariantDisablesTheReductionSoViolationsCannotBeMissed() {
        BenchmarkProgram program = BugCorpus.all().stream()
            .filter(p -> "broken-peterson-v2".equals(p.name()))
            .findFirst()
            .orElseThrow();
        Invariant invariant = program.invariant().orElseThrow();

        DfsResult porResult = new StaticPorExplorer().explore(program.program(), invariant);

        assertTrue(
            porResult.traces().stream()
                .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.VIOLATION),
            "StaticPorExplorer must report the violation in broken-peterson-v2 that it used to miss");
        assertEquals("VIOLATION", program.expectedVerdict());
    }

    /**
     * The differential property that was never asserted anywhere, and which would have caught the
     * false pass: with an invariant supplied, the reduced explorer must reach the same verdict as the
     * exhaustive one, on every corpus program that carries an invariant.
     *
     * <p>Note the comparison the existing tests do <em>not</em> make: {@code DslEquivalenceTest} compares
     * StaticPorExplorer only against its own typed/declarative re-encoding, never against
     * {@link DfsExplorer}, and it does so on {@code lost-update} -- a program where the reduction happens
     * to be correct. {@code staticPorReducesStatesWhenInvariantPresent} asserted verdict equality on that
     * same program. Both encode the belief that the comparison was meaningful when it was not.
     */
    @Test
    void withAnInvariantEveryExplorerAgreesWithDfsOnTheCorpus() {
        for (BenchmarkProgram program : BugCorpus.all()) {
            if (program.invariant().isEmpty()) continue;
            Invariant invariant = program.invariant().orElseThrow();

            DfsResult dfs = new DfsExplorer().explore(program.program(), invariant);
            DfsResult por = new StaticPorExplorer().explore(program.program(), invariant);

            assertEquals(verdictOf(dfs), verdictOf(por),
                "explorer verdicts must match DfsExplorer for " + program.name());
            assertEquals(verdictOf(dfs), program.expectedVerdict(),
                "DfsExplorer must agree with the declared expected verdict for " + program.name());
        }
    }

    /** Reduces to the same coarse verdict the existing tests use. */
    private static String verdictOf(DfsResult result) {
        if (result.traces().stream()
                .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.VIOLATION)) {
            return "VIOLATION";
        }
        if (result.traces().stream()
                .anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.DEADLOCK)) {
            return "DEADLOCK";
        }
        return "PASS";
    }
}
