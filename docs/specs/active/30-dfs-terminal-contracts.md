# Spec: DFS terminal evidence, budgets and run isolation

## Objective

Protect the DFS oracle's public terminal evidence and visitor notifications, exact state-budget
boundaries and per-run results before extending explorer/reduction verification under roadmap 9-V1.
Keep POR/DPOR reduction semantics in separate bundles.

## Scope

- Add `DfsExplorerContractTest` with literal terminal/callback, budget and reuse oracles.
- Ship this specification and update the future-work roadmap.
- No executable production, API, dependency, mutation operator/scope/exclusion or gate changes.
- No assertions about total schedule enumeration; DFS deduplicates configurations and terminal
  traces are counterexample evidence, not one record per possible execution.

## Invariants

- Every newly visited configuration notifies its observer before its terminal notification.
- COMPLETED, invariant VIOLATION, failing-edge VIOLATION, DEADLOCK and source INCOMPLETE carry
  the expected recorded choices/outcomes; failing edges and cutoff witnesses need no successor visit.
- Direct DFS state budgets stop at the exact threshold. Existing negative/zero direct-call
  compatibility returns no visits/traces; this does not redefine higher-level runner validation.
- Reusing an explorer clears per-run positions, counters, traces and observer selection without
  changing previously returned evidence snapshots.
- Assertions use hand-enumerated source values and literal prefixes, not canonical keys as an oracle.
- Assertion-confirmed kills exclude timeout/error detections; retained unreachable-path mutations
  get a scoped explanation rather than exclusions, reflection or forced cleanup.

## Requirements

1. Every selected terminal path SHALL report its complete literal prefix and outcome, along with
   ordered visitor events containing the actually visited shared values. Callback and returned
   evidence SHALL each match the literal oracle, not merely each other.
2. Budget limits -1, 0, 1 and 2 SHALL preserve the specified direct-explorer behavior. The public
   budget convenience overload and the full observer overload SHALL agree with independent counts.
   A runtime-failure edge SHALL not execute once the initial visit exhausts a budget of one.
3. Reusing the explorer on a distinct program SHALL expose only the second run's positions and
   observer events; the earlier result SHALL retain its original values, count and trace.
4. Baseline/final semantic mutation populations SHALL match, and every baseline kill SHALL remain
   killed. Explicitly distinguish the redundant deadlock-guard identity from remaining test gaps.

## Design and Tests

Five one-path models use the real source compiler: one committing write, one write violating a
property, one atomic modulo-by-zero failure, one disabled guard and one loop with limit zero.
Expected state values, callback order, counts, choices and outcomes are literal fixture data.
A small observer captures value snapshots of public events without mutating modeled execution.
Every terminal witness is also replayed with terminal validation enabled: use the model's supplied
invariant when present and an always-true fallback otherwise. The fallback applies only to replay;
the explorer still receives the original optional invariant.

The budget model fails its first instruction, so it distinguishes a visited configuration from
an edge failure without a successor. Reuse changes the initial shared value from zero to nine,
preventing canonical-position overlap from masking a missing visited-map clear. Result-value
assertions read DynamicState directly; they do not derive expected keys with the encoder under test.

| Requirement | Regression | Cases |
|---|---|---|
| R1 | `terminalEvidenceAndCallbacks` | Five terminal branches with literal visit/trace order and evidence |
| R2 | `budgetOverloadAndVisitorStopAtExactBoundary` | Four budgets through convenience/full overloads |
| R3 | `reuseClearsVisitedPositionsAndKeepsPreviousResults` | Two distinct programs, observers and retained result snapshots |

## Acceptance Criteria

- All ten new invocations and the full existing suite pass; build and strict Javadoc pass.
- Measure merged-main and final outer-DfsExplorer PIT, preserving semantic identities and old kills.
- Explain every baseline non-kill and leave genuine other explorer/reduction work separate.
- Default PIT and 18-check normalization pass with separate scope stated.
- CodeRabbit CLI completes review of the new bundle. Trivial/minor PR feedback patches do not
  trigger extra CLI review, per Sam's instruction.

## Baseline

