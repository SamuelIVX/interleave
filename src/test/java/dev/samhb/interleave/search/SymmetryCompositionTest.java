/* Strategy-product regressions: exact context transport, budgets, witnesses and caller wiring. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.*;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.report.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class SymmetryCompositionTest {
    /** Each strategy preserves explicit terminal oracles and replays original source evidence.
     * @param strategy built-in exact algorithm
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void terminalCorpusMatchesExhaustiveOracle(Strategy strategy) {
        check(strategy, "atomic { x = x + 1; }", "invariant final (x == 2);", Set.of(TraceOutcome.COMPLETED));
        check(strategy, "atomic { local.r = x; } atomic { x = local.r + 1; }",
            "invariant final (x == 2);", Set.of(TraceOutcome.COMPLETED, TraceOutcome.VIOLATION));
        check(strategy, "atomic { x = x + 1; }", "invariant always (x < 2);", Set.of(TraceOutcome.VIOLATION));
        check(strategy, "atomic when (false) { x = 1; }", "", Set.of(TraceOutcome.DEADLOCK));
        check(strategy, "assert(false);", "", Set.of(TraceOutcome.VIOLATION));
        check(strategy, "atomic { x = 1 % x; }", "", Set.of(TraceOutcome.VIOLATION));
        check(strategy, "while (true) limit 1 { atomic { local.r = local.r + 1; } }", "", Set.of(TraceOutcome.INCOMPLETE));
        check(strategy, "atomic { local.r = 1; } atomic { x = x + local.r; }",
            "invariant final (x == 2);", Set.of(TraceOutcome.COMPLETED));
    }

    /** Checks both a literal terminal oracle and exhaustive DFS, then validates each actual witness.
     * @param strategy algorithm under test
     * @param body identical owner source
     * @param property shared source property, or empty
     * @param expected independent expected terminal classes
     */
    private static void check(Strategy strategy, String body, String property, Set<TraceOutcome> expected) {
        var model = pair(body, property);
        var invariant = model.invariant().orElse(null);
        var ordinary = new DfsExplorer().explore(model.program(), invariant);
        var reduced = explore(model, strategy, 20);
        assertEquals(expected, outcomes(ordinary), body);
        assertEquals(expected, outcomes(reduced), strategy + ": " + body);
        for (var trace : reduced.traces()) {
            assertDoesNotThrow(() -> new TraceReplayer().replay(model.program(), trace, invariant), strategy + ": " + body);
        }
    }

    /** Four shared dependent increments give five rank layers under every strategy.
     * @param strategy selected exact algorithm
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void countsAndPublicCallersCarryAllStrategies(Strategy strategy) {
        var model = new SourceProgramLoader().load("program four { shared int x = 0; "
            + "thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } }"
            + "thread 2 { atomic { x = x + 1; } } thread 3 { atomic { x = x + 1; } } invariant final (x == 4); }");
        var traversal = explore(model,strategy,0);
        assertEquals(5,traversal.statesExplored());
        assertEquals(Set.of(TraceOutcome.COMPLETED),outcomes(traversal));
        // All strategies choose enabled owners in ascending original-ID order. These writes
        // conflict, so POR retains them and DPOR cannot sleep them. Each owner terminates in
        // one action, making every CBS switch free at K=0. The first path is 0,1,2,3; orbit
        // pruning covers every other ordering. Keep this literal independent of replay/evidence.
        assertEquals(List.of(List.of(0,1,2,3)),traversal.traces().stream().map(Trace::threadIds).toList());
        for (var trace : traversal.traces()) {
            var end = new TraceReplayer().replay(model.program(),trace,model.invariant().orElseThrow());
            assertEquals(4,((dev.samhb.interleave.format.dsl.DynamicState) end.state()).getInt("x"));
        }
        var builder = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true).invariant(model.invariant().orElseThrow());
        if (strategy == Strategy.CONTEXT_BOUNDED) builder.maxPreemptions(0);
        var runner = builder.build();
        assertEquals(5, runner.run(model.program()).statesExplored());
        assertEquals(5, runner.run(model.program()).statesExplored());
        assertTrue(runner.run(model.program()).symmetryReduction());
        var rows = new BenchmarkHarness(100,2,Set.of(StoreType.EXACT),Set.of(strategy.name()),0,false,true).runProgram(model);
        assertEquals(1, rows.size());
        assertEquals(5, rows.getFirst().statesExplored());
        assertEquals("PASS", rows.getFirst().verdict());
        assertTrue(rows.getFirst().symmetryReduction());
        var ordinary = new BenchmarkHarness(100,2,Set.of(StoreType.EXACT),Set.of(strategy.name()),0,false).runProgram(model);
        var together = new ArrayList<>(ordinary); together.addAll(rows);
        String markdown = new ReportWriter(together, List.of(model)).writeMarkdown();
        assertTrue(markdown.contains(strategy.name() + " + symmetry (exact): 5 states"), markdown);
    }

    /** Shared branch interference and independent fixed owners exercise both reduction kernels.
     * @param strategy exact algorithm being composed
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void branchAndCutoffGridPreservesReachableObservations(Strategy strategy) {
        for (int initial : List.of(0,1)) {
            for (int target : List.of(2,3,4)) {
                String body = "if (x == 0) { atomic { local.r = 1; } } else { atomic { local.r = 2; } } atomic { y = y + local.r; }";
                var model = new SourceProgramLoader().load("program grid { shared int x = " + initial
                    + "; shared int y = 0; shared int z = 0; local int r = 0; thread 0 { " + body
                    + " } thread 1 { " + body + " } thread 2 { atomic { x = 1; } atomic { z = 1; } }"
                    + " invariant final (y == " + target + "); }");
                var ordinary = new DfsExplorer().explore(model.program(), model.invariant().orElseThrow());
                var reduced = explore(model,strategy,20);
                var expected = initial == 0 ? Set.of(TraceOutcome.COMPLETED,TraceOutcome.VIOLATION)
                    : target == 4 ? Set.of(TraceOutcome.COMPLETED) : Set.of(TraceOutcome.VIOLATION);
                assertEquals(expected,outcomes(ordinary));
                assertEquals(expected,outcomes(reduced));
                assertEquals(outcomes(ordinary),outcomes(reduced), strategy + ": initial=" + initial + ", target=" + target);
                for (var trace : reduced.traces()) {
                    assertDoesNotThrow(() -> new TraceReplayer().replay(model.program(),trace,model.invariant().orElseThrow()));
                }
            }
            String body = "while (x == 0) limit 1 { atomic { local.r = local.r + 1; } }";
            var model = new SourceProgramLoader().load("program cutoff { shared int x = " + initial
                + "; local int r = 0; thread 0 { " + body + " } thread 1 { " + body + " } thread 2 { atomic { x = 1; } } }");
            var expected = initial == 0 ? Set.of(TraceOutcome.COMPLETED,TraceOutcome.INCOMPLETE) : Set.of(TraceOutcome.COMPLETED);
            var result = explore(model,strategy,20);
            assertEquals(expected,outcomes(result));
            for (var trace : result.traces()) assertDoesNotThrow(() -> new TraceReplayer().replay(model.program(),trace));
        }
    }

    /** Public store reuse must clear prior unbounded membership as well as bounded/sleep maps.
     * @param backend certified exact collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void unboundedMembershipClearsBeforeReusingTheSameStore(SymmetryBackend backend) {
        var model = pair("atomic {}", "");
        var program = model.program();
        var store = SymmetryStateStore.forRun(program,null, Strategy.DFS, backend);
        var initial = program.initialConfiguration();
        var left = TransitionExecutor.execute(program,initial,0).successor();
        var right = TransitionExecutor.execute(program,initial,1).successor();
        assertFalse(store.isVisited(left));
        store.markVisited(left);
        assertTrue(store.isVisited(right));
        assertFalse(store.isVisited(initial));
        assertFalse(store.freshCopy().isVisited(left));
        store.clear();
        assertFalse(store.isVisited(right));
        assertEquals(3,dispatch(program,null,Strategy.DFS,store,0).statesExplored());
        assertEquals(3,dispatch(program,null,Strategy.DFS,store,0).statesExplored());
    }

    /** Last-owner context and minimum cost transport together; clearing/copying isolate every map.
     * @param backend certified exact collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void boundedMembershipPreservesMappedOwnerAndMinimumCost(SymmetryBackend backend) {
        var model = pair("atomic {} atomic {}", "");
        var store = SymmetryStateStore.forRun(model.program(), null, Strategy.CONTEXT_BOUNDED, backend);
        var initial = model.program().initialConfiguration();
        var left = TransitionExecutor.execute(model.program(), initial, 0).successor();
        var right = TransitionExecutor.execute(model.program(), initial, 1).successor();
        store.markVisited(left, 0, 2);
        assertTrue(store.isVisited(right, 1, 2));
        assertTrue(store.isVisited(right, 1, 3));
        assertFalse(store.isVisited(right, 1, 1));
        assertFalse(store.isVisited(right, 0, 2));
        assertFalse(store.isVisited(right, -1, 2));
        store.markVisited(right, 1, 1);
        store.markVisited(left, 0, 3);
        assertTrue(store.isVisited(left, 0, 1));
        assertFalse(store.isVisited(left, 0, 0));
        assertFalse(store.freshCopy().isVisited(left, 0, 3));
        store.clear();
        assertFalse(store.isVisited(left, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> store.isVisited(left,0,-1));
        assertThrows(IllegalArgumentException.class, () -> store.markVisited(left,0,-1));
        assertThrows(IllegalArgumentException.class, () -> store.isVisited(left,2,0));
    }

    /** Exact DPOR contexts map owner and action PC and never collapse distinct or subset sleepers.
     * @param backend certified exact collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void sleepingMembershipTransportsWholeContextAndCopiesEntries(SymmetryBackend backend) {
        var model = pair("atomic {} atomic {}", "");
        var program = model.program();
        var store = SymmetryStateStore.forRun(program, null, Strategy.DPOR, backend);
        var initial = program.initialConfiguration();
        var left = TransitionExecutor.execute(program, initial, 0).successor();
        var right = TransitionExecutor.execute(program, initial, 1).successor();
        var entries = new HashSet<>(Set.of(Map.entry(0, program.threads().get(0).steps().get(1))));
        assertFalse(store.sleepCovered(left, entries));
        entries.clear();
        assertTrue(store.sleepCovered(right, Set.of(Map.entry(1,program.threads().get(1).steps().get(1)))));
        assertFalse(store.sleepCovered(right, Set.of(Map.entry(1,program.threads().get(1).steps().get(0)))));
        assertFalse(store.sleepCovered(right, Set.of(Map.entry(0,program.threads().get(0).steps().get(1)))));
        assertFalse(store.sleepCovered(right, Set.of()));
        assertTrue(store.sleepCovered(right, Set.of()));
        assertFalse(((SymmetryStateStore) store.freshCopy()).sleepCovered(right, Set.of()));
        store.clear();
        assertFalse(store.sleepCovered(right, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> store.sleepCovered(right, Set.of(Map.entry(-1,program.threads().get(0).steps().get(0)))));
        assertThrows(IllegalArgumentException.class, () -> store.sleepCovered(right, Set.of(Map.entry(2,program.threads().get(0).steps().get(0)))));
        assertThrows(IllegalArgumentException.class, () -> store.sleepCovered(right, Set.of(Map.entry(0,program.threads().get(1).steps().get(0)))));
        assertThrows(IllegalArgumentException.class, () -> SymmetryStateStore.forRun(program,null, Strategy.DFS, backend).sleepCovered(right, Set.of()));
    }

    /** Equal-frame ties retain exact stale-action contexts rather than applying subset dominance.
     * @param backend certified exact collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void equalFrameSleepContextsKeepOwnersSubsetsAndActionPcsDistinct(SymmetryBackend backend) {
        var model = pair("atomic {} atomic {}", "");
        var program = model.program();
        var store = SymmetryStateStore.forRun(program,null, Strategy.DPOR, backend);
        var first = TransitionExecutor.execute(program,program.initialConfiguration(),0).successor();
        var tied = TransitionExecutor.execute(program,first,1).successor();
        assertEquals(List.of(1,1),tied.programCounters());
        var stale0 = Map.entry(0,program.threads().get(0).steps().get(0));
        var stale1 = Map.entry(1,program.threads().get(1).steps().get(0));
        assertFalse(store.sleepCovered(tied,Set.of()));
        assertFalse(store.sleepCovered(tied,Set.of(stale0)));
        assertFalse(store.sleepCovered(tied,Set.of(stale1)));
        assertFalse(store.sleepCovered(tied,Set.of(stale0,stale1)));
        assertTrue(store.sleepCovered(tied,Set.of(stale0)));
        assertTrue(store.sleepCovered(tied,Set.of(stale1)));
        assertTrue(store.sleepCovered(tied,Set.of(stale0,stale1)));
        assertFalse(store.sleepCovered(tied,Set.of(Map.entry(0,program.threads().get(0).steps().get(1)))));
        assertTrue(store.sleepCovered(tied,Set.of()));
    }

    /** A partial tie can lose one permutation's match while preserving another exact context witness.
     * @param backend certified exact collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void partialFrameTiesUseOneConsistentMappedSleepWitness(SymmetryBackend backend) {
        String body = "atomic { local.r = 1; } atomic {}";
        var model = new SourceProgramLoader().load("program tie { shared int x = 0; local int r = 0;"
            + " thread 0 { " + body + " } thread 1 { " + body + " } thread 2 { " + body + " } }");
        var program = model.program();
        var store = SymmetryStateStore.forRun(program,null, Strategy.DPOR, backend);
        var left = TransitionExecutor.execute(program,program.initialConfiguration(),0).successor();
        var right = TransitionExecutor.execute(program,program.initialConfiguration(),2).successor();
        var canonicalizer = new dev.samhb.interleave.state.ThreadSymmetryCanonicalizer(3,List.of(List.of(0,1,2)));
        assertEquals(List.of(1,2,0),canonicalizer.canonicalize(
            (dev.samhb.interleave.format.dsl.DynamicState) left.state(),left.programCounters()).canonicalToOriginal());
        assertEquals(List.of(0,1,2),canonicalizer.canonicalize(
            (dev.samhb.interleave.format.dsl.DynamicState) right.state(),right.programCounters()).canonicalToOriginal());
        assertFalse(store.sleepCovered(left,Set.of(Map.entry(0,program.threads().get(0).steps().get(0)))));
        assertTrue(store.sleepCovered(right,Set.of(Map.entry(2,program.threads().get(2).steps().get(0)))));
        assertFalse(store.sleepCovered(left,Set.of(Map.entry(1,program.threads().get(1).steps().get(0)))));
        // Swapping owners 0 and 2 keeps physical owner 1 fixed, but its tie rank changes.
        assertFalse(store.sleepCovered(right,Set.of(Map.entry(1,program.threads().get(1).steps().get(0)))));
        // Another valid permutation maps the old owner 1 to new owner 0 and preserves this context.
        assertTrue(store.sleepCovered(right,Set.of(Map.entry(0,program.threads().get(0).steps().get(0)))));
    }

    /** Direct explorer entry points reject a capability bound to another run before traversal.
     * @param strategy requested algorithm
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void rejectsForeignRunBindings(Strategy strategy) {
        var model = pair("atomic {}", "");
        var other = pair("atomic {}", "");
        var store = SymmetryStateStore.forRun(model.program(),null,strategy);
        assertThrows(IllegalArgumentException.class, () -> dispatch(other.program(),null,strategy,store,0));
        assertThrows(IllegalArgumentException.class, () -> dispatch(model.program(),(s,c)->true,strategy,store,0));
        Strategy foreign = strategy == Strategy.DFS ? Strategy.DPOR : Strategy.DFS;
        assertThrows(IllegalArgumentException.class, () -> dispatch(model.program(),null,foreign,store,0));
        assertThrows(NullPointerException.class, () -> SymmetryStateStore.forRun(model.program(),null,null));
    }

    /** Custom stores cannot silently produce quotient counts without the runner's result marker.
     * @param strategy selected built-in algorithm
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void rejectsCustomSymmetryStoresInsteadOfMislabelingResults(Strategy strategy) {
        var model = pair("atomic {}", "");
        var store = SymmetryStateStore.forRun(model.program(),null,strategy);
        var concrete = InterleaveRunner.builder().strategy(strategy).stateStore(store).build();
        assertThrows(IllegalArgumentException.class, () -> concrete.run(model.program()));
        var factory = InterleaveRunner.builder().strategy(strategy)
            .stateStoreFactory(() -> SymmetryStateStore.forRun(model.program(),null,strategy)).build();
        assertThrows(IllegalArgumentException.class, () -> factory.run(model.program()));
        if (strategy == Strategy.CONTEXT_BOUNDED) {
            var iterative = InterleaveRunner.builder().strategy(strategy).iterativeDeepening(true).stateStore(store).build();
            assertThrows(IllegalArgumentException.class, () -> iterative.run(model.program()));
        }
    }

    /** Iterative CBS reopens fresh orbit/cost maps and retains the ordinary minimal failing bound. */
    @Test void boundedAndIterativeRunsPreserveFailureAndIncompleteEvidence() {
        var model = pair("atomic { local.r = x; } atomic { x = local.r + 1; }", "invariant final (x == 2);");
        var ordinary = new BenchmarkHarness(100,2,Set.of(StoreType.EXACT),Set.of("CONTEXT_BOUNDED"),2,true).runProgram(model).getFirst();
        var reduced = new BenchmarkHarness(100,2,Set.of(StoreType.EXACT),Set.of("CONTEXT_BOUNDED"),2,true,true).runProgram(model).getFirst();
        assertEquals("VIOLATION", reduced.verdict());
        assertEquals(ordinary.preemptionsUsed(), reduced.preemptionsUsed());
        assertEquals(1, reduced.preemptionsUsed());
        for (int bound : List.of(0,1,2)) {
            var plain = dispatch(model.program(),model.invariant().orElseThrow(),Strategy.CONTEXT_BOUNDED,null,bound);
            assertEquals(outcomes(plain), outcomes(explore(model,Strategy.CONTEXT_BOUNDED,bound)));
        }
        var runner = InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).symmetryReduction(true)
            .iterativeDeepening(true).maxPreemptions(2).invariant(model.invariant().orElseThrow()).build();
        assertTrue(runner.run(model.program()).hasViolation());
        assertTrue(runner.run(model.program()).symmetryReduction());
        var all = new BenchmarkHarness(100,2,Set.of(StoreType.EXACT),null,2,false,true).runProgram(model);
        assertEquals(Set.of("DFS","STATIC_POR","DPOR","CONTEXT_BOUNDED"),
            all.stream().map(BenchmarkResult::strategy).collect(Collectors.toSet()));
    }

    /** Matching repeated objects retain original execution and runner metadata under symmetry DPOR.
     * @param backend certified exact collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void acceptsMatchingAliasedActionsForDpor(SymmetryBackend backend) {
        var model = pair("atomic {} atomic {}", "");
        var threads = new ArrayList<ModelThread>();
        for (var thread : model.program().threads()) {
            Step jump = thread.steps().get(1);
            threads.add(new ModelThread(thread.id(), List.of(jump,jump)));
        }
        var program = new Program(model.program().initialConfiguration().state(), threads, model.program().sourceDigest());
        var result = InterleaveRunner.builder().strategy(Strategy.DPOR).symmetryReduction(true).symmetryBackend(backend).build().run(program);
        assertTrue(result.symmetryReduction());
        assertFalse(result.hasViolation());
        assertEquals(3, result.statesExplored());
        assertEquals(List.of(0,1), result.completedTraces().getFirst().threadIds());
        var traversal = dispatch(program,null,Strategy.DPOR,SymmetryStateStore.forRun(program,null, Strategy.DPOR, backend),0);
        assertEquals(Set.of(TraceOutcome.COMPLETED),outcomes(traversal));
        for (var trace : traversal.traces()) assertDoesNotThrow(() -> new TraceReplayer().replay(program,trace));
    }

    /** Equal execution shapes alone cannot certify DPOR identity transport, including later aliases.
     * @param backend certified exact collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void rejectsDifferentAliasPartitionsOnlyForDpor(SymmetryBackend backend) {
        String branch = "if (local.r == 0) { atomic {} } else { atomic {} } ";
        var model = pair(branch + branch + "atomic {}", "");
        var firstMismatch = aliasBranches(model,Set.of(0),1);
        var laterThreads = new ArrayList<ModelThread>(aliasBranches(model,Set.of(0,1),1).threads());
        laterThreads.set(0,aliasBranches(model,Set.of(0),1,4).threads().get(0));
        var laterMismatch = new Program(model.program().initialConfiguration().state(),laterThreads,model.program().sourceDigest());
        for (var program : List.of(firstMismatch,laterMismatch)) {
            assertEquals(List.of(List.of(0,1)),dev.samhb.interleave.format.dsl.SourceSymmetry.groups(program,null));
            for (var strategy : List.of(Strategy.DFS,Strategy.STATIC_POR,Strategy.CONTEXT_BOUNDED)) {
                assertDoesNotThrow(() -> SymmetryStateStore.forRun(program,null, strategy, backend));
            }
            assertThrows(IllegalArgumentException.class,() -> SymmetryStateStore.forRun(program,null, Strategy.DPOR, backend));
        }
    }

    /** Aliases map as one identity, distinct objects/owners stay distinct, and run state stays isolated.
     * @param backend certified exact collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void aliasedSleepContextsPreserveIdentityAndOwnerMapping(SymmetryBackend backend) {
        var model = pair("if (local.r == 0) { atomic {} } else { atomic {} } atomic {}", "");
        var program = aliasBranches(model,Set.of(0,1),1);
        var store = SymmetryStateStore.forRun(program,null, Strategy.DPOR, backend);
        var initial = program.initialConfiguration();
        var left = TransitionExecutor.execute(program,initial,0).successor();
        var right = TransitionExecutor.execute(program,initial,1).successor();
        assertEquals(List.of(1,0),left.programCounters());
        assertEquals(List.of(0,1),right.programCounters());
        assertFalse(store.sleepCovered(left,Set.of(Map.entry(0,program.threads().get(0).steps().get(1)))));
        assertTrue(store.sleepCovered(right,Set.of(Map.entry(1,program.threads().get(1).steps().get(2)))));
        assertFalse(store.sleepCovered(right,Set.of(Map.entry(1,program.threads().get(1).steps().get(3)))));
        assertFalse(store.sleepCovered(right,Set.of(Map.entry(0,program.threads().get(0).steps().get(1)))));
        assertFalse(store.sleepCovered(right,Set.of()));
        assertTrue(store.sleepCovered(right,Set.of()));
        assertThrows(IllegalArgumentException.class,() -> store.sleepCovered(right,
            Set.of(Map.entry(1,model.program().threads().get(1).steps().get(1)))));
        assertFalse(((SymmetryStateStore)store.freshCopy()).sleepCovered(right,Set.of()));
        store.clear();
        assertFalse(store.sleepCovered(right,Set.of()));
    }

    /** Fixed owners need no identity correspondence with a distinct interchangeable group. */
    @Test void fixedOwnerMayHaveItsOwnAliasPartition() {
        String branch = "if (local.r == 0) { atomic {} } else { atomic {} } atomic {}";
        var model = new SourceProgramLoader().load("program fixed { shared int x = 0; local int r = 0; "
            + "thread 0 { atomic {} atomic {} } thread 1 { " + branch + " } thread 2 { " + branch + " } }");
        var threads = new ArrayList<>(aliasBranches(model,Set.of(1,2),1).threads());
        Step jump = threads.get(0).steps().get(1);
        threads.set(0,new ModelThread(0,List.of(jump,jump)));
        var program = new Program(model.program().initialConfiguration().state(),threads,model.program().sourceDigest());
        assertEquals(List.of(List.of(1,2)),dev.samhb.interleave.format.dsl.SourceSymmetry.groups(program,null));
        var result = dispatch(program,null,Strategy.DPOR,SymmetryStateStore.forRun(program,null,Strategy.DPOR),0);
        assertEquals(Set.of(TraceOutcome.COMPLETED),outcomes(result));
        for (var trace : result.traces()) assertDoesNotThrow(() -> new TraceReplayer().replay(program,trace));
    }

    /** Both reachable branch occurrences preserve explicit terminal outcomes and original replay.
     * @param strategy built-in exact search strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void aliasedBranchesPreserveTerminalEvidence(Strategy strategy) {
        for (int initial : List.of(0,1)) {
            for (String tail : List.of("atomic { x = x + 1; }", "assert(false);",
                    "atomic { x = 1 % (local.r - local.r); }", "while (true) limit 1 { atomic {} }",
                    "atomic when (false) {}")) {
                String branch = "if (local.r == 0) { atomic { x = x + 1; } } else { atomic { x = x + 1; } } ";
                var model = new SourceProgramLoader().load("program aliases { shared int x = 0; local int r = " + initial
                    + "; thread 0 { " + branch + tail + " } thread 1 { " + branch + tail + " } }");
                var program = aliasBranches(model,Set.of(0,1),1);
                var expected = tail.startsWith("while") ? Set.of(TraceOutcome.INCOMPLETE)
                    : tail.contains("when") ? Set.of(TraceOutcome.DEADLOCK)
                    : tail.startsWith("assert") || tail.contains("1 %") ? Set.of(TraceOutcome.VIOLATION)
                    : Set.of(TraceOutcome.COMPLETED);
                assertEquals(expected,outcomes(new DfsExplorer().explore(program)));
                var result = dispatch(program,null,strategy,SymmetryStateStore.forRun(program,null,strategy),20);
                assertEquals(expected,outcomes(result),strategy + ": " + initial + ": " + tail);
                int checkedOwners = 0;
                for (var trace : result.traces()) {
                    assertDoesNotThrow(() -> new TraceReplayer().replay(program,trace));
                    if (tail.startsWith("assert")) {
                        assertEquals(FailureEvidence.Kind.ASSERTION,trace.failure().kind());
                        assertEquals(FailureEvidence.Phase.STEP,trace.failure().phase());
                    } else if (tail.contains("1 %")) {
                        assertEquals(FailureEvidence.Kind.RUNTIME_ERROR,trace.failure().kind());
                        assertEquals(FailureEvidence.Category.MODULO_BY_ZERO,trace.failure().category());
                    } else if (tail.startsWith("while")) {
                        assertEquals(CutoffEvidence.Reason.LOOP_LIMIT,trace.cutoff().reason());
                        assertEquals(1,trace.cutoff().limit());
                        assertEquals(1,trace.cutoff().iterations());
                    }
                    for (int owner : List.of(0,1)) {
                        var pcs = new ArrayList<Integer>();
                        for (int edge = 0; edge < trace.threadIds().size(); edge++) {
                            if (trace.threadIds().get(edge) == owner) pcs.add(trace.evidence().executedSteps().get(edge).instructionIndex());
                        }
                        if (pcs.size() >= 2) {
                            assertEquals(List.of(0,initial == 0 ? 1 : 2),pcs.subList(0,2));
                            checkedOwners++;
                        }
                    }
                }
                assertTrue(checkedOwners > 0,"Expected a trace to execute the aliased branch action");
                if (expected.contains(TraceOutcome.COMPLETED)) {
                    assertEquals(Set.of(4),result.states().values().stream().filter(Configuration::allTerminated)
                        .map(c -> ((dev.samhb.interleave.format.dsl.DynamicState)c.state()).getInt("x")).collect(Collectors.toSet()));
                }
            }
        }
    }

    /** Final shared properties still fail on the actual terminal values with original evidence. */
    @Test void aliasedActionsPreserveFinalPropertyViolation() {
        var model = pair("if (local.r == 0) { atomic { x = x + 1; } } else { atomic { x = x + 1; } } "
            + "atomic { x = x + 1; }", "invariant final (x == 5);");
        var program = aliasBranches(model,Set.of(0,1),1);
        var invariant = model.invariant().orElseThrow();
        assertEquals(Set.of(TraceOutcome.VIOLATION),outcomes(new DfsExplorer().explore(program,invariant)));
        var result = dispatch(program,invariant,Strategy.DPOR,SymmetryStateStore.forRun(program,invariant,Strategy.DPOR),0);
        assertEquals(Set.of(TraceOutcome.VIOLATION),outcomes(result));
        assertEquals(Set.of(4),result.states().values().stream().filter(Configuration::allTerminated)
            .map(c -> ((dev.samhb.interleave.format.dsl.DynamicState)c.state()).getInt("x")).collect(Collectors.toSet()));
        for (var trace : result.traces()) {
            assertEquals(FailureEvidence.Kind.INVARIANT,trace.failure().kind());
            assertEquals(FailureEvidence.Phase.PROPERTY,trace.failure().phase());
            assertDoesNotThrow(() -> new TraceReplayer().replay(program,trace,invariant));
        }
    }

    /** Reuses the second compiled branch action at each supplied pair while retaining bound owners.
     * @param model trusted compiled instructions with two one-action branch arms
     * @param owners owners whose listed action pairs will share the second object
     * @param starts first arm's PC for each pair, followed immediately by the second arm
     * @return immutable recomposed program retaining the actual source instructions
     * @throws IndexOutOfBoundsException if an action pair lies outside the compiled owner list
     */
    private static Program aliasBranches(BenchmarkProgram model, Set<Integer> owners, int... starts) {
        var threads = new ArrayList<ModelThread>();
        for (var thread : model.program().threads()) {
            var steps = new ArrayList<>(thread.steps());
            if (owners.contains(thread.id())) {
                for (int first : starts) steps.set(first,steps.get(first + 1));
            }
            threads.add(new ModelThread(thread.id(),steps));
        }
        return new Program(model.program().initialConfiguration().state(),threads,model.program().sourceDigest());
    }

    /** Compiles a two-owner model with identical bodies and optional shared property.
     * @param body identical owner instructions
     * @param property optional shared predicate
     * @return compiled immutable two-owner model
     */
    private static BenchmarkProgram pair(String body, String property) {
        return new SourceProgramLoader().load("program pair { shared int x = 0; local int r = 0; thread 0 { "
            + body + " } thread 1 { " + body + " } " + property + " }");
    }

    /** Collects terminal classifications without relying on trace order or multiplicity.
     * @param result completed traversal
     * @return set of emitted terminal classifications
     */
    private static Set<TraceOutcome> outcomes(DfsResult result) {
        return result.traces().stream().map(Trace::outcome).collect(Collectors.toSet());
    }

    /** Creates fresh certified membership and runs the requested exact strategy.
     * @param model compiled source and optional property
     * @param strategy exact algorithm
     * @param bound CBS preemption budget
     * @return checked quotient traversal
     */
    private static DfsResult explore(BenchmarkProgram model, Strategy strategy, int bound) {
        var invariant = model.invariant().orElse(null);
        return dispatch(model.program(),invariant,strategy,SymmetryStateStore.forRun(model.program(),invariant,strategy),bound);
    }

    /** Calls the public explorer entry for a supplied ordinary or symmetry store.
     * @param program immutable model
     * @param invariant property, or null
     * @param strategy exact algorithm
     * @param store supplied membership, or null for ordinary exact traversal
     * @param bound CBS preemption budget
     * @return completed traversal including original source evidence
     */
    private static DfsResult dispatch(Program program, Invariant invariant, Strategy strategy, StateStore store, int bound) {
        return switch(strategy) {
            case DFS -> new DfsExplorer().explore(program,invariant,store,null);
            case STATIC_POR -> new StaticPorExplorer().explore(program,invariant,store,null);
            case DPOR -> new DporExplorer().explore(program,invariant,store,null);
            case CONTEXT_BOUNDED -> new ContextBoundedExplorer().explore(program,invariant,store,null,bound);
        };
    }
}
