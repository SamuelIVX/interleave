<!-- Bounded exact subsequence search and explicit optimality metadata for authenticated cutoffs. -->
# 85 — Bounded shortest cutoff subsequences

## TL;DR

Implement B7 / I1 after PR #126 at `7c0072d`: a library operation that can prove shortest length
among subsequences of the supplied witness's owner choices. This is not a search over every legal
schedule. Keep the same entire CutoffEvidence and INCOMPLETE classification. A finite candidate
budget returns an honest best-found witness when proof is unfinished.

Bundle implementation, immutable result, shared authentication, independent acceptance, docs and
mutation evidence in this PR. B7 / I2 will add CLI proof presentation separately; existing
`--simplify-cutoff` keeps its deletion-1-minimal contract. No CLI/schema/viewer change in I1.

## Interface and authentication

Add `CutoffSimplifier.shortestSubsequence(program, witness, invariant, maxCandidates)` returning
an immutable `ShortestResult` with `trace()`, `shortestProven()`, `candidatesChecked()` and
`candidateLimit()`. Only the simplifier constructs results; fields cannot be mutated. All state
is invocation-local. Use the actual source property, or null when no additional predicate applies.

Reject a negative candidate limit before replay. Zero is valid: authenticate the original, then
return it unproven without checking candidates, except that an authenticated empty original is
already provably shortest with zero checks. Null/unsupported/corrupt inputs reject even at zero
budget. Preserve existing simplify input validation order and exceptions through shared authentication.

Reuse the existing source driver, fresh Trace.create metadata and invariant-aware replay. Candidate
prefixes must remain nonterminal; preserve reason, omitted owner/PC, limit, iteration, source span
and expansion path exactly. Guarded enabledness, prefix costs and omitted preemption costs remain
those of real source execution. Earlier failures, errors, property violations and different cutoffs
reject candidates. Unexpected model/property exceptions propagate rather than becoming rejected
candidates. Never copy stale outcomes/PCs/source labels onto changed choices.

## Search domain, order and proof

The domain contains schedules obtained by deleting indexed owner choices from the original prefix
while preserving their order, including the original and empty prefix. Retaining an owner choice
does not promise to execute the same original PC: removing earlier choices can change its progress.
Identical owner IDs can make different index subsets denote the same schedule; these candidates
still count separately. No exponential deduplication set is allocated.

Authenticate the original as the initial best answer. Enumerate candidate lengths N-1 down to zero,
with lexicographically increasing source-index combinations within each length. At the first valid
candidate of a length, adopt it and skip remaining ties at that length; only shorter lengths can
improve it. Otherwise exhaust all combinations of that length. Continue through zero unless the
budget stops the search. This can cross a deletion-1-minimal plateau and makes early improvements
available without completing a proof. Do not assume validity is monotonic under deletion.

A proven result means all shorter candidates were rejected (or length zero was reached). Skipped
equal-length candidates cannot improve length. If interrupted by the candidate limit, the returned
best may already be shortest, but shortestProven must be false. It need not be deletion-1-minimal.
This optimality statement never implies PASS, exhaustive model search or a globally shortest legal
schedule. Whole trace/evidence values are immutable; an unchanged best may reuse the original Trace.

## Finite work contract

Each attempted prefix consumes exactly one candidate before execution/replay. Never attempt more
than maxCandidates. Original authentication is mandatory and is not counted as a candidate. The
limit counts candidate evaluations, not wall time, allocation bytes or individual modeled actions.
Each evaluation can replay up to N choices and its omitted action. Without interruption the search
can require exponentially many evaluations. Use the opt-in operation outside the exploration hot path.

Keep independent remaining-budget and checked-count guards, so a single progress fault cannot turn
the candidate loop into an unbounded search. Combination scans are iterative and bounded by their
index-array length; no recursive subset generator or growing work queue. Retain source/work caps,
execution/replay semantics, mutation operators/resources/filters and default CI parallelism.

## Independent acceptance

- Owners 0..3 add literal weights 1,2,4,8; owner 4 has a zero-limit loop enabled only at x=5 or 15.
  Original prefix [0,1,2,3] reaches 15. Any single deletion and either half-chunk deletion disables
  the cutoff, so existing simplify retains four choices. The pair [0,2] reaches 5 and is globally
  shortest within this original subsequence domain; no singleton or empty prefix qualifies.
