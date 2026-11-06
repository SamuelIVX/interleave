/** Independent loop/preemption witness oracles for source-only cutoff simplification. */
package dev.samhb.interleave.minimize;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CutoffSimplifierTest {
    private final CutoffSimplifier simplifier = new CutoffSimplifier();

    /** Four unrelated actions can all disappear when the same loop cuts off at the root. */
    @Test void removesAllNoiseAndKeepsAnEmptyCutoffPrefix() {
        var model = noiseLoop();
        Trace original = loopTrace(model, List.of(0, 0, 0, 0, 1));
        assertEquals(4, original.length());
        Trace shorter = simplify(model, original);
        assertEquals(List.of(), shorter.threadIds());
        assertEquals(original.cutoff(), shorter.cutoff());
        assertEquals(TraceOutcome.INCOMPLETE, shorter.outcome());
        assertEquals(4, original.length());
        assertEquals(0, state(model, shorter).getInt("noise"));
        assertEquals(1, new TraceReplayer().captureSnapshots(model.program(), shorter).size());
        assertEquals(shorter.evidence(), simplify(model, shorter).evidence());
        assertThrows(IllegalArgumentException.class, () -> new DeltaDebugger().minimize(
            model.program(), shorter, TraceOutcome.INCOMPLETE, model.invariant().orElse(null)));
    }

    /** The write that makes a guarded cutoff enabled cannot be removed. */
    @Test void retainsRequiredGuardEnablingAction() {
        var model = load("shared bool ready = false; shared int noise = 0; thread 0 { atomic { ready = true; } }"
            + " thread 1 { atomic when (ready) { while (true) limit 0 {} } }");
        Trace original = loopTrace(model, List.of(0, 1));
        Trace shorter = simplify(model, original);
        assertEquals(List.of(0), shorter.threadIds());
        assertEquals(original.cutoff(), shorter.cutoff());
        assertOneMinimal(model, shorter);
    }

    /** Noise disappears but the body/check pair needed to reach omitted PC 2 remains. */
    @Test void keepsActualLoopCounterAndIterationIdentity() {
        var model = load("shared int counter = 0; shared int noise = 0; thread 0 { repeat 3 { atomic { noise = noise + 1; } } }"
            + " thread 1 { while (counter < 3) limit 1 { atomic { counter = counter + 1; } } } invariant always (counter <= 1);");
        Trace original = loopTrace(model, List.of(0, 0, 0, 1, 1, 1));
        assertEquals(5, original.length());
        Trace shorter = simplify(model, original);
        assertEquals(List.of(1, 1), shorter.threadIds());
        assertEquals(List.of(0, 1), shorter.evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
        assertEquals(2, shorter.cutoff().instructionIndex());
        assertEquals(1, shorter.cutoff().limit());
        assertEquals(1, shorter.cutoff().iterations());
        assertEquals(original.cutoff(), shorter.cutoff());
        assertEquals(1, state(model, shorter).getInt("counter"));
        assertEquals(0, state(model, shorter).getInt("noise"));
        assertOneMinimal(model, shorter);
    }

    /** Coarse chunks remove required actions; individual refinement can remove only the middle noise. */
    @Test void refinesChunksToIndividualChoices() {
        var model = load("shared int noise = 0; thread 0 { atomic {} atomic {} while (true) limit 0 {} }"
            + " thread 1 { atomic { noise = 1; } }");
        Trace original = loopTrace(model, List.of(0, 1, 0, 0));
        Trace shorter = simplify(model, original);
        assertEquals(List.of(0, 0), shorter.threadIds());
        assertEquals(original.cutoff(), shorter.cutoff());
        assertOneMinimal(model, shorter);
    }

    /** K=0 requires a live last owner; a root choice has no preemption to preserve. */
    @Test void zeroPreemptionBoundKeepsOneChoiceRatherThanZero() {
        var model = liveOwners();
        Trace original = preemption(model, List.of(0, 0), 1, 0);
        Trace shorter = simplify(model, original);
        assertEquals(List.of(0), shorter.threadIds());
        assertEquals(original.cutoff(), shorter.cutoff());
        assertEquals(0, shorter.cutoff().limit());
        assertOneMinimal(model, shorter);
    }

    /** K=1 preserves two target-owner actions and the last-owner switch needed to exceed it. */
    @Test void preservesNonzeroPreemptionCostAndTargetPc() {
        var model = liveOwners();
        Trace original = preemption(model, List.of(0, 0, 1, 1), 0, 1);
        Trace shorter = simplify(model, original);
        assertEquals(List.of(0, 0, 1), shorter.threadIds());
        assertEquals(2, shorter.cutoff().instructionIndex());
        assertEquals(1, shorter.cutoff().limit());
        assertEquals(original.cutoff(), shorter.cutoff());
        assertOneMinimal(model, shorter);
    }

    /** A switch away from a terminated owner is free and cannot authenticate a cutoff. */
    @Test void rejectsFreeSwitchAndPrefixAlreadyOverItsBound() {
        var model = load("shared int noise = 0; thread 0 { repeat 3 { atomic {} } } thread 1 { atomic {} }");
        Trace free = preemption(model, List.of(0, 1), 0, 1);
        assertThrows(CorruptedTraceException.class, () -> simplify(model, free));
        Trace over = preemption(liveOwners(), List.of(0, 1), 0, 0);
        assertThrows(CorruptedTraceException.class, () -> simplify(liveOwners(), over));
    }

    /** Every actual source witness emitted by existing explorers remains a valid incomplete witness. */
    @Test void acceptsActualWitnessesFromAllFourExplorers() {
        var model = noiseLoop();
        var property = model.invariant().orElse(null);
        List<DfsResult> results = List.of(new DfsExplorer().explore(model.program(), property),
            new StaticPorExplorer().explore(model.program(), property), new DporExplorer().explore(model.program(), property),
            new ContextBoundedExplorer().explore(model.program(), property, null, null, 0));
        for (var result : results) {
            var traces = result.traces().stream().filter(t -> t.outcome() == TraceOutcome.INCOMPLETE).toList();
            assertFalse(traces.isEmpty());
            for (Trace trace : traces) {
                Trace shorter = simplify(model, trace);
                assertTrue(shorter.length() <= trace.length());
                assertEquals(trace.cutoff(), shorter.cutoff());
                assertOneMinimal(model, shorter);
            }
        }
    }

    /** Deletions cannot introduce an earlier assertion, expression error or global property failure.
     * @param condition terminal behavior avoided only after the producer's write
     */
    @ParameterizedTest @ValueSource(strings = {"assert (ready == 1);", "atomic { noise = 1 % ready; }"})
    void retainsActionsAvoidingEarlierStepFailures(String condition) {
        var model = load("shared int ready = 0; shared int noise = 0; thread 0 { atomic { ready = 1; } }"
            + " thread 1 { " + condition + " while (true) limit 0 {} }");
        Trace original = loopTrace(model, List.of(0, 1, 1));
        Trace shorter = simplify(model, original);
        assertEquals(List.of(0, 1), shorter.threadIds());
        assertEquals(original.cutoff(), shorter.cutoff());
        assertOneMinimal(model, shorter);
    }

    /** The supplied global property is checked for every shortened prefix, not only at its end. */
    @Test void retainsActionsAvoidingAnIntermediatePropertyFailure() {
        var model = load("shared int ready = 0; shared int noise = 0; thread 0 { atomic { ready = 1; } }"
            + " thread 1 { atomic { noise = 1; } while (true) limit 0 {} } invariant always (noise == 0 || ready == 1);");
        Trace original = loopTrace(model, List.of(0, 1, 1));
        assertEquals(List.of(0, 1), simplify(model, original).threadIds());
    }

    /** Stale digest, wrong omitted identity and altered loop allowance never get normalized away. */
    @Test void rejectsForgedOriginalEvidenceBeforeSimplifying() {
        var model = noiseLoop();
        Trace original = loopTrace(model, List.of(0, 1));
        Trace stale = new Trace(original.threadIds(), original.outcomes(), original.outcome(),
            new TraceEvidence("0".repeat(64), original.evidence().executedSteps(), null, original.cutoff()));
        assertThrows(CorruptedTraceException.class, () -> simplify(model, stale));
        var c = original.cutoff();
        var wrong = new CutoffEvidence(c.reason(), c.threadId(), c.instructionIndex(), 1, 1, c.source(), c.expansionPath());
        Trace forged = new Trace(original.threadIds(), original.outcomes(), original.outcome(),
            new TraceEvidence(original.evidence().sourceDigest(), original.evidence().executedSteps(), null, wrong));
        assertThrows(CorruptedTraceException.class, () -> simplify(model, forged));
        Trace badOutcome = new Trace(original.threadIds(), List.of(StepOutcome.BLOCKED), original.outcome(), original.evidence());
        assertThrows(CorruptedTraceException.class, () -> simplify(model, badOutcome));
    }

    /** Missing evidence, non-source input and ordinary verdicts are deliberate API errors. */
    @Test void rejectsUnsupportedInputsAndNullArguments() {
        var model = noiseLoop();
        Trace original = loopTrace(model, List.of(1));
        assertThrows(NullPointerException.class, () -> simplifier.simplify(null, original, null));
        assertThrows(NullPointerException.class, () -> simplifier.simplify(model.program(), null, null));
        assertThrows(IllegalArgumentException.class, () -> simplify(model, Trace.incomplete(List.of(), List.of())));
        Trace missingIdentity = new Trace(List.of(), List.of(), TraceOutcome.INCOMPLETE,
            new TraceEvidence(null, List.of(), null, original.cutoff()));
        assertThrows(IllegalArgumentException.class, () -> simplify(model, missingIdentity));
        Trace wrongVerdict = new Trace(original.threadIds(), original.outcomes(), TraceOutcome.COMPLETED, original.evidence());
        assertThrows(IllegalArgumentException.class, () -> simplify(model, wrongVerdict));
        assertThrows(IllegalArgumentException.class, () -> simplify(model, new Trace(List.of(), List.of(), TraceOutcome.COMPLETED)));
        Program legacy = new Program(model.program().initialConfiguration().state(), model.program().threads());
        assertThrows(NullPointerException.class, () -> simplifier.simplify(legacy, null, null));
        assertThrows(IllegalArgumentException.class, () -> simplifier.simplify(legacy, original, null));
    }

    /** Exceptions in a caller-supplied predicate are not swallowed as failed deletion attempts. */
    @Test void propagatesUnexpectedPropertyFailures() {
        var model = noiseLoop();
        Trace original = loopTrace(model, List.of(1));
        assertThrows(IllegalStateException.class, () -> simplifier.simplify(model.program(), original,
            (state, config) -> { throw new IllegalStateException("predicate broken"); }));
    }

    /** Reuse never leaks another call's source identity, original cutoff or schedule. */
    @Test void invocationsRemainIndependent() {
        var first = noiseLoop();
        var second = liveOwners();
        Trace loop = loopTrace(first, List.of(0, 1));
        Trace bound = preemption(second, List.of(0, 0), 1, 0);
        assertEquals(List.of(), simplify(first, loop).threadIds());
        assertEquals(List.of(0), simplify(second, bound).threadIds());
        assertEquals(loop.cutoff(), simplify(first, loop).cutoff());
    }

    /** Supplies four independent noise actions followed by a root-time loop cutoff in another owner.
     * @return compiled source with a true timed property
     */
    private static BenchmarkProgram noiseLoop() {
        return load("shared int noise = 0; thread 0 { repeat 4 { atomic { noise = noise + 1; } } }"
            + " thread 1 { atomic { while (true) limit 0 {} } } invariant always (noise >= 0);");
    }

    /** Supplies four unconditional actions per owner so all preemption choices are live.
     * @return two-owner source fixture
     */
    private static BenchmarkProgram liveOwners() {
        return load("shared int noise = 0; thread 0 { repeat 4 { atomic { noise = noise + 1; } } }"
            + " thread 1 { repeat 4 { atomic { noise = noise + 1; } } }");
    }

    /** Executes an actual loop cutoff, with the omitted attempt included only in the input schedule.
     * @param model source model/property
     * @param choices prefix plus attempted loop action
     * @return authentic incomplete trace
     */
    private static Trace loopTrace(BenchmarkProgram model, List<Integer> choices) {
        var result = new ExecutionDriver().runDetailed(model.program(), new Schedule(choices), model.invariant().orElse(null));
        assertEquals(ScheduleExecution.Kind.INCOMPLETE, result.kind());
        return result.trace();
    }

    /** Builds source preemption evidence at a known PC; costs are asserted independently in tests.
     * @param model source fixture
     * @param prefix committed schedule
     * @param target omitted owner
     * @param limit original K, never adjusted by simplification
     * @return source-aware trace, including deliberately invalid witnesses for negative tests
     */
    private static Trace preemption(BenchmarkProgram model, List<Integer> prefix, int target, int limit) {
        var execution = new ExecutionDriver().runDetailed(model.program(), new Schedule(prefix), null);
        int pc = execution.configuration().programCounters().get(target);
        var metadata = ((SourceStep) model.program().threads().get(target).steps().get(pc)).metadata(pc);
        var cutoff = new CutoffEvidence(CutoffEvidence.Reason.PREEMPTION_BOUND, target, pc, limit, null, metadata.source(), metadata.expansionPath());
        return Trace.create(model.program(), prefix, execution.outcomes(), TraceOutcome.INCOMPLETE, null, cutoff);
    }

    /** Calls the new operation with the model's actual property.
     * @param model source model/property
     * @param trace original incomplete witness
     * @return validated simplification
     */
    private Trace simplify(BenchmarkProgram model, Trace trace) { return simplifier.simplify(model.program(), trace, model.invariant().orElse(null)); }

    /** Independently tests every remaining single deletion using only the existing execution/replay APIs.
     * @param model source model/property
     * @param trace final simplified witness
     */
    private static void assertOneMinimal(BenchmarkProgram model, Trace trace) {
        Invariant property = model.invariant().orElse((state, config) -> true);
        for (int i = 0; i < trace.length(); i++) {
            List<Integer> ids = new ArrayList<>(trace.threadIds()); ids.remove(i);
            try {
                var execution = new ExecutionDriver().runDetailed(model.program(), new Schedule(ids), property);
                if (execution.kind() != ScheduleExecution.Kind.PREFIX) continue;
                Trace candidate = Trace.create(model.program(), ids, execution.outcomes(), TraceOutcome.INCOMPLETE, property, trace.cutoff());
                try { new TraceReplayer().replay(model.program(), candidate, property); }
                catch (CorruptedTraceException expected) { continue; }
                fail("A single deletion still preserves the cutoff: " + ids);
            } catch (IllegalScheduleException expected) { /* Deletion disables an action. */ }
        }
    }

    /** Replays actual terminal evidence and returns the committed values.
     * @param model source model/property
     * @param trace authenticated witness
     * @return final committed state
     */
    private static DynamicState state(BenchmarkProgram model, Trace trace) {
        return (DynamicState) new TraceReplayer().replay(model.program(), trace, model.invariant().orElse((s, c) -> true)).state();
    }

    /** Loads a complete bounded source model.
     * @param body shared declarations, threads and optional property
     * @return validated source model
     */
    private static BenchmarkProgram load(String body) { return new SourceProgramLoader().load("program cutoff { " + body + " }"); }
}
