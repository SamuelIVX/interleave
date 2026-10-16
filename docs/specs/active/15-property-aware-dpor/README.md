# Spec 15 — Dynamic Property-Aware DPOR Reduction — Spec Set Overview

This directory contains the specification, formal soundness proofs, visibility contracts, and verification plans for Dynamic Property-Aware Partial Order Reduction in `interleave`.

## TL;DR

Where `DporExplorer` historically disabled all sleep-set reduction whenever an invariant was supplied (falling back to unreduced exhaustive `dfsDfs`), Spec 15 incorporates state-property visibility into dynamic sleep-set exploration. When an invariant declares complete `observedLocations()`, invisible transitions that do not write to the observed footprint are pruned using sleep sets, while visible transitions are never put to sleep and visible executions refresh sleeping transitions. Invariants with unknown observations retain the exhaustive DFS fallback.

## Motivation & Background

- **Historical limitation**: In `DporExplorer.java:91-98`, `if (invariant != null)` unconditionally routed to `dfsDfs`. This prevented DPOR from achieving reduction on any program checked against an invariant, leading to state explosion on programs with unobserved concurrent operations.
- **Spec 13.08 predecessor**: Spec 13.08 implemented static property-aware reduction via `PropertyPersistentSetComputer` in `StaticPorExplorer`.
- **Spec 15 objective**: Bring property awareness to DPOR (`DporExplorer`), establishing that sleep sets can soundly prune equivalent schedules for state invariants without sacrificing safety violation detection or deadlock detection.

## Spec Set Structure

| Document | Title | Focus |
|---|---|---|
| [01-property-visibility-dpor.md](01-property-visibility-dpor.md) | Visibility Contract & Algorithmic Dispatch | Visibility definition, sleep-set rules, API dispatch, fallback preservation |
| [02-soundness-proof.md](02-soundness-proof.md) | Mathematical Soundness Proof | Finite-trace commutativity, intermediate predicate invariance, bug preservation |
| [03-verification-and-benchmarks.md](03-verification-and-benchmarks.md) | Verification, Benchmarks & Oracles | BugCorpus validation, differential checks vs DFS/Static POR, state reduction |

## Acceptance Criteria

1. **Property-Aware DPOR Reduction**: `DporExplorer.explore(program, invariant)` prunes states via sleep sets when `invariant.observedLocations().isPresent()`.
2. **Exhaustive Fallback Preservation**: When `invariant.observedLocations().isEmpty()` (unknown callbacks, configuration-dependent predicates, DSL `final`), DPOR strictly executes exhaustive `dfsDfs`.
3. **Soundness & Zero Missed Violations**:
   - All invariant violations and deadlocks across `BugCorpus.all()` are detected identically to `DfsExplorer`.
   - Visible races (such as independent writes to distinct observed variables) are never suppressed by sleep sets.
4. **State Reduction**: Explores strictly fewer states than DFS on programs containing independent invisible operations.
5. **No Regressions**: All existing tests pass, Javadoc coverage remains complete, and single-worker CI checks succeed.

## Commands

```bash
# Run DPOR tests including property awareness
./gradlew test --tests "*Dpor*" --max-workers=1 -PpitestThreads=1

# Run full project checks
./gradlew check javadoc --max-workers=1 -PpitestThreads=1
```
