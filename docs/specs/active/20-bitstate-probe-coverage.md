# Spec: Bitstate probe coverage

## Objective

Protect actual multi-probe Bloom behavior rather than only its estimated false-positive formula.
Recheck the remaining three bitstate mutations under roadmap 9-H2 and retain unsupported
equivalence or verdict claims as explicit limitations.

## Scope

- Package: `dev.samhb.interleave.state.BitstateStoreDiagnosticsTest`.
- Modifies: that test class, this spec, the roadmap and historical bitstate disposition notes.
- Off-limits: production hashing, public APIs, dependencies, exclusions and mutation gates.

## Non-Goals

Pinning hash values, exact bit positions or specific false positives; asserting that approximate
storage proves safety; requiring every probe to be distinct for every state or capacity; exhaustive
hash-distribution benchmarking. The existing encoder-flush equivalence argument is reused.

## Invariants

- Both marking APIs use the configured probe count to populate their owned Bloom vectors.
- Every marked configuration remains visited under its marking context and cost.
- Collisions and false positives remain permitted; exact-store equivalence does not transfer.
- Public metrics reflect actual populated bits, independently of the formula-based estimate.

## Requirements

1. WHEN one- and four-probe stores receive the same fixed low-load plain workload, THE SYSTEM SHALL
   populate more bits with four probes and retain every marked configuration.
2. WHEN that workload is marked in two bounded vectors, THE SYSTEM SHALL populate more bits with
   four probes in each vector independently and retain marked contexts at their recorded costs.
3. WHEN updating mutation dispositions, THE SYSTEM SHALL distinguish actual occupancy from the
   historical verdict-only sweep; unchanged verdicts do not establish metric equivalence.

## Acceptance Criteria

- Use the existing counter fixtures: values 1–32, counters `[0,0]`, capacity 1,000,003, k=1 versus
  k=4. Bounded workloads mark `(lastThreadId,cost)` pairs `(0,0)` and `(1,1)`; compare before/after
  cardinalities at each level so one vector cannot mask a missing probe contribution in the other.
- Assertions compare actual bit cardinalities without naming hash values or bit indices.
- All marked states/contexts remain visited; no assertion demands absence for unmarked states.
- Fresh scoped PIT identifies any new kills and retains all baseline kills by semantic identity.
- Every remaining bitstate mutation has an explicit disposition; no exclusions or gate changes.
- Full build/tests/Javadoc, default mutation ratchet, normalization and CodeRabbit CLI review pass.

## Design

Add separate plain and bounded regressions in the existing diagnostics class, reusing `markPlain`,
`markPreemption` and `configurationWithCounter`. Compare `bitCount()` after identical insertion
workloads at ample capacity. This checks probe use, not the theoretical estimate that depends only
on k, insertion count and capacity. Permit ordinary collisions and avoid exact occupancy literals.

The multiplication-to-division mutation in `doubleHash` can collapse the extra probes onto the
first bit for this workload. Arithmetic sign changes may still yield valid approximate mappings;
they must not be declared equivalent merely because a selected verdict corpus cannot distinguish them.

## Current State

- [verified] Baseline is merged PR #60 at `64cd0e3`.
- [verified] Production probing uses double hashing and exposes actual occupancy through bitCount.
- [verified] The retained encoder-flush mutant concerns an unbuffered ByteArrayOutputStream sink;
  removing its flush does not change emitted bytes. Spec 12.01 owns that proof and regression.
- [verified] Fresh merged-main baseline passes 653 tests, none skipped; scoped bitstate PIT is
  94/97 assertion-confirmed kills, three survivors, zero uncovered and zero timeout/error detections.

## Tests

- `fourProbesPopulateMoreBitsThanOneForPlainWorkload` — R1.
- `fourProbesPopulateMoreBitsThanOneForBoundedWorkload` — R2.
- Semantic mutation ledger and corrected historical notes — R3.

## Constraints

- Dependencies: shipped bitstate mode, Spec 12.03 diagnostics and Spec 19's exact-store distinction.
- Preserve R7's ban on literal hash assertions and keep the old verdict sweep explicitly historical.
- Local Gradle commands use `--max-workers=1 -PpitestThreads=1`.
- H2 is triage and selective hardening; retained, justified non-kills are not a failed 100% target.

