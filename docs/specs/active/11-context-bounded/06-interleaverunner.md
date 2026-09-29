# Spec 11.06 — InterleaveRunner Strategy Enum Extension

## TL;DR
Add `CONTEXT_BOUNDED` to the `Strategy` enum in `InterleaveRunner` to support library/API mode usage alongside CLI mode.

## Current State
- `InterleaveRunner` [verified]: Fluent builder API with `Strategy` enum (DFS, STATIC_POR, DPOR)
- `InterleaveRunner.Strategy` [verified]: Used in `InterleaveRunner.Builder.strategy()`
- `Interleave.quickCheck()` / `Interleave.builder()` [verified]: Static entry points
- `TestResult`, `TraceRecord` [verified]: Immutable serializable results

## Invariants
- Library API mirrors CLI capabilities: `CONTEXT_BOUNDED` strategy with preemption bound
- `InterleaveRunner.Builder` accepts `maxPreemptions(int)` and `iterativeDeepening(boolean)` for CBS
- Results return `TestResult` with `TraceRecord` containing INCOMPLETE when applicable
- Backward compatible: existing code using DFS/STATIC_POR/DPOR unchanged

## Acceptance Criteria

### Strategy Enum Extension
In `src/main/java/dev/samhb/interleave/InterleaveRunner.java`:
```java
public enum Strategy {
    DFS,
    STATIC_POR,
    DPOR,
    CONTEXT_BOUNDED  // Context-bounded search with preemption limit
}
```

### Builder Extension
Add to `InterleaveRunner.Builder`:
```java
public class Builder {
    private int maxPreemptions = 2;
    private boolean maxPreemptionsExplicit = false;
    private boolean iterativeDeepening = false;
    
    public Builder maxPreemptions(int maxPreemptions) {
        if (maxPreemptions < 0) throw new IllegalArgumentException("maxPreemptions must be >= 0");
        this.maxPreemptions = maxPreemptions;
        this.maxPreemptionsExplicit = true;
        return this;
    }
    
    public Builder iterativeDeepening(boolean iterativeDeepening) {
        this.iterativeDeepening = iterativeDeepening;
        return this;
    }
    
    public InterleaveRunner build() {
        // Validate: maxPreemptions/iterativeDeepening only for CONTEXT_BOUNDED
        if (strategy != Strategy.CONTEXT_BOUNDED && (maxPreemptionsExplicit || iterativeDeepening)) {
            throw new IllegalStateException("maxPreemptions and iterativeDeepening only valid for CONTEXT_BOUNDED strategy");
        }
        return new InterleaveRunner(this);
    }
}
```

### Runner Execution
In `InterleaveRunner.run(Program)`:
```java
public TestResult run(Program program) {
    Invariant invariant = this.invariant;
    StateStore store = stateStoreFactory.get();
    
    return switch (strategy) {
        case DFS -> runExplorer(() -> new DfsExplorer().explore(program, invariant, store, stateVisitor));
        case STATIC_POR -> runExplorer(() -> new StaticPorExplorer().explore(program, invariant, store, stateVisitor));
        case DPOR -> runExplorer(() -> new DporExplorer().explore(program, invariant, store, stateVisitor));
        case CONTEXT_BOUNDED -> {
            if (iterativeDeepening) {
                yield runIterativeDeepening(program, invariant, store);
            }
            yield runExplorer(() -> new ContextBoundedExplorer().explore(program, invariant, store, stateVisitor, maxPreemptions));
        }
    };
}

private TestResult runIterativeDeepening(Program program, Invariant invariant, StateStore store) {
    DfsResult lastResult = null;
    for (int k = 0; k <= maxPreemptions; k++) {
        ContextBoundedExplorer explorer = new ContextBoundedExplorer();
        StateStore freshStore = store.freshCopy();
        DfsResult result = explorer.explore(program, invariant, freshStore, stateVisitor, k);
        lastResult = result; // Retain for final return
        
        String verdict = actualVerdict(result);
        if (verdict.equals("VIOLATION") || verdict.equals("DEADLOCK")) {
            return convertToTestResult(result); // Minimal K trace
        }
    }
    // Return the final iteration's result (already computed) — avoid duplicate search
    return convertToTestResult(lastResult);
}
```

### Static Entry Points
In `Interleave` class:
```java
public static TestResult quickCheck(Program program) {
    return quickCheck(program, Strategy.DFS);
}

public static TestResult quickCheck(Program program, Strategy strategy) {
    return builder().strategy(strategy).build().run(program);
}

// Overload for CBS
public static TestResult quickCheck(Program program, int maxPreemptions) {
    return builder().strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(maxPreemptions).build().run(program);
}

public static TestResult quickCheck(Program program, int maxPreemptions, boolean iterativeDeepening) {
    return builder().strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(maxPreemptions).iterativeDeepening(iterativeDeepening).build().run(program);
}
```

## Tests
**File:** `src/test/java/dev/samhb/interleave/api/InterleaveRunnerCBTest.java`
- `builder_CONTEXT_BOUNDED_strategyAccepted()`
- `builder_maxPreemptions_setsValue()`
- `builder_iterativeDeepening_setsValue()`
- `builder_rejectsMaxPreemptionsForDFS()` — throws IllegalStateException
- `quickCheck_CONTEXT_BOUNDED_withMaxPreemptions()`
- `quickCheck_CONTEXT_BOUNDED_iterativeDeepening()`
- `run_CONTEXT_BOUNDED_returnsTestResult()`

## Out of Scope
- CLI integration (Spec 11.03)
- BenchmarkHarness (Spec 11.04)
- INCOMPLETE verdict (Spec 11.05)

## Commands
```bash
./gradlew test --tests "*InterleaveRunner*"
./gradlew test --tests "*Interleave*"
```

## Map
- `src/main/java/dev/samhb/interleave/InterleaveRunner.java` — Strategy enum, Builder, run logic
- `src/main/java/dev/samhb/interleave/Interleave.java` — static quickCheck overloads
- `src/test/java/dev/samhb/interleave/api/InterleaveRunnerTest.java` — library API tests