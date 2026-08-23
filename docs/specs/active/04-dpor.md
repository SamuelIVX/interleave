# Spec 04 — Dynamic Partial Order Reduction (DPOR)

## TL;DR
Implement dynamic POR (DPOR) for explicit-state model checking. Unlike static
POR (Spec 03), DPOR discovers necessary reorderings from actual execution
traces using happens-before/race detection and sleep sets. The DPOR explorer
must produce the same invariant verdict as Spec 02's `DfsExplorer`, while
exploring fewer or equal states compared to `StaticPorExplorer`.

## Acceptance Criteria
- `HappensBefore` tracks the happens-before relation during exploration.
- `SleepSet` prevents redundant exploration of equivalent interleavings.
- `DporExplorer` performs dynamic POR using race detection and sleep sets.
- States-explored count from `DporExplorer` is strictly less than or equal to
  `StaticPorExplorer` for the same program.
- Final invariant verdict is identical to `DfsExplorer`.
- A passing `build` and `test` suite.

## Out of Scope
- Optimal DPOR or source-DPOR.
- Persistent-set optimality proofs.
- Parallel / distributed state storage.

## Commands
```bash
./gradlew test --tests "*Dpor*"
```

## Map
- `src/main/java/dev/samhb/modelcheck/core/...` — existing types from Specs 01-03
- `src/main/java/dev/samhb/modelcheck/dpor/` — new DPOR types
- `src/test/java/dev/samhb/modelcheck/dpor/DporExplorerTest.java` — Spec 04 tests
