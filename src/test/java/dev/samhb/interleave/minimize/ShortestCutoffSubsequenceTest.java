// Independent subsequence minima, budget boundaries and authenticated cutoff contracts.
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
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks shortest length against literal schedules and independent bitmask enumeration. */
class ShortestCutoffSubsequenceTest {
    /** Stateless operation reused to detect invocation-state leaks. */
    private final CutoffSimplifier simplifier = new CutoffSimplifier();

    /** Nonadjacent deletion crosses a four-choice local minimum and proves the two-choice optimum. */
    @Test void findsAShorterWitnessBeyondChunkAndSingleDeletionMinimality() {
        var model = weighted(""); var original = loop(model,List.of(0,1,2,3,4));
        assertEquals(List.of(0,1,2,3),simplifier.simplify(model.program(),original,null).threadIds());
        var result = shortest(model,original,11);
        assertEquals(List.of(0,2),result.trace().threadIds()); assertTrue(result.shortestProven());
        assertEquals(11,result.candidatesChecked()); assertEquals(11,result.candidateLimit());
        assertEquals(original.cutoff(),result.trace().cutoff()); assertEquals(TraceOutcome.INCOMPLETE,result.trace().outcome());
        assertEquals(5,state(model,result.trace()).getInt("x"));
        assertEquals(List.of(0,0),result.trace().evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
        assertEquals(original.evidence().sourceDigest(),result.trace().evidence().sourceDigest());
        assertEquals(List.of(0,1,2,3),original.threadIds()); assertEquals(15,state(model,original).getInt("x"));
        assertThrows(UnsupportedOperationException.class,() -> result.trace().threadIds().clear());
        var valid = new HashSet<List<Integer>>();
        // Independent bitmask enumeration covers all sixteen subsets, including the supplied witness.
        for (int mask = 0; mask < 16; mask++) {
            var choices = new ArrayList<Integer>();
            for (int bit = 0; bit < 4; bit++) if ((mask & (1 << bit)) != 0) choices.add(bit);
            if (valid(model,original,choices)) valid.add(List.copyOf(choices));
        }
        assertEquals(Set.of(List.of(0,2),List.of(0,1,2,3)),valid);
    }

    /** Budget stops never claim proof even when the best-found pair is already shortest.
     * @param limit maximum candidate evaluations
     * @param length literal best-found prefix length
     * @param proven whether all shorter candidates were excluded
     * @param checked literal performed candidate count
     */
    @ParameterizedTest @CsvSource({"0,4,false,0","1,4,false,1","4,4,false,4","5,4,false,5",
        "6,2,false,6","10,2,false,10","11,2,true,11","12,2,true,11"})
    void preservesHonestBestFoundAndProofAtExactBudgetBoundaries(long limit, int length, boolean proven, long checked) {
        var model = weighted(""); var original = loop(model,List.of(0,1,2,3,4));
        var result = shortest(model,original,limit);
        assertEquals(length,result.trace().length()); assertEquals(proven,result.shortestProven());
        assertEquals(checked,result.candidatesChecked()); assertEquals(limit,result.candidateLimit());
        assertEquals(length == 2 ? List.of(0,2) : List.of(0,1,2,3),result.trace().threadIds());
        assertEquals(original.cutoff(),result.trace().cutoff()); assertDoesNotThrow(() -> state(model,result.trace()));
    }

    /** Root cutoffs can reach the absolute zero-length lower bound without consuming extra budget.
     * @param limit maximum candidate count, including a very large allowance
     * @param length literal returned length
     * @param proven whether empty is reached
     * @param checked literal number of evaluated prefixes
     */
    @ParameterizedTest @CsvSource({"0,4,false,0","1,3,false,1","3,1,false,3","4,0,true,4","9223372036854775807,0,true,4"})
    void reachesEmptyExactlyAndStopsAfterProof(long limit, int length, boolean proven, long checked) {
        var model = noise(); var original = loop(model,List.of(0,0,0,0,1));
        var result = shortest(model,original,limit);
        assertEquals(length,result.trace().length()); assertEquals(proven,result.shortestProven());
        assertEquals(checked,result.candidatesChecked()); assertEquals(limit,result.candidateLimit());
        assertEquals(original.cutoff(),result.trace().cutoff());
        if (proven) assertEquals(List.of(),result.trace().threadIds());
    }

    /** Empty originals still authenticate and are already shortest at a zero candidate limit. */
    @Test void authenticatesEmptyOriginalBeforeItsZeroWorkProof() {
        var model = noise(); var original = loop(model,List.of(1));
        var result = shortest(model,original,0);
        assertSame(original,result.trace()); assertTrue(result.shortestProven());
        assertEquals(0,result.candidatesChecked()); assertEquals(0,result.candidateLimit());
        assertEquals(1,new TraceReplayer().captureSnapshots(model.program(),result.trace()).size());
        var stale = new Trace(original.threadIds(),original.outcomes(),original.outcome(),
            new TraceEvidence("0".repeat(64),original.evidence().executedSteps(),null,original.cutoff()));
        assertThrows(CorruptedTraceException.class,() -> shortest(model,stale,0));
    }

    /** A timed property excludes the apparent pair; fifteen proper subsets must then be rejected. */
    @Test void preservesIntermediatePropertyTimingAndCanProveTheOriginalShortest() {
        var model = weighted("invariant always (x != 5);"); var original = loop(model,List.of(0,1,2,3,4));
        var result = shortest(model,original,15);
        assertTrue(result.shortestProven()); assertEquals(List.of(0,1,2,3),result.trace().threadIds());
        assertEquals(15,result.candidatesChecked()); assertEquals(original.cutoff(),result.trace().cutoff());
        assertEquals(15,state(model,result.trace()).getInt("x"));
    }

    /** A shorter legal schedule outside the original choices is outside this deliberately narrow proof. */
    @Test void subsequenceProofDoesNotClaimShortestAcrossAllSchedules() {
        var model = load("shared int noise = 0; shared bool ready = false; "
            + "thread 0 { repeat 4 { atomic { noise = noise + 1; } } atomic { ready = true; } } "
            + "thread 1 { atomic { ready = true; } } thread 2 { atomic when (ready) { while (true) limit 0 {} } }");
        var original = loop(model,List.of(0,0,0,0,0,2)); var alternative = loop(model,List.of(1,2));
        var result = shortest(model,original,31);
        assertTrue(result.shortestProven()); assertEquals(List.of(0,0,0,0,0),result.trace().threadIds());
        assertEquals(31,result.candidatesChecked()); assertEquals(1,alternative.length());
        assertEquals(original.cutoff(),alternative.cutoff()); assertEquals(original.cutoff(),result.trace().cutoff());
    }

    /** Exact K=0/K=1 witnesses preserve omitted target PCs and real enabledness-based costs. */
    @Test void preservesPreemptionAllowancesAndActualOmittedIdentity() {
        var model = load("shared int noise = 0; thread 0 { repeat 4 { atomic { noise = noise + 1; } } } "
            + "thread 1 { repeat 4 { atomic { noise = noise + 1; } } }");
        var zero = preemption(model,List.of(0,0),1,0); var one = preemption(model,List.of(0,0,1,1),0,1);
        var first = shortest(model,zero,10); var second = shortest(model,one,30);
        assertTrue(first.shortestProven()); assertTrue(second.shortestProven());
        assertEquals(List.of(0),first.trace().threadIds()); assertEquals(List.of(0,0,1),second.trace().threadIds());
        assertEquals(zero.cutoff(),first.trace().cutoff()); assertEquals(one.cutoff(),second.trace().cutoff());
        assertEquals(0,first.trace().cutoff().limit()); assertEquals(1,second.trace().cutoff().limit());
        assertEquals(2,second.trace().cutoff().instructionIndex());
        assertDoesNotThrow(() -> state(model,first.trace())); assertDoesNotThrow(() -> state(model,second.trace()));
    }

    /** The loop body/check pair retains its actual omitted PC, iteration, location and ancestry. */
    @Test void retainsLoopIterationAndRegeneratesActualPrefixMetadata() {
        var model = load("shared int counter = 0; shared int noise = 0; thread 0 { atomic { noise = 1; } } "
            + "thread 1 { while (counter < 3) limit 1 { atomic { counter = counter + 1; } } } invariant always (counter <= 1);");
        var original = loop(model,List.of(0,1,1,1)); var result = shortest(model,original,10);
        assertTrue(result.shortestProven()); assertEquals(List.of(1,1),result.trace().threadIds());
        assertEquals(original.cutoff(),result.trace().cutoff()); assertEquals(2,result.trace().cutoff().instructionIndex());
        assertEquals(1,result.trace().cutoff().iterations()); assertEquals(1,result.trace().cutoff().limit());
        assertEquals(List.of(0,1),result.trace().evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
        assertEquals(0,state(model,result.trace()).getInt("noise")); assertEquals(1,state(model,result.trace()).getInt("counter"));
    }

    /** Earlier assertions/errors cannot turn a shorter but invalid prefix into a certified cutoff.
     * @param check scheduled operation requiring the producer
     */
    @ParameterizedTest @ValueSource(strings = {"assert (ready == 1);","atomic { noise = 1 % ready; }"})
    void rejectsCandidatesWithEarlierStepFailures(String check) {
        var model = load("shared int ready = 0; shared int noise = 0; thread 0 { atomic { ready = 1; } } "
            + "thread 1 { " + check + " while (true) limit 0 {} }");
        var original = loop(model,List.of(0,1,1)); var result = shortest(model,original,3);
        assertTrue(result.shortestProven()); assertEquals(List.of(0,1),result.trace().threadIds());
        assertEquals(3,result.candidatesChecked()); assertEquals(original.cutoff(),result.trace().cutoff());
    }

    /** Existing explorers' actual source evidence authenticates without inferred or copied outcomes. */
    @Test void acceptsActualSourceCutoffsFromEveryExplorer() {
        var model = noise();
        var results = List.of(new DfsExplorer().explore(model.program()),new StaticPorExplorer().explore(model.program()),
            new DporExplorer().explore(model.program()),new ContextBoundedExplorer().explore(model.program(),null,null,null,0));
        for (var run : results) {
            var witnesses = run.traces().stream().filter(t -> t.outcome() == TraceOutcome.INCOMPLETE).toList();
            assertFalse(witnesses.isEmpty());
            for (var witness : witnesses) {
                var result = shortest(model,witness,100);
                assertTrue(result.shortestProven()); assertEquals(witness.cutoff(),result.trace().cutoff());
                assertDoesNotThrow(() -> state(model,result.trace()));
            }
        }
    }

    /** Invalid limits, unsupported inputs and corrupt originals reject even without search work. */
    @Test void validatesOriginalAndBudgetBeforeReturningAnyResult() {
        var model = noise(); var original = loop(model,List.of(0,1));
        assertThrows(IllegalArgumentException.class,() -> shortest(model,original,-1));
        assertThrows(NullPointerException.class,() -> simplifier.shortestSubsequence(null,original,null,0));
        assertThrows(NullPointerException.class,() -> simplifier.shortestSubsequence(model.program(),null,null,0));
        assertThrows(IllegalArgumentException.class,() -> shortest(model,Trace.incomplete(List.of(),List.of()),0));
        var legacy = new Program(model.program().initialConfiguration().state(),model.program().threads());
        assertThrows(NullPointerException.class,() -> simplifier.shortestSubsequence(legacy,null,null,0));
        assertThrows(IllegalArgumentException.class,() -> simplifier.shortestSubsequence(legacy,original,null,0));
        var missing = new Trace(original.threadIds(),original.outcomes(),original.outcome(),
            new TraceEvidence(null,original.evidence().executedSteps(),null,original.cutoff()));
        assertThrows(IllegalArgumentException.class,() -> shortest(model,missing,0));
        var verdict = new Trace(original.threadIds(),original.outcomes(),TraceOutcome.COMPLETED,original.evidence());
        assertThrows(IllegalArgumentException.class,() -> shortest(model,verdict,0));
        var c = original.cutoff();
        var wrong = new CutoffEvidence(c.reason(),c.threadId(),c.instructionIndex(),1,1,c.source(),c.expansionPath());
        var forged = new Trace(original.threadIds(),original.outcomes(),original.outcome(),
            new TraceEvidence(original.evidence().sourceDigest(),original.evidence().executedSteps(),null,wrong));
        assertThrows(CorruptedTraceException.class,() -> shortest(model,forged,0));
        var badOutcome = new Trace(original.threadIds(),List.of(StepOutcome.BLOCKED),original.outcome(),original.evidence());
        assertThrows(CorruptedTraceException.class,() -> shortest(model,badOutcome,0));
    }

    /** Unexpected deterministic property errors during a new candidate are not swallowed. */
    @Test void propagatesPropertyErrorsFromCandidateEvaluation() {
        var model = weighted(""); var original = loop(model,List.of(0,1,2,3,4));
        var error = assertThrows(IllegalStateException.class,() -> simplifier.shortestSubsequence(model.program(),original,
            (state,config) -> { if (((DynamicState)state).getInt("x") == 11) throw new IllegalStateException("candidate predicate failed"); return true; },11));
        assertEquals("candidate predicate failed",error.getMessage());
    }

    /** Shared simplifier reuse keeps each source, cutoff, budget and best witness local.
     * @throws Exception if either real search fails
     */
    @Test void repeatedAndConcurrentInvocationsStayIndependent() throws Exception {
        var first = weighted(""); var second = noise();
        var weighted = loop(first,List.of(0,1,2,3,4)); var noise = loop(second,List.of(0,0,0,0,1));
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            var left = executor.submit(() -> { ready.countDown(); start.await(); return shortest(first,weighted,11); });
            var right = executor.submit(() -> { ready.countDown(); start.await(); return shortest(second,noise,4); });
            try { assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            var a = left.get(); var b = right.get();
            assertTrue(a.shortestProven()); assertTrue(b.shortestProven());
            assertEquals(List.of(0,2),a.trace().threadIds()); assertEquals(List.of(),b.trace().threadIds());
            assertEquals(weighted.cutoff(),a.trace().cutoff()); assertEquals(noise.cutoff(),b.trace().cutoff());
            assertEquals(11,a.candidatesChecked()); assertEquals(4,b.candidatesChecked());
        }
        assertEquals(List.of(0,2),shortest(first,weighted,11).trace().threadIds());
        assertFalse(shortest(first,weighted,0).shortestProven());
    }

    /** Four distinct weights have exactly one proper cutoff-enabling subsequence, owners zero and two.
     * @param property actual timed invariant, or empty
     * @return trusted finite weighted source
     */
    private static BenchmarkProgram weighted(String property) {
        return load("shared int x = 0; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 2; } } "
            + "thread 2 { atomic { x = x + 4; } } thread 3 { atomic { x = x + 8; } } "
            + "thread 4 { atomic when (x == 5 || x == 15) { while (true) limit 0 {} } } " + property);
    }

    /** Supplies a root cutoff after optional repeated noise choices.
     * @return deterministic two-owner source
     */
    private static BenchmarkProgram noise() {
        return load("shared int noise = 0; thread 0 { repeat 4 { atomic { noise = noise + 1; } } } "
            + "thread 1 { atomic { while (true) limit 0 {} } }");
    }

    /** Calls the bounded operation with its exact source/property binding.
     * @param model trusted source and optional timed property
     * @param witness original authenticated cutoff
     * @param limit maximum tested candidates
     * @return immutable result and proof status
     */
    private CutoffSimplifier.ShortestResult shortest(BenchmarkProgram model, Trace witness, long limit) {
        return simplifier.shortestSubsequence(model.program(),witness,model.invariant().orElse(null),limit);
    }

    /** Checks a bitmask candidate through execution/replay rather than the search's combination logic.
     * @param model source/property binding
     * @param original original exact cutoff
     * @param choices independently selected owner subsequence
     * @return whether the whole omitted evidence still authenticates
     */
    private static boolean valid(BenchmarkProgram model, Trace original, List<Integer> choices) {
        try {
            var execution = new ExecutionDriver().runDetailed(model.program(),new Schedule(choices),model.invariant().orElse(null));
            if (execution.kind() != ScheduleExecution.Kind.PREFIX) return false;
            var trace = Trace.create(model.program(),choices,execution.outcomes(),TraceOutcome.INCOMPLETE,model.invariant().orElse(null),original.cutoff());
            new TraceReplayer().replay(model.program(),trace,model.invariant().orElse((s,c) -> true)); return true;
        } catch (IllegalScheduleException | CorruptedTraceException rejected) { return false; }
    }

    /** Executes the real omitted loop attempt and keeps the driver's authentic evidence.
     * @param model source/property binding
     * @param choices committed prefix followed by the omitted loop attempt
     * @return actual INCOMPLETE trace
     */
    private static Trace loop(BenchmarkProgram model, List<Integer> choices) {
        var execution = new ExecutionDriver().runDetailed(model.program(),new Schedule(choices),model.invariant().orElse(null));
        assertEquals(ScheduleExecution.Kind.INCOMPLETE,execution.kind()); return execution.trace();
    }

    /** Builds real omitted-owner metadata while replay checks the independent literal K oracle.
     * @param model trusted source
     * @param prefix committed choices
     * @param target omitted owner
     * @param limit preserved preemption allowance
     * @return source trace carrying the exact omitted scheduling action
     */
    private static Trace preemption(BenchmarkProgram model, List<Integer> prefix, int target, int limit) {
        var execution = new ExecutionDriver().runDetailed(model.program(),new Schedule(prefix),null);
        int pc = execution.configuration().programCounters().get(target);
        var metadata = ((SourceStep)model.program().threads().get(target).steps().get(pc)).metadata(pc);
        var cutoff = new CutoffEvidence(CutoffEvidence.Reason.PREEMPTION_BOUND,target,pc,limit,null,metadata.source(),metadata.expansionPath());
        return Trace.create(model.program(),prefix,execution.outcomes(),TraceOutcome.INCOMPLETE,null,cutoff);
    }

    /** Authenticates and reads the actual returned terminal prefix values.
     * @param model exact source/property binding
     * @param trace accepted incomplete witness
     * @return committed state before its omitted action
     */
    private static DynamicState state(BenchmarkProgram model, Trace trace) {
        return (DynamicState)new TraceReplayer().replay(model.program(),trace,model.invariant().orElse((s,c) -> true)).state();
    }

    /** Loads initialized declarations, owners and optional property as one bounded source model.
     * @param body complete program contents
     * @return fresh trusted source model
     */
    private static BenchmarkProgram load(String body) { return new SourceProgramLoader().load("program shortest { " + body + " }"); }
}
