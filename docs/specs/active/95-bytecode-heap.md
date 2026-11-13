<!-- Roadmap #12 / 95: implementation milestone specification; no production implementation in this PR. -->
# Spec 95: Objects, arrays and dispatch

## Objective

Deliver objects, arrays and dispatch as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 95 only.

## Scope

Extend bytecode/ metadata verifier, heap/state/encoder, OpcodeExecutor and sharedness analysis for
application allocations, arrays and general closed dispatch. Update public BytecodeProgram profile,
replay/snapshots, CLI help, reports/viewer fixtures, examples/bytecode and README supported matrix.
Add BytecodeHeapTest/BytecodeDispatchTest/BytecodePublicationTest and heap compiler resources.
General exception handlers belong to 96; remaining primitive/library operations to 97.

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

1. **R1 — Heap behavior.** WHEN actors allocate application objects/arrays, THE SYSTEM SHALL interpret
constructors, inherited fields, null/alias cycles, reference operations, checkcast/instanceof and virtual/
interface dispatch over every validated closed-hierarchy target. It SHALL preserve JVM field hiding,
overrides, access/nestmate rules and constructor initialization, without target class loading.
2. **R2 — Arrays.** WHEN array opcodes execute, THE SYSTEM SHALL support primitive/reference and
multidimensional arrays, length and bounds/store/type/null faults, and exact rank/component metadata.
Each allocated dimension SHALL receive a real identity and budget charge in deterministic JVM order.
Long/float/double elements may exist with default values; their computations remain rejected until 97.
3. **R3 — Publication.** WHEN a reference escapes, THE SYSTEM SHALL promote reachable objects before
publication is observable, retain sharedness history and make shared array/field accesses schedulable.
Unknown roots SHALL be shared; path-sensitive locality SHALL never be inferred from allocator alone.
4. **R4 — Accounting.** WHEN objects escape or become unreachable, THE SYSTEM SHALL retain allocator
namespace, sequences and permanent allocation/cell charges. Length/count/cell/depth checks SHALL follow
the shared failure priority, preserving prior committed instructions and explicit partial multidimensional
allocation effects when a later component hits a budget. No GC/renumbering SHALL occur.
5. **R5 — Complete extension.** WHEN this broader profile is selected, THE SYSTEM SHALL extend strict
replay/evidence/consumers and the two-mode preservation argument with dynamic allocation, dispatch and
publication cases. New dynamic VM faults SHALL follow initial unwind/uncaught semantics; user catch remains rejected.

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

- R1: inherited/hidden fields, abstract/interface alternatives, invokevirtual/invokeinterface/invokespecial,
null receiver, failed casts, missing targets, nestmate private calls and unknown metadata have independent
expected behavior. Malformed linkage rejects before search; dynamic null/cast/array faults do not.
- R2/R4: zero/max/max+1 lengths, negative length, index bounds, reference covariance/store failure,
multidimensional partial construction, inherited field cells and per-allocator exact budget crossings.
No partial object identity is committed for an allocation that fails before creation; earlier successful
component allocations remain charged in terminal state. JVM differential testing covers non-budget semantics.
- R3/R4: cyclic publication, reference retained only on operand stack, two actors publishing to each
other, reversed allocation interleavings and post-publication allocator exhaustion retain identities,
charges and history. Compare reference/batching normalized outcomes; do not assert equal local snapshots.
- R5: strict replay of published aliases/array failures/resource cutoffs and browser references uses
fresh artifacts. All former initial-profile examples and legacy/source symmetry runs remain stable.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [94C](94C-bytecode-release.md) merged, plus the shared contract.
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

`heapAliasesAndDispatchMatchJvm` (R1/R2), `arrayFaultsRemainDynamic` (R2),
`transitivePublicationPrecedesVisibleEscape` (R3), `escapeRetainsAllocatorCharges` (R4),
`reversedAllocationsKeepNamespacedIdentity` (R4), `heapProfileReplaysAcrossConsumers` (R5).
Kill omitted inherited cells, actor-global allocation sequence, reversible sharing and alias-copy errors.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [94C](94C-bytecode-release.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/95-bytecode-heap.md` with implementation: fresh baseline/final evidence,
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
