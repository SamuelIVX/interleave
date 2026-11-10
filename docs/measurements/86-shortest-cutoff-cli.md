<!-- Measured CLI proof-presentation verification, raw coverage limits and consumer evidence. -->
# 86 — Shortest cutoff CLI evidence

## TL;DR

[Spec 86](../specs/active/86-shortest-cutoff-cli.md) implements B7 / I2 on merged I1
PR #127 at `fc10c62`. Fresh baseline build/strict Javadoc and **1,811 tests** pass.
Final integrated build/strict Javadoc and **1,815 tests** pass, zero failures/errors/skips.
All **23 new payload-helper mutants are KILLED**. CLI process tests do not provide in-process
parser mutation coverage: report its uncovered sites honestly rather than counting them as kills.

## Affected census and lineage

Same PIT configuration/operators/targetTests/resources and one local PIT thread throughout.
Baseline Main: **10/339 K + 329 NC**. Final Main: **33/396 K + 363 NC**.
Scoped R6 passes; no SURVIVED, TIMED_OUT, MEMORY_ERROR, NON_VIABLE or RUN_ERROR statuses.
Scoped R5/R7 are not enforced and are not claimed passed. This scope is not the default gate.

| Method | Baseline | Final |
|---|---|---|
| `findProgram` | 0 K / 5 total; 5 NC | 0 K / 5 total; 5 NC |
| `handleGenerate` | 0 K / 85 total; 85 NC | 0 K / 85 total; 85 NC |
| `isUnsoundBitstateBound` | 10 K / 10 total; 0 NC | 10 K / 10 total; 0 NC |
| `main` | 0 K / 205 total; 205 NC | 0 K / 238 total; 238 NC |
| `printGenerateUsage` | 0 K / 10 total; 10 NC | 0 K / 10 total; 10 NC |
| `printUsage` | 0 K / 24 total; 24 NC | 0 K / 25 total; 25 NC |
| `shortestCutoffPayload` | 0 K / 0 total; 0 NC | 23 K / 23 total; 0 NC |

All **110 unchanged-method identities/statuses** match exactly for findProgram, handleGenerate,
isUnsoundBitstateBound and printGenerateUsage: 10 K and 100 NC. Identity includes class, method,
descriptor, mutator, bytecode index/block and description, excluding shifted source lines.
Main and printUsage are rewritten populations: 205 -> 238 NC and 24 -> 25 NC respectively.
Do not describe their count difference as inherited kills or assert site-for-site lineage.
All 363 final non-kills are NO_COVERAGE, enumerated by their owning method above. Existing
subprocess rejection tests exercise those contracts but do not kill parent-JVM Main mutants.
This PR adds no in-process System.exit interception or parser rewrite just for a coverage number.

The new helper's full 23-site population is KILLED by real source/driver/search/enrichment and
JSON assertions, not mocks. Both proof states, counters, scope, snapshots and the source invariant
are checked. The weighted property fixture allows sums 5 or 15 at cutoff but forbids 5; all fifteen
proper subsets reject and four original choices remain. Dropping the property would admit a pair.
No simplifier/interpreter/store production code changes; I1's algorithm evidence remains in Spec 85.

## Default gate

Fresh baseline and final default both report **436/451 K + 15 S**, with R5/R6/R7 passing.
All **451 exact mutation identities/statuses match** using the same descriptor/index/block identity
as above. The fifteen raw survivors are unchanged; see the existing default mutation ledger and
Spec 85 evidence for their dispositions. No status is promoted through population growth.
The figures above summarize the measured XML inline. The measured baseline is merged I1
[`fc10c62`](https://github.com/SamuelIVX/interleave/commit/fc10c62); the measured feature is
[`81e5dfd`](https://github.com/SamuelIVX/interleave/commit/81e5dfd).

## CLI and consumer checks

Actual CLI processes check budgets 0,1,3,4 and 2147483647 on four removable noise actions.
The independent literal outputs are lengths 4,3,1,0,0; candidate counts 0,1,3,4,4;
only the last two are proven. Zero budget on an already empty witness is proven with no candidates
under all four symmetry-enabled strategies. A genuine CBS K=0 cutoff preserves its limit and
one-owner prefix. Invalid/missing/overflow/fractional/negative budgets, repeated mode flags,
legacy-mode conflicts, non-source/approximate/ambiguous selections and PASS/mixed-failure runs reject.
Legacy simplification and ordinary reports retain their existing payload shape.

Fresh direct proven/partial JSON flows through normalization and Chromium. **61 browser checks**
pass with **zero console errors**, including twelve added checks for proof/work/domain, actual
source digest/cutoff, snapshot alignment and unchanged INCOMPLETE. Existing 49 checks remain.
Normalization retains its 22 verdict cases/four symmetry assertions and adds 16 proof assertions
across direct/wrapped shapes and malformed domain, boolean, counters, missing cutoff and PASS.
The pre-change normalizer failed the new metadata-retention assertion; final checks pass.
The viewer validates producer metadata, not an independent optimality certificate.

The exact updated CI selector passes **48 isolated report-generator tests** and writes all six
fresh artifacts. Its full browser pipeline passes the same 61 checks with zero console errors.
The installed distribution launcher returns INCOMPLETE, empty choices, one fresh snapshot and
proven true with exactly 4/4 candidate checks on the supplied example. Gradle derives the launcher
name from this worktree directory; the first assumed `interleave` path was corrected before execution.

The CI selector includes the artifact generator and both new environment paths; no existing
assertion/selector is removed. CI parallelism, mutation resources/filters/floors and dependencies
are unchanged. The merged fifteen-minute visualizer allowance is retained.

## Reproducing the measurements

In separate clean checkouts of `fc10c62` and `81e5dfd`, use Java 26 and run:

```bash
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc --no-daemon
./gradlew --max-workers=1 -PpitestThreads=1 pitest -PpitestTargetOverride=dev.samhb.interleave.cli.Main --no-daemon
```

The test counts come from `build/test-results/test/TEST-*.xml`; scoped counts and identities come
from `build/reports/pitest/mutations.xml`. Preserve each scoped XML before running the default gate,
which writes the same report path:

```bash
./gradlew --max-workers=1 -PpitestThreads=1 pitest --no-daemon
```

Compare the baseline/final XML by the identity fields above and count each raw status separately.
The per-method table accounts for every scoped non-kill; the default totals and lineage are
summarized above. For the 48-test generator/six-artifact browser pipeline at `81e5dfd`, use the
selectors, environment paths and commands in the checked-in
[Visualizer compatibility job](../../.github/workflows/build.yml), adding the two local worker
flags to its Gradle command. The measurements describe these revisions; they do not claim a fresh
run for later documentation-only edits.

## Review

CodeRabbit 0.8.1 completed all ten submitted files, four findings (one minor, three trivial).
Fixed directory creation, browser banner placement, a directly tested package-private payload seam
with Javadoc, and repeated budget-flag rejection. Added a weighted-property forwarding regression.
No rerun for minor/trivial feedback. Final measured ledger/table changes receive local review.
Independent local passes check mode guards, JSON compatibility, effective wrapper verdicts,
source authentication/property forwarding and safe text-node presentation. No subagents used.

I2 is published in [PR #128](https://github.com/SamuelIVX/interleave/pull/128), awaiting merge.
Sam supplies remote CI/review/merge outcomes; no watcher runs. B7 remains active until I2 merges;
B8/#11/#12 and parked work remain excluded.
