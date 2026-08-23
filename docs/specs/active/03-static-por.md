# Spec 03 — Static Partial Order Reduction

## TL;DR
Implement static POR (Partial Order Reduction) for explicit-state model checking.
Compute persistent sets from read/write access sets to avoid exploring equivalent
interleavings. The static POR explorer must produce exactly the same final
verdict as Spec 02's `DfsExplorer`, but explore fewer states.

## Acceptance Criteria
- `IndependenceRelation` classifies two steps as independent or dependent
  based on their read/write sets.
- `PersistentSetComputer` computes the ample set for each enabled step at
  each configuration using the static algorithm.
- `StaticPorExplorer` explores configurations using persistent sets instead
  of all enabled steps.
- States-explored count from `StaticPorExplorer` is strictly less than or
  equal to `DfsExplorer` for the same program.
- Final invariant verdict is identical to `DfsExplorer`.
- A passing `build` and `test` suite.

## Out of Scope
- Dynamic POR / DPOR (Spec 04).
- Sleep sets or cycle provisos beyond basic static ample-set computation.
- Persistent-set optimality proofs.

## Commands
```bash
./gradlew test --tests "*StaticPor*"
```

## Map
- `src/main/java/dev/samhb/modelcheck/core/...` — existing types from Specs 01-02
- `src/main/java/dev/samhb/modelcheck/por/` — new POR types
- `src/test/java/dev/samhb/modelcheck/por/StaticPorExplorerTest.java` — Spec 03 tests
