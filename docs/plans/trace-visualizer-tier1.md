# Tier 1 Visualizer — Static Trace Renderer (Plan)

## Goal
Render a single failing `TraceRecord` as a readable, shareable HTML page without touching the checker. The viewer consumes existing `--json` output and requires zero changes to `DporExplorer`/`DfsExplorer`/`StateStore`.

Sequenced before Tier 2 (state-space DAG). If this proves useful, Tier 2 gets its own spec.

## Non-Goal (Tier 2)
Full state-space DAG, graph layout, `--emit-graph` instrumentation. Explicitly out of scope here — park until this ships (see `future-work.md` #6).

## Integration with interleave — where it plugs in
No Java/Gradle changes. The viewer is a pure consumer of artifacts the repo already emits — but those artifacts do **not** share one shape. The viewer normalizes them client-side:

| Existing surface | Path | Raw JSON shape (actual) | What tier 1 normalizes to |
|---|---|---|---|
| `TraceRecord` | `src/main/java/dev/samhb/interleave/TraceRecord.java:37` (`toJson()`) | `{"threads": [int], "outcomes": ["…"], "outcome": "VIOLATION\|DEADLOCK\|COMPLETED", "programHash": "…"}` | Single trace |
| `TestResult` | `src/main/java/dev/samhb/interleave/TestResult.java:65` | `{"strategy": "…", "statesExplored": n, "wallTimeMs": n, "heapDeltaBytes": n, "hasViolation": bool, "limitExceeded": bool, "failingTraces": [TraceRecord…], "deadlockedTraces": […], "completedTraces": […]}` | First `failingTraces[0]` preferred, else `deadlockedTraces[0]` |
| `VerificationResult` | `src/main/java/dev/samhb/interleave/VerificationResult.java:96` | `{"strategy": "…", "statesExplored": n, "wallTimeMs": n, "heapDeltaBytes": n, "hasViolation": bool, "failingTraces": [{"threads":[…], "outcome":"…"}], "deadlockedTraces": […], "completedTraces": […]}` — note: traces inline as `{"threads","outcome"}` without `outcomes`/`programHash` | Same selection rule; `outcomes` absent → timeline renders from `threads` only |
| `ReportWriter` | `src/main/java/dev/samhb/interleave/report/ReportWriter.java:66` (`writeJson()`) | `{"benchmarks": [{"bug":"…", "strategy":"…", "storeType":"…", "statesExplored": n, "wallTimeMs": n, "heapDeltaBytes": n, "verdict":"…", "failingTrace": {"threadIds":[int], "outcomes":["…"]}}], "soundness": bool}` — `threadIds` not `threads` | Unwrap `benchmarks[i].failingTrace` → trace; `threadIds` aliased to `threads`; `verdict` used for header |
| `Main` CLI | `src/main/java/dev/samhb/interleave/cli/Main.java:243` (`gson.toJson` / `ReportWriter`) | Either `--file <path> --json` → single `ReportWriter`-style `benchmarks` entry, or `--all --json` → full `benchmarks` array | Same unwrap as above |
| Curated programs | `examples/programs/*.json` (typed) + `examples/programs/*-declarative.json` | Program JSON with `format`, `fields`/`locals`, `invariant` | **Optional** second input for state-strip header (field/local names); not required for schedule render |
| Sample outputs | `examples/traces/` (new, checked-in) — `lost-update.json`, `peterson.json` captured via `./gradlew run --args="--file examples/programs/lost-update.json --json"` and normalized to viewer schema for demo | Demo fixtures already normalized; no generation at build time | — |

**Loader adapters (in JS, not Java):** `normalize(raw)` inspects `raw` and returns viewer schema `{trace: {threads, outcomes?, outcome, programHash?}, meta: {verdict?, strategy?, statesExplored?, wallTimeMs?, bug?}}`:
- if `raw.threads` → raw is `TraceRecord`
- else if `raw.failingTraces`/`benchmarks` → pick first failing/deadlocked trace as above
- else if `raw.benchmarks` → unwrap `benchmarks[i].failingTrace`
- alias `threadIds` → `threads` where needed

No new CLI flag, no new artifact. Viewer loads JSON via file picker + paste box, runs `normalize()`, then `validate()` (requires `trace.threads` array); parse error shown inline on failure.

Build/test contract: `gradle test` unaffected; viewer is static and ignored by Java build (no `build.gradle` change). CI stays green.

## Input contract (already exists) + missing-state behavior
Raw producers emit **schedule only**. None of the shapes above include `fields`/`locals` per-config snapshots — `TraceRecord`, `TestResult`, `VerificationResult`, and `ReportWriter` carry only the schedule (`threads`/`threadIds` + `outcomes`/`outcome`). Tier 1 therefore has two rendering modes:
- **Schedule-only (default):** timeline renders from `threads` alone; state strip shows program-declared `fields`/`locals` names as a header (if a program JSON was also loaded) and a **“no per-config snapshot — schedule only”** placeholder for values. This is the expected path for all current artifacts.
- **With snapshots (future, not required):** if a future harness emits per-config state (e.g. via `DfsResult` snapshots), the same `normalize()` will carry `states: [{fields:{…}, locals:{…}}]` through. Until then the strip degrades gracefully — missing `fields`/`locals` is not an error.

## UX — minimal useful version
1. **Load** — file picker + paste box; runs `normalize(raw)` then `validate(trace.threads)` — accepts any of the raw shapes above (`TraceRecord`, `TestResult`, `benchmarks` wrapper, either `threads` or `threadIds`). Validates after normalization; shows parse/adapter error inline. Optional second picker for program JSON (`format`, `fields`/`locals`) to populate state-strip header. Validates `format`/`expected_verdict` only when a program file is provided.
2. **Header** — program name (`bug` or `programHash`), verdict (`PASS`/`VIOLATION`/`DEADLOCK` derived from `outcome`/`verdict`/`hasViolation`), strategy/store used, `statesExplored`, time. Fields sourced from `meta` after normalization.
3. **Timeline** — one row per config (0 = initial), columns = threads. Active step cell highlighted, invariant evaluation shown, failing step row in red. Hover shows `guard`/`effects` text when a program file is loaded; otherwise shows thread/step index. Renders from `threads` even when `outcomes` is absent (`VerificationResult` path).
4. **State strip** — below timeline, header shows `fields` + `locals` names from the optional program JSON (generic table so `DynamicState` heterogeneous `fields` works). Selected-row values show **“no snapshot — schedule only”** placeholder because current artifacts do not include per-config `fields`/`locals` values; if a future harness provides `states[]`, the strip renders values for the selected row.
5. **Copy** — "copy schedule" button emits the `(thread,step)` list for replay.

No animation, no graph, no server.

## Tech choice — basic web only
- **Stack: plain HTML + CSS + vanilla JS, nothing else.** Single static `docs/visualizer.html` (or `viz/trace.html` if `docs/` should stay doc-only). No npm, no bundler, no framework, no build step. Viewable on `file://` and GitHub Pages.
- **Why this is enough:** the data is already JSON; rendering is a table + detail pane. Vanilla JS (`FileReader`, `JSON.parse`, `clipboard.writeText`) covers load/validate/render/copy. Elevate with *tokens and layout*, not dependencies.
- **CSS:** one stylesheet (or `<style>` block) using `WEB-DESIGN-RULES.md` tokens — color/type/spacing/layout + one signature element (e.g. the timeline row highlight). ≤2 fonts, contrast + focus + `prefers-reduced-motion` respected. No utility framework.
- **JS:** ~150 lines module-free script: parse → validate → `renderHeader`/`renderTimeline`/`renderState` → row click handler → copy. No `fetch` to external origins, no external CDN.
- Future alternative (Vite/React) only if Tier 2 ever needs it — Tier 1 must not add it.

## Skills & MCPs to use

| Need | Skill | MCP | When |
|---|---|---|---|
| Visual polish without template drift | `frontend-design` (anthropics) + `web-design-guidelines` (vercel-labs) | — | Before styling: plan tokens (color/type/spacing/signature) per `WEB-DESIGN-RULES.md`; audit after first render |
| Verify the viewer actually renders traces | `webapp-testing` | **Playwright MCP** (`browser_navigate`, `browser_snapshot`, `browser_network_requests`) | After writing HTML: load `examples/traces/lost-update.json` + `peterson.json`, snapshot the timeline/state strip, check `file://` + Pages Serving |
| Keep it light | `performance` (addyosmani) | — | Quick pass: no render-blocking JS, single repaint on row select |
| If any JS helper grows | `tdd` | — | Only if a pure function (e.g. `parseTrace`, `scheduleToCopyText`) warrants a unit test — keep in `src/test` only if complexity appears |

Not needed: `supabase-postgres-best-practices`, `owasp-security` (no auth/secrets), `threat-modeling` (Tier 2 DAG would need it, not Tier 1).

## File layout
```
docs/visualizer.html                  # the viewer (or viz/trace.html)
docs/plans/trace-visualizer-tier1.md  # this plan
examples/traces/lost-update.json      # checked-in sample outputs for demo (generated, not hand-written)
examples/traces/peterson.json
```

## Acceptance
- Loads raw `--json` for `lost-update` (VIOLATION) and `peterson` (PASS) **in each producer shape** — `TraceRecord` direct, `TestResult` wrapper, and `ReportWriter` `benchmarks` wrapper (`threads` and `threadIds` variants) — without error; both `format: "typed"` and `format: "declarative"` programs validate when a program file is also loaded.
- Timeline renders from `threads` even when `outcomes` is absent (`VerificationResult` path); header derives `verdict` from `outcome`/`verdict`/`hasViolation`. State strip shows `fields`/`locals` header when a program file is loaded and a graceful **schedule-only placeholder** when no per-config snapshots exist — generic table handles arbitrary `DynamicState` fields.
- Works as `file://` open and as GitHub Pages static page (no server, no CORS).
- No Java/Gradle changes, no new dependencies, `gradle test` unaffected. Verified via Playwright MCP snapshots.
- Styled with design tokens; passes `web-design-guidelines` quick audit (semantic elements, labelled controls, keyboard reachability).

## Risks / notes
- **Shape variance:** the four producers use `threads` vs `threadIds`, `failingTraces` vs `failingTrace`, `outcomes` present vs absent. The JS `normalize()` adapter must cover all four — otherwise a raw `--json` paste will fail validation. Narrowing the viewer to a documented normalized schema (checked-in `examples/traces/`) is acceptable, but the adapter must be explicit in either case.
- **No per-config snapshots today:** current artifacts carry no `fields`/`locals` values, so the state strip is header-only until a harness emits `states[]`. The plan degrades gracefully rather than implying missing data is invalid (per-contract "where available").
- Heterogeneous state (when snapshots exist): `DynamicState` fields are arbitrary — renderer must be generic (key/value table), not hardcoded to `counter`/`flag`.
- Large traces: budget not needed — traces are at most `maxStates` steps and failing traces are short by nature; no pagination in tier 1.
- Styling scope creep: the temptation is a framework. Resist — tokens + one signature element beat a component library for a single-page viz.

## Next step after this ships
If used and wanted, spec `12-state-graph-emission.md` for Tier 2: `--emit-graph` schema, budget, truncation, layout policy — behind a flag with attestation re-run when off (no regression) and on (graph correctness).
