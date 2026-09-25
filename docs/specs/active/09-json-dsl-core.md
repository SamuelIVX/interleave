# Spec 09 — JSON DSL Core (Declarative State and Steps)

## TL;DR
Introduce `format: "declarative"` as the second program format alongside the existing `format: "typed"` registry form. Authors declare state shape (`fields` + per-thread `locals`), then declare steps as pure guard + effect sequences over a tiny sandboxed expression language. A new `DynamicState` and `DynamicStep` implement `SharedState`/`Step` with deterministic canonical encoding and POR-correct `reads()`/`writes()` derivation. `ProgramLoader` dispatches on `format` and remains fully backwards compatible — every existing `typed` program keeps its identical verdict and explored-state count.

## Current State
- `format/ProgramLoader` (259 lines) [verified] validates `name`/`state.type`/`threads[].steps[].type`, sequential thread IDs `0..N-1`, `other` bounds, invariant compatibility, and builds `BenchmarkProgram` via `StateRegistry` (5 types), `StepRegistry` (19 steps), `InvariantRegistry`.
- `Step` contract [verified]: `Set<MemoryLocation> reads()`, `writes()`, `boolean enabled(state)`, `StepOutcome execute(state)` — POR (`IndependenceRelation`, `StaticPorExplorer`, `DporExplorer`) reads `reads()`/`writes()` directly; under-reporting is unsound.
- `SharedState.deepCopy()` + `encodeTo(DataOutput)` [verified] feeds `HashingStateStore`, `BitstateStore`, and `CanonicalEncoder`; determinism requires stable field order in encoding.
- `CounterState` [verified] holds per-thread `registers[]` to model read-modify-write; DSL needs equivalent per-thread locals or it cannot express `lost-update`.
- 8 JSON program files [verified] in `src/main/resources/programs/` + `examples/programs/` are loaded through `ProgramLoader` via `BugCorpus` as benchmark ground truth.
- No expression evaluator, no `DynamicState`/`DynamicStep` [verified] — `format` field does not exist yet.

## Invariants
- **Backwards compatibility:** every program with `format: "typed"` (or legacy files migrated to that value) loads and explores with byte-identical outcome to pre-09. No change to the `typed` path's validation, registries, or state encoding.
- **POR soundness:** for a `declarative` step, `reads()`/`writes()` must be a conservative over-approximation of the locations the step may touch when `enabled` is checked and `execute` runs. Over-reporting is allowed (less reduction); under-reporting is a soundness bug and is forbidden.
- **Canonical encoding:** `DynamicState.encodeTo` writes shared fields then locals in a deterministic order (declaration order) with explicit lengths for arrays, so two states that are semantically equal encode to byte-identical sequences. No field may be omitted from encoding.
- **Sandbox:** the expression language has no loops, no recursion, no reflection, no I/O, and no access to any Java API — only state fields, per-thread locals, and thread-local `tid` where documented. Evaluation depth and node count are bounded regardless of input.
- **Determinism:** a declarative program with the same JSON + seed explores identically across runs (no hidden global `Random`, no thread-local state leakage).

## Acceptance Criteria
- `format` dispatch: `ProgramLoader` requires top-level `format` in every file. Values are exactly `"typed"` and `"declarative"`. Missing, null, non-string, or unknown value throws `RegistryException` whose message lists valid values and the offending JSON path.
- Migration: all 8 in-repo JSON files contain `format: "typed"` after this spec ships. Existing tests and `BugCorpus` continue to pass without behavioral change (verified by a regression test loading each via `loadFromResource`).
- Declarative schema validation (all errors include a JSON path like `threads[1].steps[0].guard` or `state.fields[2]`):
  - `name` non-blank, `state.fields` present and non-empty when `format: "declarative"`, at most 32 shared fields; each field declares `type` in `{int, bool, int[]}` and an initial value of the matching JSON shape; array length in `[0, 64]` at load time.
  - `state.locals` optional; when present each entry declares `type` in `{int, bool}` with init; locals are per-thread, never cross-thread conflicting.
  - `threads` has `1..8` entries with sequential `id`; each `steps` non-empty, each `steps` total across all threads `<= 512`; each step declares `effects` (non-empty list of assignments) and optional `guard`; invalid syntax, unknown field/local name, or arity mismatch throws `RegistryException`.
