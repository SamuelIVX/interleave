<!-- K2 fresh-main baseline, CBS product oracles, producing-pass evidence and coverage limits. -->
# 90 — Approximate symmetry CBS evidence

## TL;DR

K2 adds checked approximate CBS in one shared logical bit budget. It also corrects the shared
ordinary/checked approximate exhaustion inference: no observed cutoff is not proof of exhaustion.
Exact early exit is preserved. The producing bound is an observation, not a proven approximate
minimum; resource limits and actual witness precedence remain intact. DPOR remains K3.

Baseline is merged K1 PR #132 at `aba13cc`, measured in a separate detached worktree.
[Spec 90](../specs/active/90-approximate-symmetry-cbs.md) and the
[set contract](../plans/approximate-symmetry-contract.md) own the approved semantics.

## Behavioral evidence

- Red tests reproduced both early exits on merged K1: the ordinary one-bit runner invoked its
  factory once rather than three times, and the harness reported bound zero rather than two.
  Precision-aware predicates now continue approximate passes and preserve exact no-cutoff exit.
  Completed runner counts continue to describe the producing pass; global resource allowance
  still counts visits across passes.
- Independent complete-product oracle: 270 typed frame/shared-value/last-owner/cost products,
  explicit identity and swapped-owner permutations, and q<=p coverage. Each absent target is
  tested after inserting every noncovering mark plus all VALUE keys. A separate 36-product
  counter shadow establishes collision-free decisions for the actual resource consumer fixture.
  Fixed fixtures fail on a detected false positive rather than retuning or skipping assertions.
- Literal fragment case: cost 4/8 marks set {0,2,4,6,7,14} in a 17-bit filter. Query cost zero
  needs {13,7,1,12}, cost one needs {14,10,6,2}. Each ordinal has a matching candidate fragment,
  but neither complete key is covered. Public lookup rejects both; a cost-zero mark then covers one.
- The typed key is read independently with DataInputStream: version 1, CBS domain 2, full UTF-8
  context-aware orbit length/payload and cost suffix. Equal-frame last owners map together;
  initial -1 remains distinct. Canonicalization and encoding happen once per query; one buffer
  supplies independently hashed complete candidate costs without combining probe fragments.
- FPR literals for n=2,m=8,k=2: single lookup 0.15481812174617549; pMax=2 gives approximately
  0.46445436523852646. Python math/Decimal routes independently cross-check the values. Queries,
  not marks/capacity, update pMax. Invalid costs/owners leave bits/statistics unchanged. Copy/clear
  reset history; Integer.MAX_VALUE capacity uses no per-cost allocation or overflowing pMax+1.
- Owned checked modes never add stores to the external-factory identity-history set. External
  approximate factories reject identical-instance reuse and accept distinct equals-colliding
  adapters using real Bloom membership. No GC-timing assertion or total-heap bound is claimed.
- Actual CLI artifacts cover saturated iterative absence at producing bound two, replay-attested
  failure and loop cutoff. The library resource artifact stops in a later pass at choices [0],
  counters [1,0], x=1 rather than retaining the prior pass's terminal x=2. Original interruption,
  BITSTATE and symmetry markers survive serialization and explicit replay.
- Build/strict Javadoc and browser verification are run with local single-worker Gradle flags;
  CI remains parallel. Browser checks include all earlier artifacts and the four new CBS shapes.

## Mutation baseline and release checks

Fresh default baseline: **455/468 K + 13 S**. Fresh affected baseline:
**990/1,437 K + 70 S + 377 NC**. These are actual merged-main runs, not K1 numbers carried forward.
Final default: **455/468 K + 13 S**, all 468 exact fingerprints/statuses identical to the fresh
baseline. R5/R6/R7 pass with the unchanged 94% floor. No population bookkeeping edit is needed.
Final affected: **1,049/1,494 K + 69 S + 376 NC**; scoped R6 passes. No timeout, memory,
nonviable or run-error outcome is counted as a kill. Build/strict Javadoc and all **1,896 tests**
pass (zero failures/errors/skips); the reviewed browser suite passes **160 checks**. An external
Java 26 jar consumer exercised observed ceiling two, resource metadata and MAX_VALUE capacity.

