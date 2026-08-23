# Spec 06 — State Hashing Infrastructure

## TL;DR
Implement canonical state encoding and hashing so the model checker can
reliably detect when two different execution paths have reached the same
logical state. This is the foundation for all state-space reduction in later
specs.

## Acceptance Criteria
- `CanonicalEncoder` produces a deterministic byte representation for any
  `SharedState`, independent of object identity or JVM run details.
- `HashingStateStore` uses `CanonicalEncoder` plus a cryptographic hash to
  deduplicate visited states in `DfsExplorer`.
- `BitstateStore` provides a fixed-size bloom-filter-style visited set that
  may return false positives but never false negatives.
- Replacing `DfsExplorer`'s visited-set backend with `HashingStateStore`
  does not change the set of reported traces.
- A passing `build` and `test` suite.

## Out of Scope
- Parallel / distributed state storage.
- Persistent on-disk state stores.
- Memory-bounded DPOR variants.

## Commands
```bash
./gradlew test --tests "*StateHashing*"
```

## Map
- `src/main/java/dev/samhb/modelcheck/core/...` — existing types
- `src/main/java/dev/samhb/modelcheck/state/` — new state hashing types
- `src/test/java/dev/samhb/modelcheck/state/StateHashingTest.java` — Spec 06 tests
