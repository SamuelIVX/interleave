<!-- B10/L1 specification: trusted declarative certification and complete exact strategy integration. -->
# Spec 92: Certified declarative symmetry with exact storage

## Objective

Let validated declarative JSON models use checked symmetry through all four exact strategies,
the existing library and --file CLI/report/viewer paths. Preserve declarative execution and original
owner evidence while reusing the existing checked context/key contracts.

**Type: delta. Status: merged in PR #136 at `013c9630e13b9835f435e4344837bf3b10e673db`.** Fresh baseline is
merged spec PR #135 at `c8ee55373701d0a7f131c752397460a61217aaf4`. The
[B10 set contract](../../plans/declarative-symmetry-contract.md) owns shared terminology, interfaces,
oracles and release boundaries. This spec owns L1 only; B10 completes after L2 merges.

## Scope

- Module: declarative validation/certification, checked search routing and exact consumers.
- Adds read-only Program.initialStateType()/initialStateInputType() queries in core/Program.java
  so certification checks both snapshot and original constructor input types before invoking
  further model copy/initialization code. This is metadata only,
  not a certificate or execution interface; generic Program behavior remains unchanged.
- Modifies `src/main/java/dev/samhb/interleave/format/dsl/{DslLoader,DynamicStep,DslInvariant,SourceSymmetry,SourceInstruction}.java`;
  SourceInstruction changes are limited to sharing exhaustive expression identity inspection.
- Modifies `src/main/java/dev/samhb/interleave/search/{SymmetryRunContext,SymmetryStateStore,BitstateSymmetryStateStore,TraceReplayer}.java`,
  `src/main/java/dev/samhb/interleave/{InterleaveRunner,TestResult}.java`,
  `src/main/java/dev/samhb/interleave/{dpor/DporExplorer,por/StaticPorExplorer,cli/Main}.java` and
  `src/main/java/dev/samhb/interleave/report/{BenchmarkHarness,SoundnessAttestation}.java` for owned
  routing, API documentation, exact selection, narrow attestation and the L1 approximate guard.
  `search/SymmetryDfsExplorer.java` and `report/BenchmarkResult.java` receive documentation-only
  applicability updates; L2 still owns approximate report normalization.
  The additive APIs are a read-only stored-type query and dedicated certified declarative snapshot
  diagnostics; existing generic replay/execution interfaces retain their behavior.
- Adds `src/test/java/dev/samhb/interleave/format/dsl/DeclarativeSymmetryEligibilityTest.java`,
  `src/test/java/dev/samhb/interleave/search/DeclarativeSymmetryOracleTest.java` and
  `src/test/java/dev/samhb/interleave/DeclarativeSymmetryRunTest.java`. Extends relevant existing
  exact composition/backend, DslLoader, CLI, report/attestation and source compatibility tests.
- Adds finite JSON fixtures under `src/test/resources/dsl/`; documents the existing
  `examples/programs/semaphore-declarative.json` without changing its execution to manufacture symmetry.
  Updates README, examples/programs/README.md, docs/visualizer.js and existing visualizer normalization/browser fixtures and
  .github/workflows/build.yml only for complete exact consumer verification. Creates the measured
  ledger `docs/measurements/92-certified-declarative-symmetry.md` with implementation, not this spec PR.
- Off-limits: L2 approximate admission/precision normalization; canonical ordering/key bytes, Bloom
  hashes, source syntax/semantics, generic replay, B8 resource-prefix APIs and mutation policy.

## Non-Goals

No declarative bitstate symmetry release, new JSON syntax/flags, semantic expression normalization,
generic callbacks/backend adapters, source diagnostic identity for JSON, final-property optimization,
larger limits, authenticated uploaded JSON, new dependencies or broad explorer refactor.

## Invariants

- Loader validation and existing source certification remain fail-closed; ordinary public constructors
  and non-symmetry execution retain their behavior.
- Whole local frames/PCs move; shared values, original executed owners and diagnostic names stay real.
- Ordered effects, short-circuit properties, disabled/erroring guards and failed-attempt semantics
  are unchanged. No failed scratch state becomes a committed successor.
- Checked stores bind exact Program/property/strategy identities and own isolated membership.
- Source evidence, ordinary JSON routes and source approximate support remain intact.

## Requirements

1. **R1 — Provenance.** WHEN DslLoader successfully validates a declarative step/property, THE SYSTEM
   SHALL issue its private immutable certification through package-private validated factories.
   WHEN public constructors create otherwise identical objects, THE SYSTEM SHALL retain ordinary
   behavior and reject those uncertified objects in checked symmetry. Factories SHALL require the
   private-loader-held capability described in the set contract; same-package callers SHALL not mint
   certification with an absent/forged permit. No public opt-out SHALL exist.
