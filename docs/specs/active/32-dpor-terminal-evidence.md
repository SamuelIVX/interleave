# Spec: DPOR terminal evidence across property modes

## Objective

Protect DPOR's public terminal evidence before extending its sleep-coverage and pruning contracts.
The reduced traversal and unknown-observation exhaustive fallback must each retain authentic
witnesses and visitor notifications. Continue roadmap 9-V1 in a focused evidence bundle.

## Scope

- Add `DporEvidenceContractTest`, this specification and a roadmap update.
- Cover terminal evidence, supplied-store initialization and the reachable legacy all-asleep
  assertion path through public exploration/visitor interfaces.
- No production, public API, dependency, reduction algorithm, mutation scope/operator/exclusion
  or gate changes. Sleep antichains, visibility/pruning, happens-before bookkeeping and helper
  classes remain separate follow-up contracts.

## Invariants

- No invariant and known observations use reduced traversal; unknown observations retain the
  exhaustive fallback. Fixture metadata must actually select its declared public property mode.
- COMPLETED, property/step VIOLATION, DEADLOCK and source INCOMPLETE retain literal prefixes,
  outcomes and callback order, rather than only comparing callbacks to returned evidence.
- Source replay validates terminal classification with the supplied property or a replay-only true
  fallback. Legacy assertion replay validates actual step outcomes; it does not establish general
  terminal-property validation for arbitrary legacy traces.
- Supplied stores are cleared before any traversal mode starts.
- All-asleep legacy fallback must not drop a reachable assertion failure or its callback.
- Hand-counted visits/map sizes apply only to these linear/two-position fixtures. Source sleep
  coverage can revisit a configuration in different contexts; no general visit/map-size equality
  or enumeration of all schedules is asserted.

## Requirements

1. Completion, step failure, deadlock and source cutoff SHALL each preserve literal evidence and
   observer order in NONE, OBSERVED and UNKNOWN modes. Genuine property violations SHALL preserve
   the same contract in both checking modes; NONE deliberately does not check the property.
2. The supplied property metadata SHALL be present for OBSERVED and absent for UNKNOWN; NONE
   SHALL pass null to exploration. Ordinary source properties read x even where they always hold,
   so state propagation into property checks is observable.
3. A real exact store already containing the initial position SHALL be cleared in all three modes,
   allowing two visits, values zero/one, a completion witness and the matching callbacks.
4. A legacy assertion independent of a preceding write SHALL still be executed when all remaining
   enabled actions are asleep, recording both the after-write and root failure prefixes and callbacks
   in reduced NONE/OBSERVED modes. Its step has complete read/error footprints and no hidden state.
5. Unknown-observation fallback SHALL preserve distinct loop cutoff owners and deduplicate repeated
   reports of one occurrence across schedules, retaining its first recorded prefix and callback.
6. Preserve baseline semantic identities and every existing kill. Assign remaining identities to
   explicit follow-up contracts rather than make blanket equivalence claims or force 100%.

## Design and Tests

The source matrix has four paths across three public property modes plus two property-violation
cases: 14 invocations. OBSERVED retains the compiler's known property metadata; UNKNOWN wraps its
pure predicate in an ordinary callback, deliberately hiding observations; NONE passes null.
Completion and property-failure visits reach counters [0] then [1]; runtime, deadlock and cutoff
paths have one visited position. Literal Witness tuples assert choices, step outcomes and terminal
classification separately for callbacks and results. All source witnesses replay with terminal
validation enabled, preserving exploration's original invariant selection.

Three real prepopulated HashingStateStore cases cover initialization in each mode. Two legacy
cases write flag[1] in T0 and assert flag[0] in T1, initially false. The assertion is independent of
the write and becomes asleep after it; legacy fallback must still emit [0,1] ADVANCED/ASSERTION_FAILED
and the root [1] ASSERTION_FAILED witness. Literal notifications and observed flag[1] values anchor
both evidence and the fact that the after-write position was visited.

The additional UNKNOWN-mode cutoff cases keep both loop owners at the initial position and
suppress repeated witnesses for one occurrence across schedules. The repeated case has three
T0 counter positions and two T1 positions, giving six configurations. Direct enumeration is
[0,0], [1,0], [2,0], [2,1], [1,1], [0,1]; the first cutoff prefix is [0,0,1]. These independent
hand derivations anchor the literal count and callback sequence.

| Requirement | Regression | Cases |
|---|---|---|
| R1, R2 | `terminalEvidenceAcrossPropertyModes` | 12 ordinary terminal cases plus two property violations |
| R3 | `prepopulatedStoreIsClearedAcrossPropertyModes` | Three metadata modes with a real prepopulated store |
| R4 | `allSleepingLegacyAssertionRetainsEvidence` | NONE and OBSERVED legacy reduced traversal |
| R5 | `exhaustiveFallbackPreservesDistinctCutoffWitnesses` | Two loop owners at one visited position |
| R5 | `exhaustiveFallbackDeduplicatesRepeatedCutoffOccurrence` | One loop reached after three schedules; six visited positions |

## Acceptance Criteria

- All 21 new invocations and the full existing suite pass; build and strict Javadoc pass.
- Fresh outer-DporExplorer baseline/final semantic identities match and no existing kill is lost.
- The changed identities are tied to literal public evidence assertions. Remaining mutation gaps
  stay explicitly assigned to later pruning/coverage/reachability/bookkeeping work; this is not
  a whole-class or whole-checker closure claim.
