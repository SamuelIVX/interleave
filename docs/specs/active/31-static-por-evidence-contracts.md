# Spec: Static-POR evidence and source dependency contracts

## Objective

Protect static POR's public evidence and source-aware selection under roadmap 9-V1. Pin reachable
terminal notifications, supplied-store initialization and depth-first failure evidence while
retaining explicit dispositions for redundant/unreachable code under the supported model contract.

## Scope

- Add `StaticPorExplorerContractTest` using real source models and one pure deterministic failing step.
- Correct class Javadoc: without an invariant, source models still use remaining-thread closure;
  legacy models retain the current-step selector.
- Ship this specification and update the future-work roadmap.
- No executable production, public API, dependency, reduction algorithm, mutation scope/operator/
  exclusion or gate changes. DPOR and helper-selector mutation scopes remain separate.

## Invariants

- Source runtime failures must remain discoverable before a different thread disables their future guard.
- Terminal callback and result evidence each match literal prefixes/outcomes, not merely each other.
- Callers may supply prepopulated stores; traversal clears them before observing new configurations.
- Full branching follows the established ascending-thread depth-first traversal and propagates its
  first execution error with the visits that preceded it. This is a partial-failure evidence contract,
  not an exhaustive-verdict guarantee for exceptional runs or a preparation-order promise for reduced sets.
- Step footprints cover modeled values/errors; steps have no hidden mutable execution state.
  Invariants are pure/deterministic and visitors do not mutate modeled configurations, as in Spec 13.08.
- Reduced search preserves supported outcomes, not every configuration, schedule or trace count.

## Requirements

1. COMPLETED, property VIOLATION, failing-edge VIOLATION, DEADLOCK and source INCOMPLETE SHALL
   retain literal visitor order, shared values, recorded choices and step outcomes. Replay SHALL
   validate terminal classification using the real property or a replay-only true fallback.
2. A caller-supplied store already containing the initial position SHALL be cleared before traversal;
   the real supplied store and returned evidence SHALL contain the expected new visit count.
3. With no invariant, source remaining-thread dependency closure SHALL retain both the deadlock
   after a disabling write and the runtime failure before it. DFS and POR SHALL each match a
   literal four-position/two-witness oracle; equality with each other alone is insufficient.
4. When dependencies require full branching, POR SHALL preserve the same literal first exception
   and visit prefix as DFS. Preparing every root edge early must not replace a descendant error
   with the later root edge's error. The error is deterministic from modeled state, not call counts.
5. Retain every baseline kill and compare semantic identities independently of Javadoc line shifts.
   Disposition redundant metadata checks and the unreachable second property-failure branch under
   supported purity assumptions; do not use impure predicates, reflection or exclusions for a score.

## Design and Tests

Five one-path source models cover real terminal execution. Both callback and returned witnesses
are extracted into comparable values and asserted against literal fixture data. Terminal replay
retains the supplied invariant, or uses an always-true property only for replay when none was declared.

The no-property source counterexample has T0 write x=1; T1 first writes y, then fails modulo-by-zero
under guard x==0. Current-step selection alone picks T0 and hides the failure. Future closure keeps
T1's continuation dependent: four distinct positions produce [0,1] DEADLOCK and [1,1] VIOLATION.
The failure's category and source evidence independently replay.

The full-branch exception model has T0 write flag[0] and T1 read it before throwing a deterministic
message. Their dependency prevents reduction. DFS and POR visit false then true and propagate
"failure after flag write". Eagerly preparing the full root set throws "failure before flag write"
before the descendant visit; this public diagnostic/observer difference catches preparation guards
without private-call or performance assertions.

| Requirement | Regression | Cases |
|---|---|---|
| R1 | `terminalEvidenceAndCallbacks` | Five terminal paths with literal evidence and observer order |
| R2 | `prepopulatedStoreIsClearedBeforeExploration` | Real prepopulated HashingStateStore |
| R3 | `invariantFreeSourcePreservesFutureGuardFailure` | Four-position source counterexample, anchored separately for DFS/POR |
| R4 | `fullBranchingPreservesFirstExceptionAndVisitedPrefix` | Modeled error diagnostic and two literal observer values for DFS/POR |

## Acceptance Criteria

- All eight new invocations and the full existing suite pass; build and strict Javadoc pass.
- Fresh outer-StaticPorExplorer baseline/final identities match and prior kills remain killed.
- Every retained identity has an explicit proof scope or separate follow-up gap; no forced 100%.
- Default mutation gate and normalization pass, with separate scope stated.
- CodeRabbit CLI completes the new four-file review; trivial/minor PR feedback does not trigger
  another CLI run, per Sam's instruction.

