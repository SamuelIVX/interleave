<!-- Implemented B9/K1: shared approximate membership and complete DFS/static-POR delivery. -->
# Spec 89: Approximate symmetry for DFS and static POR

## Objective

Let eligible source models use checked thread symmetry together with approximate bitstate membership
through the Java library and existing source CLI/reporting. Deliver DFS/static POR completely while
establishing the reusable interfaces required by later CBS and DPOR phases.

**Type: delta, with a behavior-preserving filter extraction. Status: planned, not implemented.**
The [B9 set contract](../../plans/approximate-symmetry-contract.md) owns shared terminology,
interfaces, byte encoding, oracle rules, metrics and the three-PR release matrix.

## Scope

- Package / Module: interleave checked search, state membership, runner and source consumers.
- Creates `src/main/java/dev/samhb/interleave/state/BitstateFilter.java` and
  `src/main/java/dev/samhb/interleave/search/{CheckedSymmetryStore,ApproximateStateStore,SymmetryRunContext,BitstateSymmetryStateStore}.java`.
- Modifies `src/main/java/dev/samhb/interleave/state/BitstateStore.java`,
  `src/main/java/dev/samhb/interleave/search/SymmetryStateStore.java`,
  `src/main/java/dev/samhb/interleave/search/DfsExplorer.java`,
  `src/main/java/dev/samhb/interleave/por/StaticPorExplorer.java`,
  `src/main/java/dev/samhb/interleave/InterleaveRunner.java`,
  `src/main/java/dev/samhb/interleave/cli/Main.java`, and
  `src/main/java/dev/samhb/interleave/report/{BenchmarkHarness,BenchmarkResult,ReportWriter,SoundnessAttestation}.java`
  where required for selection, precision and explanation. Migrate capability checks mechanically in
  `cb/ContextBoundedExplorer.java` and `dpor/DporExplorer.java` without enabling their new mode.
- Adds `src/test/java/dev/samhb/interleave/search/BitstateSymmetryStoreTest.java` and
  `src/test/java/dev/samhb/interleave/ApproximateSymmetryRunTest.java`; extends existing
  `state/BitstateStoreDiagnosticsTest.java`, `search/SymmetryBackendTest.java`,
  `search/SymmetryCompositionTest.java`, `cli/MainTest.java` and
  `src/test/java/dev/samhb/interleave/report/{BenchmarkHarnessTest,BenchmarkResultTest,ReportJsonContractTest,SoundnessVerdictTest,SoundnessReplayTest}.java`
  where existing contracts require integration cases.
  Updates README, `docs/visualizer-normalize-check.mjs`, `docs/visualizer-e2e.mjs`, and
  `.github/workflows/build.yml` for generated consumer fixtures. Viewer production edits belong
  only where required to display existing approximate metadata truthfully.
- Off-limits: Spec 90's cost lookup/iterative policy and Spec 91's approximate sleep coverage;
  compiler/eligibility/canonical ordering changes owned by Specs 76/77/81; exact convenience
  `SymmetryDfsExplorer` semantics; B8 witness classes and any new CLI/wire schema.

## Non-Goals

No approximate symmetry CBS/DPOR execution, generic custom backend, memory quota for the entire
checker, new language eligibility, new hash algorithm, benchmark speedup promise or dependency.
Existing exact strategies remain available, including exact CBS and DPOR.

## Invariants

- Certification binds exact program, property and strategy objects before executing visitors/actions.
- Concrete original-owner configurations execute; canonical keys are used only for membership.
- Ordinary bitstate probes, cost vectors, diagnostics, defaults and direct API behavior stay unchanged.
- Exact HASHED/ORDERED membership answers and alias certification stay unchanged.
- Every new result is identified as approximate symmetry, including empty/resource-limited runs.
- Witnesses preserve actual source identity/outcomes; approximation can omit, never fabricate them.

## Requirements

1. **R1 — Selection.** WHEN a valid configured runner begins DFS/static POR with positive
   `symmetryBitstate(bits,k)` and its final symmetry setting enabled, THE SYSTEM SHALL create a fresh
   owned checked approximate store. WHEN an exact backend/store/factory conflicts or the final
   symmetry setting is disabled, THE SYSTEM SHALL reject at build in either setter order without
   ignoring settings or calling a rejected factory. Invalid bits/k SHALL reject at the setter;
   repeated valid settings, including false then true symmetry, SHALL use the last value.
2. **R2 — Shared filter.** WHEN ordinary BitstateStore delegates probes to BitstateFilter, THE SYSTEM
   SHALL preserve all existing hashes, integer overflow/floorMod behavior, vector allocation, marks
   and diagnostics. Extraction SHALL be verified before adding the new membership behavior.
