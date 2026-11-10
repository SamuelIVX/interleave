<!-- CLI and consumer contract for bounded shortest cutoff subsequence results. -->
# 86 — Shortest cutoff CLI presentation

## TL;DR

B7 / I2 follows merged I1 PR #127 (`fc10c62`). Expose Spec 85 through
`--shortest-cutoff N`, an explicit non-negative integer candidate allowance (0..2147483647).
Require a source model, JSON, exact storage and an explicit strategy, as with legacy
`--simplify-cutoff`. Reject combining these two modes or repeating `--shortest-cutoff`. Never silently replace the legacy mode.

## Result contract

Select the first incomplete witness of the selected run only when its verdict is INCOMPLETE
and reproducible cutoff evidence exists. Reuse `shortestSubsequence` with the source invariant,
then enrich its actual returned trace through `TraceReplayer`. Keep the direct trace JSON shape
and add `cutoffSearch` containing domain `ORIGINAL_SUBSEQUENCES`, boolean `shortestProven`,
integer `candidatesChecked` and integer `candidateLimit`. The trace remains INCOMPLETE and
retains the entire cutoff. Do not modify TraceRecord/replay schemas or ordinary report output.
The extension describes this CLI optimization, not a proof certificate validated by replay.

Zero budget authenticates the original. An empty original is proven even at zero. Otherwise a
budget stop is best-found and need not be deletion-1-minimal. A true proof only establishes
minimum length among the supplied original witness's indexed owner-choice subsequences.
It never means globally shortest, PASS or exhaustive exploration. Candidate work excludes
mandatory original authentication and is not a time, memory or modeled-step bound.

The viewer validates the extension before retaining it: known domain, strict boolean proof,
non-negative safe integer counters, checked <= limit, INCOMPLETE outcome with cutoff evidence.
Reject malformed extensions rather than silently dropping them. Preserve legacy inputs without
metadata. Render explicit “Shortest among original subsequences” versus “Best found; shortest
not proven”, and candidate usage, using text nodes. Keep the INCOMPLETE verdict prominent.
Consumers cannot independently certify a claimed proof from JSON; they display producer metadata.

## Acceptance and verification

Exercise actual CLI processes at zero, partial and sufficient budgets, all four strategies,
preemption and symmetry, invalid arguments/selections, no-witness and mixed-failure runs.
Keep legacy cases. Write fresh proven/unproven artifacts for browser CI. Verify current snapshot
and source identity rather than copying the original's metadata. Test valid/invalid normalization
and browser display for both proof states, work, scope and unchanged INCOMPLETE classification.

Measure merged baseline and final ordinary build/strict Javadoc, affected/default PIT without
changing its configuration. CLI process coverage does not by itself kill in-process Main mutants;
report this limit and use in-process behavior checks where appropriate. Complete local and actual
CodeRabbit reviews, ship the spec/docs/evidence/table together, publish one draft PR and stop.
B7 completes with I2 merge; B8, #11/#12 and parked work remain unselected.

## Measured evidence

Fresh merged-main baseline `fc10c62` passes build/strict Javadoc and 1,811 tests. Final
integrated build/strict Javadoc and **1,815 tests** pass. The new payload helper has **23/23 K**;
whole Main is **33/396 K + 363 NC**, including existing/subprocess-only parser coverage limits.
All 110 unchanged-method identities/statuses match. Final default remains **436/451 K + 15 S**,
R5/R6/R7 pass and all 451 exact identities/statuses match baseline. **61 browser checks** pass,
zero console errors; normalization rejects malformed proof metadata and conflicting effective verdicts.
CodeRabbit completed ten initial files with one minor/three trivial findings, all patched and locally
verified without rerun. See the [measurement ledger](../../measurements/86-shortest-cutoff-cli.md)
for lineage, raw coverage limits, independent literals, consumer checks and local artifacts.
I2 is locally complete awaiting publication/merge; B7 completes only when I2 merges.
