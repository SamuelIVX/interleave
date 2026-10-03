# Spec 12 — Mutation Hardening — Spec Set Overview

## TL;DR

Close the 39 mutation survivors and 14 no-coverage mutants that PR #28's PIT run left open, then
ratchet the number so it cannot silently regress. Decomposed into seven specs. The set is ordered by
a hard dependency — **the encoder is upstream of both stores**, and **the ratchet must land last**
because it must pin a post-remediation measurement, not today's number.

**Spec 12.07 was added during implementation, not planning.** Writing 12.01's injectivity test turned
up a real defect: `DeadlockState.encodeTo` omits the `control` field, so two states differing only in
`control` encode identically while `equals`/`hashCode`/`deepCopy` all treat it as identity. A full
audit of all six `SharedState` implementations found one confirmed defect and one latent gap; the
other four are correct. It is **latent for `HashingStateStore`** — that store keys on a concatenated
string, so different program counters deterministically mask the collision today. `BitstateStore` keys
on a hash, so it is **not** masked: measured at a 1 024-bit store, the defect costs one reachable
`deadlock` configuration (14 of 15), while the verdict itself holds at every bitset size measured. Its
full effect there is unassessed and belongs to Spec 12.03. It becomes a separate spec rather than a
section of 12.01 because 12.01's R1 asserts `Configuration` injectivity, which *passes whether or not
this defect is fixed*, so the two own different properties and need different tests.

The uncomfortable finding that shapes this set: **PR #29's Part B moved the mutation score by
exactly one mutant** (221 → 222 of 275). Of the 17 mutants Part B targeted, 1 is now killed and 16
are still unconstrained, 6 of which never reached a test at all. That is not a reason to distrust
Part B — the verdict-level tests it added are genuinely valuable and defect injection confirms they
catch three of six injected faults. It is a reason to expect little from more of the same shape.
**The remaining CBE pruning survivors cannot be closed by writing more verdict-level tests — and,
measured, they cannot be closed by a bigger corpus either.** See Spec 12.05 §TL;DR: the 3-thread
program moved *no* mutant statuses, and injected dominance defects 4–6 already turn the suite red at
HEAD. They needed direct assertions, which is what 12.05 now does.

## Status: active — 12.07, 12.01, and 12.05 implemented; 12.02, 12.03, 12.04, 12.06 pending

Verified 2026-10-02 by running PIT scoped to each target class. "Pending" is measured, not
bookkeeping — every mutant 12.02 and 12.03 names is still open:

| spec | target class | killed | survived | no coverage |
|---|---|---|---|---|
| 12.02 | `state.HashingStateStore` | 46 / 61 | 14 | 1 |
| 12.03 | `state.BitstateStore` | 84 / 97 | 11 | 2 |

The three specs name test files that **do not yet exist** — `HashingStateStoreLifecycleTest`,
`BitstateStoreDiagnosticsTest`, and `TraceEmissionTest`. What exists in `state/` today is
`CanonicalEncoderContractTest`, `StateEncodingFidelityTest`, `StateHashingTest`,
`StateStorePreemptionTest`, and `StoreEquivalenceTest`, none of which covers the mutants 12.02 and
12.03 name. That is why every target in those two specs survives against the current suite.

### Fixed during 12.07: `StaticPorExplorer` was unsound with an invariant

**Static POR could return a false pass when given an invariant.** Surfaced by a review comment on 12.07's
`DfsExplorer` wording that turned out to apply more sharply to POR, then measured rather than argued.
**Now fixed** by applying `DporExplorer`'s existing guard; the numbers before the fix:

| corpus program | violating configurations `DfsExplorer` finds | `StaticPorExplorer` found (pre-fix) | verdict (pre-fix) |
|---|---|---|---|
| `broken-peterson-v2` | 5 | **0** | `COMPLETED` — **false pass**, expected `VIOLATION` |
| `broken-peterson` | 5 | 1 | `VIOLATION` (right verdict, 4 violations missed) |
| `double-checked-locking` | 1 | 1 | `VIOLATION` (missed the one violating configuration) |
| `lost-update`, `torn-counter` | 1 | 1 | `VIOLATION` (accidentally correct) |

