// Library selection of certified exact symmetry storage, isolation and result identity.
package dev.samhb.interleave;

import com.google.gson.JsonParser;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises both certified backends through the public runner and authentic replay. */
class SymmetryBackendRunTest {
    /** Four dependent increments give five rank layers and the original owner schedule.
     * @param strategy exact search strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void bothBackendsKeepLiteralCountsValuesAndReportIdentity(Strategy strategy) {
        var model = four();
        for (var backend : SymmetryBackend.values()) {
            var runner = runner(model,strategy,backend).maxStates(0).build();
            for (int iteration = 0; iteration < 2; iteration++) {
                var result = runner.run(model.program());
                assertEquals(5,result.statesExplored()); assertFalse(result.limitExceeded());
                assertFalse(result.hasViolation()); assertEquals(1,result.completedTraces().size());
                var trace = result.completedTraces().getFirst();
                assertEquals(List.of(0,1,2,3),trace.threadIds());
                assertEquals(4,replay(model,trace).getInt("x"));
                assertExactMarker(result,strategy);
            }
        }
    }

    /** Both backends preserve literal failure, deadlock and cutoff classifications and replay.
     * @param strategy exact search strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void terminalKindsAndTypedHelperValuesRetainTheirContracts(Strategy strategy) {
        var bodies = List.of(
            Map.entry("assert(false);",TraceOutcome.VIOLATION),
            Map.entry("atomic { x = 1 % x; }",TraceOutcome.VIOLATION),
            Map.entry("atomic when (false) {}",TraceOutcome.DEADLOCK),
            Map.entry("while (true) limit 0 {}",TraceOutcome.INCOMPLETE));
        for (var backend : SymmetryBackend.values()) {
            for (var entry : bodies) {
                var model = pair(entry.getKey(),"");
                var result = runner(model,strategy,backend).build().run(model.program());
                var traces = records(result);
                assertFalse(traces.isEmpty());
                assertEquals(Set.of(entry.getValue()),traces.stream().map(TraceRecord::outcome).collect(Collectors.toSet()),entry.getKey());
                for (var trace : traces) assertDoesNotThrow(() -> replay(model,trace));
                assertExactMarker(result,strategy);
            }
            var helper = new SourceProgramLoader().load("program values { shared int x = 0; local int result = 0; "
                + "helper add(int amount) -> int { atomic { x = x + amount; } return x; } "
                + "thread 0 { call add(1) -> local.result; } thread 1 { call add(1) -> local.result; } invariant final (x == 2); }");
            var result = runner(helper,strategy,backend).build().run(helper.program());
            assertFalse(result.hasViolation()); assertFalse(result.completedTraces().isEmpty());
            for (var trace : result.completedTraces()) assertEquals(2,replay(helper,trace).getInt("x"));
        }
    }

    /** Partial state/time results retain exact orbit identity rather than implying exhaustive absence.
     * @param strategy exact search strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void resourceStopsKeepTheirReasonAndSymmetryMarker(Strategy strategy) {
        var model = four();
        for (var backend : SymmetryBackend.values()) {
            var states = runner(model,strategy,backend).maxStates(2).build().run(model.program());
            assertEquals(2,states.statesExplored()); assertTrue(states.limitExceeded());
            assertEquals(ResourceLimitReason.MAX_STATES,states.resourceLimitReason()); assertExactMarker(states,strategy);
            var time = runner(model,strategy,backend).maxTime(Duration.ZERO).build().run(model.program(),() -> 0);
            assertEquals(1,time.statesExplored()); assertTrue(time.limitExceeded());
            assertEquals(ResourceLimitReason.MAX_TIME,time.resourceLimitReason()); assertExactMarker(time,strategy);
        }
    }

    /** A serial bound cannot lose an update; one interrupted read suffices at the next fresh bound.
     * @param backend certified storage choice reused as immutable configuration
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void iterativeBoundsReopenMembershipAndRetainOnePreemptionFailure(SymmetryBackend backend) {
        var model = pair("atomic { local.r = x; } atomic { x = local.r + 1; }","invariant final (x == 2);");
        var zero = runner(model,Strategy.CONTEXT_BOUNDED,backend).maxPreemptions(0).build().run(model.program());
        assertFalse(zero.hasViolation()); assertFalse(zero.incompleteTraces().isEmpty());
        assertTrue(zero.incompleteTraces().stream().allMatch(t -> t.evidence().cutoff().reason() == CutoffEvidence.Reason.PREEMPTION_BOUND));
        var iterative = runner(model,Strategy.CONTEXT_BOUNDED,backend).maxPreemptions(2).iterativeDeepening(true).build();
        for (int iteration = 0; iteration < 2; iteration++) {
            var result = iterative.run(model.program());
            assertTrue(result.hasViolation()); assertExactMarker(result,Strategy.CONTEXT_BOUNDED);
            var trace = result.failingTraces().getFirst();
            assertEquals(List.of(0,1,1,0),trace.threadIds());
            assertEquals(1,replay(model,trace).getInt("x"));
        }
    }

    /** Concurrent calls share configuration but never membership, including the immutable backend enum.
     * @param strategy exact search strategy
     * @throws Exception if either independently running search fails
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void concurrentRunsOwnFreshContainers(Strategy strategy) throws Exception {
        var model = four();
        for (var backend : SymmetryBackend.values()) {
            var runner = runner(model,strategy,backend).build();
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var ready = new java.util.concurrent.CountDownLatch(2);
                var start = new java.util.concurrent.CountDownLatch(1);
                var left = executor.submit(() -> { ready.countDown(); start.await(); return runner.run(model.program()); });
                var right = executor.submit(() -> { ready.countDown(); start.await(); return runner.run(model.program()); });
                try { assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)); }
                finally { start.countDown(); }
                for (var result : List.of(left.get(),right.get())) {
                    assertEquals(5,result.statesExplored()); assertExactMarker(result,strategy);
                    assertEquals(4,replay(model,result.completedTraces().getFirst()).getInt("x"));
                }
            }
        }
    }

    /** Explicit backend selection cannot enable symmetry implicitly or bypass store/property guards. */
    @Test void validatesModeNullsConflictsAndUnknownProperties() {
        assertThrows(NullPointerException.class,() -> InterleaveRunner.builder().symmetryBackend(null));
        for (var backend : SymmetryBackend.values()) {
            assertThrows(IllegalArgumentException.class,() -> InterleaveRunner.builder().symmetryBackend(backend).build());
            assertThrows(IllegalArgumentException.class,() -> InterleaveRunner.builder().symmetryReduction(true)
                .symmetryBackend(backend).symmetryReduction(false).build());
            assertThrows(IllegalArgumentException.class,() -> InterleaveRunner.builder().symmetryReduction(true)
                .symmetryBackend(backend).strategy(null).build());
            assertThrows(IllegalArgumentException.class,() -> InterleaveRunner.builder().symmetryReduction(true)
                .symmetryBackend(backend).stateStoreFactory(HashingStateStore::new).build());
            assertThrows(IllegalArgumentException.class,() -> InterleaveRunner.builder().stateStore(new BitstateStore(100,2))
                .symmetryBackend(backend).symmetryReduction(true).build());
            assertEquals("maxPreemptions applies only to Strategy.CONTEXT_BOUNDED, but strategy is DFS",
                assertThrows(IllegalArgumentException.class,() -> InterleaveRunner.builder().symmetryReduction(true)
                    .symmetryBackend(backend).maxPreemptions(0).build()).getMessage());
            assertEquals("iterativeDeepening applies only to Strategy.CONTEXT_BOUNDED, but strategy is DFS",
                assertThrows(IllegalArgumentException.class,() -> InterleaveRunner.builder().symmetryReduction(true)
                    .symmetryBackend(backend).iterativeDeepening(false).build()).getMessage());
            var callbacks = new java.util.concurrent.atomic.AtomicInteger();
            var invalid = InterleaveRunner.builder().symmetryReduction(true).symmetryBackend(backend)
                .invariant((state,config) -> { callbacks.incrementAndGet(); return true; }).build();
            assertThrows(IllegalArgumentException.class,() -> invalid.run(four().program())); assertEquals(0,callbacks.get());
            var noGroup = new SourceProgramLoader().load("program one { shared int x = 0; thread 0 { atomic {} } }");
            assertThrows(IllegalArgumentException.class,() -> InterleaveRunner.builder().symmetryReduction(true)
                .symmetryBackend(backend).build().run(noGroup.program()));
        }
    }

