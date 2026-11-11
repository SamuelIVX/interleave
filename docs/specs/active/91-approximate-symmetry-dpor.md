<!-- Planned B9/K3: complete mapped DPOR product membership and final all-strategy release. -->
# Spec 91: Approximate symmetry for DPOR

## Objective

Complete B9 by approximating the whole checked DPOR state-plus-sleep-context identity and exposing
all four selected strategies through the library and existing CLI/reports/viewer. Preserve concrete
execution and real witnesses while keeping membership storage independent of visited-context count.

**Type: delta. Status: planned, not implemented.**
[Spec 90](90-approximate-symmetry-cbs.md) must merge after
[Spec 89](89-approximate-symmetry-dfs-por.md). The
[B9 set contract](../../plans/approximate-symmetry-contract.md) owns shared interfaces and oracles.

## Scope

- Package / Module: interleave checked DPOR product membership and final B9 source consumers.
- Modifies `src/main/java/dev/samhb/interleave/search/{SymmetryRunContext,BitstateSymmetryStateStore}.java`,
  `src/main/java/dev/samhb/interleave/dpor/DporExplorer.java`,
  `src/main/java/dev/samhb/interleave/InterleaveRunner.java`,
  `src/main/java/dev/samhb/interleave/cli/Main.java`, and
  `src/main/java/dev/samhb/interleave/report/{BenchmarkHarness,BenchmarkResult,ReportWriter,SoundnessAttestation}.java`
  only where needed to admit DPOR, preserve classification and explain its complete-context metrics.
- Adds `src/test/java/dev/samhb/interleave/search/BitstateSymmetryDporTest.java`; extends
  `src/test/java/dev/samhb/interleave/ApproximateSymmetryRunTest.java`,
  `src/test/java/dev/samhb/interleave/search/SymmetryCompositionTest.java`,
  `src/test/java/dev/samhb/interleave/dpor/{DporSleepContractTest,DporEvidenceContractTest,DporPropertyContractTest}.java`,
  `src/test/java/dev/samhb/interleave/cli/MainTest.java`,
  `src/test/java/dev/samhb/interleave/report/{BenchmarkHarnessTest,ReportJsonContractTest,SoundnessReplayTest,SoundnessVerdictTest}.java`,
  and `src/test/java/dev/samhb/interleave/{ResourcePrefixCaptureTest,ResourcePrefixJsonTest}.java`
  where their existing contracts require integration cases. Updates README,
  `docs/visualizer-normalize-check.mjs`, `docs/visualizer-e2e.mjs` and
  `.github/workflows/build.yml` for fresh-artifact generation and consumer checks.
- Off-limits: certification expansion owned by B10; the exact raw-key sleep antichain and exact
  mapped-context rules of Specs 77/81; CBS cost/iteration behavior owned by Spec 90; source compiler,
  B8 original-run evidence and the existing ordinary BitstateStore storage format.

## Non-Goals

No hybrid exact sleep table, sleep-subset optimization across permutations, canonicalized execution,
new DPOR algorithm/property fallback, faster canonicalization promise, whole-process memory quota,
new approximation seed or CLI/wire field. Do not broaden source eligibility to fit the new storage.

## Invariants

- The current all-asleep normalization happens before coverage lookup.
- The same complete-frame permutation moves every sleep token's owner; instruction identity is
  represented by its first lexical occurrence, including stale sleeping actions and aliases.
- Matching alias partitions and trusted source/property certification are unchanged.
- Without false-positive decisions, equality of full mapped product contexts is the same coverage
  relation used by existing exact symmetry DPOR. No subset transport is inferred.
- Approximate symmetry DPOR never gates on value-only membership or ordinary raw-key antichains.
- All executed configurations/choices and emitted evidence remain concrete and original-ID based.
- Membership uses one filter; source metadata and existing diagnostic visited configurations are
  outside that capacity. No exact visited-context history is introduced.

## Requirements

1. **R1 — Admission.** WHEN the new store binds DPOR, THE SYSTEM SHALL reuse shared trusted-source,
   property and alias-partition certification, keep non-CBS capacity zero and reject mismatched
   program/property/strategy bindings before traversal or callbacks.
2. **R2 — Context mapping.** WHEN DPOR normalizes sleeping actions, THE SYSTEM SHALL canonicalize
   the complete frame/orbit once, move token owners using that exact witness, resolve each action's
   first occurrence in its original owner, retain stale entries, deduplicate tokens and sort them
   lexicographically before the DPOR-domain encoding specified by the shared contract.
3. **R3 — Coverage.** WHEN sleepCovered receives a valid complete product, THE SYSTEM SHALL test
   that product's probes. A positive returns covered without a new insertion; a missing product is
   inserted once and returns uncovered. Positive results MAY be false. No plain-value hit or raw
   antichain SHALL substitute for the complete product lookup.
4. **R4 — Retained storage.** WHEN product visits accumulate, THE SYSTEM SHALL retain one configured
   filter without an exact orbit-to-sleep map. Existing markVisited may record a separate VALUE key
   in that same filter after admission; those extra insertions count in its statistics, not as a
   deliberate product-coverage decision. Cross-domain bit interference remains approximation.
