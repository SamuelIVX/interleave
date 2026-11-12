<!-- K3 full-product oracles, fresh merged-K2 census, consumer evidence and explicit assurance limits. -->
# 91 — Approximate symmetry DPOR evidence

## TL;DR

K3 implements full mapped sleep-product membership in the existing single-filter checked store.
All four built-in strategies are available through the library and explicit BITSTATE source CLI,
including CLI strategy omission. Existing DPOR algorithms, exact membership, source eligibility,
CBS policy and witness/resource semantics are retained. B9 remains in progress until K3 merges;
B10 and parked roadmap items are not activated.

Fresh baseline: merged K2 PR #133 at `b8e2fa1`. See
[Spec 91](../specs/active/91-approximate-symmetry-dpor.md) and the
[B9 contract](../plans/approximate-symmetry-contract.md).

## Behavioral evidence

- Test-first admission failed at merged K2's explicit DPOR rejection. The missing-product path
  now validates every owner/action, constructs one complete product, probes it, and inserts only
  when absent. Positive lookups do not insert. Accepted VALUE marks share the filter and contribute
  to insertion statistics. No exact product map is retained by the approximate store.
- Independent finite oracle: 480 products over shared values, whole typed frames and every subset
  of four actions, including stale tokens at intermediate/terminal PCs. A literal value order and
  stable physical-ID ties choose the witness independently of production canonicalization.
  Separate Python permutation enumeration and combinatorics give 480 products / 288 identities;
  sleepers are not minimized over extra equal-frame permutations.
- Each target is probed after every unequal product and every VALUE key. A positive candidate's
  skipped mark cannot change bits because all its probes are already set. Fixed fixtures fail on
  observed false positives rather than retuning or skipping. Positive equivalence checks use a
  cleared filter with only the target product. Actual sealed-dispatch exact/approximate explorer
  comparison follows this verified collision-free shadow, including real all-asleep normalization.
- Two/four-owner shared-increment consumer shadows independently enumerate every reachable mask;
  all transitions conflict, so the actual contexts have empty sleep sets. They establish the
  three/five population ranks asserted by repeated/concurrent runner and real CLI checks.
- DataInputStream reads the specified version 1/domain 3 envelope, complete UTF-8 orbit, token count
  and sorted owner/first-occurrence-PC pairs. Reversed caller iteration produces identical bytes.
  Fixed owners stay fixed; interchangeable owners map with the same whole-frame witness. Matching
  aliases encode their first lexical occurrence, not source-label PC; mismatched partitions reject.
- Invalid/foreign contexts leave bits and statistics unchanged, even in a saturated filter. A
  one-bit product collision may hide behavior but does not invent failures or exact PASS. Copy,
  clear, repeat, concurrency, root time stops and later state stops retain independent membership.
- Real all-strategy CLI reports retain BITSTATE/symmetry and APPROXIMATE_PASS. Five DPOR visits
  yield ten insertions (product plus VALUE); independent math/Decimal estimate
  2.559764492176605e-18 uses the existing rounded JSON text 2.56e-18. No formatter/schema change.
  Actual one-bit assertion/runtime failures replay; disabled guards retain deadlock and source
  loop cutoffs retain INCOMPLETE. Later resource choices [0], counters [1,0] and x=1 survive capture,
  explicit replay and safe browser rendering. Mixed separately produced exact/approximate reports
  exclude approximate rows from exact agreement while replaying found violations.

## Measurements and review

Fresh default baseline: **455/468 K + 13 S**. Fresh affected baseline:
**1,255/1,724 K + 81 S + 388 NC**.

Final default: **455/468 K + 13 S**, all 468 exact fingerprints/statuses identical to baseline;
R5/R6/R7 pass with the unchanged 94% floor. The wide affected run measured
**1,276/1,738 K + 80 S + 382 NC**; its one new factory null-guard survivor was then killed by a
compound-invalid-input precedence regression. A separate final store run measures **73/73 K**.
Latest per-class accounting is therefore 1,277 K + 79 S + 382 NC = 1,738, not a second broad run.
No timeout, memory, nonviable or run-error detection is counted as a kill; scoped R6 passes.

