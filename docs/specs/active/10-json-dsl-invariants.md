# Spec 10 — JSON DSL Invariants, CLI, and Examples

## TL;DR
Complete the declarative format with composable invariants, the user-facing CLI path, and the teaching examples that prove the DSL is real. Invariants for `declarative` programs are pure expression predicates (with conjunction/reuse), the existing `check --file` path already dispatched by Spec 09 now carries invariants end-to-end, and two curated example programs (bounded buffer, semaphore) ship as JSON. The spec's anchor is a differential test: a declarative re-encoding of `lost-update` must match `BugCorpus`'s Java version on verdict and on explored-state count.

## Current State
- Spec 09 [assumed, ships in same wave] delivers `format` dispatch (`typed` / `declarative`), declarative `DynamicState`/`DynamicStep`, sandboxed expression language, deterministic encoding, POR-correct `reads()`/`writes()` derivation, and the single-predicate declarative invariant `{"expr": "…"}`. `ProgramLoader` loads `declarative` programs; conjunction is not yet supported.
- `typed` invariants today [verified]: `InvariantRegistry` maps 5 named types (e.g. `counter_equals`, `mutual_exclusion_peterson`) to factories; `BenchmarkProgram` carries an optional `Invariant`.
- CLI [verified]: `Main --file <path>` calls `ProgramLoader.loadFromFile`; `BenchmarkHarness`/`DfsExplorer`/`StaticPorExplorer`/`DporExplorer` run the resulting `Program` + optional `Invariant` unchanged.
- No declarative invariant conjunction, no curated DSL examples beyond the minimal lost-update shape, no differential equivalence test [verified].

## Invariants
- **Backwards compatibility:** every `format: "typed"` program — including its named `invariant.type` — keeps its identical verdict and states-explored count.
- **Invariant purity:** a `declarative` invariant is a pure predicate over shared state only — it never references `local.*` and never mentions `tid`; any `local.*` or `tid` in `invariant.expr`/`all` is a load error. It has no effects, never mutates state, never allocates an unbounded resource, and is bounded by the same AST limits as guards.
- **Same oracle, same harness:** `declarative` programs run through the unchanged `DfsExplorer`/POR explorers and `BenchmarkHarness`; no new scheduler semantics, no invariant-specific fast path that could diverge from the oracle. Evaluation errors at model-check runtime (OOB, `%` by zero) surface as `VIOLATION` with a trace, never as a checker crash.
- **Deterministic reporting:** the same `declarative` file + invariant explores identically and, when imported via the DSL builder methods, produces the same `VerificationResult`/`TestResult` as the equivalent Java-constructed program.

## Acceptance Criteria
- **Declarative invariant shape** — a `declarative` file's top-level `invariant` is exactly one of:
  1. `{"expr": "<predicate>"}` — single predicate string (introduced in Spec 09; Spec 10 keeps the contract and adds conjunction).
  2. `{"all": ["<predicate>", "<predicate>", ...]}` — conjunction (1..16 predicates) evaluated short-circuit as `&&`; empty `all` is a load error. This form is introduced in Spec 10.
  Invariant strings use the same expression language and type-check rules as Spec 09, with the restriction that they may reference only shared fields and array elements (never `local.*` and never `tid` — referencing either is a load error at `invariant.expr` or `invariant.all[i]`). Any predicate that type-checks to non-`bool`, mentions an unknown field, or exceeds AST bounds throws `RegistryException` with the offending JSON path (`invariant.expr` or `invariant.all[i]`). A `declarative` file must not use `invariant.type` (typed-only); if present it is a load error naming `invariant.expr`/`all` as the valid forms.
- **Typed invariant path unchanged** — `format: "typed"` files continue to use `invariant.type` as today. `invariant.expr`/`all` in a `typed` file is a load error. The 5 existing named invariants remain registered and unmodified.
- **Error reporting** — a failing invariant produces the same `VIOLATION` trace as today, with the violating configuration's encoded state. The trace's first violating step is the schedule prefix that reaches the bad configuration; invariant or step evaluation errors at model-check runtime after successful load (e.g. array OOB, `%` by zero — should be unreachable if guards are correct) are reported as `VIOLATION` with a diagnostic and the violating trace, not as a checker crash.
- **CLI** — no new flags. The existing `Main --file <path>` already dispatches via Spec 09; this spec requires that a `declarative` file with an `invariant` carried through that path produces the same verdict as `InterleaveRunner` programmatically built from the same file. Bounds-exceeded or type-error files produce a non-zero exit with `RegistryException` message on `stderr` that includes the JSON path.
- **Examples** — two curated programs ship as JSON and are green in CI:
  - `examples/programs/bounded-buffer-declarative.json` — `format: "declarative"`, shared `buf: int[3]`, `head`/`tail`/`count`/`capacity`, per-thread `locals` for staged values, producer + consumer threads, invariant `count <= capacity && count >= 0 && count <= 3`.
  - `examples/programs/semaphore-declarative.json` — `format: "declarative"`, shared `permits: int`, `acquire` guard `permits > 0` with effect `permits--`, `release` with `permits++`, two threads, invariant `permits >= 0`.
  Each example file has a co-located comment header or `README` entry describing the bug/pattern it teaches.