**Root cause.** `PersistentSetComputer` computes the *acyclic* set — a thread is retained only when it
is dependent on another enabled thread, otherwise one arbitrary enabled thread is returned. Godefroid's
sound persistent set is `source(c)` ∪ (dependent set), where `source(c)` comes from a reverse-reachability
analysis. Without `source(c)` the construction preserves the *existence* of a deadlock but **not** state
reachability, so it is unsound for invariant checking. `DporExplorer` already guarded against precisely
this — it disables reduction whenever an invariant is given — so the principle was known and
`StaticPorExplorer` simply never applied it.

**The fix.** `porDfs` now branches over the persistent set only when `invariant == null`; with an
invariant it branches over every enabled thread, which makes the traversal identical to `DfsExplorer`'s
and violation detection **exhaustive** — every configuration reachable without first passing through a
violating one is visited and checked. **This is not complete configuration coverage.** A violating
configuration's successors are never explored, so configurations reachable only *beyond* a violation are
never visited: measured against a null-invariant run, 9 configurations on `broken-peterson` and
`broken-peterson-v2`, 9 on `double-checked-locking` (where the totals match at 17 and only the membership
differs, so a count comparison would not show it), and 1 on `torn-counter`. The shortfall is harmless for
detection, because the search truncates only after a violation has already been reported; what would be
unsound is missing a violation on an untruncated path, and disabling the reduction is what rules that out.

Post-fix, every corpus program with an
invariant now yields identical configuration counts and identical violation counts across `DfsExplorer`,
`StaticPorExplorer`, and `DporExplorer`. The reduction is retained, and still sound for what it is
actually being asked, when no invariant is supplied.

**Residual limitation, not a bug, and not fixable by a better persistent set.** The reduction is unsound for
*trace completeness*: every reported execution is real, but not every real execution appears. Two orderings
of independent actions reach the same configuration and POR keeps one. That is the reduction doing its job,
so no source set, sleep set, or DPOR method restores them. A caller needing a specific schedule in the
output must use `DfsExplorer`, invariant or not.

**What a source set would actually repair** is the different and more valuable property, *state
reachability*. Measured with no invariant, the acyclic set is not reachability-complete:

| program | DFS configurations | POR visits | not visited |
|---|---|---|---|
| `broken-peterson-v2` | 55 | 12 | **43** |
| `broken-peterson` | 55 | 17 | 38 |
| `peterson` | 42 | 18 | 24 |
| `lost-update` | 13 | 9 | 4 |
| `deadlock`, `torn-counter` | 15, 9 | 15, 9 | 0 |

So a real `source` set would make every reachable configuration visited, which is exactly what would let
the reduction be kept while an invariant is checked and remove the trade-off made above. It would still not
add interleavings: complete configuration coverage is not complete interleaving coverage.

**Why no test caught it.** `DslEquivalenceTest` runs all three explorers but compares `StaticPorExplorer`
only against its own typed/declarative re-encoding, never against `DfsExplorer`'s verdict, and it does so
on `lost-update` — one of the two corpus programs where the reduction happens to be correct.
`StaticPorExplorerTest.staticPorReducesStatesWhenInvariantPresent` *required* the reduction to survive an
invariant, which is the unsound behaviour itself. Both encoded the false belief. The suite now carries
`withAnInvariantEveryExplorerAgreesWithDfsOnTheCorpus`, the differential comparison that was missing, plus
`supplyingAnInvariantDisablesTheReduction` and the inverted
`anInvariantDisablesTheReductionSoViolationsCannotBeMissed`; all three fail if the guard is removed.

**Proper follow-up, not done here.** Disabling the reduction is sound but gives up the speedup exactly
when invariants are in play. Computing a real Godefroid `source` set would keep the reduction sound and is
the better long-term fix; it is a genuine algorithm with its own spec, not a patch.

Every number below was measured against `build/reports/pitest/mutations.xml`. This set supersedes the
`mutation-gap-register` working notes, which were never merged; their content is absorbed here, so
they are not a second source to drift against. Where an earlier draft of this material claimed
something the measurement does not support, the correction is recorded in the spec that owns it —
not left implicit (per `AGENTS.md` §10: a stale doc is worse than none).