5. **R5 — Semantics.** WHEN DPOR executes/records a branch, THE SYSTEM SHALL preserve current
   property visibility, enabledness/independence, guarded atomic behavior, source failures/cutoffs,
   alias handling and callback order. Membership/canonicalization SHALL not execute a model action
   or property to predict coverage, and SHALL not turn resource provenance into a modeled cutoff.
6. **R6 — Complete release.** WHEN CLI/harness selects eligible source, symmetry and an explicit
   BITSTATE store without a strategy filter, THE SYSTEM SHALL support all four strategies. Explicit
   DPOR SHALL work through runner/direct explorer/harness routes; omitted/invalid store selections,
   generic injection and out-of-policy CBS bounds SHALL retain explicit rejection.
7. **R7 — Results and metrics.** WHEN DPOR/all-strategy results are exported, THE SYSTEM SHALL retain
   BITSTATE/symmetry identity, insertion-aware filter diagnostics and existing approximate verdicts,
   concrete witness precedence and B8 capture/replay separation. Approximate rows SHALL not be used
   as exact agreement or coverage evidence; reported violation traces SHALL still authenticate.
8. **R8 — Isolation and closure.** WHEN the store clears/copies or runner calls repeat/concur, THE
   SYSTEM SHALL own fresh independent filters and retain immutable binding/configuration only.
   WHEN K3 merges, THE SYSTEM SHALL mark B9 complete without starting B10 or parked roadmap work.

## Failure Modes & Edge Cases

1. WHEN a sleeping owner/action is foreign, an index is invalid or alias layouts differ, THE SYSTEM
   SHALL reject before filter/statistic mutation. Validate the entire incoming context before marking.
2. WHEN frames tie, THE SYSTEM SHALL preserve existing deterministic tie-breaking. Losing a possible
   reduction is allowed; inventing a stronger canonical sleep equivalence is not.
3. WHEN sleep sets are empty, all enabled actions sleep, tokens are stale, or the same action object
   appears at multiple PCs, THE SYSTEM SHALL preserve exact-mode normalization/first-occurrence rules.
4. WHEN different products hash to the same bits or the filter saturates, THE SYSTEM SHALL permit
   omitted behavior while retaining approximate status. Finding no bug SHALL not become exact PASS;
   actual cutoffs/resource stops SHALL retain precedence over approximate absence.
5. WHEN root/later-branch resource stops occur alongside earlier evidence, THE SYSTEM SHALL retain
   original active-path capture and prior witness priority, with no implicit replay during export.
6. WHEN shared source models and immutable runner configurations are reused concurrently, THE
   SYSTEM SHALL isolate mutable filter/context data; a caller must not mutate configurations during lookup.

## Acceptance Criteria

- R1/R2: independent owner-permutation/alias oracles distinguish actual full products, including
  stale and empty sleepers. Products whose mapped encodings agree under the existing deterministic
  witness match without relabeling execution. Equal-frame physical-ID ties may leave equivalent
  sleep products unmatched; the oracle SHALL not minimize sleepers over additional stabilizer permutations.
- R3/R4: direct store traces prove logical product equality in a fixture with observed no-false-positive
  decisions; value-only coverage cannot suppress a missing product. Forced collisions may suppress it
  only through the complete-product Bloom lookup. Logical capacity stays `bits` as contexts accumulate.
- R5: real source searches and replay cover visible/invisible actions, guards, disabled actions,
  terminal attempts and modeled/runtime/property errors without additional speculative execution.
- R6/R7: actual Java/CLI all-strategy artifacts retain existing store/verdict/witness semantics and
  reach normalization/browser checks. No consumer labels approximate absence as exhaustive PASS.
- R8: clear/copy/repeated/concurrent and partial-result cases preserve ownership and metadata.
- All template/set verification gates pass; report raw residuals and assurance limits. B9 remains
  planned/in progress until all three implementation phases have merged, not on specification approval.

## Refactor Baseline & Rollback

- Pre-Change Baseline: fresh main after merged Spec 90. Record exact DPOR alias, mapped-context,
  all-asleep, visibility, replay and ordinary approximate behavior before changing dispatch.
- Preserved Invariants: current exact/ordinary DPOR paths, earlier source contracts, K1/K2 selection,
  all concrete execution/evidence and the distinction between coverage and approximate absence.
- Call-Site Migration: checked DPOR admission/coverage branch, final runner/harness/CLI support
  matrix and consumer statements/fixtures. Do not use raw-key antichains for the new mode.
- Rollback Criteria: wrong product equality without collisions, missed required context before
  hashing, hidden exact-cache growth, fabricated/mislabeled evidence or legacy DPOR regression blocks
  publication. Revert K3 coherently; keep K1/K2 support and explicit DPOR rejection until repaired.

## Design

Keep current DPOR exploration/normalization. The shared checked capability routes new approximate
symmetry to sleepCovered; ordinary exact/bitstate DPOR retain their existing raw-key path. Exact
symmetry retains exact sets of mapped contexts. The new mode retains neither that table nor the
ordinary antichain. Allocating an unused empty compatibility map is not retained per-visit history.

