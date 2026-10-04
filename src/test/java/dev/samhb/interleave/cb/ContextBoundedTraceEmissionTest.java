package dev.samhb.interleave.cb;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.MemoryLocation;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.Step;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.StateVisitor;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;

import org.junit.jupiter.api.Test;

import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Spec 12.04 — CBS trace emission and reporting.
 *
 * <p>{@code addTrace} writes to two places: a list the explorer owns, and a callback it does not.
 * Removing the callback leaves every observation through {@code getTraces()} correct while silently
 * starving any caller using {@link StateVisitor}. That asymmetry is the reason this file exists, and
 * it is why {@link #addTrace_notifiesVisitor_withIdenticalTraceInstance()} deliberately carries the
 * list check and the callback check in one test: the falsification check for this spec requires that
 * deleting {@code onTraceCreated} turns the count comparison red <em>while the list assertion stays
 * green</em>. If both went red together the test would no longer be demonstrating the divergence.
 *
 * <p>Stated precisely, because the distinction is easy to overclaim: what goes red is the
 * {@code getTraces().size()} versus {@code visitor.traceCount()} comparison. The
 * {@code assertFalse(result.traces().isEmpty())} check before it stays green, and the identity loop
 * after it is vacuous under the mutation because the visitor receives nothing to iterate.
 *
 * <p>Bounds are never hardcoded. Every test that depends on the preemption budget derives its bound
 * in-test from a predicate over the search, so a corpus change surfaces as a failure with a readable
 * message instead of a silently skipped case.
 */
class ContextBoundedTraceEmissionTest {

    /** Upper bound for bound-derivation searches. Comfortably above any interesting corpus bound. */
    private static final int MAX_PROBE_BOUND = 8;

    // --- R1, R3: the notification path ------------------------------------------------

    /**
     * R1/R3 — the visitor is notified once per trace, with the identical instance, and the count
     * matches {@code getTraces()}.
     *
     * <p>The two assertions are intentionally in one test so the divergence above is demonstrable.
     */
    @Test
    void addTrace_notifiesVisitor_withIdenticalTraceInstance() {
        BenchmarkProgram program = named("lost-update");
        RecordingVisitor visitor = new RecordingVisitor();

        DfsResult result = new ContextBoundedExplorer()
            .explore(program.program(), program.invariant().orElseThrow(), null, visitor, 2);

        // List-side assertion. Stays green when the onTraceCreated call is removed: getTraces()
        // still returns every trace, because addTrace writes the list independently.
        assertFalse(result.traces().isEmpty(), "the run should have produced traces to compare");

        // This count comparison is the assertion that detects the missing callback. Remove
        // onTraceCreated and it fails with the list still fully populated -- 5 traces against 0
        // recorded -- which is the divergence this test exists to pin.
        assertEquals(result.traces().size(), visitor.traceCount(),
            "every recorded trace must be offered to the visitor");

        // Identity check on what the visitor did receive. Note this loop is vacuous when the
        // callback is missing, since the visitor's list is then empty -- it confirms the instances
        // handed over are the very objects in traces, and is not what detects the defect.
        for (Trace seen : visitor.traces()) {
            assertTrue(containsIdentical(result.traces(), seen),
                "the visitor must receive the identical Trace instance that lands in traces, not a copy");
        }
    }

    /**
     * R3 — full-search parity: N traces reported means exactly N callbacks, each reference-identical
     * to the corresponding entry in {@code getTraces()}, in the same order.
     */
    @Test
    void explore_reportsEveryTraceToVisitor() {
        for (BenchmarkProgram program : BugCorpus.all()) {
            RecordingVisitor visitor = new RecordingVisitor();
            DfsResult result = new ContextBoundedExplorer()
                .explore(program.program(), program.invariant().orElse(null), null, visitor, 2);

            assertEquals(result.traces().size(), visitor.traceCount(),
                "trace/callback count mismatch for " + program.name());
            for (int i = 0; i < result.traces().size(); i++) {
                assertSame(result.traces().get(i), visitor.traces().get(i),
                    "visitor must see the same instance, at the same index, for " + program.name());
            }
        }
    }

    /**
     * R2 — a null visitor is legal: traces are still recorded and nothing throws.
     */
    @Test
    void addTrace_nullVisitor_stillRecordsTrace() {
        BenchmarkProgram program = named("lost-update");

        DfsResult result = new ContextBoundedExplorer()
            .explore(program.program(), program.invariant().orElseThrow(), null, null, 2);

        assertFalse(result.traces().isEmpty(), "a null visitor must not suppress trace recording");
        assertTrue(hasOutcome(result, TraceOutcome.VIOLATION),
            "sanity: the program should still report its violation without a visitor");
    }

    // --- R4-R8: the INCOMPLETE suppression rule ---------------------------------------

    /**
     * R4 — a run that exceeds the budget <em>and</em> reports a {@code VIOLATION} emits no
     * {@code INCOMPLETE} trace.
     *
     * <p>The bound is derived rather than chosen, and both premises are asserted at the bound that
     * gets used: the run must be bounded (visible without the invariant) and must produce the
     * violation (visible with it). Deriving both from the same program at the same bound is what
     * makes this test meaningful — boundedness is otherwise unobservable once a failure suppresses
     * {@code INCOMPLETE}, which is precisely the case under test.
     */
    @Test
    void incompleteTrace_suppressedWhenViolationFound() {
        BenchmarkProgram program = named("lost-update");
        Invariant invariant = program.invariant().orElseThrow();

        int bound = -1;
        for (int k = 0; k <= MAX_PROBE_BOUND && bound < 0; k++) {
            boolean bounded = hasOutcome(run(program, null, k), TraceOutcome.INCOMPLETE);
            boolean violates = hasOutcome(run(program, invariant, k), TraceOutcome.VIOLATION);
            if (bounded && violates) {
                bound = k;
            }
        }

        if (bound < 0) {
            fail("no bound in [0," + MAX_PROBE_BOUND + "] makes " + program.name()
                + " both budget-bounded and violating; the corpus changed and this test needs"
                + " re-deriving");
        }

        DfsResult result = run(program, invariant, bound);
        assertTrue(hasOutcome(result, TraceOutcome.VIOLATION),
            "premise: a VIOLATION must be present at the derived bound " + bound);
        assertFalse(hasOutcome(result, TraceOutcome.INCOMPLETE),
            "a run that found a real failure must not also report INCOMPLETE; the pruned region is"
                + " irrelevant once there is something to reproduce");
    }

    /**
     * R5 — a run that exceeds the budget <em>and</em> deadlocks emits no {@code INCOMPLETE} trace.
     *
     * <p>This needs a purpose-built program rather than a corpus one, and the reason is worth
     * recording. Boundedness is only observable while nothing suppresses {@code INCOMPLETE}, so a
     * program that deadlocks can never <em>report</em> that it was also bounded — the very property
     * under test is the thing that hides the premise. Measured across the corpus at bounds 0-8, no
     * program is ever both: {@link #corpusSuppliesNoDeadlockWhileBoundedWitness()} pins that.
     *
     * <p>So boundedness is established a different way here. The preemption bound is the only thing
     * that prunes, so a bound at which strictly fewer states are explored than at the next bound up
     * is a bound at which the budget was demonstrably exceeded. For this program that is K=0:
     * 7 states, against 9 at K=1.
     *
     * <p>Three threads' flags plus a two-step blocking guard gives the required shape — a reachable
     * deadlock, and other schedules that need more preemptions than the bound allows.
     */
    @Test
    void incompleteTrace_suppressedWhenDeadlockFound() {
        Program program = threeThreadDeadlockProgram();

        int bound = -1;
        for (int k = 0; k < MAX_PROBE_BOUND; k++) {
            if (run(program, k).statesExplored() < run(program, k + 1).statesExplored()) {
                bound = k;
                break;
            }
        }
        if (bound < 0) {
            fail("no bound in [0," + (MAX_PROBE_BOUND - 1) + "] constrains this program, so the"
                + " budget-exceeded premise cannot be established");
        }

        DfsResult atBound = run(program, bound);
        DfsResult aboveBound = run(program, bound + 1);

        assertTrue(hasOutcome(atBound, TraceOutcome.DEADLOCK),
            "premise: a DEADLOCK trace must be present at the derived bound " + bound);
        assertTrue(atBound.statesExplored() < aboveBound.statesExplored(),
            "premise: the bound must be binding, or nothing was suppressed. Explored "
                + atBound.statesExplored() + " states at K=" + bound + " and "
                + aboveBound.statesExplored() + " at K=" + (bound + 1)
                + "; equal counts mean the budget never bound here");
        assertFalse(hasOutcome(atBound, TraceOutcome.INCOMPLETE),
            "a run that deadlocked must not also report INCOMPLETE; the pruned region is"
                + " irrelevant once there is a deadlock to reproduce");
    }

    /**
     * R5 — <b>currently unreachable with the corpus, and this test is why that stays visible.</b>
     *
     * <p>The requirement asks for a run that exceeds the budget <em>and</em> deadlocks. No corpus
     * program can produce that combination, and the reason is structural rather than accidental:
     * boundedness is only observable while nothing suppresses {@code INCOMPLETE}, so a program that
     * always deadlocks can never <em>report</em> that it was also bounded.
     *
     * <p>Measured on {@code deadlock}, the only corpus program that deadlocks: it is bounded at
     * K=0 (13 states, {@code COMPLETED + INCOMPLETE}) and deadlocks from K=1 (21 states,
     * {@code COMPLETED + DEADLOCK}). The two sets are disjoint. That 21 is <em>constant</em> from
     * K=1 to K=6 is the load-bearing observation: the bound has stopped binding, so the budget was
     * never exceeded and {@code INCOMPLETE} is absent because there was nothing to report, not
     * because it was suppressed.
     *
     * <p><b>If this test ever fails, that is good news:</b> the corpus has acquired a program that
     * both deadlocks and exceeds the budget. R5's {@code DEADLOCK} half is already covered, by
     * {@link #incompleteTrace_suppressedWhenDeadlockFound()} against a purpose-built program; what
     * this test watches is whether a <em>corpus</em> witness has appeared, at which point the
     * purpose-built program can be retired in favour of one driven by the corpus.
     */
    @Test
    void corpusSuppliesNoDeadlockWhileBoundedWitness() {
        boolean anyProgramDeadlocked = false;

        // Per-program disjointness, because 'bounded at some K' and 'deadlocks at some K' are
        // properties of a program, not of the corpus as a whole.
        for (BenchmarkProgram program : BugCorpus.all()) {
            Set<Integer> programBounded = new TreeSet<>();
            Set<Integer> programDeadlocking = new TreeSet<>();
            long statesAtFirstDeadlock = -1;
            long statesAtMaxBound = -1;
            for (int k = 0; k <= MAX_PROBE_BOUND; k++) {
                DfsResult result = run(program, null, k);
                if (hasOutcome(result, TraceOutcome.INCOMPLETE)) {
                    programBounded.add(k);
                }
                if (hasOutcome(result, TraceOutcome.DEADLOCK)) {
                    programDeadlocking.add(k);
                    if (statesAtFirstDeadlock < 0) {
                        statesAtFirstDeadlock = result.statesExplored();
                    }
                }
                if (k == MAX_PROBE_BOUND) {
                    statesAtMaxBound = result.statesExplored();
                }
            }
            if (programDeadlocking.isEmpty()) {
                continue;
            }
            anyProgramDeadlocked = true;
            assertTrue(Collections.disjoint(programBounded, programDeadlocking),
                program.name() + " is now both budget-bounded (K=" + programBounded
                    + ") and deadlocking (K=" + programDeadlocking + "). That is the R5 witness this"
                    + " spec has been missing - add the covering test, do not relax this assertion");
            assertEquals(statesAtFirstDeadlock, statesAtMaxBound,
                program.name() + " should have saturated its state count once it can deadlock,"
                    + " since the preemption bound no longer binds; if it has not, a DEADLOCK"
                    + " witness may now exist");
        }

        assertTrue(anyProgramDeadlocked,
            "expected at least one corpus program to deadlock; if none does, this test is vacuous"
                + " and R5's DEADLOCK branch has lost its only possible witness class");
    }

    /**
     * R6 — a run that exceeds the budget having emitted only {@code COMPLETED} traces emits exactly
     * one {@code INCOMPLETE}.
     *
     * <p>{@code peterson} is the corpus program with exactly this shape, and the reason
     * {@code INCOMPLETE} exists at all: some paths finished, the budget ran out, and nothing was
     * proven about the rest.
     */
    @Test
    void incompleteTrace_emittedWhenOnlyCompletedTracesExist() {
        BenchmarkProgram program = named("peterson");

        int bound = firstBoundWhere(program, null,
            r -> hasOutcome(r, TraceOutcome.INCOMPLETE));
        if (bound < 0) {
            fail("no bound in [0," + MAX_PROBE_BOUND + "] leaves " + program.name()
                + " budget-bounded; R6 needs a program whose budget runs out with no failure");
        }

        DfsResult result = run(program, null, bound);
        assertEquals(1, countOutcome(result, TraceOutcome.INCOMPLETE),
            "exactly one INCOMPLETE per search, at the derived bound " + bound);
        assertFalse(hasOutcome(result, TraceOutcome.VIOLATION),
            "premise: peterson is correct, so no violation may be present");
        assertFalse(hasOutcome(result, TraceOutcome.DEADLOCK),
            "premise: peterson is correct, so no deadlock may be present");
        assertTrue(countOutcome(result, TraceOutcome.COMPLETED) > 0,
            "premise: the whole point of INCOMPLETE is that some paths did complete");
    }

    /**
     * R7 — a run that never exceeds the budget emits no {@code INCOMPLETE}.
     */
    @Test
    void incompleteTrace_absentWhenBudgetNeverExceeded() {
        BenchmarkProgram program = named("peterson");

        int bound = firstBoundWhere(program, null,
            r -> !hasOutcome(r, TraceOutcome.INCOMPLETE));
        if (bound < 0) {
            fail("no bound in [0," + MAX_PROBE_BOUND + "] exhausts " + program.name()
                + " within budget; R7 needs an exhaustive bound to exist");
        }

        DfsResult result = run(program, null, bound);
        assertFalse(hasOutcome(result, TraceOutcome.INCOMPLETE),
            "exhaustiveness at the bound is a pass, not INCOMPLETE");
    }

    /**
     * R8 — an exhaustive run that finds nothing reports a clean pass: {@code COMPLETED} traces and
     * none of the three problem outcomes.
     */
    @Test
    void exhaustiveAtBound_reportsPass_notIncomplete() {
        BenchmarkProgram program = named("peterson");

        int bound = firstBoundWhere(program, null,
            r -> !hasOutcome(r, TraceOutcome.INCOMPLETE));
        if (bound < 0) {
            fail("no exhaustive bound found for " + program.name());
        }

        DfsResult result = run(program, null, bound);
        assertTrue(countOutcome(result, TraceOutcome.COMPLETED) > 0, "a pass is made of completions");
        for (TraceOutcome problem : List.of(TraceOutcome.VIOLATION, TraceOutcome.DEADLOCK,
                TraceOutcome.INCOMPLETE)) {
            assertEquals(0, countOutcome(result, problem),
                "an exhaustive clean run must not report " + problem);
        }
    }

    // --- R9: the no-invariant entry point ---------------------------------------------

    /**
     * R9 — {@code explore(Program)} delegates to the two-argument overload rather than reimplementing
     * anything, so both produce the same search.
     */
    @Test
    void explore_withoutInvariant_delegatesCorrectly() {
        for (BenchmarkProgram program : BugCorpus.all()) {
            DfsResult viaNoArg = new ContextBoundedExplorer().explore(program.program());
            DfsResult viaTwoArg = new ContextBoundedExplorer().explore(program.program(), null);

            assertNotNull(viaNoArg, "explore(Program) must not return null for " + program.name());
            assertEquals(viaTwoArg.statesExplored(), viaNoArg.statesExplored(),
                "explore(Program) must explore the same states as explore(program, null) for "
                    + program.name());
            assertEquals(signature(viaTwoArg), signature(viaNoArg),
                "explore(Program) must produce the same traces as explore(program, null) for "
                    + program.name());
        }
    }

    // --- R10: reachability of the ASSERTION_FAILED branch -----------------------------

    /**
     * R10 — the {@code ASSERTION_FAILED} branch in {@code dfs} (the second {@code VIOLATION} call
     * site) does not execute for any corpus program, at any bound.
     *
     * <p><b>Verdict: not dead code, and now covered.</b> The branch turned out to be reachable
     * after all — {@link #dfsAssertionFailedBranch_executesWhenDeclarativeStepDividesByZero()} is the
     * witness. What follows is the *corpus-scoped* finding, kept because it is what this test
     * measures. Established by instrumentation and then by path analysis, in that order, because the
     * two directions are not symmetric:
     *
     * <ol>
     *   <li><b>Instrumentation.</b> A write placed inside the branch, run across the full suite,
     *       never fired. That is positive evidence of nothing on its own — a silent probe cannot
     *       distinguish "unreachable" from "not exercised", which is why it is only the first step.
     *   <li><b>Path analysis.</b> {@code dfs} has exactly two call sites: the initial call from
     *       {@code explore} and its own recursive call. Both reach the branch only when
     *       {@code step.execute(...)} returns {@code ASSERTION_FAILED}, and that return has exactly
     *       one producer: {@code DynamicStep}. {@code DynamicStep} is constructed only in
     *       {@code DslLoader}, which runs only when a program's {@code format} is
     *       {@code declarative}.
     * </ol>
     *
     * <p>What the path analysis does <em>not</em> establish is that the branch is unreachable. It
     * establishes a single producer, {@code DynamicStep}, on a live feature — not that the producer
     * cannot fire. A well-formed declarative program reaches the branch whenever a step hits a
     * <em>runtime evaluation error</em>: a non-dynamic state, a non-boolean guard, an out-of-range
     * index, or {@code %} by zero. A guard evaluating false is {@code BLOCKED}, the normal path, and
     * not this one.
     *
     * <p>The first draft of this finding asserted the stronger and false version — that no
     * well-formed program can return {@code ASSERTION_FAILED} at all. {@code Parser} admits
     * {@code '%'}, {@code TypeChecker} checks only that both operands are {@code INT}, and
     * {@code Evaluator} throws {@code EvalException("% by zero")} at run time, so
     * {@code local.r = 10 % divisor} over a field pinned to zero type-checks, loads, and trips the
     * branch. That premise was never checked and it was load-bearing: it was the stated reason the
     * region was written down as contingent on the corpus.
     *
     * <p>This test asserts the verdict mechanically rather than by assertion of fact. The
     * {@code VIOLATION} traces the explorer does emit come from the separate invariant check, whose
     * snapshot excludes the step that would have failed — so the absence of
     * {@code ASSERTION_FAILED} in any emitted trace's outcomes is precisely "this branch did not
     * run". If a program that trips a runtime evaluation error ever enters the corpus, this fails
     * and the corpus-scoped verdict must be revisited.
     *
     * <p>Scope this carefully, because it is the claim in this file a reader is most likely to
     * over-generalise. It says the corpus does not reach the branch. It does not say the branch is
     * unreachable — the sibling test above is the counterexample.
     */
    @Test
    void dfsAssertionFailedBranch_neverExecutesForAnyCorpusProgram() {
        int violatingTracesSeen = 0;

        for (BenchmarkProgram program : BugCorpus.all()) {
            for (int k = 0; k <= MAX_PROBE_BOUND; k++) {
                DfsResult result = run(program, program.invariant().orElse(null), k);
                for (Trace trace : result.traces()) {
                    if (trace.outcome() != TraceOutcome.VIOLATION) {
                        continue;
                    }
                    violatingTracesSeen++;
                    assertFalse(trace.outcomes().contains(StepOutcome.ASSERTION_FAILED),
                        "a VIOLATION trace carrying an ASSERTION_FAILED outcome can only come from"
                            + " the dfs step branch, so " + program.name() + " at K=" + k
                            + " reached it; R10's verdict is now stale and this region needs a"
                            + " covering test");
                }
            }
        }

        assertTrue(violatingTracesSeen > 0,
            "sanity: the corpus must produce some invariant VIOLATION traces, otherwise this test"
                + " would pass vacuously");
    }

    /**
     * R10 — the {@code ASSERTION_FAILED} branch is reachable after all, and this is the program that
     * reaches it.
     *
     * <p>The original finding closed with "so a well-formed declarative program never returns
     * {@code ASSERTION_FAILED}". That is false, and the DSL refutes it in three places.
     * {@code Parser} admits {@code '%'} as a multiplicative operator; {@code TypeChecker} checks only
     * that both operands are {@code INT}, and has no notion of a divisor being zero; and
     * {@code Evaluator} throws {@code EvalException("% by zero")} at run time, which
     * {@code DynamicStep.execute} catches and returns as {@code ASSERTION_FAILED}.
     *
     * <p>So the program below is well formed — it type-checks and loads without complaint. At run
     * time its only step divides by a field pinned to zero, the explorer reports the resulting
     * {@code ASSERTION_FAILED} as a {@code VIOLATION} trace, and the branch executes. The assertion
     * is deliberately narrow: a {@code VIOLATION} trace on its own would not prove anything, because
     * the invariant check emits those too. The discriminator is a {@code VIOLATION} trace
     * <em>carrying</em> an {@code ASSERTION_FAILED} outcome, which only the step branch can produce.
     *
     * <p>Built inline rather than added to the corpus on purpose.
     * {@link #dfsAssertionFailedBranch_neverExecutesForAnyCorpusProgram()} measures a live property of
     * the corpus, and adding a program that trips the branch would make that test fail by
     * construction — destroying a real signal to make this one pass. Keeping the witness here also
     * keeps it next to the finding it corrects.
     *
     * <p>This closes the four {@code NO_COVERAGE} mutants at `L176` — measured rather than assumed:
     * the class reports 104/105 with {@code NO_COVERAGE} 0 at this commit. It moves the finding's
     * verdict, not just its reasoning: "not dead code, contingent on the corpus" becomes "reachable,
     * and covered", and the reason changes too, since the original one was a premise that turned out
     * to be false.
     */
    @Test
    void dfsAssertionFailedBranch_executesWhenDeclarativeStepDividesByZero() {
        BenchmarkProgram program = new ProgramLoader().load("""
            {
              "format": "declarative",
              "name": "modulo-by-zero",
              "state": {
                "fields": [
                  {"name": "counter", "type": "int", "init": 0},
                  {"name": "divisor", "type": "int", "init": 0}
                ],
                "locals": [{"name": "r", "type": "int", "init": 0}]
              },
              "threads": [
                {"id": 0, "steps": [{"effects": ["local.r = 10 % divisor"]}]}
              ],
              "invariant": {"expr": "counter == 0"},
              "expected_verdict": "VIOLATION"
            }
            """);

        DfsResult result = run(program, program.invariant().orElse(null), 0);

        List<Trace> violations = result.traces().stream()
            .filter(trace -> trace.outcome() == TraceOutcome.VIOLATION)
            .toList();

        assertFalse(violations.isEmpty(),
            "a step dividing by zero must surface as a VIOLATION trace; " + program.name() + " at K=0"
                + " produced " + result.traces().size() + " trace(s) and none was VIOLATION");
        assertTrue(violations.stream().anyMatch(t -> t.outcomes().contains(StepOutcome.ASSERTION_FAILED)),
            "a VIOLATION trace carrying ASSERTION_FAILED can only originate at the dfs step branch,"
                + " which is the region this test exists to cover; got " + violations);
    }

    // --- R11: determinism -------------------------------------------------------------

    /**
     * R11 — two identical runs produce equal trace sequences, guarding against nondeterminism from
     * iteration over a hash-based collection.
     */
    @Test
    void traces_areDeterministicAcrossRuns() {
        for (BenchmarkProgram program : BugCorpus.all()) {
            Invariant invariant = program.invariant().orElse(null);
            List<String> first = signature(run(program, invariant, 2));
            List<String> second = signature(run(program, invariant, 2));
            assertEquals(first, second,
                "trace emission must be a function of the search, not of hash iteration order, for "
                    + program.name());
        }
    }

    // --- a purpose-built program for R5 -------------------------------------------------

    /**
     * Builds a three-thread program whose threads each raise a flag and then block once any two
     * flags are up.
     *
     * <p>The shape is chosen so a single run is both budget-bounded and deadlocking: the all-flags
     * schedule reaches a deadlock, while schedules that run a thread to completion need more
     * preemptions than a small bound allows. No corpus program has both properties — see
     * {@link #corpusSuppliesNoDeadlockWhileBoundedWitness()}.
     *
     * @return the constructed program
     */
    private static Program threeThreadDeadlockProgram() {
        FlagState initial = new FlagState(new boolean[3]);
        List<ModelThread> threads = new ArrayList<>();
        for (int id = 0; id < 3; id++) {
            threads.add(new ModelThread(id, List.of(new SetFlag(id), new BlockIfTwoFlagsSet())));
        }
        return new Program(initial, threads);
    }

    /** A shared state holding one boolean flag per thread. */
    private static final class FlagState implements SharedState {

        /** One flag per thread, indexed by thread id. */
        private final boolean[] flags;

        /**
         * Wraps a flag array.
         *
         * @param flags the backing array, indexed by thread id
         */
        FlagState(boolean[] flags) {
            this.flags = flags;
        }

        /**
         * Clones the flags, so the successor state shares nothing with this one.
         *
         * @return an independent state over a cloned flag array
         */
        @Override
        public SharedState deepCopy() {
            return new FlagState(flags.clone());
        }

        /**
         * Writes the flags positionally, in thread-id order.
         *
         * @param out the sink to write to
         * @throws IOException if the sink rejects the write
         */
        @Override
        public void encodeTo(DataOutput out) throws IOException {
            for (boolean flag : flags) {
                out.writeBoolean(flag);
            }
        }

        /**
         * Two states are equal when their flags agree position by position.
         *
         * @param other the object to compare against
         * @return true if {@code other} is a {@code FlagState} with the same flags
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof FlagState state && Arrays.equals(flags, state.flags);
        }

        /**
         * @return a hash consistent with {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Arrays.hashCode(flags);
        }

        /**
         * @return the flags as a bracketed list, for readable assertion failures
         */
        @Override
        public String toString() {
            return Arrays.toString(flags);
        }
    }

    /** Raises this thread's own flag and advances. Always enabled. */
    private static final class SetFlag implements Step {

        /** The thread whose flag this step raises. */
        private final int id;

        /**
         * Binds the step to a thread.
         *
         * @param id the thread id whose flag this step sets
         */
        SetFlag(int id) {
            this.id = id;
        }

        /**
         * Reads nothing: the step writes a flag and reads nothing back.
         *
         * @return an empty location set
         */
        @Override
        public Set<MemoryLocation> reads() {
            return Set.of();
        }

        /**
         * The flag lives in the shared state, not in a {@link MemoryLocation}, so this is empty.
         *
         * @return an empty location set
         */
        @Override
        public Set<MemoryLocation> writes() {
            return Set.of();
        }

        /**
         * @param state the current state, unused
         * @return always true — this step is never parked
         */
        @Override
        public boolean enabled(SharedState state) {
            return true;
        }

        /**
         * Raises this thread's flag and advances.
         *
         * @param state the successor state to mutate
         * @return {@link StepOutcome#ADVANCED}
         */
        @Override
        public StepOutcome execute(SharedState state) {
            ((FlagState) state).flags[id] = true;
            return StepOutcome.ADVANCED;
        }
    }

    /**
     * Blocks once two or more flags are up, and terminates otherwise.
     *
     * <p>Blocking rather than terminating is what makes the deadlock real: the thread parks with
     * steps remaining, so the terminal state is "no thread enabled and none finished" rather than
     * "everything finished".
     */
    private static final class BlockIfTwoFlagsSet implements Step {

        /**
         * Reads nothing — the step inspects the flag count, which is not a tracked location.
         *
         * @return an empty location set
         */
        @Override
        public Set<MemoryLocation> reads() {
            return Set.of();
        }

        /**
         * Writes nothing — the step blocks rather than mutating state.
         *
         * @return an empty location set
         */
        @Override
        public Set<MemoryLocation> writes() {
            return Set.of();
        }

        /**
         * Enabled until two flags are up, which is what lets the search park the thread.
         *
         * @param state the current state
         * @return true while fewer than two flags are set
         */
        @Override
        public boolean enabled(SharedState state) {
            return countSet(state) < 2;
        }

        /**
         * Blocks rather than terminating once two flags are up, so the terminal state is a deadlock
         * rather than a clean finish.
         *
         * @param state the successor state, inspected but not mutated
         * @return {@link StepOutcome#BLOCKED} at two flags, otherwise {@link StepOutcome#ADVANCED}
         */
        @Override
        public StepOutcome execute(SharedState state) {
            return countSet(state) < 2 ? StepOutcome.ADVANCED : StepOutcome.BLOCKED;
        }

        /**
         * Counts the raised flags.
         *
         * @param state the state to inspect
         * @return how many threads have raised their flag
         */
        private static int countSet(SharedState state) {
            int set = 0;
            for (boolean flag : ((FlagState) state).flags) {
                if (flag) {
                    set++;
                }
            }
            return set;
        }
    }

    // --- helpers ----------------------------------------------------------------------

    /**
     * Runs a context-bounded search over a corpus program with no explicit store and no visitor.
     *
     * @param program the corpus program to explore
     * @param invariant the invariant to check, or null for none
     * @param bound the preemption bound
     * @return the search result
     */
    private static DfsResult run(BenchmarkProgram program, Invariant invariant, int bound) {
        return new ContextBoundedExplorer().explore(program.program(), invariant, null, null, bound);
    }

    /**
     * Runs a context-bounded search over a directly constructed program.
     *
     * @param program the program to explore
     * @param bound the preemption bound
     * @return the search result
     */
    private static DfsResult run(Program program, int bound) {
        return new ContextBoundedExplorer().explore(program, null, null, null, bound);
    }

    /**
     * Finds the smallest bound in {@code [0, MAX_PROBE_BOUND]} at which {@code predicate} holds.
     *
     * @param program the corpus program to probe
     * @param invariant the invariant to probe with, or null
     * @param predicate the condition defining the bound
     * @return the smallest satisfying bound, or -1 if none does
     */
    private static int firstBoundWhere(BenchmarkProgram program, Invariant invariant,
                                       java.util.function.Predicate<DfsResult> predicate) {
        for (int k = 0; k <= MAX_PROBE_BOUND; k++) {
            if (predicate.test(run(program, invariant, k))) {
                return k;
            }
        }
        return -1;
    }

    /**
     * @param result a search result
     * @param outcome the outcome to look for
     * @return true if any trace has that outcome
     */
    private static boolean hasOutcome(DfsResult result, TraceOutcome outcome) {
        return result.traces().stream().anyMatch(t -> t.outcome() == outcome);
    }

    /**
     * @param result a search result
     * @param outcome the outcome to count
     * @return how many traces have that outcome
     */
    private static int countOutcome(DfsResult result, TraceOutcome outcome) {
        return (int) result.traces().stream().filter(t -> t.outcome() == outcome).count();
    }

    /**
     * @param traces the traces to search
     * @param candidate the instance to look for
     * @return true if the same instance is present, by reference rather than by equality
     */
    private static boolean containsIdentical(List<Trace> traces, Trace candidate) {
        for (Trace trace : traces) {
            if (trace == candidate) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renders a result's traces as comparable strings, since {@code Trace} does not define
     * {@code equals}.
     *
     * @param result a search result
     * @return one string per trace, in emission order
     */
    private static List<String> signature(DfsResult result) {
        List<String> out = new ArrayList<>();
        for (Trace trace : result.traces()) {
            out.add(String.valueOf(trace.outcome()) + trace.threadIds() + trace.outcomes());
        }
        return out;
    }

    /**
     * @param name the corpus program name
     * @return that program
     * @throws IllegalArgumentException if no such corpus program exists
     */
    private static BenchmarkProgram named(String name) {
        for (BenchmarkProgram program : BugCorpus.all()) {
            if (program.name().equals(name)) {
                return program;
            }
        }
        throw new IllegalArgumentException("no such corpus program: " + name);
    }

    /**
     * Records every {@code onTraceCreated} callback, keeping the instances by reference so identity
     * can be asserted.
     */
    private static final class RecordingVisitor implements StateVisitor {

        /** Every trace reported so far, held by reference so identity can be asserted. */
        private final List<Trace> traces = new ArrayList<>();

        /**
         * Records nothing, and does so deliberately: this spec is about trace notification, so a
         * state callback here would be dead weight that reads like an oversight.
         *
         * @param config the configuration just visited, unused
         */
        @Override
        public void onStateVisited(Configuration config) {
            // Nothing to record; this spec is about trace notification, not state notification.
        }

        /**
         * Keeps the reported instance rather than a copy of it.
         *
         * @param trace the trace just created
         */
        @Override
        public void onTraceCreated(Trace trace) {
            traces.add(trace);
        }

        /**
         * @return the traces reported to this visitor, in callback order
         */
        List<Trace> traces() {
            return traces;
        }

        /**
         * @return how many traces were reported to this visitor
         */
        int traceCount() {
            return traces.size();
        }
    }
}