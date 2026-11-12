<!-- B10/L2 specification: complete approximate declarative matrix, precision and final release evidence. -->
# Spec 93: Approximate declarative symmetry

## Objective

Complete B10 by enabling certified declarative JSON symmetry with approximate bitstate membership
across DFS, static POR, DPOR and CBS. Reuse B9's complete context encodings and L1's certification,
concrete execution and witness checks; expose approximation honestly through every selected consumer.

**Type: delta. Status: planned; depends on unimplemented L1. No new verification run.**
The [B10 set contract](../../plans/declarative-symmetry-contract.md) owns common semantics/oracles;
[Spec 92](92-certified-declarative-symmetry.md) owns certification and exact integration. Planning
reference is `b1c7586fc34542e919b3f82d1dece87ade7dec86`; the implementation baseline must be the actual
merged L1 revision, not that historical planning SHA. B10 completes only after L2 merges.

## Scope

- Module: checked approximate admission, existing strategy/context integration and precision consumers.
- Modifies `src/main/java/dev/samhb/interleave/search/BitstateSymmetryStateStore.java` to remove L1's
  declarative rejection, preserving all shared key/filter behavior. Updates documentation in
  `src/main/java/dev/samhb/interleave/search/{SymmetryRunContext,SymmetryStateStore}.java` only where
  needed to describe final applicability. Eligibility/algorithm changes are not selected here.
- Modifies `src/main/java/dev/samhb/interleave/{InterleaveRunner,TestResult,cli/Main}.java` and
  `src/main/java/dev/samhb/interleave/report/{BenchmarkHarness,BenchmarkResult,ReportWriter,SoundnessAttestation}.java`
  only for final selection, existing precision fields/verdicts, wording and consumer checks. Preserve
  L1's private witness validator and source/ordinary compatibility; do not create another replay path.
- Adds `src/test/java/dev/samhb/interleave/search/DeclarativeBitstateOracleTest.java` and
  `src/test/java/dev/samhb/interleave/ApproximateDeclarativeSymmetryRunTest.java`. Extends existing
  checked bitstate store/CBS/DPOR, CLI, report/attestation, lifecycle and source compatibility cases.
  Adds declarative finite fixtures under src/test/resources/dsl where distinct contracts require them.
- Updates README, examples/programs/README.md, docs/visualizer.js, docs/visualizer-normalize-check.mjs,
  docs/visualizer-e2e.mjs and .github/workflows/build.yml for real final-matrix producer/consumer checks.
  Updates both work tables and creates docs/measurements/93-approximate-declarative-symmetry.md with
  implementation evidence. No ledger claiming feature verification belongs in the spec-only PR.
- Off-limits: L1 provenance/group semantics, generic replay, source-only resource/cutoff APIs,
  canonicalizer ordering, B9 byte domains/hash/probe algorithms, CBS capacity policy and PIT settings.

## Non-Goals

No calibrated absence probability, exhaustive approximate PASS, proven minimum CBS bound, total JVM
memory guarantee, speedup promise, new exact context table, per-cost filter allocation, public trust
API, extra model family, JSON expression normalization, larger limits, new flags/schema or dependency.

## Invariants

- The same trusted declarative/source certification and Program/property/strategy binding applies
  to exact and approximate checked storage; public-constructor objects remain uncertified.
- Canonicalization affects membership only; concrete original owners/steps execute and supply traces.
- One existing filter serves complete VALUE/CBS/DPOR keys; diagnostics describe actual producing passes.
- Collisions can omit behavior, not create a valid violation/deadlock or justify exhaustive absence.
- Source identity stays absent for JSON; source-only APIs and ordinary non-symmetry results stay intact.
- L1 exact HASHED/ORDERED behavior and FINAL-property conservative fallbacks remain available.

## Requirements

1. **R1 — Admission.** WHEN a certified declarative model selects owned bitstate symmetry with valid
   settings, THE SYSTEM SHALL admit all four built-in strategies through the library, direct checked
   factory and existing CLI/harness. All L1 certification, binding, setter conflict and argument guards
   SHALL remain; only the temporary declarative approximate rejection SHALL be removed.
