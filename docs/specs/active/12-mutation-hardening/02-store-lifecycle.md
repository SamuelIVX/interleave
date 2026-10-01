# Spec 12.02 — `HashingStateStore` Lifecycle & Equivalent-Mutant Adjudication

## TL;DR

Three things, in priority order. **(a)** `clear()` has four collection-clears and no test calls it,
so a mutant that drops one of them survives; the omission is a state leak between searches.
**(b)** `freshCopy()` returning `null` is never exercised, and a `null` store NPEs mid-exploration
far from its cause. **(c)** Thirteen survivors are hash-arithmetic mutants whose equivalence status
is currently *assumed*, not established — and that assumption is load-bearing, because chasing an
equivalent mutant produces a test that asserts an implementation detail. This spec fixes (a) and
(b) with cheap tests, and defines a rigorous procedure for (c) that ends in either a justified
suppression or a real test.

## Objective

Give `HashingStateStore`'s lifecycle methods — the ones that manage state across a search rather
than during it — real coverage, and replace the working assumption that the nine low-priority
survivors are equivalent mutants with a per-mutant adjudication backed by evidence.

## Scope

- **Package:** `interleave` / `src/main/java/dev/samhb/interleave/state`
- **Modifies:** adds `src/test/java/dev/samhb/interleave/state/HashingStateStoreLifecycleTest.java`
- **Off-limits:** `state/CanonicalEncoder.java` — Spec 12.01 owns it; this spec's tests depend on
  12.01 landing first, but must not modify it. `state/BitstateStore.java` — Spec 12.03.

## Non-Goals

- Changing the `StateStore` interface or either implementation's search semantics.
- Reworking the two-level hash prefilter (`preemptionHashes` + `minPreemptions`). That design is
  shipped and specified; this spec tests it, not redesigns it.
- Asserting on specific hash *values*. Hash values are an implementation detail; tests that pin them
  break on every legitimate change to the encoding.

## Current State

All claims [verified] by reading `state/HashingStateStore.java` (125 lines) and the mutation report.

**Fields** (`L15–19`): `encoder`, `visitedHashes`, `visitedStates`, `preemptionHashes`,
`minPreemptions`.

**The three lifecycle methods under test:**

| symbol | line | body | current test coverage |
|---|---|---|---|
| `clear()` | `L51–56` | clears all four collections | **none** |
| `preemptionEntryCount()` | `L97–99` | `return minPreemptions.size();` | one call in `StateStorePreemptionTest` |
| `freshCopy()` | `L122–124` | `return new HashingStateStore();` | three callers, always non-null in practice |

`isVisited(Configuration)` at `L33–40` is the two-level scheme: return `false` unless the hash
prefilter hits, then confirm against the exact encoded state. The `BooleanTrue` mutant at `L39`
survives — returning `true` unconditionally would make every state look visited, and no test
catches it. That is Spec 12.03-adjacent work in spirit but lives in this class; §R3 below owns it.

**Mutation inventory — 61 mutants, 46 killed (75.4%), 15 not killed:**

| line | symbol | mutator | status | priority |
|---|---|---|---|---|
| L39 | `isVisited(Configuration)` | `TRUE_RETURNS` | SURVIVED | **high** — catastrophic-if-real |
| L52 | `clear` — `visitedHashes.clear()` | `VOID_METHOD_CALLS` | SURVIVED | **high** — leak |
| L54 | `clear` — `preemptionHashes.clear()` | `VOID_METHOD_CALLS` | SURVIVED | **high** — leak |
| L98 | `preemptionEntryCount` | `NON_VOID_METHOD_CALLS` | SURVIVED | med |
| L98 | `preemptionEntryCount` | `PRIMITIVE_RETURNS` | SURVIVED | med |
| L102 | `preemptionHash`/key helper | `NON_VOID_METHOD_CALLS` | SURVIVED | low — R4 |
| L103 | `preemptionHash` | `MATH` ×2, `NON_VOID_METHOD_CALLS` | SURVIVED ×3 | low — R4 |
| L104 | `preemptionHash` | `PRIMITIVE_RETURNS` | SURVIVED | low — R4 |
| L114 | `preemptionHashIndices`/key helpers | `MATH` ×2, `NON_VOID_METHOD_CALLS`, `PRIMITIVE_RETURNS` | SURVIVED ×4 | low — R4 |
| L123 | `freshCopy` | `NULL_RETURNS` | **NO_COVERAGE** | **high** — NPE |

