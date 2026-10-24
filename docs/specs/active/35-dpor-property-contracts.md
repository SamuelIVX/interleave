# Spec: DPOR property dispatch and duplicate-check dispositions

## Objective

Protect the observable distinction between absent, known-empty and unknown property metadata, and
close the focused duplicate-property audit from roadmap 9-V1 without manufacturing invalid tests.
Raw mutation outcomes and supported-contract dispositions remain separate evidence.

## Scope

Add DporPropertyContractTest, this specification and the roadmap update. No production, API,
dependency, report-output, mutation-scope/operator/exclusion or gate changes. Step-PC lookups,
visibility/interference, antichain compaction and happens-before bookkeeping remain separate.

## Requirements

1. NONE and known-empty constant properties SHALL retain reduced exploration; unknown properties
   SHALL use exhaustive traversal even when the predicate happens to be always true.
2. A pure unknown callback depending on counters SHALL retain a concrete violation at a configuration
   omitted by reduced traversal. Counter observations SHALL NOT be mislabeled as known-empty.
3. At a blocked position, a false property SHALL produce VIOLATION before deadlock classification;
   a true property SHALL retain DEADLOCK in both property-checking traversal modes.
4. Result and visitor evidence SHALL independently match literal schedules/classifications and replay
   with the actual property. No call counters, alternating predicates or model mutation force coverage.
5. The two duplicate violation bodies and two redundant dispatch checks SHALL have explicit
   supported-contract dispositions, with raw SURVIVED/NO_COVERAGE statuses retained honestly.
6. Preserve every fresh baseline semantic identity and kill; no new-kill or full-closure promise.

## Design and independent oracles

Three metadata cases, one configuration-sensitive case and four blocked cases give eight new tests.
The independent model has two two-action owners: T0 writes x=1 then x=2; T1 writes y=1 then y=2.
Three positions per owner give nine counter pairs. Independent enumeration lists the nine pairs
[0,0], [0,1], [0,2], [1,0], [1,1], [1,2], [2,0], [2,1], [2,2]. Reduced traversal omits [1,1],
leaving eight; the two serial boundary paths have five positions each and share both endpoints,
independently confirming eight. All constant properties retain first completion [0,0,1,1].

The unknown callback is false precisely at counters [1,1], otherwise true. Its observation metadata
is absent, as required for configuration-sensitive properties. Exhaustive traversal retains that
position and literal VIOLATION prefix [0,1], alongside completed prefix [0,0,1,1]. It still visits
all nine pairs: the other outgoing branches cover every nonviolating position independently of
expanding the violating position. Callbacks and returned witnesses each match literal expectations.

A separate source model has one initially disabled guarded atomic action. Exactly one position is
visited and the schedule prefix is empty. Pure immutable true/false predicates produce DEADLOCK/
VIOLATION respectively, with both known-empty and unknown metadata. Source terminal replay receives
the actual property; only NONE uses a replay-only true fallback.

## Supported-contract audit

References are symbols in production files, not source-line identities:

- `Invariant.holds` in `search/Invariant.java` requires a pure check without modeled/external mutation.
  Present observations additionally require deterministic state-only complete observations.
- `Configuration.allTerminated` and `enabledThreadIds` in `core/Configuration.java` return cached
  immutable facts. No step/guard execution occurs in these getters.
- `DporExplorer.dporDfs` and `dfsDfs` check the invariant immediately after visitation. A false
  result emits VIOLATION and returns. The later deadlock branch runs no transition or observer
  callback between that successful check and the second check, using the same state/configuration.

### Duplicate violation bodies: twelve uncovered identities

The nested violation body inside each deadlock branch is unreachable for a deterministic pure
predicate. If the first check returned false, exploration already returned. If it returned true,
the same unchanged inputs cannot make the second check false. This also applies to pure UNKNOWN
properties that inspect counters, since counters and configuration identity are unchanged. A
predicate exception at the first check propagates before either body is reached. Stateful predicates,
mutating observers or history-sensitive external behavior are outside this disposition.

The six mutations in each body therefore retain scope-limited unreachable dispositions rather
than artificial coverage. No production branch is deleted, no test is skipped, and no exclusion
or numerical score is adjusted.

### Nested exhaustive null guard: one survivor

