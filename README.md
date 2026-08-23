# interleave

An explicit-state model checker for small shared-memory concurrent programs, written in Java. It explores every possible thread interleaving, checks invariants, and prints a deterministic, replayable failing trace when it finds a bug.

The headline artifact is a states-explored reduction table: naive DFS → +hashing → +static POR → +DPOR (for example, `4.2M → 31K`).

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

- JDK 17 or later
- Gradle 8.11+ (wrapper included)

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew run
```

### Test

```bash
./gradlew test
```

## Example output

```
$ ./gradlew run

Program: broken-peterson
  NAIVE:       4,201,003 states  |  2.4s  |  128MB
  HASHING:         31,004 states  |  0.3s  |   12MB
  STATIC_POR:      4,203 states  |  0.2s  |    8MB
  DPOR:              901 executions |  0.2s  |    7MB

Verdict: ASSERTION_VIOLATION
Minimized trace: (t0, set-flag) -> (t1, set-flag) -> (t0, enter-cs) -> (t1, enter-cs)
```

## Project structure

```
src/main/java/dev/samhb/interleave/
  core/        SharedState, Step, ModelThread, Program, Configuration, ExecutionDriver
  search/      DfsExplorer, Invariant, Trace, TraceReplayer
  state/       CanonicalEncoder, HashingStateStore, BitstateStore
  por/         IndependenceRelation, PersistentSetComputer, CycleProviso
  dpor/        DporExplorer, HappensBefore, SleepSet
  bugs/        Concurrency classics corpus
  minimize/    DeltaDebugger (ddmin)
  report/      BenchmarkHarness, StatesExploredTable, SoundnessAttestation, ReportWriter
  cli/         Main
```

## Spec-driven development

This project is built from a frozen 7-spec plan. Each spec defines requirements, tests, design, constraints, and cross-references before implementation begins. The exhaustive DFS oracle from Spec 2 is kept alive forever as the differential baseline for all later reduction strategies.

Specs: [`docs/specs/active`](docs/specs/active)

## Tech stack

- **Language:** Java 17+
- **Build:** Gradle 8.11+
- **Testing:** JUnit 5
- **Algorithm references:** [Holzmann SPIN](https://spinroot.com/spin/Man/README.html), [Clarke/Grumberg/Peled Model Checking](https://mitpress.mit.edu/9780262032701/model-checking/), [Flanagan & Godefroid DPOR (POPL 2005)](https://dl.acm.org/doi/10.1145/1047659.1047676), [Godefroid thesis (LNCS 1032)](https://link.springer.com/book/10.1007/BFb0055379)

## Future Extensions

See [`docs/plans/future-work.md`](docs/plans/future-work.md) for the full list. The main candidates are:

- **JSON/YAML/DSL program definition format** — describe concurrent programs declaratively instead of hand-writing Java `Step` objects. See `docs/plans/future-work.md` for the full ranked list.
- **Concurrent-program parser** — parse a small imperative language with threads, shared variables, and atomic sections.
- **Library/API mode** — expose the checker as a Java library for external tools to construct programs and invoke verification programmatically.
- **Real Java bytecode instrumentation** — analyze actual concurrent Java programs instead of modeled ones.
- **Web UI / visualizer** — render interleaving trees, state-space DAGs, or failing traces.
