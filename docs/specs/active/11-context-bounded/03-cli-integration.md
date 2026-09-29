# Spec 11.03 — CLI Integration for Context-Bounded Search

## TL;DR
Add `--strategy CONTEXT_BOUNDED` and `--max-preemptions N` (default: 2) flags to Main CLI. Validate flag combinations: `--max-preemptions` only valid with `CONTEXT_BOUNDED`. Add optional `--iterative-deepening` flag. Warn when K>5 on any run that actually uses bitstate.

## Current State
- `Main.java` [verified, `cli/Main.java:65-86`]: parses `--store exact|bitstate` into an uppercased `storeFilter` string, and `--strategy DFS|STATIC_POR|DPOR` into `strategyFilter`
- `storeFilter` is **`null` when `--store` is omitted** (`Main.java:48`) and is turned into a filter set at `Main.java:169-170`
- `BenchmarkHarness.runProgram` [verified, `report/BenchmarkHarness.java:95-96`]: `runExact = storeFilter == null || ...`, `runBitstate = storeFilter == null || ...` — so a `null` filter means **both** store types run
- `--help` text is at `Main.java:268-269`
- Strategy filter is passed to `BenchmarkHarness` constructor

## Invariants
- `--max-preemptions` **only valid with `--strategy CONTEXT_BOUNDED`** — error otherwise
- Default `--max-preemptions 2` (empirical sweet spot per Microsoft CHESS research)
- `--iterative-deepening` off by default (single run at maxPreemptions for benchmark consistency)
- Warning for `--max-preemptions > 5` on any run that includes bitstate: "Context bound >5 may increase bitstate memory significantly (O((K+1)×m))."
- **`storeFilter == null` means both stores run.** Any warning gated on bitstate must test for `null` as well as for the literal `"BITSTATE"`.

## Acceptance Criteria

### Main.java Argument Parsing
Add to switch statement:
```java
int maxPreemptions = 2;
boolean maxPreemptionsExplicit = false;
boolean iterativeDeepening = false;

case "--max-preemptions" -> {
    if (i + 1 >= args.length) { error("Error: --max-preemptions requires a non-negative integer"); System.exit(1); }
    try {
        maxPreemptions = Integer.parseInt(args[i + 1]);
        if (maxPreemptions < 0) throw new NumberFormatException();
        maxPreemptionsExplicit = true;
    } catch (NumberFormatException e) {
        System.err.println("Error: --max-preemptions must be a non-negative integer");
        System.exit(1);
    }
    i += 2;
}

case "--iterative-deepening" -> {
    iterativeDeepening = true;
    i++;
}
```

### Strategy Validation
```java
if (!"DFS".equals(s) && !"STATIC_POR".equals(s) && !"DPOR".equals(s) && !"CONTEXT_BOUNDED".equals(s)) {
    System.err.println("Error: --strategy must be 'DFS', 'STATIC_POR', 'DPOR', or 'CONTEXT_BOUNDED'");
    System.exit(1);
}
```

The message must stay in the style of the existing one at `Main.java:84` (single-quoted values, comma-separated, "must be" phrasing).

### Flag Combination Validation
```java
// --max-preemptions only with CONTEXT_BOUNDED (only if explicitly provided)
if (maxPreemptionsExplicit && !"CONTEXT_BOUNDED".equals(strategyFilter)) {
    System.err.println("Error: --max-preemptions only valid with --strategy CONTEXT_BOUNDED");
    System.exit(1);
}

// --iterative-deepening only with CONTEXT_BOUNDED
if (iterativeDeepening && !"CONTEXT_BOUNDED".equals(strategyFilter)) {
    System.err.println("Error: --iterative-deepening only valid with --strategy CONTEXT_BOUNDED");
    System.exit(1);
}

// Warn for high K on any run that includes bitstate.
// storeFilter is null when --store is omitted, and a null filter means BOTH
// store types run (BenchmarkHarness.runProgram:95-96) — so a null filter
// includes bitstate and must trigger this warning too.
boolean runsBitstate = storeFilter == null || "BITSTATE".equals(storeFilter);
if (maxPreemptions > 5 && runsBitstate) {
    System.err.println("Warning: Context bound >5 may increase bitstate memory significantly (O((K+1)×m)).");
}
```

