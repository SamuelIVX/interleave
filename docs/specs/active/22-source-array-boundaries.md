# Spec: Source array boundaries and thread ownership

## Objective

Protect valid endpoints, modeled bounds failures and `tid`-selected array ownership in the next
contract-sized roadmap 9-V1 batch. Preserve the distinction between evaluator failures and host
exceptions even when both become the same source-level failure category.

## Scope

- Adds `SourceArrayTest` and `EvaluatorArrayTest`; updates this spec and the roadmap.
- No production/API, dependency, mutation scope, mutator, exclusion or gate changes.
- Defensive malformed-AST/classifier cases, transitions/reductions/reporting and compiler safeguard
  isolation remain separate follow-ups. The full historical expanded population is not remeasured.

## Invariants and Requirements

1. WHEN shared or private array indices are zero or length minus one, THE SYSTEM SHALL read and
   write the correct element while preserving the other element and unrelated array namespace.
2. WHEN an index is negative or equals length (including an empty array), THE EVALUATOR SHALL
   throw its documented `EvalException`, retaining its array/index/length read diagnostic or
   checked-write bounds diagnostic. A host array exception is not an interchangeable API result.
3. WHEN an invalid array action follows tentative scalar/shared/private writes in a source atomic
   block, THE SYSTEM SHALL report a STEP/RUNTIME_ERROR/ARRAY_BOUNDS violation and roll back all
   those writes, including the other thread's untouched local copy.
4. WHEN a nonzero thread runs first and uses `tid` for shared/private array indexing, THE SYSTEM
   SHALL use that thread's identity and local storage, independently of schedule position.

## Design and Tests

Source tests load supported `.ilv` syntax and run fixed schedules through `ExecutionDriver`.
Use distinct shared `[11,22]` and private `[31,42]` initial arrays, a changed write value (81),
and literal output/rollback arrays. The `tid` test uses schedule `[1,0]`, initial result/owner
sentinels `[-1,-1]`, expected shared results `[11,22]`, owners `[0,1]`, private arrays `[80,22]`
and `[11,81]`. None of these expected values comes from the evaluator under test.

Direct evaluator tests use declared shared/private arrays and valid integer ASTs with invalid
indices. They require exactly `EvalException`: merely checking a source verdict/category would
miss a mutant that falls through to JVM indexing. Check evaluator-owned diagnostic content to
keep the selected array identity observable. These tests do not broaden source trace diagnostics,
which intentionally expose stable categories rather than arbitrary exception text.

| Requirement | Regression | Cases |
|---|---|---|
| R1 | `readsBothArrayEndpoints` | Four shared/private first/last reads |
| R1 | `writesSharedEndpoints`, `writesLocalEndpoints` | Four first/last writes with untouched-element/namespace/peer checks |
| R2 | `invalidReadsThrowModeledBoundsErrors` | Six shared/private negative, length and empty-array reads |
| R2 | `invalidWritesThrowModeledBoundsErrors` | Six shared/private negative, length and empty-array writes; arrays remain unchanged |
| R3 | `invalidIndicesProduceStepFailuresAndRollback` | Eight shared/private read/write negative/length failures |
| R4 | `tidIndexesSharedResultsAndIsolatedLocalArrays` | One two-thread ownership schedule |

## Acceptance Criteria

- All 29 new invocations and the full existing suite pass; no production behavior changes.
- Compare a fresh merged-main evaluator baseline and final report by semantic identity: preserve
  every prior kill, record new kills and all remaining statuses without equivalence assumptions.
- Full build/Javadoc/default mutation gate and normalization pass, with unchanged gates/exclusions.
- CodeRabbit CLI reviews this four-file bundle and findings receive independent dispositions.

## Baseline

Merged PR #62 is `66cf4b9`. Fresh baseline: 711 passing tests, no failures/errors/skips;
evaluator PIT **262/282 assertion-confirmed kills**, eight survivors, 12 uncovered, zero
timeout/error detections. This reproduces Spec 21's per-class result, not the historical aggregate.
Local Gradle invocations use `--max-workers=1 -PpitestThreads=1`.

## Verification

All 740 tests pass (29 new invocations), zero failures/errors/skips. Scoped evaluator PIT improves
**262/282 → 269/282 assertion-confirmed kills**. All 282 semantic identities match and all 262
baseline kills remain killed. Six surviving array mutations and one uncovered `tid` mutation are
newly killed; two survivors and 11 uncovered remain, with zero timeout/error detections. Identity
comparison uses class/method/descriptor/mutator plus instruction indices and blocks, not line numbers.

### Scoped mutation ledger

All rows belong to `dev.samhb.interleave.format.dsl.Evaluator`. Mutation indices identify bytecode
instructions; production bytecode is unchanged. No exclusion or equivalence declaration is added.

| Baseline → final | Method / indices | Change / disposition |
|---|---|---|
| SURVIVED → KILLED (4) | `assignArray` / 8; `eval` / 272, 357, 361 | Array bounds conditional boundaries; valid zero endpoints or exact modeled exception checks distinguish them. |
| SURVIVED → KILLED (2) | `eval` / 278, 367 | Removed array-name call in read bounds diagnostics; direct diagnostic assertions distinguish the selected namespace. |
| NO_COVERAGE → KILLED (1) | `eval` / 42 | Null `tid` return; nonzero-first owner/index test distinguishes it. |
| SURVIVED (1) | `assign` / 14 | Removed generated `Objects.requireNonNull` before LHS switch; manual null-effect defense remains a separate batch. |
| SURVIVED (1) | `failureCategory` / 28 | Negated classification condition; type/message/null classifier follow-up remains separate. |
| NO_COVERAGE (6) | `eval` / 67, 107, 109, 137, 179, 181 | Unknown-field/local or array-as-scalar diagnostic name calls; validated source loading rejects these paths. Defensive AST follow-up. |
| NO_COVERAGE (1) | `eval` / 317 | Invalid shared-array diagnostic name call; defensive AST follow-up. |
| NO_COVERAGE (4) | `eval` / 489, 536, 593, 1159 | Unknown unary/binary operator or invalid boolean operand diagnostic calls; defensive AST follow-up. |

The retained totals sum to two survivors and 11 uncovered, not 13 equivalent mutations. Default
mutation gates do not include the evaluator; its scoped result is recorded independently.
Final `clean build javadoc pitest` passes all 740 tests, zero failures/errors/skips, including
strict Javadoc doclint. Default PIT remains **281/291 kills**, ten survivors, zero uncovered and
zero timeout/error detections; all semantic identities/statuses match merged PR #62's evidence.
The 94% floor, scopes, mutators and exclusions are unchanged. The inherited informational per-class
baseline notice remains outside this bundle. All 18 visualizer normalization checks pass.
Browser integration is not rerun because no production output or browser behavior changed.
GitHub CI has not been checked.

CodeRabbit CLI completed review of all four changed files with **zero findings**. Only measured
verification/status prose was finalized afterward. Relative Markdown links, credential-pattern
scan and diff whitespace checks pass. Roadmap 9-V1 stays open; next focused work is defensive
AST/classifier contracts, followed by separate transition/reduction/reporting batches.
