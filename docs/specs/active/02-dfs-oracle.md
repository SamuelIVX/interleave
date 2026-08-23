# Spec 02 — Exhaustive DFS Oracle

## TL;DR
Implement the exhaustive-depth-first-search model-checker core that explores
every reachable configuration from a program's initial state, records the
trace that reaches each state, and reports whether any invariant is violated.
This oracle becomes the **immutable differential baseline** for all later
reduction strategies (hashing, static POR, DPOR).

## Acceptance Criteria
- `DfsExplorer.explore(program)` returns a non-empty result set containing
  every reachable configuration, keyed by a canonical representation.
- Every returned result includes the full replayable trace from the initial
  configuration.
- The explorer does not revisit a configuration whose canonical form is
  already in the result set.
- If an `Invariant` is supplied, the explorer reports every violating
  configuration together with the trace that reaches it.
- A `TraceReplayer` can replay any returned trace on the same program and
  reach the same final configuration.
- A passing `build` and `test` suite.

## Out of Scope
- POR / DPOR (Spec 03+).
- Parallel / distributed state storage.
- GUI or report formatting beyond basic trace printing.

## Commands
```bash
./gradlew test --tests "*DfsExplorer*"
```

## Map
- `src/main/java/dev/samhb/modelcheck/core/...` — existing types from Spec 01
- `src/main/java/dev/samhb/modelcheck/search/` — new DFS + trace types
- `src/test/java/dev/samhb/modelcheck/search/DfsExplorerTest.java` — Spec 02 tests
