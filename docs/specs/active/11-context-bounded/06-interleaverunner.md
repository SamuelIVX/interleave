# Spec 11.06 — InterleaveRunner Strategy Enum Extension

## TL;DR
Add `CONTEXT_BOUNDED` to the `Strategy` enum in `InterleaveRunner` to support library/API mode usage alongside CLI mode. Track explicitly-set `iterativeDeepening` so a caller mistake on a non-CBS strategy is a loud failure rather than a silent no-op.

## Current State
- `InterleaveRunner` [verified]: Fluent builder API with `Strategy` enum (DFS, STATIC_POR, DPOR)
- `InterleaveRunner.run` [verified, `InterleaveRunner.java:56-70`]: `switch (strategy)` with `case DFS`/`STATIC_POR`/`DPOR` and a `default -> throw new IllegalArgumentException(...)` arm. The `default` arm means adding an enum value does **not** break compilation here, but CBS would hit the throw — a real `case` is required.
- `InterleaveRunner.convertToTestResult` [verified, `:157-173`] and `PartialResult.withTrace` [verified, `:137-149`]: two exhaustive `switch (TraceOutcome)` statements that **will** break when Spec 11.05 adds `INCOMPLETE`
- `createLimitEnforcingVisitor` [verified, `:87-116`]: overrides only single-arg `onStateVisited`
- `Interleave.quickCheck()` / `Interleave.builder()` [verified, `Interleave.java:119-172`]: static entry points
- `TestResult`, `TraceRecord` [verified]: Immutable serializable results

## Invariants
- Library API mirrors CLI capabilities: `CONTEXT_BOUNDED` strategy with preemption bound
- `InterleaveRunner.Builder` accepts `maxPreemptions(int)` and `iterativeDeepening(boolean)` for CBS
- Results return `TestResult` with `TraceRecord` containing INCOMPLETE when applicable
- **The builder default (`iterativeDeepening = false`) must remain valid for every strategy** so that non-CBS callers are unaffected. Only an *explicit* call is a caller error.
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

Note: `Interleave.verify` has its own two exhaustive switch *expressions* over the same enum and **will not compile** until it gains `CONTEXT_BOUNDED` cases — that work is specified in Spec 11.02.

### Builder Extension
Add to `InterleaveRunner.Builder`:
```java
public class Builder {
    private int maxPreemptions = 2;
    private boolean maxPreemptionsExplicit = false;
    private boolean iterativeDeepening = false;
    private boolean iterativeDeepeningExplicit = false;

    public Builder maxPreemptions(int maxPreemptions) {
        if (maxPreemptions < 0) throw new IllegalArgumentException("maxPreemptions must be >= 0");
        this.maxPreemptions = maxPreemptions;
        this.maxPreemptionsExplicit = true;
        return this;
    }

    public Builder iterativeDeepening(boolean iterativeDeepening) {
        this.iterativeDeepening = iterativeDeepening;
        this.iterativeDeepeningExplicit = true;
        return this;
    }

    public InterleaveRunner build() {
        // Validate: maxPreemptions/iterativeDeepening only for CONTEXT_BOUNDED
        if (strategy != Strategy.CONTEXT_BOUNDED
                && (maxPreemptionsExplicit || iterativeDeepeningExplicit)) {
            throw new IllegalStateException(
                "maxPreemptions and iterativeDeepening only valid for CONTEXT_BOUNDED strategy");
        }
        return new InterleaveRunner(this);
    }
}
```

