# Viz Security Hardening + Bolder Color (Plan)

Saved 2026-09-27 — implements CodeRabbit PR #24 review (5 findings) + additional OWASP hardening, plus color direction B.

Skills: `owasp-security` (OWASP Top 10:2025, ASVS 5.0), `webapp-testing` + Playwright MCP, `frontend-design` + `web-design-guidelines`, `performance`. Hostile fixtures stay in-memory via Playwright paste (no exploit strings committed).

## Threat model
Static page at `docs/visualizer.html` (served `file://` or GitHub Pages). Input = attacker-influenced JSON via file picker/paste or `fetch` of a crafted trace artifact (shared via Pages). No auth, no server, no secrets, no DB. Blast radius = the viewer tab (self-XSS per CodeRabbit). Trust boundary: raw parsed JSON → DOM.

## CodeRabbit findings — verdicts (verified against docs/visualizer.html)
| # | Line | Type | Verdict |
|---|---|---|---|
| 1 | 218 | XSS via innerHTML (CWE-79) — `strategy`, `outcomes`, step labels/detail, invariant, field names/types, snapshot JSON | **VALID** — sinks in `renderHeader`/`renderTimeline`/`renderStateStrip` use `innerHTML` + `title="${detail}"` attribute context. Fix: `esc`/`attrEsc` or DOM `textContent`. |
| 2 | 238–239 | DoS via `Math.max(...threads)` spread + `Array.from({length: maxTid+1})` | **VALID, worst** — `threads:[1e9]` → billion columns; spread past ~65k args throws. Also uncovered: schedule length & paste size unbounded. Fix: distinct-ID Set + iterative max + caps. |
| 3 | 249 | Fail-row hidden when selected (`active-row` wins) | **VALID** functional. Fix: combined classes, CSS so `fail-row` red wins. |
| 4 | 274 | Keyboard focus lost after re-render | **VALID** a11y. Fix: refocus `tr[data-idx]` post-render. |
| 5 | 380–384 | Demo load race / stale result after Clear | **VALID** TOCTOU. Fix: monotonic `loadGen` token checked after each await. |

## Additional hardening (my OWASP pass)
- Paste/file ≤ 5 MB before `JSON.parse` (LLM10 unbounded consumption)
- Schedule length ≤ 5000 steps, distinct threads ≤ 64 — fail-closed with inline error (A10)
- `asNum(v)` coercion for `statesExplored`/`wallTimeMs`/`heapDeltaBytes` (non-numbers rendered as text, not HTML)
- Outcome/title length cap for display (truncate long strings)
- Demo `key` allowlist `/^[a-z0-9-]{1,64}$/` before `fetch` (future-proof path traversal)
- CSP meta: `default-src 'none'; img-src 'self'; connect-src 'self'; style-src 'unsafe-inline'; object-src 'none'; base-uri 'none'; form-action 'none'` — note: `script-src` inline needed for `file://`, so CSP is defense-in-depth; real XSS fix is escaping (A02)
- Prototype pollution: inert (direct property reads only) — documented constraint.
- Never render JSON values as `href`/`src` (constraint to keep).

OWASP mapping: #1 → A05 Injection / ASVS 1.2.1 output encoding; #2 + size caps → A10 Mishandling of Exceptional Conditions / LLM10; CSP → A02; key allowlist + fail-closed → A10.

## Implementation steps (single PR push to feat/trace-visualizer-tier1)

1. Sanitizer layer at script top: `esc()`, `attrEsc()`, `asNum()`.
2. `validateTrace`: keep integer ≥0, plus `threads.length ≤5000`, distinct ≤64, `outcomes` entries capped, paste size check before parse.
3. Rewrite sinks to DOM API:
   - `renderHeader`: `#meta` via `createElement` + `textContent`/`asNum`
   - `renderTimeline`: distinct-ID Set sorted, iterative max, cells/outcome via text nodes, `title` via `setAttribute`, combined `fail-row active-row` classes
   - `renderStateStrip`: table via DOM API (invariant, field/local names, snapshot JSON text-only)
4. `loadGen` token across demo/file/paste/clear.
5. Key allowlist before fetch; meta CSP tag.
6. Verification: `gradle test` green; Playwright MCP in-memory hostile suite (XSS strings inert, 1e9 thread OOM → error, 100k row → error, 5 MB reject, fail-red + focus, demo race).

## Color direction B — bolder (user choice)

Keep amber signature but enrich with *verdict + thread* color:
- **Tokens:** add thread palette `t0:#0ea5e9, t1:#8b5cf6, t2:#10b981, t3:#f59e0b` (muted, WCAG AA on light); header band tinted by verdict (VIOLATION faint red top-border + badge already red, PASS faint teal, DEADLOCK amber).
- **Timeline:** thread columns get subtle header tint per thread + `cell-active` pill tinted by thread color (still amber wash for row, but pill carries thread identity). Failing row red overrides.
- **Guardrails:** ≤6 colors total, 2 fonts, 8px scale, contrast AA, `prefers-reduced-motion` retained, no gradients/blur. Performance: no paint-thrashing, single repaint.

If bolder feels too loud after screenshot, revert to restrained (verdict band only) in follow-up.
