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

## Property-aware invariant checking

Static POR keeps exhaustive traversal for ordinary invariant callbacks. State-only properties can
opt in by declaring every observed memory location:

```java
Invariant safe = Invariant.observing(Set.of(MemoryLocation.of("flag[0]")),
    state -> !((PetersonState) state).flag(0));
DfsResult result = new StaticPorExplorer().explore(program, safe);
```

DSL `when: "always"` invariants derive their observations automatically. Default/`final` checks
and properties inspecting counters or termination retain exhaustive branching. DPOR's invariant
fallback is unchanged.

Complete step footprints and pure value-based properties are required; incomplete declarations can
cause false passes. With an exact store, reduced runs preserve violation detection but report only
the explored configurations and schedules. Bitstate remains approximate. See
[the 13.08 contract and proof](docs/specs/active/13-deferred-debt/08-godefroid-source-set.md).

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
./gradlew run --args="<bug-name> [flags]"
```

Available bugs: `peterson`, `broken-peterson`, `broken-peterson-v2`, `deadlock`, `double-checked-locking`, `lost-update`, `torn-counter`

### CLI Flags

| Flag | Description |
|------|-------------|
| `--json` | Output as JSON (default: Markdown) |
| `--store exact\|bitstate` | Filter by store type (default: both) |
| `--strategy DFS\|STATIC_POR\|DPOR` | Filter by strategy (default: all) |
| `--bitstate-size N` | Bitstate bit-array size (default: 1,000,003) |
| `--bitstate-k N` | Bitstate hash function count (default: 4) |
| `--all` | Run entire corpus |
| `--file <path>` | Load program from JSON file |

### Examples

```bash
# Run all strategies for a program
./gradlew run --args="peterson"

# Run only bitstate DFS for a program
./gradlew run --args="peterson --store bitstate --strategy DFS"

# Run entire corpus as JSON
./gradlew run --args="--all --json"

# Run from JSON file with custom bitstate params
./gradlew run --args="--file examples/programs/lost-update.json --bitstate-size 500001 --bitstate-k 6"
```

### Test

```bash
./gradlew test
```

## Historical benchmark results (2026-09-29)

These figures predate the exhaustive invariant guard and property-aware 13.08 path; they are
historical, not current reduction measurements. See [13.08](docs/specs/active/13-deferred-debt/08-godefroid-source-set.md)
for current contracts and verification.

State counts with the exact store. CBS runs at K=2, where the search explores a superset of the
configurations at that bound rather than a reduction — see the tradeoff note below.

| Program | DFS | Static POR | DPOR | CBS (K=2) | Verdict (exhaustive) | Verdict (CBS) |
|---------|-----|-----------|------|-----------|----------------------|---------------|
| peterson | 42 | 18 (57%↓) | 38 (10%↓) | 65 | PASS | INCOMPLETE |
| broken-peterson | 46 | 15 (67%↓) | 46 | 76 | VIOLATION | VIOLATION |
| broken-peterson-v2 | 46 | 12 (74%↓) | 46 | 68 | VIOLATION | VIOLATION |
| deadlock | 15 | 15 | 15 | 21 | DEADLOCK | DEADLOCK |
| double-checked-locking | 17 | 14 (18%↓) | 17 | 31 | VIOLATION | VIOLATION |
| lost-update | 13 | 9 (31%↓) | 13 | 17 | VIOLATION | VIOLATION |
| torn-counter | 8 | 8 | 8 | 8 | VIOLATION | VIOLATION |

At that historical measurement, bitstate runs matched their exact counterparts on the corpus.
Bitstate remains approximate; later exploration changes can expose suppression differences. Soundness attestation: all failing traces replay to genuine violations, and
every buggy program is caught by CBS at K=2.

`peterson` is the interesting row: correct, and therefore a `PASS` under exhaustive search, but
`INCOMPLETE` under a bounded one. That is the correct answer — a bounded run that ran out of budget
proved nothing about the region it pruned.

## Project structure

```
src/main/java/dev/samhb/interleave/
  core/        SharedState, Step, ModelThread, Program, Configuration, ExecutionDriver
  search/      DfsExplorer, Invariant, Trace, TraceReplayer
  state/       CanonicalEncoder, HashingStateStore, BitstateStore
  por/         IndependenceRelation, PersistentSetComputer, CycleProviso
  dpor/        DporExplorer, HappensBefore, SleepSet
  cb/          ContextBoundedExplorer (CHESS-style preemption-bounded search)
  corpus/      CorpusGenerator, TemplateRegistry, CorpusEntry
  format/      JSON program loader + declarative DSL
  bugs/        Concurrency classics corpus (7 programs)
  minimize/    DeltaDebugger (ddmin)
  report/      BenchmarkHarness, StatesExploredTable, SoundnessAttestation, ReportWriter
  cli/         Main
  *.java       Interleave, InterleaveRunner, Strategy, TestResult, TraceRecord, VerificationResult