| Class (dev.samhb.interleave prefix) | Baseline K / total | Latest K / total | S | NC |
|---|---:|---:|---:|---:|
| search.BitstateSymmetryStateStore | 68 / 68 | 73 / 73 | 0 | 0 |
| search.SymmetryRunContext | 78 / 78 | 102 / 102 | 0 | 0 |
| search.SymmetryStateStore | 44 / 44 | 44 / 44 | 0 | 0 |
| dpor.DporExplorer | 293 / 317 | 293 / 317 | 12 | 12 |
| InterleaveRunner | 123 / 148 | 123 / 148 | 22 | 3 |
| InterleaveRunner.Builder | 43 / 43 | 40 / 40 | 0 | 0 |
| InterleaveRunner anonymous visitor | 30 / 43 | 30 / 43 | 13 | 0 |
| InterleaveRunner.PartialResult | 21 / 21 | 21 / 21 | 0 | 0 |
| InterleaveRunner.LimitState | 4 / 4 | 4 / 4 | 0 | 0 |
| report.BenchmarkHarness | 274 / 300 | 270 / 294 | 22 | 2 |
| report.BenchmarkResult | 55 / 57 | 55 / 57 | 2 | 0 |
| report.ReportWriter | 189 / 197 | 189 / 197 | 8 | 0 |
| cli.Main | 33 / 404 | 33 / 398 | 0 | 365 |

Fingerprint matching retains class, method, descriptor, mutator, description, bytecode indices and
blocks, excluding shifted lines. With the final store replacement, 1,587 fingerprints match:
1,586 statuses remain identical and one inherited harness runProgramWithStore/program-call
survivor becomes K (index 303/block 72). No matched K regresses. The 137 removed and 151 new
fingerprints reflect encoding, constructor-mode and admission rewrites rather than inherited
kill credit. Removing obsolete strategy restrictions reduces Main's NC by six; this is not six
newly killed CLI mutations. DPOR's 317, exact store's 44, ReportWriter's 197 and BenchmarkResult's
57 fingerprints/statuses are entirely unchanged.

The temporary factory survivor removed Objects.requireNonNull(strategy): the shared context
caught a null later for otherwise valid settings. The final compound-invalid case verifies eager
null-strategy rejection before budget validation, retaining the original API order. Store-only
XML is `/private/tmp/interleave-k3-final-store.xml`. The wide census preceded the minor/trivial
consumer refinements and this final factory assertion; production logic was unchanged by those
assertion updates. Final integrated build/tests/Javadoc pass afterwards.

All 79 remaining survivors match inherited survivor fingerprints: DPOR 12, runner 22 plus visitor
13, harness 22, BenchmarkResult 2 and ReportWriter 8. They retain their raw dispositions and
ownership; none is newly declared universally equivalent. NC remains Main 365, DPOR 12, runner 3
and harness 2. No new final survivor or uncovered site is concealed by population totals.
Raw baseline/final XML is saved independently under
`/private/tmp/interleave-k3-{baseline,final}-{default,affected}.xml`.
Only measured bookkeeping can change: no weaker operators, targets, tests, resources or 94% floor.
Scoped R5/R7 are not full-default checks; timeout/run-error detections will not be called kills.

Final build/strict Javadoc and all **1,907 tests** pass (zero failures/errors/skips); reviewed
browser verification passes **185 checks**. An external Java 26 jar consumer checks DPOR ranks,
all-strategy reports and product/VALUE insertion diagnostics. CodeRabbit 0.8.1 completed all 20
submitted files with two Minor and one Trivial finding. All were valid and patched: distinct
assertion/runtime artifact names, explicit failure kind/category assertions, and the active resource
cell's actual owner column (including no Thread 1 action). No CLI rerun for these minor/trivial fixes.
Connected review traced complete-key dispatch, stable mappings, source validation and retained data. The affected scope includes
existing exact/ordinary DPOR as well as new encoding, membership, selection and reporting paths.
Main's child-JVM CLI boundary and inherited runner/harness residuals remain visible rather than
being presented as covered merely because real CLI/public tests pass.

## Limits

The bit budget covers membership per live store. Source metadata, temporary encodings, traversal,
diagnostic configuration maps and traces remain outside it. Exact symmetry retains its exact map;
ordinary DPOR retains its existing antichain. The checked approximate branch uses neither table;
its unused empty compatibility map is not retained per-visit membership.

No calibrated false-positive probability, coverage percentage, total-process memory bound, speedup,
stronger sleep equivalence or expanded source eligibility is claimed. B9 completion awaits merge.
