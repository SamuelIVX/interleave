# Spec 12.04 — CBS Trace Emission & Reporting

## TL;DR

`ContextBoundedExplorer` populates its `traces` list and *also* calls
`StateVisitor.onTraceCreated(...)` in `addTrace`. **Dropping the callback call still leaves
`getTraces()` fully populated**, so any test asserting on the returned list stays green while a
caller using the visitor interface silently stops learning about traces. That is the exact
false-confidence shape: the observable stays right and the notification disappears. Three further
survivors sit in the `INCOMPLETE`-suppression rule, and six `NO_COVERAGE` mutants mean two code
paths (`explore` entry and a `dfs` helper) are never executed by any test.

## Objective

Make trace *notification* as pinned as trace *content*, cover the `INCOMPLETE` suppression rule, and
determine whether the two dark paths are dead code or untested code — because those demand opposite
responses.

## Scope

- **Package:** `interleave` / `src/main/java/dev/samhb/interleave/cb` and `.../search`
- **Modifies:** adds `src/test/java/dev/samhb/interleave/cb/ContextBoundedTraceEmissionTest.java`
- **Off-limits:** the pruning/dominance logic — Spec 12.05 owns it. `TraceOutcome` propagation into
  `VerificationResult` / `InterleaveRunner` / `DeltaDebugger` — Spec 11.07 shipped that.
  `Trace` itself — Spec 11.05 owns its constructor invariants.

## Non-Goals

- Changing *what* a trace contains. Spec 11.05 defines the `INCOMPLETE` trace shape and the
  prune-site snapshot; this spec tests that the shape reaches both consumers.
- Making `INCOMPLETE` and `limitExceeded` coexist. Spec 11.05 §Invariants records that as a
  deliberate, pinned non-delivery (`partialResult_limitedRun_reportsLimitAndNoIncomplete`).
- Adding a new verdict or touching the four `TraceOutcome` switch sites.

## Current State

All claims [verified] by reading `cb/ContextBoundedExplorer.java` (217 lines) and the mutation report.

**`addTrace` — the load-bearing method for this spec** (`L211–216`):

```java
private void addTrace(Trace trace, StateVisitor stateVisitor) {
    traces.add(trace);
    if (stateVisitor != null) {
        stateVisitor.onTraceCreated(trace);   // L214 — mutating this leaves every other assertion green
    }
}
```

**The `INCOMPLETE` suppression rule** (`emitIncompleteTraceIfNeeded`, `L198–209`) checks whether any
emitted trace is already `VIOLATION` or `DEADLOCK` via `Stream::anyMatch` and two
`Trace.outcome() ==` comparisons (`L203`, `L204`). The rule: *if the run found a real failure, do not
also report `INCOMPLETE`*, because the pruned region is irrelevant once there is something to
reproduce.

**Two dark paths — the survey as it stood at `c5fdcd0`, before this spec.** Read the results below
before acting on either: both were closed, and neither reads as a live finding any more.
- `L41` — `explore(Program)` delegating to `explore(program, null)`. Two `NO_COVERAGE` mutants, so
  the no-invariant entry point was **never called by any test**. **Now `KILLED` ×2** (R9).
- `L176` — inside `dfs`, four `NO_COVERAGE` mutants. The survey reports this region as a list copy
  plus a `Trace.of` construction. It is the `ASSERTION_FAILED` branch, **not** the invariant check at
  `:120–123`, so it is reached only by a step that *returns* `ASSERTION_FAILED` — which only
  `DynamicStep` does. **R10 settled it: reachable, and now `KILLED` ×4.**

**Result of this spec's implementation — 105 mutants, 104 killed (99.0%), 1 not killed.**
Three different baselines are in play in this document and conflating them is how the wrong number
gets quoted, so they are named:

| baseline | figure | what it is |
|---|---|---|
| `c5fdcd0`, this class | **86/105 (81.9%)** | the pre-spec inventory below; stale for planning |
| `main` at this spec, this class | **94/105 (89.5%)** | measured on `main` before this spec — the real starting point |
| `main` at this spec, full scope | **246/270 (91.1%)** | whole-scope starting point, `NO_COVERAGE` 6 |

