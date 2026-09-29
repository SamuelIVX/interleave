# Spec 11 — Context-Bounded Search (CHESS-Style) — Spec Set Overview

This directory contains the decomposed specification for implementing Context-Bounded Search (CBS) as the 4th exploration strategy in interleave.

**Status: specified, not implemented.** Nothing in `src/` has changed. The CLI flags, library API, and
benchmark columns described here do not exist in the current build. See [`../../../../README.md`](../../../../README.md)
for the user-facing "Planned" note.

## Spec Set Structure

| Spec | Title | Scope |
|------|-------|-------|
| [01-statestore-extension.md](01-statestore-extension.md) | StateStore Preemption-Aware Extension | Interface + HashingStateStore (O(1) min-preemption map) + BitstateStore capacity contract |
| [02-explorer-core.md](02-explorer-core.md) | ContextBoundedExplorer Core Algorithm | CHESS-style preemption classification, cost-aware DFS, `Interleave.verify` integration |
| [03-cli-integration.md](03-cli-integration.md) | CLI Integration | `--strategy CONTEXT_BOUNDED`, `--max-preemptions`, `--iterative-deepening`, help text |
| [04-benchmark-harness.md](04-benchmark-harness.md) | BenchmarkHarness Integration | Strategy registration, `cbVerdict`, iterative deepening helper, store-capacity wiring |
| [05-incomplete-verdict.md](05-incomplete-verdict.md) | INCOMPLETE Verdict & Iterative Deepening | Budget tracking, prune-site snapshot, `SoundnessAttestation` / `StatesExploredTable` |
| [06-interleaverunner.md](06-interleaverunner.md) | InterleaveRunner Strategy Enum | Library API: Strategy enum, Builder, `quickCheck` overloads |
| [07-unchanged-paths.md](07-unchanged-paths.md) | Propagation into Existing Code | The 6 switch sites the new enum values break, reporting/test fallout, visualizer, build order |

**Tests are co-located in each spec** — no separate test spec.

## Dependency Graph

```
01-statestore-extension
        ↓
05 §1-2 (TraceOutcome.INCOMPLETE)
        ↓
07 §1-2 (TraceOutcome switch sites + result buckets)  ── one atomic commit
        ↓
02-explorer-core  +  07 §3 (Strategy.CONTEXT_BOUNDED + its switch sites)
        ↓                                        ── one atomic commit
03-cli-integration    04-benchmark-harness    06-interleaverunner
        ↓                    ↓                       ↓
05 §3-7 (explorer budget tracking + reporting)  →  07 §6 (visualizer)
```

## Implementation Order (compile-green)

The set must be implemented in this order. It is **not** the same as the reading order, and getting it
wrong leaves `main` uncompilable. The order is driven by the fact that the two enum additions have
different dependency shapes: `TraceOutcome.INCOMPLETE` needs only new switch buckets, while
`Strategy.CONTEXT_BOUNDED` needs `ContextBoundedExplorer` to already exist.

1. **01-statestore-extension** — Foundation: `StateStore` interface + both implementations. No enum change; compiles standalone.
2. **05 §1–2 + 07 §1–2** — `TraceOutcome.INCOMPLETE`, `Trace.incomplete`, the four `TraceOutcome` switch
   sites (`VerificationResult:34`, `InterleaveRunner:142`, `InterleaveRunner:164`, `DeltaDebugger:81`),
   and the `TestResult` / `VerificationResult` field additions. **One atomic commit** — the four
   switches do not compile without the constant.
3. **02-explorer-core + 07 §3** — `ContextBoundedExplorer`, the `CONTEXT_BOUNDED` enum constant, and its
   three switch sites (`Interleave:58`, `Interleave:88`, `InterleaveRunner:56`) **together**. The
   explorer needs step 2's `Trace.incomplete`; the constant needs the explorer's class. Splitting these
   does not compile in either direction.
4. **03-cli-integration / 04-benchmark-harness / 06-interleaverunner** — can be parallelized after #3.
5. **05 §3–7** — explorer budget tracking, prune-site snapshot, and reporting.
6. **07 §6** — `visualizer.js` + `visualizer.html`.

Steps 2 and 3 are the ones most likely to be mis-grouped, and both mis-groupings leave `main` broken. A
`default ->` arm is not an escape hatch — it hides the unhandled case instead of forcing a decision.

## Skills Required

| Skill | Purpose |
|-------|---------|
| **implement** | Core implementation workflow (spec-driven, test-first) |
| **tdd** | Test-driven development for each component |
| **codebase-design** | Deep module vocabulary for explorer/StateStore integration |
| **refactor** | Safe refactoring of the `StateStore` interface and existing explorers |
| **diagnosing-bugs** | If preemption classification or the min-preemption equivalence has edge cases |
| **code-review** | Review implementation against spec invariants |

