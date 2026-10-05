# Spec 13 — Deferred Debt — Spec Set Overview

## TL;DR

Clear every open entry in [Spec Set 12's deferred register](../12-mutation-hardening/DEFERRED.md).
Set 12 is complete — 12.07, 12.01 through 12.06 all landed, and the ratchet is merged and green under
CI's parallel PIT — but it closed the mutants it set out to close and left nine problems recorded
rather than fixed.

Decomposed into eight specs, ordered by dependency and blast radius. **13.08 lands last** because it
is the only item that is an algorithm rather than debt-clearing.

**Most of these items exist because of one scoping rule: every 12.x spec scoped itself out of `core`.**
The register's debt list and `core`'s debt list are the same list, which is why these were never
picked up by the set that found them.

**E4's cause is not unknown.** The register recorded it as `NOT ESTABLISHED`; it is provable in three
lines. `ContextBoundedExplorer:126` returns when `allTerminated()` is true, so if control reaches
L187 then `allTerminated()` was already false, which makes `!config.allTerminated()` unconditionally
true — the guard is exactly `enabled.isEmpty()` and the call is redundant. That also explains why the
*same* removal at L126 **is** killed. Full argument under
[Corrections](#corrections-to-the-register) item 3.

## Status: active — planned, not implemented

No spec in this set has been implemented. Each lands as its own PR, in the order below.

## Corrections to the register

Every ref in [DEFERRED.md](../12-mutation-hardening/DEFERRED.md) is pinned to `ba3fe01`. `main` is at
`575a7b3`. Its own E2 rule — *a spec's numbers are valid only against the tree it names* — applies, so
every claim below was re-verified against source at `575a7b3`. Six were wrong or incomplete:

1. **The `toString()` key has FIVE sites, not three, and one is a different shape.**

   | Site | Key shape | In PIT scope |
   |---|---|---|
   | `DfsExplorer:135` | `state.toString() + "\|" + counters` | no |
   | `StaticPorExplorer:141` | same | no |
   | `DporExplorer:109` | same | no |
   | `DporExplorer:272` | same | no |
   | **`ContextBoundedExplorer:113`** | `state + "\|" + counters + "\|" + lastThreadId` | **yes (`cb.*`)** |
   | `HashingStateStore:109` | private base64 `encode` | no |

   Site 5 appends `lastThreadId`, so the CB explorer **deliberately distinguishes** configurations the
   other four treat as identical. A single blanket key would merge distinct visit records and change
   `statesExplored`.

2. **B2's real form is sharper than "three derivations exist": the derivations have already drifted.**
   `Configuration` already owns the canonical predicates — `deadlockCandidate`, computed at
   `Configuration.java:117` as `!allTerm && nextEnabled.isEmpty()`. Two callers ignore it and recompute
   from components, and they **do not compute the same thing**:

   - `ContextBoundedExplorer:187` — `enabled.isEmpty() && !config.allTerminated()`
   - `DeltaDebugger:95-96` — `!config.allTerminated() && !config.isDeadlockCandidate()`, which expands
     to `allTerminated() && !enabled.isEmpty()` — a *different* predicate

3. **E4 is provable, not unknown.** See 13.04.

4. **`DynamicState:343` already documents the hazard** in its own Javadoc, calling the explorer key
   "a debug aid rather than an identity." Reuse that wording rather than inventing new prose.

5. **A1's "no defect ships" is confirmed from source.** `Configuration.successor()` (lines 94–107)
   computes enabled threads from `this.programCounters` / `nextPcs`, **not** from the dead
   `ModelThread.pc`. Only `initial()` uses `ModelThread.enabled()`, safe because it seeds every counter
   to 0. The trap is real for future callers, not for production today.

6. **Spec 09's `Current State` is stale.** It states *"`format` field does not exist yet"*, but
   `format/dsl/` contains `DynamicState`, `DynamicStep`, `Evaluator`, `Parser`, `TypeChecker`.
   Specs 09/10 shipped; the doc never followed. A live E2 instance.

Two further constraints found while planning, not in the register:

7. **`KNOWN_EQUIVALENT_SURVIVORS` is keyed `Class:line`** — the existing entry is
   `dev.samhb.interleave.state.CanonicalEncoder:16`. 13.04 adds an entry in `ContextBoundedExplorer`,
   and **13.03 edits that same file at line 113, above 187**. A line-count change there moves the
   mutant and the entry silently stops matching. Stale entries print
   `WARNING — KNOWN_EQUIVALENT_SURVIVORS names mutants that were not non-kills` and do **not** fail
   the build, so the survivor degrades from "explained" to "unexplained" with nothing turning red.
   Ordering is the only thing preventing that; do not parallelise 13.03 and 13.04.

8. **The reflection helpers cannot represent the case 13.04 is about.** `Configuration`'s private
   constructor takes `enabledThreadIds`, `allTerminated`, and `deadlockCandidate` as *independent*
   parameters, and both helpers pass `false, false` for the booleans
   (`BitstateStoreDiagnosticsTest:406`, `HashingStateStoreLifecycleTest:485`, same 8-arg signature).
   Every reflected fixture is therefore by construction neither all-terminated nor a deadlock
   candidate. This constrains 13.01's design.
   **Superseded by 13.01** — both helpers and the lines cited are gone, replaced by
   `Configuration.forTest`. The constraint itself was correct and shaped 13.01's design.

9. **B2 and D5 are mutually exclusive, which no single reading of this plan reveals.** *(Found while
   implementing 13.03.)* D5 kept line 187's redundant call so 13.04 could explain the surviving mutant
   against it. B2's assigned consolidation deletes that call, so the mutant is deleted rather than
   explained — `ContextBoundedExplorer` went 104/105 to 103/103 and the total 270 → 268.

   Correction #7 saw a piece of this: it warned that 13.03 edits line 113 *above* 187, so a line-count
   change would move the mutant and the `Class:line` entry would silently stop matching. It
   anticipated line **movement**. What occurred is the **deletion** of the mutant's subject, which no
   amount of keying discipline survives. The sequencing advice was right about order and wrong about
   the failure mode.

   **Resolved** by keeping the consolidation and reframing 13.04 from *explaining a survivor* into
   *recording why the removal was safe*. The score rose 0.34 points with no new test — the effect D5
   forbade, reached because the assigned work deleted the redundancy. See
   [13.03](03-canonical-configuration-key.md#the-consequence-l187s-mutant-disappeared) for why that
   distinction is one of intent and why the revert, if you reject it, is one line.

**Out of scope, settled:** `IndependenceRelation:39-40` keys on `MemoryLocation.toString()`, not
`SharedState.toString()` — a different class. `MemoryLocation` is the **exemplar** of the discipline
13.03 wants: `equals`, `hashCode`, and `toString()` all delegate to the same `name`, so its string
comparison is provably sound. Use it as the model; do not touch it.

## Decisions taken

| # | Decision |
|---|---|
| D1 | One set, 8 specs, 13.08 (A4) lands last |
| D2 | Mutation floor **frozen at 94** for the whole set; `EXPECTED_TOTAL_MUTANTS` re-measured per-spec when it moves; floor re-derived once at set exit |
| D3 | 13.03 = canonical key + contract note. **Do not** rewrite the six `toString()` implementations |
| D4 | E1 = **non-failing** CI drift notice. Never a gate |
| D5 | 13.04 = **record the equivalence proof, keep the redundant call.** Total stays 270, score stays 94.81% |

**Why D2.** Every spec passes or fails against a number it did not choose, so no spec can raise the
gate to launder its own regression. Re-derivation happens once, from a post-remediation run, exactly as
12.06 landed last and derived from post-remediation numbers.

**Why D5.** 12.06 declined to delete a proven-equivalent mutant for the same reason — it would raise
the score with no new test. Consistency with that precedent is worth more than the cleaner line.

## Spec Set Structure

| Spec | Title | Closes | Priority |
|---|---|---|---|
| [01-configuration-test-factory.md](01-configuration-test-factory.md) | `Configuration` test factory | D1 | **HIGH — unblocks the rest** |
| [02-model-thread-dead-pc.md](02-model-thread-dead-pc.md) | `ModelThread` dead program counter | A1 | HIGH |
| [03-canonical-configuration-key.md](03-canonical-configuration-key.md) | Canonical key + `Configuration`'s predicates | B1, B2 | **HIGH — highest risk** |
| [04-context-bounded-l187.md](04-context-bounded-l187.md) | `ContextBoundedExplorer` L187 | E4 | MED |
| [05-dsl-encoding-completeness.md](05-dsl-encoding-completeness.md) | DSL encoding completeness | A2 | MED |
| [06-thread-count-general-flags.md](06-thread-count-general-flags.md) | Thread-count-general flag arrays | A3 | LOW |
| [07-conventions-and-process.md](07-conventions-and-process.md) | Conventions and process debt | D2, E1, E2, E5 | LOW |
| [08-godefroid-source-set.md](08-godefroid-source-set.md) | Godefroid `source` set | A4 — **design note, not implemented** | **MED — its own session** |

Filenames are **not hyperlinks yet** — each spec file is written when its spec is implemented, and
eight links to files that do not exist is eight broken links that a linter will not catch. MD051
validates fragments only, which is how the one fragment link below survived review while these eight
did not get reported. Link them as each spec lands.

## Dependency Graph

```
01-configuration-test-factory   (no dependency — entry point)
    ├── 02-model-thread-dead-pc
    ├── 03-canonical-configuration-key
    └── 08-godefroid-source-set

03-canonical-configuration-key ──► 04-context-bounded-l187   (positional; see correction #7)

05-dsl-encoding-completeness   (no hard dependency)
06-thread-count-general-flags  (no hard dependency)
07-conventions-and-process     (no dependency)
```

**01 first because the rest need it.** 02, 03, and 08 all need fixtures at arbitrary program counters,
and today that requires reflection.

**03 before 04, and not parallel with it.** Not a behavioural dependency — a positional one. 03 edits
`ContextBoundedExplorer` above L187, and `KNOWN_EQUIVALENT_SURVIVORS` is keyed `Class:line`.

## Blast radius

PIT's scope is `state.*` + `cb.*`. Everything else is ratchet-neutral *pending verification*.

| # | Spec | Touches | Ratchet |
|---|---|---|---|
| 13.01 | core + test | — | neutral |
| 13.02 | core | — | neutral (correctness proven, correction #5) |
| **13.03** | core, search, por, dpor, **cb** | — | **moves — re-measure** |
| 13.04 | `build.gradle.kts` only | — | **neutral by design** |
| 13.05 | format/dsl | — | verify |
| 13.06 | format/registry | — | verify |
| 13.07 | docs + CI | — | neutral |
| **13.08** | por | — | **may move — re-measure** |

## Set exit — floor re-derivation

One full post-remediation run, re-derive the floor from it, update `EXPECTED_TOTAL_MUTANTS`. If 13.08
slips, re-derive against whatever landed rather than deferring the question again.

## Verification Checklist (per spec, filled in as each lands)

- [ ] `./gradlew --max-workers=1 -PpitestThreads=1 clean test javadoc` — full suite green
- [ ] `./gradlew --max-workers=1 -PpitestThreads=1 pitest` — re-run, and the spec's named mutants flipped
- [ ] `./gradlew --max-workers=1 mutationRatchet` — passes, and its per-class census matches the spec's claim
- [ ] Falsification check named in the spec executed, and reverted
- [ ] Total mutant count unchanged from the recorded baseline, or re-measured and the reason recorded
      (Spec 12.06 §R5)
- [ ] For 13.03 / 13.05 / 13.06 / 13.08: configuration counts across **every corpus program × all three
      explorers**, before and after

## Known Risks

- **13.03 is the highest-risk edit.** Consolidating a key changes exploration unless proven not to, and
  it touches PIT scope at `ContextBoundedExplorer:113`. If `lastThreadId` is dropped, `statesExplored`
  changes. The before/after diff is mandatory, not optional.
- **13.03's "read the stored predicate" refactor is not mechanical.** Two call sites currently compute
  *different* predicates; unifying them changes behaviour at one of them by design.
- **13.01's factory shape is load-bearing.** If it re-exposes `allTerminated` / `deadlockCandidate` as
  free parameters it reproduces the defect it is meant to remove, and later tests will assert against
  fixtures that cannot represent the case.
- **13.05 inherits 12.07's hazard verbatim** — a new field at the head of an encoding invalidates
  pinned byte strings and bit positions.
- **13.08 is an algorithm, not a patch.** The register says so and is right. It is the item most likely
  to slip, and if it does it must be re-deferred with a fresh rationale, not dropped quietly.
- **Deletions need explicit sign-off** (13.02), per the 12.01 §R4 precedent.
- **Every register ref is pinned to `ba3fe01`.** Re-verify before acting on any of them.

## Rejected alternatives

| Alternative | Why rejected |
|---|---|
| Two spec sets — core debt, then POR/DSL | Separates the correctness foundation from the algorithm work, but creates two indexes to cross-read, and A4 becomes the item that keeps not happening. |
| Re-defer A4 rather than scheduling it last | Keeps every spec small and uniform, but A4 is already deferred once. Scheduling it last gives it a definite position without inflating any other spec. |
| Raise the floor as each spec improves the score | Couples each spec's correctness to its own optimism. A spec that both regressed coverage and raised the floor to match would go green. |
| Gate on the README per-class table disagreeing with the census | Couples the build to a documentation format. 12.06 already declined this, and the register's own reasoning is that such a gate gets switched off rather than fixed. |
| Rewrite all six `toString()` methods to be value-based | More thorough, and gives deterministic diagnostics — but `toString()` is production code inside PIT's scope, so the total moves. The contract note is what the register itself calls "cheaper and arguably more correct." |
| Delete the redundant `allTerminated()` call at L187 | Raises the score to 95.17% with no new test. See D5. |
| `Configuration.equals`/`hashCode` + identity sets | Deepest fix, but adds a collision-handling surface and churns three explorers more than the duplication warrants. |
