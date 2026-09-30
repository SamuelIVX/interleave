package dev.samhb.interleave.cb;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.BitstateStore;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ContextBoundedExplorerTest {

    private static Program twoStepEachThread() {
        ModelThread t0 = new ModelThread(0, List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)));
        ModelThread t1 = new ModelThread(1, List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)));
        return new Program(PetersonState.of(false, false, 0), List.of(t0, t1));
    }

    private static BenchmarkProgram named(String name) {
        for (BenchmarkProgram p : BugCorpus.all()) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        throw new IllegalArgumentException("no such program: " + name);
    }

    // --- Preemption classification -------------------------------------------------

    @Test
    void zeroBudget_allowsOnlyContinuations() {
        // At K=0 the only schedules reachable are ones that never switch away from a thread that
        // could continue, so a two-step program yields very little.
        DfsResult k0 = new ContextBoundedExplorer().explore(twoStepEachThread(), null, null, null, 0);
        DfsResult k1 = new ContextBoundedExplorer().explore(twoStepEachThread(), null, null, null, 1);
        DfsResult k2 = new ContextBoundedExplorer().explore(twoStepEachThread(), null, null, null, 2);

        assertTrue(k0.statesExplored() > 0);
        assertTrue(k0.statesExplored() < k1.statesExplored(),
            "K=0 (" + k0.statesExplored() + ") should explore less than K=1 (" + k1.statesExplored() + ")");
        assertTrue(k1.statesExplored() < k2.statesExplored(),
            "K=1 (" + k1.statesExplored() + ") should explore less than K=2 (" + k2.statesExplored() + ")");
    }

    @Test
    void monotonic_statesExploredGrowsWithBound() {
        // The single most important property: a deeper bound must never explore less. This is
        // what a shared state store across bounds would break.
        for (String name : List.of("lost-update", "double-checked-locking", "peterson")) {
            Program program = named(name).program();
            long previous = 0;
            for (int k = 0; k <= 4; k++) {
                long states = new ContextBoundedExplorer().explore(program, null, null, null, k).statesExplored();
                assertTrue(states >= previous,
                    name + ": states explored decreased at K=" + k + " (" + previous + " -> " + states + ")");
                previous = states;
            }
        }
    }

    @Test
    void initialStep_isFree() {
        // The very first thread selection has no predecessor, so it must not consume budget:
        // K=0 must still be able to start a program at all.
        DfsResult k0 = new ContextBoundedExplorer().explore(twoStepEachThread(), null, null, null, 0);
        assertTrue(k0.statesExplored() > 1,
            "K=0 should still reach more than the initial configuration");
    }

    @Test
    void forcedSwitch_isFree_whenLastThreadBlocked() {
        // Thread 1 blocks on its very first step, so the only way to make progress is a switch
        // away from a thread that cannot continue. That switch must be free: if it were charged
        // as a preemption, a K=0 search could not drain the remaining thread to completion and
        // would report INCOMPLETE instead.
        Program program = new Program(DeadlockState.of(false, false), List.of(
            new ModelThread(0, List.of(
                new dev.samhb.interleave.bugs.DeadlockWriteFlagStep(0, true),
                new dev.samhb.interleave.bugs.DeadlockWriteFlagStep(0, false))),
            new ModelThread(1, List.of(new UnconditionalWaitStep(1, 0)))));

        DfsResult result = new ContextBoundedExplorer().explore(program, null, null, null, 0);

        assertTrue(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.COMPLETED),
            "the unblocked thread should still run to completion at K=0");
        assertFalse(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE),
            "a forced switch must not consume budget, so K=0 should suffice");
    }

    @Test
    void deadlockProgram_needsAPaidPreemption() {
        // Guards the classification from the opposite direction: the corpus deadlock program
        // genuinely does require a preemptive switch, so it must NOT be reachable at K=0. If this
        // ever started passing, forced switches would have started being charged as preemption.
        Program program = named("deadlock").program();
        assertFalse(new ContextBoundedExplorer().explore(program, null, null, null, 0).traces().stream()
                .anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK),
            "this deadlock needs at least one paid preemption");
        assertTrue(new ContextBoundedExplorer().explore(program, null, null, null, 1).traces().stream()
                .anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK),
            "and should be found once one is available");
    }

    // --- Violation detection -------------------------------------------------------

    @Test
    void violation_foundAtSufficientBound() {
        for (String name : List.of("lost-update", "double-checked-locking", "torn-counter")) {
            BenchmarkProgram program = named(name);
            DfsResult result = new ContextBoundedExplorer()
                .explore(program.program(), program.invariant().orElse(null), null, null, 3);
            assertTrue(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION),
                name + " should yield a violation at K=3");
        }
    }

    @Test
    void boundedSearch_withoutEnoughBudget_isIncomplete() {
        // peterson is correct: a bounded search that runs out of budget must say so rather than
        // reporting a clean pass.
        Program program = named("peterson").program();
        DfsResult result = new ContextBoundedExplorer().explore(program, null, null, null, 1);

        assertTrue(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE),
            "a bounded search that ran out of budget must report INCOMPLETE, not a pass");
        assertFalse(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION),
            "peterson is correct, so no violation may be reported");
    }

    @Test
    void exhaustiveAtSufficientBound_reportsPassNotIncomplete() {
        // The counterpart: a bound high enough to cover the whole space reports a real pass,
        // which is what makes the INCOMPLETE above meaningful rather than unconditional.
        Program program = named("peterson").program();
        DfsResult result = new ContextBoundedExplorer().explore(program, null, null, null, 12);

        assertFalse(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE),
            "with a bound above the program's needs, nothing should be pruned");
        assertTrue(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.COMPLETED));
    }

    // --- INCOMPLETE trace shape ----------------------------------------------------

    @Test
    void incompleteTrace_carriesNonEmptySchedule() {
        // The live DFS path lists are popped as the search unwinds, so reading them after the
        // search returns yields a valid-but-empty trace. Snapshot at the prune site instead.
        Program program = named("lost-update").program();
        DfsResult result = new ContextBoundedExplorer().explore(program, null, null, null, 0);

        Trace incomplete = result.traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.INCOMPLETE)
            .findFirst()
            .orElse(null);
        assertNotNull(incomplete, "expected an INCOMPLETE trace at K=0");
        assertFalse(incomplete.threadIds().isEmpty(), "INCOMPLETE trace must carry a real schedule");
        assertEquals(incomplete.threadIds().size(), incomplete.outcomes().size(),
            "Trace requires equal-length lists");
    }

    @Test
    void incompleteTrace_deterministic_acrossRuns() {
        Program program = named("lost-update").program();
        Trace first = incompleteTraceFor(program, 0);
        for (int i = 0; i < 5; i++) {
            assertEquals(first.threadIds(), incompleteTraceFor(program, 0).threadIds(),
                "INCOMPLETE trace must not depend on hash iteration order");
        }
    }

    private static Trace incompleteTraceFor(Program program, int k) {
        DfsResult result = new ContextBoundedExplorer().explore(program, null, null, null, k);
        return result.traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.INCOMPLETE)
            .findFirst()
            .orElse(null);
    }

    @Test
    void incompleteTrace_suppressedWhenViolationFound() {
        // Once there is a violation to reproduce, the pruned region is irrelevant.
        BenchmarkProgram program = named("lost-update");
        DfsResult result = new ContextBoundedExplorer()
            .explore(program.program(), program.invariant().orElse(null), null, null, 3);

        assertTrue(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
        assertFalse(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE),
            "a run that found a violation must not also report INCOMPLETE");
    }

    @Test
    void atMostOneIncompleteTrace_perSearch() {
        Program program = named("lost-update").program();
        DfsResult result = new ContextBoundedExplorer().explore(program, null, null, null, 0);
        long incompleteCount = result.traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.INCOMPLETE)
            .count();
        assertTrue(incompleteCount <= 1, "expected at most one INCOMPLETE trace, got " + incompleteCount);
    }

    @Test
    void budgetState_doesNotLeakBetweenSearches() {
        // A reused explorer must not carry INCOMPLETE state from a previous run.
        ContextBoundedExplorer explorer = new ContextBoundedExplorer();
        Program program = named("lost-update").program();

        DfsResult first = explorer.explore(program, null, null, null, 0);
        DfsResult second = explorer.explore(program, null, null, null, 0);

        assertEquals(first.statesExplored(), second.statesExplored(),
            "a repeated search on the same explorer must be identical");
        assertEquals(first.traces().size(), second.traces().size());
    }

    // --- Store behaviour -----------------------------------------------------------

    @Test
    void costAwareCheck_higherBudgetNotPrunedByLowerBudget() {
        // The property that makes CBS sound: a state first reached under a low budget was
        // explored with more budget remaining, so a deeper search must skip it. Asserting the
        // actual bound ordering rather than merely "some states were explored".
        Program program = named("peterson").program();
        long atK0 = new ContextBoundedExplorer().explore(program, null, null, null, 0).statesExplored();
        long atK3 = new ContextBoundedExplorer().explore(program, null, null, null, 3).statesExplored();

        assertTrue(atK3 > atK0 * 2,
            "a deeper bound must reach substantially further; got K=0:" + atK0 + " K=3:" + atK3);
    }

    @Test
    void costAwareCheck_bothStoresAgree() {
        // The same search against both store types must reach the same conclusion. Disagreement
        // means one store prunes where the other does not, which would make a verdict depend on
        // which store the caller happened to pass.
        Program program = named("peterson").program();
        for (int k : new int[] {0, 1, 2}) {
            DfsResult exact = new ContextBoundedExplorer()
                .explore(program, null, new dev.samhb.interleave.state.HashingStateStore(), null, k);
            DfsResult bitstate = new ContextBoundedExplorer()
                .explore(program, null, new BitstateStore(1_000_003, 4, k), null, k);

            assertEquals(exact.statesExplored(), bitstate.statesExplored(),
                "stores disagree at K=" + k + " (exact=" + exact.statesExplored()
                + " bitstate=" + bitstate.statesExplored() + ")");
        }
    }

    @Test
    void nullStore_usesDefaultExactStore() {
        DfsResult result = new ContextBoundedExplorer().explore(twoStepEachThread(), null, null, null, 2);
        assertTrue(result.statesExplored() > 0);
    }

    @Test
    void bitstateStoreCapacityBelowBound_throws() {
        // A silently clamped bound would return a verdict that looks exhaustive at K but is not.
        BitstateStore store = new BitstateStore(1_000_003, 4, 2);
        Program program = twoStepEachThread();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new ContextBoundedExplorer().explore(program, null, store, null, 3));
        assertTrue(e.getMessage().contains("BitstateStore"), e.getMessage());
    }

    @Test
    void bitstateStoreCapacityAtBound_succeeds() {
        BitstateStore store = new BitstateStore(1_000_003, 4, 3);
        DfsResult result = new ContextBoundedExplorer().explore(twoStepEachThread(), null, store, null, 3);
        assertTrue(result.statesExplored() > 0);
    }

    @Test
    void negativeBound_throws() {
        Program program = twoStepEachThread();
        assertThrows(IllegalArgumentException.class,
            () -> new ContextBoundedExplorer().explore(program, null, null, null, -1));
    }

    @Test
    void limitEnforcingVisitor_stillTripsUnderCbs() {
        // The 3-arg onStateVisited default delegates to the 1-arg form the runner overrides, so
        // maxStates must keep working for a bounded search without any change to that visitor.
        Program program = named("lost-update").program();
        long[] count = {0};
        StateVisitor counting = new StateVisitor() {
            @Override
            public void onStateVisited(Configuration config) {
                count[0]++;
            }
        };
        DfsResult result = new ContextBoundedExplorer().explore(program, null, null, counting, 4);
        assertTrue(count[0] > 0, "visitor should be notified during a bounded search");
        assertEquals(result.statesExplored(), count[0],
            "the 3-arg callback must fire once per state, matching statesExplored");
    }
}