This spec takes the class to **104/105 (99.0%)** and the full scope to **256/270 (94.8%)**,
`NO_COVERAGE` **0**, test strength 95%, with **no regressions**. All ten mutant variants across this
spec's five named locations are `KILLED`: `L41` ×2, `L176` ×4, `L203` ×2, `L204`, `L214`. The single
survivor in this class is `L187`, which belongs to Spec 12.05.

`NO_COVERAGE` reaching **0** across the whole scope is the part worth stating plainly, because it is
the first time that has been true in this set. Before this spec the class carried four uncovered
mutants at `L176`; the witness program added for R10
(`dfsAssertionFailedBranch_executesWhenDeclarativeStepDividesByZero`) kills all four, so no mutant in
`state.*` or `cb.*` is now uncovered for want of a test.

**Original mutation inventory (at `c5fdcd0`) — 105 mutants, 86 killed (81.9%), 19 not killed:**

| line | symbol | mutator | status |
|---|---|---|---|
| L41 | `explore(Program)` | `NON_VOID_METHOD_CALLS`, `NULL_RETURNS` | **NO_COVERAGE** ×2 |
| L82 | `dfs` — store-capacity read | `NON_VOID_METHOD_CALLS` | SURVIVED (Spec 12.05) |
| L87 | `dfs` — visitor call | `VOID_METHOD_CALLS` | SURVIVED (Spec 12.05) |
| L88 | `dfs` — visitor call | `VOID_METHOD_CALLS` | SURVIVED (Spec 12.05) |
| L113 | `dfs` — visited-key construction | `NON_VOID_METHOD_CALLS` ×5 | SURVIVED (Spec 12.05) |
| L176 | `dfs` helper | `NON_VOID_METHOD_CALLS` ×3, `VOID_METHOD_CALLS` | **NO_COVERAGE** ×4 |
| L187 | `dfs` | `NON_VOID_METHOD_CALLS` | SURVIVED (Spec 12.05) |
| L203 | `emitIncompleteTraceIfNeeded` — `anyMatch` | `NON_VOID_METHOD_CALLS` ×2 | SURVIVED ×2 |
| L204 | `emitIncompleteTraceIfNeeded` — outcome compare | `NON_VOID_METHOD_CALLS` | SURVIVED |
| L214 | `addTrace` — `onTraceCreated` | `VOID_METHOD_CALLS` | SURVIVED |

Totals: 13 `SURVIVED` + 6 `NO_COVERAGE` = 19. `L203`, `L204` and `L187` each sit on a line that
**also** carries killed mutants, so a line-grouped inventory that reports only the dominant status
undercounts by four.

`L82`, `L87`, `L88`, `L113` and `L187` belong to Spec 12.05's pruning/dominance surface; this spec
takes `L41`, `L176`, `L203`, `L204`, `L214`. Note `L87`/`L88` are also visitor calls — Spec 12.05
adjudicates those as pruning-side, this spec owns only the `addTrace` path, and neither spec may
claim the other's mutants.

## R10 Verdict — `L176` is **reachable**, and now covered

**[verified]** by instrumentation *and* by path analysis, in that order, because the two directions
are not symmetric. A firing counter proves reachability; a silent one proves nothing.

**1. Instrumentation.** A write placed inside the branch, run across the whole suite (**445 tests**,
including the DSL suites), never fired — the file was never created. That establishes only that *the
executions run so far* did not reach it.

**2. Path analysis.** `dfs` has exactly two call sites: the initial call from `explore` and its own
recursive call. Both reach `L176` only when `step.execute(...)` returns `ASSERTION_FAILED`, and that
return has exactly one producer, `DynamicStep` — every other occurrence of the constant in
`src/main/java` is either the enum declaration or a comparison against it. `DynamicStep` is
constructed in one place, `DslLoader.java:205`, which runs only when a program's `format` is
`declarative` (`ProgramLoader:194`).

