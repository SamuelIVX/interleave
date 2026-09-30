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

`InterleaveRunner.run` [verified, `InterleaveRunner.java:42-85`] is **not** a switch expression. Its actual
shape is a statement switch inside a `try` block, with a `catch (LimitExceededException)` that returns
partial results flagged `limitExceeded=true`:

```java
public TestResult run(Program program) {
    long start = System.currentTimeMillis();
    Runtime runtime = Runtime.getRuntime();
    runtime.gc();
    long memBefore = runtime.totalMemory() - runtime.freeMemory();

    // Lazy: the existing three strategies each need exactly one store; CBS iterative
    // mode must NOT trigger a factory call here (it obtains one per K itself).
    StateStore[] runStateStore = new StateStore[1];
    Supplier<StateStore> runStore = () -> {
        if (runStateStore[0] == null) runStateStore[0] = stateStoreFactory.get();
        return runStateStore[0];
    };

    LimitState limitState = new LimitState();
    StateVisitor visitor = createLimitEnforcingVisitor(limitState);

    DfsResult result;
    try {
        switch (strategy) {
            case DFS -> { /* unchanged: uses runStore.get() */ }
            case STATIC_POR -> { /* unchanged: uses runStore.get() */ }
            case DPOR -> { /* unchanged: uses runStore.get() */ }
            // NEW: CBS must go through the SAME try block, not around it
            case CONTEXT_BOUNDED -> {
                result = iterativeDeepening
                    ? runIterativeDeepening(program, invariant, visitor)
                    : new ContextBoundedExplorer().explore(
                          program, invariant, runStore.get(), visitor, maxPreemptions);
            }
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    } catch (LimitExceededException e) {
        // unchanged: return partial results including traces
        return convertToTestResult(limitState.partialResult, /* ... */, true);
    }

    return convertToTestResult(result, /* ... */, false);
}
```

> **Do not replace `run()` with a switch expression.** There is no `runExplorer` or `actualVerdict` method
> on `InterleaveRunner` — those live on `BenchmarkHarness` (`BenchmarkHarness.java:193-223`). A
> `return switch (strategy) { … }` rewrite would not compile, and would discard `createLimitEnforcingVisitor`,
> the `try`/`catch`, and the `LimitState` bookkeeping that make `maxStates` / `maxTime` work. The only
> change to `run()` is **one added `case` inside the existing `try`**, plus making the store acquisition
> lazy.

**Why the store acquisition must be lazy.** In iterative mode `runIterativeDeepening` calls
`stateStoreFactory.get()` once per K. An unconditional `stateStoreFactory.get()` at the top of `run()`
would therefore build one store that is never used and never cleared — for `BitstateStore` that is a real
allocation (`maxPreemptions + 1` bit arrays, ~1 MB at the default `bitstateSize`), and a caller-supplied
factory with side effects (pooling, registration, counters) would observe a spurious extra call. Deferring
the acquisition keeps the existing three strategies' behavior identical — each still gets exactly one
store — while making the call count depend only on the path actually taken.

#### Iterative deepening and limits

`runIterativeDeepening` must let `LimitExceededException` propagate to the existing `catch` rather than
catching it locally. Every iteration shares the **same** `visitor`, so a state/time limit that trips at
any K is caught once, by the one handler that already knows how to assemble partial results:

```java
private DfsResult runIterativeDeepening(Program program, Invariant invariant, StateVisitor visitor) {
    DfsResult lastResult = null;
    Set<StateStore> seenStores = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int k = 0; k <= maxPreemptions; k++) {
        int bound = k; // Capture loop variable for lambda
        ContextBoundedExplorer explorer = new ContextBoundedExplorer();
        StateStore freshStore = stateStoreFactory.get();   // fresh per K — see below
        if (!seenStores.add(freshStore)) {
            throw new IllegalStateException(
                "Iterative deepening requires a fresh StateStore per bound K, but "
                + "stateStoreFactory reused an instance. Iterations would share a visited set, so "
                + "each deeper bound would prune states the previous bound already explored. Use "
                + "stateStoreFactory(...) with a supplier returning a new store per call.");
        }
        DfsResult result = explorer.explore(program, invariant, freshStore, visitor, bound);
        lastResult = result;

        if (isFailure(result)) {
            return result; // Minimal K trace
        }
        // INCOMPLETE / APPROXIMATE_PASS at this K → deepen
    }
    return lastResult; // already computed — avoid duplicate search
}

private static boolean isFailure(DfsResult result) {
    return result.traces().stream().anyMatch(t ->
        t.outcome() == TraceOutcome.VIOLATION || t.outcome() == TraceOutcome.DEADLOCK);
}
```