## Verification

Both occupancy regressions pass. Scoped bitstate PIT improves **94/97 → 95/97 assertion-confirmed
kills**. All 97 semantic identities are unchanged and all 94 baseline kills remain killed.
There are two survivors, zero uncovered and zero timeout/error detections. PIT identifies the plain
workload regression as the killing test for the multiplication-to-division probe mutant.

### Semantic mutation ledger

All rows belong to `dev.samhb.interleave.state.BitstateStore`, use
`org.pitest.mutationtest.engine.gregor.mutators.MathMutator`, and were SURVIVED in the fresh baseline.
`doubleHash` has descriptor `(I)[I`; `preemptionHashIndices` has descriptor
`(Ldev/samhb/interleave/core/Configuration;I)[I`. Index/block pairs exclude source line numbers.

| Method | Index / block | Change | Final status / disposition |
|---|---|---|---|
| doubleHash | 39 / 5 | Multiplication → division | KILLED by `fourProbesPopulateMoreBitsThanOneForPlainWorkload`; extra probes collapse instead of populating more bits. |
| doubleHash | 40 / 5 | Addition → subtraction | SURVIVED; changes the approximate mapping and possible false positives. No violated public contract is demonstrated; retain without claiming equivalence. |
| preemptionHashIndices | 10 / 1 | Addition → subtraction | SURVIVED; changes the last-thread salt and possible false positives. No violated public contract is demonstrated; retain without claiming equivalence. |

The two retained mappings remain deterministic, produce in-range indices and use the same mapping
for marking and querying. A marked state/context therefore remains visited. This is narrower than
equivalence: different false positives can affect explored schedules and bug discovery. No assertion
pins a particular false-positive pattern or demands absence for an unmarked state. The historical
verdict sweep is not rerun. Its claimed 1,080 cases conflict with the listed factors, which multiply
to 840; the raw execution record is unavailable, so the executed population remains unverified.
This arithmetic correction is not a new measurement, and no current acceptance claim relies on
that sweep or extrapolates it to current source-language models.

Clean build, strict Javadoc and all **655 tests** pass, none failed or skipped. The default mutation
ratchet passes **281/291 assertion-confirmed kills**, ten survivors, zero uncovered and zero
timeout/error detections; the 94% floor and all gates remain unchanged. Its retained population is
seven exact-store total-function survivors (Spec 19), these two approximate-mapping survivors and
the encoder-flush survivor (Spec 12.01). H2 triage is complete with these explicit dispositions;
this does not claim complete mutation coverage. All **18 visualizer normalization checks** pass.

Post-feedback clean build, strict Javadoc and all 655 tests pass again, none failed or skipped.
Default PIT/ratchet remains 281/291 with ten survivors and no uncovered or timeout/error detections.
The final bitstate portion is 95/97 with unchanged identities and no lost baseline kills.

Independent reader review found no concrete issues. Two CodeRabbit CLI rounds completed across
all five files (`review_completed`, not skipped). Dispositions:

- First round, minor: aggregate bounded occupancy could mask a per-vector omission. Valid; compare
  each level's before/after occupancy independently, then rerun integrated verification.
- Follow-up, minor: reconcile the historical 1,080 total with factors yielding 840. Valid; inspect
  the historical commit and available artifacts, mark the actual sweep population unverified in
  all three notes, and avoid using the old claim as verification evidence.
- Follow-up, minor: compute an expected density from live bitCount divided by two vector capacities.
  Not adopted: this repeats the density formula and duplicates vector-ownership coverage.
  `StateStorePreemptionTest.bitstateStore_metrics_countOnlyVectorsInUse` independently asserts two
  occupied one-bit vectors, bitCount=2 and density=1.0 despite unused vectors. It detects a lost
  vector or wrong denominator with literal expectations; the new tests focus on probe use.

The density recommendation is explicitly declined, not reported as a zero-finding follow-up.
Only historical-count and verification prose changed after follow-up review; test code did not.
The sweep-count correction and Spec 19 pointer were also propagated to the set README afterward;
that sixth documentation file was checked locally, not included in the five-file CLI reviews.
Browser integration is not rerun locally because production output and metadata propagation are unchanged.
