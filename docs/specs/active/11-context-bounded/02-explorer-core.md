# Spec 11.02 — ContextBoundedExplorer Core Algorithm

## TL;DR
Implement the CHESS-style context-bounded explorer as a new 4th strategy. Explores all interleavings up to a configurable preemption bound K. Uses cost-aware state hashing (via Spec 11.01) and classifies preemptive vs forced switches. Also specifies the `Interleave.verify` integration, which is a compile-breaking requirement, not an optional extra.

## Current State
- `DfsExplorer` [verified]: exhaustive DFS with `Configuration` keyed by `state|pcs`
- `StaticPorExplorer` [verified]: persistent sets via `IndependenceRelation`
- `DporExplorer` [verified]: happens-before + sleep sets
- `Configuration` [verified]: `state`, `programCounters`, `enabledThreadIds`, `allTerminated`
- `Trace` [verified, `search/Trace.java:13-23`]: `threadIds`, `outcomes`, `outcome`; the constructor **requires** `threadIds.size() == outcomes.size()` and throws `IllegalArgumentException` otherwise
- `StateStore` [spec 11.01]: extended with `isVisited(config, lastThreadId, preemptions)` — fail-fast defaults, implemented by HashingStateStore/BitstateStore
- `Interleave.verify` [verified, `Interleave.java:58-62` and `Interleave.java:88-92`]: two **exhaustive switch expressions** over `Strategy`, handling only `DFS`/`STATIC_POR`/`DPOR`
- `InterleaveRunner.run` [verified, `InterleaveRunner.java:56-70`]: `switch (strategy)` with a `default -> throw` arm, so it does not break on a new enum value but must still gain a real `case`

## Invariants
- **Preemption definition (CHESS):** Preemption occurs ONLY when scheduler switches away from a thread that was still enabled (could continue). Forced switches (thread blocked/terminated) = 0 cost.
- **Continuation = 0 cost:** Same thread running again doesn't increment preemption count.
- **Initial step = 0 cost:** First thread selection has no previous thread.
- **Cost-aware visited check:** Check `stateStore.isVisited(config, lastThreadId, currentPreemptions)` — exact `(config, lastThreadId)` pair plus a minimum-preemption bound. The preemption cost for the next step depends on `lastThreadId`, so two histories reaching the same configuration with different `lastThreadId` must be tracked separately.
- **State at P=2 doesn't block P=1** (more budget); **State at P=1 blocks P=2** (already explored with more budget).
- **Preemption granularity = step boundaries:** A step with multiple effects executes atomically. No preemption between effects within a step.
- **DPOR/CBS isolation:** CBS is a separate strategy; does NOT combine with DPOR.
- **Bound-vs-capacity:** the explorer asserts the supplied store can represent the requested bound (Spec 11.01 §4). It never clamps.

## Acceptance Criteria

### ContextBoundedExplorer Class
New file: `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java`
Package: `dev.samhb.interleave.cb`

```java
public final class ContextBoundedExplorer {
    public DfsResult explore(Program program) { return explore(program, null); }
    public DfsResult explore(Program program, Invariant invariant) { return explore(program, invariant, null, null); }
    // Uses default K=2. Applies the bitstate capacity assertion against K=2.
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {
        return explore(program, invariant, stateStore, stateVisitor, 2);
    }
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor, int maxPreemptions) { ... }
}
```

The capacity assertion (Spec 11.01 §4) runs in the 5-arg overload, which every other overload delegates to — one check site, not four.

**Null state store means the default.** The 2-, 3-, and 4-argument overloads pass `stateStore = null`,
so the 5-arg overload must substitute a fresh `HashingStateStore` when it receives `null`, matching
`DfsExplorer` and the other strategies. `null` is the established "caller did not choose" signal — it is
distinct from an explicitly supplied store, which must be used as given.

**Resolve a store factory exactly once.** Where a caller supplies a `Supplier<StateStore>`, call
`.get()` **once, before** dispatching to the explorer, and pass the resulting `StateStore`. This mirrors
`Interleave.verify`'s existing custom-store overload (`Interleave.java:87`, which does
`StateStore store = stateStoreFactory.get();` before its switch). Resolving inside each strategy arm would
call the supplier a different number of times, which matters for any factory that allocates, reuses, or
has setup side effects.

### DFS Method Signature
```java
private void dfs(Program program, Configuration config,
                 List<Integer> currentThreadIds,
                 List<StepOutcome> currentOutcomes,
                 int lastThreadId, int currentPreemptions,
                 int maxPreemptions,
                 Invariant invariant, StateStore stateStore,
                 StateVisitor stateVisitor,
                 Map<String, Configuration> visitedStates,
                 List<Trace> traces,
                 long[] statesExplored)
```