**One of this spec's stated premises is wrong, and it matters.** The prior recorded above says "no
corpus program is declarative — all seven are `typed` [verified]". That is false:
`programs/lost-update-3t.json` declares `"format": "declarative"` and is loaded into `BugCorpus`
(`BugCorpus.java:62`). The premise being wrong does not make the region reachable, but it removes the
reason it was thought to be dead.

**Why the corpus does not exercise it.** `DynamicStep.execute` returns `ASSERTION_FAILED` for a
*runtime evaluation error* — a non-`DynamicState` state, a guard that does not evaluate to `BOOL`, an
out-of-range index, an `EvalException`. A guard evaluating **false** is `BLOCKED`, which is the normal
path. Instrumenting the branch across the full suite, the tested corpus produces no
`ASSERTION_FAILED` outcome at all.

**But "the corpus does not" is not "it cannot", and the first draft of this finding had it backwards.**
It concluded that a well-formed declarative program never returns `ASSERTION_FAILED`. That is false,
and the DSL refutes it in three places: `Parser` admits `%` as a multiplicative operator
(`Parser.java:261`); `TypeChecker` checks only that both operands are `INT`, with no notion of a
divisor being zero (`TypeChecker.java:61`); and `Evaluator` throws `EvalException("% by zero")` at run
time (`Evaluator.java:131`), which `DynamicStep.execute` catches and returns as `ASSERTION_FAILED`. A
program of `local.r = 10 % divisor` over a field pinned to zero type-checks, loads without complaint,
and trips the branch on its only step.

**Which makes the region reachable, and under R10 that obliges a covering test rather than a
verdict.** `dfsAssertionFailedBranch_executesWhenDeclarativeStepDividesByZero` builds precisely that
program and asserts a `VIOLATION` trace carrying an `ASSERTION_FAILED` outcome. The assertion is
narrow on purpose: a `VIOLATION` trace alone would prove nothing, because the invariant check emits
those too — the discriminator is one *carrying* `ASSERTION_FAILED`, which only the step branch can
produce. That kills all four `NO_COVERAGE` mutants, and this class now reports `NO_COVERAGE` 0.

The witness is built in-test rather than added to the corpus deliberately.
`dfsAssertionFailedBranch_neverExecutesForAnyCorpusProgram` measures a live property of the corpus, so
a corpus program that trips the branch would fail it by construction — destroying a real signal to make
this one pass. That test stays useful: if such a program ever enters the corpus, it fails, and the
failure message says R10 needs revisiting.

## Two findings worth carrying forward

**R5 has no corpus witness, and the reason is structural.** The requirement asks for a run that exceeds
the budget *and* deadlocks. No corpus program is ever both, and the cause is an observability limit
rather than an accident: boundedness is only visible while nothing suppresses `INCOMPLETE`, so a
program that deadlocks can never *report* that it was also bounded — the property under test is what
hides its own premise. `corpusSuppliesNoDeadlockWhileBoundedWitness` pins that over the corpus, and
R5 is tested against a purpose-built three-thread program instead, with boundedness established by
state growth (7 states at K=0 against 9 at K=1 — the bound demonstrably binds). That is what kills
`L204`, the `DEADLOCK` comparison, which no `VIOLATION`-based test can reach.

**Per-class kill counts do not depend on PIT scope — verified, after being assumed wrong.** This spec
originally recorded that a `-PpitestTargetOverride` run reports a *different* kill count for the same
class, on the stated grounds that scoping changes covering-test selection. That was never tested and it
is false: scoping leaves `targetTests` untouched, so coverage and kills are unchanged. Measured both ways
on `ContextBoundedExplorer` at this commit — target-scoped **104/105** with `NO_COVERAGE` 0,
full-scope **104/105** with `NO_COVERAGE` 0. All four classes in the README table agree the same
way.

What scoping *does* change is the denominator, so a scoped run's percentage is not comparable to a
full-scope percentage. The real hazard was different and far more ordinary: the `86/105` above was
measured at `c5fdcd0` and had gone stale as sibling specs landed, so the class was really at `94/105`
before this spec began. That is a provenance problem, not a scope problem, which is why `AGENTS.md`
leads with "measure the baseline on `main`" and says nothing about scope.

