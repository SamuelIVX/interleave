<!-- Roadmap #12 / 94B: implementation milestone specification; no production implementation in this PR. -->
# Spec 94B: Concurrent execution and feasibility

## Objective

Deliver concurrent execution and feasibility as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 94B only.

## Scope

Internal bytecode scheduler/transition and exact-search adapter in bytecode/; extend OpcodeExecutor,
BytecodeState and verifier for full monitor/cleanup/check execution. Reuse the complete-payload membership policy of state/HashingStateStore.java through an owned
bytecode key adapter; it currently accepts Configuration, so do not claim direct bytecode reuse or
weaken its existing encoding. Use an independent sorted-key set in tests, not a new public backend.
Do not adapt via source forward PCs. Add BytecodeConcurrencyTest, BytecodeBatchingOracleTest and BytecodeMeasurementTest with
src/test/resources/bytecode/concurrency fixtures and docs/measurements/94B-bytecode-concurrency.md.
Public loader/runner/CLI/report/viewer remains 94C ownership.

## Non-Goals

No expansion beyond this milestone's behavior. No host execution of target code, executable application
initializers, dynamic actors, collections, wait/notify, weak memory, bytecode static POR/symmetry,
frozen exhausted actors or fuel-free cycle detection. Later-phase support stays rejected until its
complete implementation and consumers merge.

## Invariants

- Existing explicit/source/JSON/replay/symmetry behavior, validation and mutation policy remain intact.
- Owned state and deterministic identities preserve aliases, resource counters and sharedness history.
- Cutoffs end prefixes; external stops and observer errors remain honestly incomplete.
- All executed target behavior stays interpreted; no host fallback or forged source locations.
- Public selection follows the phase release boundary and never advertises partial consumer support.

## Requirements

1. **R1 — Concurrent state.** WHEN fixed actors run, THE SYSTEM SHALL enumerate enabled choices with
exact DFS over complete owned states, including real BCIs/frames, monitors, sharedness history, budgets
and pending events. EXACT enumeration SHALL agree with the independent reference on normalized outcomes. Backedges SHALL not weaken
Configuration.successorAt's existing forward-source validation.
2. **R2 — Monitor behavior.** WHEN blocks or synchronized methods acquire/release, THE SYSTEM SHALL
honor identity, owner/depth, static class locks, disabled-attempt no fuel, reentrance and normal/abrupt
cleanup. Nonowner/null release SHALL fault. A blocked acquire SHALL remain blocked until enabled.
3. **R3 — Checks and verdicts.** WHEN checks execute, THE SYSTEM SHALL enforce transitive purity,
initial/ALWAYS/FINAL timing, separate observer fuel and false/fault/exhaustion classifications. It SHALL
classify deadlock only with actual blocked actors and no executable/pending choices, and preserve
uncaught-fault versus false-check identity.
4. **R4 — Two modes.** WHEN REFERENCE/BATCHED executes, THE SYSTEM SHALL share OpcodeExecutor and
retain observable read/write/publication/monitor/terminal boundaries. It SHALL yield before terminal
failure/cutoff/termination and perform pure classification. Unknown locality or observation effects
SHALL prevent batching; conservative sharedness SHALL propagate transitively and irreversibly.
5. **R5 — Preservation.** WHEN batching is proposed for release, THE SYSTEM SHALL supply a written
state relation and event/observation argument covering errors, fuel, observer evaluation, cleanup and
terminal competition. Every allowed local segment SHALL justify omitted interleavings; equivalence of
verdict strings alone SHALL not suffice.
6. **R6 — Measurement gate.** WHEN 94B completes, THE SYSTEM SHALL measure both modes on identical
lost-update and two-lock fixtures/limits using all common-contract metrics, raw repetitions and
conditions. It SHALL compare normalized outcome sets, document default-limit suitability and investigate
regressions before 94C. No benchmark proxy SHALL be reported as interpreter evidence.

## Failure Modes & Edge Cases

- WHEN metadata, descriptors or reachable behavior is unsupported, THE SYSTEM SHALL reject before
exploration with actionable identity/location diagnostics; dynamic supported faults SHALL remain runtime behavior.
- WHEN actor/setup/observer budgets fail, THE SYSTEM SHALL preserve their separate ownership and the
shared priority, exact-limit semantics and omitted operation. A check/setup failure SHALL not become an actor violation.
- WHEN a run stops externally, THE SYSTEM SHALL retain original INCOMPLETE classification and genuine
already-recorded findings. It SHALL not manufacture a reproducible modeled resource cutoff.
- WHEN this milestone exposes new payload/state, THE SYSTEM SHALL include every future-relevant field
in keys and replay validation, including pending terminal events and permanent allocation charges.

## Acceptance Criteria

