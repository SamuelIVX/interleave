<!-- Planned B9/K2: one shared Bloom budget with cost dominance and honest iterative CBS results. -->
# Spec 90: Approximate symmetry for context-bounded search

## Objective

Extend the checked approximate source store to CBS without losing mapped last-owner identity or
cost dominance. Deliver single-bound and iterative library/CLI/reporting behavior while keeping
one shared logical bit budget across all costs and reporting only observed, approximate results.

**Type: delta with a shared iterative-termination correction. Status: planned, not implemented.**
[Spec 89](89-approximate-symmetry-dfs-por.md) must merge first. The
[B9 set contract](../../plans/approximate-symmetry-contract.md) owns shared interfaces and oracles.

## Scope

- Package / Module: interleave CBS membership, runner/harness iteration and source consumers.
- Modifies `src/main/java/dev/samhb/interleave/search/BitstateSymmetryStateStore.java`,
  `src/main/java/dev/samhb/interleave/InterleaveRunner.java`,
  `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java`,
  `src/main/java/dev/samhb/interleave/report/{BenchmarkHarness,BenchmarkResult,ReportWriter}.java`,
  and `src/main/java/dev/samhb/interleave/cli/Main.java` for CBS capability, iteration and reporting.
- Adds `src/test/java/dev/samhb/interleave/search/BitstateSymmetryCbsTest.java` and extends
  `src/test/java/dev/samhb/interleave/ApproximateSymmetryRunTest.java`,
  `src/test/java/dev/samhb/interleave/search/SymmetryCompositionTest.java`,
  `src/test/java/dev/samhb/interleave/cli/MainTest.java`,
  `src/test/java/dev/samhb/interleave/cb/{ContextBoundedExplorerContractTest,CbsDifferentialTest,CbsMonotonicityTest}.java`,
  and `src/test/java/dev/samhb/interleave/report/{BenchmarkHarnessCBTest,ReportingCBTest,ReportJsonContractTest,SoundnessReplayTest,SoundnessVerdictTest}.java`
  where existing contracts require integration cases. Updates README,
  `docs/visualizer-normalize-check.mjs`, `docs/visualizer-e2e.mjs` and
  `.github/workflows/build.yml` when new actual CLI artifacts are introduced.
- Off-limits: Spec 91's approximate sleep-context membership, exact symmetry/ordinary filter key
  formats owned by Specs 77/84/89, broader source eligibility and new result/wire fields.

## Non-Goals

No new CLI bound allowance, per-cost Bloom vector, exact retained minimum-cost table, seed/retry
feature, minimum-bound proof for approximate search, or whole-process memory quota. Do not change
ordinary BitstateStore's per-cost vectors or exact CBS dominance/early-exhaustion behavior.

## Invariants

- The same canonical frame permutation transports the last owner; initial last `-1` stays distinct.
- Without false-positive decisions, a previously marked cost q covers p exactly when q <= p.
- A better-cost revisit is not deliberately covered by a higher-cost mark. Bloom collisions remain
  the explicitly approximate exception; missing cost information is not an admissible encoding.
- Each iterative pass owns fresh membership; original-run state/time limits retain their current
  accounting and precedence. Reporting does not execute extra actions/properties.
- Observed failures/deadlocks/cutoffs/resource prefixes retain original owner/source identities.
- Exact results and ordinary filter probes/metrics remain unchanged; approximate deepening changes
  only where a shared exhaustion inference was previously unjustified.

## Requirements

1. **R1 — Bound contract.** WHEN a checked approximate CBS store is constructed, THE SYSTEM SHALL
   retain a nonnegative configured capacity and reject negative/over-capacity query/mark costs
   before mutating membership/statistics or executing callbacks. Library capacity may exceed the
   unchanged CLI bitstate limit; non-CBS direct factories continue requiring capacity zero.
2. **R2 — Product identity.** WHEN CBS queries at p, THE SYSTEM SHALL canonicalize the full orbit
   and mapped last owner together, then probe complete CBS-domain keys for each q in [0,p].
   A positive complete lookup MAY cover p; separate partial probe matches SHALL NOT be combined.
3. **R3 — Cost marking.** WHEN CBS marks at p, THE SYSTEM SHALL insert only the complete cost-p
   key. No deliberate higher-cost dominance, exact minimum map, or per-cost filter SHALL be retained.
   Marking a lower cost SHALL permit that cost to cover future higher-cost queries.
4. **R4 — Budget and lifecycle.** WHEN costs/bounds increase, THE SYSTEM SHALL retain one `bits`
   filter per live checked approximate store. Clear/copy/new pass SHALL reset filter statistics and
   cost history while preserving binding/capacity. Owned-mode bookkeeping SHALL not strongly retain
   prior passes' filters; caller-supplied factory reuse detection SHALL remain effective.