Merged PR #70 is `98e7bd9`. Fresh full test baseline passes 863 invocations, no failures/errors/skips.
Outer-DfsExplorer PIT measures **74/83 KILLED**, six SURVIVED, three NO_COVERAGE, no timeout/error
detections. Semantic identities use class/method/descriptor/mutator/instruction indices/blocks,
not source lines. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`.

### Baseline mutation ledger

All identities are in `dev.samhb.interleave.search.DfsExplorer`; indices refer to bytecode.
The two `explore` descriptors below are distinguished by arguments rather than source line.

| Baseline | Method / index | Contract or disposition |
|---|---|---|
| SURVIVED | `dfs` / 8 | Exact entry budget boundary under R2. |
| SURVIVED | `dfs` / 165 | Exact pre-edge budget boundary; a failing edge cannot consume an exhausted budget under R2. |
| SURVIVED | `dfs` / 97 | Property-violation trace notification under R1. |
| NO_COVERAGE | `dfs` / 285 | Failing-edge violation notification under R1. |
| SURVIVED | `dfs` / 343 | Deadlock notification under R1. |
| NO_COVERAGE | `explore(Program,long)` / 9, 10 | Budget convenience delegation and returned result under R2. |
| SURVIVED | `explore(Program,Invariant,StateStore,StateVisitor,long)` / 34 | Clear visited positions between distinct programs under R3. |
| SURVIVED | `dfs` / 313 | Removed post-loop `allTerminated` call: candidate redundant guard; verify reachability reasoning below. |

### Redundant deadlock-guard disposition

`Configuration.allTerminated` reads a final boolean. A true value causes the earlier completion
branch to record a trace and return from this same DFS frame. Every frame reaching the post-loop
deadlock guard therefore has that immutable flag false; recursive successors are different
configurations. Removing the later call substitutes false, leaving `!allTerminated` true as before.
This reasoning covers normal public exploration with Configuration's immutable metadata; it does
not use reflection/unsafe mutation or promise identical resource/work/performance behavior. Retain
the mutation and guard without exclusions or production cleanup to force a score.

## Verification

### Initial bundle

All **873 tests** pass, including the ten new invocations, with zero failures/errors/skips.
Outer-DfsExplorer PIT improves **74/83 → 82/83 KILLED**: five survivors and three uncovered become
killed, with zero uncovered/timeout/error detections remaining. All 83 semantic identities match
and all 74 baseline kills remain killed. The sole survivor is `dfs` / 313, with the immutable-flag
reachability disposition above; it is not an unassigned test gap or a whole-checker proof.

| Final status | Baseline identities | Evidence |
|---|---|---|
| KILLED (3) | `dfs` / 97, 285, 343 | Literal property/failing-edge/deadlock callbacks under R1. |
| KILLED (2) | `dfs` / 8, 165 | Exact entry/pre-edge state-budget limits under R2. |
| KILLED (2) | `explore(Program,long)` / 9, 10 | Public convenience overload has literal result/evidence oracles under R2. |
| KILLED (1) | Full `explore` / 34 | Distinct-program reuse clears stale positions under R3. |
| SURVIVED (1) | `dfs` / 313 | Redundant post-loop termination read, disposition above. |

The final integrated `clean build javadoc pitest` passes all 873 tests and strict Javadoc.
Default PIT remains **281/291**, with identical semantic identities/statuses to the prior merged-main
evidence. Ten retained default mutations keep their existing scoped dispositions; no uncovered/error
detections. All 18 visualizer normalization checks pass.

CodeRabbit CLI completed the entire three-file review with zero findings (`review_completed`, not
skipped). Only measured verification/status prose was finalized afterward. Browser integration was
not rerun because executable production and report output are unchanged; CI is unverified at
publication. Broader explorer/reduction verification remains open.

### PR feedback: invariant-free terminal validation

The trivial PR comment correctly identified that passing a null invariant to replay checks
execution/step evidence but skips terminal-verdict comparison. The four invariant-free fixtures
now pass an always-true invariant only to replay; the property fixture retains its real invariant.
Exploration inputs and literal callback/prefix/outcome assertions remain unchanged.
Post-patch full tests and scoped DFS PIT pass: all 873 invocations pass with zero failures/errors/
skips; DFS remains 82/83 with identical semantic identities/statuses to the initial bundle.
The initial clean build/Javadoc/default-PIT/normalization results above predate this replay-check-only
patch and were not rerun; browser integration was not rerun. No extra CodeRabbit CLI review was run
for trivial feedback, per Sam's explicit instruction.