**Why the `null` case matters:** gating on `"BITSTATE".equals(storeFilter)` alone means the warning can *never* fire on a default run, which is the most common invocation. A user running `--all --strategy CONTEXT_BOUNDED --max-preemptions 10` with no `--store` gets the O((K+1)×m) memory blowup and no warning. The `maxPreemptions > 5` threshold is unchanged.

### BenchmarkHarness Construction
```java
BenchmarkHarness harness = new BenchmarkHarness(bitstateSize, bitstateK, storeFilterSet, strategyFilterSet, maxPreemptions, iterativeDeepening);
```

### Help Text
`Main.java:268-269` must be updated:
- `--strategy` row lists `DFS|STATIC_POR|DPOR|CONTEXT_BOUNDED`
- new rows for `--max-preemptions N` and `--iterative-deepening`
- both new rows must note they apply only to `CONTEXT_BOUNDED`

### Usage Examples
```bash
# Single run at K=2
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --json"

# Iterative deepening (K=0,1,2... stops on first violation)
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 3 --iterative-deepening --json"

# Corpus run
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"
```

## Tests

**File:** `src/test/java/dev/samhb/interleave/cli/MainTest.java`
- `cli_CONTEXT_BOUNDED_strategyRuns()`
- `cliRejects_maxPreemptionsWithDFS()` — `--max-preemptions 2 --strategy DFS` exits error
- `cliRejects_maxPreemptionsWithDPOR()` — `--max-preemptions 2 --strategy DPOR` exits error
- `cliRejects_maxPreemptionsWithStaticPOR()` — `--max-preemptions 2 --strategy STATIC_POR` exits error
- `cliWarns_highKWithBitstate()` — `--max-preemptions 10 --store bitstate --strategy CONTEXT_BOUNDED` prints warning
- **`cliWarns_highKWithDefaultStore()`** — `--max-preemptions 10 --strategy CONTEXT_BOUNDED` (no `--store`)
  prints the warning. **Regression guard for the null-`storeFilter` path**; the `cliWarns_highKWithBitstate`
  case passes even with the broken condition, so this is the test that actually pins the behavior.
- `cliNoWarn_highKWithExactStoreOnly()` — `--max-preemptions 10 --store exact --strategy CONTEXT_BOUNDED`
  does **not** warn
- `cliNoWarn_boundAtThresholdWithBitstate()` — `--max-preemptions 5 --store bitstate --strategy CONTEXT_BOUNDED`
  does **not** warn (the threshold is strictly `> 5`, so K=5 is silent). `--strategy CONTEXT_BOUNDED` is
  required here: without it the flag-combination validation exits with an error before the warning check
  is ever reached, and the test would pass for the wrong reason.
- `cliRejects_maxPreemptionsNegative()` — `--max-preemptions -1` exits error
- `cliRejects_maxPreemptionsNonNumeric()`
- `cliRejects_maxPreemptionsMissingValue()`
- `cliRejects_iterativeDeepeningWithDFS()`
- `cliIterativeDeepening_flagRecognized()`
- `cliHelp_listsContextBoundedFlags()` — `--help` mentions `CONTEXT_BOUNDED`, `--max-preemptions`, `--iterative-deepening`
- `cliDefault_maxPreemptionsIsTwo()` — omitting the flag yields K=2 behavior

## Out of Scope
- `BenchmarkHarness` internal logic — Spec 11.04
- Explorer implementation — Spec 11.02
- Verdict propagation (`INCOMPLETE`) — Spec 11.05
- Library API (`InterleaveRunner.Builder` validation) — Spec 11.06
- `TraceOutcome` / `VerificationResult` / `TestResult` switch-site updates — Spec 11.07

## Commands
```bash
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --json"
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"
```

## Map
- `src/main/java/dev/samhb/interleave/cli/Main.java` — CLI flags, validation, help text
- `src/test/java/dev/samhb/interleave/cli/MainTest.java` — CLI integration tests
