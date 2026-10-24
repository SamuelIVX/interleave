# Spec: Source DPOR coverage compaction and normalization

## Objective

Protect source sleep-context coverage when a later incomparable context arrives, and audit the
related normalization/compaction survivors from roadmap 9-V1. Keep raw mutation measurements,
scoped dispositions and remaining audits separate.

## Scope

Add DporCoverageCompactionTest, this specification and a roadmap update. No production, API,
dependency, report-output, mutation-scope/operator/exclusion or gate changes. Visibility/interference
and happens-before bookkeeping remain separate except for any measured incidental kills.

The disposition arguments below are limited to compiler-produced SourceInstruction definitions,
faithful configuration/store operations, complete stable footprints and non-mutating model observers.
Compiler instructions use stable total identity equality. Arbitrary custom SourceSteps or partial/
side-effecting equality implementations do not inherit these arguments. Digest-free wrappers use
legacy caching and do not traverse this source coverage branch; Spec34 covers their terminal evidence.
No process/resource/performance equivalence is claimed.

## Requirements

1. A later incomparable sleep context SHALL NOT erase earlier incomparable coverage needed to prune
   a subsequent arrival. Coverage means an earlier sleeping set is a subset of the incoming set.
2. The specified source model SHALL retain its independently enumerated values, legitimate context
   revisits and first completed witness; compaction SHALL NOT manufacture another visit.
3. Completion and genuine deadlock after a committed write SHALL preserve literal position values,
   prefixes, step outcomes and callbacks at the no-enabled boundary.
4. Retaining redundant supersets SHALL receive a scoped public-evidence disposition only under total
   stable comparison semantics. Do not confuse retaining redundant entries with removing incomparable ones.
5. The no-enabled normalization guard SHALL receive only the compiler-scoped argument below;
   uncovered/custom-step behavior is not declared universally unreachable.
6. Replay actual source traces directly with original evidence, using the actual property or a
   true replay-only fallback. No private reflection, fabricated stores or execution counters force coverage.

## Design and independent oracles

Two compaction cases and four terminal-boundary cases give six new invocations. Both reduced modes
are exercised: no property and the compiler's known-empty constant property. Fixture metadata is
explicitly checked; no unknown-observation exhaustive fallback silently replaces source coverage.

### Three-thread compaction counterexample

T0 writes z=1 then y=1. T1 writes z=1 then performs an empty action. T2 conditionally writes z=1
if y==0, as one atomic action. Initially y=z=0.

T0/T1 each have three counter positions and T2 has two: 3×3×2 gives **18 unique positions**. Direct
enumeration is every pair in {0,1,2}×{0,1,2}, each with T2 at zero or one. Only the initial position
has z=0. Any committed first action sets z=1; if T2's condition is false, T0 already wrote z before
setting y. The value y is one exactly when T0's PC is two. These facts independently determine all
18 literal value tuples asserted in the returned state set and visitor positions.

Three counter positions need a second traversal with a newly available action:

| Counters | First prefix / sleeping owner IDs | Later prefix / sleeping owner IDs |
|---|---|---|
| [1,1,0] | [0,1] / {0} | [1,0] / {1} |
| [1,1,1] | [0,1,2] / {1} | [0,2,1] / {0} |
| [0,1,1] | [1,2] / {1} | [2,1] / {} |

The first two rows are incomparable contexts; the last incoming set leaves more work awake. Each
row therefore contributes one additional visit: **18+3=21**. Counting the listed multiplicities
also gives fifteen once-visited positions plus three twice-visited positions: **15+6=21**. The tests
assert those exact repeated counter keys, multiplicity two, all position values and the first
completed witness [0,0,2,1,1] with five ADVANCED outcomes, independently for result and callbacks.

At [1,1,1], a third arrival via prefix [1,0,2] again has sleeping owner {1}. The earlier {1} context
must remain recorded after the incomparable {0} arrival, so the third arrival is covered. Replacing
compaction with unconditional removal loses {1}, and the third arrival adds an unnecessary visit.
A temporary external variant produced 22 visits instead of 21; production files were not changed
by that experiment. Any actual mutation kill still requires scoped PIT measurement.

### Empty-enabled boundaries

One source owner writes y=1 then terminates: two positions, one ADVANCED choice [0], COMPLETED.
A second model adds another owner whose z-write guard is always false: the same two value states,
now at counters [0,0] then [1,0], with DEADLOCK after [0]. The disabled action never enters a sleep
set. Both properties are true throughout. Literal source traces replay directly without rebuilding
the evidence; returned states are compared as sets, while visitor position order is asserted.

## Supported-contract dispositions

### Retaining redundant supersets

Let a stored family F cover an incoming set T exactly when some S in F satisfies S⊆T. When a new
context R is accepted, every removed old context P has R⊆P. If P⊆T for a future arrival, transitivity
already gives R⊆T. Retaining P alongside R therefore cannot change the coverage Boolean. Induction
on the same accepted arrivals gives the same traversal decisions and public evidence.

