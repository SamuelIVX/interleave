# Spec: Source scalar-expression verification

## Objective

Protect the existing scalar-expression contract with independent observable value/error oracles.
This is the first non-replay batch of roadmap 9-V1, not closure of its historical expanded scope.

## Scope

- Adds `SourceExpressionTest` under `dev.samhb.interleave.format`.
- Updates this spec and the roadmap's shipped/pending verification boundaries.
- Production evaluator, loaders, execution, APIs, dependencies and mutation gates stay unchanged.

## Non-Goals

Array access/assignment boundaries, malformed manually constructed ASTs, all evaluator defenses,
reduction/verdict reporting, compiler safeguard mutation isolation (9-V2), performance changes and
remeasuring the full historical 1,299-mutant population. These need their own contract-sized batches.

## Invariants

- Arithmetic uses signed 32-bit Java-int wraparound and signed remainder.
- Comparisons distinguish equality from both orderings; boolean operators preserve truth tables.
- `&&` and `||` evaluate their RHS only when required by the left operand.
- Boolean local assignments are visible to their owning thread and isolated from other threads.
- Required modulo-by-zero errors are modeled step violations; atomic tentative writes roll back.

## Requirements

1. WHEN a valid scalar arithmetic assignment executes, THE SYSTEM SHALL commit the literal expected
   integer, including negative remainder and overflow boundaries.
2. WHEN integer comparisons or boolean operations execute, THE SYSTEM SHALL commit their literal
   truth-table result, including every comparison's equality boundary.
3. WHEN a RHS can fail at runtime, THE SYSTEM SHALL skip it only under short-circuit semantics;
   a required error SHALL retain runtime/step/modulo evidence and discard earlier atomic writes.
4. WHEN one thread updates a boolean local and reads it back, THE SYSTEM SHALL preserve that
   assigned value without changing another thread's initialized copy.

## Design and Tests

Exercise the public `SourceProgramLoader` and fixed-schedule `ExecutionDriver`, observing committed
`DynamicState` fields. Do not call the evaluator to compute expectations. Source loading also
ensures these expressions are reachable through the supported language rather than only synthetic
invalid ASTs. Use initialized shared operands so arithmetic results are observable independently
of a global property that could share the same evaluator defect.

| Requirement | Regression | Cases / independent oracle |
|---|---|---|
| R1 | `arithmeticProducesLiteralResults` | 15 literal results: addition/subtraction/multiplication, zero multiplier, four remainder sign combinations, unary signs, int wraparound and MIN_VALUE remainder -1 |
| R2 | `comparisonsProduceLiteralResults` | 18 literals: below/equal/above for all six integer relations |
| R2 | `booleansProduceLiteralResults` | 18 literals: full AND/OR/equality/inequality tables and both NOT inputs |
| R3 | `shortCircuitControlsRuntimeErrors` | Four cases: successful false-AND/true-OR and failing true-AND/false-OR; committed state, outcomes and error classification |
| R4 | `booleanLocalsKeepTheirAssignedValuesAndThreadOwnership` | Two threads; owner toggles/reads true while the other's local stays false |

Numeric expectations are hand-derived literals. Cross-check signed wraparound with modular
arithmetic and signed remainder with truncation-toward-zero quotient identities outside Java;
never mirror the evaluator implementation in the test. Full truth tables follow boolean definitions.

## Acceptance Criteria

- All 56 new test invocations pass without modifying production behavior.
- Fresh scoped PIT on merged main and after the tests has the same semantic mutation population;
  every previously killed identity stays killed. Record new kills and residual statuses separately.
- Distinguish valid-language runtime coverage from typechecker-protected defensive paths and
  diagnostic-only changes; do not call all retained mutations equivalent.
- Full clean build/tests/Javadoc and unchanged default mutation gate pass; no exclusions or new deps.
- CodeRabbit CLI reviews the bundle; findings receive independently verified dispositions.

## Baseline