Four points that are easy to get wrong:

1. **`LimitExceededException` propagates.** The helper has no `try`/`catch`. If `maxStates` trips during
   iteration 3 of 5, the exception reaches `run()`'s `catch`, which returns the partial `TestResult` with
   `limitExceeded=true` and the traces recorded so far. Catching locally and continuing to K=4 would
   silently exceed the caller's budget — a limit that does not limit anything. A run that stops early for
   a resource reason must say so rather than deepening past its own budget.
2. **Fresh store per K via `stateStoreFactory.get()`, and reject a shared one.** See below — this is a
   soundness requirement, not just isolation.
3. **`isFailure`, not a verdict string.** `InterleaveRunner` has no `actualVerdict`; the harness's
   `cbVerdict` (Spec 11.04) is benchmark-only and returns a `String` for report labels. The library layer
   needs only a boolean. Keep the two separate — do not unify them, and do not introduce a verdict-string
   helper into the library path.

**Why the store must be genuinely fresh, and shared must throw.** Calling `store.freshCopy()` per K would
work for `HashingStateStore` and `BitstateStore` but throws `UnsupportedOperationException` for any store
that does not override it — including the shared-instance fallback that `Builder.stateStore(...)`
installs at `InterleaveRunner.java:236-243`. Going through the factory reuses the caller's own isolation
policy for **every** K.

The fallback case is not merely wasteful, it is **unsound**: a shared visited set makes each deeper bound
*prune* what the previous bound explored, because `isVisited` is `min ≤ p` and K=0 marks every state at
count 0. Deepening would narrow rather than widen. Hence the identity check over **all** stores used so far —
an alternating `A, B, A, B` factory must be rejected too, or K=2 would inherit K=0's visited set. The set
holds `maxPreemptions + 1` references, so the cost is trivial. Full derivation in Spec 11.01 §5.
`BitstateStore.freshCopy()` must still preserve `maxPreemptions` (Spec 11.01 §4) for the
`stateStore(BitstateStore)` path.

**Interaction with the limit visitor.** The 3-arg `onStateVisited` default delegates to the single-arg form
(Spec 11.01 §1b), and `createLimitEnforcingVisitor` overrides only the single-arg form
(`InterleaveRunner.java:91-116`). Limit enforcement therefore works for CBS with no change to that visitor.
`LimitState.partialResult` accumulates traces via `onTraceCreated`; its `switch (record.outcome())` at
`:142` is one of the four sites Spec 11.07 updates.


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
- `run_CONTEXT_BOUNDED_staysInsideLimitEnforcement()` — **guards the `run()` rewrite**: CBS must not bypass
  `createLimitEnforcingVisitor` or the `try`/`catch`
- `run_CONTEXT_BOUNDED_maxStates_returnsPartialWithLimitExceeded()` — a limit trips mid-deepening and
  yields `limitExceeded=true` with partial traces, not an escaping `LimitExceededException`
- `runIterativeDeepening_doesNotDeepenPastLimit()` — a `maxStates` budget is a **total** budget across all
  K iterations, not per-iteration. Use **distinct** stores per K (a shared store throws, per
  `sharedStoreFactory_throwsIllegalState()` below, so it cannot be what exercises this). The budget is
  cumulative because every iteration receives the **same** `visitor` from `run()`, and
  `createLimitEnforcingVisitor` holds one `long[] stateCount` counter that is never reset between
  iterations. Assert the total across iterations is capped at `maxStates`, not each iteration independently
- `runIterativeDeepening_budgetIsCumulativeAcrossBounds()` — a sharper form of the above: K=0 alone does not
  consume the whole allowance, so the run continues to K=1 and *then* trips, returning
  `limitExceeded=true` with partial traces
- `runIterativeDeepening_storeFactoryUsedPerIteration()` — assert the factory is invoked once per K, so
  iterations cannot contaminate each other's visited set
- `runIterativeDeepening_sharedStoreFactory_throwsIllegalState()` — a store that does not override
  `freshCopy()` (so `.stateStore(x)` installs the shared-instance fallback) must **throw** under iterative
  deepening, not run narrowing. This is the soundness guard, and it is the opposite of the old expectation
  that such a store would "just work"
- `runIterativeDeepening_alternatingStoreFactory_throwsIllegalState()` — a factory returning `A, B, A, B …`
  must also be rejected; the check covers every store used, not only the last one
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
