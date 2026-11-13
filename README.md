# interleave

An explicit-state model checker for small shared-memory concurrent programs, written in Java. It explores every possible thread interleaving, checks invariants, and prints a deterministic, replayable failing trace when it finds a bug.

The headline artifact is a states-explored reduction table: naive DFS → +hashing → +static POR → +DPOR.

## Why this exists

Concurrency bugs — race conditions, deadlocks, missed signals — are notoriously hard to find because they depend on exact thread ordering. Rather than running a program once and hoping for the right schedule, this checker systematically explores every distinct interleaving and reports the exact `(thread, step)` schedule that reaches a bad state.

The project is built as a clean, spec-driven proof-of-concept. It is not a production Java bytecode checker; programs are modeled as explicit atomic steps or bounded `.ilv` source over cloneable shared state so the core algorithms can be taught, verified, and measured.

## What it does

- **Readable source models** — `.ilv` atomic blocks, bounded reusable helpers, branching, repetition and bounded loops share the existing checker and source-aware trace viewer.
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
and properties inspecting counters or termination retain exhaustive branching in both static POR and DPOR.
Invariants declaring complete observations enable property-aware reduction in both `StaticPorExplorer` and `DporExplorer`.

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
| `--strategy DFS\|STATIC_POR\|DPOR\|CONTEXT_BOUNDED` | Filter by strategy (default: all) |
| `--bitstate-size N` | Bitstate bit-array size (default: 1,000,003) |
| `--bitstate-k N` | Bitstate hash function count (default: 4) |
| `--all` | Run entire corpus |
| `--file <path>` | Load program from JSON file |
| `--source <path>` | Load a bounded concurrent `.ilv` source model |
| `--symmetry` | Opt-in checked symmetry: source or certified declarative JSON with exact/bitstate |

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
  format/      JSON/source program loaders + shared DSL semantics and source compiler
  bugs/        Concurrency classics corpus (8 programs, including lost-update-3t)
  minimize/    DeltaDebugger (ddmin)
  report/      BenchmarkHarness, StatesExploredTable, SoundnessAttestation, ReportWriter
  cli/         Main
  *.java       Interleave, InterleaveRunner, Strategy, TestResult, TraceRecord, VerificationResult
