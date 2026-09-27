# Tier 1 Visualizer — Static Trace Renderer (Plan)

## Goal
Render a single failing `TraceRecord` as a readable, shareable HTML page without touching the checker. The viewer consumes existing `--json` output and requires zero changes to `DporExplorer`/`DfsExplorer`/`StateStore`.

Sequenced before Tier 2 (state-space DAG). If this proves useful, Tier 2 gets its own spec.

## Non-Goal (Tier 2)
Full state-space DAG, graph layout, `--emit-graph` instrumentation. Explicitly out of scope here — park until this ships (see `future-work.md` #6).

## Integration with interleave — where it plugs in
No Java/Gradle changes. The viewer is a pure consumer of artifacts the repo already emits:

| Existing surface | Path | What tier 1 reads |
|---|---|---|
| `TraceRecord` | `src/main/java/dev/samhb/interleave/TraceRecord.java:8` (`toJson()`) | Linear schedule `[(thread, step)]` + per-config state snapshots |
| `TestResult` / `VerificationResult` | `src/main/java/dev/samhb/interleave/TestResult.java:65`, `VerificationResult.java:96` | Same trace wrapped with `verdict`, `statesExplored`, `time` |
| `ReportWriter` / `BenchmarkHarness` | `src/main/java/dev/samhb/interleave/report/ReportWriter.java`, `BenchmarkHarness.java` | `--all --json` benchmark JSON — trace embedded per program |
| `Main` CLI | `src/main/java/dev/samhb/interleave/cli/Main.java:243` (`gson.toJson`) | `--file <path> --json` single-program JSON |
| Curated programs | `examples/programs/*.json` (typed) + `examples/programs/*-declarative.json` (tier 1 must handle both) | Program JSON + `expected_verdict` for header validation |
| Sample outputs | `examples/traces/` (new, checked-in) — `lost-update.json`, `peterson.json` captured via `./gradlew run --args="--file examples/programs/lost-update.json --json"` | Demo fixtures; no generation at build time |

Load path: CLI `--json` → file picker / paste box in the HTML → `JSON.parse` → validate shape (`format`, `expected_verdict`, `verdict`, `traces`) → render. No server, no fetch to local filesystem, no new CLI flag.

Build/test contract: `gradle test` unaffected; viewer is static and ignored by Java build (no `build.gradle` change). CI stays green.

## Input contract (already exists)
- `TraceRecord.toJson()` / `TestResult.toJson()` / `VerificationResult.toJson()` — linear schedule `[(thread, step)]` plus state snapshots where available
- `ReportWriter` benchmark JSON (`--all --json`, `--file <path> --json`) — same trace embedded

No new artifact, no new CLI flag. Viewer loads JSON via file input or paste.

## UX — minimal useful version
1. **Load** — file picker + paste box; validates `format` / `expected_verdict` / `traces` shape, shows parse error inline.
2. **Header** — program name, verdict (`PASS`/`VIOLATION`/`DEADLOCK`), strategy/store used, `statesExplored`, time.
3. **Timeline** — one row per config (0 = initial), columns = threads. Active step cell highlighted, invariant evaluation shown, failing step row in red. Hover shows `guard`/`effects` text.
4. **State strip** — below timeline, current `fields` + `locals` for selected row. Generic table rendering so `DynamicState` (heterogeneous `fields`) works, not just 7 typed corpus states.
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
- Loads sample `--json` for `lost-update` (VIOLATION) and `peterson` (PASS) without error — both `format: "typed"` and `format: "declarative"` traces render.
- Timeline + state strip render correctly; generic field tables handle arbitrary `DynamicState` fields.
- Works as `file://` open and as GitHub Pages static page (no server, no CORS).
- No Java/Gradle changes, no new dependencies, `gradle test` unaffected. Verified via Playwright MCP snapshots.
- Styled with design tokens; passes `web-design-guidelines` quick audit (semantic elements, labelled controls, keyboard reachability).

## Risks / notes
- Heterogeneous state: `DynamicState` fields are arbitrary — renderer must be generic (key/value table), not hardcoded to `counter`/`flag`.
- Large traces: budget not needed — traces are at most `maxStates` steps and failing traces are short by nature; no pagination in tier 1.
- Styling scope creep: the temptation is a framework. Resist — tokens + one signature element beat a component library for a single-page viz.

## Next step after this ships
If used and wanted, spec `12-state-graph-emission.md` for Tier 2: `--emit-graph` schema, budget, truncation, layout policy — behind a flag with attestation re-run when off (no regression) and on (graph correctness).