2. **R2 — Certification.** WHEN checked symmetry binds a model, THE SYSTEM SHALL select one trusted
   instruction family, verify the actual stored initial snapshot is DynamicState before further
   copying/initialization, validate dense owner/declaration/state/property bindings and infer groups
   from complete ordered structural semantics and typed initial locals. Tid-observing owners SHALL
   stay fixed; empty eligible groups, foreign/custom/uncertified/mixed objects SHALL reject. Reassembled
   objects SHALL still satisfy 1–64 steps per owner and total step bounds and the 5,000-node model AST budget, with
   bounded counting that includes repeated instruction occurrences and the property.
3. **R3 — Equivariance.** WHEN corresponding certified owners execute, THE SYSTEM SHALL preserve
   enabledness, successful values/PCs, failed-attempt outcomes, property observations and deadlock/
   termination under the inferred frame permutation. Original configurations SHALL execute, with
   current sequential effects/error/short-circuit semantics and no normalization or added rollback.
4. **R4 — Strategy routing.** WHEN an exact checked declarative store is selected, THE SYSTEM SHALL
   support DFS, static POR, DPOR and CBS with HASHED/ORDERED membership. DPOR SHALL normalize all-asleep
   and query mapped sleep coverage independently of isSource; static POR without a property SHALL use
   whole-remaining dependency closure. Unknown-observation FINAL properties SHALL keep exhaustive
   fallbacks. CBS costs, iterative isolation and existing DPOR alias/context rules SHALL remain intact.
5. **R5 — Release selection.** WHEN library/CLI/harness selects eligible declarative exact symmetry,
   THE SYSTEM SHALL run the selected built-in strategy, or all four for omitted CLI strategy.
   WHEN declarative bitstate symmetry is selected in L1, THE SYSTEM SHALL reject through the checked
   approximate factory before allocation/traversal. Existing source bitstate support SHALL remain.
6. **R6 — Positive witnesses.** WHEN a symmetry-enabled declarative report claims VIOLATION, THE
   SYSTEM SHALL attest actual enabled original-owner transitions and recorded outcomes, accepting a
   final real ASSERTION_FAILED or a false supplied invariant at the observed configuration. It SHALL
   reject malformed/invented witnesses and execution after an earlier failure/property violation.
   Source/cutoff evidence attached to a declarative violation SHALL reject rather than authenticate
   invented diagnostics. Certification/execution errors SHALL produce failed attestation, never a passing fallback.
7. **R7 — Consumers and precision.** WHEN exact results are exported, THE SYSTEM SHALL retain the
   symmetry marker, EXACT identity, representative-count meaning, concrete witness categories and
   bounded/resource incompleteness while leaving JSON models non-source. Failed-attempt diagnostic
   snapshots SHALL repeat the last committed state, with one initial snapshot plus one per attempt;
   tentative failed writes SHALL not be exported as a successor. Certified declarative
   symmetry INCOMPLETE DFS rows SHALL remain inconclusive under harness/attestation expectation
   checks, with shared certification rather than the VIOLATION-only helper authorizing that exception.
   All exact consumer paths
   SHALL work without adding source digests, resource-prefix authentication or new wire fields.
8. **R8 — Lifecycle.** WHEN runs repeat, concur, clear/copy stores or change CBS bounds, THE SYSTEM
   SHALL retain immutable binding and independent mutable storage, reject foreign bindings/custom
   injection, preserve source/ordinary behavior and leave B10 in progress until L2 merges.

## Failure Modes & Edge Cases

1. WHEN a fixed owner contains an unknown step, a loader-issued object belongs to a foreign
   declaration, owner IDs are wrong, or property provenance is missing, THE SYSTEM SHALL reject;
   one valid interchangeable subgroup SHALL not exempt other objects from validation. WHEN two
   owners each reassemble 65 otherwise certified steps, or their aggregate AST exceeds 5,000 nodes,
   THE SYSTEM SHALL reject before traversal without unbounded work or an integer-count overflow.
2. WHEN tid appears in a short-circuited guard, nested RHS or shared/local array index, THE SYSTEM
   SHALL keep that owner fixed. WHEN other certified owners still match, THE SYSTEM SHALL admit only
   those groups; WHEN none match, THE SYSTEM SHALL reject rather than silently disable symmetry.