```

## Spec-driven development

This project is built from a frozen 7-spec plan. Each spec defines requirements, tests, design, constraints, and cross-references before implementation begins. The exhaustive DFS oracle from Spec 2 is kept alive forever as the differential baseline for all later reduction strategies.

Specs: [`docs/specs/active`](docs/specs/active)

Specs 1–7 are the original frozen 7-spec plan. Specs 8–10 (corpus mining, the JSON DSL core, and its invariants) were added later as the declarative-programming surface. Spec 11 ([`11-context-bounded`](docs/specs/active/11-context-bounded/README.md)) added CBS and is shipped. Spec 12 ([`12-mutation-hardening`](docs/specs/active/12-mutation-hardening/README.md)) closes the mutation-testing gaps left by PIT and is implemented, including the assertion-backed ratchet. Spec 13 ([`13-deferred-debt`](docs/specs/active/13-deferred-debt/README.md)) is complete. It closes the deferred implementation debt, including E5 through repeated full-scope parallel CI evidence.

The historical verification recorded in [13.10](docs/specs/active/13-deferred-debt/10-parallel-ci-and-documentation.md)
passes 526 tests and PIT at 255/268, with zero timeout or execution-error statuses. PR #45’s final
mutation job used three workers and satisfied E5’s closure criterion; the strict gate remains intact.

[Spec 14](docs/specs/active/14-test-quality.md) audits the test contracts, replaces placeholders and
overstated checks, and maps every consolidation to retained coverage in the
[per-method ledger](docs/plans/test-quality-audit.md). The cleanup suite has 524 passing invocations;
its scoped mutation comparisons and remaining limits are recorded separately from the historical
default-scope score above.

[Spec 15](docs/specs/active/15-property-aware-dpor/README.md) implements property-aware dynamic partial order reduction (DPOR) with sleep sets, enabling pruning of independent invisible work for state invariants while strictly preserving exhaustive fallback for unannotated predicates.

The historical spec 13.10 documentation audit covered 824 explicit methods and constructors in its
production and PR #44 test scope. That census predates the test-quality changes; it is not a current
whole-suite documentation count. Javadoc enables all doclint groups and treats warnings as errors.
CodeRabbit’s 80% docstring threshold is a minimum; every new function requires meaningful
documentation. PR #45’s remote CodeRabbit review was skipped due to its file limit, so no remote
coverage percentage was produced. The local audit, Javadoc checks, and two local reviews passed.

[Spec 75](docs/specs/active/75-thread-symmetry-canonicalization.md) provides thread-orbit keys and
reversible owner mappings. [Spec 76](docs/specs/active/76-source-symmetry-exact-search.md) integrates
conservative source eligibility and opt-in exact DFS. [Spec 77](docs/specs/active/77-source-symmetry-strategy-composition.md) composes exact
static POR, DPOR and CBS with checked source symmetry. Specs 89–91 add source bitstate composition;
[Specs 92–93](docs/plans/declarative-symmetry-contract.md) add certified declarative exact and approximate symmetry.

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
- **Serializable results** — `TestResult`, `TraceRecord` implement `Serializable` for persistence/transport between matching library builds; native object streams are not a stable cross-build format

**Native Java stream upgrade boundary (PR #118).** The symmetry field/API changes `TestResult`'s
computed serial UID. Java streams from the preceding build (`ac16444`) and this build reject each
other with `InvalidClassException`, including ordinary results. The project retains its existing
build-specific serialization policy; Java source API and additive JSON compatibility are separate.
An older Java reader therefore cannot silently treat a new symmetry result as ordinary DFS.

Keep the exact writer build available when upgrading stored Java results. Read them with that
build and export `TestResult.toJson()` before upgrading, or recompute them with the new build.
Use JSON for interchange/archive data, preserving its named fields; consumers of symmetry results
must retain `symmetryReduction` and treat counts as representative visits with strategy context. A matching version
string alone is insufficient for development builds: retain the artifact/revision. Do not force
an old UID onto the new class: old readers would ignore the new field and lose the reduction marker.

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

## Readable concurrent models

Reusable `helper` definitions can call earlier helpers. Optional read-only `int`/`bool` parameters
are captured by value at one call-entry action; a final scalar `return` is assigned at one exit
action. Parameter slots count toward the existing eight-local limit. Zero-argument void helpers
retain their existing actions. See [scalar helper values](examples/programs/helper-values.ilv) and
[Spec 83](docs/specs/active/83-bounded-helper-values.md).

Use `.ilv` to describe a small concurrent model directly. Assignments inside one `atomic` block run
together, including nested atomic blocks. Inner braces add no scheduling or commit boundary.
A reached false inner `atomic when` guard disables the entire outer action and discards tentative
writes; earlier failures/cutoffs remain observable. Guards read preceding tentative values. See the
[nested example](examples/programs/nested-atomic.ilv) and
[guarded handoff](examples/programs/guarded-nested-handoff.ilv).
Thread-level `if` and `while` conditions are separate scheduling actions; another thread
can run between a decision and its body.

```text
program increments {
  shared int counter = 0;
  thread 0 { atomic { counter = counter + 1; } }
  thread 1 { atomic { counter = counter + 1; } }
  invariant final (counter == 2);
  expected PASS;
}
```

```bash
./gradlew --max-workers=1 -PpitestThreads=1 run --args="--source examples/programs/atomic-increment.ilv --json"
```

`assert (condition);` checks a boolean at its scheduled position or privately inside an atomic
block. A false private assertion rolls back the outer action and reports its own source location.
See [passing assertions](examples/programs/statement-assertions.ilv) and
[assertion rollback](examples/programs/assertion-rollback.ilv).

Use `--symmetry --store exact --strategy DFS` to explore one representative of each interchangeable
source-thread orbit. The checker groups structurally identical instructions with equal initial
locals and no `tid` observations; other owners stay fixed. It accepts absent or compiler-built
shared source properties and rejects unknown callbacks or a model with no eligible group. Actual
execution and replay traces retain original thread IDs and source locations. Reports/viewer label
counts as representative visits with strategy context; this is not a runtime-speedup claim.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 run --args="--source examples/programs/symmetry-counter.ilv --store exact --strategy DFS --symmetry --json"
```

