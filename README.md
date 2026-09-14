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

## Current benchmark results (2026-09-13)

| Program | DFS (exact) | DFS (bitstate) | Static POR (exact) | Static POR (bitstate) | DPOR (exact) | DPOR (bitstate) | Verdict |
|---------|-------------|----------------|---------------------|------------------------|--------------|-----------------|---------|
| peterson | 42 | 42 | 18 (57%↓) | 18 (57%↓) | 38 | 38 | PASS |
| broken-peterson | 46 | 46 | 15 (67%↓) | 15 (67%↓) | 46 | 46 | VIOLATION |
| broken-peterson-v2 | 46 | 46 | 12 (74%↓) | 12 (74%↓) | 46 | 46 | VIOLATION |
| deadlock | 15 | 15 | 15 | 15 | 15 | 15 | DEADLOCK |
| double-checked-locking | 17 | 17 | 14 (18%↓) | 14 (18%↓) | 17 | 17 | VIOLATION |
| lost-update | 13 | 13 | 9 (31%↓) | 9 (31%↓) | 13 | 13 | VIOLATION |
| torn-counter | 8 | 8 | 8 | 8 | 8 | 8 | VIOLATION |

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
- **Testing:** JUnit 5 (73 tests passing)
- **Algorithm references:** [Holzmann SPIN](https://spinroot.com/spin/Man/README.html), [Clarke/Grumberg/Peled Model Checking](https://mitpress.mit.edu/9780262032701/model-checking/), [Flanagan & Godefroid DPOR (POPL 2005)](https://dl.acm.org/doi/10.1145/1047659.1047676), [Godefroid thesis (LNCS 1032)](https://link.springer.com/book/10.1007/BFb0055379)

## Library/API Mode

The checker can now be used as a Java library in other projects:

```java
// Static ergonomic entry point
TestResult result = Interleave.quickCheck(program);
TestResult result = Interleave.quickCheck(program, Strategy.STATIC_POR);

// Instance-based API with fluent builder (reusable, thread-safe)
InterleaveRunner runner = InterleaveRunner.builder()
    .strategy(Strategy.DPOR)
    .invariant(myInvariant)
    .stateStoreFactory(() -> new HashingStateStore())
    .maxStates(10_000)
    .maxTime(Duration.ofSeconds(30))
    .build();

TestResult result1 = runner.run(program1);
TestResult result2 = runner.run(program2); // reusable

// Results are immutable and serializable
if (result.hasViolation()) {
    for (TraceRecord trace : result.failingTraces()) {
        System.out.println(trace.toJson());
    }
}

// Backward-compatible static facade (returns VerificationResult)
VerificationResult vr = Interleave.verify(program, Strategy.DFS);
TestResult tr = vr.toTestResult(); // convert to new API
TraceRecord record = vr.completedTraces().get(0).toRecord();
```

**Key features:**
- **Reusable instances** — runner config is immutable; `run(Program)` can be called multiple times
- **Fresh state per run** — each `run()` creates a new `StateStore` to avoid cross-run contamination
- **Limit enforcement** — `maxStates` / `maxTime` stop exploration early and return partial results with `limitExceeded=true`
- **Trace capture on limit** — traces found before limit are preserved via `StateVisitor.onTraceCreated()`
- **StateStore factory** — `stateStoreFactory(Supplier<StateStore>)` preserves configured implementation type
- **Serializable results** — `TestResult`, `TraceRecord` implement `Serializable` for persistence/transport

## Recent improvements (2026-09-11)

### PR #8: HappensBefore wake-up fix
- `HappensBefore.record()` now uses `putIfAbsent` to preserve the first/earliest PC for each edge pair
- `SleepSet.copyFiltering()` re-evaluates sleep set entries when current step changes
- Test fixture updated to isolate recorded-PC dependency

### PR #9: Static POR with invariant support
- Static POR now always uses `porDfs()` regardless of invariant presence
- `IndependenceRelation` treats read-read as independent (standard POR semantics)
- Static POR reduces states with invariants: `broken-peterson` 46→15 (67%), `lost-update` 13→9 (31%)
- DPOR uses exhaustive DFS path for invariants (`explore(program, invariant)` → `dfsDfs()`)
- Removed dead `dfsDfs` from `StaticPorExplorer`, restored it in `DporExplorer`

## JSON Program Definition Format

Starting with v1.1, programs can be defined declaratively in JSON instead of writing Java code. This enables rapid prototyping and makes the tool accessible for teaching.

### Format

A program definition is a JSON object with these fields:

```json
{
  "name": "lost-update",
  "state": { "type": "counter", "counter": 0 },
  "threads": [
    { "id": 0, "steps": [{"type": "read_counter"}, {"type": "write_counter"}] },
    { "id": 1, "steps": [{"type": "read_counter"}, {"type": "write_counter"}] }
  ],
  "invariant": { "type": "counter_equals", "expected": 2 },
  "expected_verdict": "VIOLATION"
}
```

### State types

| Type | Required fields |
|---|---|
| `peterson` | `flags`: [bool, bool], `turn`: int |
| `counter` | `counter`: int |
| `dcl` | `initialized`: bool |
| `deadlock` | `flags`: [bool, bool] |
| `pair` | `high`: int, `low`: int |

### Step types (19 total)

| type | Required params | Compatible state |
|---|---|---|
| `write_flag` | `value`: bool | peterson |
| `write_turn` | `value`: int | peterson |
| `busy_wait` | `other`: int | peterson |
| `read_flag` | `other`: int | peterson |
| `cs_enter` | — | peterson |
| `cs_exit` | — | peterson |
| `read_counter` | — | counter |
| `write_counter` | — | counter |
| `write_high` | `value`: int | pair |
| `write_low` | `value`: int | pair |
| `read_snapshot` | — | pair |
| `deadlock_write_flag` | `value`: bool | deadlock |
| `unconditional_wait` | `other`: int | deadlock |
| `dcl_lock` | — | dcl |
| `dcl_unlock` | — | dcl |
| `dcl_init` | — | dcl |
| `dcl_create_instance` | — | dcl |
| `dcl_read_instance` | — | dcl |
| `dcl_use_instance` | — | dcl |

All step types accept an optional `thread` parameter (defaults to the owning thread's ID). If present, it must match the owning thread's ID. Steps like `busy_wait`, `read_flag`, and `unconditional_wait` also require an `other` parameter (the other thread's ID, must be valid).

### Invariant types

| type | Params | Compatible state |
|---|---|---|
| `mutual_exclusion_peterson` | `thread0_cs_pc`, `thread1_cs_pc` | peterson |
| `counter_equals` | `expected` | counter |
| `dcl_uninitialized_observed` | (none) | dcl |
| `torn_read` | `high_value`, `low_value` | pair |

### CLI Usage

```bash
# Run a built-in bug program (unchanged)
./gradlew run --args="lost-update --json"

# Run a program from a JSON file
./gradlew run --args="--file examples/programs/lost-update.json --json"
```

### Example files

Seven example program definitions are included in `examples/programs/` and `src/main/resources/programs/`:

- `peterson.json` (correct Peterson, expected PASS, no invariant)
- `broken-peterson.json` (VIOLATION, mutual_exclusion_peterson)
- `broken-peterson-v2.json` (VIOLATION, mutual_exclusion_peterson)
- `deadlock.json` (DEADLOCK, no invariant)
- `double-checked-locking.json` (VIOLATION, dcl_uninitialized_observed)
- `lost-update.json` (VIOLATION, counter_equals)
- `torn-counter.json` (VIOLATION, torn_read)

### Migration

The built-in `BugCorpus` programs now load from JSON resources. Load-and-compare tests verify that JSON-loaded programs produce identical results to the original Java implementations.

## Future Extensions
