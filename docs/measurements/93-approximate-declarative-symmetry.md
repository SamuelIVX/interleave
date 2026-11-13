<!-- Fresh merged-L1 baseline and bounded assurance for complete approximate declarative symmetry. -->
# 93 — Approximate declarative symmetry evidence

## Status and baseline

L2 is implemented and locally verified; merge is pending. B10 remains in progress until
L2 merges. See [Spec 93](../specs/active/93-approximate-declarative-symmetry.md) and the
[B10 contract](../plans/declarative-symmetry-contract.md). Roadmap #11/#12 and parked work stay excluded.

Fresh baseline: merged L1 PR #136 at **013c9630e13b9835f435e4344837bf3b10e673db**, measured in an
untouched detached worktree. Baseline build/Javadoc passes **1,948 tests**, no failures/errors/skips.
Default PIT: **455/468 K + 13 S**. Affected scope: **233/235 K + 2 S**, including BitstateSymmetryStateStore,
SymmetryRunContext and nested types, and BenchmarkResult. Source/ordinary controls are produced by the
same full suite. No older B9/L1 measurement is substituted for this baseline.

## Implementation and assurance

The production behavior changes are intentionally small: remove the temporary non-source rejection
from the checked approximate factory; normalize PASS/BITSTATE for source OR checked symmetry reports.
Existing shared certification, concrete execution, positive-witness attestation, product bytes,
Bloom hash/probes, one-filter allocation and CBS iteration policies remain unchanged. CLI/help and
applicability documentation describe the new matrix. L1 already supplied the needed viewer/runtime
precision predicates, so L2 verifies them without introducing another normalization/replay path.

| Contract | Independent or concrete evidence |
|---|---|
| Admission / R1 | Direct factory, runner, harness and actual child-JVM CLI select all four strategies. Invalid settings, untrusted properties, typed input, foreign bindings/alias partitions and conflicting owned/custom settings reject. Existing source/public-constructor trust regressions remain. |
| Typed VALUE / R2 | 75 positions: five shared values × fifteen original full-frame pairs. Explicit two-owner permutations compare PC/int/bool/array frames without production grouping, key encoding or canonicalization. Mark all other value identities before each absent target. |
| CBS products / R2–R3 | 675 products: 75 positions × three last-owner values × three costs. Try both full permutations and mathematical cost dominance independently. Insert all noncovering products plus VALUE marks before every absent target; check all deliberate positive coverage. |
| DPOR products / R2–R3 | 1,200 products: 75 positions × sixteen owner/action subsets. A declared independent frame order chooses one stable witness; equal-frame ties retain original IDs. Every other independently distinct full product and every VALUE position precedes each absent lookup. Check every equal product against its representative. No subset/value shortcut defines coverage. |
| Actual equality premise | The finite context universes include all positions/costs/sleep subsets of the actual two-owner, two-increment fixture. Only after the shadows show no false-positive decisions do fresh checked stores compare traversal counts. DFS/POR/DPOR visit six value orbits; CBS is compared with exact checked CBS at each bound. Actual DPOR covers both absent and ALWAYS properties. Concrete witnesses validate enabled original choices/outcomes, x=4 at termination, and actual preemption cutoffs. |
| Wire and stale aliases | Independently Python-packed DynamicState bytes yield literal VALUE/CBS/DPOR headers, lengths, complete orbit strings and sorted integer suffixes. A stale aliased action retains its first occurrence and mapped owner; mismatched alias partitions reject. Version/domain/hash algorithms are unchanged. |
| Atomicity and budget / R3 | Invalid owner/action/cost contexts preserve bit count and heuristic, even under saturation. VALUE interference shares the same budget. Duplicate marks count as insertions; clear/copy reset history/filter without affecting other copies. The three-candidate heuristic is independently derived with Decimal and floating-point exp, not production code. |
| Execution / R4 | Repeated/concurrent runners isolate filters. Actual root/later state/time stops retain INCOMPLETE and no source prefix. Saturation-hidden cutoffs do not stop CBS deepening early; producing bound and metrics match its single pass rather than accumulating discarded passes. Real observed failures retain their actual bound without a minimum-proof claim. |
| Precision / R5 | A one-bit filter can hide a real error and emits APPROXIMATE_PASS, including an empty/no-trace library result. Mixed exact/approximate rows preserve exact agreement exclusions. Actual deadlock, root/final property failure, error and later interruption precedence are checked separately. |
| Positive evidence / R6 | Existing positive-attestation cases now run independently for EXACT and BITSTATE: enabled real errors with true invariants validate; disabled/invented/trailing/outcome-mismatched traces, foreign evidence and untrusted properties fail. Committed failure snapshots never export tentative x=7. |
| Consumers / R7 | Actual CLI emits one explicit strategy or all four if omitted, truthful JSON/Markdown and observed CBS bound. Fresh report, completed/no-trace, resource and failure library/CLI files feed normalization and browser checks. JSON gains no SOURCE identity or authenticated resource prefix. |