5. **R5 — Iterative policy.** WHEN an approximate iterative pass reports no failure/deadlock and
   no observed preemption cutoff, THE SYSTEM SHALL continue toward the requested ceiling rather
   than infer exhaustion. Actual failure/deadlock or existing resource interruption MAY stop it.
   Exact passes SHALL retain their existing no-cutoff shortcut. Apply the precision-aware condition
   in both runner and harness, including ordinary approximate stores on those shared paths.
6. **R6 — Results.** WHEN a checked approximate symmetry CBS row is emitted, THE SYSTEM SHALL
   retain BITSTATE and symmetry identity. Exact and other rows SHALL retain their producing store
   type and reduction identity. All rows SHALL retain existing incomplete/resource/failure precedence
   and actual producing-pass statistics.
   `preemptionsUsed` SHALL mean the bound that produced the row, never a proven approximate minimum.
   Cleared-pass filters/statistics SHALL not be aggregated into that row's density or estimate.
7. **R7 — Metrics.** WHEN the new checked approximate CBS mode reports its heuristic, THE SYSTEM
   SHALL use the set's capped
   union-model estimate over complete cost lookups, retain actual bit count/density, and distinguish
   those diagnostics from behavior coverage or a probability that the model is correct.
8. **R8 — Consumers.** WHEN CLI selects eligible source, explicit symmetry BITSTATE and CBS within
   existing CLI limits, THE SYSTEM SHALL run single/iterative CBS and emit supported existing JSON.
   Approximate DPOR or a strategy-omitted request containing it SHALL still reject before execution.

## Failure Modes & Edge Cases

1. WHEN capacity/cost is invalid, last owner is out of range, a run binding differs, or source/property
   eligibility fails, THE SYSTEM SHALL reject without partial writes or precision fallback.
2. WHEN costs are zero/equal/lower/higher, or a shared filter is tiny/saturated, THE SYSTEM SHALL
   retain the encoded cost relation and approximate status; false positives may still hide a path.
3. WHEN a capacity is very large, THE SYSTEM SHALL avoid overflow in capacity arithmetic and cost
   iteration. Lookup complexity is O((p+1)*k); this spec introduces no constant-time or CPU-quota claim.
4. WHEN collisions hide a preemption-cutoff witness, THE SYSTEM SHALL not stop deepening on that
   absence. WHEN the next pass hits a global runner resource allowance, THE SYSTEM SHALL retain its
   actual current resource prefix and original stop reason, not an earlier pass's path.
5. WHEN an ordinary caller factory returns the same instance across iterations, THE SYSTEM SHALL
   preserve identity-based rejection even if the store implements ApproximateStateStore or custom equals.
6. WHEN configuration changes from one call/bound to another, THE SYSTEM SHALL preserve fresh
   membership and original callbacks; no loop-bound or concrete-step semantics are reinterpreted.

## Acceptance Criteria

- R1–R3: a complete-key set oracle reproduces exact mapped-last/minimum-cost decisions when no
  false positives occur; higher-cost marks do not intentionally cover lower costs; initial sentinel
  and plain VALUE domains are distinct before hashing. Encodings contain cost and mapped context.
- R4: raising the number of recorded costs leaves logical capacity at `bits`; independent passes
  use fresh stores. No prior filter is retained solely by owned-mode identity bookkeeping.
- R5: forced-collision fixtures demonstrate hidden cutoff evidence without early approximate
  exhaustion; exact no-cutoff behavior and custom factory reuse rejection remain covered.
- R6/R7: JSON/Markdown match the producing pass and observed bound; no minimality/coverage claim
  appears. Mixed loop/preemption/resource evidence and B8 captured/replayed observations survive.
- R8: real CLI outputs reach browser tests, including iterative approximate absence, real found
  witness, modeled cutoff and resource examples supported by their existing producer interfaces.
- Full baseline/final build/tests/Javadoc, affected/default mutation evidence and actual completed
  CodeRabbit review satisfy the set contract. No future result is asserted as measured here.

## Refactor Baseline & Rollback

- Pre-Change Baseline: fresh main after merged Spec 89, with its commit pinned in the measurement
  ledger. Reproduce the no-observed-cutoff early exit in runner and harness using a tiny ordinary
  approximate store before correcting the shared precision condition.
- Preserved Invariants: exact iteration, ordinary store hashing/vector/FPR behavior, source
  certification, current resource accounting and exact backend selection.
- Call-Site Migration: both iterative loops, checked CBS factory/capacity checks, ordinary approximate
  termination predicates, result bound documentation/report labels and actual producing-store metrics.
- Rollback Criteria: wrong cost dominance without collisions, false exactness/minimum claim, stale
  filter retention or resource provenance blocks publication. Revert the complete K2 change while
  retaining K1 support; do not weaken existing tests or alter unrelated source execution.

## Design