Use a scoped run to iterate on one class — it is much faster and the per-class numbers are trustworthy.
Quote full-scope totals when the number being reported is a total.

## Invariants

- **Every trace in `traces` has been offered to the visitor.** `addTrace` SHALL invoke
  `onTraceCreated` for each trace it records, unless no visitor was supplied. The two lists are not
  independently maintained: the visitor callback is the *only* notification path for callers using
  `StateVisitor`, and `getTraces()` is a convenience for tests.
- **A trace reaching `addTrace` is reported at most once**, and the visitor sees the identical object
  that lands in `traces` — not an equal copy.
- **A null visitor is legal** and suppresses notification without affecting `traces`.
- **`INCOMPLETE` is suppressed when, and only when, a `VIOLATION` or `DEADLOCK` trace already
  exists.** A run that found a real failure reports that failure and says nothing about the pruned
  region.
- **`INCOMPLETE` is not suppressed by `COMPLETED` traces** — completing some paths while exceeding
  budget is exactly the case `INCOMPLETE` exists for.
- **A run that neither violates nor exceeds budget SHALL NOT emit `INCOMPLETE`.** Exhaustiveness at
  the bound is `PASS`, not `INCOMPLETE`.
- **Trace emission is a function of the search, not of iteration order** over any hash-based
  collection.

## Requirements

1. **WHEN** a trace is added and a non-null `StateVisitor` is supplied, **THE SYSTEM SHALL** invoke
   `onTraceCreated` exactly once with the identical `Trace` instance.
2. **WHEN** a trace is added and the visitor is `null`, **THE SYSTEM SHALL** still record it in
   `traces` and SHALL NOT throw.
3. **WHEN** a search emits N traces with a visitor attached, **THE SYSTEM SHALL** report N visitor
   callbacks whose instances are reference-identical to those in `getTraces()`.
4. **WHEN** a run exceeds the preemption budget **and** has emitted a `VIOLATION` trace,
   **THE SYSTEM SHALL** emit no `INCOMPLETE` trace.
5. **WHEN** a run exceeds the preemption budget **and** has emitted a `DEADLOCK` trace,
   **THE SYSTEM SHALL** emit no `INCOMPLETE` trace.
6. **WHEN** a run exceeds the preemption budget with only `COMPLETED` traces, **THE SYSTEM SHALL**
   emit exactly one `INCOMPLETE` trace.
7. **WHEN** a run does not exceed the budget, **THE SYSTEM SHALL** emit no `INCOMPLETE` trace.
8. **WHEN** a run does not exceed the budget and finds no violation, **THE SYSTEM SHALL** report
   `PASS`, not `INCOMPLETE`.
9. **THE SYSTEM SHALL** exercise `explore(Program)` — the no-invariant entry point — in at least one
   test, establishing that it delegates correctly and does not NPE.
10. **THE SYSTEM SHALL** determine whether the `dfs` region at `L176` is reachable, and SHALL record
    which of these two it concluded:

    - **Instrumentation shows the region executes** → it is reachable, and SHALL be covered by a
      test that reaches it. A firing counter is positive evidence.
    - **Instrumentation shows it does not execute** → this establishes only that *the executions run
      so far* did not reach it. It SHALL NOT on its own support an unreachability verdict, because a
      finite run cannot cover the input space. An unreachability verdict SHALL be supported by
      **complete caller and path analysis**: every call site of `dfs`, every path from each to `L176`,
      and the precondition each path would have to satisfy without satisfying.

    The two directions are not symmetric. Instrumentation is a sound way to prove *reachability* and
    an unsound way to prove *unreachability*. Getting that backwards is how a live path gets
    documented as dead and then deleted.
11. **THE SYSTEM SHALL** emit traces deterministically — two identical runs SHALL produce equal trace
    sequences, guarding against hash-iteration nondeterminism.

## Acceptance Criteria

- [x] A `StateVisitor` test records every `onTraceCreated` call and asserts the count equals
      `getTraces().size()` (R1, R3).