The independent finite counts describe bounded fixtures, not arbitrary-model soundness, a calibrated
probability or a general reduction percentage. Forced collisions deliberately do not assert exact
absence or guaranteed discovery of every exact failure.

## Verification

- Admission and precision tests each failed before their production fix, then passed. Early test
  development also exposed a non-CBS builder bound, an incorrect assumption that every CBS trace
  completes, and a report metric read at the wrong JSON level. Tests now respect the actual public
  contracts and distinguish bounded prefixes; no production defect was hidden by weakening a test.
- Final integrated build/strict Javadoc: **1,963 tests**, zero failures/errors/skips. Explicit
  deadlock/property/partial-precedence and heuristic-history assertions pass. Private-member doclint
  passes for the checked store, benchmark row and harness with all groups and warnings as errors.
- Fresh normalization/browser suite: **248 assertions passed**, zero console errors, including all
  prior source controls and the complete approximate declarative artifact set.
- External Java 26 consumer compiled against the actual built jar and Gson: all twelve exact/
  approximate strategy selections, four direct factories, report precision, non-source identity and
  typed/untrusted-property guards pass.
- Workflow YAML parses; all 163 local Markdown targets/anchors and `git diff --check` pass.

## Mutation evidence

All Gradle/PIT runs use one worker and one minion; thresholds/operators/default target tests remain
unchanged. K = KILLED, S = SURVIVED. No census below contains uncovered/resource/run-error statuses.

| Scope | Fresh merged baseline | Feature |
|---|---|---|
| Default state/cb gate | 455/468 K + 13 S | 455/468 K + 13 S |
| Affected total | 233/235 K + 2 S | 231/233 K + 2 S |
| BitstateSymmetryStateStore | 76/76 K | 73/73 K |
| SymmetryRunContext | 102/102 K | 102/102 K |
| BenchmarkResult | 55/57 K + 2 S | 56/58 K + 2 S |

The final default census retains all 468 baseline fingerprints and statuses, with no uncovered or
resource/run-error cases. The affected census has 219 matching instruction fingerprints and all retain status. Sixteen old and fourteen new fingerprints
reflect guard removal and shifted constructor indexes; removed mutants are not kills. The two
BenchmarkResult residuals correspond to the existing default-empty snapshot argument and the source
empty-incomplete-list guard. The constructor normalizes null snapshots to empty; the empty-prefix
capture attempt is caught and likewise yields empty snapshots. These bounded explanations preserve
the explicit source guard/default and make no general whole-checker equivalence claim.

Raw XML retains all identities/statuses. No affected percentage is presented as a whole-checker score.
The additional test-only assertions after the affected census do not alter its production population.

## Review and raw evidence

Ordinary connected review checked shared trust, fixed filter allocation, original execution,
fail-closed positive validation and source/ordinary compatibility. Actual CodeRabbit CLI 0.8.1
completed the full **21-file** implementation/spec/test/consumer/ledger review with **zero findings**.
The completion event is preserved; a heartbeat or previous L1 review is not substituted for it.

Raw local prefix: `/private/tmp/interleave-l2-`:

- `baseline-build.log`, `baseline-default.log`/`.xml`, `baseline-affected.log`/`.xml`.
- `admission-red.log`, `precision-red.log`, `admission-precision-green.log`, `oracle-first.log`,
  `oracles-attestation.log`, `lifecycle.log`, `consumers.log`, `integrated-build.log`, `final-build.log`,
  `browser.log`, `browser-final.log`, `private-javadoc.log`.
- `final-affected.log`/`.xml`, `affected-comparison.txt`, `final-default.log`/`.xml`,
  `default-comparison.txt`, `coderabbit.ndjson`. All runs/reviews completed. The default/affected
  production populations were measured before the last test-only assertions; no production change
  followed those measurements.

## Limits and release boundary

Approximation can miss behavior. Bloom membership diagnostics are an idealized single-lookup/capped
cost-union heuristic, not calibrated behavior coverage; the configured budget bounds membership,
not total JVM memory. A discovered CBS bound is observed, not proven minimal. Source-native evidence,
cutoff shortening, schemas and generic replay remain separate. L1 exact HASHED/ORDERED remains available.
Publish one complete L2 PR and stop. Only after Sam reports its merge should B10 be marked complete
in both trackers/context, explicitly reported complete, and work stop before #11/#12 or parked tasks.