Use the DPOR byte layout from the set contract. Tokens are `(mappedOwner, firstOccurrencePC)`
integer pairs, not Step.hashCode or metadata labels. Canonicalization uses the same witness as
the value orbit, including the existing physical-ID tie break. Validate all owner/action entries
before encoding/probing; no partial marks or counters change on invalid input.

On a missing product, record its DPOR-domain key. The existing accepted-visit mark records a
VALUE-domain key in the same filter. Both insertions contribute to long mark count; duplicate VALUE
marks across different products are counted as insertions, not new states. A positive sleepCovered
lookup performs no new insertion. Metrics use the shared single-lookup heuristic and actual density;
they do not certify independence of probes, collision freedom or explored-behavior coverage.

Finite fixture oracles must not wrap the store in an ordinary StateStore and accidentally bypass
the sealed checked dispatch. Drive public checked membership with independently enumerated products;
then use a fresh store for actual explorer differential tests. Include both VALUE and DPOR marks
when establishing a fixture's no-false-positive property. No production test callback is required.

## Current State

- [verified] The inspected baseline recognizes concrete SymmetryStateStore in DPOR, normalizes
  all-asleep before exact mapped-context coverage and bypasses value-only pruning for that path.
- [verified] Exact symmetry uses copied immutable token sets; ordinary source DPOR uses a separate
  raw-key sleep antichain and special approximate value-pruning behavior. Those policies differ.
- [verified] Exact alias support uses first occurrence plus matching partitions under Spec 81;
  metadata labels/ordinary instruction counters are not equivalent token identities.
- [unverified] K1/K2 implementation and the new approximate DPOR path have not been built or measured.
  Reinspect actual merged predecessors; do not carry historical population figures into K3.

## Tests

- `mappedProductsMatchIndependentPermutationOracle` (R1/R2) — equal frames, unequal locals/PCs,
  fixed owners, original/canonical owner mapping, byte lengths and deterministic sorted token order.
  Include a tied-frame negative case: identical owners with sleeper 0 versus sleeper 1 retain
  distinct mapped tokens under the existing physical-ID tie break.
- `emptyStaleAndAliasedSleepersRetainIdentity` (R2/R5) — first-occurrence aliases, matching and
  mismatched partitions, stale actions, foreign owner/action and invalid-context no-write assertions.
- `allAsleepNormalizationPrecedesApproximateCoverage` (R3/R5) — reach the real explorer branch,
  preserve fallback behavior and distinguish normalized empty context from other mapped products.
- `valueMembershipCannotReplaceProductCoverage` (R3/R4) — same orbit/different contexts with an
  independently verified collision-free fixture; no subset-antichain assumption across permutations.
- `tinyProductFilterMayMissButNeverFabricatesEvidence` (R3/R5/R7) — force saturation, replay found
  violations and preserve concrete cutoff/resource identities; never demand exact counts under collisions.
- `membershipCapacityStaysFixedAcrossProductVisits` (R4/R7/R8) — insertion statistics, duplicates,
  copied/cleared storage and repeated/concurrent calls without an exact retained context history.
- `allStrategyApproximateSymmetryArtifactsReachBrowser` (R6/R7) — final CLI default strategy matrix,
  mixed report controls combining separately produced exact/approx rows, producing metrics and B8
  original/replay observations; do not request an unsupported mixed-store symmetry CLI selection.
- High-risk mutants: missing owner/PC/token count/domain, unordered encoding, wrong permutation,
  replacing equality with subset coverage, dropping stale tokens, moving all-asleep normalization,
  value-only pruning, unexpected extra marks and inverted precision/precedence.

## Constraints

- Dependencies: [Spec 89](89-approximate-symmetry-dfs-por.md), then
  [Spec 90](90-approximate-symmetry-cbs.md), merged sequentially. This completes selected B9 only.
- Backward compatibility: exact/ordinary DPOR, source eligibility, current CLI values/caps, wire
  fields, consumers and replay authentication remain intact. No native stream compatibility promise
  is added beyond existing documented build-specific result behavior.
- Oracle Contract: [B9 set oracle and verification contract](../../plans/approximate-symmetry-contract.md#oracle-and-verification-contract).

## Relevant Skills and Review

| Skill | Required use within this phase |
|---|---|
| `ponytail` full / `codebase-design` | Keep complete product membership behind the shared checked interface |
| `domain-modeling` | Distinguish orbit, mapped sleep product, insertion and actual observation |
| `tdd` | Behavioral product-oracle and real explorer/replay/consumer slices |
| `owasp-security` | Foreign-context validation, safe rendering and honest producer claims |
| `ponytail-review` | Connected review of DPOR dispatch, mappings, retained memory and evidence |
| `code-review` | Invoke CodeRabbit after completing this spec and after its future implementation, before pushing |

Use `doc-coauthoring` for fresh-reader checks and `receiving-code-review`/`autofix` for existing
findings. Use the approved interfaces without adding interviews or redundant approvals. Record
actual CodeRabbit completion and final local verification; an ordinary review is not external evidence.