- [x] The R3 test asserts reference identity (`assertSame`), not `equals` (R3).
- [x] A test passes `null` as the visitor and asserts `getTraces()` is still populated (R2).
- [x] `L214`'s mutant is killed. **Falsification:** delete the `onTraceCreated` call, confirm the
      visitor test goes red **while a `getTraces()`-based assertion in the same test stays green**,
      then revert. The divergence is the point — if the `getTraces()` assertion also goes red, the
      test is not demonstrating what this spec exists to pin.
- [x] R4–R8 each have a named test using a corpus program that produces the required trace mix
      (R4–R7).
- [x] `explore(Program)` is called by at least one test and both `L41` mutants become `KILLED` (R9).
- [x] The `L176` reachability verdict is recorded in §Current State as `[verified]` with the
      instrumented method or the caller enumeration that established it (R10).
- [x] A determinism test runs the same program twice and asserts equal trace sequences (R11).
- [x] `./gradlew clean test javadoc` passes.
- [x] `./gradlew pitest` shows `L41`, `L203`, `L204`, `L214` `KILLED`.

## Design

### R1/R3 — the asymmetry that makes this spec necessary

`addTrace` writes to two places: a list it owns, and a callback it does not. Mutating the callback
changes one and not the other:

| | `traces` populated | visitor notified |
|---|---|---|
| correct | yes | yes |
| **`onTraceCreated` removed** | **yes** | **no** |
| `traces.add` removed | no | yes |

Both single-line mutants are plausible defect shapes, and only the second row is the dangerous one —
it degrades a *notification* while every *observation* stays correct. That asymmetry is why the test
must assert on the visitor, and why the falsification check specifically verifies the
`getTraces()` assertion stays green.

### R4–R6 — building the trace mix the suppression rule needs

The suppression rule's three branches each need a program whose trace set has the right shape:

| requirement | needed trace set | approach |
|---|---|---|
| R4 (suppressed by `VIOLATION`) | `VIOLATION` present, budget exceeded | a corpus buggy program at a low bound — `broken-peterson` or `lost-update` at K=1 |
| R5 (suppressed by `DEADLOCK`) | `DEADLOCK` present, budget exceeded | `deadlock` at a bound that also exceeds budget |
| R6 (not suppressed by `COMPLETED`) | only `COMPLETED`, budget exceeded | `peterson` at K=1 — correct program, bounded, so the budget runs out with no failure |

`peterson` at K=1 is the natural R6 case and is worth naming explicitly: it is the one corpus program
whose bounded run produces exactly the "completed some paths, exceeded budget" shape, which is the
entire reason `INCOMPLETE` exists.

Because (R4) and (R5) depend on a bound at which the budget *is* exceeded, do not hardcode the bound
from today's corpus. Derive it the way PR #29's tests did — search for the smallest K at which the
run is bounded without losing the required verdict, and assert the derivation in the test so a corpus
change surfaces as a failure rather than a silent skip.

### R10 — reachability, not assumption

`L176` has four `NO_COVERAGE` mutants. Two responses are correct depending on what is true, and
guessing wrong is costly. Note the asymmetry in what "wrong" costs: the false-*reachable* verdict
leaves a coverage gap open, while the false-*unreachable* verdict marks a live branch dead and
proposes deleting it. R10's evidence bar is set accordingly.

- **Unreachable** → dead code. Document it; consider deletion under `AGENTS.md` §2 (needs sign-off).
- **Reachable but untested** → a real coverage gap, and it sits on a path the search can take.

Establish the reachable direction by instrumenting the region with a counter or throw, running the
full corpus plus the `Strategy.CONTEXT_BOUNDED` CI job's own invocation, and observing whether it
fires. **A firing counter settles reachability; a silent counter settles nothing** and must be
escalated to the path analysis above rather than recorded as "unreachable". Inferring from "no test
covers it" confuses *untested* with *unreachable*, which is the mistake this requirement exists to
prevent — and the silent-instrumentation trap is the same error wearing a lab coat, because it looks
like evidence.