3. **R3 — Checked identity.** WHEN the new store is constructed or used for another run, THE SYSTEM
   SHALL apply the same certification, immutable binding and mismatched-run rejection as exact
   symmetry. Shared mapping SHALL retain existing DPOR alias certification during mechanical migration.
4. **R4 — Value membership.** WHEN DFS/static POR queries/marks a configuration, THE SYSTEM SHALL
   encode the complete VALUE orbit as specified by the set contract before lossy hashing. Marked
   keys SHALL remain positive until clear; collisions MAY produce additional positives.
5. **R5 — Isolation.** WHEN a run starts, repeats, clears or copies a store, THE SYSTEM SHALL own
   independent membership/statistics while retaining only the immutable validated configuration.
6. **R6 — Precision.** WHEN any runner/harness path receives the new store, THE SYSTEM SHALL use
   ApproximateStateStore for BITSTATE identity, metrics and capacity. An ordinary factory returning
   either sealed checked-store implementation SHALL reject instead of producing unmarked results.
7. **R7 — Consumers.** WHEN CLI selects eligible source, symmetry, explicit BITSTATE and an explicit
   DFS/static-POR strategy, THE SYSTEM SHALL run that selection and produce existing approximate
   JSON/report identity. A strategy-omitted/all-strategy request or CBS/DPOR new-mode request SHALL reject.
8. **R8 — Observations.** WHEN execution reports failure/deadlock/cutoff/resource evidence, THE SYSTEM
   SHALL preserve current classification, original choices and authenticated replay. WHEN traversal
   returns without a conclusive witness, modeled cutoff or resource stop, THE SYSTEM SHALL use
   APPROXIMATE_PASS rather than exact PASS. Cutoff/resource precedence SHALL remain intact, and
   approximate rows SHALL not enter exact agreement checks.
9. **R9 — Metrics.** WHEN the store reports statistics, THE SYSTEM SHALL expose actual cardinality,
   density and the shared single-lookup heuristic, with insertion counts distinct from unique states
   and the membership budget distinct from total heap use.

## Failure Modes & Edge Cases

1. WHEN bits/k are zero/negative, a required object is null, or non-CBS capacity is not zero,
   THE SYSTEM SHALL reject before traversal; null property remains a supported step-only selection.
2. WHEN a model/property is ineligible, a bound identity differs, or a stage requests unsupported
   approximate CBS/DPOR, THE SYSTEM SHALL reject without fallback to exact or ordinary search.
3. WHEN the filter is empty, tiny, saturated, or repeatedly marked with the same key, THE SYSTEM
   SHALL retain the one-sided membership contract and approximate label; saturation is not a proof.
4. WHEN clear/freshCopy/repeated or concurrent runner calls occur, THE SYSTEM SHALL prevent bits,
   counters, callbacks or partial results crossing run ownership.
5. WHEN existing limits stop at root or a later branch, THE SYSTEM SHALL retain actual B8 prefixes
   and BITSTATE/symmetry markers without any additional model/property execution for reporting.

## Acceptance Criteria

- R1/R3/R5: conflicting/invalid selections and binding mismatches fail before callback execution;
  independent clears/copies/runners preserve configuration and do not share membership.
- R2: existing ordinary filter oracles and per-cost diagnostics remain unchanged; characterize moved
  mutation operations separately and preserve exact backend behavior through all four strategies.
- R4: independent complete-frame oracles, signed hashes, arrays/locals, equal/different orbits and
  forced collision fixtures verify the intended membership; fixture equality is conditioned on an
  observed zero-false-positive shadow, not just on a large configured filter.
- R6/R7/R8: real Java/CLI/report JSON and browser fixtures retain BITSTATE plus symmetry, do not
  show exact absence, replay every reported violation and preserve modeled/resource incompleteness.
- R9: independent numeric literals and stable capacity demonstrate bounded membership only.
- Full build/tests/strict Javadoc, affected/default PIT and actual completed CodeRabbit review meet
  the set verification contract before publishing K1. No measured result is assumed by this proposal.

## Refactor Baseline & Rollback

- Pre-Change Baseline: inspected merged main `1db7870`; re-fetch and measure the actual main at
  implementation start. Record existing ordinary filter probes/diagnostics and exact symmetry behavior.
- Preserved Invariants: all above; current public exact factories, backend selection and exact DFS
  convenience signatures retain their behavior. New capability interfaces do not admit external
  checked implementations.
- Call-Site Migration: every concrete BitstateStore/SymmetryStateStore type test in runner,
  DfsExplorer, StaticPorExplorer, DporExplorer, ContextBoundedExplorer and BenchmarkHarness;
  report identity/attestation and CLI selection checks; corresponding tests/fixtures/help text.