**Landed so far:**

- **12.07** — `DeadlockState.encodeTo` now writes `control`; `SharedState`'s Javadoc states both
  encoding directions; 11 tests including a reflection completeness guard. Verified: full suite green,
  `deadlock` still `DEADLOCK` at 15 states, PIT total unmoved at 275 (`core.*` is outside PIT scope).
- **12.01** — `CanonicalEncoder.equals` **deleted** (Branch A, Sam 2026-10-01); 5 contract tests;
  R6 falsification executed and reverted. Measured: **270 total, 222 `KILLED`, 39 `SURVIVED`,
  9 `NO_COVERAGE`**; `CanonicalEncoder` 6/12 (50.0%) → **6/7 (85.7%)**.

## Measured baseline

Same config, same scope (`state.*` + `cb.*`), same 12 mutators, on both commits. All figures below
are **[verified]** against `build/reports/pitest/mutations.xml` at each named commit; the current-run
column was re-derived from the report rather than carried forward from an earlier draft.

| | `313df44` (pre-Part-B) | `c5fdcd0` (post-Part-B) | delta | **post-12.01 (current)** |
|---|---|---|---|---|
| total mutants | 275 | 275 | 0 | **270** |
| `KILLED` | 221 | 222 | +1 | **222** |
| `SURVIVED` | 40 | 39 | −1 | **39** |
| `NO_COVERAGE` | 14 | 14 | 0 | **9** |
| mutation coverage | 80.36% | 80.73% | +0.4pp | **82.22%** |
| test strength | 221/261 = 84.67% | 222/261 = 85.06% | — | **85%** |
| `TIMED_OUT` / `MEMORY_ERROR` / `NON_VIABLE` / `RUN_ERROR` / `EQUIVALENT` | 0 | 0 | 0 | 0 |

**The coverage rise is not new coverage.** `KILLED` did not move; the denominator shrank because 5
unkillable mutants were deleted with the dead method. Deleting dead code raises the score without
testing anything new, which is exactly why 12.06 must derive its floor from a post-remediation
measurement rather than accept the highest number that has ever printed.

`c5fdcd0` is the **current** baseline and the one every later number descends from; `313df44` is the
pre-Part-B run, retained because Spec 12.06 §R2's threshold argument turns on the difference between
them. Neither figure is interchangeable with the other, and each numerator below moves with its own
run's denominator — see Spec 12.06 §Three metrics, three denominators.

The run's 53 non-killed mutants are exactly `SURVIVED` (39) + `NO_COVERAGE` (14) — both of which PIT
scores as *not* detected. The five statuses that score as detected without an assertion behind them
are all zero, so the percentage is not inflated by wall-time or memory kills (see Spec 12.06).

**Per-class mutation coverage** [verified, `build/reports/pitest/mutations.xml`] — the
**post-12.01** column is current; the others are unchanged by 12.01 and still carry their `c5fdcd0`
figures:

| class | at `c5fdcd0` | coverage | **post-12.01** | coverage |
|---|---|---|---|---|
| `CanonicalEncoder` | 6/12 | 50.0% | **6/7** | **85.7%** |
| `HashingStateStore` | 46/61 | 75.4% | 46/61 | 75.4% |
| `ContextBoundedExplorer` | 86/105 | 81.9% | 86/105 | 81.9% |
| `BitstateStore` | 84/97 | 86.6% | 84/97 | 86.6% |

`CanonicalEncoder` is the only row that moved, and it moved because its denominator shrank — 12.01
deleted `equals` and its five unkillable mutants with it. The killed count did not change. Read that
row as *dead code removed*, not *coverage earned*; the class total (7) is not comparable to the old
one (12) without reading both.

## Spec Set Structure

