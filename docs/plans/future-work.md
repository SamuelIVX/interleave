# Future Work / Extensions

Roadmap and follow-up candidates. Items 1–7 and 9 have shipped; the remaining entries are
options to evaluate, not scheduled implementation commitments.

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

## Remaining Candidates — Ranked by LOE

PR grouping is advisory. Bundle only work that shares one behavior contract and its
verification; sharing a spec or touching the same file is not enough. Each bundle below
means a small scoped PR, not every item in the referenced mutation scope. Count overlapping
mutation cases once. **Separate workstream** means separate from other roadmap items and
usually multiple staged PRs, not one large PR. Include required callers, fixtures, docs and
regression coverage with each behavior change; dependencies alone do not justify bundling.

| # | Item | LOE | Spec Needed? | Why | PR grouping / boundary |
|---|---|---|---|---|---|
| 10 | Symmetry reduction | High | Spec-set | Canonicalization is subtle; soundness must be proven | Separate workstream; staged PRs for canonicalization, soundness and integration. |
| 11 | Relaxed memory models | Very High | Spec-set | Fundamental semantics change — needs formal spec | Separate workstream; staged PRs under the memory-model spec-set. |
| 12 | Real Java bytecode instrumentation | Very High | Spec-set | Entirely new subsystem with external deps (ASM/Javassist) | Separate workstream; staged PRs for ingestion, execution mapping and integration. |

## Spec 16 follow-ups — hardening, larger changes and feature candidates

These entries collect the documented gaps and intentional limits of completed item #9. Their
`9-H`, `9-V` and `9-F` IDs are follow-ups, not replacements for roadmap numbers #10–12.
An unsupported capability is not automatically a defect or an implementation promise.

LOE is a rough estimate including verification: **S** = hours–2 days, **M** = several days,
**L** = weeks, **XL** = substantial research or a new subsystem. A **simple spec** is one focused
document; a **spec-set** separates interacting behavior, correctness and integration contracts.
Recommendations below are advisory; selecting an item precedes writing its specification.