- Rollback Criteria: ordinary filter drift, weakened certification, wrong precision, fabricated
  witness, or cross-run leakage blocks publication. Revert the complete K1 feature commit; leave
  no helper-only release or partially enabled CLI mode. Do not revert unrelated work or user edits.

## Design

Use the signatures and byte layout fixed in the set contract. Keep public exact interfaces intact;
the sealed checked capability shares the two existing checking operations. BitstateFilter owns
bit/probe operations, not model canonicalization or strategy decisions. SymmetryRunContext owns
certification and reversible owner/action mapping, with the existing tie-breaking rule.

K1's new store admits only DFS/static POR with capacity zero. CBS-specific inherited membership
operations and sleepCovered remain unsupported until their owning phase. `maxPreemptions()` returns
the configured zero here; it does not make a mismatched CBS run eligible. Migrate capacity/type
recognition without enabling later algorithms. No source input or callback gets a backend injection seam.

Approximation applies after complete typed-key construction. Use a single shared VALUE filter;
membership retains no exact orbit-key collection. Program certification metadata is bounded by the
existing source shape and is outside the bit budget. Diagnostic visitedStates maps remain unchanged.

## Current State

- [verified] At `1db7870`, symmetry uses exact SymmetryStateStore and HASHED/ORDERED collections;
  builder rejects explicit stores/factories and CLI/harness require an explicit exact selection.
- [verified] Precision/metric/capacity decisions currently contain concrete BitstateStore tests;
  the new class would be mislabeled or unreported without migration.
- [verified] Existing source/replay/viewer contracts distinguish approximate absence, real witnesses
  and original resource interruption; they are reused rather than replaced.
- [verified] K1 implements the shared filter and run context, sealed checked capability, approximate
  metric capability, VALUE membership, builder selection, CLI/harness integration and viewer fixtures.
  CBS and DPOR approximate selection still reject. Exact convenience APIs remain exact.
- Verification and mutation evidence: [K1 ledger](../../measurements/89-approximate-symmetry-dfs-por.md).
  No speed or total-memory improvement is claimed.

## Tests

Implemented tests cross the agreed store, runner, actual CLI/report and browser interfaces:

- `ordinaryFilterExtractionPreservesProbeAndDiagnosticLiterals` (R2) — negative/zero hashes,
  repeated indices, int overflow, rotation/fallback, duplicate marks, lazy vectors and clear.
- `approximateSymmetryRejectsConflictsBeforeCallbacks` (R1/R3/R7) — both setter orders, nulls,
  invalid bits/k/capacity, foreign bindings, unavailable strategies and factory invocation counts.
- `valueOrbitMembershipMatchesIndependentCollisionFreeOracle` (R4) — complete shared/local
  values, PC distinctions, permutations and a test-only exact shadow at the public membership seam.
- `tinyFilterMayOmitButCannotInventWitnesses` (R4/R8) — force saturation; replay any real failure;
  do not require exact verdict/count agreement when false positives occur.
- `clearCopyAndConcurrentRunnersOwnIndependentFilters` (R5) — getters, counters and root/later stops.
- `approximateSymmetryDfsPorArtifactsReachBrowser` (R6–R9) — generated payloads and exact field-cell
  checks for identity, verdict, metrics, source witness and actual resource-prefix observations.
- High-risk mutants: `<=`/`<` probe/range changes, missing domain/key component, skipped marking,
  bad secondary-zero fallback, stale clear/copy state, inverted precision and removed conflict checks.

## Constraints

- Dependencies: merged Specs 76/77/81/84 and B8 Specs 87/88; no dependence on later B9 phases.
- Backward compatibility: preserve ordinary bitstate and exact search behavior, source eligibility,
  existing CLI option/default values and current wire fields. No total-memory or speed guarantee.
- Oracle Contract: [B9 set oracle and verification contract](../../plans/approximate-symmetry-contract.md#oracle-and-verification-contract).

## Relevant Skills and Review

| Skill | Required use within this phase |
|---|---|
| `ponytail` full / `codebase-design` | Keep the checked interface small and shared ownership local |
| `refactor` | Only the behavior-preserving filter/mapping extraction; preserve existing oracles |
| `tdd` | One behavioral slice at the agreed store/runner/consumer interfaces at a time |
| `owasp-security` | Input/capability validation and safe JSON-to-DOM presentation |
| `ponytail-review` | Connected-code review of selection, precision, isolation and compatibility |
| `code-review` | Invoke CodeRabbit after completing this spec and after its future implementation, before pushing |

Use `doc-coauthoring` for spec refinement/reader checks. Do not reopen the approved design or add
approval pauses through a skill. Verify actual review completion, fix valid findings and record the
remaining limits; ordinary review never substitutes for required CodeRabbit evidence.
