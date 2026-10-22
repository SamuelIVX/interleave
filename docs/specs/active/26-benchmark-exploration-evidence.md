# Spec: Benchmark verdict qualification and exploration evidence

## Objective

Protect benchmark-row evidence attachment and source/approximate verdict qualification as a
focused roadmap 9-V1 batch. Verify preserved row contents, not recomputed timing/store formulas.

## Scope

- Extends `BenchmarkResultTest`; updates this spec, the roadmap and verdict getter Javadoc.
- No executable production/API, dependency, mutation scope/mutator/exclusion or gate changes.
  Getter Javadoc now mentions the existing source-model verdict labels.
- Explorer verdict aggregation, soundness attestations, serialization and reduction semantics
  remain separate. This does not remeasure the historical expanded mutation population.

## Invariants and Requirements

1. WHEN evidence is attached, THE SYSTEM SHALL qualify PASS as APPROXIMATE_PASS only for source
   models with BITSTATE storage; exact, legacy/absent contexts and non-PASS verdicts retain labels.
2. WHEN attaching mixed terminal evidence, THE SYSTEM SHALL preserve the original row's measured
   fields, failing trace and failure snapshots without modifying that original row.
3. THE SYSTEM SHALL retain only INCOMPLETE traces in encounter order, derive distinct reasons in
   enum order, and capture the first incomplete prefix's snapshots when source replay succeeds.
   Failed optional replay SHALL leave snapshots empty without discarding evidence.
4. THE SYSTEM SHALL expose immutable evidence/snapshot lists; null constructor failure snapshots
   SHALL normalize to an immutable empty list and remain empty after attachment.

## Design and Tests

The verdict matrix uses seven literal cases: source EXACT/BITSTATE PASS, source BITSTATE
VIOLATION/DEADLOCK/INCOMPLETE, and legacy/absent BITSTATE PASS. It tests `withExploration`'s row
qualification, not whether a search computed its supplied verdict correctly.

The mixed-evidence source model has three threads: one writes 7 then 8 before a limit-zero loop;
one writes 9; one errors on modulo zero. Fixed schedules create an actual failure and two loop
cutoffs with different snapshot histories. A source-aware K=0 preemption witness after the first
write identifies an enabled alternative while thread zero remains enabled. Input order is failure,
preemption, loop, loop: filter/order assertions and literal `[LOOP_LIMIT, PREEMPTION_BOUND]` reasons
must distinguish filtering, duplicate reasons and enum ordering. Expected representative snapshots
are literal counter values `[0,7]`, distinct from both loop histories.

The row carries sentinel measurements (17 states, time 23, heap 31, FPR 0.125, eight set bits,
density 0.25, K=0) and two literal initial-state failure snapshots. These are provided payloads,
not benchmark measurements or recalculated Bloom estimates. Exploration's separate 999-count
sentinel must not replace the row's 17. Mutating the caller's original snapshot list cannot affect
stored failure snapshots; attaching evidence cannot change the base row's empty incomplete lists.

| Requirement | Regression | Cases |
|---|---|---|
| R1 | `qualifiesOnlySourceBitstatePass` | Seven qualification/context cases, including unchanged original rows |
| R2–R4 | `attachesOrderedEvidenceWithoutReplacingMeasurementsOrFailureSnapshots` | Mixed authentic trace prefixes, preserved measurements/failure snapshots, representative replay, reason order and immutable output lists |
| R4 | `nullFailureSnapshotsRemainEmptyAndImmutable` | Null snapshot normalization through attachment |
| R3 | `incompleteSnapshotsAreBestEffort` (existing, unchanged) | Corrupt optional cutoff replay retains evidence/verdict and leaves snapshots empty |

## Acceptance Criteria

- Nine new invocations plus the existing suite pass without production behavior changes.
- Compare fresh merged-main/final BenchmarkResult mutation identities/statuses, retaining all
  baseline kills and documenting residual behavior rather than forcing a 100% score.
- Full build/tests/Javadoc, unchanged default mutation gate and normalization pass.
- CodeRabbit CLI reviews the four-file bundle; independently disposition any findings.

## Baseline

Merged PR #66 is `618c8a2`. Fresh baseline passes 771 tests, no failures/errors/skips;
BenchmarkResult PIT is **41/54 assertion-confirmed kills**, 12 survivors and one uncovered,
zero timeout/error detections. The existing target override measures this class independently
of default state/CBS gates. Local Gradle always uses `--max-workers=1 -PpitestThreads=1`.

