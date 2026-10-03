# Spec 12.05 — `ContextBoundedExplorer` Contract Tests and a 3-Thread Corpus Program

## TL;DR

**Rescoped 2026-10-02. The original premise was measured and did not survive.**

The spec was written on this theory: every corpus program has exactly two threads, and that is *why*
`ContextBoundedExplorer`'s dominance logic is undertested — two threads produce too few distinct
`(config, lastThreadId, preemption)` triples for wrong dominance to be observable. The fix was to add one
3-thread program and show that injected dominance defects then turn the suite red.

The program was added. It changes nothing.

**R4 — the defects were already caught.** Exact JUnit XML failure counts, 404 tests at HEAD versus 406
with `lost-update-3t` added:

| injected defect | at HEAD | with `lost-update-3t` |
|---|---|---|
| 4, budget-blind visited key (`-1`) | 59 | 59 |
| 5, records `currentPreemptions + 1` | 47 | 47 |
| 6, always records zero cost | 1 | 1 |

Identical. The suite was already red for all three. The claim that defects 4–6 survive was true when PR
#29 measured it and is stale now; tests that landed since catch them.

**R10 — no mutant moved.** PIT scoped to `ContextBoundedExplorer`, with and without the program:
105 mutants, 86 killed, 13 survived, 6 no coverage, and **no status changes at all**. Not one.

So the survivors were never caused by too small a corpus:

- **L87, L88** are `store.clear()` and `visitedStates.clear()` — see §Relationship to 12.02.
- **L82** removes `bitstate.maxPreemptions()` from an *exception message*. Only a test asserting the
  message text can kill it; corpus size is irrelevant.
- **L113 x5** is `visitedStates.put`'s result-map key. Distinct configurations silently collide and are
  lost from `states()`. No verdict-level test can see it, because the verdict is unaffected.

**What this spec does instead.** The corpus program is kept — it is a real coverage improvement, and R2's
measurement cannot exist without it — and the mutants are closed by asserting the four contracts
directly. That works where the corpus did not:

| | before | after |
|---|---|---|
| killed (scoped to `ContextBoundedExplorer`) | 86 / 105 | **94 / 105** |
| survived | 13 | **5** |

Eight mutants closed, none of them by adding a program.

## Objective

Give `ContextBoundedExplorer`'s result-reporting and search-lifecycle contracts real, direct assertions,
and keep one 3-thread corpus program so the corpus is no longer uniformly 2-thread.

## Scope

- **Package:** `interleave` / `src/main/java/dev/samhb/interleave/corpus` (or `bugs`), `format`, and
  `cb` for the contract tests
- **Modifies:** the corpus resource(s), the corpus registry, `README.md` corpus listing, any
  benchmark/attestation table that enumerates programs; adds
  `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerContractTest.java`
- **Off-limits:** `cb/ContextBoundedExplorer.java` internals — this spec writes tests, not the algorithm.
  If a contract assertion reveals a real defect, that fix is a separate scoped change with its own
  regression test. `format/ProgramLoader.java` invariant registrations — Specs `09-json-dsl-core` /
  `10-json-dsl-invariants` own those.

## Non-Goals

- **Not making dominance observable via corpus size.** Attempted, measured, abandoned. Do not
  re-attempt without new evidence; a future corpus program is a coverage improvement, not a
  mutant-killing strategy.
- Not writing the `CorpusDominanceCoverageTest` this spec originally specified. Its headline tests would
  be vacuous, because the defects they target are already caught.
- **Not pinning absolute state counts.** The original spec's rationale still holds and is preserved here:
  PR #29 recorded that a pruner exploring 12% fewer states passes verdict-agreement, monotonicity, and
  store-contract tests alike. A count guard pins the implementation, not the property.
- Not changing the CBS algorithm, the `StateStore` interface, or the dominance equivalence itself.
- Not raising the mutation score by itself. The success criterion is that specific named mutants die.

## Current State

### The corpus is no longer uniformly 2-thread [verified]

`bugs/BugCorpus.all()` returns **eight** programs. Seven declare two threads; `lost-update-3t` declares
three.

| program | threads | expected verdict | invariant |
|---|---|---|---|
| `peterson` | 2 | `PASS` | none |
| `broken-peterson` | 2 | `VIOLATION` | `mutual_exclusion_peterson` |
| `broken-peterson-v2` | 2 | `VIOLATION` | `mutual_exclusion_peterson` |
| `deadlock` | 2 | `DEADLOCK` | none |
| `double-checked-locking` | 2 | `VIOLATION` | `dcl_uninitialized_observed` |
| `lost-update` | 2 | `VIOLATION` | `counter_equals` |
| **`lost-update-3t`** | **3** | `VIOLATION` | `counter == 3` (declarative) |
| `torn-counter` | 2 | `VIOLATION` | `torn_read` |