This four-owner example visits five representatives instead of 16 concrete states. Library use:

```java
var model = new SourceProgramLoader().loadFromFile(Path.of("examples/programs/symmetry-counter.ilv"));
var result = InterleaveRunner.builder().symmetryReduction(true)
    .invariant(model.invariant().orElse(null)).build().run(model.program());
```

The library mode supports all four built-in strategies. HASHED remains the default backend;
select natural-order tree membership with `.symmetryBackend(SymmetryBackend.ORDERED)` on a builder
that enables symmetry. The runner owns fresh storage per run and iterative bound; both backends
retain complete keys, exact CBS cost handling and DPOR sleep contexts. This storage choice makes
no speed or memory promise. Checked symmetry still rejects arbitrary supplied stores/factories.
Ordinary runner mode rejects supplied symmetry stores too, ensuring quotient counts carry their
result marker.
Omit CLI `--strategy` to run all four; select `CONTEXT_BOUNDED`
for CBS bounds or iterative deepening. DPOR retains exact mapped sleep context. Repeated instruction objects in recomposed programs are
supported when interchangeable owners have matching alias patterns; incompatible patterns reject.
CBS retains mapped last owner and minimum cost.
Counts can include revisits with different strategy context.

Approximate symmetry supports **all four built-in strategies: DFS, static POR, DPOR and CBS**.
Use CLI `--symmetry --store bitstate`, optionally selecting one `--strategy`, or use
`.symmetryReduction(true).symmetryBitstate(1_000_003, 4)` on the library builder.
An explicit exact backend or custom store/factory cannot be combined with this selection.
Omitting CLI `--strategy` runs all four; a library builder selects one strategy and defaults to DFS.
For CBS, `.maxPreemptions(k)` selects library capacity; the CLI retains its bitstate limit of two.
All checked CBS costs share one logical filter, with mapped last owner and cost encoded together.
With `--iterative-deepening`, approximate runs continue to the requested ceiling unless actual
failure/deadlock or a resource limit stops them. This also corrects ordinary approximate CBS:
missing cutoff evidence cannot prove exhaustion. Exact searches keep their exhaustion shortcut.
`preemptionsUsed` is the producing bound, not a proven approximate minimum. CBS's capped
union-model false-positive estimate uses the largest validated queried cost in that pass.
The complete value orbit is encoded before Bloom hashing. DPOR additionally encodes the full
mapped sleep context, with first-occurrence action identities and stable whole-frame owner mapping;
VALUE hits cannot deliberately replace product coverage. All marks share one filter per owned store.
False positives may omit reachable behavior. Absence is `APPROXIMATE_PASS`, while actual violations and incomplete evidence keep
precedence. Bit density and the false-positive heuristic are diagnostics, not coverage guarantees.
The bit budget covers membership only: source metadata, traversal, diagnostic state maps,
temporary keys and retained traces also consume memory.
See [Spec 77](docs/specs/active/77-source-symmetry-strategy-composition.md) for strategy composition,
[Spec 81](docs/specs/active/81-aliased-source-symmetry.md) for alias eligibility and transport,
and [Spec 84](docs/specs/active/84-certified-symmetry-backends.md) for certified library backends.
The CLI/harness retain HASHED; backend selection adds no report field or CLI flag.

