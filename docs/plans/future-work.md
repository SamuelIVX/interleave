# Future Work / Extensions

These are explicitly out of scope for the 7-spec deliverable, but are natural extensions.

## Items 1–7: Completed

| # | Item | Status |
|---|---|---|
| 1 | Library/API mode | ✅ Done (PR #1) |
| 2 | JSON program definition format | ✅ Done (PR #15) |
| 3 | Bitstate / supertrace mode | ✅ Done (PR #18) — CLI `--store`/`--strategy`/`--bitstate-*`, `BitstateStore` + metrics wired into `BenchmarkHarness`/`ReportWriter`, 143 tests |
| 4 | Property-based corpus mining | ✅ Done (PR #19) — `CorpusGenerator`/`TemplateRegistry` + 2 curated templates, `GeneratorConfig` bounds, budget-aware `DfsExplorer` oracle, `CorpusEntry` JSON persistence, `generate` CLI |
| 5 | General-purpose JSON format | ✅ Done (PR #21 + PR #22) — `09-json-dsl-core` (format dispatch, sandboxed DSL, DynamicState/DynamicStep, deterministic encoding, POR derivation) + `10-json-dsl-invariants` (composable `all`/`when` invariants, curated examples), differential `lost-update` anchor |
| 6 | Web UI / visualizer — Tier 1 (static trace renderer) | ✅ Done — `docs/visualizer.html`, `examples/traces/*.json`, pure HTML/CSS/JS, no build, loads all `--json` shapes |
| 7 | Context-bounding / CHESS-style | ✅ Done — Spec 11; preemption-bounded exploration, CLI, reporting, and visualizer integration |

## Item 9: Implemented

**#9 — Concurrent-program parser:** [Spec 16](../specs/active/16-concurrent-program-parser/README.md)
defines a bounded `.ilv` modeling language with explicit atomic blocks, branching, fixed repetition,
bounded while loops and POR/DPOR support. Implemented with CLI/library, source traces and all strategies. Spec 16 records local acceptance, mutation evidence and completed CLI review dispositions; publication/CI status is tracked on the implementation PR.

## Remaining Candidates — Ranked by LOE

| # | Item | LOE | Spec Needed? | Why |
|---|---|---|---|---|
| 10 | Symmetry reduction | High | Yes | Canonicalization is subtle; soundness must be proven |
| 11 | Relaxed memory models | Very High | Yes | Fundamental semantics change — needs formal spec |
| 12 | Real Java bytecode instrumentation | Very High | Yes | Entirely new subsystem with external deps (ASM/Javassist) |

## Deferred

**#8 — Web UI / visualizer, Tier 2 (state-space DAG): deferred indefinitely.**
The existing trace viewer provides state snapshots and change highlighting. Full graph capture,
cross-strategy semantics, and rendering add more implementation and maintenance cost than the
currently demonstrated value warrants. No graph-export spike or reduced-scope implementation is
planned. Revisit only if a concrete need emerges that the trace viewer and reduction table cannot meet.

## Item Details

3. **Bitstate / supertrace mode** — ✅ Done. Replaces exact visited sets with a bloom-filter approximation (`BitstateStore` with `k` hash functions) to trade completeness for memory. Wired into `DfsExplorer`/`DporExplorer` via `StateStore`, `BenchmarkHarness` (`--store`/`--bitstate-*`), `ReportWriter`, and `Main` CLI (PR #18).

4. **Property-based corpus mining** — ✅ Done. Generates programs from curated templates (`lost-update`, `counter-race`) via `CorpusGenerator` with seeded `Random`, bounded `GeneratorConfig`, exact `DfsExplorer` oracle (`TRUNCATED`/`VIOLATION`/`SAFE`), and persisted `CorpusEntry` (PR #19).

5. **General-purpose JSON program format** — ✅ Done (PR #21 + PR #22). Extends the JSON loader beyond the 5 hardcoded state types and 19 step types. Authors declare `format: "typed"` (legacy registry) or `format: "declarative"` (new `fields`/`locals`/`guard`/`effects` DSL with sandboxed expression language); `DynamicState`/`DynamicStep` carry deterministic `encodeTo` and POR-correct `reads()`/`writes()` derivation, composable invariants (`expr`/`all` with `when: "final"|"always"`), and two teaching examples (bounded buffer, semaphore). The DSL re-encoding of `lost-update` is the differential anchor against `BugCorpus`. Implemented and merged.

6. **Web UI / visualizer — Tier 1 (static trace renderer)** — ✅ Done. Pure static `docs/visualizer.html` (vanilla HTML/CSS/JS, no build). Normalizes all four `--json` producer shapes (`TraceRecord`, `TestResult`, `VerificationResult`, `ReportWriter`/`benchmarks`) client-side via JS `normalize()`. Renders schedule timeline, header with verdict/strategy/states/time, state-strip header with `fields`/`locals` names from optional program JSON, and graceful "no per-config snapshot" placeholder. Loads via file picker or paste; works on `file://` and GitHub Pages. Sample traces in `examples/traces/` (lost-update, peterson). No Java/Gradle changes, 143 tests unaffected.

7. **Context-bounding / CHESS-style stateless search** — ✅ Shipped as Spec 11 (7 specs, `docs/specs/active/11-context-bounded/`). Bounds the number of **preemptive** context switches (forced switches are free) instead of exploring all interleavings. `ContextBoundedExplorer` + `Strategy.CONTEXT_BOUNDED` + `TraceOutcome.INCOMPLETE`; preemption-aware `StateStore` overloads with min-count indexing; CLI `--max-preemptions` / `--iterative-deepening`; `BenchmarkHarness` + `SoundnessAttestation` + visualizer integration. Verified on the 7-program corpus: every buggy program is caught at K=2 and the correct one (`peterson`) reports `INCOMPLETE`.

   *Visualizer CI follow-up:* `docs/visualizer-normalize-check.mjs` now runs in the independent
   **Visualizer compatibility** job. It checks normalized verdicts; browser rendering and freshly
   generated Java output remain outside its coverage. This closes the existing check's CI-wiring
   gap, rather than introducing a full JavaScript test suite.

   *Remaining observation:* CBS explores *more* configurations than DFS on this corpus, so the
   reduction table shows bare counts rather than percentages for those rows.

8. **Web UI / visualizer — Tier 2 (state-space DAG)** — Deferred indefinitely; see the decision above. The earlier graph-emission proposal is not scheduled work.

9. **Concurrent-program parser** — Specification authored in [Spec 16](../specs/active/16-concurrent-program-parser/README.md); implemented with local acceptance and CLI reviews complete; see the set for measured evidence and the implementation PR for CI status. Readable source models compile into finite forward control flow with atomic interiors, explicit loop cutoffs and shared execution/reduction machinery. Actual Java project ingestion remains item #12.

10. **Symmetry reduction** — exploit thread-identity symmetry to collapse equivalent states that differ only by which thread has which ID. Subtle correctness concerns; needs careful canonicalization and proof of soundness.

11. **Relaxed memory models** — support weak consistency models (e.g., ARM/POWER) instead of assuming sequential consistency. Fundamental change to `Configuration` snapshot semantics and step execution.

12. **Real Java bytecode instrumentation** — instead of hand-written `Step` objects, instrument real Java bytecode so the checker can analyze actual concurrent programs. Requires bytecode analysis (ASM/Javassist), thread detection, and mapping bytecode to atomic steps.
