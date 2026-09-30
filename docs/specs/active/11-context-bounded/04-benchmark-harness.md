# Spec 11.04 — BenchmarkHarness Integration

## TL;DR
Integrate ContextBoundedExplorer into BenchmarkHarness as a 4th strategy alongside DFS, STATIC_POR, DPOR. Supports both single-run and iterative-deepening modes. Introduces a **separate** `cbVerdict` helper so bitstate verdicts for the three existing strategies are unchanged. Handles INCOMPLETE verdict propagation (Spec 11.05).

## Current State
- `BenchmarkHarness` [verified, `report/BenchmarkHarness.java:24-242`]: Runs all programs with 3 strategies × 2 store types = 6 results/program
- `runProgramWithStore()` [verified, `:123-168`]: Creates explorers, runs with timing/memory measurement
- `actualVerdict()` [verified, `:214-223`]: `private static String actualVerdict(DfsResult result)` — **single-argument**, returns `VIOLATION`/`DEADLOCK`/`PASS`. Called at `:137` (DFS), `:152` (STATIC_POR), `:162` (DPOR)
- `findFailingTrace()` [verified, `:231-236`]
- Bitstate store factory [verified, `:105-106`]: `() -> new BitstateStore(bitstateSize, bitstateK)` — has no knowledge of the preemption bound
- `createResult()` [verified, `:170-183`]: branches on `store instanceof BitstateStore bs` to attach Bloom metrics
- `StoreType` enum: EXACT, BITSTATE
- Verdict validation against expected [verified, `:138-142`]: EXACT only

## Invariants
- CBS runs as **separate strategy** — not mixed with DPOR
- `maxPreemptions` and `iterativeDeepening` passed from CLI via BenchmarkHarness constructor
- CBS respects `--store exact|bitstate` filter like other strategies
- **Bitstate verdicts for DFS/STATIC_POR/DPOR remain `"PASS"`.** Only CBS bitstate runs report `APPROXIMATE_PASS`. This is deliberate: changing the existing strategies' verdicts would alter shipped report output and the `BenchmarkHarnessTest` attestation filter for no benefit to this feature.
- CBS verdict validation: only validate exact-store CBS verdicts against expected when not INCOMPLETE
- Bitstate stores are constructed with capacity `maxPreemptions` so the Spec 11.01 §4 assertion passes without throwing

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

The two existing constructors are retained and delegate with `maxPreemptions = 2`, `iterativeDeepening = false`, so no existing caller changes.

### Bitstate Store Factory Must Carry the Bound
[verified `BenchmarkHarness.java:105-106`] currently builds `new BitstateStore(bitstateSize, bitstateK)`, which defaults to capacity 2. With `--max-preemptions 3` that store cannot represent P=3 and the Spec 11.01 §4 assertion in the explorer would fire on every run. Change to:
```java
results.addAll(runProgramWithStore(program, invariant,
    () -> new BitstateStore(bitstateSize, bitstateK, maxPreemptions), StoreType.BITSTATE));
```

### runProgramWithStore Integration
```java
boolean runCb = strategyFilter == null || strategyFilter.contains("CONTEXT_BOUNDED");
if (runCb) {
    DfsResultWithTiming cbResult;
    StateStore cbUsedStore;   // the store that actually produced cbResult

    if (iterativeDeepening) {
        CbRun run = runExplorerWithIterativeDeepening(program, invariant, storeFactory, storeType, maxPreemptions);
        cbResult = run.result();
        cbUsedStore = run.store();
    } else {
        ContextBoundedExplorer cbExplorer = new ContextBoundedExplorer();
        StateStore cbStore = storeFactory.get();
        cbUsedStore = cbStore;
        cbResult = runExplorer(() -> cbExplorer.explore(program.program(), invariant, cbStore, null, maxPreemptions));
    }

    String cbVerdict = cbVerdict(cbResult.result(), storeType);
    Trace cbFailing = findFailingTrace(cbResult.result());
    results.add(createResult("CONTEXT_BOUNDED", program.name(), cbResult, cbVerdict, cbFailing, storeType, cbUsedStore));
}
```

`storeType` is already a parameter of `runProgramWithStore` (`:125`), so it is in scope at the CBS block.

