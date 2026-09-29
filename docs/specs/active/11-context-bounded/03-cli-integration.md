# Spec 11.03 — CLI Integration for Context-Bounded Search

## TL;DR
Add `--strategy CONTEXT_BOUNDED` and `--max-preemptions N` (default: 2) flags to Main CLI. Validate flag combinations: `--max-preemptions` only valid with `CONTEXT_BOUNDED`. Add optional `--iterative-deepening` flag. Warn when K>5 with bitstate.

## Current State
- `Main.java` [verified]: Parses `--strategy DFS|STATIC_POR|DPOR`, `--store exact|bitstate`, `--bitstate-size`, `--bitstate-k`
- Strategy filter passed to `BenchmarkHarness` constructor
- `BenchmarkHarness` runs strategies based on filter

## Invariants
- `--max-preemptions` **only valid with `--strategy CONTEXT_BOUNDED`** — error otherwise
- Default `--max-preemptions 2` (empirical sweet spot per Microsoft CHESS research)
- `--iterative-deepening` off by default (single run at maxPreemptions for benchmark consistency)
- Warning for `--max-preemptions > 5` with `--store bitstate`: "Context bound >5 may increase bitstate memory significantly (O((K+1)×m))."

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

// Warn for high K with bitstate
if (maxPreemptions > 5 && "BITSTATE".equals(storeFilter)) {
    System.err.println("Warning: Context bound >5 may increase bitstate memory significantly (O((K+1)×m)).");
}
```

### BenchmarkHarness Construction
```java
BenchmarkHarness harness = new BenchmarkHarness(bitstateSize, bitstateK, storeFilterSet, strategyFilterSet, maxPreemptions, iterativeDeepening);
```

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
- `cliIterativeDeepening_flagRecognized()`

## Out of Scope
- BenchmarkHarness internal logic (Spec 11.04)
- Explorer implementation (Spec 11.02)
- INCOMPLETE verdict (Spec 11.05)

## Commands
```bash
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --json"
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"
```

## Map
- `src/main/java/dev/samhb/interleave/cli/Main.java` — CLI flags & validation
- `src/test/java/dev/samhb/interleave/cli/MainTest.java` — CLI integration tests