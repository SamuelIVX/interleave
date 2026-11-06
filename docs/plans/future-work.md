# Future Work / Extensions

Roadmap and follow-up candidates. Items 1–7 and 9 have shipped; follow-ups 9-H1, 9-H2 and the
tracked 9-V1/9-V2 verification work are complete. Remaining candidates are options to evaluate,
not scheduled implementation commitments.

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

## Item 9: Completed

**#9 — Concurrent-program parser:** [Spec 16](../specs/active/16-concurrent-program-parser/README.md)
defines a bounded `.ilv` modeling language with explicit atomic blocks, branching, fixed repetition,
bounded while loops and POR/DPOR support. Shipped in [PR #57](https://github.com/SamuelIVX/interleave/pull/57)
with CLI/library support, source traces and all strategies. Spec 16 records acceptance, mutation
evidence and completed CLI review dispositions; all implementation PR CI checks passed.

## Remaining work — execution order and PR grouping

Complete the preceding follow-up backlog, including deferred tasks and bug fixes, before starting
items **#10, #11 or #12**. The 9-H, 9-V and 9-F follow-ups come first. Each of #10–12 is a separate
later workstream with its own specification and PRs; do not combine them with the preceding backlog.

PR grouping is advisory: prefer one complete behavior in a moderately sized PR. Bundle its
implementation, affected callers, API/CLI wiring, fixtures, documentation and regression tests;
do not create separate small PRs for those parts of the same behavior. Sharing a spec or touching
the same file is not enough to combine independent features. Split when a change introduces a
separate behavior contract, substantial production fix or correctness argument.

PR counts below are provisional grouping targets, not implementation commitments or fixed totals.
Sam selected sequential continuation starting with 9-F2, then 9-F3 and onward. Preserve that
order across PR merges; do not ask for repeated feature selection. Earlier conditional/deferred
recommendations below describe tradeoffs; they do not override this selected order. Count overlapping
mutation cases once and shared groundwork once. **Separate workstream** means staged, coherent
PRs for a larger subsystem, rather than one PR per small helper or test family.

If a complete feature is too large, separate compatible groundwork from the usable feature.
Every enabled capability must include correct execution evidence, replay/reporting where affected,
and safe handling or rejection of unsupported strategy combinations. Do not expose partial support
while postponing those required callers or regression checks to a later PR.

Shared bundles and proposed sequence:

- [Spec 67](../specs/active/67-parser-contract-closeout.md) implements the planned Parser closeout
  in one coherent bundle, including the fresh census. Reopen tracked 9-V2 for concrete regressions.
  If structured diagnostics under 9-F11 are selected, include their relevant Parser/source-loader
  regressions in that feature PR instead of duplicating already discharged verification work.
- [Spec 68](../specs/active/68-trace-and-sleep-context-profiling.md) combines 9-V3 and 9-V5
  profiling. Three fresh JVM forks show material trace-construction cost on the trace-heavy model;
  [Spec 69](../specs/active/69-traversal-trace-evidence.md) implements traversal-built evidence in
  one separate bundle, including every explorer, replay compatibility and before/after checks.
  Defer DPOR representation changes: coverage proxies are small and do not measure peak antichain
  width. Neither bundle changes pruning rules.
- Share cyclic-execution groundwork once across 9-F6, backward jumps in 9-F7 and liveness in 9-F8.
  Their language/analysis features retain separate acceptance contracts and complete feature PRs.
- [Spec 70](../specs/active/70-final-source-property-reduction.md) follows the measured trace work
  with a declared final source-property contract, proof argument and complete POR/DPOR integration.
  Existing final source examples motivate the extension; unknown callbacks and legacy final checks
  keep exhaustive fallback. Further feature/proof work still follows demonstrated modeling needs.
  The grouping plan does not change the deferred status or priority of any candidate below.

## Spec 16 follow-ups — hardening, larger changes and feature candidates

These entries collect the documented gaps and intentional limits of completed item #9. Their
`9-H`, `9-V` and `9-F` IDs are follow-ups, not replacements for roadmap numbers #10–12.
An unsupported capability is not automatically a defect or an implementation promise.

LOE is a rough estimate including verification: **S** = hours–2 days, **M** = several days,
**L** = weeks, **XL** = substantial research or a new subsystem. A **simple spec** is one focused
document; a **spec-set** separates interacting behavior, correctness and integration contracts.
Recommendations below record tradeoffs; sequential continuation from 9-F2 is selected.

Mutation figures belong to the revisions and scopes recorded in their linked specs. Historical
PR #57 (`a0658a2`) measurements remain historical; Specs 47–48 provide the tracked 9-V1 closeout
evidence. Scopes overlap and must not be added together. The PR #57 replay-only run followed the
expanded run, so its later result applies only to replay. Remeasure the affected scope when
reopening hardening.
See [Spec 16's verification ledger](../specs/active/16-concurrent-program-parser/README.md#feedback-patch-verification).

### Small hardening work — tests and existing behavior

These tasks strengthen the current checker rather than add modeling features. They can share one
focused test-hardening spec, executed in coherent behavior bundles without promising 100% mutation coverage.

| ID | Gap and proposed work | Benefit | Cost / risk | LOE | Spec | Recommendation | PR grouping / boundary |
|---|---|---|---|---|---|---|---|
| 9-H1 | Replay hardening implemented in [Spec 17](../specs/active/17-replay-hardening.md): 202/203 kills, one scope-limited metadata survivor, no uncovered mutations. | Protects snapshots, source enrichment, failure evidence and invariant-aware replay. | The retained survivor's equivalence argument applies to compiler-produced instructions only. | S–M | Simple hardening spec | Merged in PR #58. | Replay bundle includes invariant-aware replay tests and matching 9-V1 replay gaps; no separate replay-hardening PR is needed for those same cases. |
| 9-H2 | CBS single-emission hardening shipped in [Spec 18](../specs/active/18-cbs-cutoff-hardening.md) / PR #59 (123/123 scoped kills). [Spec 19](../specs/active/19-exact-store-byte-domain.md) / PR #60 covers exact-store division boundaries (45/52). [Spec 20](../specs/active/20-bitstate-probe-coverage.md) covers actual probe occupancy (95/97 bitstate; 281/291 default). Ten retained mutations have explicit dispositions: seven exact-store, two approximate-mapping and one encoder-flush. | Protects scheduling witnesses, exact-store byte-domain membership and actual Bloom probe use. | Exact-store equivalence requires total hashing. Approximate sign-change mappings are retained without claiming equivalence or universal verdict preservation. | S–M | Focused hardening specs | Triage and selective hardening complete; Spec 20 merged in PR #61. Continue with scoped 9-V1 work. | Each class/contract has its own PR. Bitstate occupancy checks and corrected bitstate disposition notes share this final H2 PR; no unrelated compiler/replay work is bundled. |

### Larger verification and performance work

These are broader investigations or changes to existing machinery. Profiling and proof work come
before optimization; preserving safety fallbacks is the default.

| ID | Gap and proposed work | Benefit | Cost / risk | LOE | Spec | Recommendation | PR grouping / boundary |
|---|---|---|---|---|---|---|---|
| 9-V1 | Tracked verification hardening complete in Specs 21–48 / PRs #62–89. [Spec47](../specs/active/47-verification-census.md) records the original ten-class census: 1,264/1,301 KILLED, 19 SURVIVED, 18 NO_COVERAGE; all 37 non-kills have scoped dispositions. [Spec48](../specs/active/48-selector-mutation-work-bound.md) addresses the separate selector resource failure: 111/115 KILLED, four scoped survivors, zero resource/run errors. Other helper/report populations remain separate in their specs. | Protects the recorded source execution, evidence, replay, reduction and reporting contracts. | Completion is limited to the enumerated ledger and each disposition's model/metadata/purity assumptions; raw survivors/uncovered remain visible. No whole-checker mutation or soundness closure claim. | M–L | Focused hardening specs | Complete; Spec48 merged in PR #89 at `06985a0`. Merged revision passes 1,003 tests, strict Javadoc, default full gate 281/291 and selector 111/115 (scoped R6 passes; R5/R7 skipped), plus 18 normalization checks. Reopen for a concrete contract regression; unsupported fixtures and production cleanup are not required to force 100%. | No further tracked 9-V1 implementation PR planned. Replay/state/CBS overlap is covered with 9-H1/H2. Broader compiler-harness work belongs to the separate 9-V2 effort. |
| 9-V2 | Tracked frontend/harness verification complete in Specs 49–67 / PRs #91–109. [Spec 67](../specs/active/67-parser-contract-closeout.md) closes the recorded Parser ledger in one bundle. | Protects literal syntax trees, assignment validation, diagnostic precedence and recursion limits. | Parser 320/361 K + 30 S + 11 NC: all 41 residuals have scoped dispositions. Compiler/loader/default scopes remain separate; no whole-checker claim. | M | Focused verification specs | Spec 67 merged in PR #109: 61 new cases, 1,290 tests/build/Javadoc pass; 45 new kills, all 361 identities/275 prior kills retained, zero resource/run errors. CLI four files, one trivial finding patched. Default 281/291 retains all 291 identities/statuses and passes R5/R6/R7. | Completed in one Parser closeout bundle, including the requested roadmap grouping plan. No further tracked 9-V2 PR planned; reopen for a concrete regression. Structured diagnostics remains a separately selected 9-F11 feature. Preserve completed 9-V1. |
| 9-V3 | Traversal-built source evidence merged in PR #111 at 7bc8ee0, implemented in [Spec 69](../specs/active/69-traversal-trace-evidence.md), following the shared Spec 68 profiling; [before/after report](../measurements/69-traversal-trace-evidence.md). | Removes redundant prefix execution when explorers emit source traces; public factory and replay schemas stay compatible. | Three-fork trace-heavy medians: 22.37–29.23% less time and 19.97–30.38% less allocation. Trace-light DFS costs 3.53–4.35% more time and 5.34% more allocation from eager metadata capture. Synthetic workload limits apply. | Implementation complete; verification below | Focused optimization spec | Keep the measured optimization and explicit tradeoff; reopen for a concrete trace-light regression or evidence mismatch. | One complete PR includes DFS/POR/DPOR/CBS traversal capture, failure/cutoff evidence, replay/serialization regressions, fresh mutation census and before/after measurements. No separate caller/test/CLI PR required; interfaces and source JSON remain unchanged. |
| 9-V4 | Declared final source predicates use the separate observation contract and completion-gate argument in [Spec 70](../specs/active/70-final-source-property-reduction.md); [dispatch evidence](../measurements/70-final-source-reduction.md). Unknown/configuration-dependent callbacks and legacy final properties retain exhaustive fallback. | Reduces unrelated interleavings for existing final source postconditions without weakening state-only observations. | Requires complete expression/error reads, pure predicates and finite forward source execution. No inferred metadata for arbitrary callbacks or mechanically checked proof claim. | Narrow source extension complete | Focused contract/proof and implementation spec | Merged in PR #112 at `8f502d7`. Keep unknown and legacy fallback intentional; broader proof work remains separately selected. | One coherent PR includes the new contract, source metadata, both dispatchers, proof applicability, replay/minimization/CLI/report regressions and fresh gates. No separate feature or caller/test PR remains for this narrow extension. |
| 9-V5 | DPOR context costs profiled with 9-V3 in [Spec 68](../specs/active/68-trace-and-sleep-context-profiling.md). | A representation change could reduce context bookkeeping. | Coverage lambda/access proxies are small on these models; 1,274 accepted visits/729 unique positions exercise revisits, not peak antichain width. No worst-case or retained-memory claim. | Profiling complete; L algorithm work if selected | Focused representation spec; separate coverage proof | Defer representation changes on the current workload-limited evidence; keep existing coverage/pruning semantics. | Shared profiling done once. A later selected representation PR must include storage/subsumption, verdict/witness regressions and before/after measurements. Coverage-changing pruning requires a separate proof and implementation scope. |
| 9-V6 | Mechanically checked reduction proof: formalize forward control flow, independence, terminal preservation and sleep-context subsumption. | Stronger assurance and research value. | Expensive; a formal model still must correspond to the Java implementation. | XL | Research/spec-set | Revisit for research or high-assurance goals. | Use research milestones: group formal models, related theorems and Java-correspondence checks by correctness contract. Set PR boundaries after choosing the proof approach; do not combine the entire formalization with a reduction algorithm change. |

### New modeling features and larger subsystems

These extend the supported language or analysis rather than refactor existing code. Each needs a
demonstrated use case before specification; changing a finite-model assumption needs fresh proof
and independent corpus/oracle cases.

| ID | Capability and proposed work | Benefit | Cost / risk | LOE | Spec | Recommendation | PR grouping / boundary |
|---|---|---|---|---|---|---|---|
| 9-F1 | Larger model budgets: benchmark useful models and selectively raise caps while retaining finite bounds and boundary checks. | Supports larger models. | Exploration time and memory can grow sharply. | M–L | Simple spec for a narrow cap change | Only when a useful model hits a limit. | One PR per coherent, justified budget change includes affected validators/compiler/harness bounds, acceptance/rejection tests and resource measurements. Do not raise unrelated limits merely because their guards share a file or numeric value. |
| 9-F2 | Bounded zero-argument, nonrecursive source helpers implemented in [Spec 71](../specs/active/71-bounded-source-helpers.md), lowered into existing instructions. | Less repeated model code. | Adds name, expansion, budget and source-location rules. | M–L | Simple spec for tightly bounded helpers | Merged in PR #113 at `eb6db82`. Next: 9-F3. | Target one complete bounded, nonrecursive-helper feature PR: syntax, name resolution, expansion, work accounting, source identities, execution/replay tests and examples. Keep recursion or independent syntax extensions outside that scope. |
| 9-F3 | Unguarded nested atomic syntax implemented in [Spec 72](../specs/active/72-nested-atomic-source.md): inner blocks share the outer action. | Easier authoring for some models. | Mostly convenience; guarded nesting raises additional semantics. | S–M | Simple spec | Implemented and verified in Spec 72; awaiting merge. Next: 9-F4. | Target one complete unguarded-nesting PR: parsing, one outer atomic action, private lowering, work/source-span handling, execution and rollback regressions, documentation and examples. Guarded nesting remains a separate semantic decision. |
| 9-F4 | Statement assertions: define observation timing, rollback, failure evidence, replay and minimization. | Checks conditions at specific execution points. | Adds a failure contract across execution and reporting. | M–L | Small spec-set | Only if existing always/final properties cannot express a needed check. | Target 1–2 PRs: shared failure-evidence groundwork only if substantial, then one complete assertion feature with syntax, observation timing, rollback, replay/minimization, serialization/viewer support and regressions. Do not postpone required evidence/reporting support after enabling assertions. |
| 9-F5 | Dynamic threads: extend thread identity, state encoding, scheduling and reduction contracts. | Models spawning behavior. | Changes static-thread assumptions throughout the checker. | XL | Spec-set | Defer. | Target 3–5 PRs: thread identity/state-encoding foundations; bounded spawning with execution, search and replay; supported scheduling/reduction integration. Include required reporting and safe unsupported-strategy handling with the first enabled feature. Keep loops and memory models separate. |
| 9-F6 | Unbounded loops: introduce cyclic-state exploration and appropriate completeness reasoning. | Models more programs directly. | Changes the finite forward-control-flow foundation. | XL | Spec-set | Keep bounded exploration for current needs. | Target 3–4 PRs: cyclic-control foundations; complete execution/search/replay support; completeness, reduction and scheduling integration. Share necessary cycle foundations with 9-F7/9-F8 once, but keep jumps and liveness as separate features. |
| 9-F7 | Explicit jumps: define supported directions and revise control-flow/reduction reasoning. | More flexible control flow. | Harder to reason about; backward jumps break current assumptions. | L–XL | Spec-set | Not currently worth pursuing. | One forward-jump PR includes labels, name/target validation, execution, source identities and replay tests. A backward-jump feature gets its own PR after 9-F6 supplies the required cyclic-execution foundation; do not duplicate that groundwork. |
| 9-F8 | Liveness checking: define fairness/progress properties and add appropriate cycle analysis. | Addresses starvation or eventual progress. | Much harder than safety and deadlock checking. | XL | Spec-set | Defer unless progress checking becomes a goal. | Use staged PRs for the property/fairness contract, cycle analysis with complete witnesses, and replay/reporting plus supported-strategy integration. Reuse 9-F6 cycle foundations; define the applicable fairness semantics before exposing results. Exact count follows the analysis contract. |
| 9-F9 | Cutoff-witness simplification: design a separate operation that preserves bound semantics. | Easier-to-understand incomplete results. | A shorter prefix does not establish anything about unexplored behavior. | M–L | Simple dedicated spec | Low priority; ordinary failure minimization remains separate. | Target one complete dedicated operation: cutoff-preservation rules, simplification, regenerated evidence, replay validation and API/CLI presentation with regressions. Keep ordinary failure minimization and replay hardening separate. |
| 9-F10 | Browser source authoring: add browser-compatible compilation or a service-backed flow. | Interactive model writing and checking. | Adds deployment, validation and compatibility responsibilities. | L–XL | Spec-set | Only if browser authoring is wanted. | Target 2–3 PRs after selecting the browser/service architecture: compiler/execution interface with request limits and API tests; browser editing, diagnostics and trace rendering with browser tests; additional integration only if required by that architecture. |
| 9-F11 | Diagnostics/editor tooling: add structured diagnostics and multiple-error recovery, then editor integration if needed. | Better authoring experience for larger models. | Recovery can produce misleading follow-on errors; tooling adds maintenance. | M–L | Simple diagnostic spec; spec-set for editor integration | Defer until authoring volume warrants it. | One core-diagnostics PR includes structured codes/locations, Parser/compiler propagation, loader/CLI adapters and relevant 9-V2 regressions. If selected, error recovery gets one separate complete PR and editor integration another. Target 1–3 PRs without duplicating diagnostic tests across roadmap items. |

### Existing contracts and related roadmap items

Keep these visible without duplicating existing feature entries or treating supported behavior as
an unfinished fix.

| Item | Current boundary / possible work | Benefit | Cost / risk | LOE | Spec | Recommendation | PR grouping / boundary |
|---|---|---|---|---|---|---|---|
| Invariant-aware replay | Use the existing invariant-aware overload when validating terminal verdicts. Plain replay checks execution evidence, not completion or terminal classification. | Supported validation with explicit property context. | Callers must supply the relevant invariant. | S usage/docs | Existing spec sufficient | Use the current API; no new feature needed. | Replay bundle with 9-H1 when its tests need terminal-verdict validation; existing API needs no standalone feature PR. |
| #11 — Weak memory | Define explicit memory-model semantics; keep this under roadmap #11. | Checks behaviors sequential consistency cannot represent. | Fundamental semantic and correctness changes. | XL | Spec-set | Future work driven by a real use case. | Same workstream as #11 above; no duplicate task or PR. |
| #12 — Actual Java input | Implement Java ingestion under roadmap #12; C compatibility would require separate scoping. | Reduces manual translation toward real-project analysis. | Large subsystem; faithful execution mapping is difficult. | XL | Spec-set | Strategically valuable if real-project analysis remains the goal. | Same workstream as #12 above; no duplicate task or PR. |

Language boundaries come from [16.01](../specs/active/16-concurrent-program-parser/01-language-and-semantics.md)
and [16.03](../specs/active/16-concurrent-program-parser/03-parser-validation-and-lowering.md);
search tradeoffs from [16.05](../specs/active/16-concurrent-program-parser/05-por-and-dpor-soundness.md);
replay, minimization and browser boundaries from [16.06](../specs/active/16-concurrent-program-parser/06-traces-replay-and-minimization.md).

## Later independent workstreams — items 10–12

Start these only after the preceding follow-up backlog, including its deferred tasks and bug fixes,
is complete. The order below does not make any of them a substitute for completing that backlog.
Keep symmetry reduction, relaxed memory models and actual Java input as separate workstreams.

| # | Item | LOE | Spec Needed? | Why | PR grouping / boundary |
|---|---|---|---|---|---|
| 10 | Symmetry reduction | High | Spec-set | Canonicalization is subtle; soundness must be proven | Target 2–3 PRs: canonicalization and identity-mapping proof/tests; complete exact-search integration with original-identity trace/replay support; reduced-strategy integration. Preserve safe behavior for unsupported combinations until that integration lands. |
| 11 | Relaxed memory models | Very High | Spec-set | Fundamental semantics change — needs formal spec | Stage one selected memory model: state/transition foundations; complete execution and witness support; supported search/reduction integration. Keep each additional memory model separate. Exact PR count follows the semantics/proof scope. |
| 12 | Real Java bytecode instrumentation | Very High | Spec-set | Entirely new subsystem with external deps (ASM/Javassist) | Stage supported-bytecode ingestion and rejection rules; execution/lowering with source identities and replay; CLI/library integration and differential corpus verification. Group broader bytecode coverage by supported capability, not individual opcodes. Exact PR count follows the selected Java subset. |

## Deferred

**#8 — Web UI / visualizer, Tier 2 (state-space DAG): deferred indefinitely.**
The existing trace viewer provides state snapshots and change highlighting. Full graph capture,
cross-strategy semantics, and rendering add more implementation and maintenance cost than the
currently demonstrated value warrants. No graph-export spike or reduced-scope implementation is
planned. **PR grouping:** none while deferred; do not bundle with active visualizer work. Revisit only if a concrete need emerges that the trace viewer and reduction table cannot meet.

## Item Details

3. **Bitstate / supertrace mode** — ✅ Done. Replaces exact visited sets with a bloom-filter approximation (`BitstateStore` with `k` hash functions) to trade completeness for memory. Wired into `DfsExplorer`/`DporExplorer` via `StateStore`, `BenchmarkHarness` (`--store`/`--bitstate-*`), `ReportWriter`, and `Main` CLI (PR #18).

4. **Property-based corpus mining** — ✅ Done. Generates programs from curated templates (`lost-update`, `counter-race`) via `CorpusGenerator` with seeded `Random`, bounded `GeneratorConfig`, exact `DfsExplorer` oracle (`TRUNCATED`/`VIOLATION`/`SAFE`), and persisted `CorpusEntry` (PR #19).

5. **General-purpose JSON program format** — ✅ Done (PR #21 + PR #22). Extends the JSON loader beyond the 5 hardcoded state types and 19 step types. Authors declare `format: "typed"` (legacy registry) or `format: "declarative"` (new `fields`/`locals`/`guard`/`effects` DSL with sandboxed expression language); `DynamicState`/`DynamicStep` carry deterministic `encodeTo` and POR-correct `reads()`/`writes()` derivation, composable invariants (`expr`/`all` with `when: "final"|"always"`), and two teaching examples (bounded buffer, semaphore). The DSL re-encoding of `lost-update` is the differential anchor against `BugCorpus`. Implemented and merged.

6. **Web UI / visualizer — Tier 1 (static trace renderer)** — ✅ Done. Pure static `docs/visualizer.html` (vanilla HTML/CSS/JS, no build). Normalizes all four `--json` producer shapes (`TraceRecord`, `TestResult`, `VerificationResult`, `ReportWriter`/`benchmarks`) client-side via JS `normalize()`. Renders schedule timeline, header with verdict/strategy/states/time, state-strip header with `fields`/`locals` names from optional program JSON, and graceful "no per-config snapshot" placeholder. Loads via file picker or paste; works on `file://` and GitHub Pages. Sample traces in `examples/traces/` (lost-update, peterson). No Java/Gradle changes, 143 tests unaffected.

7. **Context-bounding / CHESS-style stateless search** — ✅ Shipped as Spec 11 (7 specs, `docs/specs/active/11-context-bounded/`). Bounds the number of **preemptive** context switches (forced switches are free) instead of exploring all interleavings. `ContextBoundedExplorer` + `Strategy.CONTEXT_BOUNDED` + `TraceOutcome.INCOMPLETE`; preemption-aware `StateStore` overloads with min-count indexing; CLI `--max-preemptions` / `--iterative-deepening`; `BenchmarkHarness` + `SoundnessAttestation` + visualizer integration. Verified on the 7-program corpus: every buggy program is caught at K=2 and the correct one (`peterson`) reports `INCOMPLETE`.

   *Visualizer CI follow-up:* the independent **Visualizer compatibility** job runs verdict
   normalization checks and Playwright browser tests. Spec 16 adds a freshly generated Java source
   report to verify source metadata across the Java-to-browser boundary.

   *Remaining observation:* CBS explores *more* configurations than DFS on this corpus, so the
   reduction table shows bare counts rather than percentages for those rows.

8. **Web UI / visualizer — Tier 2 (state-space DAG)** — Deferred indefinitely; see the decision above. The earlier graph-emission proposal is not scheduled work.

9. **Concurrent-program parser** — Shipped in [PR #57](https://github.com/SamuelIVX/interleave/pull/57), with all implementation PR checks passing. [Spec 16](../specs/active/16-concurrent-program-parser/README.md) records measured acceptance and review evidence. Readable source models compile into finite forward control flow with atomic interiors, explicit loop cutoffs and shared execution/reduction machinery. Actual Java project ingestion remains item #12; follow-up candidates are grouped above.

10. **Symmetry reduction** — exploit thread-identity symmetry to collapse equivalent states that differ only by which thread has which ID. Subtle correctness concerns; needs careful canonicalization and proof of soundness.

11. **Relaxed memory models** — support weak consistency models (e.g., ARM/POWER) instead of assuming sequential consistency. Fundamental change to `Configuration` snapshot semantics and step execution.

12. **Real Java bytecode instrumentation** — instead of hand-written `Step` objects, instrument real Java bytecode so the checker can analyze actual concurrent programs. Requires bytecode analysis (ASM/Javassist), thread detection, and mapping bytecode to atomic steps.