**The store passed to `createResult` must be the one that produced the result.** `createResult` (`:170-183`) branches on `store instanceof BitstateStore bs` and attaches `bs.estimatedFalsePositiveRate()`, `bs.bitCount()`, and `bs.bitDensity()`. Hoisting `cbStore` above the branch — as an earlier draft did — means the iterative-deepening path hands `createResult` a store that was allocated but never searched, so every Bloom metric in the report reads `0.0` while the run actually explored a populated filter. The DFS/POR/DPOR blocks (`:135`, `:149`, `:159`) do not have this hazard because each allocates its store and immediately searches with it; the CBS block has two paths and must thread the used store explicitly.

### Verdict Helpers — `cbVerdict`, Not a Changed `actualVerdict`

`actualVerdict(DfsResult)` is called at three existing sites (`:137`, `:152`, `:162`). **Those three calls must keep the single-argument form and must keep returning `"PASS"` for bitstate.** Changing the shared helper to take a `StoreType` and return `APPROXIMATE_PASS` for any bitstate result would relabel every existing bitstate row in every report and pull all of them into the `BenchmarkHarnessTest` attestation filter (`:25`, `!"PASS".equals(r.verdict())`).

Add a separate helper used only by CBS:
```java
/**
 * Derives the verdict for a CBS run.
 *
 * <p>Distinct from {@link #actualVerdict} so that bitstate DFS/STATIC_POR/DPOR
 * results keep their existing "PASS" verdict. CBS bitstate runs report
 * APPROXIMATE_PASS because Bloom-filter false positives can prune a real
 * state, making any "no violation found" result inconclusive.
 */
private static String cbVerdict(DfsResult result, StoreType storeType) {
    boolean hasViolation = result.traces().stream()
        .anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
    boolean hasDeadlock = result.traces().stream()
        .anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK);
    boolean hasIncomplete = result.traces().stream()
        .anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE);

    if (hasViolation) return "VIOLATION";
    if (hasDeadlock) return "DEADLOCK";
    if (storeType == StoreType.BITSTATE) return "APPROXIMATE_PASS";
    if (hasIncomplete) return "INCOMPLETE";
    return "PASS";
}
```

**Ordering matters:** VIOLATION and DEADLOCK are checked before the BITSTATE branch. A bitstate run that *did* find a real violation reports `VIOLATION` (and the failing trace is replay-validated by `SoundnessAttestation`), not `APPROXIMATE_PASS`. `APPROXIMATE_PASS` means only "found nothing, and that finding is not trustworthy."

### Iterative Deepening Helper
```java
/** A CBS run together with the store that actually produced it. */
private record CbRun(DfsResultWithTiming result, StateStore store) {}

private CbRun runExplorerWithIterativeDeepening(BenchmarkProgram program, Invariant invariant,
                                                java.util.function.Supplier<StateStore> storeFactory,
                                                StoreType storeType,
                                                int maxPreemptions) {
    CbRun last = null;
    Set<StateStore> seenStores = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int k = 0; k <= maxPreemptions; k++) {
        int bound = k; // Capture loop variable for lambda
        ContextBoundedExplorer explorer = new ContextBoundedExplorer();
        StateStore store = storeFactory.get();
        if (!seenStores.add(store)) {
            throw new IllegalStateException(
                "Iterative deepening requires a fresh StateStore per bound K, but storeFactory "
                + "reused an instance. Iterations would share a visited set, so each deeper bound "
                + "would prune states the previous bound already explored.");
        }
        DfsResultWithTiming result = runExplorer(() -> explorer.explore(program.program(), invariant, store, null, bound));
        last = new CbRun(result, store); // Retain result AND the store that produced it

        String verdict = cbVerdict(result.result(), storeType);
        if (verdict.equals("VIOLATION") || verdict.equals("DEADLOCK")) {
            return last; // Stop at first violation (minimal K trace)
        }
        // If INCOMPLETE or APPROXIMATE_PASS at this K, continue to next K
    }
    // Return the final iteration's run (already computed) — avoid duplicate search
    return last;
}
```

The helper returns the **store alongside the result** because it is the only place that knows which K
iteration won and therefore which store's Bloom metrics are meaningful. Returning just the
`DfsResultWithTiming` would force the caller to guess, and the natural guess (the pre-branch `cbStore`) is
wrong — see the note in §"runProgramWithStore Integration".

