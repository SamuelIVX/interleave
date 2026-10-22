# Spec: Evaluator defensive AST and classifier contracts

## Objective

Verify the shared evaluator's remaining diagnostic and classification paths as the next roadmap
9-V1 batch. Exercise direct AST calls that valid loaded source cannot reach; do not broaden the
source language or equate source loading with direct malformed-AST evaluation.

## Scope

- Adds `EvaluatorDefensesTest` in `dev.samhb.interleave.format.dsl`.
- Updates this spec, the roadmap and two inherited diagnostic-label corrections; production, public APIs, dependencies and mutation gates stay
  unchanged. Transition/reduction/reporting and compiler safeguard work remain separate batches.
- Scoped mutation evidence targets the outer `Evaluator` class, not every nested value/exception
  class, parser, typechecker or the full historical expanded mutation population.

## Invariants and Requirements

1. WHEN direct evaluation encounters an unknown shared/local name or an array used as a scalar,
   THE EVALUATOR SHALL reject it with `RegistryException` retaining the actual referenced name.
2. WHEN a shared/local array is missing or names a scalar, or an operator/boolean operand is invalid,
   THE EVALUATOR SHALL retain the selected namespace/operator in its rejection diagnostic.
3. WHEN an effect with a null target and a valid RHS is assigned, THE EVALUATOR SHALL reject it
   with `NullPointerException` and leave declared scalar/array values unchanged.
4. WHEN a non-null failure is classified, THE SYSTEM SHALL honor its existing owned-prefix/type
   policy: `% by zero` prefix first, then bounds exception type or bounds prefix, otherwise OTHER.
   A null message is treated as empty; an embedded non-prefix match cannot acquire modulo status.

## Design and Tests

Use direct AST construction with a valid declaration fixture: shared `count`/`values`, private
`scratch`/`items`, distinct scalar sentinels and literal arrays. Only the expression/effect under
test bypasses loader validation. Typed `assertThrowsExactly` distinguishes registry errors from
accidental null dereferences, while literal messages check retained name/operator diagnostics.

Classification uses non-null exceptions with literal categories, independent of the classifier.
The deliberate bounds-type/modulo-prefix collision preserves existing prefix priority; it does
not claim all arbitrary bounds exceptions semantically mean modulo or expose their messages in
source evidence. Passing a null exception is outside the classifier's documented precondition.

| Requirement | Regression | Cases |
|---|---|---|
| R1–R2 | `rejectsMalformedExpressionsWithOwnedDiagnostics` | 14 ASTs: four unknown-name/array-as-scalar references, four invalid shared/local array references, unknown unary/binary operators and four invalid logical operands |
| R3 | `rejectsNullAssignmentTargetWithoutWritingState` | One null-target effect with valid RHS; scalar/array sentinels unchanged |
| R4 | `classifiesFailureTypesAndMessages` | Nine prefix/type/null-message/unrelated-text/precedence cases |

## Acceptance Criteria

- All 24 new invocations and the full existing suite pass; no production behavior changes.
- Compare fresh merged-main and final outer-Evaluator scoped mutation identities/statuses, retaining
  every baseline kill and explicitly recording any residual without an automatic equivalence claim.
- Full build/tests/Javadoc, unchanged default mutation gate and normalization pass.
- CodeRabbit CLI reviews this five-file bundle; independently disposition any findings.

## Baseline

Merged PR #63 is `88e06e9`. Fresh baseline passes 740 tests, no failures/errors/skips;
outer-Evaluator PIT is **269/282 assertion-confirmed kills**, two survivors, 11 uncovered and zero
timeout/error detections. The same existing target override measures this class independently of
state/CBS default gates. Local Gradle always uses `--max-workers=1 -PpitestThreads=1`.

## Verification

All **764 tests** pass (24 new invocations), zero failures/errors/skips. Scoped outer-Evaluator
PIT improves **269/282 → 282/282 assertion-confirmed kills**: two survivors and 11 uncovered
mutations become killed, with zero survivors/uncovered/timeouts/errors. The same 282 semantic
identities remain and all 269 baseline kills stay killed. Identity comparison uses class/method/
descriptor/mutator plus instruction indices and blocks, not line numbers.

### Scoped mutation ledger

All identities belong to `dev.samhb.interleave.format.dsl.Evaluator`; indices identify bytecode
instructions. Production bytecode is unchanged. No equivalence declaration or exclusion is added.

| Baseline → final | Method / indices | Distinguishing contract |
|---|---|---|
| SURVIVED → KILLED (1) | `assign` / 14 | Removed generated null guard; null-target assignment must throw exactly NPE without writes. |
| SURVIVED → KILLED (1) | `failureCategory` / 28 | Negated classifier condition; literal type/prefix/unrelated/null-message cases distinguish categories. |
| NO_COVERAGE → KILLED (6) | `eval` / 67, 107, 109, 137, 179, 181 | Reference-name calls in unknown-name/array-as-scalar errors; exact RegistryException diagnostics retain the actual name. |
| NO_COVERAGE → KILLED (1) | `eval` / 317 | Array-name call in invalid **shared-array** diagnostic; missing/wrong-type shared targets retain the name. |
| NO_COVERAGE → KILLED (4) | `eval` / 489, 536, 593, 1159 | Operator calls in unknown unary/binary or boolean type-error diagnostics; actual operator text is retained. |

The initial defensive pass reached 281/282. Inspecting its one uncovered identity exposed an
inherited label error: index 317 belongs to the shared-array diagnostic, not the local-array
branch. Two single-cell corrections in Specs 21–22 fix that label without changing their historical
counts. Missing/wrong-type cases cover both array namespaces in this batch.

This is complete coverage of the measured outer-class mutation population, not proof of all
possible evaluator behavior, nested classes or the full checker. Roadmap 9-V1 remains open;
transition/verdict and reduction/reporting contracts need their own scoped baselines.
Final `clean build javadoc pitest` passes all 764 tests, zero failures/errors/skips, including
strict Javadoc doclint. Default PIT remains **281/291 kills**, ten survivors, zero uncovered and
zero timeout/error detections; all semantic identities/statuses match merged PR #63's evidence.
The 94% floor, scopes, mutators and exclusions are unchanged. Its inherited informational
per-class baseline notice remains outside this bundle. All 18 visualizer normalization checks
pass. Browser integration is not rerun because production output and browser code are unchanged.
GitHub CI has not been checked.

CodeRabbit CLI completed review of all five changed files with **zero findings**. Only measured
verification/status prose was finalized afterward. Scoped credential-pattern scan, relative
Markdown links and diff whitespace checks pass. No production/API or dependency changes.
