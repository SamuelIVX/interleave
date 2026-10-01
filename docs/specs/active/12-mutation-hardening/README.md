# Spec 12 — Mutation Hardening — Spec Set Overview

## TL;DR

Close the 39 mutation survivors and 14 no-coverage mutants that PR #28's PIT run left open, then
ratchet the number so it cannot silently regress. Decomposed into six specs. The set is ordered by
a hard dependency — **the encoder is upstream of both stores**, and **the ratchet must land last**
because it must pin a post-remediation measurement, not today's number.

The uncomfortable finding that shapes this set: **PR #29's Part B moved the mutation score by
exactly one mutant** (221 → 222 of 275). Of the 17 mutants Part B targeted, 1 is now killed and 16
are still unconstrained, 6 of which never reached a test at all. That is not a reason to distrust
Part B — the verdict-level tests it added are genuinely valuable and defect injection confirms they
catch three of six injected faults. It is a reason to expect little from more of the same shape.
**The remaining CBE pruning survivors cannot be closed by writing more verdict-level tests.** They
need a corpus program with enough threads to make wrong dominance observable (Spec 12.05).

## Status: active — nothing here has been implemented

Every number below was measured against `build/reports/pitest/mutations.xml`. This set supersedes the
`mutation-gap-register` working notes, which were never merged; their content is absorbed here, so
they are not a second source to drift against. Where an earlier draft of this material claimed
something the measurement does not support, the correction is recorded in the spec that owns it —
not left implicit (per `AGENTS.md` §10: a stale doc is worse than none).

## Measured baseline

Same config, same scope (`state.*` + `cb.*`), same 12 mutators, on both commits. All figures below
are **[verified]** against `build/reports/pitest/mutations.xml` at each named commit; the current-run
column was re-derived from the report rather than carried forward from an earlier draft.

| | `313df44` (pre-Part-B) | `c5fdcd0` (post-Part-B, **current**) | delta |
|---|---|---|---|
| total mutants | 275 | 275 | 0 |
| `KILLED` | 221 | 222 | +1 |
| `SURVIVED` | 40 | 39 | −1 |
| `NO_COVERAGE` | 14 | 14 | 0 |
| mutation coverage | 80.36% | 80.73% | +0.4pp |
| test strength | 221/261 = 84.67% | 222/261 = 85.06% | — |
| `TIMED_OUT` / `MEMORY_ERROR` / `NON_VIABLE` / `RUN_ERROR` / `EQUIVALENT` | 0 | 0 | 0 |

`c5fdcd0` is the **current** baseline and the one every later number descends from; `313df44` is the
pre-Part-B run, retained because Spec 12.06 §R2's threshold argument turns on the difference between
them. Neither figure is interchangeable with the other, and each numerator below moves with its own
run's denominator — see Spec 12.06 §Three metrics, three denominators.

The run's 53 non-killed mutants are exactly `SURVIVED` (39) + `NO_COVERAGE` (14) — both of which PIT
scores as *not* detected. The five statuses that score as detected without an assertion behind them
are all zero, so the percentage is not inflated by wall-time or memory kills (see Spec 12.06).

**Per-class mutation coverage** [verified, `build/reports/pitest/mutations.xml`]:

| class | killed/total | coverage | non-killed |
|---|---|---|---|
| `CanonicalEncoder` | 6/12 | **50.0%** | 6 |
| `HashingStateStore` | 46/61 | 75.4% | 15 |
| `ContextBoundedExplorer` | 86/105 | 81.9% | 19 |
| `BitstateStore` | 84/97 | 86.6% | 13 |

## Spec Set Structure

| Spec | Title | Gap | Priority |
|---|---|---|---|
| [01-encoder-contract.md](01-encoder-contract.md) | `CanonicalEncoder` Contract & Dead Code | 1 | **HIGH** |
| [02-store-lifecycle.md](02-store-lifecycle.md) | `HashingStateStore` Lifecycle & Equivalent-Mutant Adjudication | 2, 5 | MED-HIGH |
| [03-bitstate-diagnostics.md](03-bitstate-diagnostics.md) | `BitstateStore` Diagnostic Correctness | 3 | MED |
| [04-trace-emission.md](04-trace-emission.md) | CBS Trace Emission & Reporting | 4 | MED |
| [05-corpus-coverage.md](05-corpus-coverage.md) | Corpus Program That Exposes Dominance Bugs | 6 | **HIGH / largest** |
| [06-mutation-ratchet.md](06-mutation-ratchet.md) | Ratchet, Threshold Accounting & Wall-Time Kills | 7 | MED |