- Search checks four length-three candidates, then [0,1] and [0,2], then four singletons and empty:
  eleven checks prove length two. Limit six returns [0,2] unproven; ten still leaves empty unchecked;
  eleven is exactly sufficient; a larger limit must report only eleven checks. An independent
  sixteen-subset bitmask oracle and manual weighted sums confirm the valid schedules and minimum.
- With invariant always (x !=5), the pair fails before cutoff. Only the original qualifies; all
  fifteen proper index subsets must be rejected before certifying length four.
- Four noise actions before an unconditional root cutoff shrink once per checked length, reaching
  empty with four candidates. Empty originals are proven at zero budget. Large candidate limits
  must not increase completed work after a proof.
- Another owner outside the original prefix may enable the same guard in one action, while the
  supplied owner's five-action prefix cannot shorten. A proven length-five subsequence must not
  be described as globally shortest; independently authenticate the outside one-action witness.
- Preserve literal K=0/K=1 preemption minima, target PCs/allowances, loop ancestry and current source
  metadata. Authenticate actual witnesses from all four explorers. Check corruption, unsupported
  resource-only inputs, negative/zero budgets, properties/early failures, repeated and concurrent use.

Derive numeric expectations outside Java and assert literals. Keep all existing simplify regressions
and CLI behavior intact. Measure fresh baseline and final scopes, including the nested result class,
and compare actual identities/statuses. Record every non-kill without treating resource errors as kills.

## Verification and publication

Fresh merged-main build/full suite/strict Javadoc and affected/default PIT precede implementation.
Run integrated checks, affected census, default gate, artifact-level library/legacy CLI and unchanged
normalizer checks. Perform local review and the required actual completed CodeRabbit review. Patch
minor/trivial feedback locally without a CLI rerun. Publish one draft I1 PR and stop; I2 remains the
next B7 stage after merge. B8/#11/#12, resource-prefix shortening and parked work stay excluded.

## Measured evidence

Merged baseline `7c0072d` passes build/strict Javadoc and **1,786 tests**, simplifier
**48/54 K + 6 S**, default **436/451 K + 15 S**. Final integrated build/strict Javadoc passes
**1,811 tests**, adding 25 invocations. Final enclosing simplifier is **94/102 K + 8 S** and
nested result **5/5 K**, scoped R6 passes with zero uncovered/resource/run errors. All 107 first/final
post-implementation identities/statuses match. The unchanged candidate helper retains eight exact
K identities; moved authentication/lambdas and new populations are accounted separately in the
[measurement ledger](../../measurements/85-shortest-cutoff-subsequence.md), including all eight raw survivors.

Final default remains **436/451 K + 15 S**, R5/R6/R7 pass and all 451 exact baseline
identities/statuses are retained. Mutation filters/operators/target tests/floors/resources and CI parallelism
are unchanged. Standalone built-library checks pass the four budget/proof cases. Actual legacy
CLI calls under all four strategies retain INCOMPLETE, empty prefixes and fresh one-snapshot payloads.
All eight fresh direct payloads normalize; 22 compatibility cases and four symmetry assertions pass.
The initial feature checks omitted a DOM rerun because consumers/schema were unchanged.
The PR review/CI follow-up below subsequently verifies the full browser suite. No CLI proof
presentation is enabled by I1.

CodeRabbit 0.8.1 completed all eight submitted files, two minor documentation findings. Numeric
spacing and pending measurement text are corrected with actual results; no CLI rerun for minor
feedback. Local review additionally gates both concurrent tasks at one start. I1 merged as PR #127 at `fc10c62`. [Spec 86](86-shortest-cutoff-cli.md) defines I2 CLI proof presentation.

## PR #127 review and CI follow-up

Corrected the B7 plan's stale best-found/1-minimal wording: an early budget stop returns
best-found evidence with shortestProven false and need not be deletion-1-minimal. The evidence cell
now links both legacy and shortest-subsequence tests. Production behavior is unchanged.

The original Build workflow's Build and Mutation jobs passed. Visualizer compatibility hit its
five-minute job limit: browser/OS installation consumed 4m45s and Gradle began after 4m57s. The
GitHub annotation explicitly reports the exceeded five-minute maximum. Increase only that job's
allowance to fifteen minutes; retain all generation/browser assertions, dependencies and CI parallelism.
Local verification passes 47 report-generator tests, all four fresh artifacts, normalization checks
and 49 browser checks with zero console errors. This does not claim the updated remote run is green;
Sam supplies its result. See the measurement follow-up for the original CI evidence.