A prior signal is worth recording before the work starts — and it is weaker than it first looks, so it
is stated as the open question R10 has to settle rather than as an answer.

`L176` is the `VIOLATION` `addTrace` call inside `if (outcome == StepOutcome.ASSERTION_FAILED)`. It is
**not** the invariant check. `ContextBoundedExplorer` evaluates the invariant separately, at
`:120–123`, and emits its own `VIOLATION` trace from there — a different call site. So a buggy
program producing a violation does not, by itself, reach `L176` [verified].

The branch requires a step to *return* `ASSERTION_FAILED`, and that return has exactly one producer:
`DynamicStep` [verified — the only other occurrences of the constant outside `StepOutcome` itself are
the five explorers comparing against it]. `DynamicStep` is used only for programs with
`format: "declarative"`, dispatched at `ProgramLoader:175`. **No program in the current corpus is
declarative** — all seven are `typed` [verified], and no `typed` step returns `ASSERTION_FAILED`.

So the honest prior is: **reachability is unestablished, not established.** It turns on whether any
declarative program exists or will exist — the DSL is a live feature (Specs 09–10, with
`DslLoaderTest`, `DslEquivalenceTest`, and `DslInvariantTest`), so the producer is real and reachable
in principle; only the corpus currently has no program that drives it. R10 SHALL establish which of
these holds, and SHALL NOT delete the region on the strength of a silent counter. Two findings follow
directly and belong in the record:

- The five invariant-bearing programs named above reach the **`:120–123`** VIOLATION path under CBS,
  so they are the right corpus for R4/R5/R6 — and the wrong evidence for `L176`.
- If a declarative program is ever added to the corpus, `L176` becomes reachable and these four
  `NO_COVERAGE` mutants become a genuine gap. Recording that dependency is part of R10's output.

  **Superseded — the dependency was not the operative one.** `L176` turned out to be reachable with no
  corpus change at all, and all four mutants are now killed, so the conditional describes a gap that no
  longer exists. The finding is left in place because the reasoning error it records is the reason the
  spec now carries a witness test instead of an argument; see §R10 Verdict.

## Tests

**File:** `src/test/java/dev/samhb/interleave/cb/ContextBoundedTraceEmissionTest.java`

- `addTrace_notifiesVisitor_withIdenticalTraceInstance` (R1, R3) — `assertSame`, and asserts count
  parity with `getTraces()`
- `addTrace_nullVisitor_stillRecordsTrace` (R2)
- `explore_reportsEveryTraceToVisitor` (R3) — full-search parity, not a single add
- `incompleteTrace_suppressedWhenViolationFound` (R4)
- `incompleteTrace_suppressedWhenDeadlockFound` (R5)
- `incompleteTrace_emittedWhenOnlyCompletedTracesExist` (R6) — `peterson` at a derived bound
- `incompleteTrace_absentWhenBudgetNeverExceeded` (R7)
- `exhaustiveAtBound_reportsPass_notIncomplete` (R8)
- `explore_withoutInvariant_delegatesCorrectly` (R9) — the `L41` mutant-killing test
- `traces_areDeterministicAcrossRuns` (R11)
- `dfsHelperRegion_reachability` (R10) — asserts the recorded verdict; if the region turns out to be
  reachable this becomes a covering test, if not it asserts the documented absence

**Falsification check (not committed):** remove the `onTraceCreated` call from `addTrace`; confirm the
visitor assertion fails and the `getTraces()` assertion in the same test passes; revert.

### As implemented

Thirteen tests, all named in the file: `addTrace_notifiesVisitor_withIdenticalTraceInstance` (R1, R3),
`explore_reportsEveryTraceToVisitor` (R3), `addTrace_nullVisitor_stillRecordsTrace` (R2),
`incompleteTrace_suppressedWhenViolationFound` (R4), `incompleteTrace_suppressedWhenDeadlockFound` (R5),
`corpusSuppliesNoDeadlockWhileBoundedWitness` (R5, the corpus-gap pin),
`incompleteTrace_emittedWhenOnlyCompletedTracesExist` (R6),
`incompleteTrace_absentWhenBudgetNeverExceeded` (R7),
`exhaustiveAtBound_reportsPass_notIncomplete` (R8), `explore_withoutInvariant_delegatesCorrectly` (R9),
`dfsAssertionFailedBranch_neverExecutesForAnyCorpusProgram` (R10, corpus-scoped),
`dfsAssertionFailedBranch_executesWhenDeclarativeStepDividesByZero` (R10, the witness),
`traces_areDeterministicAcrossRuns` (R11).