- **Differential equivalence test** — `DslEquivalenceTest` holds a `declarative` re-encoding of `lost-update` (two threads, `counter: int`, per-thread `local.r`, steps `local.r = counter` / `counter = local.r + 1`, invariant `counter == 2`, `expected_verdict: "VIOLATION"`). The test runs that file through `ProgramLoader.loadFromFile` and each explorer (`DfsExplorer`, `StaticPorExplorer`, `DporExplorer`) and asserts:
  - verdict `VIOLATION` matches `BugCorpus.lostUpdate()`'s verdict (and equals `expected_verdict`),
  - `statesExplored` is exactly equal across `typed` vs `declarative` for each explorer (exact equality — the derivation for this scalar+per-thread-local program is precise; any divergence is a derivation bug). A helper also asserts the failing trace from the `declarative` program replays through `ExecutionDriver` to a violating configuration.
- **README/docs** — `README.md` gains a "Program formats" section documenting both `format: "typed"` and `format: "declarative"` with a minimal example of each and a pointer to the example files. `docs/` or `examples/programs/README.md` lists the available example programs with their invariant in one line.
- **Docstrings & tests** — every new public type and exported method carries Javadoc (`@param`/`@returns`/`@throws`); each file carries a top-of-file summary comment. Test coverage includes load-error paths (unknown field, duplicate name, `local.*` in invariant, `tid` in invariant, type errors, bound violations, unknown format), `invariant.type`-in-declarative rejection, and the two examples.
- A passing `./gradlew build` and `./gradlew test`.

## DSL Shape (frozen for implementation)

### Declarative invariant forms

| JSON shape | example | semantics |
|---|---|---|
| `{"expr": "counter == 2"}` | single predicate | `Invariant.holds` evaluates the predicate; non-bool predicate is a load error |
| `{"all": ["count >= 0", "count <= capacity", "head < 3"]}` | conjunction | `holds` is `p0 && p1 && …`; short-circuit left-to-right; `all` with `0` or `>16` entries is a load error |

`invariant` is optional. When absent the program has no invariant (POR may use the invariant-absent path, which is allowed to prune more — behavior is unchanged per explorer). Postfix: if `invariant` contains `type` in a `declarative` file, throw `RegistryException` with `Use invariant.expr or invariant.all in format \"declarative\"; invariant.type is for format \"typed\".`.

Typed invariants are the 5 builtins already in `InvariantRegistry` — `mutual_exclusion_peterson`, `counter_equals`, etc. — and accept their existing param shapes. No new typed invariant type is added in this spec.

### Examples (frozen shapes — exact literal values may adjust by one, structure is frozen)

#### `bounded-buffer-declarative.json`

```json
{
  "format": "declarative",
  "name": "bounded-buffer-declarative",
  "state": {
    "fields": [
      {"name": "buf", "type": "int[]", "init": [0, 0, 0]},
      {"name": "head", "type": "int", "init": 0},
      {"name": "tail", "type": "int", "init": 0},
      {"name": "count", "type": "int", "init": 0},
      {"name": "capacity", "type": "int", "init": 3}
    ],
    "locals": [{"name": "v", "type": "int", "init": 0}]
  },
  "threads": [
    {"id": 0, "steps": [
      {"guard": "count < capacity", "effects": ["buf[tail] = 1", "tail = (tail + 1) % 3", "count = count + 1"]},
      {"guard": "count < capacity", "effects": ["buf[tail] = 2", "tail = (tail + 1) % 3", "count = count + 1"]}
    ]},
    {"id": 1, "steps": [
      {"guard": "count > 0", "effects": ["local.v = buf[head]", "head = (head + 1) % 3", "count = count - 1"]},
      {"guard": "count > 0", "effects": ["local.v = buf[head]", "head = (head + 1) % 3", "count = count - 1"]}
    ]}
  ],
  "invariant": {"all": ["count >= 0", "count <= capacity", "count <= 3", "head >= 0", "head < 3", "tail >= 0", "tail < 3"]}
}
```

