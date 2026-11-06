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

    /** Public store reuse must clear prior unbounded membership as well as bounded/sleep maps. */
    @Test void unboundedMembershipClearsBeforeReusingTheSameStore() {
        var model = pair("atomic {}", "");
        var program = model.program();
        var store = SymmetryStateStore.forRun(program,null,Strategy.DFS);
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

    /** Last-owner context and minimum cost transport together; clearing/copying isolate every map. */
    @Test void boundedMembershipPreservesMappedOwnerAndMinimumCost() {
        var model = pair("atomic {} atomic {}", "");
        var store = SymmetryStateStore.forRun(model.program(), null, Strategy.CONTEXT_BOUNDED);
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

    /** Exact DPOR contexts map owner and action PC and never collapse distinct or subset sleepers. */
    @Test void sleepingMembershipTransportsWholeContextAndCopiesEntries() {
        var model = pair("atomic {} atomic {}", "");
        var program = model.program();
        var store = SymmetryStateStore.forRun(program, null, Strategy.DPOR);
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
        assertThrows(IllegalArgumentException.class, () -> SymmetryStateStore.forRun(program,null,Strategy.DFS).sleepCovered(right, Set.of()));
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

    /** Recomposition can alias forward instructions; only DPOR's action transport must reject it. */
    @Test void rejectsAliasedActionOccurrencesOnlyForDpor() {
        var model = pair("atomic {} atomic {}", "");
        var threads = new ArrayList<ModelThread>();
        for (var thread : model.program().threads()) {
            Step jump = thread.steps().get(1);
            threads.add(new ModelThread(thread.id(), List.of(jump,jump)));
        }
        var program = new Program(model.program().initialConfiguration().state(), threads, model.program().sourceDigest());
        assertDoesNotThrow(() -> SymmetryStateStore.forRun(program,null,Strategy.DFS));
        assertThrows(IllegalArgumentException.class, () -> SymmetryStateStore.forRun(program,null,Strategy.DPOR));
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