## Baseline

Merged PR #71 is `2a436ae`. Fresh baseline: 873 tests pass without failures/errors/skips.
Outer-StaticPorExplorer PIT measures **106/122 KILLED**, nine SURVIVED, seven NO_COVERAGE,
zero timeout/error detections. All local Gradle runs use `--max-workers=1 -PpitestThreads=1`.
Semantic identities use class/method/descriptor/mutator/instruction indices/blocks, not source lines.

### Baseline mutation ledger

All identities belong to `dev.samhb.interleave.por.StaticPorExplorer`; indices are bytecode sites.

| Baseline | Method / indices | Contract / disposition |
|---|---|---|
| SURVIVED (1) | `explore` / 47 | Source-aware selector must retain a future guarded runtime failure under R3. |
| SURVIVED (1) | `explore` / 18 | Supplied store must be cleared under R2. |
| SURVIVED (2) | `porDfs` / 164, 167 | Full-branch preparation changes first propagated error/observer history under R4. |
| SURVIVED (3) | `porDfs` / 80, 116, 502 | Property, completion and deadlock callbacks under R1. |
| NO_COVERAGE (1) | `porDfs` / 399 | Failing-edge callback under R1. |
| SURVIVED (1) | `porDfs` / 259 | PC zeroing affects only a redundant non-null next-step lookup; verify disposition below. |
| SURVIVED (1) | `porDfs` / 434 | Post-loop termination guard repeats an immutable false flag after the earlier completion return. |
| NO_COVERAGE (6) | `porDfs` / 450, 452, 455, 461, 466, 471 | Unreachable second property-failure branch for pure predicates/non-mutating observers; disposition below. |

### Retained-identity reasoning

The PC read at 259 is used only to fetch a step for a null check; actual execution uses the
configuration's current PC through `prepare`/TransitionExecutor. Branch IDs are enabled threads,
so each has a nonempty immutable ModelThread step list; List.copyOf rejects null elements.
Replacing that lookup's PC with zero still fetches a non-null step and cannot change traversal
for valid models. No equal-resource/work/performance claim or synthetic null-step model is used.

At 434, Configuration.allTerminated reads a final boolean. A true value already returned through
the earlier completion branch in the same frame; successors are different configurations.
Removing the later read substitutes false, the value it always has on this path.

The six uncovered identities sit inside the second invariant-failure branch at deadlock. A false
pure invariant already recorded a violation and returned earlier. For a frame reaching deadlock,
no enabled transition ran and no callback changes the state between the two checks; the same
configuration/property must yield the same value. The null-invariant case also cannot enter that
branch. These are supported-contract reachability dispositions, not claims about stateful predicates,
mutating visitors, reflection/unsafe changes, concurrent mutation or performance. Keep code and
mutations unchanged; do not manufacture unsupported inputs merely to cover them.

## Verification

All **881 tests** pass, including eight new invocations, zero failures/errors/skips.
Outer-StaticPorExplorer PIT improves **106/122 → 114/122 KILLED**: seven survivors and one uncovered
identity become killed. All 122 semantic identities match, and all 106 prior kills remain killed.
Two survivors and six uncovered remain, zero timeout/error detections; all eight have the scoped
redundancy/reachability dispositions above rather than being hidden or forced through unsupported inputs.

| Final status | Identities | Behavioral oracle |
|---|---|---|
| KILLED (1) | `explore` / 47 | Literal source four-position/two-witness counterexample |
| KILLED (1) | `explore` / 18 | Prepopulated real store still receives two new visits |
| KILLED (2) | `porDfs` / 164, 167 | Full-branch first exception and visited prefix match literal DFS oracles |
| KILLED (4) | `porDfs` / 80, 116, 399, 502 | Property/completion/failing-edge/deadlock callback evidence |
| SURVIVED (2) | `porDfs` / 259, 434 | Non-null lookup and immutable termination-flag dispositions |
| NO_COVERAGE (6) | `porDfs` / 450, 452, 455, 461, 466, 471 | Pure-property deadlock branch is unreachable under the supported contract |

The final integrated `clean build javadoc pitest` passes all 881 tests and strict Javadoc.
Default PIT remains **281/291**, with identical semantic identities/statuses to prior merged-main
evidence. The ten retained default mutations keep their existing scoped dispositions; no uncovered/
error detections. All 18 visualizer normalization checks pass.

CodeRabbit CLI completed the full four-file review with zero findings (`review_completed`, not
skipped). Only measured verification/status prose was finalized afterward. Browser integration was
not rerun because executable production and report output are unchanged; CI is unverified at
publication. DPOR and selector-helper verification remain separate follow-ups.
