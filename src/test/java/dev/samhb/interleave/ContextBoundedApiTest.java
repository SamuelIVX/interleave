package dev.samhb.interleave;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.StateStore;
import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.state.HashingStateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ContextBoundedApiTest {

    private static BenchmarkProgram named(String name) {
        for (BenchmarkProgram p : BugCorpus.all()) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        throw new IllegalArgumentException("no such program: " + name);
    }

    // --- Interleave.verify ---------------------------------------------------------

    @Test
    void verify_contextBounded_defaultOverload_usesK2() {
        Program program = named("lost-update").program();
        VerificationResult viaVerify = Interleave.verify(program, Strategy.CONTEXT_BOUNDED);
        VerificationResult viaExplicit = Interleave.verify(program, Strategy.CONTEXT_BOUNDED, null, 2);

        assertEquals(viaExplicit.statesExplored(), viaVerify.statesExplored(),
            "the no-bound overload should dispatch at the default K=2");
    }

    @Test
    void verify_contextBounded_explicitBound_matchesExplorer() {
        Program program = named("lost-update").program();
        VerificationResult result = Interleave.verify(program, Strategy.CONTEXT_BOUNDED, null, 3);
        DfsResult direct = new ContextBoundedExplorer().explore(program, null, null, null, 3);

        assertEquals(direct.statesExplored(), result.statesExplored());
    }

    @Test
    void verify_contextBounded_customStore_isUsed() {
        // A BitstateStore with capacity 3 only works if it is actually passed through; dropping it
        // would substitute a default exact store, or trip the capacity assertion.
        Program program = named("lost-update").program();
        VerificationResult result = Interleave.verify(program, Strategy.CONTEXT_BOUNDED, null,
            () -> new BitstateStore(1_000_003, 4, 3), 3);
        assertTrue(result.statesExplored() > 0);
    }

    @Test
    void verify_contextBounded_storeCapacityTooLow_throws() {
        Program program = named("lost-update").program();
        assertThrows(IllegalArgumentException.class,
            () -> Interleave.verify(program, Strategy.CONTEXT_BOUNDED, null,
                () -> new BitstateStore(1_000_003, 4, 2), 5));
    }

    @Test
    void verify_contextBounded_storeFactory_invokedExactlyOnce() {
        // Resolved before dispatch, so a factory with setup side effects sees the same call count
        // regardless of which strategy arm runs.
        Program program = named("lost-update").program();
        AtomicInteger calls = new AtomicInteger();

        Interleave.verify(program, Strategy.CONTEXT_BOUNDED, null, () -> {
            calls.incrementAndGet();
            return new HashingStateStore();
        });

        assertEquals(1, calls.get(), "store factory should be resolved exactly once");
    }

    @Test
    void verify_contextBounded_nullStoreArgument_isUnambiguous() {
        // int and Supplier<StateStore> are disjoint, so this must resolve to the Supplier overload
        // and fall back to a default store rather than throwing or NPE-ing.
        Program program = named("lost-update").program();
        VerificationResult result = Interleave.verify(program, Strategy.CONTEXT_BOUNDED, null, null);
        assertTrue(result.statesExplored() > 0);
    }

    @Test
    void verify_contextBounded_negativeBound_throws() {
        Program program = named("lost-update").program();
        assertThrows(IllegalArgumentException.class,
            () -> Interleave.verify(program, Strategy.CONTEXT_BOUNDED, null, -1));
        assertThrows(IllegalArgumentException.class,
            () -> Interleave.verify(program, Strategy.CONTEXT_BOUNDED, null, HashingStateStore::new, -1));
    }

    @Test
    void verify_otherStrategies_unaffectedByBoundOverload() {
        BenchmarkProgram program = named("lost-update");
        Invariant invariant = program.invariant().orElseThrow();
        assertTrue(Interleave.verify(program.program(), Strategy.DFS, invariant).hasViolation());
        assertTrue(Interleave.verify(program.program(), Strategy.STATIC_POR, invariant).hasViolation());
        assertTrue(Interleave.verify(program.program(), Strategy.DPOR, invariant).hasViolation());
    }

    // --- Builder -------------------------------------------------------------------

    @Test
    void builder_contextBounded_withBound_runs() {
        BenchmarkProgram program = named("lost-update");
        TestResult result = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED)
            .invariant(program.invariant().orElse(null))
            .maxPreemptions(3)
            .build()
            .run(program.program());

        assertTrue(result.hasViolation(), "lost-update should be caught at K=3");
    }

    @Test
    void builder_contextBounded_iterativeDeepening_findsViolation() {
        BenchmarkProgram program = named("lost-update");
        TestResult result = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED)
            .invariant(program.invariant().orElse(null))
            .maxPreemptions(3)
            .iterativeDeepening(true)
            .build()
            .run(program.program());

        assertTrue(result.hasViolation());
        assertFalse(result.limitExceeded());
    }

    @Test
    void builder_defaultSettings_ignoreContextBoundedOptions() {
        // Every existing caller must keep working: never naming these options on a non-CBS
        // strategy is not an error.
        Program program = named("peterson").program();
        TestResult result = InterleaveRunner.builder().build().run(program);
        assertFalse(result.hasViolation());
    }

    @Test
    void builder_rejectsExplicitMaxPreemptionsOnNonContextBoundedStrategy() {
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder()
            .strategy(Strategy.DFS).maxPreemptions(3).build());
    }

    @Test
    void builder_rejectsExplicitIterativeDeepeningOnNonContextBoundedStrategy() {
        // Even iterativeDeepening(false): naming a context-bounded option on another strategy is
        // almost certainly a refactor slip, and silently ignoring it is worse than failing.
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder()
            .strategy(Strategy.DFS).iterativeDeepening(false).build());
    }

    @Test
    void builder_negativeMaxPreemptions_throws() {
        assertThrows(IllegalArgumentException.class, () -> InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(-1));
    }

    // --- Iterative deepening store isolation ---------------------------------------

    @Test
    void iterativeDeepening_sharedStoreFactory_throws() {
        // A shared visited set makes deepening narrow instead of widen: states recorded at bound 0
        // satisfy min <= p and get pruned at bound 1. That would report a K=3 result covering less
        // than K=0, so it must be rejected rather than tolerated.
        BenchmarkProgram program = named("lost-update");
        StateStore shared = new HashingStateStore();

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> InterleaveRunner.builder()
                .strategy(Strategy.CONTEXT_BOUNDED)
                .invariant(program.invariant().orElse(null))
                .stateStoreFactory(() -> shared)
                .maxPreemptions(3)
                .iterativeDeepening(true)
                .build()
                .run(program.program()));

        assertTrue(e.getMessage().contains("fresh StateStore"), e.getMessage());
    }

    @Test
    void iterativeDeepening_alternatingStoreFactory_throws() {
        // Membership is tested over every store used, not just the last one, so A,B,A,B is caught
        // too -- otherwise bound 2 would inherit bound 0's visited set. Uses a program with no
        // violation so deepening actually runs every bound; a failing program would stop early
        // and never reach the repeat.
        Program program = named("peterson").program();
        StateStore a = new HashingStateStore();
        StateStore b = new HashingStateStore();
        AtomicInteger index = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED)
            .stateStoreFactory(() -> (index.getAndIncrement() % 2 == 0) ? a : b)
            .maxPreemptions(3)
            .iterativeDeepening(true)
            .build()
            .run(program));
    }

    @Test
    void iterativeDeepening_freshStorePerBound_exploresWiderAtHigherBound() {
        // The positive counterpart proving the check guards a real property.
        BenchmarkProgram program = named("peterson");

        long atK0 = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(0).build()
            .run(program.program()).statesExplored();
        long atK2 = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(2).build()
            .run(program.program()).statesExplored();

        assertTrue(atK2 > atK0, "K=2 (" + atK2 + ") should explore more than K=0 (" + atK0 + ")");
    }

    @Test
    void iterativeDeepening_stopsAtMinimalFailingBound() {
        BenchmarkProgram program = named("lost-update");
        AtomicInteger storeCalls = new AtomicInteger();

        TestResult result = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED)
            .invariant(program.invariant().orElse(null))
            .stateStoreFactory(() -> {
                storeCalls.incrementAndGet();
                return new HashingStateStore();
            })
            .maxPreemptions(3)
            .iterativeDeepening(true)
            .build()
            .run(program.program());

        assertTrue(result.hasViolation());
        // If it had to run all four bounds, the factory would have been called four times.
        assertTrue(storeCalls.get() < 4,
            "should stop before the final bound, but factory was called " + storeCalls.get() + " times");
    }

    @Test
    void iterativeDeepening_doesNotDeepenPastMaxStates() {
        // maxStates is a total budget across bounds, because every iteration shares one visitor
        // whose state counter is never reset.
        BenchmarkProgram program = named("lost-update");

        TestResult result = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED)
            .invariant(program.invariant().orElse(null))
            .maxPreemptions(3)
            .iterativeDeepening(true)
            .maxStates(5)
            .build()
            .run(program.program());

        assertTrue(result.limitExceeded(), "a tripped maxStates must surface as limitExceeded");
    }

    @Test
    void limitAndIncomplete_areIndependentFlags() {
        // INCOMPLETE means the preemption bound stopped the search; limitExceeded means a resource
        // limit did. A single run can carry both and neither must be inferred from the other.
        BenchmarkProgram program = named("peterson");

        TestResult incompleteOnly = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(0).build()
            .run(program.program());
        assertTrue(incompleteOnly.hasIncomplete());
        assertFalse(incompleteOnly.limitExceeded());

        TestResult limitedOnly = InterleaveRunner.builder()
            .strategy(Strategy.DFS).maxStates(1).build()
            .run(program.program());
        assertTrue(limitedOnly.limitExceeded());
    }

    @Test
    void partialResult_limitedRun_reportsLimitAndNoIncomplete() {
        // Characterises the current emit ordering, which is worth pinning because it is subtle.
        // ContextBoundedExplorer emits its single INCOMPLETE trace only after dfs() returns, but
        // a tripped limit throws out of dfs(). So a limited bounded run reports limitExceeded and
        // nothing else: the run was inconclusive, but for the resource limit rather than the
        // preemption bound, and the two are not merged.
        //
        // This is also why the dropped-incomplete-bucket fix is not observable through the public
        // API today -- the bucket is always empty on the path where the rebuild runs. The fix is
        // still correct and still needed: it removes a trap for anyone who later moves the emit
        // earlier, and it was the only thing separating the bucket from being silently lost.
        BenchmarkProgram program = named("peterson");
        TestResult result = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED)
            .maxPreemptions(0)
            .maxStates(3)
            .build()
            .run(program.program());

        assertTrue(result.limitExceeded(), "expected the state limit to trip");
        assertFalse(result.hasIncomplete(),
            "the search aborted before it could emit an INCOMPLETE trace");
        assertFalse(result.hasViolation());
    }

    @Test
    void partialResult_unlimitedRun_doesReportIncomplete() {
        // The positive counterpart: with the limit out of the way, the same search does emit one.
        BenchmarkProgram program = named("peterson");
        TestResult result = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED)
            .maxPreemptions(0)
            .build()
            .run(program.program());

        assertTrue(result.hasIncomplete());
        assertFalse(result.limitExceeded());
    }

    @Test
    void partialResult_iterativeDeepening_doesNotDuplicateTraces() {
        // The visitor is shared across bounds, and each bound re-discovers the same completed
        // schedules over its fresh store. Without clearing per bound, the partial result would
        // carry each one twice.
        BenchmarkProgram program = named("peterson");
        TestResult result = InterleaveRunner.builder()
            .strategy(Strategy.CONTEXT_BOUNDED)
            .maxPreemptions(3)
            .iterativeDeepening(true)
            .maxStates(4)
            .build()
            .run(program.program());

        assertTrue(result.limitExceeded());
        List<String> signatures = new ArrayList<>();
        for (TraceRecord record : result.completedTraces()) {
            signatures.add(record.threadIds() + "|" + record.outcomes());
        }
        assertEquals(signatures.size(), signatures.stream().distinct().count(),
            "completed schedules were duplicated across deepening bounds: " + signatures);
    }
}