### Preemption Classification Logic
```java
List<Integer> enabled = config.enabledThreadIds();
boolean lastStillEnabled = (lastThreadId != -1) && enabled.contains(lastThreadId);

for (int threadId : enabled) {
    int nextPreemptions = currentPreemptions;

    if (lastThreadId == -1) {
        nextPreemptions = 0; // Initial step
    } else if (threadId == lastThreadId) {
        nextPreemptions = currentPreemptions; // Continuation
    } else if (lastStillEnabled) {
        nextPreemptions = currentPreemptions + 1; // PREEMPTION (paid)
    } else {
        nextPreemptions = currentPreemptions; // FORCED SWITCH (free)
    }

    if (nextPreemptions > maxPreemptions) continue; // Budget exceeded - prune (see Spec 11.05)

    // ... execute step, recurse
}
```

**Note on the prune site:** the bare `continue` above is the single place where budget exhaustion is detected. Spec 11.05 requires snapshotting the path at exactly this point, before `continue`, because by the time DFS unwinds the path lists are empty. Spec 11.02 must not swallow or relocate that hook.

### Visited Check
```java
if (stateStore.isVisited(config, lastThreadId, currentPreemptions)) return;
stateStore.markVisited(config, lastThreadId, currentPreemptions);
```

O(1) amortized per call — see Spec 11.01 §2 for why a full-set scan is not acceptable.

### Capacity Assertion
Before searching:
```java
if (stateStore instanceof BitstateStore bs && bs.maxPreemptions() < maxPreemptions) {
    throw new IllegalArgumentException(
        "State store capacity " + bs.maxPreemptions()
        + " is below requested maxPreemptions " + maxPreemptions
        + "; construct the store as new BitstateStore(size, k, maxPreemptions)");
}
```

### Trace Creation
- `Trace.of(threadIds, outcomes, TraceOutcome.VIOLATION/DEADLOCK/COMPLETED)`
- Budget-exceeded paths tracked via a `budgetExceeded` flag plus a saved path snapshot (Spec 11.05 §3)
- Because `Trace`'s constructor enforces equal-length lists, a snapshot must be taken with `List.copyOf` of both lists together; never assemble one list from two different points in time.

### `Interleave.verify` integration

`Interleave.verify` is **not** reachable through `InterleaveRunner.Builder` — it returns `VerificationResult`, a different type from `TestResult`, and has its own switch statements. Both are exhaustive switch **expressions** (`Interleave.java:58-62`, `Interleave.java:88-92`) with no `default` arm, so adding `Strategy.CONTEXT_BOUNDED` without updating them is a **compile error**, not a silent fallthrough.

**Existing overloads** dispatch CBS at the default bound K=2:
```java
// Interleave.java:58 — verify(Program, Strategy, Invariant)
case CONTEXT_BOUNDED -> new ContextBoundedExplorer().explore(program, invariant);
```
```java
// Interleave.java:88 — verify(Program, Strategy, Invariant, Supplier<StateStore>)
case CONTEXT_BOUNDED -> new ContextBoundedExplorer().explore(program, invariant, store, null);
```

Note the custom-store overload must pass its `store` through. Omitting it would silently use a default `HashingStateStore`, discarding the caller's configured store.

