# Spec 11.05 — INCOMPLETE Verdict & Iterative Deepening

## TL;DR
Add `TraceOutcome.INCOMPLETE` for CBS budget-exceeded runs. Track when paths are pruned due to preemption budget exhaustion, and snapshot the pruned path at the prune site so the emitted INCOMPLETE trace is a real partial schedule. Propagate through the verdict and reporting system. Implement iterative deepening mode that runs K=0,1,2... and stops at first violation.

The enum constant itself, and the `VerificationResult` / `TestResult` propagation it forces, are owned by **Spec 11.07**. This spec defines the explorer-side budget tracking, the verdict derivation (delegating the harness helper to Spec 11.04), and the reporting consumers.

## Current State
- `TraceOutcome` enum [verified, `search/TraceOutcome.java`]: VIOLATION, DEADLOCK, COMPLETED
- `Trace` class [verified, `search/Trace.java:13-23`]: `threadIds`, `outcomes`, `outcome`; constructor **requires** `threadIds.size() == outcomes.size()` and throws `IllegalArgumentException` on mismatch
- `BenchmarkHarness.actualVerdict()` [verified, `:214-223`]: Returns VIOLATION/DEADLOCK/PASS
- `SoundnessAttestation.checkSoundness()` [verified, `:51-117`]: cross-strategy verdict agreement at `:75-85` requires **all** EXACT results for a correct program to report an identical verdict
- `StatesExploredTable` [verified, `:83`]: `String[] strategyOrder = {"DFS", "STATIC_POR", "DPOR"}`
- `ReportWriter` [verified, `:95`]: emits `"verdict": "%s"` per result
- `ContextBoundedExplorer` [spec 11.02]: single prune site where `nextPreemptions > maxPreemptions`

## Invariants
- **INCOMPLETE = budget exceeded, no violation found**: Some paths were pruned due to `nextPreemptions > maxPreemptions` (EXACT store only)
- **PASS = truly exhaustive within bound**: No paths pruned, no violation found (EXACT store only)
- **BITSTATE results are always APPROXIMATE**: Bloom filter false positives may cause spurious pruning. Bitstate **CBS** verdicts are inherently inconclusive and marked `APPROXIMATE_PASS`. (Bitstate DFS/STATIC_POR/DPOR verdicts are unchanged — see Spec 11.04.)
- **The INCOMPLETE trace carries a real schedule.** An INCOMPLETE trace with an empty `threadIds` list is useless to a reader and is exactly the failure mode of reading the path lists after DFS has unwound them.
- **At most one INCOMPLETE trace per top-level search**, and its content is deterministic.
- **Iterative deepening**: Runs K=0,1,2...maxPreemptions; returns immediately on first VIOLATION/DEADLOCK (EXACT store)
- **`INCOMPLETE` and `limitExceeded` are independent.** `InterleaveRunner` already returns partial results with `limitExceeded=true` when `maxStates`/`maxTime` trips ([verified, `InterleaveRunner.java:71-78`]). That flag means "stopped for a resource reason"; INCOMPLETE means "stopped because the preemption bound was reached". A single run may set both. Reporting must surface both rather than collapsing one into the other.
- **SoundnessAttestation** excludes INCOMPLETE and APPROXIMATE_PASS results from cross-strategy agreement, but still replay-validates any CBS VIOLATION.

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

> **This single line breaks compilation at four exhaustive switch sites** (`VerificationResult.java:34`, `InterleaveRunner.java:142`, `InterleaveRunner.java:164`, `DeltaDebugger.java:81`) and adds a trace shape to two result types. All of those are specified in **Spec 11.07**, and they must land as one atomic change or `main` does not compile.

### 2. Trace Factory Method
In `src/main/java/dev/samhb/interleave/search/Trace.java`:
```java
public static Trace incomplete(List<Integer> threadIds, List<StepOutcome> outcomes) {
    return new Trace(threadIds, outcomes, TraceOutcome.INCOMPLETE);
}
```

Both arguments must come from the same snapshot; the constructor rejects mismatched lengths, so a caller that assembles `threadIds` from one point in time and `outcomes` from another will fail at runtime.

### 3. ContextBoundedExplorer Budget Tracking

**Snapshot at the prune site, not at the top level.** `currentThreadIds` and `currentOutcomes` are the live DFS path lists, passed by reference and popped as DFS unwinds. By the time the top-level `explore()` regains control, both are empty, so a trace built from them is a valid-but-useless zero-length INCOMPLETE trace.