```

## Spec-driven development

This project is built from a frozen 7-spec plan. Each spec defines requirements, tests, design, constraints, and cross-references before implementation begins. The exhaustive DFS oracle from Spec 2 is kept alive forever as the differential baseline for all later reduction strategies.

Specs: [`docs/specs/active`](docs/specs/active)

Specs 1–7 are the original frozen 7-spec plan. Specs 8–10 (corpus mining, the JSON DSL core, and its invariants) were added later as the declarative-programming surface. Spec 11 ([`11-context-bounded`](docs/specs/active/11-context-bounded/README.md)) added CBS and is shipped. Spec 12 ([`12-mutation-hardening`](docs/specs/active/12-mutation-hardening/README.md)) closes the mutation-testing gaps left by PIT and is implemented, including the assertion-backed ratchet. Spec 13 ([`13-deferred-debt`](docs/specs/active/13-deferred-debt/README.md)) is complete. It closes the deferred implementation debt, including E5 through repeated full-scope parallel CI evidence.

The verification recorded in [13.10](docs/specs/active/13-deferred-debt/10-parallel-ci-and-documentation.md)
passes 526 tests and PIT at 255/268, with zero timeout or execution-error statuses. PR #45’s final
mutation job used three workers and satisfied E5’s closure criterion; the strict gate remains intact.

Java documentation now covers all 824 explicit methods and constructors in the audited production
and PR #44 test scope. Javadoc enables all doclint groups and treats warnings as errors.
CodeRabbit’s 80% docstring threshold is a minimum; every new function requires meaningful
documentation. PR #45’s remote CodeRabbit review was skipped due to its file limit, so no remote
coverage percentage was produced. The local audit, Javadoc checks, and two local reviews passed.

## Tech stack

- **Language:** Java 26+
- **Build:** Gradle 8.11+
- **Testing:** JUnit 5 (`./gradlew test`)
- **Algorithm references:** [Holzmann SPIN](https://spinroot.com/spin/Man/README.html), [Clarke/Grumberg/Peled Model Checking](https://mitpress.mit.edu/9780262032701/model-checking/), [Flanagan & Godefroid DPOR (POPL 2005)](https://dl.acm.org/doi/10.1145/1047659.1047676), [Godefroid thesis (LNCS 1032)](https://link.springer.com/book/10.1007/BFb0055379)

## Bitstate Mode

Bitstate provides probabilistic state storage using Bloom filters. It trades completeness for memory savings — false positives are possible (states may be incorrectly marked as visited), but false negatives are not (a visited state is never explored twice).

**Tradeoffs:**
- Memory: O(m) total for m bits (configurable), vs O(n) total for n states in exact hashing
- Speed: faster due to cache-friendly bit-array access
- Completeness: may miss violations in rare cases (false positive rate tracked in reports)

**When to use:**
- Large state spaces where exact hashing exhausts memory
- Quick initial screening before exhaustive verification
- Benchmarking and performance analysis

**Reports include:**
- False-positive rate estimate for each bitstate run
- Bit density (fraction of bits set) as a health metric
- Reduction percentages relative to DFS baseline

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

### Historical PR #9: Static POR with invariant support

- Static POR now always uses `porDfs()` regardless of invariant presence
- `IndependenceRelation` treats read-read as independent (standard POR semantics)
- Static POR reduces states with invariants: `broken-peterson` 46→15 (67%), `lost-update` 13→9 (31%)
- DPOR uses exhaustive DFS path for invariants (`explore(program, invariant)` → `dfsDfs()`)
- Removed dead `dfsDfs` from `StaticPorExplorer`, restored it in `DporExplorer`

### Context-Bounded Search (Spec 11)

Context-bounded search (CHESS-style) systematically explores all interleavings up to a configurable number of **preemptive context switches** (bound K). A preemption occurs when the scheduler switches away from a thread that *could have continued* (i.e., was still enabled). Forced switches (previous thread blocked/terminated) do not count toward the bound.

```bash
# Run CBS on a single program
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --max-preemptions 2 --json"