#### `semaphore-declarative.json`

```json
{
  "format": "declarative",
  "name": "semaphore-declarative",
  "state": {
    "fields": [{"name": "permits", "type": "int", "init": 1}],
    "locals": []
  },
  "threads": [
    {"id": 0, "steps": [
      {"guard": "permits > 0", "effects": ["permits = permits - 1"]},
      {"guard": null, "effects": ["permits = permits + 1"]}
    ]},
    {"id": 1, "steps": [
      {"guard": "permits > 0", "effects": ["permits = permits - 1"]},
      {"guard": null, "effects": ["permits = permits + 1"]}
    ]}
  ],
  "invariant": {"expr": "permits >= 0"}
}
```

Either example may also be trimmed by one step per thread during implementation if the explorer proves the invariant holds trivially — the shape (shared `buf`/`capacity` for buffer, `permits` with guarded `acquire` for semaphore) is frozen; the exact number of producer/consumer items may adjust by one before the spec is considered superseded (archive if it changes).

### CLI contract (frozen)

```
interleave --file <path> [--strategy dfs|static-por|dpor] [--store ...] [--bitstate-...]
```

No new flags. A `declarative` file that fails validation exits non-zero; `stderr` contains the `RegistryException` message with JSON path. Success prints the same `ReportWriter` output as a `typed` run. The invariant, when present, is evaluated at every reachable `Configuration` — identical to the existing invariant path.

### Differential test (frozen)

File: `src/test/resources/dsl/lost-update-declarative.json` (same shape as the example in Spec 09, promoted to a test fixture). Test class: `src/test/java/dev/samhb/interleave/format/dsl/DslEquivalenceTest.java`.

```
load typed lost-update via BugCorpus.lostUpdate()          → typedResult
load declarative re-encoding via ProgramLoader.loadFromFile → dslResult
assert typedResult.verdict() == dslResult.verdict()
assert typedResult.verdict() == VIOLATION
for each explorer in [dfs, staticPor, dpor]:
  assert dslStatesExplored within 5% of typedStatesExplored  // over-report is allowed, wild divergence is a derivation bug
  assert ExecutionDriver replay of dsl's failing trace reaches a configuration where invariant is false
```

## Out of Scope
- Any new expression operator beyond Spec 09's grammar (no `/`, no new built-ins like `min`/`max`, no string ops).
- Nondeterministic choice, fairness, or liveness — single-path `VIOLATION`/`DEADLOCK` verdicts only.
- Invariant parameterization (e.g. `counter_equals(expected=2)` style) for `declarative` — predicates are self-contained strings.
- Web UI / visualizer — JSON remains the artifact; trace rendering stays in `ReportWriter`/`BenchmarkHarness`.
- Auto-repair / synthesis of a correct program from a failing one.

## Commands
```bash
./gradlew test --tests "*format.dsl*"
./gradlew test --tests "*DslEquivalenceTest*"
./gradlew run --args="--file examples/programs/bounded-buffer-declarative.json"
./gradlew run --args="--file examples/programs/semaphore-declarative.json"
```

## Map
- `src/main/java/dev/samhb/interleave/format/dsl/DslInvariant.java` — predicate / conjunction invariant for `declarative`
- `src/main/java/dev/samhb/interleave/format/dsl/DslLoader.java` — extension for `invariant.expr`/`all` handling (extends Spec 09 work)
- `src/main/java/dev/samhb/interleave/cli/Main.java` — no new flags; existing `--file` path verified for declarative
- `examples/programs/bounded-buffer-declarative.json` + `examples/programs/semaphore-declarative.json` — curated teaching examples
- `src/test/resources/dsl/lost-update-declarative.json` — differential fixture
- `src/test/java/dev/samhb/interleave/format/dsl/DslEquivalenceTest.java` — verdict + explored-state equivalence
- `src/test/java/dev/samhb/interleave/format/dsl/DslInvariantTest.java` — `expr`/`all` validation, type errors, bound errors, `type`-in-declarative rejection
- `README.md` — "Program formats" section + example index
- `examples/programs/README.md` — one-line catalog of example programs (if not already present)