To shorten a bound explanation, use `--simplify-cutoff` with a source model, `--json`,
`--store exact` and one `--strategy`. It emits one direct trace for the existing viewer, preserving
the exact loop/preemption cutoff and its bound. It remains INCOMPLETE and requires an INCOMPLETE
selected run; this operation does not hide a known violation or minimize failures.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 run --args="--source examples/programs/cutoff-simplification.ilv --store exact --strategy DFS --json --simplify-cutoff"
```

The existing CLI operation is deletion-1-minimal, not necessarily shortest. Library callers can
opt into bounded exact search among subsequences of an authenticated cutoff witness:

```java
var search = new CutoffSimplifier().shortestSubsequence(model.program(), witness,
    model.invariant().orElse(null), 10_000);
Trace best = search.trace(); // Still INCOMPLETE; the entire omitted cutoff is unchanged.
boolean shortestWithinOriginalChoices = search.shortestProven();
long checked = search.candidatesChecked();
```

A stopped search returns its best-found witness without claiming shortest length or deletion-1
minimality. The budget counts candidate prefixes, excluding original authentication; it does not
bound elapsed time or memory. A proof concerns the original choice subsequences, not all legal
schedules or unexplored model behavior. Empty originals still authenticate. Search can be exponential;
use it outside the exploration hot path. See [Spec 85](docs/specs/active/85-shortest-cutoff-subsequence.md).
Use `--shortest-cutoff N` for the same operation through the CLI, with an explicit non-negative
candidate allowance (up to 2147483647). Require `--source`, `--json`, `--store exact` and an explicit
`--strategy`; this flag is mutually exclusive with `--simplify-cutoff`, which keeps its existing contract.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 run --args="--source examples/programs/cutoff-simplification.ilv --store exact --strategy DFS --json --shortest-cutoff 100"
```

The direct INCOMPLETE trace includes `cutoffSearch`: `domain: "ORIGINAL_SUBSEQUENCES"`,
`shortestProven`, `candidatesChecked` and `candidateLimit`. The viewer displays the proof state,
work and limited scope. A budget stop is best-found only and need not be deletion-1-minimal;
a proven answer is shortest only among the supplied original witness's subsequences.
This never certifies PASS or a globally shortest legal schedule. The metadata is a producer claim,
not an independently replay-verified proof certificate. See [Spec 86](docs/specs/active/86-shortest-cutoff-cli.md).

Resource-interrupted library runs expose a separate actual execution prefix:

```java
var model = new SourceProgramLoader().loadFromFile(Path.of("examples/programs/cutoff-simplification.ilv"));
var result = InterleaveRunner.builder().maxStates(2)
    .invariant(model.invariant().orElse(null)).build().run(model.program());
var prefix = result.resourcePrefix().orElseThrow();
ResourcePrefix.Replay observed = prefix.replay(model.program(), model.invariant().orElse(null));
String capturedJson = prefix.toJson();  // Captured final state only; no implicit replay.
String replayJson = observed.toJson();  // Separate already-computed replay observation.
String runJson = result.toJson();       // Optional raw resourcePrefix, with original run metadata.
```

`ResourcePrefix` freezes the actual committed choices, outcomes, source identities, owner PCs and
shared/local values at a known `MAX_STATES` or `MAX_TIME` interruption. Capturing it executes no
modeled actions or properties. The optional accessor is empty for successful/legacy/unattributed
interruptions; model loop/preemption witnesses retain their existing separate buckets.

Explicit replay authenticates the prefix and returns fresh snapshots, its execution kind, whether
a property was checked and any final-position failure. It can discover completion, deadlock or a
property failure because the original resource check precedes those checks. It does not reproduce
the resource budget or change the original result; one completed path never proves exhaustive PASS.
Supply the original deterministic source model and actual property. Replay is additional diagnostic
work outside the original allowance. [Spec 87](docs/specs/active/87-resource-prefix-evidence.md)
owns capture/replay; [Spec 88](docs/specs/active/88-resource-prefix-presentation.md) owns JSON/viewer
presentation. Load captured/replayed JSON into the existing viewer: raw prefixes show only the final
snapshot; explicit replay adds intermediate states and separately labeled observations. A reported
replay never changes the original INCOMPLETE verdict or independently authenticates uploaded JSON.
Earlier recorded failures/deadlocks keep selection priority. No shortening or new CLI mode is selected.

