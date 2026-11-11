/** Resource interruption ordering, all explorer callback routes and retained run metadata. */
package dev.samhb.interleave;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses actual explorers/stores and a deterministic clock instead of sleeps or model mocks. */
class ResourcePrefixCaptureTest {
    /** Capture evaluates no extra modeled transition or property, including DPOR's opaque fallback.
     * @param strategy explorer under the shared runner limits
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void captureDoesNotExecuteTheModelOrPropertyAgain(Strategy strategy) {
        var original = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } }");
        var transitions = new AtomicInteger(); var properties = new AtomicInteger();
        var thread = original.program().threads().getFirst();
        var steps = thread.steps().stream().map(step -> (Step)new CountingStep((SourceStep)step, transitions)).toList();
        var program = new Program(original.program().initialConfiguration().state(), List.of(new ModelThread(0, steps)), original.program().sourceDigest());
        var result = InterleaveRunner.builder().strategy(strategy).maxStates(2)
            .invariant((s, c) -> {properties.incrementAndGet();return true;}).build().run(program);
        assertEquals(1, transitions.get()); assertEquals(1, properties.get());
        var prefix = result.resourcePrefix().orElseThrow();
        assertEquals(List.of(0), prefix.threadIds()); assertEquals(1, prefix.terminalState().fields().get("x"));
        assertEquals(ScheduleExecution.Kind.PREFIX, prefix.replay(program, null).kind());
    }

    /** Time boundaries use the existing clock seam; simultaneous limits keep state precedence.
     * @param strategy selected real explorer
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void capturesDeadlineAndStatePrecedence(Strategy strategy) {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } }");
        for (long elapsed:new long[]{9, 10, 11}) {
            var clock = new AtomicLong(1000);
            var result = InterleaveRunner.builder().strategy(strategy).maxTime(Duration.ofMillis(10))
                .stateStoreFactory(() -> new ClockStore(clock, elapsed)).build().run(model.program(), clock::get);
            assertEquals(elapsed >= 10, result.limitExceeded());
            assertEquals(elapsed<10?3:2, result.statesExplored());
            assertEquals(elapsed >= 10, result.resourcePrefix().isPresent());
            if (elapsed >= 10) {
                var prefix = result.resourcePrefix().orElseThrow();
                assertEquals(ResourceLimitReason.MAX_TIME, prefix.reason()); assertEquals(List.of(0), prefix.threadIds());
                assertEquals(1, prefix.terminalState().fields().get("x"));
            }
        }
        var root = InterleaveRunner.builder().strategy(strategy).maxTime(Duration.ZERO).build().run(model.program(), () -> 1000);
        assertEquals(ResourceLimitReason.MAX_TIME, root.resourcePrefix().orElseThrow().reason());
        assertTrue(root.resourcePrefix().orElseThrow().threadIds().isEmpty());
        var both = InterleaveRunner.builder().strategy(strategy).maxStates(1).maxTime(Duration.ZERO).build().run(model.program(), () -> 1000);
        assertEquals(ResourceLimitReason.MAX_STATES, both.resourcePrefix().orElseThrow().reason());
        assertEquals(1, both.statesExplored());
    }

    /** Interrupts only the visit callback and observes the active path before any recursive unwinding.
     * @param strategy selected real explorer
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void preservesVisitorOrderingAndExceptionIdentity(Strategy strategy) {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic {} }");
        for (boolean opaque:new boolean[]{false, true}) {
            var expected = new LimitExceededException("visit sentinel"); var events = new ArrayList<String>();
            StateVisitor visitor = new StateVisitor() {
                /** {@inheritDoc} */
                @Override public void onStateVisited(Configuration config) {
                    events.add("visit"+config.programCounters().getFirst());
                    if (config.programCounters().getFirst() == 1) throw expected;
                }
                /** {@inheritDoc} */
                @Override public void onStateVisited(Configuration config, int last, int cost) {
                    assertEquals(strategy, Strategy.CONTEXT_BOUNDED);
                    assertEquals(config.programCounters().getFirst() == 0?-1:0, last); assertEquals(0, cost);
                    onStateVisited(config);
                }
                /** {@inheritDoc} */
                @Override public void onInterruptedPrefix(Configuration config, Trace prefix) {
                    events.add("prefix"); assertEquals(List.of(0), prefix.threadIds());
                    assertEquals(List.of(StepOutcome.ADVANCED), prefix.outcomes());
                    assertEquals(List.of(1), config.programCounters()); assertNull(prefix.cutoff());
                    assertNull(prefix.failure()); assertEquals(model.program().sourceDigest(), prefix.evidence().sourceDigest());
                }
            };
            assertSame(expected, assertThrows(LimitExceededException.class, () -> explore(strategy, model.program(), opaque?(s, c) -> true:null, visitor)));
            assertEquals(List.of("visit0","visit1","prefix"), events);
        }
        var observerFailure = new IllegalStateException("observer sentinel");
        StateVisitor failedObserver = new StateVisitor() {
            /** {@inheritDoc} */
            @Override public void onStateVisited(Configuration config) { throw observerFailure; }
            /** {@inheritDoc} */
            @Override public void onInterruptedPrefix(Configuration config, Trace prefix) {
                fail("A non-limit visitor failure must not trigger resource capture");
            }
        };
        assertSame(observerFailure, assertThrows(IllegalStateException.class,
            () -> explore(strategy, model.program(), null, failedObserver)));
        var expected = new LimitExceededException("default hook");
        assertSame(expected, assertThrows(LimitExceededException.class, () -> explore(strategy, model.program(), null, c -> {throw expected;})));
    }

    /** Earlier completion, failure and cutoff buckets survive a later resource interruption. */
    @Test
    void preservesPriorEvidenceAndActualBranch() {
        for (String property:List.of("", "invariant always (a == 0 || b == 0);")) {
            var model = model("shared int a = 0; shared int b = 0; thread 0 { atomic { a = 1; } } thread 1 { atomic { b = 1; } } "+property);
            var result = InterleaveRunner.builder().maxStates(4).invariant(model.invariant().orElse(null)).build().run(model.program());
            var prefix = result.resourcePrefix().orElseThrow();
            assertEquals(List.of(1), prefix.threadIds()); assertEquals(0, prefix.terminalState().fields().get("a"));
            assertEquals(1, prefix.terminalState().fields().get("b")); assertEquals(List.of(0, 1), prefix.programCounters());
            assertEquals(property.isEmpty()?1:0, result.completedTraces().size());
            assertEquals(property.isEmpty()?0:1, result.failingTraces().size());
        }
        var blocked = model("shared bool gate = true; thread 0 { atomic { gate = false; } } "
            + "thread 1 { atomic when (gate) { gate = true; } }");
        var interrupted = InterleaveRunner.builder().maxStates(3).build().run(blocked.program());
        assertEquals(1, interrupted.deadlockedTraces().size());
        assertEquals(List.of(0), interrupted.deadlockedTraces().getFirst().threadIds());
        assertEquals(List.of(1), interrupted.resourcePrefix().orElseThrow().threadIds());
        assertEquals(true, interrupted.resourcePrefix().orElseThrow().terminalState().fields().get("gate"));
        var bounded = model("shared int x = 0; thread 0 { atomic { while (true) limit 0 {} } } thread 1 { atomic { x = 1; } atomic {} }");
        var result = InterleaveRunner.builder().maxStates(2).build().run(bounded.program());
        assertTrue(result.hasIncomplete()); assertTrue(result.limitExceeded());
        assertEquals(CutoffEvidence.Reason.LOOP_LIMIT, result.incompleteTraces().getFirst().evidence().cutoff().reason());
        assertEquals(List.of(1), result.resourcePrefix().orElseThrow().threadIds());
        assertNull(result.resourcePrefix().orElseThrow().evidence().cutoff());
    }

    /** K = 0 visits root plus two four-position owner-first chains; visit ten is the next pass's root. */
    @Test
    void iterativeStopUsesTheActivePassAndKeepsEarlierCutoffs() {
        var model = model("shared int x = 0; thread 0 { repeat 2 { atomic { x = x + 1; } } } thread 1 { repeat 2 { atomic { x = x + 1; } } }");
        var single = InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(0).build().run(model.program());
        assertEquals(9, single.statesExplored()); assertTrue(single.hasIncomplete());
        for (boolean approximate : new boolean[]{false, true}) {
            var stores = new AtomicInteger();
            var result = InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(1).iterativeDeepening(true)
                .maxStates(10).stateStoreFactory(() -> {
                    stores.incrementAndGet();
                    return approximate ? new BitstateStore(1_000_003, 4) : new HashingStateStore();
                }).build().run(model.program());
            assertEquals(2, stores.get()); assertEquals(10, result.statesExplored());
            assertTrue(result.resourcePrefix().orElseThrow().threadIds().isEmpty());
            assertEquals(List.of(0, 0), result.resourcePrefix().orElseThrow().programCounters());
            assertTrue(result.hasIncomplete());
            assertEquals(approximate ? "BITSTATE" : "EXACT",
                com.google.gson.JsonParser.parseString(result.toJson()).getAsJsonObject().get("storeType").getAsString());
        }
    }

    /** Checked backend identity and approximate result metadata survive attaching/copying the prefix.
     * @param strategy selected supported strategy
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void preservesSymmetryApproximationAndCopies(Strategy strategy) {
        var model = model("shared int x = 0; thread 0 { repeat 2 { atomic { x = x + 1; } } } thread 1 { repeat 2 { atomic { x = x + 1; } } }");
        for (var backend:SymmetryBackend.values()) {
            var result = InterleaveRunner.builder().strategy(strategy).symmetryReduction(true).symmetryBackend(backend).maxStates(2).build().run(model.program());
            assertTrue(result.symmetryReduction()); assertEquals(List.of(0), result.resourcePrefix().orElseThrow().threadIds());
            assertSame(result.resourcePrefix().orElseThrow(), result.withRunMetadata(true, ResourceLimitReason.MAX_STATES).resourcePrefix().orElseThrow());
            assertSame(result.resourcePrefix().orElseThrow(), result.withRunMetadata(true, ResourceLimitReason.MAX_STATES, false).resourcePrefix().orElseThrow());
            assertThrows(IllegalArgumentException.class, () -> result.withRunMetadata(false, ResourceLimitReason.MAX_STATES));
            assertThrows(IllegalArgumentException.class, () -> result.withRunMetadata(true, null));
        }
        var approximate = InterleaveRunner.builder().strategy(strategy).maxStates(2).stateStoreFactory(() -> new BitstateStore(1_000_003, 4)).build().run(model.program());
        assertTrue(approximate.resourcePrefix().isPresent());
        assertEquals("BITSTATE", com.google.gson.JsonParser.parseString(approximate.toJson()).getAsJsonObject().get("storeType").getAsString());
    }

    /** Actual PCs and repeat ancestry survive skipping a source branch and unrolling its successor. */
    @Test
    void retainsControlIdentityAndExpansionAncestry() {
        var branch = model("shared int x = 0; thread 0 { if (false) { atomic { x = 99; } } atomic { x = 1; } atomic {} }");
        var prefix = InterleaveRunner.builder().maxStates(3).build().run(branch.program()).resourcePrefix().orElseThrow();
        assertEquals(List.of(0, 2), prefix.evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
        assertEquals(List.of(3), prefix.programCounters());
        assertEquals(1, prefix.replay(branch.program(), null).states().getLast().fields().get("x"));
        var repeated = model("shared int x = 0; thread 0 { repeat 3 { atomic { x = x + 1; } } }");
        var repeatedPrefix = InterleaveRunner.builder().maxStates(3).build().run(repeated.program()).resourcePrefix().orElseThrow();
        assertEquals(1, repeatedPrefix.evidence().executedSteps().get(1).expansionPath().getFirst().iteration());
        assertEquals(2, repeatedPrefix.replay(repeated.program(), null).states().getLast().fields().get("x"));
        var helper = model("shared int x = 0; helper assign(int amount) { atomic { x = amount; } } "
            + "thread 0 { call assign(7); atomic {} }");
        var helperPrefix = InterleaveRunner.builder().maxStates(3).build().run(helper.program()).resourcePrefix().orElseThrow();
        assertEquals(2, helperPrefix.evidence().executedSteps().size());
        assertTrue(helperPrefix.evidence().executedSteps().get(1).label().contains("assign"));
        assertEquals(7, helperPrefix.terminalState().fields().get("x"));
        assertEquals(7, helperPrefix.replay(helper.program(), null).states().getLast().fields().get("x"));
    }

    /** Aliased instruction objects keep actual PCs and their definition's source identity.
     * @param strategy selected explorer, including ordinary DPOR alias traversal
     */
    @ParameterizedTest @EnumSource(Strategy.class)
    void preservesAliasedInstructionOccurrences(Strategy strategy) {
        String body = "if (local.r == 0) { atomic { x = x + 1; } } else { atomic { x = x + 2; } } atomic {}";
        var original = model("shared int x = 0; local int r = 0; thread 0 { " + body + " } thread 1 { " + body + " }");
        var threads = new ArrayList<ModelThread>();
        for (var thread : original.program().threads()) {
            var steps = new ArrayList<>(thread.steps());
            steps.set(1, steps.get(2));
            threads.add(new ModelThread(thread.id(), steps));
        }
        var program = new Program(original.program().initialConfiguration().state(), threads, original.program().sourceDigest());
        var result = InterleaveRunner.builder().strategy(strategy).maxStates(3).build().run(program);
        var prefix = result.resourcePrefix().orElseThrow();
        assertEquals(List.of(0, 0), prefix.threadIds());
        assertEquals(List.of(0, 1), prefix.evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
        assertEquals(((SourceStep)threads.getFirst().steps().get(2)).metadata(2).source(),
            prefix.evidence().executedSteps().get(1).source());
        assertEquals(List.of(3, 0), prefix.programCounters());
        assertEquals(2, prefix.replay(program, null).states().getLast().fields().get("x"));
        assertThrows(CorruptedTraceException.class, () -> prefix.replay(original.program(), null));
    }

    /** Successful/legacy/unknown interruptions and old public constructors do not acquire provenance. */
    @Test
    void doesNotFabricateResourceEvidence() {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } }");
        var reusable = InterleaveRunner.builder().maxStates(3).build();
        assertTrue(reusable.run(model.program()).resourcePrefix().isEmpty());
        assertTrue(InterleaveRunner.builder().maxStates(1).build().run(dev.samhb.interleave.testsupport.TestPrograms.independentFlags()).resourcePrefix().isEmpty());
        var expected = new LimitExceededException("unattributed store interruption");
        var result = InterleaveRunner.builder().stateStoreFactory(() -> {throw expected;}).build().run(model.program());
        assertTrue(result.limitExceeded()); assertNull(result.resourceLimitReason()); assertTrue(result.resourcePrefix().isEmpty());
        var old = new TestResult(Strategy.DFS, 0, 0, 0, List.of(), List.of(), List.of(), false);
        var prefix = InterleaveRunner.builder().maxStates(1).build().run(model.program()).resourcePrefix().orElseThrow();
        assertTrue(old.resourcePrefix().isEmpty()); assertThrows(IllegalArgumentException.class, () -> old.withResourcePrefix(prefix));
        var sourceWithoutStop = old.withRunMetadata(true, ResourceLimitReason.MAX_STATES);
        assertThrows(IllegalArgumentException.class, () -> sourceWithoutStop.withResourcePrefix(prefix));
        assertThrows(NullPointerException.class, () -> old.withResourcePrefix(null));
    }

    /** Executes real traversal routes; an unknown property deliberately selects exhaustive fallback.
     * @param strategy selected explorer
     * @param program actual source model
     * @param property actual property, or null
     * @param visitor synchronous observer
     */
    private static void explore(Strategy strategy, Program program, Invariant property, StateVisitor visitor) {
        switch (strategy) {
            case DFS -> new DfsExplorer().explore(program, property, new HashingStateStore(), visitor);
            case STATIC_POR -> new dev.samhb.interleave.por.StaticPorExplorer().explore(program, property, new HashingStateStore(), visitor);
            case DPOR -> new dev.samhb.interleave.dpor.DporExplorer().explore(program, property, new HashingStateStore(), visitor);
            case CONTEXT_BOUNDED -> new dev.samhb.interleave.cb.ContextBoundedExplorer().explore(program, property, new HashingStateStore(), visitor, 2);
        }
    }

    /** Loads a real compiler-backed fixture.
     * @param body source declarations/threads/property
     * @return isolated immutable model
     */
    private static BenchmarkProgram model(String body) { return new SourceProgramLoader().load("program capture { "+body+" }"); }

    /** Delegates real source semantics, counting only actual transitions without influencing them.
     * @param delegate compiler-produced instruction
     * @param transitions observation-only counter
     */
    private record CountingStep(SourceStep delegate, AtomicInteger transitions) implements SourceStep {
        /** {@inheritDoc} */ @Override public Set<MemoryLocation> reads(){return delegate.reads();}
        /** {@inheritDoc} */ @Override public Set<MemoryLocation> writes(){return delegate.writes();}
        /** {@inheritDoc} */ @Override public boolean enabled(SharedState state){return delegate.enabled(state);}
        /** {@inheritDoc} */ @Override public ExecutedStep metadata(int pc){return delegate.metadata(pc);}
        /** {@inheritDoc} */ @Override public StepTransition transition(List<ModelThread> threads, Configuration parent, int id, SharedState scratch){
            transitions.incrementAndGet();return delegate.transition(threads, parent, id, scratch);
        }
    }

    /** Real exact store with a visit-driven test clock; duplicate suppression and CBS costs are unchanged. */
    private static final class ClockStore implements StateStore {
        private final HashingStateStore delegate = new HashingStateStore();
        private final AtomicLong clock;
        private final long elapsed;
        /** Retains the test clock and a fixed post-action elapsed time.
         * @param clock controlled millisecond clock
         * @param elapsed elapsed value after the first action
         */
        private ClockStore(AtomicLong clock, long elapsed){this.clock = clock;this.elapsed = elapsed;}
        /** {@inheritDoc} */ @Override public boolean isVisited(Configuration c){return delegate.isVisited(c);}
        /** {@inheritDoc} */ @Override public boolean isVisited(Configuration c, int last, int cost){return delegate.isVisited(c, last, cost);}
        /** {@inheritDoc} */ @Override public void markVisited(Configuration c){delegate.markVisited(c);advance(c);}
        /** {@inheritDoc} */ @Override public void markVisited(Configuration c, int last, int cost){delegate.markVisited(c, last, cost);advance(c);}
        /** {@inheritDoc} */ @Override public void clear(){delegate.clear();}
        /** Advances only the clock; never changes modeled values or store membership.
         * @param config newly marked position
         */
        private void advance(Configuration config){clock.set(1000+(config.programCounters().getFirst() == 0?0:elapsed));}
    }
}