Totals: 14 `SURVIVED` + 1 `NO_COVERAGE` = 15.

Note the asymmetry: `clear()` at `L53` and `L55` have killed mutants but `L52` and `L54` do not.
Partial coverage is worse than none here — a test that calls `clear()` and asserts *something*
changed will pass with one collection still populated.

`L102`, `L103` and `L114` sit on lines that **also** carry killed mutants. An inventory that groups by
line and reports only the dominant status will miss them; a per-`(line, mutator)` enumeration does
not. R6 covers all 9 of these low-priority survivors.

## Invariants

- **`clear()` empties every collection.** All four fields (`visitedHashes`, `visitedStates`,
  `preemptionHashes`, `minPreemptions`) SHALL be empty after `clear()` returns. A partially-cleared
  store reports states as visited that this search never explored, pruning branches it should
  follow.
- **`freshCopy()` never returns `null`.** A caller holding a `null` store fails at the first
  `isVisited` call, far from the code that produced it.
- **`freshCopy()` is independent.** Mutating the copy SHALL NOT affect the original, or two searches
  sharing a "copy" silently share deduplication state.
- **`isVisited` is exact, never approximate.** This store is the differential oracle for
  `BitstateStore`. If it reports a state as visited that it has not seen, every verdict-level
  conclusion drawn by comparing the two stores is unsound. This is why its `TRUE_RETURNS` mutant is
  the highest-priority item in this spec despite being one line.
- **A surviving mutant is not automatically a defect.** Some are equivalent by construction. §R4
  defines how to tell, and requires the reasoning be written down either way.

## Requirements

1. **WHEN** `clear()` returns, **THE SYSTEM SHALL** have all four collections empty, verified
   independently per collection rather than through a single aggregate assertion.
2. **WHEN** `clear()` is called on a store populated by `markVisited` and `markVisited(config, tid, p)`,
   **THE SYSTEM SHALL** report `size() == 0` and `preemptionEntryCount() == 0`, and SHALL answer
   `isVisited` `false` for a state that was marked before the clear.
3. **WHEN** `freshCopy()` is called, **THE SYSTEM SHALL** return a non-null store whose contents are
   independent of the original — marking in the copy SHALL NOT change the original's answers.
4. **THE SYSTEM SHALL** resolve the `TRUE_RETURNS` mutant at `isVisited(Configuration)` `L39` — that
   is, it SHALL either kill it with a test, or record a documented suppression. **Corpus-only
   absence SHALL NOT, on its own, establish either outcome.** The prefilter's guard is
   `if (!visitedHashes.contains(hash)) return false;` — the hash is an *optimisation*, so the question
   is not "does any pair collide in today's corpus" but "can any supported `Configuration` pair
   collide, and what does the store answer then". Three dispositions are correct, and which one applies
   is itself a finding about this store:

   - **A deterministic colliding pair exists and is constructible** → write a test that marks one and
     queries the other, asserting the exact contract. This is the strongest outcome, and it is
     constructible here: `HashingStateStore` exposes `markVisited(Configuration)`, and a colliding
     pair can be found by inverting the hash rather than waiting for the corpus to produce one.
   - **The pair cannot be constructed, proven by enumeration over the full supported `Configuration`
     domain** → suppression, with that enumeration written down.
   - **A wrong prefilter answer is contract-neutral** → suppression on *that* basis, which is a
     different and stronger claim than "nothing observed it". Proving it requires reading every
     reader of the prefilter and the store contract, not running the suite.

   Recording which of the three applies is required, because "no reachable pair collides in the
   current corpus" is a fact about the corpus and tells you the prefilter does no work *on this
   corpus* — it does not tell you the guard is correct, and it cannot support a deletion on its own.
5. **THE SYSTEM SHALL** pin `preemptionEntryCount()` to `0` before any preemption is marked, and to
   the exact count after each `markVisited(config, tid, p)` for distinct `(config, tid)` pairs,
   including the min-merging behaviour: marking the same key twice with different `p` values SHALL
   leave the count at 1.