Use existing context-aware canonicalization, including mapped last owner, once per query/mark.
Encode keys with the CBS domain and cost suffix fixed by the shared contract. For a query, each
candidate q must have every one of its own probes set before returning positive. Marks at p do not
erase other costs; all costs share the one filter. Plain VALUE membership remains a separate encoded
domain, even if a direct caller exercises it; cross-domain bit interference is ordinary approximation.

Implement overflow-safe finite cost iteration. Library callers are responsible for their configured
bound/work request; the existing source rank and CLI limits are not widened. A configured capacity
is a validation bound, not a per-cost allocation instruction.

For metrics, pMax begins/resets at zero and increases only after validated CBS queries. Insertions
use long counts and floating conversion before formula multiplication. With no marks F=0; otherwise
use `min(1,(pMax+1)*F)` with overflow-safe conversion. Distinct-key estimates and filter correlations
are not inferred from mark count. Report the current producing pass, not cumulative cleared filters.

Owned approximate stores are allocated internally and cannot be supplied through custom factories.
Do not retain them in the strong identity-history set used to detect external factory reuse; preserve
that guard for caller-supplied stores. Do not replace it with equals-based or probabilistic identity.
The existing diagnostic configurations/traces may still grow; no total heap bound is claimed.

## Current State

- [verified] At the inspected B9 baseline, exact symmetry keys include mapped last owner and retain
  minimum costs; ordinary BitstateStore instead uses lazy per-preemption vectors.
- [verified] Runner and BenchmarkHarness currently stop iterative deepening when no preemption
  cutoff was observed and retain store identities in strong sets. That inference is not evidence
  of exhaustive exploration for approximate storage.
- [verified] BenchmarkResult documentation currently calls the iterative bound minimal; the new
  approximate mode cannot substantiate that claim. The current CLI bitstate bound limit is two.
- [unverified] K1/K2 implementations, collision fixtures, memory measurements and mutation results
  do not exist yet. Implementation must inspect actual merged K1 rather than assume it matches.

## Tests

- `costDominanceMatchesIndependentMappedOwnerOracle` (R1–R3) — q==p, q<p, q>p, better-cost
  reopening, mapped last owner, initial -1, complete-key lengths and plain-domain distinction.
- `costLookupsNeverCombineProbeFragmentsAcrossCandidates` (R2) — construct bit patterns where
  neither candidate individually has all probes; do not accept a composite match as deliberate coverage.
- `sharedFilterCapacityDoesNotGrowWithRecordedCosts` (R3/R4) — marks across multiple costs,
  duplicate marks, clear/copy and independent iterative passes at the public store/runner interfaces.
- `hiddenApproximateCutoffDoesNotProveExhaustion` (R5) — failing regression first in both loops;
  ordinary and checked approximate stores continue, exact no-cutoff runs still stop early.
- `ownedIterationDoesNotRetainPriorFiltersForReuseDetection` (R4/R5) — verify ownership/lifecycle
  without GC-timing assertions; external same-identity and equals-colliding factories keep rejection.
- `iterativeResourcePrefixBelongsToActivePass` (R5/R6) — global runner allowance, current path,
  actual reason, typed snapshot and BITSTATE/symmetry metadata across successive bounds.
- `producingPassMetricsAndObservedBoundReachConsumers` (R6–R8) — fresh Java/CLI artifacts,
  strict JSON, Markdown and browser observations; independently derived numeric literals.
- High-risk mutants: q<=p versus q<p, wrong last-owner permutation/sentinel, omitted cost suffix,
  partial-probe union, integer overflow, stale pMax/clear, approximate early exit and wrong-store metrics.

## Constraints

- Dependencies: [Spec 89](89-approximate-symmetry-dfs-por.md) merged first; preserve shared key,
  precision and ownership interfaces. Do not enable Spec 91 behavior here.
- Backward compatibility: exact CBS behavior and ordinary filter storage/metrics stay intact.
  The shared approximate exhaustion correction intentionally may visit more bounds or reach a
  configured resource stop; document that change instead of preserving an invalid inference.
- Oracle Contract: [B9 set oracle and verification contract](../../plans/approximate-symmetry-contract.md#oracle-and-verification-contract).

## Relevant Skills and Review

| Skill | Required use within this phase |
|---|---|
| `ponytail` full / `codebase-design` | One owned cost-membership implementation and shared iteration policy |
| `tdd` / `diagnosing-bugs` | Red/green regressions for shared approximate early-exit behavior |
| `domain-modeling` | Keep observed bound, dominance, approximate absence and resource stop distinct |
| `owasp-security` | Capacity/argument validation and trustworthy consumer classification |
| `ponytail-review` | Trace both loops, ownership, producing-pass data and resource precedence |
| `code-review` | Invoke CodeRabbit after completing this spec and after its future implementation, before pushing |

Use `doc-coauthoring` for fresh-reader review and `receiving-code-review` for actual findings.
The approved store/runner/CLI/report interfaces are the TDD seams; do not add an interview or another
approval gate. Actual CodeRabbit completion is required; ordinary review cannot replace it.
