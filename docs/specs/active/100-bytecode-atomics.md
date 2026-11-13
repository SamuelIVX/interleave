<!-- Roadmap #12 / 100: implementation milestone specification; no production implementation in this PR. -->
# Spec 100: Narrow sequentially consistent atomic variables

## Objective

Deliver narrow sequentially consistent atomic variables as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 100 only.

## Scope

Extend bytecode IntrinsicRegistry/heap/value/fault/sharedness/dependency/resource metadata for the
exact AtomicInteger/AtomicBoolean/AtomicReference descriptors below. Update DPOR analysis, strict
replay, library/CLI/report/viewer and supported examples. Add BytecodeAtomicTest/BytecodeCasLoopTest
and measurements/100-bytecode-atomics.md. No weak-memory or broader atomic methods.

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

1. **R1 — Atomic descriptors.** WHEN allowed atomic constructors/get/set/strong compareAndSet execute,
THE SYSTEM SHALL use modeled payloads with SC atomic visible transitions, exact old-value tests and
specified return values. Failed CAS SHALL still read/observe and consume its intrinsic work.
2. **R2 — Identity/publication.** WHEN AtomicReference CAS compares expected with current, THE SYSTEM
SHALL compare modeled identity, including null, never equals. Successful set/CAS SHALL promote
transitive referenced objects before publication; failed CAS SHALL not publish the replacement.
3. **R3 — Bounds and DPOR.** WHEN retry loops occur, THE SYSTEM SHALL allow DFS/CBS within fuel and
fall back exhaustively for DPOR due to cyclic control flow. Acyclic atomic programs SHALL require a
fresh complete cost/dependency proof for optimized admission; unknown effects SHALL fall back.
4. **R4 — Coverage and consumers.** WHEN this profile is selected, THE SYSTEM SHALL extend replay,
snapshots/dependency identities/reports/viewer and JVM/reference oracles. Unsupported weak CAS,
getAndUpdate/callback variants, lazySet, acquire/release/opaque operations and fences SHALL reject.

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

- R1: `java/util/concurrent/atomic/AtomicInteger` `<init>()V`, `<init>(I)V`, `get()I`, `set(I)V`,
`compareAndSet(II)Z`; AtomicBoolean analogous Z descriptors; AtomicReference `<init>()V`,
`<init>(Ljava/lang/Object;)V`, `get()Ljava/lang/Object;`, `set(Ljava/lang/Object;)V`,
`compareAndSet(Ljava/lang/Object;Ljava/lang/Object;)Z`. Constructor initializes one payload field;
header+field costs two cells, normal new allocation charging. Invoke costs one opcode plus one
intrinsic fuel unit; constructors/get/set/CAS each execute atomically. Null receiver faults as usual.
- R1/R2: success/failure/no-change CAS, equal-valued distinct reference objects, null expected/current,
publication chains and failed-publication negatives match independent expected outcomes. No spurious
failure for strong CAS; volatile metadata does not claim a Java Memory Model implementation.
- R3: bounded terminating CAS retries can succeed; contention/spin paths can cutoff, preserving exact
actor/reason/location/limit. DPOR fallback is visible; sufficient acyclic programs use only justified
summaries. Re-run earlier DPOR resource certificates against newly supported registry entries.
- R4: strict replay rejects changed expected/current identity and return result; fresh producer artifacts
render atomic objects and fallback without hiding incompleteness. Unsupported overload fixtures reject.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [99](99-bytecode-dpor.md) merged, plus the shared contract.
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

`strongCasMatchesValueOrReferenceIdentity` (R1/R2), `casPublicationIsTransitiveOnlyOnSuccess` (R2),
`casRetryLoopsUseBoundedExhaustiveFallback` (R3), `atomicDescriptorsAndConsumersStayClosed` (R4).
Kill equals-for-reference-CAS, spurious failure, failed-CAS publication and cyclic optimized admission.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [99](99-bytecode-dpor.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/100-bytecode-atomics.md` with implementation: fresh baseline/final evidence,
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