This applies to removing the List.removeIf call entirely, a containsAll result replaced with false,
and the compaction predicate returning false: all retain supersets instead of dropping them. The
compiler's total identity comparisons execute no model code and cannot introduce a new modeled
exception through additional comparisons. Extra comparison/storage cost is deliberately not called
equivalent. Custom equality that throws or mutates state is outside this argument.

An always-true predicate is different: it drops incomparable contexts too. The concrete {1}/{0}/{1}
sequence above disproves inheriting the redundant-superset disposition for that mutation.

### No enabled work

For compiler-produced source actions, every incoming sleeping entry is the exact instruction at
its owner's current PC and is enabled at that position. Initially the set is empty. A selected action is awake, so its own action is not
retained as a sleeper when its PC advances. Filtering retains only independent other actions;
complete guard/error footprints preserve those other actions' enabledness. Newly added sleepers
are currently enabled others with independence and no enable/disable interference. Only the selected
owner's PC changes. A visible-action reset or all-asleep normalization can only remove entries.
Thus the invariant is preserved by each committed transition; terminal attempts add no successor.

When enabledHere is empty, incoming sleepers are consequently empty. Replacing List.isEmpty with
false makes allSleeping true in this one boundary case, resetting an already-empty set. Coverage
and public evidence are unchanged. This is a compiler/source-protocol argument, not a claim that
the constructor rejects every invalid custom-step transition or equality implementation.

## Acceptance

- Six new cases and the full suite pass; build and strict Javadoc pass.
- Fresh baseline/final scoped semantic populations match and all old kills remain killed.
- Record actual changed identities, scoped dispositions and remaining audits without score changes,
  exclusions, whole-class or whole-checker closure claims.
- Default PIT, normalization and the substantive three-file CodeRabbit CLI review complete; valid
  findings receive appropriate local checks. Minor feedback does not trigger extra CLI runs.

## Baseline and verification

Merged PR #77 is `f120d28`. Fresh baseline: 934 tests pass with zero failures/errors/skips;
outer-DporExplorer261/289 KILLED,16 SURVIVED,12 NO_COVERAGE, zero timeout/error statuses.
All **940 tests** pass, including six new cases, with zero failures/errors/skips. Scoped PIT
improves **261/289 → 262/289 KILLED**, 15 SURVIVED and 12 NO_COVERAGE, zero timeout/error statuses.
All 289 semantic identities match (class/method/descriptor/mutator/instruction indices/blocks),
and all 261 old kills remain killed. The sole changed identity is lambda$dporDfs$2/7 with
BooleanTrueReturnValsMutator, killed by incomparableCoverageSurvivesCompaction. The false-return
variant at the same instruction remains a survivor; indices alone do not distinguish these identities.

### Four new compiler-scoped dispositions

These retain the raw SURVIVED status; none is credited as a kill. The proofs and applicability
limits above are part of each disposition.

| Disposition | Method / bytecode index / mutator | Count |
|---|---|---|
| Resetting an already-empty incoming set when no work is enabled | `dporDfs` / 20 / removed List.isEmpty call | 1 |
| Retaining redundant supersets | `dporDfs` / 130 / removed List.removeIf call | 1 |
| Retaining redundant supersets | `lambda$dporDfs$2` / 6 / removed Set.containsAll call | 1 |
| Retaining redundant supersets | `lambda$dporDfs$2` / 7 / BooleanFalseReturnValsMutator | 1 |
| **Total new dispositions** | **Four raw survivors** | **4** |

[Spec35](35-dpor-property-contracts.md) and [Spec36](36-dpor-program-counter-contracts.md) retain
sixteen scoped dispositions. With these four, twenty identities have supported-contract dispositions
(eight survivors/twelve uncovered), while seven additional survivors remain separate open audits:

| Follow-up | Method / indices | SURVIVED |
|---|---|---|
| Visibility refresh and enable/disable interference | `dporDfs` / 569, 570, 582, 663 | 4 |
| Happens-before bookkeeping | `dporDfs` / 540, 546, 554 | 3 |
| **Open total** | **Seven identities** | **7** |

Thus 262 kills plus 20 scoped dispositions plus 7 open identities account for all 289 identities.
The raw 15 SURVIVED and 12 NO_COVERAGE remain honest measurements. The expanded-scope aggregate
is unremeasured; no whole-class/whole-checker closure is claimed.

All 18 visualizer normalization checks pass. CodeRabbit CLI completed review of all three changed
files with zero findings; only measured verification/status prose was finalized afterward. Final
`clean build javadoc pitest` passes with all 940 tests, zero failures/errors/skips, strict Javadoc
and default PIT at 281/291. All 291 default semantic identities/statuses match the merged Spec36
evidence. Remote CI is unverified; this bundle is published as a draft PR. All local Gradle commands use
`--max-workers=1 -PpitestThreads=1`. Browser integration is outside
this bundle because executable production and report output are unchanged.