2. **R2 — Full products.** WHEN approximate membership is queried, THE SYSTEM SHALL use B9's existing
   complete VALUE orbit, CBS mapped-last/cost candidate, or DPOR normalized mapped sleep product as
   appropriate. DPOR SHALL use its checked product path rather than value-only hits/raw antichains.
   First-occurrence/stale-action mapping and the one whole-frame witness SHALL stay unchanged.
3. **R3 — Budget and metrics.** WHEN domains/costs accumulate marks, THE SYSTEM SHALL share one
   configured Bloom budget, preserve insertion-aware statistics and existing queried-cost diagnostics,
   and retain no new exact visited-context history. Clear/copy/run/bound changes SHALL isolate filters
   and statistics; invalid contexts SHALL not partially mutate them.
4. **R4 — Bounded execution.** WHEN approximate CBS runs once or deepens iteratively, THE SYSTEM
   SHALL preserve B9's approximate termination/capacity rules, observed producing bound and resource
   precedence. Hidden cutoff observations SHALL not become a claim of exhaustion, a proven minimum
   bound or aggregate metrics from discarded passes. Existing bound limits SHALL not change.
5. **R5 — Precision and identity.** WHEN a bitstate symmetry report would otherwise say PASS, THE
   SYSTEM SHALL emit the existing APPROXIMATE_PASS verdict for declarative as well as source rows.
   It SHALL retain BITSTATE, symmetryReduction, actual metrics, failure/deadlock precedence and
   INCOMPLETE. JSON models SHALL remain non-source; no source digest/location/resource prefix SHALL
   be fabricated to obtain precision handling.
6. **R6 — Positive evidence.** WHEN approximate search reports a declarative violation, THE SYSTEM
   SHALL use L1's strict enabled-choice/outcome/terminal-observation helper. Mixed reports SHALL
   exclude approximate rows from exact agreement and retain the positive-witness obligation.
   Forged traces SHALL fail even when other rows agree or approximation is enabled.
7. **R7 — Complete consumers.** WHEN eligible --file input selects symmetry and explicit BITSTATE,
   THE SYSTEM SHALL run an explicit strategy or all four when omitted, preserve existing CBS option
   checks and deliver correctly labeled JSON/Markdown/viewer output. Invalid/omitted stores, typed/
   custom/uncertified models and conflicting library configuration SHALL reject without fallback.
8. **R8 — Completion.** WHEN L2 verification and review finish, THE SYSTEM SHALL publish one complete
   feature PR and stop for Sam's update. WHEN L2 merges, THE SYSTEM SHALL mark B10 complete in the
   trackers/context, explicitly report completion and stop before #11/#12 or deferred/conditional work.

## Failure Modes & Edge Cases

1. WHEN tiny filters force collisions or VALUE/domain marks interfere, THE SYSTEM SHALL allow
   additional false-positive pruning without inventing positive observations or exact-coverage claims.
   A positive complete-product lookup need not imply that product was previously inserted.
2. WHEN DPOR sleepers contain foreign actions/owners or contexts are invalid, THE SYSTEM SHALL retain
   full validation before any insertion/statistic mutation. Equal frames/stale tokens SHALL not be
   reduced to source labels, current PC alone or raw Step hash/identity strings.
3. WHEN a lower CBS cost covers a higher request, THE SYSTEM SHALL use the existing whole-candidate
   probe rule and capacity validation; it SHALL not combine partial probe fragments across costs.
4. WHEN root/later state/time limits or bounded cutoffs interrupt a run, THE SYSTEM SHALL preserve
   actual INCOMPLETE/resource classification and available concrete observations. It SHALL not
   manufacture a B8 resource prefix for JSON or promote a collision-hidden cutoff into PASS proof.
5. WHEN approximation is enabled for a malformed violation trace, THE SYSTEM SHALL fail positive
   attestation rather than skip it. An actual final step error may validate even when a supplied
   invariant remains true; a no-error/no-property forged violation SHALL not validate.

## Acceptance Criteria

- R1/R7: public jar, direct factory, runner, harness and CLI exercise all four strategies; default
  library strategy stays unchanged while omitted CLI strategy produces four rows. Existing conflict,
  invalid budget/probe/non-CBS capacity, source/typed/custom-model and CBS-bound rejections pass.
