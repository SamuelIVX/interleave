<!-- Roadmap #12 / 99: implementation milestone specification; no production implementation in this PR. -->
# Spec 99: Conservatively admitted bytecode DPOR

## Objective

Deliver conservatively admitted bytecode dpor as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 99 only.

## Scope

Create internal bytecode admission/resource/dependency/observation analysis and dynamic continuation
sleep-context representation; integrate dpor/DporExplorer.java through a justified bytecode path or
small bytecode-specific explorer, preserving source future-list semantics. Update requested/effective
selection and fallback consumers in runner/CLI/report/replay/viewer. Add BytecodeDporAdmissionTest,
BytecodeDporOracleTest, BytecodeDporReductionTest and measurements/99-bytecode-dpor.md with written
preservation argument. No static POR, symmetry, cyclic optimization or bitstate.

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

1. **R1 — Admission.** WHEN DPOR is requested, THE SYSTEM SHALL require acyclic reachable control and
call graphs, conservative resource bounds within selected limits and complete dependency/observation
summaries. Analysis SHALL include setup, dispatch alternatives, constructors, intrinsics, implicit
monitor/unwind/error events and all check executions. Unknown metadata SHALL already reject loading;
unknown analysis costs/effects or possible exhaustion SHALL select exhaustive traversal with reason.
2. **R2 — Runtime cutoff.** WHEN execution reaches an actual limit, THE SYSTEM SHALL determine it
from runtime state and preserve prefix-ending cutoffs, conservatively dependent with competing choices.
It SHALL append no sentinel or future pseudo-cutoff and SHALL infer no independence from actor-local
fuel/allocation ownership alone. Runtime exhaustion contradicting an admission certificate SHALL be
a failed correctness gate, with safe exhaustive rerun or INCOMPLETE analysis-error result, never trusted
reduced absence.
3. **R3 — Independence.** WHEN actions are pruned, THE SYSTEM SHALL establish enabledness preservation,
commutation, check/error observations and terminal-outcome preservation, including publication,
allocation identity and monitor ownership. Dynamic continuations and complete sleep contexts SHALL
enter visited coverage; list indices/Step identity from source SHALL not masquerade as bytecode actions.
4. **R4 — Argument and measurement.** WHEN optimization is accepted, THE SYSTEM SHALL provide a written
preservation argument and genuine measured reduction on an acyclic disjoint-write fixture versus exact
DFS at identical mode/limits. It SHALL exercise fallback/terminal behavior separately; a DPOR label or
all-path exhaustive run SHALL not count as reduction evidence.
5. **R5 — Consumers.** WHEN DPOR falls back, THE SYSTEM SHALL report requested DPOR, effective DFS and
specific reason in library/CLI/reports/viewer/replay metadata. Loops SHALL remain usable via exhaustive
fallback. Existing source/JSON DPOR and check contracts SHALL remain intact.

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

- R1: bound every path through dispatch and exceptions with saturating arithmetic; compute actor fuel,
allocations/cells/depth and per-observer-invocation demand plus setup, including payload growth
charged to an original allocator when other actors mutate escaped modeled objects. Setup/check error possibilities
and intrinsic payload costs are included. Known dynamic faults cannot be treated as invisible locals.
Unknown observation or possible resource exhaustion falls back; simple separate budgets do not admit.
- R2/R3: exact-limit return, exhaustion versus another actor's violation, null fault versus release,
publication/aliases and complete sleep coverage match independent exhaustive normalized sets. Test
all-asleep handling without calling it deadlock; no synthetic sentinel appears in future summaries.
- R3/R4: argument states admitted domain, complete state relation, action identity, observation visibility,
terminal cases, sleep product coverage and exhaustive fallback. Review counterexamples before acceptance.
Acyclic independent writes to distinct unobserved fields with FINAL checks absent or justified must
reduce actual states/transitions and preserve final observations; publish raw counts, not invented targets.
- R5: cyclic loops, unknown costs and insufficient limits show distinct fallback reasons in actual jar,
CLI/report/browser output. Effective DFS is exact with exact storage; source DPOR tests still pass.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [98](98-bytecode-cbs.md) merged, plus the shared contract.
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

`admissionIncludesImplicitAndObserverCosts` (R1), `possibleExhaustionFallsBackWithoutSentinel` (R1/R2),
`dynamicSleepProductsMatchExactEnumeration` (R3), `acyclicDisjointWritesActuallyReduce` (R4),
`allConsumersReportRequestedAndEffectiveTraversal` (R5). Kill omitted dispatch alternatives,
underestimated intrinsic/unwind costs, fuel-only independence, lost sleep context and false optimized labels.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [98](98-bytecode-cbs.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/99-bytecode-dpor.md` with implementation: fresh baseline/final evidence,
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
