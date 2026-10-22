# Spec: Source blocked and cutoff transition contracts

## Objective

Protect the source transition protocol's defensive blocked edge and noncommitting cutoff evidence
as a focused roadmap 9-V1 batch. Preserve scheduler eligibility rules: a disabled source guard is
not normally scheduled; the direct protocol still defines a safe defensive result.

## Scope

- Adds `SourceTransitionTest`; updates this spec and the roadmap.
- No production/API, dependencies, mutation scopes/mutators/exclusions or gate changes.
- Metadata label formatting, higher-level verdict/reporting and reduction contracts stay separate.
  This does not remeasure the full historical expanded mutation population.

## Invariants and Requirements

1. WHEN a false outer guard is invoked directly through `SourceStep.transition`, THE SYSTEM SHALL
   return BLOCKED with the committed state and selected owner's PC unchanged, no failure/cutoff,
   and correctly derived blocked/deadlock metadata. The original parent SHALL remain unchanged.
2. WHEN an inner loop in an atomic block exhausts its allowance, THE SYSTEM SHALL return only
   LOOP_LIMIT evidence identifying the attempted owner/outer PC and actual inner loop source span,
   without a successor, outcome or failure. Tentative writes SHALL not modify the parent state/PC.

## Design and Tests

Both fixtures use thread 1 after a successful first action writes counter 7, so the selected PC
and owner are nonzero. Zero-default mutants cannot hide behind an initial instruction or thread.
Assert literal ordered PCs `[0,1]`, literal state 7, enabled IDs and last outcomes; do not infer
identity from production-generated metadata or serialization.

The blocked fixture calls `SourceStep.transition` directly with a private scratch copy. It does
not ask a scheduler or fixed-schedule driver to execute a disabled choice. Inspect the returned
BLOCKED successor and the preserved parent's ADVANCED outcome independently.

The cutoff fixture uses `TransitionExecutor` and a tentative write to 9 before an inner limit-zero
loop. Its independent expected witness identifies thread 1, PC 1, limit/iterations 0 and the inner
loop at line 8, columns 7–45 (exclusive end). The trimmed loop is 38 UTF-16 characters, preceded
by six spaces: hand token-length addition and a separate text-length calculation agree. No emoji,
tabs complicate this fixture's source span. The zero-limit final check still retains the loop's
own expansion frame `(sourceOffset=133, iteration=0)`. Independently adding the seven preceding
line lengths plus newlines and indentation agrees with a separate text search for the while token;
the expected frame is a literal, not copied from compiled instruction metadata.

| Requirement | Regression | Oracle |
|---|---|---|
| R1 | `disabledGuardAttemptPreservesNonzeroParentPosition` | Defensive blocked successor, state/PC and derived facts; parent remains committed |
| R2 | `innerCutoffRetainsExactLocationAndCommittedParent` | Literal complete cutoff witness; no successor/outcome/failure; parent state/PC unchanged |

## Acceptance Criteria

- Both new regressions and the full existing suite pass without production changes.
- Fresh merged-main/final scoped mutation reports preserve semantic identities and every old kill;
  explicitly record new kills and remaining metadata dispositions without equivalence assumptions.
- Full build/tests/Javadoc, unchanged default mutation gate and normalization pass.
- CodeRabbit CLI reviews the three-file bundle; independently disposition any findings.

## Baseline

Merged PR #64 is `bb03289`. Fresh baseline passes 764 tests, no failures/errors/skips.
Scoped `SourceInstruction` is **102/114 assertion-confirmed kills**, five survivors and seven
uncovered; `TransitionExecutor` is **18/18**. Aggregate **120/132**, zero timeout/error detections.
The two-class override is independent of default state/CBS gates. Local Gradle always uses
`--max-workers=1 -PpitestThreads=1`.

## Verification

All **766 tests** pass (two new regressions), zero failures/errors/skips. Scoped
`SourceInstruction` improves **102/114 → 110/114 assertion-confirmed kills**; `TransitionExecutor`
remains **18/18**. Aggregate **120/132 → 128/132**; the same semantic population and all 120 old
kills remain. Six uncovered blocked-edge mutations and two surviving cutoff-location mutations
become killed. Three metadata survivors and one metadata uncovered remain, zero timeouts/errors.
Identity comparison uses class/method/descriptor/mutator plus instruction indices and blocks,
not line numbers. These totals are independent of the default scope.

### Scoped mutation ledger

All changed or retained non-kill identities below belong to
`dev.samhb.interleave.format.dsl.SourceInstruction`; indices identify bytecode instructions.
Production bytecode and scope are unchanged. No equivalence declaration or exclusion is added.

| Baseline → final | Method / indices | Contract / disposition |
|---|---|---|
| NO_COVERAGE → KILLED (5) | `transition` / 29, 31, 33, 35, 36 | Removed parent state/PC lookup, unboxing or successor call in the defensive blocked edge; nonzero parent PC and committed state are asserted. |
| NO_COVERAGE → KILLED (1) | `transition` / 42 | Null blocked result; successor/outcome/parent invariants distinguish it. |
| SURVIVED → KILLED (2) | `transition` / 173, 176 | Removed loop start/location call; literal inner-loop span and complete cutoff witness distinguish them. |
| SURVIVED (1) | `metadata` / 25 | Changed label truncation boundary; separate exact-limit label test. |
| SURVIVED (2) | `metadata` / 8, 23 | Removed node-start or String.length call; separate source label origin/truncation tests. |
| NO_COVERAGE (1) | `metadata` / 29 | Removed String.substring call in truncation; separate overlong-label test. |

The four retained metadata identities have explicit follow-up tests, not equivalence claims.
The initial cutoff oracle omitted the loop's own terminal expansion frame. Source expansion rules
and independently calculated text offsets confirmed its required `(133,0)` frame; the corrected
literal witness passes without production changes.
Final `clean build javadoc pitest` passes all 766 tests, zero failures/errors/skips, including
strict Javadoc doclint. Default PIT remains **281/291 kills**, ten survivors, zero uncovered and
zero timeout/error detections; all semantic identities/statuses match merged PR #64's evidence.
The 94% floor, scopes, mutators and exclusions are unchanged. Its inherited informational
per-class baseline notice remains outside this bundle. All 18 visualizer normalization checks
pass. Browser integration is not rerun because production output and browser code are unchanged.
GitHub CI has not been checked.

CodeRabbit CLI completed review of all three changed files with **zero findings**. Only measured
verification/status prose was finalized afterward. Scoped credential-pattern scan, relative
Markdown links and diff whitespace checks pass. No production/API or dependency changes.
Next focused 9-V1 batch: source label metadata, then higher-level verdict/reduction/reporting work.