- R2/R3: independent finite complete-key/context oracle and byte/domain assertions establish correct
  mapping for declarative states. Exact-context shadows certify fixture no-false-positive premises
  before equality checks; forced-collision fixtures separately demonstrate honest lossy behavior.
  Actual DPOR tests use absent/ALWAYS properties, not only FINAL fallback.
- R3/R4: VALUE interference, CBS q==p/q<p/q>p, DPOR empty/stale/equal-frame sets, invalid no-write
  contexts, repeated clear/freshCopy and concurrent/iterative runs preserve one filter and owned metrics.
- R5/R6: reports keep approximate verdict/store/symmetry identity with no SOURCE metadata. Exact
  agreement ignores approximate absence; actual positive traces pass, forged traces fail, and error
  outcomes never become fabricated detailed source categories.
- R7: fresh CLI artifacts pass normalization and browser checks with actual original-owner values;
  source-only cutoff simplification/resource prefix APIs remain rejected/unavailable for JSON.
  Both empty/no-trace and completed-trace TestResult JSON render approximate absence rather than PASS.
- R8: integrated build/strict Javadoc/tests, public jar, affected/default PIT and consumers pass with
  honest lineage/residual accounting. B10 remains in progress until the resulting PR merges.

## Refactor Baseline & Rollback

- Pre-Change Baseline: record the actual merged Spec 92 full SHA, commands, all-strategy exact results,
  explicit approximate-declarative rejection, fresh affected/default PIT and source/ordinary control
  artifacts. Do not reuse B9 or planning counts as this baseline.
- Preserved Invariants: L1 certification/execution, ordinary bitstate hashes/metrics, B9 product bytes,
  CBS rules and source exact/approximate evidence. Preserve zero-to-one probe fallback/overflow rules,
  insertion counts and native stream policy; no floor/operator/target-test weakening.
- Call-Site Migration: remove the one factory admission guard; finish CLI/help/applicability prose,
  BenchmarkResult precision normalization, existing TestResult identity, mixed report/attestation and
  fresh producer/browser fixtures. Do not copy eligibility or positive-replay logic into L2 callers.
- Rollback Criteria: fabricated/exact-mislabeled absence, invalid positive witnesses, wrong product
  equality before hashing, hidden exact context history, budget growth per cost or source/ordinary
  regressions blocks publication. Revert L2 coherently and restore the single approximate guard;
  keep L1 exact declarative support operational.

## Design

Remove L1's explicit non-source admission restriction in BitstateSymmetryStateStore.forRun; its
SymmetryRunContext already shares the certified family dispatcher. Do not add duplicate loader flags,
factory adapters, raw hash helpers or alternate context serializers.

Keep B9's version-1 encodings and metric formulas unchanged. VALUE domain is 1; CBS domain 2 uses
complete mapped-last orbit plus whole candidate cost; DPOR domain 3 uses complete orbit plus sorted
mapped-owner/first-occurrence-PC tokens. Reuse the accepted-visit VALUE mark in the same filter and
existing insertion counts. No exact table of seen declarative products is retained in production.

BenchmarkResult's existing PASS/BITSTATE normalization becomes conditional on sourceModel OR
symmetryReduction. Ordinary non-source, non-symmetry legacy rows retain their current behavior.
TestResult keeps L1's known storeType emission for symmetry without setting programKind SOURCE.
Reports, CLI and viewer reuse the existing verdict vocabulary and wire format; no new DECLARATIVE
programKind is selected. In docs/visualizer.js, source-or-symmetry TestResult normalization covers
BITSTATE absence for both no-trace and selected completed-trace payloads; retain L1's INCOMPLETE
handling and source-only resourcePrefix checks. Keep current semantic distinction between INCOMPLETE
and approximate absence.

Exact and approximate runs use the same L1 witness helper. Attestation does not infer exact absence
or model identity from a symmetry marker alone; it re-certifies the registered Program/property.
Actual discovered declarative step failures retain legacy ASSERTION_FAILED classification, not
invented source ASSERTION/RUNTIME_ERROR evidence. No stronger generic replay contract is introduced.

Do not confuse a store's finite no-false-positive test with a universal guarantee: establish the
complete relevant context universe independently for each equality fixture, including VALUE marks
that can interfere with products. Use a fresh checked store for actual exploration, retaining sealed
dispatch. Forced-collision fixtures assert real positive witnesses and truthful metadata, not equal
counts or guaranteed discovery of every exact failure.