**Commit boundary:** the `Strategy.CONTEXT_BOUNDED` constant, the `ContextBoundedExplorer` class, and all
three of its switch sites (`Interleave:58`, `Interleave:88`, `InterleaveRunner:56`) land in **one** commit.
The constant without the class does not compile, and the class without `TraceOutcome.INCOMPLETE` does not
compile either — so this spec follows the `TraceOutcome` propagation step (Spec 11.07 §1–2) and precedes
Specs 11.03 / 11.04 / 11.06. See the implementation order in this directory's
[`README.md`](README.md#implementation-order-compile-green).

**Two new overloads** carry an explicit bound, because the existing signatures have no place to put K:
```java
public static VerificationResult verify(Program program, Strategy strategy,
                                        Invariant invariant, int maxPreemptions) {
    // reject maxPreemptions < 0
    // case CONTEXT_BOUNDED -> new ContextBoundedExplorer().explore(program, invariant, null, null, maxPreemptions);
}

public static VerificationResult verify(Program program, Strategy strategy,
                                        Invariant invariant,
                                        Supplier<StateStore> stateStoreFactory, int maxPreemptions) {
    // reject maxPreemptions < 0
    // case CONTEXT_BOUNDED -> new ContextBoundedExplorer()
    //         .explore(program, invariant, stateStoreFactory.get(), null, maxPreemptions);
}
```

`int` and `Supplier<StateStore>` are disjoint types, so adding both does not introduce ambiguity: `verify(p, s, inv, null)` continues to resolve unambiguously to the store-factory overload. The 2-arg `verify(program, strategy)` already delegates to the 3-arg one and needs no change.

**Rejected alternative:** threading K through by hardcoding `explore(program, invariant)` (K=2) in the existing overloads. That satisfies compilation but leaves library callers with no way to select a bound outside the builder, which is a capability the feature exists to provide.

## Tests

**File:** `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerTest.java`

### Preemption Classification
- `preemptionClassification_initialStep_zeroCost()`
- `preemptionClassification_continuation_zeroCost()`
- `preemptionClassification_forcedSwitch_blockedThread_zeroCost()`
- `preemptionClassification_forcedSwitch_terminatedThread_zeroCost()`
- `preemptionClassification_paidPreemption_enabledThreadSwitched_increments()`

### State Counts
- `basicExploration_twoThreadProgram_K0_K1_K2_stateCountDiff()` — K=0 < K=1 < K=2

### Violation Detection
- `violationDetection_brokenPeterson_foundAtK1()`
- `violationDetection_lostUpdate_foundAtK1()`
- `violationDetection_doubleCheckedLocking_foundAtK2()`

### Visited Logic (Cost-Aware Pruning)
- `visitedLogic_sameStateLowerPreemptionNotPruned()` — S@P=2 then S@P=1 explores
- `visitedLogic_sameStateHigherPreemptionPruned()` — S@P=1 then S@P=2 prunes
- `visitedLogic_samePreemptionPruned()` — S@P=1 then S@P=1 prunes
- `visitedLogic_differentLastThreadIdNotPruned()` — same config, different last thread

### Budget Exceeded / INCOMPLETE
- `verdict_INCOMPLETE_whenBudgetExceeded()` — deep bug at K=3 returns INCOMPLETE at K=1
- `verdict_PASS_whenNoBudgetExceeded()` — shallow bug found within K returns VIOLATION, no INCOMPLETE

### Step Granularity
- `stepGranularity_singleStepAtomic_noPreemptionInside()` — step with 2 effects executes atomically

### Capacity Contract
- see `ContextBoundedExplorerCapacityTest` in Spec 11.01

**File:** `src/test/java/dev/samhb/interleave/InterleaveVerifyCBTest.java`
- `verify_contextBounded_defaultOverload_usesK2()` — states explored equals an explicit K=2 run
- `verify_contextBounded_explicitBound_matchesExplorer()` — `verify(p, CBS, inv, 3)` equals a direct
  `explore(..., 3)` call
- `verify_contextBounded_customStore_usesSuppliedStore()` — a `BitstateStore` with capacity 3 is the
  store actually used (assert via behavior: K=3 succeeds, which would throw if the store were dropped)
- `verify_contextBounded_nullStoreArgument_usesDefaultStore()` — the overloads that pass `null` fall back
  to a fresh `HashingStateStore` and complete rather than NPE
- `verify_contextBounded_storeFactory_invokedExactlyOnce()` — a counting `Supplier<StateStore>` records one
  `get()` call, pinning the resolve-once-before-dispatch rule
- `verify_contextBounded_negativeBound_throws()`
- `verify_contextBounded_nullFactoryArg_unambiguous()` — `verify(p, s, inv, null)` compiles and resolves
  to the `Supplier` overload

## Out of Scope
- Iterative deepening — Spec 11.05
- CLI integration — Spec 11.03
- `BenchmarkHarness` integration — Spec 11.04
- Verdict propagation (`INCOMPLETE`) — Spec 11.05
- `InterleaveRunner` / `InterleaveRunner.Builder` — Spec 11.06
- `VerificationResult` / `TestResult` field additions and switch-site updates — Spec 11.07

## Commands
```bash
./gradlew test --tests "*ContextBoundedExplorer*"
./gradlew test --tests "*InterleaveVerifyCB*"
```

## Map
- `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java` — new explorer
- `src/main/java/dev/samhb/interleave/Interleave.java` — both `verify` overloads gain `CONTEXT_BOUNDED`; two new `maxPreemptions` overloads
- `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerTest.java` — core algorithm tests
- `src/test/java/dev/samhb/interleave/InterleaveVerifyCBTest.java` — `verify` dispatch tests