`storeType` is a **parameter, not a captured field** — it varies per store invocation (`runProgram` calls `runProgramWithStore` once for EXACT and once for BITSTATE), and `BenchmarkHarness` itself has no `storeType` field to capture. Each iteration requests a fresh store from `storeFactory`, so K iterations do not share visited state.

The **identity check** is required, not defensive. A `storeFactory` returning a shared instance would make
deepening *narrow*: `isVisited` is `min ≤ p`, so states K=0 marked at count 0 would be pruned at K=1, and
every deeper bound would cover strictly less than the one before it — reporting a `PASS` at K=5 from less
search than K=0 performed. The harness builds its own `storeFactory` per store type (`:105-106`), so this
is reachable only if that wiring regresses, which is exactly when a loud exception beats a
plausible-looking wrong number. Same rule and rationale as the library path (Spec 11.06 §3, derivation in
Spec 11.01 §5).

The set must be identity-based and cover **all** previously used stores, not just the last one — a factory
alternating `A, B, A, B` would otherwise let K=2 inherit K=0's visited set. See Spec 11.01 §5.

### Verdict Validation
```java
// In runProgramWithStore after getting the CBS verdict:
String expectedVerdict = program.expectedVerdict();
if (expectedVerdict != null && storeType == StoreType.EXACT) {
    if (!expectedVerdict.equals(cbVerdict) && !cbVerdict.equals("INCOMPLETE")) {
        throw new IllegalStateException("Expected verdict " + expectedVerdict + " for " + program.name() + " but got " + cbVerdict);
    }
}
// BITSTATE verdicts are always APPROXIMATE — no validation against expected
```

An `INCOMPLETE` CBS verdict is never a validation failure: a correct program that needs more than K preemptions is *expected* to be INCOMPLETE, not wrong. A CBS `VIOLATION` on a correct program still fails validation, as it should.

## Existing Tests That Must Change

- **`BenchmarkHarnessTest.runProgram_includesBothStoreTypes` [`:71`]: asserts exactly 6 results per program.** Adding a 4th strategy makes it 8. Update the assertion and the per-strategy loop at `:74-79`, which enumerates `{"DFS","STATIC_POR","DPOR"}` and asserts both store types per strategy.
- **`BenchmarkHarnessTest.benchmarkHarness_runsAllPrograms` [`:16`]: asserts `results.size() >= 4`.** Still passes; leave it.
- **`BenchmarkHarnessTest.benchmarkHarness_producesSameVerdictAcrossStrategies` [`:24-25`]: filters `r.failingTrace().isPresent() || !"PASS".equals(r.verdict())`.** This is the regression guard for the `cbVerdict` decision: if `actualVerdict` had been changed globally, every bitstate row would stop matching `"PASS"` and all of them would flow into the `SoundnessAttestation` under construction. Confirm this test's inputs are unchanged by this spec; add a comment noting it guards the scoping decision.
- **`SoundnessAttestation` cross-strategy agreement** — see Spec 11.05 §6. CBS `INCOMPLETE` on a correct program would fail the attestation if not excluded. This spec must not attempt to fix it; Spec 11.05 owns it.

## Tests

**File:** `src/test/java/dev/samhb/interleave/report/BenchmarkHarnessCBTest.java`
- `runProgram_CONTEXT_BOUNDED_includedInResults()` — 8 results per program with no filters
- `runProgram_contextBounded_exactAndBitstateVariants()` — both store types present
- `runProgram_iterativeDeepening_returnsMinimalKTrace()`
- `actualVerdict_bitstateStrategies_remainPass()` — **guard for the `cbVerdict` scoping decision**;
  asserts DFS/STATIC_POR/DPOR bitstate rows still report `PASS`
- `cbVerdict_bitstate_returnsApproximatePass()` — CBS bitstate row reports `APPROXIMATE_PASS`
- `cbVerdict_bitstateWithRealViolation_returnsViolation()` — VIOLATION wins over APPROXIMATE_PASS
- `cbVerdict_exactBudgetExceeded_returnsIncomplete()`
- `bitstateStore_constructedWithMaxPreemptionsCapacity()` — no capacity assertion is thrown for K>2
- `iterativeDeepening_bloomMetrics_comeFromTheWinningStore()` — **regression guard for the `CbRun`
  store threading.** Run iterative deepening on a bitstate store and assert the reported
  `bitDensity()` / `estimatedFalsePositiveRate()` are non-zero and match the store that produced the
  returned iteration. A `0.0` density here means `createResult` was handed an unsearched store.