Merged PR #61 is `03ac680`. Fresh baseline full suite passes 655 tests. Scoped evaluator PIT is
**203/282 assertion-confirmed kills**, 18 survivors, 61 uncovered mutations and zero timeout/error
detections. This exactly reproduces the historical per-class count, but does not refresh the old
expanded-run aggregate. Default scope remains state/CBS; evaluator is measured with the existing
`-PpitestTargetOverride=dev.samhb.interleave.format.dsl.Evaluator` mechanism, not a gate change.
Local Gradle commands always include `--max-workers=1 -PpitestThreads=1`.

## Verification

All **711 tests** pass (56 new invocations), zero failures/errors/skips. Scoped evaluator PIT
improves **203/282 → 262/282 assertion-confirmed kills**. The same 282 semantic identities are
present, all 203 old kills stay killed, and 59 become killed (49 formerly uncovered, ten formerly
surviving). Eight survivors and 12 uncovered remain; zero timeout/error detections. Identity
comparison uses class/method/descriptor/mutator plus instruction indices and blocks, not line numbers.

### Remaining scoped dispositions

These are follow-up boundaries, not equivalence declarations. No exclusion is added. All identities
below belong to `dev.samhb.interleave.format.dsl.Evaluator`; indices identify bytecode instructions.

| Status | Method / indices | Mutation / boundary | Disposition |
|---|---|---|---|
| SURVIVED (1) | `assign` / 14 | Remove generated `Objects.requireNonNull` before LHS pattern switch | Null/manual-effect defense; valid loaded assignments have a non-null typed LHS. Out of this valid-language batch; no universal equivalence claim. |
| SURVIVED (4) | `assignArray` / 8; `eval` / 272, 357, 361 | Array bounds conditional boundaries | Separate shared/local read/write edge batch; distinguish a proper modeled error from a host exception. |
| SURVIVED (2) | `eval` / 278, 367 | Remove array-name call in bounds diagnostics | Separate array diagnostics; unchanged selected outcomes do not prove diagnostic equivalence. |
| SURVIVED (1) | `failureCategory` / 28 | Negate classifier conditional | Separate classifier type/message/null cases; current modulo evidence does not exhaust classification. |
| NO_COVERAGE (6) | `eval` / 67, 107, 109, 137, 179, 181 | Remove shared/local name calls in unknown-name or array-as-scalar diagnostics | Loader typing rejects these before execution. Direct malformed AST contracts require their own tests. |
| NO_COVERAGE (1) | `eval` / 317 | Remove name call in invalid local-array diagnostic | Array validation/defensive AST follow-up. |
| NO_COVERAGE (4) | `eval` / 489, 536, 593, 1159 | Remove operator calls in unknown unary/binary or boolean type-error diagnostics | Loaded operators and operand types are validated; defensive AST follow-up. |
| NO_COVERAGE (1) | `eval` / 42 | Replace `tid` result with null | Valid-language gap remains; cover thread identity with the next local/array ownership batch. |

The counts sum to 8 survivors and 12 uncovered. The new tests also kill valid boolean-local,
logical/negation, comparison, multiply and remainder mutants; the scalar contract is not claimed
exhaustive merely because those mutation sites now fail.

Final `clean build javadoc pitest` passes: **711 tests**, zero failures/errors/skips;
Javadoc doclint passes. The unchanged default scope measures **281/291 kills**, ten survivors,
zero uncovered and zero timeout/error detections; all identities and statuses match merged PR #61's
evidence. The 94% floor, mutators and exclusions are unchanged. Its inherited informational
per-class baseline notice remains; no build/gate bookkeeping is changed in this scalar-only PR.
All 18 visualizer normalization checks pass. Browser integration is not rerun because production
behavior/output and browser code are unchanged. GitHub CI has not been checked.

CodeRabbit CLI completed review of all three changed files with **zero findings**. The review
included test code and the scoped disposition table; only measured verification/status prose was
finalized afterward. Credential-pattern scan, relative Markdown links and diff whitespace checks
pass. No new dependencies or production/public-API changes.

Roadmap 9-V1 remains open. Next focused batch: shared/local array edges and `tid` ownership;
defensive AST/classifier diagnostics and transition/reduction/reporting contracts remain separate.