- Default PIT and normalization pass with separate scopes stated.
- CodeRabbit CLI completes review of this new three-file bundle. Trivial/minor PR feedback does
  not trigger extra CLI runs, per Sam's instruction.

## Baseline

Merged PR #72 is `1ad2391`. Fresh baseline: 881 tests pass, no failures/errors/skips.
Outer-DporExplorer PIT measures **192/289 KILLED**, 41 SURVIVED, 56 NO_COVERAGE, zero timeout/error
statuses. Semantic identity comparison uses class/method/descriptor/mutator/instruction indices/
blocks rather than source lines. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`.

## Verification

All **902 tests** pass, including 21 new invocations, zero failures/errors/skips.
Outer-DporExplorer PIT improves **192/289 → 234/289 KILLED**: seven survivors and 35 uncovered
identities become killed. One previously uncovered site becomes a covered survivor. Final totals
are 35 SURVIVED and 20 NO_COVERAGE, zero timeout/error detections. All 289 semantic identities
match and all 192 baseline kills remain killed. Every new kill names DporEvidenceContractTest.

### Changed-identity ledger

All identities are in `dev.samhb.interleave.dpor.DporExplorer`; indices are bytecode instructions.

| New kills | Method / indices | Public evidence oracle |
|---|---|---|
| 2 | `explore` / 18, 69 | Prepopulated store initialization and genuine UNKNOWN-mode exhaustive dispatch |
| 5 | `dfsDfs` / 80, 116, 198, 292, 360 | Property/completion/deadlock/cutoff/assertion callback evidence |
| 9 | `dfsDfs` / 130, 138, 140, 141, 177, 179, 182, 188, 193 | Genuine deadlock classification, property state and recorded prefix |
| 8 | `dfsDfs` / 260, 266, 267, 268, 277, 278, 284, 289 | Cutoff construction, distinct identity, first-prefix deduplication and notification guard |
| 5 | `dfsDfs` / 339, 341, 344, 350, 355 | Failing-edge evidence and notification guard |
| 5 | `dporDfs` / 219, 255, 337, 514, 951 | Reduced property/completion/deadlock/main-assertion/fallback-assertion callbacks |
| 3 | `dporDfs` / 277, 279, 280 | Repeated property check must preserve the supplied state and genuine deadlock outcome |
| 5 | `dporDfs` / 930, 932, 935, 941, 946 | All-asleep legacy assertion evidence and notification guard |

The rows sum to 42. The distinct-cutoff case kills removed `StepTransition.cutoff` at dfsDfs/266:
substituting null as the dedup key collapses distinct owners. The repeated-cutoff case also kills
explore/69's dispatch inversion: routing the UNKNOWN predicate through reduced source coverage
changes the literal visit/notification evidence instead of preserving exhaustive fallback.

### Retained follow-up census

Every retained semantic identity is assigned below; these are follow-up gaps/audits, not equivalence
declarations or exclusions. Counts are identities, not unique instruction indices. Each lambda's
index 7 has both FALSE_RETURNS and TRUE_RETURNS mutations, plus the removed containsAll call at 6.

| Follow-up | Method / indices | SURVIVED | NO_COVERAGE |
|---|---|---|---|
| Exhaustive-fallback metadata and duplicate-property reachability | `dfsDfs` / 135, 146, 148, 151, 157, 162, 167, 234 | 2 | 6 |
| Source sleep normalization and coverage contexts | `dporDfs` / 10, 11, 20, 21, 58, 59, 67, 68, 98, 99, 121, 130, 137 | 13 | 0 |
| Sleep-coverage predicates | `lambda$dporDfs$1` and `lambda$dporDfs$2` / 6, 7 (both boolean-return variants) | 6 | 0 |
| Visibility, wakeup and future-PC pruning | `dporDfs` / 377, 569, 570, 582, 632, 663, 682 | 7 | 0 |
| Happens-before bookkeeping observability | `dporDfs` / 540, 546, 554 | 3 | 0 |
| All-asleep fallback PC and cutoff selection | `dporDfs` / 825, 851 | 2 | 0 |
| Reduced duplicate-property reachability | `dporDfs` / 285, 287, 290, 296, 301, 306 | 0 | 6 |
| All-asleep fallback cutoff reachability/evidence | `dporDfs` / 857, 858, 859, 868, 869, 875, 880, 883 | 0 | 8 |
| Optional-observation handling | `explore` / 62, 131 | 2 | 0 |
| **Total** | **55 retained identities** | **35** | **20** |

The remaining UNKNOWN-mode null-guard site dfsDfs/135 is now covered but survived; it is not counted
as a new kill. Audit redundant checks and bookkeeping under supported purity/error contracts before
claiming output equivalence. Source all-asleep normalization and arbitrary custom SourceStep/wrapped
program reachability also require explicit scope; do not declare all fallback cutoff sites universally
unreachable from their current coverage status. No private mutation/call-count tests, exclusions or
score-floor changes force the remaining sites closed.

The final integrated `clean build javadoc pitest` passes with 902 tests and default PIT at
281/291 kills. All 291 default mutation identities and statuses match the merged Spec 31 evidence.
All 18 visualizer normalization checks pass. CodeRabbit CLI completed its review of all three
changed files with zero findings; only verification/status prose was finalized afterward. Browser
integration was not rerun because executable production and report output are unchanged. Remote CI
is unverified; this bundle is published as a draft PR.