Three names differ from the plan above, each to say what it asserts: the corpus-scoped R10 test is
named for the branch it watches rather than `dfsHelperRegion_reachability`; R5 needed a second test
because the corpus cannot supply its witness; and R10 ended up needing a *third*, because the witness
proving the branch reachable could not live in the corpus without failing the corpus-scoped test.
No test is skipped or disabled.

**Falsification result (performed, production restored byte-identical).** Disabling the
`onTraceCreated` call:

| test | outcome |
|---|---|
| `addTrace_notifiesVisitor_withIdenticalTraceInstance` | **RED** — `expected: <5> but was: <0>` |
| `explore_reportsEveryTraceToVisitor` | **RED** — `expected: <4> but was: <0>` |
| `addTrace_nullVisitor_stillRecordsTrace` | green |
| the other nine | green |

The numbers are the whole point: `getTraces()` still held **5** traces while the visitor recorded
**0**, so every observation through the returned list stayed correct while the notification vanished.
Stated precisely, since it is easy to overclaim: the assertion that turns red is the
`getTraces().size()` versus `visitor.traceCount()` comparison. The
`assertFalse(result.traces().isEmpty())` before it stays green, and the identity loop after it is
**vacuous** under this mutation because the visitor receives nothing to iterate — so the identity
check is not what detects the defect, and the divergence is narrower than "the list assertions stay
green" suggests. It is still the divergence this spec exists to pin, and it is only visible because
the list check and the count check sit in one test.

## Constraints

- **Dependencies:** none hard. Spec 12.05 will change the corpus, so if 12.05 lands first, the
  derived bounds in R4–R6 must be re-derived — the derivation-in-test requirement exists precisely so
  that surfaces as a failure.
- **Backward compatibility:** no signature changes. `addTrace` is private.
- **Test-only.** If R10 finds the `L176` region unreachable, deleting it is a production change and
  needs Sam's sign-off per `AGENTS.md` §2 — and its own PR. **Deletion additionally requires
  establishing that no declarative program can reach the branch**, not merely that today's corpus does
  not: the DSL is a supported feature (Specs 09–10), so the producer is live code and "no corpus
  program is declarative" is a fact about the corpus, not a property of the branch.
- **Do not weaken the pinned `partialResult_limitedRun_reportsLimitAndNoIncomplete` test.** It pins a
  deliberate Spec 11.05 non-delivery. Making the two coexist is a separate, larger change.

## Commands

```bash
./gradlew test --tests "*ContextBoundedTraceEmission*"
./gradlew test --tests "*ContextBounded*"
./gradlew pitest
./gradlew clean test javadoc
```

## Map

- `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java` — `addTrace`, `emitIncompleteTraceIfNeeded`, `explore`, `dfs`
- `src/main/java/dev/samhb/interleave/search/StateVisitor.java` — the callback contract
- `src/main/java/dev/samhb/interleave/search/Trace.java` — `Trace.of`, constructor invariants (Spec 11.05)
- `src/test/java/dev/samhb/interleave/ContextBoundedApiTest.java` — existing CBS tests, including the
  pinned non-delivery `partialResult_limitedRun_reportsLimitAndNoIncomplete`
- `src/test/java/dev/samhb/interleave/cb/CbsDifferentialTest.java` — verdict agreement against exhaustive DFS
- `src/test/java/dev/samhb/interleave/cb/CbsMonotonicityTest.java` — monotonicity in the bound
- `docs/specs/active/11-context-bounded/05-incomplete-verdict.md` — the shipped `INCOMPLETE` spec