- Expression language: int literals, bool literals, field refs (`x`), local refs (`local.r`), array element refs (`q[expr]`), `tid`, unary `!`/`-`, binary `+ - * % == != < <= > >= && ||`, parentheses. Effects are a sequence of assignments `lhs = expr` where `lhs` is a field, local, or array element `q[expr]`. All sub-expressions are type-checked at load time; type mismatch (e.g. `bool + int`) is a load error.
- Bounds: any single program's total AST node count `<= 5000`, expression depth `<= 16`, guard/effect list per step `<= 16` entries. Exceeding a bound throws `RegistryException` naming the bound and the violating path.
- Runtime: `DynamicState.deepCopy` copies all fields and all per-thread locals; `encodeTo` is deterministic (see Invariants) and round-trips through `Configuration` hashing; `DynamicStep.enabled(state)` evaluates `guard` (absent guard is `true`); `execute(state)` evaluates `effects` left-to-right on the shared state and the owning thread's locals only.
- POR derivation: for each declarative step, `reads()` and `writes()` are derived statically from the guard and effects (not user-declared). Scalars cover their own `MemoryLocation`; array element with constant integer index covers `name[index]`; non-constant index or any non-trivial index expression covers whole-array `name`. Locals map to per-thread locations `t<id>.<name>` (never conflicting across threads). Derivation is over-approximate — no under-report is permitted; a dedicated test asserts this by comparing derived sets against the set of locations actually touched during evaluation.
- Explorers: `DfsExplorer`, `StaticPorExplorer`, `DporExplorer` show no behavior change on `typed` programs; on `declarative` programs they explore via `DynamicState`/`DynamicStep` without additional wiring.
- A passing `./gradlew build` and `./gradlew test`.

## DSL Shape (frozen for implementation)

### Format dispatch
| `format` value | meaning | state object | step objects |
|---|---|---|---|
| `"typed"` | legacy registry form (5 state types / 19 step types) | `StateRegistry.create(state)` | `StepRegistry.create(step, tid)` |
| `"declarative"` | new DSL form | `DynamicState` | `DynamicStep` per entry in `threads[].steps` |

`format` is **required**. Unknown value message is exactly: `Unknown format '<value>'. Valid formats: [typed, declarative]` (order as listed). Missing/null/non-string `format` message names the field and lists valid values.

#### Example (minimal declarative)

```json
{
  "format": "declarative",
  "name": "lost-update-declarative",
  "state": {
    "fields": [
      {"name": "counter", "type": "int", "init": 0}
    ],
    "locals": [
      {"name": "r", "type": "int", "init": 0}
    ]
  },
  "threads": [
    {
      "id": 0,
      "steps": [
        {"guard": null, "effects": ["local.r = counter"], "name": "read"},
        {"guard": null, "effects": ["counter = local.r + 1"], "name": "write"}
      ]
    },
    {
      "id": 1,
      "steps": [
        {"guard": null, "effects": ["local.r = counter"], "name": "read"},
        {"guard": null, "effects": ["counter = local.r + 1"], "name": "write"}
      ]
    }
  ],
  "invariant": {"expr": "counter <= 2"}
}
```

### Declarative state schema

| JSON path | type | required | constraints |
|---|---|---|---|
| `state.fields` | array of field decl | yes | `1..32` entries |
| `state.fields[].name` | string | yes | `[a-z][a-z0-9_]*`, unique across `fields` and `locals`, not `local`/`tid` |
| `state.fields[].type` | string | yes | `int` | `bool` | `int[]` |
| `state.fields[].init` | literal or array | yes | `int` for `int`, `bool` for `bool`, JSON array of `int` (length `0..64`) for `int[]` |
| `state.locals` | array of local decl | no | `0..8` entries, same `name`/`type`/`init` rules; `int[]` not allowed in locals |
| `threads` | array | yes | `1..8` entries, `id` is `0..N-1` sequential, each `steps` non-empty |
| `threads[].steps` | array | yes | each step is `{effects, guard?, name?}` |

Shared fields are global; locals are per-thread (`state.locals` defines the shape, each thread gets its own copy initialized from `init`).

### Declarative step shape

| field | type | required | semantics |
|---|---|---|---|
| `effects` | string[] | yes | `1..16` assignments `lhs = expr`, evaluated left-to-right on `enabled`==true; empty effects is invalid |
| `guard` | string or null | no | predicate; absent or `null` means `true`; evaluation returning non-bool is a load-time type error |
| `name` | string | no | diagnostic only, ignored by the checker |
| `expr` inside guard/effects | string | — | parsed by the sandboxed language (grammar below) |

Legacy `type`-based step objects are **not** permitted inside a `declarative` file (load error at `threads[].steps[].type`).

### Expression grammar (frozen, deterministic-only)

```
expr   ::= or
or     ::= and ( "||" and )*
and    ::= cmp ( "&&" cmp )*
cmp    ::= add ( ("==" | "!=" | "<" | "<=" | ">" | ">=") add )*
add    ::= mul ( ("+" | "-") mul )*
mul    ::= unary ( ("*" | "%") unary )*
unary  ::= ("!" | "-")* primary
primary::= INT | BOOL | "tid" | IDENT | IDENT "[" expr "]" | "local." IDENT | "local." IDENT "[" expr "]" | "(" expr ")"
```

- `INT` is a JSON int literal inside the string (`-2147483648..2147483647`); overflow during evaluation wraps as Java `int` (documented, deterministic).
- `BOOL` is `true`/`false`.
- `IDENT` is `[a-z][a-z0-9_]*`; must name a declared field or local (with `local.` prefix for locals).
- Division `/` is **not** in v1 (avoid divide-by-zero observability questions); use `*` and `%` only.
- Every expression is type-checked at load: comparisons require `int` operands producing `bool`; `&&`/`||`/`!` require `bool`; arithmetic requires `int`; `q[expr]` requires `int[]` base and `int` index with bounds check at evaluation (out-of-bounds `OOB` traps as `RegistryException` at execution time if ever reached, but a `guard` should prevent it — OOB during a model-check run is reported as `VIOLATION` with the violating trace, not a crash).
- Node count and depth are counted on the parsed AST; exceeding bounds is a load-time `RegistryException` naming the offending path and the bound.