3. WHEN a later effect errors after earlier effects wrote scratch values, THE SYSTEM SHALL preserve
   DynamicStep's direct semantics and the executor's no-successor failure boundary. It SHALL neither
   reinterpret the attempt as BLOCKED nor commit its partial scratch values.
4. WHEN a report trace has mismatched lengths, invalid owner/PC, disabled choice, outcome mismatch,
   trailing action, or no actual violated condition, THE SYSTEM SHALL fail attestation. Root invariant
   failure SHALL accept an empty witness; empty no-property VIOLATION SHALL reject.
5. WHEN a bound/resource stop occurs, THE SYSTEM SHALL retain INCOMPLETE and existing available
   observations without fabricating a source cutoff/resource prefix or complete verdict.

## Acceptance Criteria

- R1/R2: positive loader cases and matching public-constructor/foreign-declaration/custom/mixed-family
  negatives demonstrate provenance, full validation and fixed-owner handling. Same-package missing
  permit tests and an attempted local-observing property certification reject. Reassembled 65-step
  owners, empty owner lists and aggregate AST boundary cases reject, while supported boundary models pass. Existing loader caps,
  public constructors and unsupported source rejection tests still pass.
- R3/R4: independent finite permutations/transition oracles establish both successful and failed
  observation preservation. Exact DFS semaphore counts are 8 ordinary/5 symmetry; four one-action
  counters are 16/5. HASHED/ORDERED agree on verdicts and membership. Do not impose those counts on POR/DPOR/CBS.
- R4: absent/ALWAYS properties prove real DPOR product routing, all-asleep ordering, stale-action
  mapping and available continuations. FINAL-property cases verify safe fallback separately.
  CBS cases compare bounded references and repeated iterative passes, including root/later stops.
- R5/R7: actual public jar and CLI --file runs cover explicit and omitted strategies, omitted/invalid
  stores, exact backends and L1 bitstate rejection. Fresh JSON/Markdown reaches normalization/browser
  checks with exact/symmetry identity, real owner values and no false SOURCE metadata. Both empty
  and completed-trace TestResult payloads retain exact precision/resource INCOMPLETE in the viewer.
- R6: a real step error with a still-true invariant passes the narrow positive-witness check; forged
  errors, disabled choices, outcome mismatches and trailing actions fail. Root/ALWAYS/FINAL violations
  reproduce, using an independent test checker rather than only the new helper itself.
- R8: source/ordinary compatibility, fresh/copy/concurrent isolation, full build/Javadoc/tests,
  affected/default PIT and consumer checks pass. Publish raw residuals/limits in the L1 ledger;
  no inherited-equivalence or speedup claim follows from a green gate. An exact certified JSON DFS
  INCOMPLETE row expected PASS remains inconclusive; a forged marker on an uncertified model does
  not grant this exception. Ordinary JSON expectation handling is unchanged.

## Refactor Baseline & Rollback

- Pre-Change Baseline: re-fetch merged main before implementation and record its full SHA. At the
  planning SHA, SourceSymmetry rejects non-source inputs, DynamicStep/DslInvariant have public
  constructors without loader certification, and CLI --symmetry requires source. Establish failing
  regressions for JSON admission, checked DPOR routing and positive-error attestation before editing.
- Preserved Invariants: source and ordinary declarative execution/results, existing public signatures,
  canonical encodings, store ownership, exact backends and caps. Record fresh affected/default PIT
  populations on that baseline; previous B9 counts are historical, not the new baseline.
- Call-Site Migration: DslLoader construction, SourceSymmetry/SymmetryRunContext certification,
  checked static POR/DPOR routing, owned runner/harness/CLI selection, exact report/attestation paths,
  API/help prose and consumer fixtures. Source-only evidence/casts retain their original predicate.
- Rollback Criteria: unauthorized admission, incorrect mapped continuation, hidden source relabeling,
  ordinary/source regression, fabricated failure or altered partial-write semantics blocks release.
  Revert L1 coherently and retain source-only symmetry; do not bypass certification to pass tests.

## Design

Use private final certification state in DynamicStep/DslInvariant. Public constructors delegate to
private uncertified construction. Package-private validated factories require the non-null
DslLoader.ValidationPermit capability defined in the set contract before selecting the certified
path. Its sole instance and constructor are private to DslLoader; no accessor exposes the instance.
Only successful loader validation supplies it; same-package callers cannot construct a permit or
certify an arbitrary local-observing property by passing null. Copy existing immutable
AST/effect/predicate inputs as today; retain declaration identity. Keep factories/eligibility details
package-local and expose no Step/property getters solely to let external callers assert trust.

