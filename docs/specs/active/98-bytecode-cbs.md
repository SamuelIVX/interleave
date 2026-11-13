<!-- Roadmap #12 / 98: implementation milestone specification; no production implementation in this PR. -->
# Spec 98: Exact bytecode context-bounded search

## Objective

Deliver exact bytecode context-bounded search as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 98 only.

## Scope

Extend bytecode exact-search adapter and cb/ContextBoundedExplorer.java only through a narrow owned
bytecode route, preserving existing source/JSON paths. Wire InterleaveRunner/CLI strategy selection,
reports/attestation/replay/viewer and CBS iteration/resource prefixes; add BytecodeCbsOracleTest,
built-jar/CLI/browser fixtures and measurements/98-bytecode-cbs.md.
DPOR analysis belongs to 99; bitstate to 101.

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

1. **R1 — Cost product.** WHEN CBS explores, THE SYSTEM SHALL retain complete bytecode state plus
last actor and minimum preemption cost. Switching from a runnable prior actor SHALL cost one; forced
switch after blocking/termination SHALL cost zero. Pending terminal event enabledness SHALL follow the
shared scheduler, never host-thread status.
2. **R2 — Bounds.** WHEN another preemption would exceed the selected bound, THE SYSTEM SHALL emit
a prefix-ending PREEMPTION_BOUND cutoff at the omitted bytecode/event location, continue alternatives
and preserve fuel/allocation counters without executing the omitted choice. It SHALL not freeze actors.
Cost dominance SHALL not suppress uncovered cutoff obligations: retain/revisit distinct cost contexts
until their omitted-operation outcomes are covered, even when a lower-cost visit covers positive
behavior. A completed lower-cost path SHALL not erase bounded incompleteness on a higher-cost path.
3. **R3 — Reference agreement.** WHEN compared at a context bound, THE SYSTEM SHALL match independent
exhaustive enumeration at the same production scheduling granularity. Instruction-reference CBS SHALL
be evaluated at its own granularity; changing granularity SHALL not be assumed to preserve context costs.
4. **R4 — Isolation and consumers.** WHEN iterative/repeated/concurrent CBS runs occur, THE SYSTEM
SHALL use fresh per-bound membership and immutable bindings, preserve real evidence, and expose
requested/effective strategy, bound and precision through library/CLI/reports/viewer. Discovered failure
bound SHALL not imply a globally shortest schedule or a proven minimum after interruption.

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

- R1/R3: independent tiny-state enumerator validates bounds 0,1,2; running versus blocked switches,
reentrant monitors, local computation, pending failure/cutoff and termination. Same values at different
last actor/cost remain distinct where future costs differ; lower cost dominates only soundly.
- R2/R3: two actors with two independent abstract actions each, bound2, include reachable prefixes
B1,A1,A2 and A1,B1,A2 and their full continuations. Independently enumerate last-actor/cost and
preemption omissions before applying dominance; retain the first actual bound-prune witness and
INCOMPLETE status. Extend with a case where the omitted choice lies below a dominated state.
- R2: strict replay authenticates owner/location/limit/omitted choice for preemption cutoff and rejects
an executed over-bound action. Instruction fuel cutoff competing with preemption uses fuel only when
that actor is selected; a preemption-disallowed choice is not executed or charged.
- R3/R4: iterative CBS equals fresh single-bound results, interrupted search remains INCOMPLETE and
real violations/deadlocks retain their original witnesses. Built-jar/CLI and fresh browser artifacts
exercise explicit/omitted selection and invalid bound handling; old source CBS regressions remain.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [97](97-bytecode-core-library.md) merged, plus the shared contract.
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

`bytecodeCbsMatchesIndependentBoundedEnumeration` (R1/R3),
`forcedSwitchAndPendingTerminalCostsAreExact` (R1), `preemptionCutoffOmitsUnexecutedChoice` (R2),
`iterativeBytecodeCbsIsIsolated` (R4), `cbsConsumersRetainLimitsAndPrecision` (R4).
Kill last-actor omission, wrong runnable-switch cost, strict-versus-nonstrict bound and per-bound reuse.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [97](97-bytecode-core-library.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/98-bytecode-cbs.md` with implementation: fresh baseline/final evidence,
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
