# interleave

An explicit-state model checker for small shared-memory concurrent programs, written in Java. It explores every possible thread interleaving, checks invariants, and prints a deterministic, replayable failing trace when it finds a bug.

The headline artifact is a states-explored reduction table: naive DFS → +hashing → +static POR → +DPOR.

## Why this exists

Concurrency bugs — race conditions, deadlocks, missed signals — are notoriously hard to find because they depend on exact thread ordering. Rather than running a program once and hoping for the right schedule, this checker systematically explores every distinct interleaving and reports the exact `(thread, step)` schedule that reaches a bad state.

The project is built as a clean, spec-driven proof-of-concept. It is not a production Java bytecode checker; programs are modeled as hand-written atomic steps over cloneable shared state so the core algorithms can be taught, verified, and measured.

## What it does

- **Exhaustive DFS** — tries every enabled thread at every reachable configuration.
- **State hashing** — collapses identical configurations so the search space becomes a DAG instead of a tree.
- **Static POR** — computes persistent sets from read/write access sets to avoid exploring equivalent interleavings.
- **Dynamic POR (DPOR)** — discovers necessary reorderings from actual execution using happens-before/race detection and sleep sets.
- **Reproducible evidence** — every found bug comes with a minimized, replayable trace; the final report includes wall-clock, peak memory, and a soundness attestation across all strategies.

## Getting started

### Prerequisites

- JDK 26 or later
- Gradle 8.11+ (wrapper included)

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew run --args=<bug-name>
```

Available bugs: `peterson`, `broken-peterson`, `broken-peterson-v2`, `deadlock`, `double-checked-locking`, `lost-update`, `torn-counter`

### Test

```bash
./gradlew test
```

## Current benchmark results (2026-09-04)

| Program | DFS | Static POR | DPOR | Verdict |
|---------|-----|------------|------|---------|
| peterson | 42 | 18 (57%↓) | 33 | PASS |
| broken-peterson | 46 | 15 (67%↓) | 46 | VIOLATION |
| broken-peterson-v2 | 46 | 12 (74%↓) | 46 | VIOLATION |
| deadlock | 15 | 15 | 15 | DEADLOCK |
| double-checked-locking | 23 | 20 (13%↓) | 23 | VIOLATION |
| lost-update | 13 | 9 (31%↓) | 13* | VIOLATION |
| torn-counter | 8 | 8 | 8 | VIOLATION |

*DPOR has a known soundness limitation for `lost-update` (sleep-set pruning misses the violation interleaving). Uses exhaustive DFS for invariants.

Soundness attestation: all failing traces replay to genuine violations.

## Project structure

```
src/main/java/dev/samhb/interleave/
  core/        SharedState, Step, ModelThread, Program, Configuration, ExecutionDriver
  search/      DfsExplorer, Invariant, Trace, TraceReplayer
  state/       CanonicalEncoder, HashingStateStore, BitstateStore
  por/         IndependenceRelation, PersistentSetComputer, CycleProviso
  dpor/        DporExplorer, HappensBefore, SleepSet
  bugs/        Concurrency classics corpus (7 programs)
  minimize/    DeltaDebugger (ddmin)
  report/      BenchmarkHarness, StatesExploredTable, SoundnessAttestation, ReportWriter
  cli/         Main
```

## Spec-driven development

This project is built from a frozen 7-spec plan. Each spec defines requirements, tests, design, constraints, and cross-references before implementation begins. The exhaustive DFS oracle from Spec 2 is kept alive forever as the differential baseline for all later reduction strategies.

Specs: [`docs/specs/active`](docs/specs/active)

## Tech stack

- **Language:** Java 26+
- **Build:** Gradle 8.11+
- **Testing:** JUnit 5 (38 tests passing)
- **Algorithm references:** [Holzmann SPIN](https://spinroot.com/spin/Man/README.html), [Clarke/Grumberg/Peled Model Checking](https://mitpress.mit.edu/9780262032701/model-checking/), [Flanagan & Godefroid DPOR (POPL 2005)](https://dl.acm.org/doi/10.1145/1047659.1047676), [Godefroid thesis (LNCS 1032)](https://link.springer.com/book/10.1007/BFb0055379)

## Recent improvements (2026-09-04)

### PR #8: HappensBefore wake-up fix
- `HappensBefore.record()` now uses `putIfAbsent` to preserve the first/earliest PC for each edge pair
- `wakeUp()` uses recorded PC via `getPcAtRecord()` when `getStep()` returns null
- `SleepSet.copyFiltering()` re-evaluates sleep set entries when current step changes
- Test fixture updated to isolate recorded-PC dependency

### PR #9: Static POR with invariant support
- Static POR now always uses `porDfs()` regardless of invariant presence
- `IndependenceRelation` treats read-read as independent (standard POR semantics)
- Static POR reduces states with invariants: `broken-peterson` 46→15 (67%), `lost-update` 13→9 (31%)
- DPOR uses exhaustive DFS path for invariants (`explore(program, invariant)` → `dfsDfs()`)
- Removed dead `dfsDfs` from `StaticPorExplorer`, restored it in `DporExplorer`

## Future Extensions

See [`docs/plans/future-work.md`](docs/plans/future-work.md) for the full list. The main candidates are:

- **JSON/YAML/DSL program definition format** — describe concurrent programs declaratively instead of hand-writing Java `Step` objects. See `docs/plans/future-work.md` for the full ranked list.
- **Concurrent-program parser** — parse a small imperative language with threads, shared variables, and atomic sections.
- **Library/API mode** — expose the checker as a Java library for external tools to construct programs and invoke verification programmatically.
- **Real Java bytecode instrumentation** — analyze actual concurrent Java programs instead of modeled ones.
- **Web UI / visualizer** — render interleaving trees, state-space DAGs, or failing traces.