- R1/R2: actual `x++` produces lost-update final x=1 and nonlost x=2; two opposing Object locks produce
real wait-cycle evidence as well as terminating schedules. Nested/reentrant blocks, synchronized
instance/static methods and exception cleanup release exactly their held depths. Null monitor faults. Zero-fuel blocked entry exposes the prior fuel cutoff;
a sufficiently fueled disabled entry changes nothing and consumes zero.
- R3: initially false ALWAYS, transient shared false, final-only false, absent checks, check arithmetic
error and observer fuel/depth stop have distinct expected outcomes. Reject checks that allocate or
mutate through helper/dispatch paths; FINAL never runs on deadlock/exception/cutoff.
- R4/R5: independent opcode-choice enumerator agrees on normalized observations, including cutoff
actor/reason/omitted BCI/limit, with batching for targeted/generated tiny programs. Pure local infinite
loop, fuel-sensitive branch and exhaustion competing with another actor's violation cannot disappear.
Fault discovered after a local prefix yields before delivery; unselected probe leaves fuel/state untouched.
- R4: canonical encoding retains history even when fixture fields are rewired; frame-root reachability
and unknown metadata rejection are covered. Until 95 fixtures cannot introduce unsupported allocations
or object graphs. Nontrivial cyclic publication/history/allocator adversarial cases are 95 acceptance;
94B reports genuine zero/not-applicable metrics when its setup-only profile cannot produce those states.
- R6: publish states/transitions/encoding/elapsed/retained-memory/sharedness/history-only-state data
with reproducible commands and variability. Correctness or unexplained cost gaps block the public release;
no fixed percentage speed target and no claim of prior measured performance.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [94A](94A-bytecode-foundation.md) merged, plus the shared contract.
Record fresh full build/strict Javadoc/tests and affected/default PIT fingerprints before production edits.
The authoring SHA and older counts are historical context, never fresh implementation baselines.
- Call-site migration: all paths listed in Scope, plus their existing connected tests and exports;
new profile state reaches executor, encoder, replay and consumers in this same milestone where exposed.
- Preserve existing public signatures/semantics, source forward validation, mutation operators/floors,
resource settings and CI parallelism. No hidden compatibility adapter may bypass validation.
- Roll back/restrict the newly admitted profile coherently if normalized reference outcomes diverge,
state/replay identity omits future behavior, cleanup/evidence is fabricated or legacy compatibility fails.
Do not weaken tests, change bounds to conceal a counterexample or silently enable a later phase.

## Design

Use the shared contract's deep loader/executor/search seams: immutable validated metadata and program,
owned execution state, one opcode executor and mode-aware scheduler, then existing result/consumer
adapters where contracts permit. Keep bytecode continuation identity separate from source forward PCs.
Do not add a generic backend/framework for hypothetical languages. Internal names in Scope are planned;
choose existing equivalent modules at implementation after connected-source inspection.

## Current State

- [verified] Planning main is `2a242c4`; the existing engine has immutable Program, forward SourceStep
continuations and source-specific evidence. The shared contract records inspected integration constraints.
- [approved decision] This milestone and the shared contract define the selected behavior/release boundary.
- [unverified] This milestone's bytecode implementation, measurements, compiler/runtime acceptance and
mutation results. No authoring review establishes those results.

## Tests

`lostUpdateAndDeadlockMatchInstructionEnumeration` (R1/R2/R4),
`reentrantCleanupAndBlockedAttemptsKeepExactAccounting` (R2), `checksUseSeparatePureObservers` (R3),
`batchYieldsBeforeCompetingTerminalEvents` (R4/R5), `historyAndBudgetsAffectKeys` (R1/R4),
`referenceBatchOutcomeGate` (R5/R6). Kill read/write merging, missing shared-history bits,
premature terminal delivery, cleanup depth errors and cutoff-as-deadlock mutations.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [94A](94A-bytecode-foundation.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/94B-bytecode-concurrency.md` with implementation: fresh baseline/final evidence,
independent checks, raw measurement conditions, review dispositions and limits. Authoring evidence belongs
in the specification-set record, not a fabricated runtime ledger.

## Relevant Skills and Review

- doc-coauthoring: requirements/acceptance completeness and fresh-reader testing.
- domain-modeling / codebase-design: reuse [domain vocabulary](../../../CONTEXT.md) and the shared
small owned interfaces; do not create a competing glossary or source-shaped bytecode abstraction.
- tdd: establish failing behavioral regressions before new production behavior; fixed-seed/shrinking
oracles supplement targeted cases. owasp-security: fail-closed untrusted class metadata and replay input.
- ponytail / ponytail-review: smallest coherent implementation and connected-source review.
- code-review: actual CodeRabbit after this completed specification and before substantial implementation
publication; record completed/quota/error status through agent-setup without inventing completion.
- 94B/95/96/97: review the batching preservation argument for every newly admitted behavior; 99/100/101:
review reduction admission/resource/product obligations in addition to runtime tests.

The specification-only PR runs document/link/structure validation, reader review and required actual
CodeRabbit invocations. It does not run Java/PIT/browser feature checks for code that does not exist.
After publishing this milestone's implementation PR, report its link and stop; do not watch CI.