    /** Ordinary store factories reject either checked backend before returning unmarked orbit counts.
     * @param strategy exact algorithm requested through the ordinary runner
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void ordinaryRunnerCannotSmuggleEitherBackendThroughAFactory(Strategy strategy) {
        var model = four();
        for (var backend : SymmetryBackend.values()) {
            var checked = SymmetryStateStore.forRun(model.program(),model.invariant().orElseThrow(),strategy,backend);
            var runner = InterleaveRunner.builder().strategy(strategy).stateStoreFactory(() -> checked).build();
            assertThrows(IllegalArgumentException.class,() -> runner.run(model.program()));
            if (strategy == Strategy.CONTEXT_BOUNDED) assertThrows(IllegalArgumentException.class,
                () -> InterleaveRunner.builder().strategy(strategy).iterativeDeepening(true).stateStore(checked).build().run(model.program()));
        }
    }

    /** Configures a checked library run with a generous CBS bound and the actual compiler property.
     * @param model trusted source definition
     * @param strategy requested built-in algorithm
     * @param backend certified exact membership implementation
     * @return caller-adjustable builder
     */
    private static InterleaveRunner.Builder runner(BenchmarkProgram model, Strategy strategy, SymmetryBackend backend) {
        var builder = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true).symmetryBackend(backend)
            .invariant(model.invariant().orElse(null));
        return strategy == Strategy.CONTEXT_BOUNDED ? builder.maxPreemptions(20) : builder;
    }

    /** Four identical one-increment owners have rank zero through four and final value four.
     * @return trusted four-owner model with final property
     */
    private static BenchmarkProgram four() {
        return new SourceProgramLoader().load("program four { shared int x = 0; "
            + "thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } "
            + "thread 2 { atomic { x = x + 1; } } thread 3 { atomic { x = x + 1; } } invariant final (x == 4); }");
    }

    /** Builds two identical owners with a real typed local and optional shared-state property.
     * @param body scheduled body for each owner
     * @param property source invariant or empty text
     * @return trusted paired model
     */
    private static BenchmarkProgram pair(String body, String property) {
        return new SourceProgramLoader().load("program pair { shared int x = 0; local int r = 0; thread 0 { "
            + body + " } thread 1 { " + body + " } " + property + " }");
    }

    /** Authenticates the runner's full source evidence through the real replay implementation.
     * @param model exact source/property binding
     * @param record produced original-owner witness
     * @return actual replayed typed terminal state
     */
    private static DynamicState replay(BenchmarkProgram model, TraceRecord record) {
        var trace = new Trace(record.threadIds(),record.outcomes(),record.outcome(),record.evidence());
        var replayer = new TraceReplayer();
        assertEquals(replayer.captureSnapshots(model.program(),trace),record.states(),"Returned snapshots match authenticated replay");
        return (DynamicState)replayer.replay(model.program(),trace,model.invariant().orElse(null)).state();
    }

    /** Collects every result category without changing its order within a category.
     * @param result real runner output
     * @return all produced witnesses
     */
    private static List<TraceRecord> records(TestResult result) {
        var all = new ArrayList<TraceRecord>(result.completedTraces()); all.addAll(result.failingTraces());
        all.addAll(result.deadlockedTraces()); all.addAll(result.incompleteTraces()); return List.copyOf(all);
    }

    /** Verifies storage and representative markers remain exact for complete and partial outputs.
     * @param result real runner output
     * @param strategy original built-in strategy
     */
    private static void assertExactMarker(TestResult result, Strategy strategy) {
        assertTrue(result.symmetryReduction());
        var json = JsonParser.parseString(result.toJson()).getAsJsonObject();
        assertEquals("EXACT",json.get("storeType").getAsString());
        assertTrue(json.get("symmetryReduction").getAsBoolean());
        assertEquals(strategy.name(),json.get("strategy").getAsString());
    }
}
