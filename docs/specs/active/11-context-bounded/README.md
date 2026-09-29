# Spec 11 — Context-Bounded Search (CHESS-Style) — Spec Set Overview

This directory contains the decomposed specification for implementing Context-Bounded Search (CBS) as the 4th exploration strategy in interleave.

## Spec Set Structure

| Spec | Title | Scope |
|------|-------|-------|
| [01-statestore-extension.md](01-statestore-extension.md) | StateStore Preemption-Aware Extension | Interface + HashingStateStore + BitstateStore |
| [02-explorer-core.md](02-explorer-core.md) | ContextBoundedExplorer Core Algorithm | CHESS-style preemption classification, cost-aware DFS |
| [03-cli-integration.md](03-cli-integration.md) | CLI Integration | `--strategy CONTEXT_BOUNDED`, `--max-preemptions`, `--iterative-deepening` |
| [04-benchmark-harness.md](04-benchmark-harness.md) | BenchmarkHarness Integration | Strategy registration, iterative deepening helper, verdict validation |
| [05-incomplete-verdict.md](05-incomplete-verdict.md) | INCOMPLETE Verdict & Iterative Deepening | `TraceOutcome.INCOMPLETE`, budget tracking, report propagation |
| [06-interleaverunner.md](06-interleaverunner.md) | InterleaveRunner Strategy Enum | Library API: Strategy enum, Builder, quickCheck overloads |

**Tests are co-located in each spec** — no separate test spec.

## Dependency Graph

```
01-statestore-extension
    ↓
02-explorer-core
    ↓
03-cli-integration  04-benchmark-harness  06-interleaverunner
    ↓                    ↓                      ↓
05-incomplete-verdict ←─── (shared) ──────────→
```

## Implementation Order

1. **01-statestore-extension** — Foundation: StateStore interface + implementations
2. **02-explorer-core** — Core algorithm using new StateStore methods
3. **03-cli-integration** / **04-benchmark-harness** / **06-interleaverunner** — Can be parallelized after #2
4. **05-incomplete-verdict** — Requires #2, #4, #6 for full propagation

## Skills Required

| Skill | Purpose |
|-------|---------|
| **implement** | Core implementation workflow (spec-driven, test-first) |
| **tdd** | Test-driven development for each component |
| **codebase-design** | Deep module vocabulary for explorer/StateStore integration |
| **refactor** | Safe refactoring of StateStore interface and existing explorers |
| **diagnosing-bugs** | If preemption classification has edge cases |
| **code-review** | Review implementation against spec invariants |

## MCPs Required

| MCP | Purpose |
|-----|---------|
| **GitHub MCP** | Creating PR, checking CI status, reviewing diffs |
| **Sequential Thinking MCP** | Working through preemption classification logic and visited semantics |

## Key Architectural Decisions

### Already Resolved
- ✅ Cost-aware hashing: `state|pcs|lastThreadId|preemptions` key (no separate `BoundedStateStore` needed)
- ✅ Free vs paid preemption: `lastStillEnabled` check at each config
- ✅ Step granularity = step boundaries (preemption only between steps)
- ✅ DPOR/CBS isolation: separate strategies, no Bounded-DPOR
- ✅ INCOMPLETE verdict for budget-exceeded runs

### CLI Interface
```bash
# Single run at K=2
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --json"

# Iterative deepening (K=0→1→2, stops on first violation)
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 3 --iterative-deepening --json"

# Corpus run
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"
```

### Library API
```java
// Static entry points
TestResult result = Interleave.quickCheck(program, Strategy.CONTEXT_BOUNDED);
TestResult result = Interleave.quickCheck(program, 2); // K=2
TestResult result = Interleave.quickCheck(program, 2, true); // K=2 iterative

// Builder
InterleaveRunner runner = InterleaveRunner.builder()
    .strategy(Strategy.CONTEXT_BOUNDED)
    .maxPreemptions(2)
    .iterativeDeepening(false)
    .build();
```

## Verification Checklist

After full implementation:
- [ ] `./gradlew test` — all tests pass (143+ new tests)
- [ ] `./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --json"` — works
- [ ] `./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"` — corpus runs
- [ ] `./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --iterative-deepening --json"` — iterative mode
- [ ] Benchmark table includes CONTEXT_BOUNDED column with states explored
- [ ] INCOMPLETE verdict appears for budget-exceeded runs
- [ ] SoundnessAttestation passes (excludes INCOMPLETE CBS)
- [ ] Library API: `InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).build().run(program)` works