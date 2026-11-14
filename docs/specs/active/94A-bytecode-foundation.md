<!-- Roadmap #12 / 94A: internal interpreter foundation and its implementation acceptance contract. -->
# Spec 94A: Interpreter foundation

## Objective

Deliver interpreter foundation as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: internal implementation; public release remains94C.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 94A only.

## Scope

Internal `src/main/java/dev/samhb/interleave/bytecode/`: planned BytecodeProgram, BytecodeLimits,
BytecodeLoader, BytecodeState, BytecodeFrame, OpcodeExecutor and metadata verifier. Reserve the future
format/BytecodeProgramLoader facade for 94C. Add corresponding package tests and
src/test/resources/bytecode/foundation fixtures, including separately compiled 17/21/26 inputs.
Before94C, compile fixture sources against test-only annotation declarations with the approved binary
names; publish those annotations in the main artifact only with94C. No public runner/CLI/report/viewer selectors; 94B owns concurrent search/checks and full monitor behavior.

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

1. **R1 — Closed loading.** WHEN bytes are loaded, THE SYSTEM SHALL enforce the shared contract's
version/input bounds, annotations, hierarchy/access/stack verification, reachable support and executable
initializer rejection using JDK 26 Class-File API and metadata only. Bound annotation nesting at32
before recursive decoding; reject reachable bridge methods skipped by the JDK verifier. Missing classes, untrusted platform
calls, malformed branches/handlers, wrong method roles and target-path escape SHALL reject before execution.
2. **R2 — Setup and ownership.** WHEN a fixture loads, THE SYSTEM SHALL interpret its public no-arg
constructor and known helper calls, allocate fixture/Object monitors in setup identity space and freeze
an isolated snapshot. Setup faults/limits SHALL yield loading diagnostics. All runs/copies SHALL be independent.
3. **R3 — Execution.** WHEN one actor executes internally, THE SYSTEM SHALL implement the first-profile
int/boolean/reference instruction subset and real branches/calls/stacks with one deterministic opcode
executor. Unsupported actor allocations/arrays/dispatch/general handlers SHALL reject before exploration.
4. **R4 — Budgets.** WHEN work reaches a limit, THE SYSTEM SHALL charge executed opcodes, permanent
allocator cells and identities exactly as the contract specifies. It SHALL permit return at N and omit
N+1, distinguish setup/actor accounting and preserve canonical counters/frames/pending-event state.
5. **R5 — Dynamic faults and cleanup recognition.** WHEN null access or divide/remainder zero occurs,
THE SYSTEM SHALL produce interpreted fault state with real BCI/stack, preserving prior effects and
charging the throwing opcode. It SHALL validate compiler monitor-cleanup handler shapes now, permit
required astore/aload/athrow cleanup instructions only in that verified context, and retain pending
unwind metadata. Concurrent/reentrant release semantics are completed in 94B, not claimed here.
6. **R6 — Independent evidence.** WHEN foundation acceptance runs, THE SYSTEM SHALL compare generated
and targeted sequential executions with a separate JVM process, saving seeds, shrunk failures and
unsupported fixtures. Test oracle execution SHALL never become a production loader fallback.

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

- R1: Java 17/21/26 class majors accepted for supported code; preview/newer/older/malformed inputs,
missing superclass, wrong static/instance invocation, invalid stacks, duplicate/gapped actor IDs,
9 actors, zero actors, static/argument/nonvoid actors and impure annotations rejected. Boundary input
sizes use bounded byte reads and reject non-regular files before opening. The classpath root and entries
remain immutable during loading; concurrent replacement is unsupported. No reflection/classloader
invocation even for setup.
- R2/R3: constructor-wired distinct Object locks, int overflow/shifts, local branches/loops, static
helpers and exact-limit normal return match independent JVM values. Concurrent calls to internal
fresh-state construction cannot leak heap/frame mutations.
- R4/R5: N versus N+1, call-depth crossing, disabled-attempt no-charge seam, setup quota isolation,
reversed identity construction, null field and division after an earlier write preserve exact state.
Cleanup handler impostors reject; recognized javac cleanup metadata survives loading.
- R6: fixed-seed bounded sequential generator with shrinking and persisted minimal regression; independent
oracle does not call OpcodeExecutor/encoder to compute expected values. Record compiler/toolchain hashes.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; approved shared contract/specification PR and freshly fetched main.
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
- [implementation evidence] Package-private loader/state/executor, setup and VM faults are implemented.
  [94A ledger](../../measurements/94A-bytecode-foundation.md) records fresh baseline/final checks,
  compiler/oracle conditions, mutation residuals and review coverage. The planning observations above
  remain historical; they are not substituted for that evidence.
- [unverified] Concurrent search/check execution, batching/sharedness costs, bytecode DPOR reduction and
  JPF compatibility remain later-phase obligations. Single-actor monitor tests do not close94B.

## Tests

`loadsOnlyClosedSupportedMetadata` (R1), `setupNeverExecutesOnHost` (R1/R2),
`integerAndReferenceExecutionMatchesJvm` (R3/R6), `exactFuelReturnAndFaultCharge` (R4/R5),
`isolatedCopiesRetainAllocatorIdentity` (R2/R4), `monitorCleanupShapeIsVerified` (R5).
Kill inverted limit boundaries, missing fuel charge, omitted state fields, shared copy aliases and
validation bypasses. Monitor concurrency tests belong to 94B.
These names describe the original acceptance contracts. Actual tests are BytecodeLoaderTest,
BytecodeAdmissionTest, BytecodeAnnotationInputTest, BytecodeInputBoundaryTest, BytecodeExecutionTest, BytecodeEventTest,
BytecodeResourceTest, BytecodeEncodingTest, BytecodeOpcodeTest and BytecodeGeneratedTest, with limits
covered by BytecodeLimitsTest. The ledger distinguishes actual results from later-phase gates.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: approved shared contract/specification PR and freshly fetched main; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/94A-bytecode-foundation.md` with implementation: fresh baseline/final evidence,
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