Tests are co-located in each spec — no separate test spec.

## Dependency Graph

```
01-encoder-contract
   ├── 02-store-lifecycle
   ├── 03-bitstate-diagnostics
   └── 05-corpus-coverage        (needs encoder injectivity to trust any new store test)

04-trace-emission                (no hard dependency — can start in parallel)

05-corpus-coverage
   └── 06-mutation-ratchet       (ratchet pins post-remediation numbers — MUST land last)
```

**04 has no hard dependency.** Its requirements are test-side and its own `## Constraints` says so.
The `L82`/`L87`/`L88` mutants it is adjacent to belong to 12.05's pruning surface, but that is an
ownership boundary, not a build dependency — it must not be read as one.

**Why 06 is last and is not parallelizable with anything.** The ratchet records a floor. If it
lands before remediation, it pins today's 53-mutant gap as the accepted state forever, and Specs
01–05 then have to raise it by hand one item at a time — with no way to tell a deliberate raise
from a regression. Landing it last means the number it records is the number the remediated suite
actually earns.

## Implementation Order (compile-green)

| # | Spec | Rationale |
|---|---|---|
| 1 | **01-encoder-contract** | Foundation. Every store key routes through it; no other spec's assertions can be trusted until injectivity is pinned. Compiles standalone. |
| 2 | **03-bitstate-diagnostics** | User-facing output; highest visible value per line; independent of 02. |
| 3 | **02-store-lifecycle** | Depends on 01 (encoder injectivity underpins its assertions). |
| 4 | **04-trace-emission** | Depends on nothing new; safe to run parallel with 02/03. |
| 5 | **05-corpus-coverage** | Largest item. Depends on 01. Touches the corpus and possibly the JSON DSL, so it is its own PR. |
| 6 | **06-mutation-ratchet** | Depends on all of the above. One commit. |

## Conventions This Set Uses

**Line numbers appear as data, not as references.** `AGENTS.md` §4 requires referencing code by
symbol, because line numbers rot. This set is the exception, deliberately: PIT's unit of reporting
*is* the line number, and a mutation inventory that cannot name its units is not auditable. So
every spec names the symbol first and the line second (`CanonicalEncoder.encode` at `L16`), and a
reviewer who checks the line number against a drifted file should treat the symbol as authoritative
and flag the spec for refresh. Any refactor that moves these lines invalidates the inventory and
requires a re-run of `./gradlew pitest` plus an inventory refresh — Spec 12.06 §R5 owns that.