Unless explicitly attributed to Specs 17–20, mutation figures are recorded measurements from PR #57,
merged as `a0658a2`, rather than a live census. The scopes overlap and must not be added together.
The PR #57 replay-only run followed the expanded run, so its later result applies only to replay.
Remeasure current main before hardening.
See [Spec 16's verification ledger](../specs/active/16-concurrent-program-parser/README.md#feedback-patch-verification).

### Small hardening work — tests and existing behavior

These tasks strengthen the current checker rather than add modeling features. They can share one
focused test-hardening spec, executed in small batches without promising 100% mutation coverage.

| ID | Gap and proposed work | Benefit | Cost / risk | LOE | Spec | Recommendation | PR grouping / boundary |
|---|---|---|---|---|---|---|---|
| 9-H1 | Replay hardening implemented in [Spec 17](../specs/active/17-replay-hardening.md): 202/203 kills, one scope-limited metadata survivor, no uncovered mutations. | Protects snapshots, source enrichment, failure evidence and invariant-aware replay. | The retained survivor's equivalence argument applies to compiler-produced instructions only. | S–M | Simple hardening spec | Merged in PR #58. | Replay bundle includes invariant-aware replay tests and matching 9-V1 replay gaps; no separate replay-hardening PR is needed for those same cases. |
| 9-H2 | CBS single-emission hardening shipped in [Spec 18](../specs/active/18-cbs-cutoff-hardening.md) / PR #59 (123/123 scoped kills). [Spec 19](../specs/active/19-exact-store-byte-domain.md) / PR #60 covers exact-store division boundaries (45/52). [Spec 20](../specs/active/20-bitstate-probe-coverage.md) covers actual probe occupancy (95/97 bitstate; 281/291 default). Ten retained mutations have explicit dispositions: seven exact-store, two approximate-mapping and one encoder-flush. | Protects scheduling witnesses, exact-store byte-domain membership and actual Bloom probe use. | Exact-store equivalence requires total hashing. Approximate sign-change mappings are retained without claiming equivalence or universal verdict preservation. | S–M | Focused hardening specs | Triage and selective hardening complete; Spec 20 merged in PR #61. Continue with scoped 9-V1 work. | Each class/contract has its own PR. Bitstate occupancy checks and corrected bitstate disposition notes share this final H2 PR; no unrelated compiler/replay work is bundled. |

### Larger verification and performance work

These are broader investigations or changes to existing machinery. Profiling and proof work come
before optimization; preserving safety fallbacks is the default.

| ID | Gap and proposed work | Benefit | Cost / risk | LOE | Spec | Recommendation | PR grouping / boundary |
|---|---|---|---|---|---|---|---|
| 9-V1 | Historical expanded scope: 130 survivors and 156 uncovered mutations; the aggregate has not been remeasured. Replay is covered by Spec 17; evaluator work by Specs 21–23 ([outer-Evaluator 282/282](../specs/active/23-evaluator-defensive-contracts.md)). [Spec 24](../specs/active/24-source-terminal-transitions.md) covers blocked/cutoff source transition edges; [Spec 25](../specs/active/25-source-label-metadata.md) closes source-label boundaries (114/114 outer-SourceInstruction). [Spec 26](../specs/active/26-benchmark-exploration-evidence.md) covers benchmark-row qualification/evidence (52/54 with two scoped dispositions); [Spec 27](../specs/active/27-soundness-verdict-policy.md) covers attestation verdict policy; [Spec 28](../specs/active/28-soundness-replay-diagnostics.md) covers positive replay obligations and diagnostics; [Spec 29](../specs/active/29-soundness-markdown-contract.md) covers qualified Markdown claims and complete-section oracles; [Spec 30](../specs/active/30-dfs-terminal-contracts.md) follows with DFS terminal callbacks, budget boundaries and run isolation. | Strengthens source-language verification. | Broad scope includes legacy paths; complete closure is not yet estimable. | M–L | Simple hardening spec, scoped batches | Specs 21–29 merged in PRs #62–70. Spec 30 DFS terminal/budget/reuse tests verified (82/83 kills, one redundant-guard disposition); three-file review clean. POR/DPOR reduction and other explorer contracts remain separate. | Replay gaps with 9-H1; state/CBS gaps with 9-H2; compiler safeguards with 9-V2. Scalar, array, defensive-AST and terminal-transition contracts have separate focused PRs; label formatting and higher-level verdict/reduction/reporting checks stay separate. |
| 9-V2 | Compiler mutation harness: isolate safeguard-removal mutants, retain huge-count boundary tests and add bounded behavioral oracles. | More repeatable compiler verification. | The earlier 13 memory errors and one timeout occurred in deliberately mutated code. Partitioning must not hide missing safeguards or credit resource failures as kills. | M | Simple verification spec | Useful if broad compiler mutation testing will be maintained. | Compiler-safeguard bundle: one PR with only the 9-V1 cases needed to validate harness isolation and bounded oracles. |
| 9-V3 | Trace construction: profile prefix replay; capture metadata during traversal only if the cost is material. | May avoid repeated execution work. | Adds metadata plumbing and memory use; no measured bottleneck is documented. | S profiling; M–L implementation | Simple optimization spec after profiling | Profile first; defer implementation. | Separate profiling PR; any justified optimization follows in its own PR with regression checks. |
| 9-V4 | Unknown/final-property reduction: prove a broader observation/reduction contract before replacing exhaustive fallback. | Could explore fewer schedules. | Unsound pruning can hide failures. | L–XL | Spec-set | Defer; the current fallback protects correctness. | Separate workstream; establish the observation contract/proof before implementation PRs. |
| 9-V5 | DPOR context costs: benchmark sleep-set antichains; optimize representation before considering new pruning rules. | Potential memory/time savings. | Revisited configurations can represent necessary work; dropping contexts can lose outcomes. | S profiling; L algorithm work | Simple spec for representation changes; spec-set for coverage changes | Keep current semantics unless measurements justify work. | Separate benchmark PR; representation optimization and any coverage-changing algorithm work get separate follow-ups. |
| 9-V6 | Mechanically checked reduction proof: formalize forward control flow, independence, terminal preservation and sleep-context subsumption. | Stronger assurance and research value. | Expensive; a formal model still must correspond to the Java implementation. | XL | Research/spec-set | Revisit for research or high-assurance goals. | Separate research/proof PRs; do not bundle the full proof effort with 9-V4 implementation. |

### New modeling features and larger subsystems

These extend the supported language or analysis rather than refactor existing code. Each needs a
demonstrated use case before specification; changing a finite-model assumption needs fresh proof
and independent corpus/oracle cases.

| ID | Capability and proposed work | Benefit | Cost / risk | LOE | Spec | Recommendation | PR grouping / boundary |
|---|---|---|---|---|---|---|---|
| 9-F1 | Larger model budgets: benchmark useful models and selectively raise caps while retaining finite bounds and boundary checks. | Supports larger models. | Exploration time and memory can grow sharply. | M–L | Simple spec for a narrow cap change | Only when a useful model hits a limit. | Separate PR per justified cap change, including its resource measurements and boundary tests. |
| 9-F2 | Reusable source helpers: start with bounded, nonrecursive helpers lowered into existing instructions. | Less repeated model code. | Adds name, expansion, budget and source-location rules. | M–L | Simple spec for tightly bounded helpers | Consider when duplication becomes painful. | Separate feature PR for tightly bounded helpers after specification; do not bundle independent syntax features. |
| 9-F3 | Nested atomic syntax: define unguarded nested blocks as part of the outer action. | Easier authoring for some models. | Mostly convenience; guarded nesting raises additional semantics. | S–M | Simple spec | Low priority. | Separate feature PR for unguarded nesting, with lowering, source-location and execution tests. |
| 9-F4 | Statement assertions: define observation timing, rollback, failure evidence, replay and minimization. | Checks conditions at specific execution points. | Adds a failure contract across execution and reporting. | M–L | Small spec-set | Only if existing always/final properties cannot express a needed check. | Separate workstream; stage PRs under one assertion contract, including replay/reporting tests with the behavior they validate. |
| 9-F5 | Dynamic threads: extend thread identity, state encoding, scheduling and reduction contracts. | Models spawning behavior. | Changes static-thread assumptions throughout the checker. | XL | Spec-set | Defer. | Separate workstream with staged PRs; do not bundle with loops, jumps or memory models. |
| 9-F6 | Unbounded loops: introduce cyclic-state exploration and appropriate completeness reasoning. | Models more programs directly. | Changes the finite forward-control-flow foundation. | XL | Spec-set | Keep bounded exploration for current needs. | Separate workstream for cyclic execution and completeness; do not bundle liveness checking. |
| 9-F7 | Explicit jumps: define supported directions and revise control-flow/reduction reasoning. | More flexible control flow. | Harder to reason about; backward jumps break current assumptions. | L–XL | Spec-set | Not currently worth pursuing. | Separate workstream; backward jumps depend on cyclic-control-flow support but remain a separate feature. |
| 9-F8 | Liveness checking: define fairness/progress properties and add appropriate cycle analysis. | Addresses starvation or eventual progress. | Much harder than safety and deadlock checking. | XL | Spec-set | Defer unless progress checking becomes a goal. | Separate workstream after required cycle-analysis foundations; fairness and liveness need their own acceptance scope. |
| 9-F9 | Cutoff-witness simplification: design a separate operation that preserves bound semantics. | Easier-to-understand incomplete results. | A shorter prefix does not establish anything about unexplored behavior. | M–L | Simple dedicated spec | Low priority; ordinary failure minimization remains separate. | Separate dedicated PR after the cutoff-preservation contract is specified; do not bundle with replay hardening. |
| 9-F10 | Browser source authoring: add browser-compatible compilation or a service-backed flow. | Interactive model writing and checking. | Adds deployment, validation and compatibility responsibilities. | L–XL | Spec-set | Only if browser authoring is wanted. | Separate workstream; stage compiler/service integration and browser authoring under a shared interface contract. |
| 9-F11 | Diagnostics/editor tooling: add structured diagnostics and multiple-error recovery, then editor integration if needed. | Better authoring experience for larger models. | Recovery can produce misleading follow-on errors; tooling adds maintenance. | M–L | Simple diagnostic spec; spec-set for editor integration | Defer until authoring volume warrants it. | Separate PRs for structured diagnostics, error recovery and editor integration; each includes its own callers/tests. |

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