SourceSymmetry dispatches on the complete trusted family. Declarative instruction shape includes
guard and ordered effects; original owner must match its slot. Validate provenance for every owner
before deriving groups, including fixed owners. Recheck owner/step limits and the aggregate AST
budget during certification of a reassembled Program. Count each occurrence and property, abort at
the cap and reuse bounded validated expression metadata/counting; never expand an unbounded alias
list or trust loader-time aggregate counts for a newly assembled Program. Shared exhaustive Expr identity inspection may be
package-private on SourceInstruction for both callers; no separate expression evaluator is created.
Program.initialStateType() returns the actual stored snapshot class; initialStateInputType()
records the original constructor input class before its first deepCopy, without retaining that
input object. Require both types to be DynamicState before initialConfiguration; first-copy or
later-copy adapters cannot establish trust. DslLoader.load also requires the explicit declarative
discriminator for direct callers before it issues certified instructions. DynamicStep already has straight-line next-PC semantics; do not add SourceStep/control metadata.

Use existing CheckedSymmetryStore dispatch for exact DPOR contexts, keeping value-only pruning
bypassed. Extend only the source-or-checked predicate around its mapped sleep normalization/lookup.
Static POR selects the conservative whole-remaining dependency policy for checked JSON without a
property. Keep declarative FINAL's unknown observation interface; no strategy label guarantees pruning.
Keep run identity and alias checks centralized in SymmetryRunContext.

CLI replaces the source-only symmetry precondition with trusted model certification and phase-aware
store validation, producing a clear error for typed/uncertified JSON. It keeps separate source-only
cutoff predicates. L1's single new non-source restriction in BitstateSymmetryStateStore.forRun enforces
the phase boundary for direct callers too; preserve existing argument-validation precedence.

Implement private SoundnessAttestation.authenticatesDeclarativeViolation for marked non-source
symmetry rows only. Re-certify with SourceSymmetry.groups, validate list lengths/indices/enabledness
before execution, and use TransitionExecutor on original configurations. Failure attempts have no
successor; matching ASSERTION_FAILED is valid only at the final action. Check the supplied property
at root and after committed transitions so an earlier false observation cannot be followed by more
choices. With no failed action, accept only an actually false property at the last configuration.
Handle malformed input/execution exceptions as failed checks. Leave generic TraceReplayer and source
attestation unchanged, and qualify Markdown claims to match this new narrow check.

TraceReplayer.captureDeclarativeSnapshots is a dedicated diagnostic API for certified non-source
models. It checks model eligibility, enabled original choices and actual outcomes via TransitionExecutor,
then captures only committed configurations. A final failed attempt repeats the preceding immutable
snapshot to retain choice/state alignment; trailing execution after failure rejects. Runner full and
partial conversion and harness snapshot creation select this method only for declarative symmetry.
Ordinary captureSnapshots/replay and source capture stay unchanged. Snapshot capture does not prove
terminal properties or authenticate uploaded JSON; private attestation retains those separate checks.

Update docs/visualizer.js normalization predicates for exact symmetry TestResult payloads to use
source-or-symmetry without treating JSON as SOURCE. Handle no selected trace and selected completed
trace cases, plus actual INCOMPLETE/resource classification. Keep ordinary unmarked JSON display and
the source-only resourcePrefix provenance branch unchanged.

Separately, broaden the harness/attestation exact DFS expected-verdict exception for INCOMPLETE
certified declarative symmetry rows. Re-certify the registered model/property when validating
external rows; keep ordinary JSON behavior. Do not pass INCOMPLETE to the violation helper or use it
as an exact agreement seed. Preserve existing source exceptions.

## Current State

- [verified] At merged baseline c8ee553, source-only admission, source-gated DPOR coverage and
  source-gated precision prevented the new mode. Red admission/routing/CLI/viewer/attestation
  regressions were observed before the corresponding fixes.
- [verified] DslLoader issues the inaccessible ValidationPermit; public constructors remain
  uncertified. Groups check every owner/declaration/property and reassembled step/AST bounds.
- [verified] All four exact strategies/backends work through existing interfaces. Checked DPOR
  uses normalized mapped products for declarative models; FINAL retains conservative fallback.
- [verified] Private attestation checks enabled actual transitions/terminal observations, rejects
  foreign diagnostics and preserves failed-attempt/no-successor behavior. Ordinary report wording
  remains unchanged; JSON has no fabricated source identity/resource prefix.
- [verified] Full build, strict Javadoc and 1,948 tests pass with no failures/errors/skips. All 218
  browser checks and an external Java 26 built-jar consumer pass. See the
  [L1 ledger](../../measurements/92-certified-declarative-symmetry.md) for scope and provenance.