`dfsDfs` is private and called initially only by explore's non-null UNKNOWN-property arm; recursive
calls pass the same invariant. Its nested deadlock null-guard mutation cannot change an emitted
outcome: the first property check already held and the duplicate false body is unreachable under
the argument above. This is a supported-contract output disposition, not blanket equivalence for
arbitrary impure callbacks, process/resource failures or performance.

### Absent-property Optional construction: one survivor

In explore's `invariant == null` case, maybeObs is initialized by Optional.empty, but the next
null-invariant arm goes directly to dporDfs with observations=null. It never dereferences maybeObs.
Replacing only that Optional.empty result with null therefore cannot change public traversal or
evidence. The non-null invariant arms call observedLocations instead; this disposition does not
permit them to return null or conflate Optional.empty with a present empty observation set.

## Acceptance

- Eight new tests and the full suite pass; build and strict Javadoc pass.
- Fresh baseline/final scoped semantic populations and old kills match; record actual raw scores.
- Each of the fourteen dispositioned identities is assigned once. Remaining mutations stay explicit
  follow-ups, without whole-class, whole-checker, resource or performance equivalence claims.
- Default PIT, normalization and three-file CodeRabbit CLI review pass. Trivial/minor feedback does
  not trigger extra CLI runs, per Sam's instruction.

## Baseline and verification

Merged PR #75 is `ac8e2eb`. Fresh baseline: 920 tests pass, zero failures/errors/skips;
outer-DporExplorer260/289 KILLED, 17 SURVIVED and 12 NO_COVERAGE, zero timeout/error statuses.
All **928 tests** pass, including eight new cases, with zero failures/errors/skips. Final scoped
PIT remains **260/289 KILLED, 17 SURVIVED, 12 NO_COVERAGE**, zero timeout/error statuses. All 289
semantic identities (class/method/descriptor/mutator/instruction indices/blocks) and their statuses
match the fresh baseline; all 260 baseline kills remain killed. There are **zero new kills**.
The fourteen scoped dispositions below do not alter these raw measurements. All local Gradle
commands use `--max-workers=1 -PpitestThreads=1`.

All 18 visualizer normalization checks pass. CodeRabbit CLI completed its review of all three
changed files with zero findings; only measured verification/status prose was finalized afterward.
Final integrated `clean build javadoc pitest` passes with all 928 tests, strict Javadoc and
default PIT at 281/291 kills. All 291 default semantic identities/statuses match the merged Spec34
evidence. Remote CI is unverified; this bundle is published as a draft PR.

### Fourteen supported-contract dispositions

Identities belong to dev.samhb.interleave.dpor.DporExplorer. Indices are bytecode instructions,
not source lines. Raw statuses are retained; none of these dispositions is credited as a kill.

| Disposition | Method / indices | SURVIVED | NO_COVERAGE |
|---|---|---|---|
| Redundant absent-property Optional value | `explore` / 62 | 1 | 0 |
| Redundant nested exhaustive null guard | `dfsDfs` / 135 | 1 | 0 |
| Unreachable exhaustive duplicate violation body | `dfsDfs` / 146, 148, 151, 157, 162, 167 | 0 | 6 |
| Unreachable reduced duplicate violation body | `dporDfs` / 285, 287, 290, 296, 301, 306 | 0 | 6 |
| **Total** | **14 identities with the scoped arguments above** | **2** | **12** |

### Separate remaining audits

These fifteen identities remain open, without equivalence claims. The fourteen scoped dispositions
above do not automatically apply to step preparation, metadata or representation changes.

| Follow-up | Method / indices | SURVIVED |
|---|---|---|
| Exhaustive PC lookup | `dfsDfs` / 234 | 1 |
| Empty-enabled normalization | `dporDfs` / 20 | 1 |
| Antichain compaction | `dporDfs` / 130; `lambda$dporDfs$2` / 6, 7 (both return variants) | 4 |
| Visibility refresh, interference and current-PC pruning | `dporDfs` / 377, 569, 570, 582, 663 | 5 |
| Happens-before bookkeeping | `dporDfs` / 540, 546, 554 | 3 |
| All-asleep fallback PC lookup | `dporDfs` / 825 | 1 |
| **Total** | **15 open identities** | **15** |

The 14 dispositioned plus 15 open identities account for all 29 baseline non-kills, each once. Browser integration is outside this test/documentation bundle
because executable production and report output are unchanged.