### R2 measurement [verified]

Exhaustive DFS configuration counts, **measured with invariants**, which is how the corpus's own programs
are run:

| program | configurations |
|---|---|
| `broken-peterson`, `broken-peterson-v2` | 46 (2-thread maximum) |
| **`lost-update-3t`** | **84** |
| `peterson` | 42 |
| `double-checked-locking` | 17 |
| `deadlock` | 15 |
| `lost-update` | 13 |
| `torn-counter` | 8 |

Margin: **1.83x** over the 2-thread maximum.

**Both figures must be measured the same way.** Measured *without* an invariant, `broken-peterson` is 55
rather than 46, because a violating configuration ends the search before its successors are created.
That 46-versus-55 ambiguity already caused one misreading during Spec 12.07; R2 compares like for like
and the numbers above are the with-invariant ones.

### The typed format cannot express 3 threads [verified, new]

The original spec asserted that multi-thread programs were already expressible and that no work was
needed. True of the DSL, false of the typed path:

- `StateRegistry` creates the counter state as `CounterState.of(counter)`, which sizes per-thread
  registers for **exactly two threads**. A 3-thread typed program throws on thread 2's register write.
- `DslLoader` sizes `DynamicState` from the declared thread count, so the **declarative** format is
  correct.

`lost-update-3t` is therefore declarative. Fixing the typed path would be a change to
`StateRegistry`, outside this spec's scope.

### Defect-injection evidence [STALE — superseded 2026-10-02]

The original table, preserved because its conclusion is now known to be wrong:

| # | Injected defect | Direction | Caught by Part B, as measured then |
|---|---|---|---|
| 4 | Budget-blind visited key (`markVisited(config, tid, -1)`) | over-prunes | **NO** |
| 5 | Record `currentPreemptions + 1` (inverted dominance) | over-prunes | **NO** |
| 6 | Always record zero cost | over-prunes | **NO** |

**All three now turn the suite red**, at HEAD, without `lost-update-3t` — see the R4 table in the TL;DR.
Defect 6 is caught by exactly one test, `ContextBoundedExplorerTest.zeroBudget_allowsOnlyContinuations`.

### Mutants this spec owns [verified]

PIT scoped to `dev.samhb.interleave.cb.ContextBoundedExplorer`, statuses after this spec:

| line | symbol | mutator | before | after |
|---|---|---|---|---|
| L82 | `explore` — capacity-guard message | `NON_VOID_METHOD_CALLS` | SURVIVED | **KILLED** |
| L87 | `dfs` — `store.clear()` | `NON_VOID_METHOD_CALLS` | SURVIVED | **KILLED** |
| L88 | `dfs` — `visitedStates.clear()` | `NON_VOID_METHOD_CALLS` | SURVIVED | **KILLED** |
| L113 | `dfs` — result-map key construction | `NON_VOID_METHOD_CALLS` x5 | SURVIVED x5 | **KILLED x5** |

L112 (`markVisited`) is KILLED and was never a target. The original spec's "L113 x5" is
`visitedStates.put`, not `markVisited`; the two lines are adjacent and were easy to conflate.

### Remaining survivors in scope [verified]

Per-line counts, so the totals can be checked rather than trusted:

| line | symbol | `SURVIVED` | `NO_COVERAGE` | note |
|---|---|---|---|---|
| L41 | `explore` | 0 | 2 | assigned to Spec 12.04 by the original table |
| L176 | `dfs` | 0 | 4 | dead branch / recursion guard |
| L187 | `dfs` | 1 | 0 | |
| L203 | `emitIncompleteTraceIfNeeded` | 2 | 0 | method and its lambda |
| L204 | `emitIncompleteTraceIfNeeded` | 1 | 0 | lambda |
| L214 | `addTrace` | 1 | 0 | |
| **total** | | **5** | **6** | |

**Not owned here.** L41's two `NO_COVERAGE` are assigned to Spec 12.04; the remaining 9 are unassigned
and are a candidate for a future spec.

## Relationship to 12.02

L87 and L88 sit next to Spec 12.02 §(a), but assert a **different fact about a different class**, and are
**not** handed to it:

- **12.02 (a)** is scoped to `interleave/state` and asserts that `HashingStateStore.clear()` empties its
  own four collections. That is a fact about the *store*.
- **L87/L88** are in `cb/ContextBoundedExplorer` and assert that the *caller* invokes `clear()` at all.
  Removing L87 leaves the store's `clear()` perfectly correct while every subsequent search on that store
  prunes against the previous search's contents.