- `runProgram_CBS_skippedWhenStrategyFilterExcludes()`
- `runExplorerWithIterativeDeepening_sharedStoreFactory_throwsIllegalState()` — the per-K identity check
  fires; without it, deepening narrows instead of widening (Spec 11.01 §5)
- `runExplorerWithIterativeDeepening_exploresWiderAtHigherK()` — with a real per-K store, states explored at
  K=2 is ≥ K=1. The positive counterpart that proves the check is guarding a real property

## 7. Recording the Preemption Bound in Reports

`BenchmarkResult` [verified, `report/BenchmarkResult.java:10-20`] has no field for the preemption bound. Its
constructor overloads take `strategy`, `bugName`, `statesExplored`, `wallTimeMs`, `heapDeltaBytes`,
`verdict`, `failingTrace`, `storeType`, and the three Bloom metrics — nothing recording which K produced the
row.

Without it, a `CONTEXT_BOUNDED` row shows a states-explored count with no way to tell whether it came from
K=1 or K=5. Since the bound *is* what determines completeness, a reader cannot judge the result or
reproduce the run. So this is specified here rather than deferred: it is small, and a bounded-search
report that hides its bound is close to worse than no report at all.

**`BenchmarkResult` changes:**
- new field `private final Integer preemptionsUsed;` — boxed so `null` is meaningful and distinct from
  `0` (K=0 is a legal bound, so a primitive `int` cannot express "not applicable")
- new accessor `public Integer preemptionsUsed()`
- one new constructor overload carrying the extra trailing `Integer preemptionsUsed` parameter
- the four existing constructors delegate with `preemptionsUsed = null`, so **no existing caller changes**
  and existing reports are byte-identical

**Threading the value.** `runProgramWithStore` builds the CBS result, so the bound it used is in scope
there. `createResult` gains a trailing `Integer preemptionsUsed` parameter; every existing call site passes
`null` except the CBS ones, which pass the K that produced the row. For iterative deepening this is the K
that actually returned the winning result (the minimal K with a failure, or `maxPreemptions` if the search
exhausted the bound) — **not** the configured ceiling, since reporting the ceiling when the search stopped
early at K=1 would misstate what was actually explored.

**Rendering** — `ReportWriter.writeJson()` (`:95`) emits `"preemptionsUsed": null` for non-CBS rows so the
key stays present and consumers do not need special-casing, and the integer on CBS rows.
`StatesExploredTable` appends a `K` column showing the bound on CBS rows and `—` elsewhere; the value is
not meaningful for a strategy that has no bound, and printing `0` would falsely imply one.

**Tests:**
- `benchmarkResult_preemptionsUsed_defaultsToNull()` — existing constructors unchanged
- `cbsResult_recordsPreemptionsUsed()` — a CBS run carries its K
- `iterativeDeepening_reportsKThatProducedResult()` — stops at the minimal failing K, not the ceiling
- `writeJson_nonCbsRow_emitsNullPreemptionsUsed()` — key present, value null
- `statesExploredTable_cbsRow_showsK_nonCbsRow_showsDash()`

## Out of Scope
- Verdict propagation into `TraceOutcome.INCOMPLETE` (enum + switch sites) — Spec 11.05 / 11.07
- Explorer core — Spec 11.02
- CLI parsing — Spec 11.03
- `InterleaveRunner` — Spec 11.06
- `ReportWriter` / `StatesExploredTable` / `SoundnessAttestation` changes — Spec 11.05

## Commands
```bash
./gradlew test --tests "*BenchmarkHarness*"
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"
```

## Map
- `src/main/java/dev/samhb/interleave/report/BenchmarkHarness.java` — constructors, store factory, strategy integration, `cbVerdict`, iterative deepening helper
- `src/test/java/dev/samhb/interleave/report/BenchmarkHarnessTest.java` — existing result-count assertions to update
- `src/test/java/dev/samhb/interleave/report/BenchmarkHarnessCBTest.java` — CBS integration tests