### MemoryLocation derivation (POR — frozen)

Derived once at load from the textual `guard` + all `effects` (not observed at runtime):

| syntactic form | `reads()` / `writes()` entry |
|---|---|
| read of shared field `x` | `x` |
| write of shared field `x` (`x = …` or `x[i] = …`) | `x` (whole-array cases) or `x[i]` per below |
| shared array element `q[CONST]` read or written | `q[CONST]` (e.g. `q[0]`) |
| shared array element `q[nonConst]` read or written | `q` (whole array — conservative) |
| local `local.r` read or written by owning thread `t` | `t<t>.r` (e.g. `t0.r`) — never conflicts with `t1.r` |
| guard expression touching `x` | `x` (or `q`/`q[i]` / `t.r` per above) in `reads()` |

No user-supplied `reads`/`writes` are accepted; the derived sets are the contract for `IndependenceRelation`.

### `DynamicState` / `DynamicStep` (frozen)

```
package dev.samhb.interleave.format.dsl;

final class DynamicState implements SharedState
  DynamicState(StateDecl decl, int threadCount)           // completes at Construction
  int  getInt(String field)
  void setInt(String field, int v)
  boolean getBool(String field) / setBool(...)
  int[] getArray(String field)  // copy-on-read; set via element assignment only
  int  getLocal(int tid, String name) / setLocal(...)
  SharedState deepCopy()           // copies all shared fields + all per-thread locals
  void encodeTo(DataOutput out)    // fields in declaration order: type tag, length for arrays, then values; then locals per tid in id order

final class DynamicStep implements Step
  DynamicStep(int owner, String guardSrc, List<String> effectsSrc, StateDecl decl, Set<MemoryLocation> derivedReads, Set<MemoryLocation> derivedWrites)
  Set<MemoryLocation> reads() / writes()     // derived at construction (above table)
  boolean enabled(SharedState s)             // evaluates guard on s (or true)
  StepOutcome execute(SharedState s)         // evaluates effects on s left-to-right; returns COMPLETED
```

`encodeTo` order is: for each `fields[i]` in declaration order — write type ordinal, then for `int[]` write length then elements as `int`s; for `int`/`bool` write value. Then for `tid = 0..N-1`, for each `locals[j]` in declaration order, write value. No field is omitted.

### Limits (frozen — `RegistryException` on violation, message names the limit)

| limit | value | scope |
|---|---|---|
| shared fields | `32` | per program |
| locals | `8` | per program |
| threads | `8` | per program |
| array length (`int[]` init or runtime size) | `64` | per array |
| steps per thread | `64` | per thread |
| total steps | `512` | per program |
| assignments per step (`effects` length) | `16` | per step |
| AST node count | `5000` | per program |
| AST depth | `16` | per expression |
| identifier length | `64` | per name |

## Out of Scope
- Division operator `/` and any floating-point or string types — deferred (no `double`/`String` fields in v1).
- Nondeterministic choice (`choose`/`havoc`/`assume`), fairness, or liveness — determinism-only.
- Division-by-zero / modulo-by-zero as a separate schedule outcome — those trap deterministically (see grammar note).
- User-declared `reads`/`writes` — derived only.
- `format: "typed"` schema changes (no new built-in state/step types in this spec; registry extension is future work).
- Parallel exploration or on-disk state storage.

## Commands
```bash
./gradlew test --tests "*format.dsl*"
./gradlew test --tests "*ProgramLoader*"
./gradlew run --args="--file examples/programs/lost-update.json"
```

## Map
- `src/main/java/dev/samhb/interleave/format/dsl/StateDecl.java` — field/local declarations
- `src/main/java/dev/samhb/interleave/format/dsl/Expression.java` + `Parser.java` + `Interpreter.java` — sandboxed grammar, type-check, evaluation
- `src/main/java/dev/samhb/interleave/format/dsl/DynamicState.java` — `SharedState` with deterministic `encodeTo`
- `src/main/java/dev/samhb/interleave/format/dsl/DynamicStep.java` — `Step` with derived `reads()`/`writes()`, `enabled`/`execute`
- `src/main/java/dev/samhb/interleave/format/dsl/DslLoader.java` — `declarative` branch used by `ProgramLoader`
- `src/main/java/dev/samhb/interleave/format/ProgramLoader.java` — `format` dispatch (no behavior change on `typed` path)
- `src/main/java/dev/samhb/interleave/format/model/ProgramDefinition.java` — `format` field
- `src/main/resources/programs/*.json` + `examples/programs/*.json` — migrated to `format: "typed"`
- `src/test/java/dev/samhb/interleave/format/dsl/` — unit tests for parser/type-check/encoding/derivation/bounds