Complementary, not duplicate. Handing them over would either drag 12.02's package scope out to `cb/` or
file a CBS mutant under a spec that does not cover that package.

## Invariants

- **The dominance equivalence is the property under test.** A state reached at preemption budget *p*
  SHALL be pruned only if some recorded state with identical `(config, lastThreadId)` was reached at
  budget *q ≤ p*.
- **A correct program SHALL be `PASS` under exhaustive DFS.** Under a *bounded* CBS run it SHALL be
  `PASS` when it completes within its budget, and `INCOMPLETE` **only** when the budget is exceeded —
  never the reverse. These must not be asserted as agreement: `SoundnessAttestation` excludes
  `INCOMPLETE` from cross-strategy agreement precisely because a bounded run on `peterson` can be
  `PASS` under DFS and `INCOMPLETE` under CBS at K=2.
- **A buggy program SHALL report its declared verdict** under every strategy expected to find it.
- **Adding a corpus program SHALL NOT change the total mutant count.** Mutators target production source;
  new tests only move mutants *between* statuses. A change in the total means production code changed or
  scope moved — investigate before reading the percentage (Spec 12.06 §R5).
- **A contract test SHALL compare configurations by value, never by identity.** `Configuration` declares
  no `equals` or `hashCode`, so a set of `Configuration` objects measures object identity and reports
  nonsense — including the false result that every visited configuration is missing from the result map.
  This cost two wrong probes during this spec's implementation and is the single most important
  methodological note here.

## Requirements

1. **THE SYSTEM SHALL** keep `lost-update-3t`, a 3-thread program in the **declarative** format, with a
   thread-count-agnostic invariant, and SHALL NOT use `mutual_exclusion_peterson`
   [verified: rejected by `ProgramLoader` for requiring exactly two threads].
2. **WHEN** the new program is run exhaustively under DFS **with its invariant**, **THE SYSTEM SHALL**
   report a measured configuration count exceeding the 2-thread maximum by a documented margin. Recorded
   in §Current State as 84 against 46.
3. **THE SYSTEM SHALL** assert that the undersized-store rejection names the store's configured capacity
   (L82).
4. **THE SYSTEM SHALL** assert that a reused store does not prune the next search, and that
   `visitedStates` does not carry configurations from a previous run into a different program's result
   (L87, L88).
5. **THE SYSTEM SHALL** assert that every distinct configuration reached appears in `states()`, comparing
   configurations by value against the `StateVisitor` ground truth (L113).
6. **THE SYSTEM SHALL NOT** assert `states().size() == statesExplored()`. That is false on correct code;
   see §Design.
7. **WHEN** the corpus is extended, **THE SYSTEM SHALL** update every place that enumerates programs and
   would otherwise silently omit the new one — at minimum the README corpus listing, the benchmark
   states-explored table, the soundness attestation inputs, and the corpus-size assertion.
8. **THE SYSTEM SHALL** make the Java-fixture exclusion explicit rather than implicit, so a future typed
   program added without a fixture fails instead of escaping equivalence checking.
9. **THE SYSTEM SHALL** record the measured mutant statuses for L82, L87, L88, L113 in §Current State.

## Acceptance Criteria

- [x] A >=3-thread program exists, is registered, and is listed (R1, R7)
- [x] Its invariant loads; its exhaustive DFS count is measured and exceeds the 2-thread maximum (R2)
- [x] The corpus is no longer uniformly 2-thread (R1)
- [x] L82, L87, L88 and L113 x5 are all **KILLED** (R3, R4, R5, R9)
- [x] Every corpus program without a Java fixture is declarative, and that set is asserted by name (R8)
- [x] `SoundnessAttestation` passes with the extended corpus
- [x] `./gradlew clean test javadoc` passes
- [x] A correct program yields no `VIOLATION` at any bound, and only `COMPLETED` traces once its budget
      suffices. Measured for `peterson`: `{COMPLETED=3, INCOMPLETE=1}` at K=1 and K=2, `{COMPLETED=3}`
      at K=3. The `INCOMPLETE` trace at K<=2 is the budget-exceeded case §Invariants describes, not a
      failure — an earlier wording asserted "only `COMPLETED` traces" unqualified and was wrong at K<=2.
- ~~A buggy program whose violation is reachable only via states over-pruning drops~~ — **retired, the
      claim was false.** `lost-update-3t` was asserted to need all three threads read before any thread
      writes. It does not. The shortest violating trace is 6 steps: thread 0 reads and writes
      (`counter = 1`), then threads 1 and 2 both read `1` and both write `2`, losing thread 2's
      increment. A single lost update violates `counter == 3`, and 15 of 21 traces violate. Separately,
      R4 shows over-pruning does **not** hide this program's violation, so it cannot be the
      over-pruning-only program this criterion asks for. Whether such a program exists is open; it is
      not a prerequisite for this spec, whose mutants are now closed directly.