| Spec | Title | Gap | Priority |
|---|---|---|---|
| [07-state-encoding-fidelity.md](07-state-encoding-fidelity.md) | `SharedState` Encoding Fidelity | **found during 12.01 implementation** | **HIGH / lands first** |
| [01-encoder-contract.md](01-encoder-contract.md) | `CanonicalEncoder` Contract & Dead Code | 1 | **HIGH** |
| [02-store-lifecycle.md](02-store-lifecycle.md) | `HashingStateStore` Lifecycle & Equivalent-Mutant Adjudication | 2, 5 | MED-HIGH |
| [03-bitstate-diagnostics.md](03-bitstate-diagnostics.md) | `BitstateStore` Diagnostic Correctness | 3 | MED |
| [04-trace-emission.md](04-trace-emission.md) | CBS Trace Emission & Reporting | 4 | MED |
| [05-corpus-coverage.md](05-corpus-coverage.md) | Corpus Program That Exposes Dominance Bugs | 6 | **HIGH / largest** |
| [06-mutation-ratchet.md](06-mutation-ratchet.md) | Ratchet, Threshold Accounting & Wall-Time Kills | 7 | MED |

The table is ordered by implementation sequence, not by number. **12.07 is numbered last and lands
first** — see §Dependency Graph.

Tests are co-located in each spec — no separate test spec.

## Dependency Graph

```
07-state-encoding-fidelity         (no dependency — the entry point)
    └── 01-encoder-contract
            ├── 02-store-lifecycle
            ├── 03-bitstate-diagnostics
            └── 05-corpus-coverage   (needs encoder injectivity to trust any new store test)

04-trace-emission                 (no hard dependency — can start in parallel)

06-mutation-ratchet               (depends on EVERY remediation spec: 07, 01, 02, 03, 04, 05)
```

**06 depends on all six remediation specs, not only 05.** The graph's edge from `05` is the *hard*
dependency — 12.06 reads 12.05's post-change corpus configuration counts to derive its floor, so it
cannot be written before 12.05 lands. But that is not the same as "05 is the only predecessor." The
ratchet records a single mutant floor for the **whole** target, so landing it after only some of the
remediation would pin a number that the specs still in flight are about to change — and 12.06 §R5
requires a re-run whenever that happens, which defeats the point of a ratchet. The rule is
therefore: **06 must follow every remediation spec**, including `04`, which has no dependency edge and
could otherwise be left in flight indefinitely. If `04` is genuinely being deferred past the ratchet,
that is a deliberate scope decision to record here, not a default.

**Why 07 lands before 01, and why it is still numbered 07.** Both stores' keys route through
`encodeTo`, and 12.01's test is what found the defect, so 12.07 is where implementation starts. It is
appended rather than renumbered into position 1 because `01`–`06` cross-reference each other — 12.05
and 12.06 name each other's requirements, and 12.06's §Derivation Record is the single writer for the
post-12.01 mutant total. Renumbering would break those references to buy a tidier number.

**07 and 01 own different properties, and neither test subsumes the other.** 12.07 asks whether each
state emits every field its own `equals` treats as identity (the leaves). 12.01 asks whether the
composer faithfully represents what it is given, as `Configuration` injectivity (the whole key). A
green 12.01 R1 is therefore **not** evidence the encoding is complete — program counters mask the
12.07 defect, so 12.01's test passes with or without the fix.

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
| 1 | **07-state-encoding-fidelity** | Entry point. No dependencies, and both stores' keys route through `encodeTo`. Fixes the confirmed `DeadlockState` defect and adds the reflection completeness check that prevents recurrence. Compiles standalone. |
| 2 | **01-encoder-contract** | Foundation. Every store key routes through it; no other spec's assertions can be trusted until injectivity is pinned. Depends on 07. |
| 3 | **03-bitstate-diagnostics** | User-facing output; highest visible value per line; independent of 02. |
| 4 | **02-store-lifecycle** | Depends on 01 (encoder injectivity underpins its assertions). |
| 5 | **04-trace-emission** | Depends on nothing new; safe to run parallel with 02/03. |
| 6 | **05-corpus-coverage** | Largest item. Depends on 01. Touches the corpus and possibly the JSON DSL, so it is its own PR. |
| 7 | **06-mutation-ratchet** | Depends on all of the above. One commit. |

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
  the corpus cannot expose them, and PIT confirmed the corpus program moves none of them. Spec 12.05
  was **rescoped 2026-10-02** from "build a corpus program" to "assert the four contracts directly",
  which closed 8 of 13 in the class (86/105 → 94/105 killed). The corpus program is kept for coverage.
