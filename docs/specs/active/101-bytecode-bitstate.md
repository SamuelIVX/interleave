<!-- Roadmap #12 / 101: implementation milestone specification; no production implementation in this PR. -->
# Spec 101: Bitstate storage and selected-scope completion

## Objective

Deliver bitstate storage and selected-scope completion as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 101 only.

## Scope

Extend bytecode search/store integration for complete DFS/CBS/DPOR products with one owned Bloom
filter, reusing existing BitstateStore primitives without hidden exact production maps. Wire runner (including the owned Builder.bytecodeBitstate selection),
CLI, TestResult/report/attestation/strict replay/viewer and final supported-profile documentation.
Add BytecodeBitstateProductTest/collision fixtures, complete compiler matrix, pinned JPF comparison
harness/evidence and docs/measurements/101-bytecode-bitstate.md. No new language or reduction semantics.

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

1. **R1 — One approximate store.** WHEN BITSTATE is selected, THE SYSTEM SHALL encode complete typed
bytecode state and strategy context into one filter: value domain, CBS last actor/cost product and DPOR
continuation/sleep context as applicable. It SHALL retain no exact visited/context shadow in production;
filter budget SHALL not be presented as total JVM memory.
2. **R2 — Precision.** WHEN approximate exploration finds no failure, THE SYSTEM SHALL report
APPROXIMATE_PASS or INCOMPLETE as appropriate, never exact absence. Actual violations/deadlocks/cutoffs
SHALL retain concrete strictly replayable witnesses. Requested DPOR with effective DFS fallback SHALL
retain both fallback and approximate precision.
3. **R3 — Product verification.** WHEN exact/approximate equality is asserted on a fixture, THE SYSTEM
SHALL independently establish zero false-positive decisions across that fixture's full relevant context
universe, including cross-domain interference. Deliberate collisions SHALL demonstrate possible missed
behavior without fabricating positive witnesses or claiming calibrated coverage.
4. **R4 — Compiler and cross-tool gate.** WHEN selected-scope acceptance is evaluated, THE SYSTEM
SHALL publish Java17/21/26 supported/rejected compiler fixtures and a pinned JPF experiment: probe actual
class version/construct support, choose mutually supported compilation release and compare normalized
outcomes with scheduling/semantic differences. Incompatibility SHALL mark that gate blocked and SHALL
not silently lower Interleave's Java17 input floor or claim complete acceptance.
5. **R5 — Integration and closure.** WHEN 101 completes, THE SYSTEM SHALL verify all supported strategy/
store/profile consumers, preserve exact/source/JSON/symmetry behavior and honest mutation evidence,
update roadmap status only to demonstrated acceptance, and stop after publishing its PR.

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

- R1: independently enumerate finite state/context bytes, domain separation, CBS cost/last actor,
DPOR sleep continuations, filter insertion/query semantics, clear/copy/concurrent run isolation and
cross-domain collisions. Test-only exact shadows cannot leak into production configuration.
- R2/R3: tiny-filter forced collision may miss a real outcome; every found witness still replays.
Known zero-collision finite fixtures agree with exact normalized observations. Fallback+bitstate rows
remain approximate and excluded from exact agreement; root/later resource stops remain incomplete.
- R4: pin JPF repository revision/toolchain/configuration and store probe commands/results before choosing
compiler target. Lost-update and deadlock comparisons use actual compiled common supported fixtures;
explain missing outcomes due to scheduling/semantics without forcing misleading equality. If no mutual
release >=17 supports the required constructs, record blocked evidence and leave completion unaccepted.
- R5: built-jar, CLI, JSON/Markdown, attestation, strict replay, normalization/browser exercise DFS/CBS/
DPOR admitted and fallback with EXACT and approximate BITSTATE. Bytecode static POR,
symmetry and weak-memory requests continue rejecting. Full gates preserve default and scoped mutation
accounting; no NO_COVERAGE-as-kill or inherited baseline assumptions.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [100](100-bytecode-atomics.md) merged, plus the shared contract.
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

`bytecodeBitstateEncodesCompleteStrategyProducts` (R1), `collisionsNeverInventWitnesses` (R2/R3),
`independentZeroCollisionUniverseMatchesExact` (R3), `compilerAndPinnedJpfEvidenceIsHonest` (R4),
`allSupportedBytecodeConsumersKeepPrecision` (R5). Kill incomplete product keys, exact-shadow routing,
precision upgrades, context-cost omission and unconditional completion claims.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [100](100-bytecode-atomics.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/101-bytecode-bitstate.md` with implementation: fresh baseline/final evidence,
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
