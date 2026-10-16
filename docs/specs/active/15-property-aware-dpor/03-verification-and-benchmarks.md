# 15.03 — Verification, Benchmarks & Differential Oracles for Spec 15

## TL;DR

This document details the verification suite, empirical benchmarks, differential testing oracles, and mutation targets for Property-Aware Dynamic Partial Order Reduction in `DporExplorer`.

## Verification Matrix

### 1. Dedicated Property-Aware Unit Tests (`DporPropertyAwarenessTest.java`)

| Test Method | Category | Verified Contract |
|---|---|---|
| `observedPropertyReducesIndependentInvisibleWork` | Reduction | DPOR explores fewer states than DFS when independent invisible work exists. |
| `visibleIndependentWritesCannotHideIntermediateViolations` | Soundness | Two visible independent writes to distinct observed variables preserve the intermediate violation trace. |
| `visibleStepRefreshesSleepSet` | Soundness | Executing a visible transition empties the sleep set so subsequent transitions observe the new state. |
| `invisibleStepsCommuteSafelyWithoutHidingViolation` | Soundness | Interleaving invisible operations before an assertion or invariant failure does not prune the bug path. |
| `unknownObservationsFallBackToExhaustiveDfs` | Backward Compat | An invariant with `observedLocations().isEmpty()` triggers exhaustive `dfsDfs`. |
| `dslFinalPropertyFallsBackToExhaustiveDfs` | Backward Compat | JSON DSL program with `when: final` falls back to exhaustive `dfsDfs`. |
| `dslAlwaysPropertyAppliesPropertyAwareReduction` | Feature | JSON DSL program with `when: always` derives observations and prunes invisible interleavings. |
| `emptyObservationsReducesCompletely` | Edge Case | Invariant with `observedLocations() = Optional.of(Set.of())` treats all steps as invisible and maximizes reduction. |

### 2. Differential Oracles

For all corpus benchmarks:
- **Verdict Equivalence**: $\text{outcomes}(\text{DPOR}(P, I)) \equiv \text{outcomes}(\text{DFS}(P, I))$
- **Violation Preservation**: If $\text{DFS}(P, I)$ contains `VIOLATION`, then $\text{DPOR}(P, I)$ contains `VIOLATION`.
- **Deadlock Preservation**: If $\text{DFS}(P, I)$ contains `DEADLOCK`, then $\text{DPOR}(P, I)$ contains `DEADLOCK`.
- **State Monotonicity**: $\text{states}(\text{DPOR}(P, I)) \le \text{states}(\text{DFS}(P, I))$.

### 3. BugCorpus Benchmark Suite

All 8 benchmarks in `BugCorpus.all()` are executed against `DporExplorer`:
- `peterson`: Expected `COMPLETED` (mutual exclusion holds)
- `broken-peterson`: Expected `VIOLATION`
- `broken-peterson-v2`: Expected `VIOLATION`
- `deadlock`: Expected `DEADLOCK`
- `double-checked-locking`: Expected `VIOLATION`
- `lost-update`: Expected `VIOLATION`
- `lost-update-3t`: Expected `VIOLATION`
- `torn-counter`: Expected `VIOLATION`

## Mutation Targets (PIT)

Target scope: `dev.samhb.interleave.dpor.DporExplorer` and `dev.samhb.interleave.dpor.SleepSet`.
- Inverting `stepVisible` condition: caught by `visibleStepRefreshesSleepSet` and `visibleIndependentWritesCannotHideIntermediateViolations`.
- Inverting `otherInvisible` condition: caught by `observedPropertyReducesIndependentInvisibleWork` (mutant surviving if visibility test omitted).
- Removing fallback dispatch: caught by `unknownObservationsFallBackToExhaustiveDfs`.
- Mutating `writesOverlap`: caught by aliasing tests and visible race preservation.