- [ ] `CorpusDominanceCoverageTest` as originally specified — **withdrawn**, see §Non-Goals
- [ ] Defect-injection protocol R4 demonstrating the corpus program catches injected defects —
      **withdrawn as unachievable**, see §TL;DR

## Design

### The program shape

N threads performing read-modify-write on a shared counter, one lost-update bug, under a
thread-count-agnostic invariant. Three threads, two steps each: `local.r = counter` then
`counter = local.r + 1`, invariant `counter == 3`. If all three read before any writes, the counter ends
at 1 rather than 3.

Chosen over a larger thread count deliberately: every additional thread multiplies the state space and
test runtime, and 3 is the minimum that breaks the 2-thread degeneracy. It stays exhaustively
DFS-able at 84 configurations.

### `states()` versus `statesExplored`

These are different quantities and the difference is **not** a defect. Measured across the corpus at
K=1..3:

- `statesExplored` counts exploration **events** — `(config, lastThreadId, preemption)` triples. The same
  configuration is legitimately reached at several preemption counts, up to **4 times** measured.
- `states()` reports **distinct configurations**. Every configuration reached appears, with **zero**
  missing on every program at every bound tested.

The result-map key omits the preemption count that the store's visited key includes, and that is
harmless: two explorations sharing `(config, lastThreadId)` at different costs hold the same
`Configuration` value, so the map still reports it.

Asserting `states().size() == statesExplored()` would fail on correct code, and asserting the map "misses"
configurations would fail for the same reason. Both were tried and rejected.

## Tests

**File:** `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerContractTest.java`

- `undersizedStore_isRejectedWithAMessageNamingTheConfiguredCapacity` (L82)
- `storeIsClearedBeforeTraversal_soAPrePopulatedStoreDoesNotPrune` (L87)
- `visitedStatesIsClearedBetweenRunsOnTheSameExplorer` (L88) — note this needs a **different** program
  per run; repeating one program re-adds identical keys and cannot detect a missing `clear()`
- `everyDistinctConfigurationReached_isReportedInStates` (L113) — the visitor is the ground truth,
  configurations compared by value
- `statesMapReportsDistinctConfigurationsWhileStatesExploredCountsEvents` — records the measured
  relationship so the deliberate omission in the test above is not mistaken for an oversight

Plus the two corpus-enumeration tests in `ProgramLoaderTest`
(`everyMigratedProgram_equivalentToJava`, `everyCorpusProgramWithoutAJavaFixtureIsDeclarative`) and
`BugCorpusTest` (`corpusHasExactlyEightPrograms`, `corpusProgramNamesAreDistinct`).

## Constraints

- **Dependencies:** Spec 12.01 (a lossy encoder would make the R2 counts meaningless). Spec 12.04 owns
  L41's `NO_COVERAGE` x2; if 12.04 lands after this spec, re-derive its bounds against 8 programs.
- **Backward compatibility:** the corpus is a shipped artifact — README counts, benchmark output, and the
  evidence artifact all change. Anything asserting "7 programs" must be updated in the same commit.
- **Runtime:** measured; the 3-thread program adds 84 configurations and does not slow the suite
  materially.
- **If a contract assertion reveals a real defect,** that is a success of this spec. Ship the corpus
  change and the production fix as separate commits.

## Commands
```bash
./gradlew test --tests "*ContextBoundedExplorerContractTest*"
./gradlew test --tests "*Corpus*"
./gradlew test --tests "*SoundnessAttestation*"
./gradlew pitest -PpitestTargetOverride=dev.samhb.interleave.cb.ContextBoundedExplorer
./gradlew clean test javadoc
```

## Map
- `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java` — the class under test
- `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerContractTest.java` — this spec's tests
- `src/main/java/dev/samhb/interleave/bugs/BugCorpus.java` — the corpus registry
- `src/main/resources/programs/lost-update-3t.json` — the 3-thread program
- `src/main/java/dev/samhb/interleave/core/Configuration.java` — declares no `equals`/`hashCode`
- `src/main/java/dev/samhb/interleave/format/registry/StateRegistry.java` — `counter` state, 2-thread only
- `src/main/java/dev/samhb/interleave/format/dsl/DslLoader.java` — sizes `DynamicState` by thread count
- `src/main/java/dev/samhb/interleave/report/StatesExploredTable.java` — corpus enumeration
- `src/main/java/dev/samhb/interleave/report/SoundnessAttestation.java` — cross-strategy agreement
- Spec 12.02 §(a) — the related `HashingStateStore.clear()` contract; see §Relationship to 12.02