## Verification

All **780 tests** pass (nine new invocations), zero failures/errors/skips. BenchmarkResult PIT
improves **41/54 → 52/54 assertion-confirmed kills**: ten survivors and one uncovered mutation
become killed; two survivors remain, zero uncovered/timeouts/errors. All 54 semantic identities
match and all 41 baseline kills remain killed. Comparison uses class/method/descriptor/mutator plus
instruction indices and blocks, not line numbers.

### Scoped mutation ledger

All identities belong to `dev.samhb.interleave.report.BenchmarkResult`. Constructor aliases below
identify overloads: **copy** is `(BenchmarkResult, Program, DfsResult)`; **full** ends in
`(Integer preemptionsUsed, List<StateSnapshot> states)`; **delegating** is the corresponding
public overload ending in `Integer preemptionsUsed` without a snapshot-list argument.

| Baseline → final | Method / indices | Contract / disposition |
|---|---|---|
| SURVIVED → KILLED (2) | copy constructor / 54, 59 | Verdict equals/store conditional changes; seven qualification cases retain only the allowed source/bitstate PASS adjustment. |
| SURVIVED → KILLED (3) | copy constructor / 123, 127, 136 | Snapshot guard/first-prefix selection; a real nonempty representative history must survive successful replay. |
| SURVIVED → KILLED (1) | copy constructor / 117 | Removed empty snapshot default; context/no-prefix rows must expose an empty list, not null. |
| NO_COVERAGE → KILLED (1) | full constructor / 70 | Removed null-snapshot normalization; attached and original states must stay immutable empty lists. |
| SURVIVED → KILLED (1) | full constructor / 87 | Removed default list construction; the untouched original row's incomplete lists remain empty and non-null. |
| SURVIVED → KILLED (2) | `estimatedFalsePositiveRate` / 5; `incompleteStates` / 5 | Default return replacements; nonzero metric and literal representative snapshots remain observable. |
| SURVIVED → KILLED (1) | `lambda$new$0` / 14 | Always-true filter; failure evidence must not enter the ordered incomplete list. |
| SURVIVED (1) | copy constructor / 126 | Removed List.isEmpty call; row-output-equivalent under the owned-list/best-effort policy described below. |
| SURVIVED (1) | delegating constructor / 18 | Removed List.of argument; the full constructor normalizes the replacement null to the same empty snapshot value. |

### Retained survivor boundaries

The incomplete-list emptiness mutation returns false instead of calling `isEmpty`. For a nonempty
owned list the branch is unchanged. For an empty source list it attempts `getFirst`, which throws
inside the existing RuntimeException catch and restores the same immutable empty snapshots as the
original skipped branch. Non-source contexts short-circuit both versions. The list is created by
filtering the immutable exploration traces, not supplied as a callback-bearing custom collection.
Stored row/evidence values are unchanged; this does **not** claim equal work, exception allocations
or performance. The guard and best-effort catch remain in production.

The delegating constructor normally supplies `List.of()` as the failure-snapshot argument. Its
mutation supplies null; the full constructor's existing null normalization stores the same empty
snapshot value. No snapshot-list identity or resource/allocation equivalence is claimed. Both
identities remain visible in PIT; no exclusions or score-floor changes are made.

Clean integrated `build javadoc pitest` passes all 780 tests, zero failures/errors/skips, with
strict Javadoc doclint. Default PIT remains **281/291 kills**, ten survivors, zero uncovered and
zero timeout/error detections; all semantic identities/statuses match merged PR #66's evidence.
The 94% floor, scopes, mutators and exclusions are unchanged; inherited informational per-class
baseline notices remain outside this bundle. After the getter-only Javadoc correction, build,
Javadoc, all tests and BenchmarkResult PIT pass again with identical 52/54 scoped statuses.
All 18 visualizer normalization checks pass. Browser integration is not rerun because executable
production output and browser code are unchanged. GitHub CI has not been checked.

Two CodeRabbit CLI rounds completed with **zero findings**: the initial three-file test/spec
bundle, then the final four-file scope including the getter Javadoc correction. Only measured
verification/status prose was finalized afterward. Scoped credential-pattern scan, relative
Markdown links and diff whitespace checks pass. No executable production/API or dependency changes.
Roadmap 9-V1 remains open; next work needs fresh explorer verdict/soundness/reduction scopes.