| Class (dev.samhb.interleave prefix) | Baseline K / total | Final K / total | Final S | Final NC |
|---|---:|---:|---:|---:|
| search.BitstateSymmetryStateStore | 33 / 33 | 68 / 68 | 0 | 0 |
| search.SymmetryRunContext | 67 / 67 | 78 / 78 | 0 | 0 |
| InterleaveRunner | 118 / 144 | 123 / 148 | 22 | 3 |
| InterleaveRunner.Builder | 42 / 42 | 43 / 43 | 0 | 0 |
| InterleaveRunner anonymous visitor | 30 / 43 | 30 / 43 | 13 | 0 |
| InterleaveRunner.PartialResult | 21 / 21 | 21 / 21 | 0 | 0 |
| InterleaveRunner.LimitState | 4 / 4 | 4 / 4 | 0 | 0 |
| cb.ContextBoundedExplorer | 131 / 131 | 131 / 131 | 0 | 0 |
| report.BenchmarkHarness | 267 / 296 | 274 / 300 | 24 | 2 |
| report.BenchmarkResult | 55 / 57 | 55 / 57 | 2 | 0 |
| report.ReportWriter | 189 / 197 | 189 / 197 | 8 | 0 |
| cli.Main | 33 / 402 | 33 / 404 | 0 | 371 |

Fingerprint comparison retains class, method, descriptor, mutator, description, bytecode indices
and blocks, excluding shifted lines. **1,274** fingerprints match: 1,273 statuses remain identical
and one inherited harness cutoff-predicate true-return survivor becomes K (lambda$runCbsIterative$1,
index 18/block 7). No matched K regresses. The 163 removed and 220 new fingerprints belong to
rewritten constructor/factory/iteration/metric populations, not 57 inherited kill claims.
The affected run preceded the two trivial assertion refinements and final mixed-evidence test; production was unchanged, and
all final revised tests subsequently passed. No additional affected aggregate is implied.

All 69 raw survivors remain accounted for above: runner 22 plus anonymous visitor 13;
harness 24; BenchmarkResult 2; ReportWriter 8. Of these, 68 match inherited survivor fingerprints;
one is a new harness ownership-guard mutation. None is declared universally equivalent.
NC consists of Main 371, runner 3 and harness 2.

The new survivor is BenchmarkHarness.runCbsIterative's symmetry-mode guard,
NegateConditionalsMutator at index 34/block 5. Negating it retains owned checked stores and skips
ordinary identity history; the public harness always constructs fresh stores, so functional results
at the tested bounds remain unchanged. Functional public tests do not prove heap retention.
Connected-source review verifies that the actual guard never adds owned stores to the history set;
no GC-timing test, private-reflection hook or whole-process memory proof is substituted for that
limited evidence. Owner: CBS lifecycle/ownership; revisit deterministic retention verification if
an appropriate lifecycle test interface is approved. The new survivor remains visible as S.
Main adds **two NC** at the new selection condition; its 369 inherited NC persist. Harness and
runner retain the visible remaining incomplete coverage boundaries. These are not mutation kills
merely because actual CLI or public-library cases pass.

Raw XML is kept separately under `/private/tmp/interleave-k2-{baseline,final}-{default,affected}.xml`.
Only measured bookkeeping may change; operators, default targets, tests, resource bounds and the
94% floor remain intact. Scoped R5/R7 are not the full default gate. No timeout or run error will
be presented as an assertion-backed kill.

Main's inherited child-JVM CLI mutation boundary remains visible: end-to-end commands execute
real valid/invalid selection paths, but parent-process PIT cannot claim their child execution
as kills. The table and lineage above distinguish all new NC, inherited survivors and rewritten sites.
No exact equivalence or correctness claim is inferred from an aggregate percentage.

## Review and limits

CodeRabbit 0.8.1 completed all 21 submitted files: one Major and two Trivial findings. The Major
proposed weak identity tracking for external factories. This was not applied: Spec 90's Design
explicitly retains the external-factory reuse guard and scopes the retention correction to owned
stores; replacing it and adding GC-timing tests would expand the approved change. Owned modes
already bypass the history set. Actual shared/alternating-instance and equals-collision tests
retain identity-based rejection. Severity is recorded as returned, not silently downgraded.

Both Trivial findings were valid and patched: clear the oracle filter before checking coverage
from a single target mark, and assert actual rendered resource choices/counters/x rather than only
identity/verdict. The updated browser suite passes 160 checks. No CLI rerun for these trivial fixes.
Connected-code review includes input validation, last-owner
mapping, whole-key cost dominance, both iterative loops, ownership, producing-pass metrics,
resource prefixes and actual JSON/browser consumers are included.

Lookup work is linear in requested cost and probes for a fixed source key size. Large library
capacities do not increase allocated filter count; they can increase CPU work. The CLI bitstate
ceiling remains two. No calibrated probability, coverage percentage, minimum approximate bound,
whole-process memory bound or speedup is claimed. Broader source eligibility and DPOR are excluded.