```java
private boolean budgetExceeded = false;
private List<Integer> incompleteThreadIds = null;
private List<StepOutcome> incompleteOutcomes = null;

// In each top-level explore() method, reset before search:
public DfsResult explore(Program program, Invariant invariant, StateStore stateStore, StateVisitor stateVisitor, int maxPreemptions) {
    this.budgetExceeded = false;
    this.incompleteThreadIds = null;
    this.incompleteOutcomes = null;
    ...
}

// In dfs(), at the single prune site (Spec 11.02 "Preemption Classification Logic"):
if (nextPreemptions > maxPreemptions) {
    if (!budgetExceeded) {                 // first prune site wins → deterministic
        budgetExceeded = true;
        incompleteThreadIds = List.copyOf(currentThreadIds);
        incompleteOutcomes = List.copyOf(currentOutcomes);
    }
    continue;
}

// After TOP-LEVEL search completes (not per-DFS-invocation):
boolean foundFailure = traces.stream()
    .anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION || t.outcome() == TraceOutcome.DEADLOCK);
if (budgetExceeded && incompleteThreadIds != null && !foundFailure) {
    // Emit ONE run-level INCOMPLETE trace carrying the saved partial schedule
    Trace trace = Trace.incomplete(incompleteThreadIds, incompleteOutcomes);
    traces.add(trace);
    if (stateVisitor != null) stateVisitor.onTraceCreated(trace);
}
```

**"First prune site wins"** is required, not incidental. Snapshotting on every prune site and taking the last would still be deterministic for a fixed search order, but the *first* prune site is the shallowest and cheapest to reason about, and the guard `if (!budgetExceeded)` makes the intent explicit. Either way, the emitted trace must be a function of the search alone — never of iteration order over a hash-based collection.

The `incompleteThreadIds != null` guard is belt-and-braces: `budgetExceeded` is only ever set alongside a snapshot, but if the two ever desynchronized the code should emit nothing rather than an empty trace.

### 4. BenchmarkHarness Verdict Logic
Owned by **Spec 11.04** (`cbVerdict(DfsResult, StoreType)`), not here. The `actualVerdict(DfsResult)` helper is deliberately left untouched so that bitstate DFS/STATIC_POR/DPOR rows keep reporting `PASS`.

### 5. ReportWriter / StatesExploredTable

- `ReportWriter.writeJson()` [verified, `:95`] already emits `result.verdict()` verbatim, so `INCOMPLETE` and `APPROXIMATE_PASS` need no new branch there. Add a test asserting the string round-trips rather than a code change.
- `ReportWriter.writeMarkdown()`: the verdict column must render `INCOMPLETE` and `APPROXIMATE_PASS` as-is; confirm no switch or lookup table maps verdicts to a fixed set.
- **`StatesExploredTable` [verified, `:83`] hardcodes `String[] strategyOrder = {"DFS", "STATIC_POR", "DPOR"}`.** CBS results would be silently dropped from the states-explored table — a benchmark table that quietly omits the strategy you just added. `strategyOrder` must gain `"CONTEXT_BOUNDED"`.
- **Reduction percentages:** `StatesExploredTable` computes percentages against the DFS baseline (`:77-78`). CBS is **not** guaranteed to explore fewer states than DFS — for some programs a bounded search explores strictly more distinct configurations, because the `(config, lastThreadId)` dimension fragments what plain DFS deduplicates. A negative or >100% percentage must be representable rather than producing a nonsensical `"-23%↓"`. Specify the rendering for that case explicitly (e.g. a bare count with no percentage, or `N/A`).

### 6. SoundnessAttestation

`checkSoundness()` [verified, `:75-85`] requires that for a correct (non-`VIOLATION`-expected) program, **all** EXACT results report an identical verdict:

```java
if (expectedVerdict != null && !"VIOLATION".equals(expectedVerdict)) {
    if (correctVerdicts.containsKey(key)) {
        if (!correctVerdicts.get(key).equals(actualVerdict)) {
            return SoundnessCheck.failed("Verdict mismatch for correct program " + key + ...);
        }
    } else {
        correctVerdicts.put(key, actualVerdict);
    }
}
```

**This fails once CBS is added.** `peterson` is `PASS` under exhaustive DFS and `INCOMPLETE` under CBS at K=2, so the attestation reports "Verdict mismatch for correct program peterson: PASS vs INCOMPLETE" and `isSound()` returns `false`. The current spec wording — "Only validate EXACT store with non-INCOMPLETE verdicts for CBS" — does not prevent this, because the failure is a *disagreement between two EXACT results*, not a check against `expectedVerdict`.

Required behavior:
- **Skip, don't merely "not validate".** Results whose verdict is `INCOMPLETE` or `APPROXIMATE_PASS` must be excluded from the `correctVerdicts` agreement loop entirely, so they neither seed nor conflict with the agreed verdict.
- **A CBS `VIOLATION` must still be replay-validated.** The replay loop at `:91-113` runs over **all** results regardless of store type. It must keep covering CBS results — a bounded search that reports a violation has found a real schedule, and that schedule must be genuine. Add this as a positive acceptance criterion, not only an exclusion rule.
- **INCOMPLETE is never a contradiction.** An `INCOMPLETE` result must not be treated as disagreeing with another strategy's `PASS`, and a `PASS` from a bounded CBS run must never be described as exhaustive.
- `SoundnessAttestation.formatMarkdown()` [verified, `:146-151`] hardcodes the success prose "All EXACT programs produced expected verdicts under DFS." That statement remains true (the DFS branch at `:67-73` is unchanged) and needs no edit, but the INCOMPLETE/APPROXIMATE_PASS exclusions should be acknowledged in the surrounding text so a reader does not mistake the pass for coverage of every strategy.

