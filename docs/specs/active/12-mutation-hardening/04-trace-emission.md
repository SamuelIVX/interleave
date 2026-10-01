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

**Two dark paths:**
- `L41` — `explore(Program)` delegating to `explore(program, null)`. Two `NO_COVERAGE` mutants, so
  the no-invariant entry point is **never called by any test**.
- `L176` — inside `dfs`, four `NO_COVERAGE` mutants. The survey reports this region as a list copy
  plus a `Trace.of` construction; exact reachability is **not** established and §R6 makes
  establishing it a requirement rather than assuming either way.

**Mutation inventory — 105 mutants, 86 killed (81.9%), 19 not killed:**

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
10. **THE SYSTEM SHALL** determine, by instrumentation or by tracing every caller, whether the `dfs`
    region at `L176` is reachable. If unreachable it SHALL be documented as dead with the evidence; if
    reachable it SHALL be covered by a test that reaches it.
11. **THE SYSTEM SHALL** emit traces deterministically — two identical runs SHALL produce equal trace
    sequences, guarding against hash-iteration nondeterminism.

## Acceptance Criteria

- [ ] A `StateVisitor` test records every `onTraceCreated` call and asserts the count equals
      `getTraces().size()` (R1, R3).
- [ ] The R3 test asserts reference identity (`assertSame`), not `equals` (R3).
- [ ] A test passes `null` as the visitor and asserts `getTraces()` is still populated (R2).
- [ ] `L214`'s mutant is killed. **Falsification:** delete the `onTraceCreated` call, confirm the
      visitor test goes red **while a `getTraces()`-based assertion in the same test stays green**,
      then revert. The divergence is the point — if the `getTraces()` assertion also goes red, the
      test is not demonstrating what this spec exists to pin.
- [ ] R4–R8 each have a named test using a corpus program that produces the required trace mix
      (R4–R7).
- [ ] `explore(Program)` is called by at least one test and both `L41` mutants become `KILLED` (R9).
- [ ] The `L176` reachability verdict is recorded in §Current State as `[verified]` with the
      instrumented method or the caller enumeration that established it (R10).
- [ ] A determinism test runs the same program twice and asserts equal trace sequences (R11).
- [ ] `./gradlew clean test javadoc` passes.
- [ ] `./gradlew pitest` shows `L41`, `L203`, `L204`, `L214` `KILLED`.

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
guessing wrong is costly:

- **Unreachable** → dead code. Document it; consider deletion under `AGENTS.md` §2 (needs sign-off).
- **Reachable but untested** → a real coverage gap, and it sits on a path the search can take.

Establish it by instrumenting the region with a counter or throw, running the full corpus plus the
`Strategy.CONTEXT_BOUNDED` CI job's own invocation, and observing whether it fires. That is evidence.
Inferring from "no test covers it" confuses *untested* with *unreachable*, which is the mistake this
requirement exists to prevent.

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

## Constraints

- **Dependencies:** none hard. Spec 12.05 will change the corpus, so if 12.05 lands first, the
  derived bounds in R4–R6 must be re-derived — the derivation-in-test requirement exists precisely so
  that surfaces as a failure.
- **Backward compatibility:** no signature changes. `addTrace` is private.
- **Test-only.** If R10 finds the `L176` region unreachable, deleting it is a production change and
  needs Sam's sign-off per `AGENTS.md` §2 — and its own PR.
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