# Run CBS on entire corpus
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"

# Deepen until the first failure, reporting the minimal bound that found it
# --store exact is required above K=2: see the note on bitstate below
./gradlew run --args="lost-update --strategy CONTEXT_BOUNDED --store exact --max-preemptions 3 --iterative-deepening"
```

**Reading the verdict.** A bounded run that exhausts its preemption bound without finding a bug
reports `INCOMPLETE`, never `PASS` — it proved nothing about the pruned region. A bitstate run at
any bound reports `APPROXIMATE_PASS` or `INCOMPLETE` rather than `PASS`, because Bloom collisions
can prune real states. The CLI therefore **refuses** a bound above 2 combined with bitstate and
tells you to pass `--store exact`, rather than emitting a row whose verdict cannot be interpreted.

**Cost-aware deduplication.** States are recorded with the *least* preemption budget at which they
were reached, so a deeper bound is never pruned by a shallower one. Iterative deepening requires a
fresh `StateStore` per bound and rejects a factory that reuses one; a shared visited set would make
deepening narrow instead of widen.

**Tradeoffs:**
- **State reduction is not automatic** — on this corpus CBS explores *more* configurations than
  plain DFS (e.g. `peterson` 65 vs 42 at K=2), because the extra `(config, lastThreadId)` dimension
  fragments what DFS deduplicates. The win is bounded exploration, not fewer states, and the
  reduction table renders these as bare counts rather than negative percentages.
- **Incomplete by design** — bugs requiring >K preemptions are missed
- **Sweet spot: K=2** — every buggy program in the corpus is caught at K=2, and `peterson` (which
  is correct) is the one that reports `INCOMPLETE`

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

### Program formats

Interleave supports two program definition formats, dispatched by the `format` field.

**Typed (`format: "typed"`):** the 7 corpus programs above — `state.type` selects a hand-written `SharedState` and `InvariantRegistry` types:

```json
{
  "format": "typed",
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

**Declarative (`format: "declarative"`):** threads are lists of `{guard, effects}` over declared `fields`/`locals` and an optional invariant `{expr}` or `{all: [...]}` with `when: "final"|"always"` (default `"final"`). See `docs/specs/active/09-json-dsl-core.md` and `10-json-dsl-invariants.md`; curated examples live in `examples/programs/`:

```json
{
  "format": "declarative",
  "name": "lost-update-declarative",
  "state": {
    "fields": [{"name": "counter", "type": "int", "init": 0}],
    "locals": [{"name": "r", "type": "int", "init": 0}]
  },
  "threads": [
    {"id": 0, "steps": [{"effects": ["local.r = counter"]}, {"effects": ["counter = local.r + 1"]}]},
    {"id": 1, "steps": [{"effects": ["local.r = counter"]}, {"effects": ["counter = local.r + 1"]}]}
  ],
  "invariant": {"expr": "counter == 2"},
  "expected_verdict": "VIOLATION"
}
```

| File | Invariant | Description |
|------|-----------|-------------|
| `examples/programs/bounded-buffer-declarative.json` | `count >= 0 && count <= capacity && count <= 3 && …` | Producer/consumer ring buffer (capacity 3) |
| `examples/programs/semaphore-declarative.json` | `permits >= 0` | Binary semaphore acquire/release |

Run declaratively: `./gradlew run --args="--file examples/programs/bounded-buffer-declarative.json"` — invariants are carried end-to-end through the same explorers and harness as typed.

## Future Extensions