6. **THE SYSTEM SHALL** adjudicate all **9** low-priority hash-arithmetic survivors as either
   *equivalent* (with the reasoning that establishes it) or *testable* (with a named test), and SHALL
   record the verdict per mutant in this spec's §Current State. The nine are: `L102` ×1, `L103` ×3,
   `L104` ×1, `L114` ×4. No mutant SHALL be left in an assumed state.
7. **THE SYSTEM SHALL NOT** add a test whose only assertion is a specific hash value, a specific
   `hashCode()` result, or the internal structure of a key string.

## Acceptance Criteria

- [ ] A test populates all four collections, calls `clear()`, and asserts each is empty
      **individually** — four separate assertions, so a partial clear fails (R1).
- [ ] A test asserts `size() == 0`, `preemptionEntryCount() == 0`, and `isVisited == false` for a
      pre-clear state after `clear()` (R2).
- [ ] A test asserts `freshCopy()` is non-null and that marking in the copy leaves the original
      unchanged (R3).
- [ ] `isVisited` is driven down the hash-prefilter-hit path with an unseen state and asserted
      `false` — **or**, if no reachable colliding pair exists in the current corpus, that absence is
      documented as the recorded suppression R4 permits, with the search for such a pair recorded.
      Either way the line is accounted for (R4).
- [ ] `preemptionEntryCount()` tests cover 0, 1, N, and the merge case (R5).
- [ ] §Current State carries a verdict row for all 9 low-priority survivors, each marked
      `equivalent — <reason>` or `tested — <test name>` (R6).
- [ ] A repo-wide grep confirms no test asserts a literal hash value (R7).
- [ ] `./gradlew clean test javadoc` passes.
- [ ] `./gradlew pitest` shows `HashingStateStore` above 75.4%, and specifically that L52, L54 and
      L123 are `KILLED`.

## Design

### R4 — killing the `TRUE_RETURNS` mutant at `L39`

This one needs care, because the obvious test does not kill it. `isVisited` short-circuits at `L35`:
if the hash prefilter misses, it returns `false` without ever reaching `L39`. A test using a fresh
store therefore never touches the mutant line.

To reach `L39` the prefilter must hit while the exact check does not. The two-level scheme
guarantees this by construction whenever two distinct states share a hash — but constructing such a
collision on demand is the hard part. Options, in preference order:

1. **A pre-seeded store.** Mark state A, then query state B where A and B collide on
   `hashCode(config)` but differ in `encode(config)`. Finding such a pair is a small search over
   corpus states; if none exists in the reachable space, that is a finding to record — it means the
   prefilter is doing nothing on this corpus, which is itself worth knowing.
2. **A deliberately-colliding test double.** A `SharedState` whose `encodeTo` is crafted to produce
   a chosen `encode` output while a different state produces the same hash. Requires control over the
   hash path and is over-engineered for one mutant.
3. **Accept it as unreachable-on-this-corpus** and suppress with that reason, per §R4's procedure.

Option 1 first. Option 3 is legitimate but must say *why* — "no reachable pair collides in the
current corpus" is a real finding, not a dismissal.

### R6 — the equivalent-mutant adjudication procedure

This is the part of the set most likely to be done badly, so it is procedural rather than advisory.
For each surviving hash-arithmetic mutant, in order:

1. **State the mechanism.** What does the mutation change? (e.g. "L114 drops the `lastThreadId`
   term, making the preemption key independent of the last scheduled thread.")
2. **Determine observability class:**
   - *Verdict-observable* — the mutation can change which states collide, which can prune a branch
     that mattered. **Not equivalent.** Requires a test.
   - *Diagnostic-observable* — the mutation only changes selectivity/FPR metrics a user reads, never
     correctness. Candidate for a metrics test (Spec 12.03 §R4 territory).
   - *Unobservable* — the mutation changes a value nothing reads. **Equivalent.**
3. **Verify, do not assume.** For *unobservable*, confirm by reading every reader of the value. A
   claim of equivalence is only as good as the enumeration of readers, and that enumeration must be
   written down. **A reader that stores into a Bloom filter is not a neutral reader**, even when the
   class it belongs to is `BitstateStore` rather than this one: a wrong index there changes
   false-positive membership and therefore pruning behaviour, so "only affects selectivity" has to be
   argued from the contract, not asserted. Where the argument cannot be closed, the mutant is
   verdict-observable by default and requires a test.
4. **Record:** `L114 NON_VOID_METHOD_CALLS — equivalent: the dropped value is read only by
   `preemptionHashIndices`, whose output feeds bit selection; a wrong index degrades selectivity,
   never correctness. Verified by tracing all readers of `preemptionHashIndices`, which reach only
   `BitSet.get`/`set` and the FPR counter — none of which can turn an unvisited configuration into a
   reported visited one without a coincidental collision.`
5. **Then** either suppress with that reason or write the test.

The example in step 4 is written to the shape it must take, not as a pre-answered verdict: the
reader enumeration is part of the record, and a reader list that stops at the class boundary has not
been enumerated.

The ordering matters. The earlier working claim that these mutants were equivalent was
**assumed, not verified** — and when a spec is handed to a reviewer, an assumed equivalence is
indistinguishable from a hidden defect. Step 3 is what separates them.

Note also that `preemptionHash`/`preemptionHashIndices` feed **bit selection in
`BitstateStore`**, so the readers for several of these cross into that class. Where the reader set
spans both classes, adjudicate once, in this spec, and have Spec 12.03 cite the verdict rather than
re-deriving it (DRY — one authoritative representation per fact).

## Tests

**File:** `src/test/java/dev/samhb/interleave/state/HashingStateStoreLifecycleTest.java`

- `clear_emptiesVisitedHashes_visitedStates_preemptionHashes_andMinPreemptions` (R1) — **four
  separate assertions**, not one aggregate
- `clear_afterMarkingVisited_makesStoreReportNothingVisited` (R2)
- `clear_resetsSizeAndPreemptionEntryCount_toZero` (R2)
- `freshCopy_isNonNull` (R3)
- `freshCopy_isIndependentOfOriginal` (R3)
- `isVisited_hashPrefilterHitButExactMiss_returnsFalse` (R4) — the mutant-killing test; requires the
  colliding pair from Design R4
- `preemptionEntryCount_isZeroBeforeAnyMark` (R5)
- `preemptionEntryCount_countsDistinctConfigThreadPairs` (R5)
- `preemptionEntryCount_mergeOfSameKey_doesNotIncreaseCount` (R5)
- `markVisited_sameKeyDifferentBudget_keepsMinimum` (R5) — pins the min-merge, which is the whole
  point of the cost-aware scheme

**Falsification check (not committed):** delete `preemptionHashes.clear()` from `clear()`; confirm
the R2 test goes red; revert. A second check: return `null` from `freshCopy()`; confirm R3 goes red;
revert.

## Constraints

- **Dependencies:** Spec 12.01. Its injectivity result is what makes any store-level assertion here
  trustworthy — if the encoder merges states, these tests pass for the wrong reason.
- **Backward compatibility:** no signature changes. `clear`, `freshCopy`, `preemptionEntryCount`,
  `size`, `isVisited`, `markVisited` are all part of the `StateStore` contract or already-public API.
- **Test-only change.** This spec must not modify production code unless §R6's adjudication reveals
  a genuine defect, in which case that becomes a separate, explicitly-scoped fix with its own
  regression test.

## Commands

```bash
./gradlew test --tests "*HashingStateStore*"
./gradlew test --tests "*StateStorePreemption*"
./gradlew pitest
./gradlew clean test javadoc
```

## Map

- `src/main/java/dev/samhb/interleave/state/HashingStateStore.java` — `clear`, `freshCopy`, `preemptionEntryCount`, `isVisited`, `markVisited`
- `src/main/java/dev/samhb/interleave/search/StateStore.java` — the interface these implement
- `src/main/java/dev/samhb/interleave/state/CanonicalEncoder.java` — Spec 12.01 owns it
- `src/test/java/dev/samhb/interleave/state/StateStorePreemptionTest.java` — existing preemption tests
- `src/test/java/dev/samhb/interleave/state/StateHashingTest.java` — existing encoding tests
