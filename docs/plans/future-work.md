# Future Work / Extensions

These are explicitly out of scope for the 7-spec deliverable, but are natural extensions.

## Items 1–4: Completed

| # | Item | Status |
|---|---|---|
| 1 | Library/API mode | ✅ Done (PR #1) |
| 2 | JSON program definition format | ✅ Done (PR #15) |
| 3 | Bitstate / supertrace mode | ✅ Done (PR #18) — CLI `--store`/`--strategy`/`--bitstate-*`, `BitstateStore` + metrics wired into `BenchmarkHarness`/`ReportWriter`, 143 tests |
| 4 | Property-based corpus mining | ✅ Done (PR #19) — `CorpusGenerator`/`TemplateRegistry` + 2 curated templates, `GeneratorConfig` bounds, budget-aware `DfsExplorer` oracle, `CorpusEntry` JSON persistence, `generate` CLI |

## Remaining Items — Ranked by LOE

| # | Item | LOE | Spec Needed? | Why |
|---|---|---|---|---|
| 5 | General-purpose JSON format | Medium | Yes | New DSL — specs `09-json-dsl-core` + `10-json-dsl-invariants` drafted (typed `format: "typed"` / `format: "declarative"`, frozen schema + sandbox); implementation pending |
| 6 | Web UI / visualizer | Medium | Maybe | Trace renderer: no. Full state-space DAG viz: yes |
| 7 | Context-bounding / CHESS-style | Medium-High | Yes | New exploration strategy — correctness depends on bound semantics |
| 8 | Concurrent-program parser | High | Yes | Language design + parser + semantic mapping — full spec needed |
| 9 | Symmetry reduction | High | Yes | Canonicalization is subtle; soundness must be proven |
| 10 | Relaxed memory models | Very High | Yes | Fundamental semantics change — needs formal spec |
| 11 | Real Java bytecode instrumentation | Very High | Yes | Entirely new subsystem with external deps (ASM/Javassist) |

### Item Details

3. **Bitstate / supertrace mode** — ✅ Done. Replaces exact visited sets with a bloom-filter approximation (`BitstateStore` with `k` hash functions) to trade completeness for memory. Wired into `DfsExplorer`/`DporExplorer` via `StateStore`, `BenchmarkHarness` (`--store`/`--bitstate-*`), `ReportWriter`, and `Main` CLI (PR #18).

4. **Property-based corpus mining** — ✅ Done. Generates programs from curated templates (`lost-update`, `counter-race`) via `CorpusGenerator` with seeded `Random`, bounded `GeneratorConfig`, exact `DfsExplorer` oracle (`TRUNCATED`/`VIOLATION`/`SAFE`), and persisted `CorpusEntry` (PR #19).

5. **General-purpose JSON program format** — ✅ Spec drafted (`09-json-dsl-core` + `10-json-dsl-invariants`). Extends the JSON loader beyond the 5 hardcoded state types and 19 step types. Authors declare `format: "typed"` (legacy registry) or `format: "declarative"` (new `fields`/`locals`/`guard`/`effects` DSL with sandboxed expression language); `DynamicState`/`DynamicStep` carry deterministic `encodeTo` and POR-correct `reads()`/`writes()` derivation, composable invariants (`expr`/`all`), and two teaching examples (bounded buffer, semaphore). The DSL re-encoding of `lost-update` is the differential anchor against `BugCorpus`. Pending implementation.

6. **Web UI / visualizer** — render the interleaving tree, state space DAG, or failing trace visually. Can start as a minimal HTML trace renderer; complexity grows with visualization depth.

7. **Context-bounding / CHESS-style stateless search** — bound the depth of context switches instead of exploring all interleavings. Algorithmic addition on top of DFS; needs new exploration strategy but reuses core types.

8. **Concurrent-program parser** — parse a small imperative language with threads, shared variables, and atomic sections into the checker's internal model. Requires designing a small language, lexer/parser, and semantic mapping to `Step`/`SharedState`.

9. **Symmetry reduction** — exploit thread-identity symmetry to collapse equivalent states that differ only by which thread has which ID. Subtle correctness concerns; needs careful canonicalization and proof of soundness.

10. **Relaxed memory models** — support weak consistency models (e.g., ARM/POWER) instead of assuming sequential consistency. Fundamental change to `Configuration` snapshot semantics and step execution.

11. **Real Java bytecode instrumentation** — instead of hand-written `Step` objects, instrument real Java bytecode so the checker can analyze actual concurrent programs. Requires bytecode analysis (ASM/Javassist), thread detection, and mapping bytecode to atomic steps.
