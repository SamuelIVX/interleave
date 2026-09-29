# Spec 11.05 — INCOMPLETE Verdict & Iterative Deepening

## TL;DR
Add `TraceOutcome.INCOMPLETE` for CBS budget-exceeded runs. Track when paths are pruned due to preemption budget exhaustion. Propagate through verdict system. Implement iterative deepening mode that runs K=0,1,2... and stops at first violation.

## Current State
- `TraceOutcome` enum [verified]: VIOLATION, DEADLOCK, COMPLETED
- `Trace` class [verified]: `threadIds`, `outcomes`, `outcome`
- `BenchmarkHarness.actualVerdict()` [verified]: Returns VIOLATION/DEADLOCK/PASS
- `ContextBoundedExplorer` [spec 11.02]: Tracks `budgetExceeded` flag when pruning paths

## Invariants
- **INCOMPLETE = budget exceeded, no violation found**: Some paths were pruned due to `nextPreemptions > maxPreemptions` (EXACT store only)
- **PASS = truly exhaustive within bound**: No paths pruned, no violation found (EXACT store only)
- **BITSTATE results are always APPROXIMATE**: Bloom filter false positives may cause spurious pruning. Bitstate CBS verdicts are inherently inconclusive and marked as such in reports.
- **Iterative deepening**: Runs K=0,1,2...maxPreemptions; returns immediately on first VIOLATION/DEADLOCK (EXACT store)
- **SoundnessAttestation**: Excludes INCOMPLETE CBS results and all BITSTATE results (incomplete by design)

## Acceptance Criteria

### 1. TraceOutcome Enum Extension
In `src/main/java/dev/samhb/interleave/search/TraceOutcome.java`:
```java
public enum TraceOutcome {
    VIOLATION,
    DEADLOCK,
    COMPLETED,
    INCOMPLETE  // Context-bounded search exhausted preemption budget without finding violation
}
```

### 2. Trace Factory Method
In `src/main/java/dev/samhb/interleave/search/Trace.java`:
```java
public static Trace incomplete(List<Integer> threadIds, List<StepOutcome> outcomes) {
    return new Trace(threadIds, outcomes, TraceOutcome.INCOMPLETE);
}
```

### 3. ContextBoundedExplorer Budget Tracking
Add to `ContextBoundedExplorer` class:
```java
private boolean budgetExceeded = false;

// In each top-level explore() method, reset before search:
public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor, int maxPreemptions) {
    this.budgetExceeded = false; // Reset for each top-level search
    ...
}

// In dfs(), when pruning due to budget:
if (nextPreemptions > maxPreemptions) {
    budgetExceeded = true;
    continue;
}

// After TOP-LEVEL search completes (not per-DFS-invocation), if budget was exceeded and no violation found:
if (budgetExceeded && traces.stream().noneMatch(t -> t.outcome() == TraceOutcome.VIOLATION || t.outcome() == TraceOutcome.DEADLOCK)) {
    // Emit ONE run-level INCOMPLETE trace with the partial schedule
    Trace trace = Trace.incomplete(List.copyOf(currentThreadIds), List.copyOf(currentOutcomes));
    traces.add(trace);
    if (stateVisitor != null) stateVisitor.onTraceCreated(trace);
}
```

### 4. BenchmarkHarness Verdict Logic
In `BenchmarkHarness.actualVerdict()`:
```java
private static String actualVerdict(DfsResult result, StoreType storeType) {
    boolean hasViolation = result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
    boolean hasDeadlock = result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK);
    boolean hasIncomplete = result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE);

    if (hasViolation) return "VIOLATION";
    if (hasDeadlock) return "DEADLOCK";
    if (storeType == StoreType.BITSTATE) return "APPROXIMATE_PASS"; // Bitstate is always approximate
    if (hasIncomplete) return "INCOMPLETE";
    return "PASS";
}
```

### 5. ReportWriter / StatesExploredTable
- Add INCOMPLETE column/row handling in `ReportWriter.writeMarkdown()` and `writeJson()`
- `StatesExploredTable` includes INCOMPLETE in verdict column
- JSON output includes `outcome: "INCOMPLETE"` in trace records

### 6. SoundnessAttestation
- Only validate EXACT store with non-INCOMPLETE verdicts for CBS
- INCOMPLETE CBS runs are "pass" for soundness (incomplete by design, not a bug)

### 7. Iterative Deepening Behavior
- When `--iterative-deepening` flag set: runs K=0,1,2...maxPreemptions
- Stops at first K where VIOLATION or DEADLOCK found (returns minimal-preemption trace)
- If no violation at any K, returns final result (PASS or INCOMPLETE at maxPreemptions)
- Each K iteration uses fresh StateStore (clear between iterations)

## Tests
**File:** `src/test/java/dev/samhb/interleave/report/ReportWriterCBTest.java`
- `writeJson_INCOMPLETE_traceIncluded()`
- `writeMarkdown_INCOMPLETE_verdictColumn()`
- `soundnessAttestation_excludesIncompleteCBS()`

**File:** `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerTest.java` (iterative deepening tests)
- `iterativeDeepening_violationAtK1_stopsAtK1()` — runs K=0,1 returns at K=1
- `iterativeDeepening_noViolation_runsAllK()` — runs K=0,1,2 returns final

## Out of Scope
- Explorer core (Spec 11.02)
- CLI parsing (Spec 11.03)
- BenchmarkHarness integration (Spec 11.04)

## Commands
```bash
./gradlew test --tests "*ContextBounded*"
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --iterative-deepening --json"
```

## Map
- `src/main/java/dev/samhb/interleave/search/TraceOutcome.java` — add INCOMPLETE enum
- `src/main/java/dev/samhb/interleave/search/Trace.java` — add incomplete() factory
- `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java` — budget tracking
- `src/main/java/dev/samhb/interleave/report/BenchmarkHarness.java` — actualVerdict() update
- `src/main/java/dev/samhb/interleave/report/ReportWriter.java` — INCOMPLETE handling
- `src/main/java/dev/samhb/interleave/report/StatesExploredTable.java` — INCOMPLETE column
- `src/main/java/dev/samhb/interleave/report/SoundnessAttestation.java` — exclude INCOMPLETE CBS