- [verified] First-copy and later-copy adapters reject before another copy callback. Read-only
  Program type metadata covers both the original constructor input and stored snapshot; generic
  initialization and source identity remain unchanged. Direct DslLoader calls also reject missing,
  empty or non-declarative formats before issuing trust. Both gaps have observed red/green regressions.
- [verified] Default PIT retains all 468 baseline fingerprints/statuses (455 K + 13 S). The broad
  affected census and final focused mutation checks are recorded separately in the ledger. Three
  actual CodeRabbit reviews completed; independently checked dispositions and patched findings are
  recorded there. L1 merged in PR #136; B10 completion awaits L2 merge.

## Tests

- `loaderCertificationCannotBeReplacedByPublicConstruction` (R1/R2) — remove certification/foreign
  declaration/owner checks individually; each must expose a failing negative contract case.
- `reassembledModelsRetainAggregateAdmissionBounds` (R1/R2) — empty owners and 64 versus 65 steps per owner, repeated
  certified occurrences, total AST boundary including property; capability/null/local-property forgery.
- `groupsPreserveOrderedSemanticsAndFixTidOwners` (R2/R3) — guard/effect reordering, nested tid/local
  arrays, noncontiguous groups and unequal typed locals; independent permutation cases.
- `semaphoreAndCounterOrbitsMatchIndependentReference` (R3/R4) — independently derived literal DFS
  counts and transition outcomes; do not generate expectations with the production canonicalizer.
- `checkedDeclarativeDporUsesNormalizedProductCoverage` (R4) — absent/ALWAYS, stale sleepers, equal
  frames and all-asleep order; removal of checked-format routing must fail, not just reduce coverage.
- `finalPropertyFallbackAndBoundedContextsStayConservative` (R4/R8) — FINAL fallback, mapped CBS last
  owner, cost dominance, repeated bounds and root/later interruption.
- `declarativeViolationAttestationRequiresActualTerminalObservation` (R6) — errors after effects,
  root/ALWAYS/FINAL properties, malformed/disabled/trailing/forged traces, true/no invariant negatives.
- `exactDeclarativeConsumersRetainIdentityAndRejectApproximateStage` (R5/R7) — jar/CLI/report/browser;
  wrong store/source labels or skipped admission must fail assertions.
- `incompleteCertifiedDfsDoesNotBecomeAnExpectationMismatch` (R7) — expected PASS with exact
  declarative symmetry INCOMPLETE; ordinary JSON controls and forged-marker rejection.
- `certifiedRunsStayIsolatedAndLegacyModelsStayUnchanged` (R8) — copies/repeats/concurrency, foreign
  bindings, source all-strategy regressions and ordinary declarative compatibility.

These are intended test names/contracts, not tests already added or run. Retain existing distinct
regressions. Kill meaningful validation/routing/outcome mutants; record remaining identities honestly.

## Constraints

- Dependencies: merged B9, approved B10 contract/spec PR and a freshly measured implementation base.
- Backward compatibility: preserve existing source/ordinary public behavior, JSON schemas and native
  Java stream rejection policy. No new source evidence or generic replay guarantee for JSON.
- Oracle Contract: [set verification rules](../../plans/declarative-symmetry-contract.md#independent-oracle-and-verification-contract).
- Resource policy: every local Gradle run uses --max-workers=1 -PpitestThreads=1; CI stays parallel.

## Relevant Skills and Review

- doc-coauthoring — template completeness and independent reader checks of requirements/ownership.
- codebase-design / domain-modeling — small shared certification interface and consistent group,
  action, failed-attempt and representative-evidence terminology; reuse the repo's domain authority
  if present, without creating a competing glossary.
- tdd — failing admission/routing/attestation regressions before implementation.
- owasp-security — untrusted JSON admission and prevention of caller-forged trust, scoped to these
  actual validation paths rather than a general security audit.
- ponytail / ponytail-review — minimal coherent change, connected-call-site review and preserved error
  handling; code-review — actual CodeRabbit after this spec and before substantive publication.

The spec-only PR runs documentation/link/structure checks and per-spec CodeRabbit, not Java/PIT/browser
feature tests. Implementation runs the complete relevant checks and publishes its measured ledger.
Stop after publishing L1 and await Sam's merge update before L2; do not mark B10 complete here.

## Implementation evidence

The [L1 ledger](../../measurements/92-certified-declarative-symmetry.md) records fresh baselines,
red/green regressions, independent finite graphs/permutations, consumers and review outcomes.
Spec-authoring reviews are historical evidence; implementation has its own required review gate.
