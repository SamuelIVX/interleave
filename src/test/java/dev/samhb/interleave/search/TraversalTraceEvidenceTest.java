/** Differential source-evidence contracts and actual execution counts for traversal-built traces. */
package dev.samhb.interleave.search;

import com.google.gson.JsonParser;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.por.StaticPorExplorer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class TraversalTraceEvidenceTest {
    /** Traversal paths, including the conservative unknown-property DPOR route. */
    enum Mode { DFS, POR, DPOR, DPOR_UNKNOWN, CBS }

    /** Produces independently specified source scenarios crossed with every explorer path.
     * @return named source and expected terminal classification per strategy
     */
    static Stream<Arguments> cases() {
        var bodies = List.of(
            new Scenario("empty completion", "thread 0 {}", "true", TraceOutcome.COMPLETED),
            new Scenario("initial invariant", "thread 0 {}", "false", TraceOutcome.VIOLATION),
            new Scenario("initial property error", "thread 0 {}", "1 % x == 0", TraceOutcome.VIOLATION),
            new Scenario("property after action", "thread 0 { atomic { x = 1; } }", "x == 0", TraceOutcome.VIOLATION),
            new Scenario("array runtime", "shared int[] a = []; thread 0 { atomic { x = 1; x = a[0]; } }", "true", TraceOutcome.VIOLATION),
            new Scenario("step runtime", "thread 0 { atomic { x = 1; } atomic { x = 4; y = 1 % y; } }", "true", TraceOutcome.VIOLATION),
            new Scenario("deadlock", "thread 0 { atomic when (false) { x = 1; } }", "true", TraceOutcome.DEADLOCK),
            new Scenario("empty cutoff", "thread 0 { while (true) limit 0 {} }", "true", TraceOutcome.INCOMPLETE),
            new Scenario("expanded cutoff", "thread 0 { repeat 2 { atomic { x = x + 1; } } while (true) limit 1 { atomic { x = x + 1; } } }", "true", TraceOutcome.INCOMPLETE),
            new Scenario("forward branches", "thread 0 { if (false) { atomic { x = 100; } } else { repeat 2 { atomic { x = x + 1; } } } if (x == 2) { atomic { y = 1; } } }", "true", TraceOutcome.COMPLETED),
            new Scenario("sibling schedules", "thread 0 { atomic { x = x * 3 + 1; } atomic { x = x * 3 + 1; } } thread 1 { atomic { x = x * 3 + 2; } atomic { x = x * 3 + 2; } }", "true", TraceOutcome.COMPLETED),
            new Scenario("independent sleepers", "thread 0 { atomic { x = 1; } atomic { x = 2; } } thread 1 { atomic { y = 1; } atomic { y = 2; } }", "true", TraceOutcome.COMPLETED),
            new Scenario("revisited contexts", "thread 0 { atomic { x = 1; } atomic {} } thread 1 { atomic { x = 1; } atomic { y = 1; } }", "true", TraceOutcome.COMPLETED));
        return bodies.stream().flatMap(s -> Arrays.stream(Mode.values()).map(m -> Arguments.of(s, m)));
    }

    /** New construction must preserve every serialized evidence field and reproduce the same verdict.
     * @param scenario literal model and expected outcome
     * @param mode public exploration route
     */
    @ParameterizedTest(name = "{0}: {1}") @MethodSource("cases")
    void evidenceMatchesReplayFactory(Scenario scenario, Mode mode) {
        var model = new SourceProgramLoader().load("program evidence { shared int x = 0; shared int y = 0; "
            + scenario.body() + " invariant always (" + scenario.property() + "); }");
        DfsResult result = explore(model, mode);
        assertFalse(result.traces().isEmpty());
        assertEquals(Set.of(scenario.outcome()), result.traces().stream().map(Trace::outcome).collect(java.util.stream.Collectors.toSet()));
        for (Trace trace : result.traces()) assertCompatible(model, trace, property(model, mode));
    }

    /** CBS's separate omitted choice must not enter the captured execution list. */
    @Test void cbsPreemptionSnapshotSurvivesUnwindingAndReuse() {
        var model = new SourceProgramLoader().load("program preempt { shared int x = 0; thread 0 { repeat 2 { atomic { x = x + 1; } } } thread 1 { repeat 2 { atomic { x = x + 1; } } } }");
        var explorer = new ContextBoundedExplorer();
        var first = explorer.explore(model.program(), null, null, null, 0);
        Trace cutoff = first.traces().stream().filter(t -> t.cutoff() != null).findFirst().orElseThrow();
        assertEquals(CutoffEvidence.Reason.PREEMPTION_BOUND, cutoff.cutoff().reason());
        assertEquals(List.of(0), cutoff.threadIds());
        assertEquals(1, cutoff.evidence().executedSteps().size());
        for (Trace trace : first.traces()) assertCompatible(model, trace, null);
        var second = explorer.explore(model.program(), null, null, null, 0);
        assertEquals(first.traces().stream().map(t -> JsonParser.parseString(t.toRecord().toJson())).toList(), second.traces().stream().map(t -> JsonParser.parseString(t.toRecord().toJson())).toList());
        assertEquals(List.of(0), cutoff.threadIds());
    }

    /** Two real actions require exactly two executions; old terminal replay would execute four.
     * @param mode traversal route to instrument
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void traversalDoesNotExecutePrefixAgain(Mode mode) {
        var loaded = new SourceProgramLoader().load("program count { shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } } invariant always (true); }");
        var executions = new AtomicInteger();
        var steps = loaded.program().threads().getFirst().steps().stream()
            .map(step -> (Step) new CountingStep((SourceStep) step, executions)).toList();
        var program = new Program(loaded.program().initialConfiguration().state(), List.of(new ModelThread(0, steps)), loaded.program().sourceDigest());
        var model = new BenchmarkProgram("count", program, null, loaded.invariant().orElseThrow());
        var result = explore(model, mode);
        assertEquals(2, executions.get());
        assertEquals(List.of(0, 0), result.traces().getFirst().threadIds());
        Trace trace = result.traces().getFirst();
        Invariant invariant = property(model, mode);
        Trace old = Trace.create(program, trace.threadIds(), trace.outcomes(), trace.outcome(), invariant);
        assertEquals(4, executions.get()); // Two real actions plus two executions by the compatibility factory.
        assertEquals(old.evidence(), trace.evidence());
        new TraceReplayer().replay(program, trace, invariant);
    }

    /** A failed source attempt executes once before failure evidence is frozen, without terminal replay.
     * @param mode traversal route to instrument
     */
    @ParameterizedTest @EnumSource(Mode.class)
    void traversalDoesNotExecuteFailingTransitionAgain(Mode mode) {
        var loaded = new SourceProgramLoader().load("program failedcount { shared int x = 0; thread 0 { atomic { x = 7; x = 1 % 0; } } invariant always (true); }");
        var executions = new AtomicInteger();
        var step = new CountingStep((SourceStep) loaded.program().threads().getFirst().steps().getFirst(), executions);
        var program = new Program(loaded.program().initialConfiguration().state(),
            List.of(new ModelThread(0, List.of(step))), loaded.program().sourceDigest());
        var model = new BenchmarkProgram("failedcount", program, null, loaded.invariant().orElseThrow());
        var result = explore(model, mode);
        assertEquals(1, executions.get());
        assertEquals(1, result.traces().size());
        Trace trace = result.traces().getFirst();
        assertEquals(TraceOutcome.VIOLATION, trace.outcome());
        assertEquals(List.of(StepOutcome.ASSERTION_FAILED), trace.outcomes());
        assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, trace.failure().kind());
        assertEquals(FailureEvidence.Phase.STEP, trace.failure().phase());
        assertEquals(FailureEvidence.Category.MODULO_BY_ZERO, trace.failure().category());
        assertCompatible(model, trace, property(model, mode));
    }

    /** Rejects an omitted action at the shared path seam rather than silently appending it. */
    @Test void omittedCutoffCannotBeAppended() {
        var model = new SourceProgramLoader().load("program cutoff { shared int x = 0; thread 0 { while (true) limit 0 {} } }");
        var parent = model.program().initialConfiguration();
        var execution = TransitionExecutor.execute(model.program(), parent, 0);
        assertNotNull(execution.cutoff());
        assertThrows(IllegalArgumentException.class, () -> TracePrefix.empty().append(model.program(), parent, 0, execution));
    }

    /** Selects a public explorer; the unknown wrapper deliberately withholds observation metadata.
     * @param model compiled fixture
     * @param mode public route
     * @return completed search, CBS using an unrestricted-for-these-fixtures bound
     */
    private static DfsResult explore(BenchmarkProgram model, Mode mode) {
        Invariant invariant = property(model, mode);
        return switch (mode) {
            case DFS -> new DfsExplorer().explore(model.program(), invariant);
            case POR -> new StaticPorExplorer().explore(model.program(), invariant);
            case DPOR -> new DporExplorer().explore(model.program(), invariant);
            case DPOR_UNKNOWN -> new DporExplorer().explore(model.program(), invariant);
            case CBS -> new ContextBoundedExplorer().explore(model.program(), invariant, null, null, 20);
        };
    }

    /** Supplies the independently selected property contract for this route.
     * @param model compiled source model
     * @param mode selected traversal path
     * @return original diagnostic property, or an unknown-observation plain wrapper
     */
    private static Invariant property(BenchmarkProgram model, Mode mode) {
        Invariant original = model.invariant().orElse(null);
        return mode == Mode.DPOR_UNKNOWN ? (s, c) -> original.holds(s, c) : original;
    }

    /** Compares complete immutable records then validates source identity, steps and terminal outcome.
     * @param model source fixture and original diagnostic property
     * @param trace emitted traversal evidence
     * @param comparison exact property contract used by exploration, including unknown wrappers
     */
    private static void assertCompatible(BenchmarkProgram model, Trace trace, Invariant comparison) {
        Trace old = Trace.create(model.program(), trace.threadIds(), trace.outcomes(), trace.outcome(), comparison, trace.cutoff());
        assertEquals(JsonParser.parseString(old.toRecord().toJson()), JsonParser.parseString(trace.toRecord().toJson()));
        new TraceReplayer().replay(model.program(), trace, comparison == null ? (s, c) -> true : comparison);
    }

    /** Independent named source fixture.
     * @param name test display name
     * @param body source thread definitions
     * @param property source invariant expression
     * @param outcome expected unique terminal classification
     */
    private record Scenario(String name, String body, String property, TraceOutcome outcome) {}

    /** Delegates real source semantics and adds an out-of-model, non-influencing execution counter.
     * @param delegate compiler-produced instruction
     * @param executions observation-only counter
     */
    private record CountingStep(SourceStep delegate, AtomicInteger executions) implements SourceStep {
        /** {@inheritDoc} */
        @Override public Set<MemoryLocation> reads() { return delegate.reads(); }
        /** {@inheritDoc} */
        @Override public Set<MemoryLocation> writes() { return delegate.writes(); }
        /** {@inheritDoc} */
        @Override public boolean enabled(SharedState state) { return delegate.enabled(state); }
        /** {@inheritDoc} */
        @Override public ExecutedStep metadata(int pc) { return delegate.metadata(pc); }
        /** {@inheritDoc} */
        @Override public StepTransition transition(List<ModelThread> threads, Configuration parent, int threadId, SharedState scratch) {
            executions.incrementAndGet();
            return delegate.transition(threads, parent, threadId, scratch);
        }
    }
}