**Every claim carries a confidence tag.** `[verified]` means confirmed against source, bytecode, or a
measurement in this repository. `[assumed]` means a reasonable default with a stated falsifier.
`[unverified]` means unknown — it blocks *any conclusion built on it*. No requirement may depend on
an `[unverified]` value; where a number does not exist yet (12.05's configuration counts), the
requirement is written as the **measurement procedure** rather than a predicted value, so
implementing it is what resolves the tag.

**Mutation coverage is not the goal.** Per `ENGINEERING-PRINCIPLES.md` §4, coverage is a floor
signal. Several mutants in this set are genuinely equivalent and must be *suppressed with a
recorded reason*, not chased with contrived tests. Spec 12.02 §R4 defines the adjudication
procedure precisely because getting this backwards produces tests that assert implementation
details — the exact failure mode that produced PR #26's under-specified metric tests.

## Key Decisions

### Already resolved

- ✅ **`mutationThreshold = 80`, not 81 — but the final value is not yet fixed.** PIT rounds mutation
  coverage to the nearest integer (verified: `222/275` renders as `81%` in
  `build/reports/pitest/index.html`), so 81 passes *only* on the strength of the single mutant PR #29
  added and fails at the pre-Part-B baseline of `221/275`. What is resolved is that **81 is wrong and
  80 is the highest integer floor either measured run clears.** The floor actually shipped comes from
  the post-remediation measurement (Spec 12.06 §R2), which is why 12.06 lands last.
- ✅ **No `fasterThreshold`.** That option does not exist in PIT 1.30.0 or plugin 1.19.0 — zero
  occurrences across all four runtime jars. An earlier draft of the material behind this set described
  it as a real hazard; it was not, and the corrected write-up is in Spec 12.06.
- ✅ **Equivalent mutants get a recorded suppression reason, not a contrived test.** Spec 12.02 §R4.
- ✅ **CBS pruning survivors are not chased with more verdict-level tests.** Defect injection proved
  the corpus cannot expose them. Spec 12.05 builds the corpus program that can.
- ✅ **`CanonicalEncoder.equals` is dead code.** Zero callers anywhere in the repository, including
  tests, fixtures, and docs [verified by repo-wide grep]. Deleting it removes 5 of the 6
  `CanonicalEncoder` mutants. But it is a deletion of existing code, so it needs Sam's explicit
  sign-off — Spec 12.01 §R3 presents the fork rather than assuming it.

### Rejected alternatives

| Alternative | Why rejected |
|---|---|
| Chase all 53 non-killed mutants with tests | Many are equivalent by construction (hash mixing, a redundant `flush`). Chasing them yields tests asserting implementation details — see Spec 12.02 §R4. |
| Ratchet at the current 80.7% / integer 81 | Passes only because of one mutant; breaks if that mutant regresses, and hides regressions that cost two. Spec 12.06 §R2. |
| Ratchet on `testStrength` (85%) instead | Excludes `NO_COVERAGE` mutants, so it cannot see exactly the gap being measured. Spec 12.06 §R1. |
| Write more differential tests for the CBE survivors | Defect injection shows 3 of 6 injected dominance defects pass every such test. More of the same shape has near-zero expected yield. Spec 12.05. |
| Pin absolute state counts to catch over-pruning | Pins the implementation, not the property; any legitimate optimisation to the visited key breaks it. Spec 12.05 §Non-Goals. |
| Keep `equals` and write tests for it | An untested public method on the correctness foundation is worse than no method — but the alternative is a deletion decision, not a silent one. Spec 12.01 §R3. |
| Widen `timeoutConstInMillis` to reduce wall-time kills | Widens the window in which *every* future mutant can be killed on time instead of on an assertion. Spec 12.06 §R6. |

## Verification Checklist (per spec, filled in as each lands)

- [ ] `./gradlew clean test javadoc` — full suite green
- [ ] `./gradlew pitest` — re-run, and the spec's named mutants flipped to `KILLED`
- [ ] `./gradlew pitest` — no mutant moved to `TIMED_OUT`/`MEMORY_ERROR` (Spec 12.06 §R6)
- [ ] Falsification check named in the spec executed, and reverted
- [ ] `./gradlew pitest` — total mutant count unchanged from the **recorded post-12.01 total**
      (275 today; 270 if 12.01 §R3 takes Branch A and deletes `CanonicalEncoder.equals`, removing its
      five mutants outright). A change means scope, mutator set, or runtime moved (Spec 12.06 §R5)

## Skills Required

| Skill | Purpose |
|---|---|
| **tdd** | Every spec is test-first; each names its falsification check |
| **codebase-design** | 12.01–12.03 are about deep-module contracts on the correctness foundation |
| **domain-modeling** | 12.05 introduces a new corpus concept; the vocabulary must stay precise |
| **diagnosing-bugs** | If 12.05's new program exposes a real CBS defect rather than only a mutant |
| **code-review** | Review implementation against each spec's invariants |

## Known Risks

- **Spec 12.05 may find a real bug.** A corpus program designed to expose wrong dominance could
  expose an actual dominance defect in `ContextBoundedExplorer`. That is a success, not a scope
  violation — but it means 12.05 is not purely additive and its PR should be prepared to carry a
  production fix. Budget for that.
- **The `runError`/`nonViable` population is zero today.** Both statuses score as detected, so a
  future run that starts producing them inflates the score silently. Spec 12.06 §R3 accounts for
  them; this is a latent trap, not a current problem.
- **The mutant inventory is pinned to a line numbering that a refactor invalidates.** Spec 12.06
  §R5 makes the re-run mandatory, but a refactor that lands without re-running will quietly stale
  every inventory in this set.