### 7. Iterative Deepening Behavior
- When `--iterative-deepening` flag set: runs K=0,1,2...maxPreemptions
- Stops at first K where VIOLATION or DEADLOCK found (returns minimal-preemption trace)
- If no violation at any K, returns final result (PASS or INCOMPLETE at maxPreemptions)
- Each K iteration uses a fresh StateStore (`storeFactory.get()` in Spec 11.04, `store.freshCopy()` in Spec 11.06). `BitstateStore.freshCopy()` must preserve capacity (Spec 11.01) or K > 2 silently under-reports.
- `APPROXIMATE_PASS` (bitstate) is not a stop condition, same as INCOMPLETE — keep deepening.

## Tests

**File:** `src/test/java/dev/samhb/interleave/report/ReportWriterCBTest.java`
- `writeJson_INCOMPLETE_traceIncluded()`
- `writeJson_APPROXIMATE_PASS_verdictIncluded()`
- `writeMarkdown_INCOMPLETE_verdictColumn()`
- `writeMarkdown_APPROXIMATE_PASS_verdictColumn()`

**File:** `src/test/java/dev/samhb/interleave/report/StatesExploredTableCBTest.java`
- `statesExploredTable_containsContextBoundedRow()` — **guards the hardcoded `strategyOrder` array**
- `statesExploredTable_cbsReductionExceedsBaseline_rendersWithoutNegativeArrow()`

**File:** `src/test/java/dev/samhb/interleave/report/SoundnessAttestationCBTest.java`
- `soundnessAttestation_correctProgram_cbsIncomplete_doesNotFail()` — the real-world case: `peterson`,
  `PASS` under DFS, `INCOMPLETE` under CBS K=2, attestation sound. **Run this through the actual harness**,
  not hand-constructed results — the failure only appears when real CBS verdicts are produced.
- `soundnessAttestation_cbsViolation_replayValidated()` — a CBS VIOLATION whose replayed trace does *not*
  violate the invariant is reported as unsound (i.e. the replay check still covers CBS)
- `soundnessAttestation_cbsApproximatePass_excludedFromAgreement()`

**File:** `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerTest.java` (iterative deepening + budget tests)
- `iterativeDeepening_violationAtK1_stopsAtK1()` — runs K=0,1 returns at K=1
- `iterativeDeepening_noViolation_runsAllK()` — runs K=0,1,2 returns final
- `incompleteTrace_carriesNonEmptySchedule()` — **the regression guard for the snapshot bug**: the
  INCOMPLETE trace's `threadIds` is non-empty and its length equals the `Trace` invariant
  `threadIds.size() == outcomes.size()`
- `incompleteTrace_deterministic_acrossRuns()` — two runs on the same program produce an equal INCOMPLETE
  trace (guards the "first prune site wins" rule against hash-iteration nondeterminism)
- `incompleteTrace_suppressedWhenViolationFound()` — no INCOMPLETE trace emitted alongside a VIOLATION
- `incompleteTrace_absentWhenBudgetNeverExceeded()` — exhaustive-at-K run reports PASS, not INCOMPLETE
- `budgetExceeded_resetBetweenTopLevelSearches()` — a reused explorer instance does not leak INCOMPLETE
  state from a previous run

## Out of Scope
- `TraceOutcome` switch-site updates in `VerificationResult` / `InterleaveRunner` / `DeltaDebugger` —
  Spec 11.07
- `TestResult` / `VerificationResult` field additions — Spec 11.07
- `cbVerdict` implementation — Spec 11.04
- Explorer core — Spec 11.02
- CLI parsing — Spec 11.03
- `InterleaveRunner.runIterativeDeepening` — Spec 11.06
- Visualizer handling of `incompleteTraces` — Spec 11.07

## Commands
```bash
./gradlew test --tests "*ContextBounded*"
./gradlew test --tests "*ReportWriterCB*"
./gradlew test --tests "*StatesExploredTableCB*"
./gradlew test --tests "*SoundnessAttestationCB*"
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --iterative-deepening --json"
```

## Map
- `src/main/java/dev/samhb/interleave/search/TraceOutcome.java` — add INCOMPLETE enum (see Spec 11.07 for the sites this breaks)
- `src/main/java/dev/samhb/interleave/search/Trace.java` — add `incomplete()` factory
- `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java` — budget tracking + prune-site snapshot
- `src/main/java/dev/samhb/interleave/report/BenchmarkHarness.java` — `cbVerdict` (see Spec 11.04)
- `src/main/java/dev/samhb/interleave/report/ReportWriter.java` — verify INCOMPLETE/APPROXIMATE_PASS render
- `src/main/java/dev/samhb/interleave/report/StatesExploredTable.java` — add CONTEXT_BOUNDED to `strategyOrder`; handle non-reduction percentages
- `src/main/java/dev/samhb/interleave/report/SoundnessAttestation.java` — exclude INCOMPLETE/APPROXIMATE_PASS from agreement; keep replay validation
