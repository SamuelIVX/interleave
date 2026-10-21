# Spec: Single-emission CBS scheduling witnesses

## Objective

Close the remaining CBS mutation gap in roadmap 9-H2 by protecting the source/legacy
scheduling-witness boundary. A source witness emitted at the preemption prune site must not
be emitted again by the deferred legacy-reporting path after search completes.

## Scope

- Package: CBS regression coverage in `dev.samhb.interleave.format.SourceCompletenessTest`.
- Modifies: that test class, this specification and the future-work roadmap.
- Off-limits: production explorers, stores, trace formats, dependencies, CI and mutation gates.

## Non-Goals

The 13 inherited state-package survivors remain separate work. This PR does not change
search semantics, implement new reductions, or remeasure the broader 9-V1 mutation population.

## Invariants

- Source scheduling witnesses are emitted immediately to survive interrupted searches.
- Legacy scheduling witnesses retain their existing deferred emission and failure suppression.
- A trace reported to a visitor is the trace returned in the search result.
- Actual incomplete schedules remain replayable against the model and its invariant.

## Requirements

1. WHEN a source CBS search both completes schedules and exceeds its preemption bound,
   THE SYSTEM SHALL return exactly one PREEMPTION_BOUND witness for the first omitted choice.
2. WHEN that witness is emitted, THE SYSTEM SHALL notify the visitor exactly once for it;
   the deferred legacy path SHALL add no duplicate witness or callback.
3. WHEN validating the witness, THE SYSTEM SHALL replay its authentic prefix with the invariant
   and retain the expected committed state and source identity.

## Acceptance Criteria

- The named regression uses a real bounded source model with no violation or deadlock, so
  failure suppression cannot mask an erroneous deferred emission.
- Removing `Program.isSource` from `emitIncompleteTraceIfNeeded` causes an assertion failure.
- Fresh scoped CBS PIT improves without changing semantic identities or losing baseline kills.
- Clean build, full tests, strict Javadoc, default PIT/ratchet and visualizer normalization pass.
- CodeRabbit CLI completes review, with valid findings independently verified and addressed.

## Design

Use two source threads, each with two atomic increments, K=0 and an always-true invariant.
Collect `onTraceCreated` events without modifying the model. Assert both completed schedules
and exactly one incomplete trace, compare result/visitor sequences, and replay the incomplete
prefix with its invariant. Existing interruption, failure-suppression and legacy regressions stay.

## Current State

- [verified] Baseline is merged PR #58, main commit `817f6ce`.
- [verified] The source prune site emits the first scheduling witness immediately. The deferred
  emission helper skips source programs; without that guard a failure-free source search can emit twice.
- [verified] Fresh baseline passes 650 tests, none skipped, with scoped CBS PIT at 122/123
  assertion-confirmed kills, one survivor, zero uncovered and zero timeout/error detections.

## Tests

`SourceCompletenessTest.sourceSchedulingWitnessIsEmittedOnceAfterSuccessfulSearch` covers R1–R3.
Keep the existing loop-deduplication, resource-interruption and legacy CBS tests unchanged.

## Constraints

- Dependencies: shipped Spec 16 source-witness contracts and merged replay-hardening Spec 17.
- Backward compatibility: no public API, production behavior or wire-format changes.
- Local Gradle commands use `--max-workers=1 -PpitestThreads=1`.
- Semantic mutation identity uses class, method descriptor, operator, instruction and block;
  line numbers are diagnostic only.

## Verification

The focused source-completeness suite passes. Scoped CBS PIT improves **122/123 → 123/123
assertion-confirmed kills**, zero uncovered and zero timeout/error detections. All 123 semantic
identities are unchanged and all 122 original kills are retained. PIT identifies the new regression
as the killing test for the remaining emission-guard mutant:

- Class: `dev.samhb.interleave.cb.ContextBoundedExplorer`
- Method: `emitIncompleteTraceIfNeeded`
- Descriptor: `(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/search/StateVisitor;)V`
- Operator: `org.pitest.mutationtest.engine.gregor.mutators.NonVoidMethodCallMutator`
- Instruction / block: `10 / 2`
- Mutation: remove `Program.isSource` (default false permits deferred source emission).

The clean integrated suite passes **651 tests**, none failed or skipped; build and strict Javadoc
pass. Visualizer normalization passes all **18 checks**. The default state/CBS mutation gate
passes **278/291 assertion-confirmed kills**, with 13 inherited state-package survivors, zero
uncovered and zero timeout/error detections. The 94% floor and ratchet remain unchanged. Browser integration is not rerun locally because production output
and metadata propagation are unchanged. No production code, mutation exclusions or gates change.

CodeRabbit CLI 0.8.1 completed review of all three changed files with **zero findings**
(`review_completed`, not skipped). Independent specification reader review found no inconsistencies
between R1–R3, the fixture and the source/legacy emission paths. Only measured verification prose
was finalized afterward; test and production code did not change after CLI review.