- ✅ **`CanonicalEncoder.equals` is dead code, and was DELETED** (Branch A, approved by Sam
  2026-10-01; landed in Spec 12.01 §R4). Zero callers anywhere in the repository, including
  tests, fixtures, and docs [verified by repo-wide grep immediately before deletion]. The class
  carried **12** mutants, 6 killed and 6 not: 1 `SURVIVED` (`encode`'s `out.flush()`) and **5
  `NO_COVERAGE`, all on `equals`** [verified against `build/reports/pitest/mutations.xml`]. Deleting it
  removed **5 of those 6 not-killed mutants**, moving the global total 275 → 270 and the class to
  **6/7 (85.7%)**. An earlier wording here said "5 of the 6 `CanonicalEncoder` mutants," which reads as
  though the class holds six; it held twelve. The deletion was gated on explicit sign-off because it
  removes existing code, and Spec 12.01 §R4 presented the fork rather than assuming it — that gate is
  now discharged.
- ✅ **`DeadlockState.encodeTo` omits `control`; it is a real defect but currently latent.** `equals`,
  `hashCode`, and `deepCopy` all treat `control` as identity, so the class contradicts itself
  [verified]. Latency is argued **per store**: `HashingStateStore` keys on a concatenated string, so
  different program counters deterministically yield different keys and the collision is masked;
  `BitstateStore` keys on a hash narrowed to a bit, so different counters do **not** guarantee a
  distinct bit and no safety argument is made for it (that store is lossy by design — Spec 12.03).
  So an unchanged `deadlock` verdict is the *expected* result, argued for one store and unexamined for
  the other — not evidence the defect is harmless. Fixed by Spec 12.07.
- ✅ **Before Spec 12.07, the interface Javadoc stated only half the contract.** `SharedState.encodeTo`
  documented *equal states encode identically* and was silent on the converse — the direction the defect
  broke. An omitted field made an encoding **coarser**, which preserved the documented direction
  trivially, so the documented direction could not detect this class of defect at all. Spec 12.07 §R1
  amended the Javadoc to state both directions and resolved the gap.
- ✅ **The fix ships with a guard, not just the fix.** `control` was added to `DeadlockState` and three
  of the four identity sites were updated; `encodeTo` was the one missed, and nothing in the build
  notices a field missing from a method. Spec 12.07 §R3's reflection completeness check makes the next
  omission a build failure. The fix without the guard leaves the defect free to recur.

### Rejected alternatives

| Alternative | Why rejected |
|---|---|
| Chase all 53 non-killed mutants with tests | Many are equivalent by construction (hash mixing, a redundant `flush`). Chasing them yields tests asserting implementation details — see Spec 12.02 §R4. |
| Ratchet at the current 80.7% / integer 81 | Passes only because of one mutant; breaks if that mutant regresses, and hides regressions that cost two. Spec 12.06 §R2. |
| Ratchet on `testStrength` (85%) instead | Excludes `NO_COVERAGE` mutants, so it cannot see exactly the gap being measured. Spec 12.06 §R1. |
| Write more differential tests for the CBE survivors | Defect injection shows 3 of 6 injected dominance defects pass every such test. More of the same shape has near-zero expected yield. Spec 12.05. |
| Add a 3-thread corpus program to kill the CBE survivors | **Tried and measured; moved zero mutants.** Injected defects 4–6 were already caught at HEAD, and PIT showed no status change with the program present. Kept for coverage; the mutants needed direct assertions. Spec 12.05 §TL;DR. |
| Pin absolute state counts to catch over-pruning | Pins the implementation, not the property; any legitimate optimisation to the visited key breaks it. Spec 12.05 §Non-Goals. |
| Keep `equals` and write tests for it | An untested public method on the correctness foundation is worse than no method — but the alternative is a deletion decision, not a silent one. Spec 12.01 §R3. |
| Widen `timeoutConstInMillis` to reduce wall-time kills | Widens the window in which *every* future mutant can be killed on time instead of on an assertion. Spec 12.06 §R6. |
| Fix the `DeadlockState` defect inside 12.01 | 12.01's R1 asserts `Configuration` injectivity, which **passes whether or not the defect is fixed** — program counters mask it. A fix with no test that catches it is indistinguishable from no fix. Separate spec, separate property. Spec 12.07 §R6. |
| One "encodes differently" assertion per state class | Satisfiable by the fields a class *does* encode, so `DeadlockState` passes it today with one of three identity fields missing. Requires one field isolated per case. Spec 12.07 §R2. |
| Rely on the defect being masked as of today | Correctness-by-coincidence. The rescue is an unrelated key field, undocumented as load-bearing, and disappears the moment two configurations share counters. Spec 12.07 §Invariants. |

## Verification Checklist (per spec, filled in as each lands)

- [ ] `./gradlew clean test javadoc` — full suite green
- [ ] `./gradlew pitest` — re-run, and the spec's named mutants flipped to `KILLED`
- [ ] `./gradlew pitest` — no mutant moved to `TIMED_OUT`/`MEMORY_ERROR` (Spec 12.06 §R6)
- [ ] Falsification check named in the spec executed, and reverted
- [ ] `./gradlew pitest` — total mutant count unchanged from the **recorded post-remediation total**
      (**270 at the post-12.01 baseline**, measured; the 275 figure predates 12.01 §R4 deleting
      `equals`). A change means scope, mutator set, or runtime moved (Spec 12.06 §R5). Spec 12.07 sits
      outside the PIT scope (`state.*` + `cb.*`), so its production fix did not move the number — that
      expectation is what makes the assertion meaningful.

## Skills Required

| Skill | Purpose |
|---|---|
| **tdd** | Every spec is test-first; each names its falsification check |
| **codebase-design** | 12.01–12.03 are about deep-module contracts on the correctness foundation |
| **domain-modeling** | 12.05 introduces a new corpus concept; the vocabulary must stay precise |
| **diagnosing-bugs** | If 12.05's new program exposes a real CBS defect rather than only a mutant |
| **code-review** | Review implementation against each spec's invariants |

## Known Risks

- **Spec 12.05 was rescoped, so this risk is retired.** The original risk was that a corpus program
  designed to expose wrong dominance might expose an actual `ContextBoundedExplorer` defect. It did
  not — the premise behind the corpus approach was falsified, and the four contract tests that replaced
  it found no defect. 12.05 is now purely additive. Spec 12.05 §TL;DR.
- **Spec 12.07's encoding change will move byte-level expectations elsewhere.** Gaining one boolean at
  the head of the `DeadlockState` encoding invalidates any hardcoded byte string or bitstate bit
  position for `deadlock`. Spec 12.03's bitstate metrics are the most likely to need re-deriving, so
  grep for hardcoded encoding expectations before merging 12.07.
- **Spec 12.07's completeness check will break on a field rename.** That is intended — it forces a
  conscious decision — but it surfaces as a build failure on a semantically null refactor. Whoever hits
  it must add or re-point the case, not delete the assertion.
- **`DynamicState.encodeTo` omits `decl` and `threadCount`.** Latent for the same masking reason as
  `DeadlockState`, and confirmed unreachable today because no store spans two programs
  (`InterleaveRunner` builds a store per run; `BenchmarkHarness` one per strategy per program). It is
  escalated to the DSL spec set (`09-json-dsl-core`, `10-json-dsl-invariants`) rather than fixed here,
  so it stays visible without 12.07 claiming a fix it did not make.
- **The `runError`/`nonViable` population is zero today.** Both statuses score as detected, so a
  future run that starts producing them inflates the score silently. Spec 12.06 §R3 accounts for
  them; this is a latent trap, not a current problem.
- **The mutant inventory is pinned to a line numbering that a refactor invalidates.** Spec 12.06
  §R5 makes the re-run mandatory, but a refactor that lands without re-running will quietly stale
  every inventory in this set.