**Why `iterativeDeepeningExplicit` is tracked separately:** the field `iterativeDeepening` defaults to `false` for every strategy, so validating on its value alone would either reject every non-CBS build or accept the mistake. `.strategy(DFS).iterativeDeepening(false).build()` is a caller who explicitly asked for a CBS option on a non-CBS strategy — almost certainly a refactor slip where the strategy was changed and the option was left behind. Failing loudly costs one exception; failing silently costs a run that quietly ignores what the caller asked for. Meanwhile plain `.strategy(DFS).build()` stays valid, so non-CBS callers never have to know about CBS.

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
        lastResult = result;

        String verdict = actualVerdict(result);
        if (verdict.equals("VIOLATION") || verdict.equals("DEADLOCK")) {
            return convertToTestResult(result); // Minimal K trace
        }
    }
    // Return the final iteration's result (already computed) — avoid duplicate search
    return convertToTestResult(lastResult);
}
```

**`freshCopy()` and the outer `store`:** each iteration must get a store that is independent of the others. `store` here is the one already created at the top of `run()` and is itself used for K iteration 0's siblings — a `BitstateStore` copy that resets `maxPreemptions` to its 2-arg default would under-report for K > 2, so `BitstateStore.freshCopy()` must propagate capacity (Spec 11.01 §3). The `stateVisitor` limit enforcement continues to work because the 3-arg `onStateVisited` default delegates to the single-arg form the visitor overrides (Spec 11.01 §1b).

`actualVerdict(result)` here is the library-side helper and does **not** take a `StoreType` — the harness's `cbVerdict` (Spec 11.04) is benchmark-only. Keep the two separate; do not unify them.

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

`quickCheck(Program, int)` is unambiguous against the existing `quickCheck(Program, Supplier<StateStore>)` because `int` is not a reference type, so `quickCheck(p, null)` still resolves to the supplier overload. Note that `quickCheck(Program, int)` does not set `iterativeDeepeningExplicit`, so the builder validation passes.

## Tests

**File:** `src/test/java/dev/samhb/interleave/api/InterleaveRunnerCBTest.java`
- `builder_CONTEXT_BOUNDED_strategyAccepted()`
- `builder_maxPreemptions_setsValue()`
- `builder_iterativeDeepening_setsValue()`
- `builder_rejectsMaxPreemptionsForDFS()` — `.strategy(DFS).maxPreemptions(2).build()` throws `IllegalStateException`
- `builder_rejectsExplicitIterativeDeepeningForDFS()` — `.strategy(DFS).iterativeDeepening(false).build()` throws
- `builder_acceptsDefaultIterativeDeepeningForDFS()` — `.strategy(DFS).build()` succeeds (guards against an
  over-broad validation that would break every existing caller)
- `builder_rejectsNegativeMaxPreemptions()` — throws `IllegalArgumentException` from the setter, not from `build()`
- `quickCheck_CONTEXT_BOUNDED_withMaxPreemptions()`
- `quickCheck_CONTEXT_BOUNDED_iterativeDeepening()`
- `quickCheck_programIntOverload_unambiguousWithSupplierOverload()` — both `quickCheck(p, 2)` and
  `quickCheck(p, supplier)` compile and dispatch correctly
- `run_CONTEXT_BOUNDED_returnsTestResult()`
- `run_CONTEXT_BOUNDED_incompleteVerdictProducesIncompleteTrace()` — requires the Spec 11.05 / 11.07
  `TestResult.incompleteTraces()` plumbing; coordinate ordering with 11.07
- `run_CONTEXT_BOUNDED_respectsMaxStatesLimit()` — the `createLimitEnforcingVisitor` delegation (Spec 11.01 §1b)
- `runIterativeDeepening_eachIterationUsesFreshStore()` — assert a fresh store per K, e.g. by counting
  `BitstateStore.statesMarked()` or by verifying a violation at K=1 is still found when K=2 follows

## Out of Scope
- `Interleave.verify` dispatch and `maxPreemptions` overloads — Spec 11.02
- CLI integration — Spec 11.03
- `BenchmarkHarness` — Spec 11.04
- Verdict propagation (`TraceOutcome.INCOMPLETE`, `TestResult.incompleteTraces`) — Spec 11.05 / 11.07
- `VerificationResult` / `DeltaDebugger` switch sites — Spec 11.07

## Commands
```bash
./gradlew test --tests "*InterleaveRunner*"
./gradlew test --tests "*Interleave*"
```

## Map
- `src/main/java/dev/samhb/interleave/InterleaveRunner.java` — Strategy enum, Builder, run logic; also hosts two `TraceOutcome` switch sites (`:142`, `:164`) updated per Spec 11.07
- `src/main/java/dev/samhb/interleave/Interleave.java` — static quickCheck overloads
- `src/test/java/dev/samhb/interleave/api/InterleaveRunnerCBTest.java` — library API tests
