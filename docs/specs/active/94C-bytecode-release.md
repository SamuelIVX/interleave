<!-- Roadmap #12 / 94C: implementation milestone specification; no production implementation in this PR. -->
# Spec 94C: Initial public bytecode release

## Objective

Deliver initial public bytecode release as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 94C only.

## Scope

Create format/BytecodeProgramLoader.java and public annotation/limits/program interfaces defined in
the contract. Modify InterleaveRunner.java, TestResult.java, TraceRecord.java, core evidence types,
search/TraceReplayer.java, cli/Main.java and report/{BenchmarkHarness,BenchmarkResult,SoundnessAttestation}.java
through explicit bytecode routes. Update docs/visualizer.js, normalization/browser checks, README.md,
examples/bytecode/ and API docs. Add built-jar/CLI/replay/report tests and
measurements/94C-bytecode-release.md. Static POR/CBS/DPOR/bitstate/symmetry remain unavailable until their
owning phases; no source/JSON behavior rewrite.

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

1. **R1 — Public selection.** WHEN the loader, runBytecode entry point or CLI selects bytecode, THE SYSTEM
SHALL expose exactly the shared signatures/annotation/limits contract and exact DFS selection, reject
incompatible arguments before search, and start fresh independent states. Generic Invariant/store callbacks and symmetry-specific
builder settings SHALL reject before invocation; the bytecode path SHALL use only declared checks
and owned exact storage. It SHALL keep public bytecode
selection absent before this phase's complete release.
2. **R2 — Identity and evidence.** WHEN execution emits a record, THE SYSTEM SHALL tag BYTECODE and
retain frozen artifact/profile/limit/mode identity, original actor choices, real method/descriptor/BCIs,
optional lines, transitions, immutable snapshots, monitor relationships and classified terminal evidence.
It SHALL neither fabricate SOURCE spans nor publish tentative host execution.
3. **R3 — Strict replay.** WHEN replay is requested, THE SYSTEM SHALL validate identity, enabled choices,
executed operations, outcomes, checks, monitors, pending terminals and exact cutoffs. Earlier failure,
false check or cutoff SHALL forbid trailing choices. External interruptions SHALL authenticate only
their captured prefix and preserve original incompleteness separately from replay observations.
4. **R4 — Consumers.** WHEN library/CLI emits JSON/Markdown and viewer data, THE SYSTEM SHALL preserve
precision, requested/effective strategy, real faults/checks/deadlocks/cutoffs and absence limits. Native
serialization SHALL explicitly version/reject incompatible shapes. Source/JSON serializers/replay and
source forward validation SHALL retain existing behavior.
5. **R5 — Usable release.** WHEN 94C is accepted, THE SYSTEM SHALL demonstrate independently compiled
lost-update and opposing-two-lock examples end to end, document supported/rejected constructs including
clinit restrictions, and incorporate reviewed 94B measurement/proof/default decisions.

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

- R1/R5: a separate Java built-jar consumer loads and runs both examples; actual CLI `--bytecode`
with `--class-path` produces the same normalized findings with the existing EXACT store selection. Missing/duplicate flags,
foreign model modes, invalid limits, unavailable strategies/stores and source-only simplify flags reject.
- R2/R3: reload at another directory with identical bytes replays; changed class bytes, debug bytes,
profile, limits/mode, selected modelClass/declaration map, BCI, actor, outcome, monitor, check identity and cutoff fields reject. Two fixtures sharing the same frozen class manifest cannot replay each other, including empty
interrupted/root records. Empty root
false-check replay succeeds; fabricated empty deadlock/completion and trailing choices fail.
- R3/R4: real runtime fault after committed write, cleanup-fuel cutoff and global stop retain honest
snapshots and classifications; observer failure remains INCOMPLETE. JSON round trips cannot turn them
into PASS. Existing source/declarative exact/approximate/symmetry fixtures stay compatible.
- R4: feed fresh Java artifacts to normalization and browser, including no-line-table classes, long
labels/untrusted text, initial failure, deadlock and resource interruption. Numeric schema reserves
lossless tagged wide types for 97. Document native-stream upgrade boundary where required.
- R5: link the 94B actual measurement ledger and preservation argument; release is blocked if equivalent
observable outcomes/default review is unresolved. Runtime examples are compiled externally; loader does not compile.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [94B](94B-bytecode-concurrency.md) merged, plus the shared contract.
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

`publicBytecodeLoaderAndRunnerWorkFromJar` (R1/R5), `bytecodeCliRejectsUnsupportedSelections` (R1),
`strictReplayAuthenticatesEveryBytecodeOutcome` (R2/R3), `bytecodeReportsRetainPrecisionAndLocations` (R4),
`bytecodeViewerUsesFreshProducerArtifacts` (R4), `legacyProfilesRemainCompatible` (R4).
Kill missing identity/limit/choice checks, false SOURCE routing, premature completion and precision upgrades.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [94B](94B-bytecode-concurrency.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/94C-bytecode-release.md` with implementation: fresh baseline/final evidence,
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
