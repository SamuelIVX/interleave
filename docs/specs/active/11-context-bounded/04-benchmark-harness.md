# Spec 11.04 — BenchmarkHarness Integration

## TL;DR
Integrate ContextBoundedExplorer into BenchmarkHarness as a 4th strategy alongside DFS, STATIC_POR, DPOR. Supports both single-run and iterative-deepening modes. Handles INCOMPLETE verdict propagation (Spec 11.05).

## Current State
- `BenchmarkHarness` [verified]: Runs all programs with 3 strategies × 2 store types = 6 results/program
- `runProgramWithStore()` [verified]: Creates explorers, runs with timing/memory measurement
- `actualVerdict()` [verified]: Derives verdict from traces (VIOLATION/DEADLOCK/PASS)
- `StoreType` enum: EXACT, BITSTATE

## Invariants
- CBS runs as **separate strategy** — not mixed with DPOR
- `maxPreemptions` and `iterativeDeepening` passed from CLI via BenchmarkHarness constructor
- CBS respects `--store exact|bitstate` filter like other strategies
- CBS verdict validation: only validate exact-store CBS verdicts against expected when not INCOMPLETE

## Acceptance Criteria

### BenchmarkHarness Constructor
```java
private final int maxPreemptions;
private final boolean iterativeDeepening;

public BenchmarkHarness(int bitstateSize, int bitstateK, Set<StoreType> storeFilter, Set<String> strategyFilter) {
    this(bitstateSize, bitstateK, storeFilter, strategyFilter, 2, false);
}

public BenchmarkHarness(int bitstateSize, int bitstateK, Set<StoreType> storeFilter, Set<String> strategyFilter, int maxPreemptions) {
    this(bitstateSize, bitstateK, storeFilter, strategyFilter, maxPreemptions, false);
}

public BenchmarkHarness(int bitstateSize, int bitstateK, Set<StoreType> storeFilter, Set<String> strategyFilter, int maxPreemptions, boolean iterativeDeepening) {
    if (maxPreemptions < 0) throw new IllegalArgumentException("maxPreemptions must be >= 0");
    this.bitstateSize = bitstateSize;
    this.bitstateK = bitstateK;
    this.storeFilter = storeFilter;
    this.strategyFilter = strategyFilter;
    this.maxPreemptions = maxPreemptions;
    this.iterativeDeepening = iterativeDeepening;
}
```

### runProgramWithStore Integration
```java
boolean runCb = strategyFilter == null || strategyFilter.contains("CONTEXT_BOUNDED");
if (runCb) {
    ContextBoundedExplorer cbExplorer = new ContextBoundedExplorer();
    StateStore cbStore = storeFactory.get();
    
    DfsResultWithTiming cbResult;
    if (iterativeDeepening) {
        cbResult = runExplorerWithIterativeDeepening(program, invariant, storeFactory, maxPreemptions);
    } else {
        cbResult = runExplorer(() -> cbExplorer.explore(program.program(), invariant, cbStore, null, maxPreemptions));
    }
    
    String cbVerdict = actualVerdict(cbResult.result(), storeType); // pass storeType
    Trace cbFailing = findFailingTrace(cbResult.result());
    results.add(createResult("CONTEXT_BOUNDED", program.name(), cbResult, cbVerdict, cbFailing, storeType, cbStore));
}
```

### Iterative Deepening Helper
```java
private DfsResultWithTiming runExplorerWithIterativeDeepening(BenchmarkProgram program, Invariant invariant,
                                                                java.util.function.Supplier<StateStore> storeFactory,
                                                                int maxPreemptions) {
    DfsResultWithTiming lastResult = null;
    for (int k = 0; k <= maxPreemptions; k++) {
        int bound = k; // Capture loop variable for lambda
        ContextBoundedExplorer explorer = new ContextBoundedExplorer();
        StateStore store = storeFactory.get();
        DfsResultWithTiming result = runExplorer(() -> explorer.explore(program.program(), invariant, store, null, bound));
        lastResult = result; // Retain for final return
        
        String verdict = actualVerdict(result.result(), storeType); // pass storeType
        if (verdict.equals("VIOLATION") || verdict.equals("DEADLOCK")) {
            return result; // Stop at first violation (minimal K trace)
        }
        // If INCOMPLETE at this K, continue to next K
    }
    // Return the final iteration's result (already computed) — avoid duplicate search
    return lastResult;
}
```

### Verdict Validation
```java
// In runProgramWithStore after getting verdict:
String expectedVerdict = program.expectedVerdict();
if (expectedVerdict != null && storeType == StoreType.EXACT) {
    if (!expectedVerdict.equals(cbVerdict) && !cbVerdict.equals("INCOMPLETE")) {
        throw new IllegalStateException("Expected verdict " + expectedVerdict + " for " + program.name() + " but got " + cbVerdict);
    }
}
// BITSTATE verdicts are always APPROXIMATE — no validation against expected
```

## Tests
**File:** `src/test/java/dev/samhb/interleave/report/BenchmarkHarnessCBTest.java`
- `runProgram_CONTEXT_BOUNDED_includedInResults()`
- `runProgram_iterativeDeepening_returnsMinimalKTrace()`
- `actualVerdict_INCOMPLETE_fromTrace()`
- `runProgram_CBS_skippedWhenStrategyFilterExcludes()`

## Out of Scope
- INCOMPLETE verdict implementation (Spec 11.05)
- Explorer core (Spec 11.02)
- CLI parsing (Spec 11.03)

## Commands
```bash
./gradlew test --tests "*BenchmarkHarness*"
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"
```

## Map
- `src/main/java/dev/samhb/interleave/report/BenchmarkHarness.java` — strategy integration, iterative deepening
- `src/test/java/dev/samhb/interleave/report/BenchmarkHarnessTest.java` — CBS integration tests