# Future Work / Extensions

These are explicitly out of scope for the 7-spec deliverable, but are natural extensions.

## Items 1–6: Completed

| # | Item | Status |
|---|---|---|
| 1 | Library/API mode | ✅ Done (PR #1) |
| 2 | JSON program definition format | ✅ Done (PR #15) |
| 3 | Bitstate / supertrace mode | ✅ Done (PR #18) — CLI `--store`/`--strategy`/`--bitstate-*`, `BitstateStore` + metrics wired into `BenchmarkHarness`/`ReportWriter`, 143 tests |
| 4 | Property-based corpus mining | ✅ Done (PR #19) — `CorpusGenerator`/`TemplateRegistry` + 2 curated templates, `GeneratorConfig` bounds, budget-aware `DfsExplorer` oracle, `CorpusEntry` JSON persistence, `generate` CLI |
| 5 | General-purpose JSON format | ✅ Done (PR #21 + PR #22) — `09-json-dsl-core` (format dispatch, sandboxed DSL, DynamicState/DynamicStep, deterministic encoding, POR derivation) + `10-json-dsl-invariants` (composable `all`/`when` invariants, curated examples), differential `lost-update` anchor |
| 6 | Web UI / visualizer — Tier 1 (static trace renderer) | ✅ Done — `docs/visualizer.html`, `examples/traces/*.json`, pure HTML/CSS/JS, no build, loads all `--json` shapes |

## In Progress & Remaining Items — Ranked by LOE

| # | Item | LOE | Spec Needed? | Why |
|---|---|---|---|---|
| 7 | Context-bounding / CHESS-style | Medium | Yes | `StateStore` preemption-aware methods, `ContextBoundedExplorer` with CHESS-style preemption counting, CLI `--max-preemptions` flag, full `BenchmarkHarness` integration |
| 8 | Web UI / visualizer — Tier 2 (state-space DAG) | Medium | Yes | `--emit-graph` schema, budget, truncation, layout policy, attestation re-run |
| 9 | Concurrent-program parser | High | Yes | Language design + parser + semantic mapping — full spec needed |
| 10 | Symmetry reduction | High | Yes | Canonicalization is subtle; soundness must be proven |
| 11 | Relaxed memory models | Very High | Yes | Fundamental semantics change — needs formal spec |
| 12 | Real Java bytecode instrumentation | Very High | Yes | Entirely new subsystem with external deps (ASM/Javassist) |

### Item Details

3. **Bitstate / supertrace mode** — ✅ Done. Replaces exact visited sets with a bloom-filter approximation (`BitstateStore` with `k` hash functions) to trade completeness for memory. Wired into `DfsExplorer`/`DporExplorer` via `StateStore`, `BenchmarkHarness` (`--store`/`--bitstate-*`), `ReportWriter`, and `Main` CLI (PR #18).

4. **Property-based corpus mining** — ✅ Done. Generates programs from curated templates (`lost-update`, `counter-race`) via `CorpusGenerator` with seeded `Random`, bounded `GeneratorConfig`, exact `DfsExplorer` oracle (`TRUNCATED`/`VIOLATION`/`SAFE`), and persisted `CorpusEntry` (PR #19).

5. **General-purpose JSON program format** — ✅ Done (PR #21 + PR #22). Extends the JSON loader beyond the 5 hardcoded state types and 19 step types. Authors declare `format: "typed"` (legacy registry) or `format: "declarative"` (new `fields`/`locals`/`guard`/`effects` DSL with sandboxed expression language); `DynamicState`/`DynamicStep` carry deterministic `encodeTo` and POR-correct `reads()`/`writes()` derivation, composable invariants (`expr`/`all` with `when: "final"|"always"`), and two teaching examples (bounded buffer, semaphore). The DSL re-encoding of `lost-update` is the differential anchor against `BugCorpus`. Implemented and merged.

6. **Web UI / visualizer — Tier 1 (static trace renderer)** — ✅ Done. Pure static `docs/visualizer.html` (vanilla HTML/CSS/JS, no build). Normalizes all four `--json` producer shapes (`TraceRecord`, `TestResult`, `VerificationResult`, `ReportWriter`/`benchmarks`) client-side via JS `normalize()`. Renders schedule timeline, header with verdict/strategy/states/time, state-strip header with `fields`/`locals` names from optional program JSON, and graceful "no per-config snapshot" placeholder. Loads via file picker or paste; works on `file://` and GitHub Pages. Sample traces in `examples/traces/` (lost-update, peterson). No Java/Gradle changes, 143 tests unaffected.

7. **Context-bounding / CHESS-style stateless search** — 🔄 In progress as Spec 11 (6 specs + `07-unchanged-paths.md`). Bounds the number of **preemptive** context switches (forced switches are free) instead of exploring all interleavings. Adds `StateStore` preemption-aware methods, `ContextBoundedExplorer` with CHESS-style preemption counting, CLI `--max-preemptions` flag, and full `BenchmarkHarness` integration. See `docs/specs/active/11-context-bounded/`.

8. **Web UI / visualizer — Tier 2 (state-space DAG)** — Full DAG visualization of the explored state space. Requires `--emit-graph` flag in explorers to emit nodes/edges (config → step → config) with budget/truncation, graph layout algorithm, and `SoundnessAttestation` re-run when enabled. Spec `12-state-graph-emission.md` needed.

9. **Concurrent-program parser** — parse a small imperative language with threads, shared variables, and atomic sections into the checker's internal model. Requires designing a small language, lexer/parser, and semantic mapping to `Step`/`SharedState`.

10. **Symmetry reduction** — exploit thread-identity symmetry to collapse equivalent states that differ only by which thread has which ID. Subtle correctness concerns; needs careful canonicalization and proof of soundness.

11. **Relaxed memory models** — support weak consistency models (e.g., ARM/POWER) instead of assuming sequential consistency. Fundamental change to `Configuration` snapshot semantics and step execution.

12. **Real Java bytecode instrumentation** — instead of hand-written `Step` objects, instrument real Java bytecode so the checker can analyze actual concurrent programs. Requires bytecode analysis (ASM/Javassist), thread detection, and mapping bytecode to atomic steps.
