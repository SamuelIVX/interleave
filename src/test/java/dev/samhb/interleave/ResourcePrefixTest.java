/** Actual source replay, corruption boundaries and immutable resource-prefix values. */
package dev.samhb.interleave;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Crosses the public resource-prefix interface with real compiler/driver behavior. */
class ResourcePrefixTest {
    /** Root, intermediate and completed stopping positions have literal independent state oracles.
     * @param strategy selected real explorer
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void capturesAndReplaysCommittedPositions(Strategy strategy) {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } atomic { x = 3; } }");
        for (int limit : new int[]{1, 2, 4}) {
            var result = runner(strategy, limit).run(model.program());
            var prefix = result.resourcePrefix().orElseThrow();
            assertEquals(ResourceLimitReason.MAX_STATES, prefix.reason());
            assertEquals(limit, result.statesExplored());
            int length = limit == 1 ? 0 : limit == 2 ? 1 : 3;
            assertEquals(length, prefix.threadIds().size());
            assertEquals(Collections.nCopies(length, 0), prefix.threadIds());
            assertEquals(Collections.nCopies(length, StepOutcome.ADVANCED), prefix.outcomes());
            assertEquals(List.of(length), prefix.programCounters());
            assertEquals(length, prefix.terminalState().fields().get("x"));
            assertEquals(model.program().sourceDigest(), prefix.evidence().sourceDigest());
            assertEquals(length, prefix.evidence().executedSteps().size());
            assertNull(prefix.evidence().cutoff()); assertNull(prefix.evidence().failure());
            assertTrue(result.limitExceeded()); assertFalse(result.hasIncomplete()); assertFalse(result.hasViolation());
            var replay = prefix.replay(model.program(), null);
            assertEquals(limit == 4 ? ScheduleExecution.Kind.COMPLETED : ScheduleExecution.Kind.PREFIX, replay.kind());
            assertFalse(replay.propertyChecked()); assertTrue(replay.failure().isEmpty());
            assertEquals(limit, replay.states().size());
            assertEquals(length, replay.states().getLast().fields().get("x"));
            assertTrue(result.completedTraces().isEmpty(), "resource stop precedes terminal classification");
            assertFalse(com.google.gson.JsonParser.parseString(result.toJson()).getAsJsonObject().has("resourcePrefix"),
                "J1 does not change the JSON schema");
        }
    }

    /** A final property failure is observed separately while the interrupted original stays unchanged.
     * @param property actual compiler property, including evaluation errors
     */
    @ParameterizedTest @ValueSource(strings={"invariant always (x != 1);", "invariant always (1 % (1 - x) == 0);"})
    void observesFinalPropertyFailureWithoutReclassifyingTheRun(String property) {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } } " + property);
        var result = InterleaveRunner.builder().strategy(Strategy.DFS).maxStates(2).invariant(model.invariant().orElseThrow()).build().run(model.program());
        var prefix = result.resourcePrefix().orElseThrow();
        var replay = prefix.replay(model.program(), model.invariant().orElseThrow());
        assertEquals(ScheduleExecution.Kind.VIOLATION, replay.kind()); assertTrue(replay.propertyChecked());
        assertEquals(FailureEvidence.Phase.PROPERTY, replay.failure().orElseThrow().phase());
        assertEquals(property.contains("%") ? FailureEvidence.Kind.RUNTIME_ERROR : FailureEvidence.Kind.INVARIANT,
            replay.failure().orElseThrow().kind());
        assertEquals(2, replay.states().size()); assertEquals(1, replay.states().getLast().fields().get("x"));
        assertEquals(List.of(StepOutcome.ADVANCED), prefix.outcomes());
        assertFalse(result.hasViolation()); assertTrue(result.limitExceeded());
        assertEquals(ScheduleExecution.Kind.PREFIX, prefix.replay(model.program(), null).kind());
    }

    /** Empty execution can reveal an initial failure, completion or real blocked-owner deadlock. */
    @Test
    void observesInitialTerminalPositions() {
        var failed = model("shared int x = 0; thread 0 { atomic {} } invariant always (false);");
        var complete = model("shared int x = 0; thread 0 {}");
        var deadlock = model("shared int x = 0; thread 0 { atomic when (false) { x = 1; } }");
        for (var model : List.of(failed, complete, deadlock)) {
            var result = runner(Strategy.DFS, 1).run(model.program());
            var replay = result.resourcePrefix().orElseThrow().replay(model.program(), model.invariant().orElse(null));
            assertEquals(model == failed ? ScheduleExecution.Kind.VIOLATION : model == complete
                ? ScheduleExecution.Kind.COMPLETED : ScheduleExecution.Kind.DEADLOCK, replay.kind());
            assertEquals(1, replay.states().size()); assertEquals(0, replay.states().getFirst().fields().get("x"));
            assertEquals(model == failed, replay.failure().isPresent());
        }
    }

    /** Rejects identity/outcome/end-position corruption and earlier property termination; propagates errors. */
    @Test
    void authenticatesEvidenceAndFullConsumption() {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } atomic {} }");
        var prefix = runner(Strategy.DFS, 3).run(model.program()).resourcePrefix().orElseThrow();
        var execution = new ExecutionDriver().runDetailed(model.program(), new Schedule(prefix.threadIds()), null);
        var carrier = new Trace(prefix.threadIds(), prefix.outcomes(), TraceOutcome.INCOMPLETE, prefix.evidence());
        assertThrows(CorruptedTraceException.class, () -> prefix.replay(model.program(), (s,c)->false));
        assertThrows(CorruptedTraceException.class, () -> prefix.replay(model.program(), (s,c)->((DynamicState)s).getInt("x") != 1));
        var thrown = new IllegalStateException("property sentinel");
        assertSame(thrown, assertThrows(IllegalStateException.class, () -> prefix.replay(model.program(), (s,c)->{throw thrown;})));
        assertEquals("program", assertThrows(NullPointerException.class, () -> prefix.replay(null, null)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> prefix.replay(
            dev.samhb.interleave.testsupport.TestPrograms.independentFlags(), null));
        var other = new Program(model.program().initialConfiguration().state(), model.program().threads(), "a".repeat(64));
        assertThrows(CorruptedTraceException.class, () -> prefix.replay(other, null));
        assertThrows(IllegalArgumentException.class, () -> new dev.samhb.interleave.minimize.CutoffSimplifier()
            .simplify(model.program(),carrier,null));
        assertThrows(IllegalArgumentException.class, () -> new dev.samhb.interleave.minimize.CutoffSimplifier()
            .shortestSubsequence(model.program(),carrier,null,0));
        var wrongEnd = new ResourcePrefix(carrier, model.program().initialConfiguration(), prefix.reason());
        assertThrows(CorruptedTraceException.class, () -> wrongEnd.replay(model.program(), null));
        var changedOutcomes = new ArrayList<>(prefix.outcomes()); changedOutcomes.set(0, StepOutcome.BLOCKED);
        var wrongOutcomes = new ResourcePrefix(new Trace(prefix.threadIds(), changedOutcomes, TraceOutcome.INCOMPLETE,
            prefix.evidence()), execution.configuration(), prefix.reason());
        assertThrows(CorruptedTraceException.class, () -> wrongOutcomes.replay(model.program(), null));
        var steps = new ArrayList<>(prefix.evidence().executedSteps()); var step = steps.getFirst();
        steps.set(0, new ExecutedStep(1, step.source(), step.label(), step.expansionPath()));
        var changedMetadata = new ResourcePrefix(new Trace(prefix.threadIds(), prefix.outcomes(), TraceOutcome.INCOMPLETE,
            new TraceEvidence(prefix.evidence().sourceDigest(), steps, null, null)), execution.configuration(), prefix.reason());
        assertThrows(CorruptedTraceException.class, () -> changedMetadata.replay(model.program(), null));
        var invalidOwner = new ResourcePrefix(new Trace(List.of(99,99), prefix.outcomes(), TraceOutcome.INCOMPLETE,
            prefix.evidence()), execution.configuration(), prefix.reason());
        assertThrows(IllegalScheduleException.class, () -> invalidOwner.replay(model.program(), null));
    }

    /** Internal construction rejects failure/cutoff/legacy carriers and missing required inputs. */
    @Test
    void rejectsInvalidCarriers() {
        var model = model("shared int x = 0; thread 0 { atomic {} }"); var config = model.program().initialConfiguration();
        var root = new Trace(List.of(), List.of(), TraceOutcome.INCOMPLETE,
            new TraceEvidence(model.program().sourceDigest(), List.of(), null, null));
        var cutoff = new CutoffEvidence(CutoffEvidence.Reason.LOOP_LIMIT,0,0,0,0,null,List.of());
        for (var invalid : List.of(
                new Trace(List.of(),List.of(),TraceOutcome.COMPLETED,root.evidence()),
                Trace.incomplete(List.of(),List.of()),
                new Trace(List.of(),List.of(),TraceOutcome.INCOMPLETE,new TraceEvidence(null,List.of(),null,null)),
                new Trace(List.of(),List.of(),TraceOutcome.INCOMPLETE,new TraceEvidence(model.program().sourceDigest(),List.of(),null,cutoff)),
                new Trace(List.of(),List.of(),TraceOutcome.INCOMPLETE,new TraceEvidence(model.program().sourceDigest(),List.of(),
                    new FailureEvidence(FailureEvidence.Kind.INVARIANT,FailureEvidence.Phase.PROPERTY,null,null),null)))) {
            assertThrows(IllegalArgumentException.class, () -> new ResourcePrefix(invalid, config, ResourceLimitReason.MAX_STATES));
        }
        var step = ((SourceStep)model.program().threads().getFirst().steps().getFirst()).metadata(0);
        var failed = new Trace(List.of(0), List.of(StepOutcome.ASSERTION_FAILED), TraceOutcome.INCOMPLETE,
            new TraceEvidence(model.program().sourceDigest(),List.of(step),null,null));
        assertThrows(IllegalArgumentException.class, () -> new ResourcePrefix(failed,config,ResourceLimitReason.MAX_STATES));
        assertEquals("prefix", assertThrows(NullPointerException.class, () -> new ResourcePrefix(null,config,ResourceLimitReason.MAX_STATES)).getMessage());
        assertEquals("terminal", assertThrows(NullPointerException.class, () -> new ResourcePrefix(root,null,ResourceLimitReason.MAX_STATES)).getMessage());
        assertEquals("reason", assertThrows(NullPointerException.class, () -> new ResourcePrefix(root,config,null)).getMessage());
    }

    /** Capture and replay do not expose mutable arrays, path lists or live configurations. */
    @Test
    void freezesTypedValuesAndSnapshots() {
        var model = model("shared int[] values = [1,2]; local int[] own = [3,4]; thread 0 { "
            + "atomic { values[0] = 9; local.own[1] = 8; } atomic {} }");
        var execution = new ExecutionDriver().runDetailed(model.program(), new Schedule(List.of(0)), null);
        var trace = Trace.create(model.program(),List.of(0),List.of(StepOutcome.ADVANCED),TraceOutcome.INCOMPLETE,null);
        var prefix = new ResourcePrefix(trace,execution.configuration(),ResourceLimitReason.MAX_STATES);
        ((DynamicState)execution.configuration().state()).setArrayElement("values",0,42);
        assertArrayEquals(new int[]{9,2}, (int[])prefix.terminalState().fields().get("values"));
        ((int[])prefix.terminalState().fields().get("values"))[0]=99;
        ((int[])prefix.terminalState().locals().get("0").get("own"))[1]=99;
        assertArrayEquals(new int[]{9,2}, (int[])prefix.terminalState().fields().get("values"));
        assertArrayEquals(new int[]{3,8}, (int[])prefix.terminalState().locals().get("0").get("own"));
        assertThrows(UnsupportedOperationException.class, () -> prefix.programCounters().add(9));
        assertThrows(UnsupportedOperationException.class, () -> prefix.threadIds().add(9));
        assertThrows(UnsupportedOperationException.class, () -> prefix.outcomes().clear());
        assertThrows(UnsupportedOperationException.class, () -> prefix.evidence().executedSteps().clear());
        var replay = prefix.replay(model.program(), (s,c)->true);
        assertThrows(UnsupportedOperationException.class, () -> replay.states().clear());
        ((int[])replay.states().getLast().fields().get("values"))[0]=99;
        assertArrayEquals(new int[]{9,2}, (int[])replay.states().getLast().fields().get("values"));
    }

    /** Immutable runner output supports isolated repeated and concurrent explicit replay.
     * @throws Exception if concurrent work fails
     */
    @Test
    void reusesCapturedEvidenceAcrossThreads() throws Exception {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic {} }");
        var prefix = runner(Strategy.DFS, 2).run(model.program()).resourcePrefix().orElseThrow();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            Callable<ResourcePrefix.Replay> action = () -> { ready.countDown(); start.await(); return prefix.replay(model.program(),null); };
            var a=pool.submit(action); var b=pool.submit(action);
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            assertEquals(ScheduleExecution.Kind.PREFIX,a.get(10,TimeUnit.SECONDS).kind());
            assertEquals(1,b.get(10,TimeUnit.SECONDS).states().getLast().fields().get("x"));
        }
        assertEquals(1,prefix.replay(model.program(),null).states().getLast().fields().get("x"));
    }

    /** Repeated/concurrent runner calls own independent resource evidence, not just replay state.
     * @throws Exception if concurrent real runs fail
     */
    @Test
    void reusableRunnerOwnsEachCapturedValue() throws Exception {
        var first = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic {} }");
        var second = model("shared int x = 0; thread 0 { atomic { x = 7; } atomic {} }");
        var runner = runner(Strategy.DFS, 2);
        var original = runner.run(first.program()).resourcePrefix().orElseThrow();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            var a = pool.submit(() -> { ready.countDown(); start.await(); return runner.run(first.program()); });
            var b = pool.submit(() -> { ready.countDown(); start.await(); return runner.run(second.program()); });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            var left = a.get(10, TimeUnit.SECONDS).resourcePrefix().orElseThrow();
            var right = b.get(10, TimeUnit.SECONDS).resourcePrefix().orElseThrow();
            assertNotSame(original, left); assertNotSame(left, right);
            assertEquals(first.program().sourceDigest(), left.evidence().sourceDigest());
            assertEquals(second.program().sourceDigest(), right.evidence().sourceDigest());
            assertEquals(1, left.replay(first.program(), null).states().getLast().fields().get("x"));
            assertEquals(7, right.replay(second.program(), null).states().getLast().fields().get("x"));
        }
        assertEquals(1, original.terminalState().fields().get("x"));
        assertEquals(1, runner.run(first.program()).resourcePrefix().orElseThrow().terminalState().fields().get("x"));
    }

    /** New captured evidence remains serializable with its TestResult in the same library build.
     * @throws Exception if native object-stream round-trip fails
     */
    @Test
    void serializesCapturedPrefixWithItsResult() throws Exception {
        var model = model("shared int[] values = [1,2]; thread 0 { atomic { values[0] = 9; } atomic {} }");
        var result = runner(Strategy.DFS, 2).run(model.program());
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.ObjectOutputStream(bytes)) { output.writeObject(result); }
        try (var input = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
            var copy = (TestResult)input.readObject();
            assertTrue(copy.limitExceeded()); assertEquals(2, copy.statesExplored());
            var prefix = copy.resourcePrefix().orElseThrow();
            assertEquals(ResourceLimitReason.MAX_STATES, prefix.reason());
            assertArrayEquals(new int[]{9,2}, (int[])prefix.terminalState().fields().get("values"));
            assertArrayEquals(new int[]{9,2}, (int[])prefix.replay(model.program(), null).states().getLast().fields().get("values"));
        }
    }

    /** Loads real source with a stable diagnostic name.
     * @param body declarations, threads and optional property
     * @return compiler-produced model
     */
    private static BenchmarkProgram model(String body) { return new SourceProgramLoader().load("program resource { " + body + " }"); }

    /** Configures the real runner with a deterministic state allowance.
     * @param strategy selected explorer
     * @param limit literal visit allowance
     * @return configured reusable runner
     */
    private static InterleaveRunner runner(Strategy strategy, int limit) {
        return InterleaveRunner.builder().strategy(strategy).maxStates(limit).build();
    }
}
