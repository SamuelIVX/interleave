# Spec 11.02 — ContextBoundedExplorer Core Algorithm

## TL;DR
Implement the CHESS-style context-bounded explorer as a new 4th strategy. Explores all interleavings up to a configurable preemption bound K. Uses cost-aware state hashing (via Spec 11.01) and classifies preemptions vs forced switches.

## Current State
- `DfsExplorer` [verified]: exhaustive DFS with `Configuration` keyed by `state|pcs`
- `StaticPorExplorer` [verified]: persistent sets via `IndependenceRelation`
- `DporExplorer` [verified]: happens-before + sleep sets
- `Configuration` [verified]: `state`, `programCounters`, `enabledThreadIds`, `allTerminated`
- `Trace` [verified]: `threadIds`, `outcomes`, `outcome` (VIOLATION/DEADLOCK/COMPLETED)
- `StateStore` [spec 11.01]: extended with `isVisited(config, lastThreadId, preemptions)` — fail-fast defaults, implemented by HashingStateStore/BitstateStore

## Invariants
- **Preemption definition (CHESS):** Preemption occurs ONLY when scheduler switches away from a thread that was still enabled (could continue). Forced switches (thread blocked/terminated) = 0 cost.
- **Continuation = 0 cost:** Same thread running again doesn't increment preemption count.
- **Initial step = 0 cost:** First thread selection has no previous thread.
- **Cost-aware visited check:** Check `stateStore.isVisited(config, lastThreadId, currentPreemptions)` — exact (config, lastThreadId, preemptions) triple. The preemption cost for the next step depends on lastThreadId, so two histories reaching the same configuration with different lastThreadId must be tracked separately.
- **State at P=2 doesn't block P=1** (more budget); **State at P=1 blocks P=2** (already explored with more budget).
- **Preemption granularity = step boundaries:** A step with multiple effects executes atomically. No preemption between effects within a step.
- **DPOR/CBS isolation:** CBS is a separate strategy; does NOT combine with DPOR.

## Acceptance Criteria

### ContextBoundedExplorer Class
New file: `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java`
Package: `dev.samhb.interleave.cb`

```java
public final class ContextBoundedExplorer {
    public DfsResult explore(Program program) { return explore(program, null); }
    public DfsResult explore(Program program, Invariant invariant) { return explore(program, invariant, null, null); }
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor) {
        return explore(program, invariant, stateStore, stateVisitor, 2); // default K=2
    }
    public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor, int maxPreemptions) { ... }
}
```

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
    
    if (nextPreemptions > maxPreemptions) continue; // Budget exceeded - prune
    
    // ... execute step, recurse
}
```

### Visited Check
```java
if (stateStore.isVisited(config, lastThreadId, currentPreemptions)) return;
stateStore.markVisited(config, lastThreadId, currentPreemptions);
```

### Trace Creation
- `Trace.of(threadIds, outcomes, TraceOutcome.VIOLATION/DEADLOCK/COMPLETED)`
- Budget exceeded paths tracked via `budgetExceeded` flag (Spec 11.05)

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

### Budget Exceeded / INCOMPLETE
- `verdict_INCOMPLETE_whenBudgetExceeded()` — deep bug at K=3 returns INCOMPLETE at K=1
- `verdict_PASS_whenNoBudgetExceeded()` — shallow bug found within K returns VIOLATION, no INCOMPLETE

### Step Granularity
- `stepGranularity_singleStepAtomic_noPreemptionInside()` — step with 2 effects executes atomically

## Out of Scope
- Iterative deepening (Spec 11.05)
- CLI integration (Spec 11.03)
- BenchmarkHarness integration (Spec 11.04)
- INCOMPLETE verdict (Spec 11.05)

## Commands
```bash
./gradlew test --tests "*ContextBoundedExplorer*"
```

## Map
- `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java` — new explorer
- `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerTest.java` — core algorithm tests