B10 enables the existing declarative JSON loader for exact or approximate symmetry across DFS,
static POR, DPOR and CBS, including HASHED/ORDERED exact library backends. For example:

```bash
./gradlew --max-workers=1 -PpitestThreads=1 run --args="--file examples/programs/semaphore-declarative.json --symmetry --store exact --json"
```

Only loader-certified steps and absent/certified shared properties qualify; public constructor
objects, custom callbacks, mixed families and foreign declarations reject. Corresponding ordered
instructions and initial locals must match; owners observing `tid` remain fixed. Existing caps also
apply when trusted objects are reassembled. The semaphore has 8 concrete DFS positions and 5 orbits;
these fixture counts do not promise general speedups. FINAL declarative properties retain conservative
exhaustive reduction fallbacks. JSON keeps its existing error outcomes and original choices. Checked declarative failure snapshots
repeat the last committed values rather than exporting tentative writes; ordinary replay/capture
behavior stays unchanged. JSON does not acquire
source identity, cutoff simplification or authenticated resource prefixes.

Select `--store bitstate` for approximation, or use `symmetryBitstate(bits, probes)` in the library.
One filter holds the complete VALUE/CBS/DPOR membership products, including mapped owners/costs and
sleepers. Collisions may omit real behavior; absence is `APPROXIMATE_PASS`, not an exhaustive PASS.
Actual failures/deadlocks and interruptions retain their precedence. CBS reports its observed
producing bound without proving a minimum, and its diagnostic heuristic is not calibrated coverage.
See [L2 / Spec 93](docs/specs/active/93-approximate-declarative-symmetry.md).

B9 approximate symmetry is delivered in the [three-phase spec set](docs/plans/approximate-symmetry-contract.md).
K1 (DFS/static POR), K2 (CBS) and K3 (DPOR and final all-strategy consumers) are merged
in PRs #132–134. B9 is complete. No total-process memory bound or
speedup is claimed.

Library callers use `new SourceProgramLoader().load(text)` or `.loadFromFile(path)` from
`dev.samhb.interleave.format`. The result supplies the compiled program and optional invariant to
the existing runner. Load failures are `RegistryException` diagnostics with file, line, column
and category. JSON loading stays on `ProgramLoader` and `--file`.

`repeat N` is fixed repetition. `while (condition) limit N` permits N bodies, then checks again:
false completes, true reports INCOMPLETE with LOOP_LIMIT evidence. Atomic errors and internal loop
cutoffs discard all tentative writes. Properties observe only committed states. Scheduling bounds
(PREEMPTION_BOUND) and resource stops (MAX_STATES/MAX_TIME) remain separate causes.

The language caps input at 1 MiB, eight threads, 64 expanded actions per thread, 64 private actions
per atomic block and 5,000 expanded expression nodes. It supports integer/boolean fields, local
fields, integer arrays, guards and bounded control flow. It executes no host functions or I/O and
does not ingest Java projects. Exact-store PASS describes this finite model; bitstate absence is
APPROXIMATE_PASS. See the [examples](examples/programs/README.md) and
[language contract](docs/specs/active/16-concurrent-program-parser/01-language-and-semantics.md).

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

Eight built-in program definitions live in `src/main/resources/programs/`; runnable JSON examples also live in `examples/programs/`:

- `peterson.json` (correct Peterson, expected PASS, no invariant)
- `broken-peterson.json` (VIOLATION, mutual_exclusion_peterson)
- `broken-peterson-v2.json` (VIOLATION, mutual_exclusion_peterson)
- `deadlock.json` (DEADLOCK, no invariant)
- `double-checked-locking.json` (VIOLATION, dcl_uninitialized_observed)
- `lost-update.json` (VIOLATION, counter_equals)
- `lost-update-3t.json` (declarative three-thread lost update, expected VIOLATION)
- `torn-counter.json` (VIOLATION, torn_read)

### Migration

The built-in corpus also includes the declarative `lost-update-3t` resource, bringing it to eight programs.
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
