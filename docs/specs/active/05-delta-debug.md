# Spec 05 — Delta Debugging (ddmin)

## TL;DR
Implement delta debugging for minimizing failing concurrent schedules.
When the model checker finds a bug, `DeltaDebugger` reduces the offending
trace to a minimal subset of steps that still reproduces the violation.

## Acceptance Criteria
- `DeltaDebugger.minimize(program, failingTrace)` returns a shorter trace
  that still triggers the same invariant violation.
- The minimized trace is a subsequence of the original trace.
- Minimization is deterministic for the same input.
- A passing `build` and `test` suite.

## Out of Scope
- Parallel delta debugging.
- Adaptive minimization strategies beyond basic ddmin.
- Integration with external bug trackers.

## Commands
```bash
./gradlew test --tests "*DeltaDebugger*"
```

## Map
- `src/main/java/dev/samhb/modelcheck/core/...` — existing types
- `src/main/java/dev/samhb/modelcheck/minimize/` — new delta-debug types
- `src/test/java/dev/samhb/modelcheck/minimize/DeltaDebuggerTest.java` — Spec 05 tests
