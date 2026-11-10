<!-- Certified exact membership storage choices without widening source or reduction eligibility. -->
# 84 — Certified exact symmetry backends

## TL;DR

The selected B6 / H capability allows a library caller to select an ordered
exact backend alongside the existing hashed backend. The supported implementations are owned
JDK HashSet/HashMap or natural-order TreeSet/TreeMap collections. This is backend selection,
not permission to inject arbitrary third-party StateStore implementations or comparators.
No demonstrated built-in-store defect motivates this optional support expansion.

## Interface and ownership

- `SymmetryBackend.HASHED` is the existing default; `ORDERED` selects natural-order tree storage.
  The enum is an immutable configuration shared safely between runs. It exposes no live container,
  factory callback, comparator or user-supplied equality operation.
- `SymmetryStateStore.forRun(program, property, strategy, backend)` first validates the exact run
  binding and source eligibility, then creates new membership containers. The existing three-argument
  method delegates to HASHED. `freshCopy` creates empty membership for the same binding/backend.
  `clear` empties every membership domain without changing backend or binding.
- `InterleaveRunner.Builder.symmetryBackend(backend)` selects storage for checked symmetry runs.
  An explicit backend requires `symmetryReduction(true)`; a null backend rejects. Selection does
  not implicitly activate symmetry or silently disappear in ordinary mode. Explicit StateStore
  instances/factories still reject in checked mode, regardless of builder call order.
- The runner constructs a fresh checked store per invocation and iterative CBS bound. Sharing an
  enum cannot share membership. Concurrent runs retain separate stores, limit visitors and partial
  results. Unknown callbacks/properties and mismatched program/property/strategy bindings still reject.
- Direct explorer users may use the four-argument factory; all explorers retain their existing
  binding checks. Ordinary runner factories returning either symmetry backend still reject so
  representative counts cannot be mislabeled as ordinary search.

```java
var runner = InterleaveRunner.builder().symmetryReduction(true)
    .symmetryBackend(SymmetryBackend.ORDERED)
    .invariant(model.invariant().orElse(null)).build();
var result = runner.run(model.program());
```

## Exactness and strategy context

The backend controls only the outer containers for immutable complete orbit keys. Canonicalization,
whole typed owner frames, grouping, last-owner mapping, minimum-cost dominance and sleep transport
remain in SymmetryStateStore. No canonicalized configuration is executed.

- DFS/static POR retain complete value-orbit Strings in a hash set or naturally ordered tree set.
  String natural-order comparison returns zero exactly when String.equals does; it cannot merge
  distinct full keys. Hash-based membership also confirms full String equality after collisions.
- CBS retains complete orbit-plus-mapped-last-owner keys and their minimum integer costs in the
  selected map. Previously visited cost q covers p iff q <= p; better costs reopen membership.
  Initial -1 is distinct from a scheduled owner, and unbounded membership remains independent.
- DPOR retains a selected outer map from full orbit keys to hash sets of exact immutable mapped
  sleep sets. Both levels of sleep-set equality remain unchanged: no ordering of sets, lossy string
  concatenation, subset optimization or new permutation witness is introduced. Empty/stale/distinct
  action contexts and matching alias partitions retain Spec 77/81 semantics.

Thus changing outer collection implementation preserves every membership answer under the existing
finite, trusted-source contracts. Natural ordering changes internal lookup complexity, not traversal
order: explorers choose original owners; they never iterate membership collections to choose actions.
No speed or memory advantage is promised. Approximate bitstate symmetry remains separate B9 work.

## Consumers, compatibility and scope

Both choices are exact and retain existing JSON `storeType: EXACT`, `symmetryReduction: true`,
strategy labels and representative/context visit counts. Complete and resource-limited runner
results keep their markers and stop reasons. Existing CLI and benchmark harness continue using HASHED;
this bounded library feature adds no CLI flag or new report/schema field. No native stream UID
promise, source-cap increase, algorithm change, dependency or CI configuration change.

Document the backend in README and supersede only Spec 77's built-in-backend restriction. Keep
arbitrary-store rejection and its tests. Broader plugins, disk-backed stores, custom comparators,
probabilistic stores, forward-helper extensions and roadmap #11/#12 remain unselected.

## Verification

Measure a fresh merged-main build/full suite/strict Javadoc and affected PIT before production edits.
Compare actual XML identities/statuses; do not count new sites as inherited kills or failures as kills.
Keep mutation operators, target tests, floors and local one-worker limits unchanged. Exercise:

- concrete container types and empty independent allocations for both backend choices;
- literal outcome/value/count oracles across all four strategies and authentic witness replay;
- independent CBS mapped-last/minimum-cost and DPOR exact-context/alias tests on both backends;
- repeated runs, copied/cleared stores, different properties/programs/strategies, concurrent runs;
- iterative CBS zero-bound cutoff versus one-preemption failure, and resource-limited results;
- builder order/null/mode conflicts and ordinary-factory marker guards for either backend;
- actual CLI/default compatibility and fresh output normalization with unchanged consumers.

Run final integrated checks, affected mutation census, default gate, local review and the required
selective CodeRabbit review. Record raw residuals and limits; publish one draft PR and stop.

Reproduce the affected census with the nested builder quoted literally:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc pitest '-PpitestTargetOverride=dev.samhb.interleave.search.SymmetryStateStore,dev.samhb.interleave.search.SymmetryBackend,dev.samhb.interleave.InterleaveRunner,dev.samhb.interleave.InterleaveRunner$Builder'
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

## Measured evidence

Fresh merged-main `a0b45b9` (PR #125) passes build/strict Javadoc and **1,750 tests**. Baseline
store is 89/89 K, runner 96/139 K + 35 S + 8 NC; a supplemental nested Builder baseline at the
same revision is 23/26 K + 3 S. The final integrated build/strict Javadoc passes **1,786 tests**.
Final affected census is store **96/96 K**, backend **4/4 K**, Builder **30/30 K** and runner
**106/139 K + 29 S + 4 NC**, with scoped R6 passing and zero resource/run errors. All 33 raw
runner residuals, lineage and remaining coverage limits are in the
[measurement ledger](../../measurements/84-symmetry-backends.md). No new backend mutation survives.

The default gate remains **436/451 K + 15 S**, R5/R6/R7 pass and all 451 exact baseline
identities/statuses are retained. New output assertions kill ten inherited runner mutations; explicit
zero-limit acceptance and literal validation messages kill the three inherited Builder survivors.
Population growth is reported separately from inherited detections. Hash-collision oracles derive
independently to literal 2,112 for Aa and BB; both distinct keys remain stored.

Artifact-level public library checks cover eight backend/strategy combinations with five visits and
exact symmetry markers. The unchanged CLI emits four PASS/five-visit rows and soundness true.
All twelve fresh outputs normalize; 22 compatibility verdict cases/four symmetry assertions pass.
No DOM rerun because schema/normalizer/renderer are unchanged. No speed/memory improvement claim.

CodeRabbit 0.8.1 completed all ten submitted files with seven trivial findings. Fixture order,
shared concurrency start, natural-order documentation, current selection links, README punctuation,
plan wording and final status/evidence are patched and locally verified. Result-object identity
is not a membership-isolation contract; independent allocation/copy tests cover ownership directly.
No CLI rerun for trivial feedback. Final measurement/roadmap cross-references receive local review.
The selected certified backend capability is locally complete, awaiting merge; arbitrary third-party
stores and approximate symmetry remain unselected.