## MCPs Required

| MCP | Purpose |
|-----|---------|
| **GitHub MCP** | Creating PR, checking CI status, reviewing diffs |
| **Sequential Thinking MCP** | Working through preemption classification logic, visited semantics, and the min-count equivalence proof |

## Key Architectural Decisions

### Already Resolved
- ✅ **Cost-aware pruning is O(1)**: `Map<key(config, lastThreadId), minPreemption>` — provably equivalent to
  "probe all recorded `q ≤ p`" without scanning the visited set. The existing `visitedHashes` prefilter in
  `HashingStateStore` is preserved and extended with `lastThreadId`.
- ✅ **Free vs paid preemption**: `lastStillEnabled` check at each config
- ✅ **Step granularity = step boundaries** (preemption only between steps)
- ✅ **DPOR/CBS isolation**: separate strategies, no Bounded-DPOR
- ✅ **INCOMPLETE verdict** for budget-exceeded runs, carrying a real partial schedule snapshotted at the prune site
- ✅ **APPROXIMATE_PASS is CBS-only.** Bitstate DFS/STATIC_POR/DPOR keep reporting `PASS`; relabeling shipped
  output is not worth the churn. This does mean a report table can show `APPROXIMATE_PASS` on CBS bitstate
  rows and `PASS` on the other bitstate rows — that inconsistency is deliberate and revisitable, not a bug.
- ✅ **Bitstate capacity fails fast.** The explorer asserts the store can represent the requested bound and
  throws `IllegalArgumentException` on mismatch. It never clamps, because a silently reduced bound produces
  a verdict that looks exhaustive-at-K but is not.
- ✅ **Library K plumbing**: `Interleave.verify` gains `maxPreemptions` overloads rather than hardcoding K=2.

### Rejected Alternatives

| Alternative | Why rejected |
|---|---|
| Scan the visited set for `q ≤ p` on each lookup | O(V²) across a search, and discards the existing hash prefilter |
| Store the full `(config, lastThreadId, P)` set | The min-count map is equivalent and O(1); the extra entries are redundant |
| Clamp the explorer bound to store capacity | Silently weakens the search; a wiring bug should be loud |
| Return `APPROXIMATE_PASS` for all bitstate results | Relabels every shipped report row and breaks the attestation filter |
| Hardcode K=2 in `Interleave.verify` | Leaves library callers no way to select a bound |
| `default ->` arms on the new enum values | Hides the unhandled case instead of forcing a decision |

### CLI Interface (target — not yet implemented)
```bash
# Single run at K=2
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --json"

# Iterative deepening (K=0→1→2, stops on first violation)
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 3 --iterative-deepening --json"

# Corpus run
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"
```

### Library API (target — not yet implemented)
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
- [ ] `./gradlew test` — all tests pass (143 existing + new)
- [ ] `./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --json"` — works
- [ ] `./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"` — corpus runs
- [ ] `./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --iterative-deepening --json"` — iterative mode
- [ ] `--help` lists `CONTEXT_BOUNDED`, `--max-preemptions`, `--iterative-deepening`
- [ ] `Interleave.verify(p, Strategy.CONTEXT_BOUNDED, inv, 3)` compiles and matches a direct `explore(..., 3)`
- [ ] `INCOMPLETE` verdict appears for budget-exceeded runs, with a non-empty schedule
- [ ] `SoundnessAttestation` passes with CBS in the result set (`peterson` is `PASS` under DFS and
      `INCOMPLETE` under CBS K=2 — this is the case that breaks the naive cross-strategy agreement check)
- [ ] Benchmark table includes a `CONTEXT_BOUNDED` column with real measured states explored
- [ ] Visualizer renders `INCOMPLETE` as `INCOMPLETE`, distinct from `LIMIT EXCEEDED`
- [ ] Library API: `InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).build().run(program)` works
- [ ] Bitstate DFS/STATIC_POR/DPOR verdicts are still `PASS` (regression guard for the `cbVerdict` split)

## Known Gaps

- **No automated JS test suite for the visualizer.** Spec 07 §6 verification is a manual checklist. Closing
  this is a reasonable follow-up.
- **`BenchmarkResult` has no preemption-bound field.** Reports will not state which K produced a row. A
  follow-up field would require new `BenchmarkResult` constructors plus `ReportWriter` and
  `StatesExploredTable` changes; deliberately deferred so this set stays implementable in one pass.
- **INCOMPLETE traces are not minimizable.** `DeltaDebugger` rejects them (Spec 07 §1). Reducing a
  bound-limited search to a shorter schedule that also claims to be INCOMPLETE is meaningless.