## Current State

- [verified] B9 supports all four source strategies with one checked approximate filter, shared
  context encoding, owned CBS passes and explicit approximate consumers.
- [verified] At the planning base, BenchmarkResult normalizes BITSTATE PASS only for sourceModel;
  TestResult emits storeType only inside its source branch. Merely admitting JSON would mislabel
  precision unless the selected consumer predicates are broadened without changing source identity.
- [verified] Existing generic non-source replay checks recorded execution outcomes but not full
  enabledness/terminal claims. L1 therefore owns the stricter private symmetry attestation path.
- [unverified] L1 has not been implemented/merged. Its real base SHA, exact evidence, approximate
  guard and reusable helper must be verified before beginning L2.
- [unverified] Declarative approximate context coverage, artifact consumers and mutation populations
  require new implementation measurements; no source fixture score proves them in advance.

## Tests

- `allDeclarativeStrategiesUseOwnedApproximateMembership` (R1/R7) — direct/library/harness/CLI matrix,
  omitted strategy versus builder default, positive settings and forbidden/conflicting selections.
- `declarativeProductsMatchIndependentFiniteContextOracle` (R2/R3) — byte layout, full typed frames,
  mapped last owner/costs and normalized mapped sleepers; never derive keys with production helpers.
- `valueInterferenceAndForcedCollisionsRemainApproximate` (R2/R3/R5) — include cross-domain marks,
  insertion counts and collision cases without asserting impossible exact-absence guarantees.
- `costCandidateAndSleepProductValidationAreAtomic` (R2–R4) — whole-cost probes, dominance direction,
  stale actions/equal frames and foreign-context no-write checks; kill dropped suffix/domain/owner
  and reversed-cost mutants if introduced by changed code.
- `iterativeDeclarativeRunsRetainBoundAndMetricOwnership` (R3/R4) — current pass, actual observed
  bound, capacity limits, hidden cutoff behavior, root/later stops and simultaneous runners.
- `approximateDeclarativeReportsDoNotClaimSourceOrExactPass` (R5/R7) — JSON/Markdown/normalization/
  browser fixtures, true BITSTATE/symmetry identity and unchanged ordinary/source controls.
- `approximatePositiveAttestationCannotBeSkipped` (R6) — actual errors with true invariants, root/
  always/final property violations, tampered/disabled/trailing traces and mixed exact/approximate rows.

These are required future test contracts, not completed runs. Preserve L1's distinct regression cases.
No production mutant is claimed killed without measurement and matching identity/lineage evidence.

## Constraints

- Dependencies: merged Spec 92 implementation, fresh merged baseline and shared B10 oracle contract.
- Backward compatibility: exact/source/ordinary behavior, JSON schema/vocabulary, native stream policy,
  source-only APIs, caps and CBS option policy stay intact. New support is explicitly opt-in.
- Oracle Contract: [independent verification](../../plans/declarative-symmetry-contract.md#independent-oracle-and-verification-contract)
  and B9's [key/filter contract](../../plans/approximate-symmetry-contract.md#shared-interface-and-key-contract).
- Every local Gradle command uses --max-workers=1 -PpitestThreads=1; CI parallelism stays unchanged.

## Relevant Skills and Review

- doc-coauthoring — phase dependencies, acceptance mappings and fresh-reader consistency checks.
- codebase-design / domain-modeling — reuse shared certification and product identity behind existing
  interfaces; retain precise approximate absence versus actual positive-observation terminology.
- tdd — failing admission/precision regressions followed by independent finite/collision assertions.
- owasp-security — ensure untrusted JSON, forged certificates and report traces cannot bypass L1 checks.
- ponytail / ponytail-review — smallest coherent admission/consumer change with connected behavior
  review; code-review — actual CodeRabbit after this spec and before substantive implementation push.

The spec-only PR runs Markdown/link/structure checks and per-spec CodeRabbit, not feature tests.
Implementation ships its evidence ledger and all consumers together. Stop after publishing; Sam
supplies review/CI/merge updates. On merge, close B10 and stop before unselected work.
