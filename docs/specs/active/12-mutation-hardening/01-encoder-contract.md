# Spec 12.01 — `CanonicalEncoder` Contract & Dead Code

## TL;DR

`CanonicalEncoder` is the root of the visited-key that both state stores use for deduplication.
If it is lossy, two distinct states collide, a store reports "already visited", and a reachable
violation is never explored — silently, with no error. **No store-level differential test can catch
this**, because both stores are fed the same encoder: a test comparing them sees two wrong answers
agree. This spec pins the encoder's contract (determinism and injectivity) and resolves one dead
public method. Highest-priority item in the set despite touching only 30 lines.

## Objective

Establish `CanonicalEncoder` as a verified-correctness boundary rather than an untested utility:
prove the properties every store depends on, and stop it carrying an untested public method that
nothing calls.

## Scope

- **Package:** `interleave` / `src/main/java/dev/samhb/interleave/state`
- **Modifies:** `state/CanonicalEncoder.java`; adds `src/test/java/dev/samhb/interleave/state/CanonicalEncoderContractTest.java`
- **Off-limits:** `state/HashingStateStore.java` and `state/BitstateStore.java` internals — Spec 12.02 and 12.03 own them. `core/SharedState.java` and every `encodeTo` implementation — the DSL spec set (`09-json-dsl-core`, `10-json-dsl-invariants`) owns state encoding.

## Non-Goals

- Changing how any `SharedState` encodes itself. This spec tests the encoder's *composition* of
  whatever `encodeTo` produces; it does not audit `encodeTo` implementations.
- Making `encode` fast. It already allocates two objects per call; that is not the problem.
- Auditing hash *distribution*. Spec 12.02 §R4 owns that; a good hash and a correct hash are
  different claims.

## Current State

All claims [verified] by reading `src/main/java/dev/samhb/interleave/state/CanonicalEncoder.java`
(30 lines) and by repo-wide grep for callers.

- `encode(SharedState)` returns `byte[]`, composing `ByteArrayOutputStream` → `DataOutputStream` →
  `state.encodeTo(out)` → `out.flush()` → `baos.toByteArray()` [verified].
- `equals(SharedState, SharedState)` is `Arrays.equals(encode(a), encode(b))` [verified]. **It has
  zero callers anywhere in the repository** — not in `src/main`, not in `src/test`, not in fixtures,
  examples, or docs [verified by repo-wide grep].
- `hashCode(SharedState)` is `Arrays.hashCode(encode(state))` [verified].
- Callers: `encode` ← `HashingStateStore.encode` (the private `Configuration`-keyed helper) and
  `StateHashingTest`; `hashCode` ← `HashingStateStore.hashCode`, `BitstateStore.hashCode`, and
  `StateHashingTest` [verified].

**Mutation inventory — 12 mutants, 6 killed (50.0%, worst in the scoped set), 6 not killed:**

| line | symbol | mutator | status |
|---|---|---|---|
| L16 | `encode` — `out.flush()` | `VOID_METHOD_CALLS` | **SURVIVED** |
| L24 | `equals` — `Arrays.equals` | `NON_VOID_METHOD_CALLS` ×3 | **NO_COVERAGE** |
| L24 | `equals` — return value | `FALSE_RETURNS` | **NO_COVERAGE** |
| L24 | `equals` — return value | `TRUE_RETURNS` | **NO_COVERAGE** |

The five L24 mutants are the dead method. The L16 mutant is the flush.

### R4 decision — `equals` DELETED (Branch A), decided by Sam 2026-10-01

`equals(SharedState, SharedState)` is **removed**. The change is a pure 4-line deletion and nothing
else in `CanonicalEncoder` moved [verified: `git diff` shows only those lines].

Rationale, recorded so the decision can be revisited on evidence rather than memory:

- **Zero callers**, re-verified immediately before deletion across `src/` and `docs/`.
- **It was the worst-covered method in the PIT scope** — all five of its mutants `NO_COVERAGE`,
  because no test ever executed the line.
- **It was a trap, which is the substantive reason.** It answers "do these produce the same bytes?"
  while a reader calling `encoder.equals(a, b)` would reasonably expect "are these the same state?"
  The two differ whenever a state omits a field from its encoding — which is exactly the defect 12.07
  found in `DeadlockState`. An untested public method on the correctness foundation invites the same
  class of bug this set exists to eliminate, so leaving it was not the neutral option.
- The counter-argument considered and rejected: "someone may need it later." Keeping dead code on a
  maybe is how it becomes load-bearing by accident.

**Consequence, measured:** global total **275 → 270**; `NO_COVERAGE` **14 → 9**; `KILLED` and
`SURVIVED` unmoved at 222 and 39. `CanonicalEncoder` goes from 6/12 (50.0%) to **6/7 (85.7%)**, and
mutation coverage rises 80.73% → 82.2%. Note the numerator did not move: deleting dead code removes
work from the denominator without inventing coverage, and that is the honest shape of this change.

### R5 verdict — the `out.flush()` mutant is EQUIVALENT, not uncovered

**The verdict was settled empirically, not assumed.** With `out.flush()` deleted, **the entire 400-test
suite still passes** [verified 2026-10-01]. The reason is mechanical: `ByteArrayOutputStream` ignores
`flush()` — it is a no-op for an in-memory sink — and `baos.toByteArray()` returns the whole buffer
regardless of any flush. Removing the call therefore cannot change observable behaviour.

So the L16 mutant is **equivalent by construction**, and per Spec 12.02 §R4 the correct response is a
**recorded suppression, not a contrived test**. A test that tries to kill it would have to assert an
implementation detail of `ByteArrayOutputStream`, which is precisely the outcome §R4's adjudication
procedure exists to prevent.

**What was done instead, and what was deliberately not:**

- `CanonicalEncoderContractTest.encode_flushRemovalIsUnobservable` pins the *consequence* — a
  `PetersonState` encoding is exactly 10 bytes — rather than the mechanism. It stays green with the
  flush removed, which is correct for an equivalent mutant, and it is what would notice if `baos` ever
  became a stream whose `flush()` mattered. Expected length is derived from the field list
  (`flag[0]`, `flag[1]` = 1 byte each; `turn`, `inCriticalSection` = 4 bytes each), **not** from a
  remembered count. An early draft asserted 6, having forgotten `inCriticalSection`; the correct
  figure is 10, and a 6-byte encoding would have silently dropped a field.
- **The mechanical PIT exclusion filter is deferred to Spec 12.06**, not added here. Adding a filter
  changes PIT's denominator and how excluded mutants are counted, and 12.06 owns exactly that
  accounting — introducing it mid-set would move the number 12.06 has yet to pin. Recorded here so the
  deferral is visible rather than forgotten.
- **The flush call was kept.** Only `equals` was approved for deletion, and the flush is defensively
  meaningful if `baos` is ever swapped for a buffering stream.

**Existing coverage** [verified, `StateHashingTest`]: one equal-pair assertion covering `encode` and
`hashCode` on the same pair of states. That is a smoke test, not a property test — it cannot
distinguish an injective encoder from a constant one.

## Invariants

- **Injectivity is the load-bearing property.** Distinct `SharedState` instances SHALL encode to
  distinct byte arrays. If two distinct states collide, every store built on this encoder silently
  merges them, `isVisited` returns `true` for an unvisited state, and the search prunes a branch
  that might have contained the only reachable violation. The failure is a wrong answer, not a
  crash, and it is indistinguishable from a correct run by inspection.
- **Determinism.** The same `SharedState` SHALL encode to the same bytes on every call, every run,
  and every JVM. A nondeterministic encoder produces a store that forgets states it has seen,
  re-exploring and potentially re-reporting.
- **Canonicalisation is the encoder's whole job.** Two states that *should* be equal for search
  purposes must produce equal bytes, or the store under-deduplicates (correct but slow); two states
  that should differ must produce different bytes, or it over-deduplicates (incorrect). This spec
  owns the second direction only — the first is Spec 12.02's concern.
- **The encoder must not become the place search policy lives.** It encodes a state; it does not
  decide what counts as the same state.

## Requirements

1. **WHEN** the encoder is given N pairwise-**distinct-in-value** search states from the corpus state
   space, **THE SYSTEM SHALL** produce N pairwise-distinct byte arrays.
   - "Distinct in value" is defined by the state model, **not by object identity**. Two equal-valued
     `SharedState` instances that are distinct objects MUST encode identically — that is
     canonicalisation, and a requirement demanding otherwise would be wrong.
   - The input set SHALL therefore be deduplicated by value before the count is taken. Sampling
     instances and calling them "distinct" would make this requirement unsatisfiable-by-design
     whenever the walk produces equal-valued duplicates.
2. **WHEN** the encoder is given the same `SharedState` instance twice, **THE SYSTEM SHALL** produce
   byte arrays that are equal, across repeated calls and across separate `CanonicalEncoder`
   instances.
3. **WHEN** two encoder instances encode the same state, **THE SYSTEM SHALL** produce equal bytes —
   the encoder SHALL hold no per-instance state that affects its output.
4. **THE SYSTEM SHALL** resolve `equals(SharedState, SharedState)` by explicit decision recorded in
   this spec: either removed, or retained with a test that pins its contract. It SHALL NOT remain a
   public method with no caller and no test.
5. **THE SYSTEM SHALL** reach a documented verdict on whether removing `out.flush()` changes any
   observable behaviour, and that verdict SHALL be either a passing test or a recorded suppression —
   never silence.
6. **THE SYSTEM SHALL** ship a falsification check proving R1's test can fail: a deliberately
   lossy encoder SHALL be rejected by R1's test.

## Acceptance Criteria

- [x] A test collects the reachable search positions by walking every corpus program and asserts all
      store keys are pairwise distinct (R1). **Measured: 160 positions, 160 distinct keys** [verified
      2026-10-01]. The `≥ 100` floor is **deferred, not met** — see "R1's floor is measured".
- [x] A test asserts two encoder instances produce equal bytes for the same state (R3).
- [x] A test asserts repeated encoding of the same state is equal (R2).
- [x] `equals` is **deleted** (Branch A, Sam 2026-10-01). Repo-wide grep returns zero references and
      the five L24 mutants are gone from `./gradlew pitest` — `NO_COVERAGE` fell 14 → 9 (R4).
- [x] The flush verdict is recorded in §Current State as `[verified]` with the reasoning: deleting
      `out.flush()` leaves the whole suite green, so the mutant is **equivalent** and receives a
      recorded suppression rather than a contrived test. The mechanical PIT filter is deferred to
      12.06, which owns the denominator accounting (R5).
- [x] The R6 falsification check was executed, observed to fail, and reverted; the suite is green after
      the revert (R6). **Observed:** with a constant one-byte encoder, R1 collapsed 128 positions onto
      49 keys and R5 reported 1 byte instead of 10. **R2 and R3 still passed** — determinism and
      injectivity are different properties, which is precisely why R1 exists separately.
- [x] `./gradlew clean test javadoc` passes and Javadoc has no errors (R1–R6).
- [x] After the change, `./gradlew pitest` reports `CanonicalEncoder` coverage strictly greater than
      50.0%: **6/7 = 85.7%** [verified 2026-10-01]. Note the denominator moved from 12 to 7 — the
      class percentage is not comparable across the deletion without reading both totals.
- [x] **The new global mutant total is recorded.** `275 → 270`, `KILLED` 222 (unchanged), `SURVIVED`
      39 (unchanged), `NO_COVERAGE` 14 → 9. Written into 12.06's §Derivation Record by this spec,
      which is its single writer (R3).

## Design

### R1 — injectivity over the reachable state space

The test must draw states from a *large* set, not hand-pick two. Hand-picking cannot distinguish
injectivity from coincidence.

```java
// Collect states by exploring, not by authoring. Hand-authored states can be chosen to collide,
// which is the exact failure being tested for.
//
// "Distinct" is by VALUE. Two equal-valued SharedState instances are one state, not two, and a
// canonicalising encoder is *required* to map them to the same bytes. Collecting instances into a
// LinkedHashSet would keep both and make injectivity unsatisfiable.
Map<SharedState, String> byValue = new LinkedHashMap<>();   // value-equality keying
// walk a corpus program exhaustively under DFS, encoding via a CHARSET-SAFE content key:
//   byValue.putIfAbsent(config.state(), Base64.getEncoder().encodeToString(encoder.encode(config.state())))
assertEquals(byValue.size(), new HashSet<>(byValue.values()).size(),
    "encoder must be injective over the reachable search-state space");
```

**The encoding key must be a content key, never the `byte[]` itself.** `byte[]` uses identity
`equals`/`hashCode`, so `new HashSet<>(byteArrays)` reports N distinct objects for N array references
and can *never* detect a collision — the assertion would pass unconditionally, which is worse than
having no test. Use base64, a `List<Byte>`, or `Arrays.equals`-based comparison.

Use `LinkedHashMap`/`LinkedHashSet` (insertion-ordered) rather than a hash-ordered collection, so a
failure is reproducible rather than dependent on iteration order. Base64 rather than
`new String(byte[])` with the platform charset, for the same reason: the default charset can map
distinct byte arrays to equal `String`s and hide the very collision the test exists to find.

**Deduplication MUST use state value semantics — `SharedState.equals`/`hashCode` — never the encoded
bytes.** Deduplicating by encoded output and then asserting the encodings are distinct is circular:
it passes by construction, whatever the encoder does, including for a constant encoder.

Using `SharedState` value equality is sound here, and verified rather than assumed: all six
implementations (`DynamicState`, `DeadlockState`, `PairState`, `DclState`, `CounterState`,
`PetersonState`) override `equals` and `hashCode` [verified].

**R1's property is NOT the documented `SharedState` guarantee, and the distinction matters.** An early
draft of this spec argued that R1 tests "a documented interface guarantee" because `SharedState`'s
Javadoc said *"Two states that are equal must encode to identical bytes."* **That was wrong**, and the
error is worth preserving because it is the natural misreading. The Javadoc guarantees
*equal → identical*; the property R1 needs is the **converse**, *distinct → distinct*. Omitting a field
from an encoding makes it **coarser**, which makes identical states encode identically *more* reliably
— so the documented direction is structurally incapable of detecting the defect that 12.07 found.
Spec 12.07 corrected the Javadoc to state both directions; R1 tests the one it never mentioned.

If a future `SharedState` implementation omits value equality, that is a defect in its own right and
SHALL be reported; it SHALL NOT be worked around by switching the dedup key.

Which program to walk: pick the corpus program with the largest reachable state space, since a
2-thread program with few steps may not reach 100 distinct states. Spec 12.05 will add a
higher-thread program precisely to widen this space; until then, assert against whatever count the
chosen program reaches and require that count to be ≥ 100 (R1's floor). If no corpus program reaches
100 states today, that is itself a finding to record, not a reason to lower the floor silently.

#### R1's floor is measured, and it is unattainable today — the finding the paragraph above predicted

**[verified, 2026-10-01]** Walking every corpus program's reachable configuration space yields **43**
distinct states in total, so the ≥ 100 floor cannot be met by any single program: the largest is
`broken-peterson` / `broken-peterson-v2` at 15.

| program | distinct states |
|---|---|
| `peterson` | 14 |
| `broken-peterson` | 15 |
| `broken-peterson-v2` | 15 |
| `deadlock` | 5 |
| `double-checked-locking` | 8 |
| `lost-update` | 6 |
| `torn-counter` | 8 |
| **total** | **43** |

This is the "no corpus program reaches 100 states today" case, recorded rather than silently absorbed.
The consequence for implementation:

- **R1's test SHALL NOT assert the ≥ 100 floor until 12.05 lands.** The floor is explicitly contingent
  on 12.05's higher-thread program, so asserting it now asserts a precondition that has not been met —
  and, as implemented and observed, the test simply fails with `got 43`. The floor is a **deferred
  assertion**, not a deleted one.
- **R1's test SHALL assert injectivity over the 43 states that exist**, since that is what makes the
  test falsifiable now, and SHALL record the measured count so a drop is visible.
- **The ≥ 100 assertion is promoted in 12.05**, when the program that justifies it exists. Until then
  R1 is under-strengthened by design, and that shall be stated in the PR body rather than presented
  as a satisfied floor.
- Note this is the same dependency that made R1 impossible to land first: **12.05 widens the corpus
  that 12.01 measures, but 12.01's contract underpins 12.05's new store tests.** The cycle is real.
  It is broken by landing 12.01 with injectivity asserted over the reachable 43, and the count as a
  recorded number, then promoting the floor when 12.05 supplies the states.

**R1's scope was also narrowed by Spec 12.07.** R1 now asserts injectivity of the **full
`Configuration` key** — `encode(state) + "|" + programCounters.toString()`, the shape
`HashingStateStore.encode` actually builds (`L107–111`) — rather than injectivity of the state
encoding alone. State-level injectivity is strictly stronger than either store requires and, as 12.07
demonstrated, need not hold for the store to be correct; 12.07 owns the state-level property. See
Spec 12.07 §R6.

### R6 — the falsification check

A property test that cannot fail is decoration. Before landing R1, break the encoder deliberately.
The break must guarantee collisions in the sampled set, so truncate to a **constant** rather than to
a per-state byte:

```java
// TEMPORARY: emit one fixed byte for every state. Every state now encodes identically, so
// R1's test MUST go red. (Truncating to each state's own first byte would NOT reliably
// collide — with >=100 states over 256 byte values it may produce no collision at all,
// which is exactly the case where the falsification check silently proves nothing.)
out.writeByte(0);
```

Confirm `CanonicalEncoderContractTest` fails, then revert. Record the reverted SHA and the observed
failure in the PR body. This is the same procedure Spec 12.05 uses, and the reason the encoder's
mutant counts cannot be trusted until a falsification check exists.

### R4 — the `equals` fork

Present both branches to Sam; do not pick unilaterally. `AGENTS.md` §2 requires stopping before
deleting existing code.

**Branch A — delete.** Removes 5 mutants, shrinks the surface, and leaves no untested public method
on the correctness foundation. Cost: if a future caller needs structural state equality, they
re-add it.

**Branch B — keep and test.** Justified only if the method is intended API for library callers.
Cost: it stays public, it stays a `Serializable` class member, and it now needs a permanent test
that pins `equals` semantics separately from `hashCode` consistency — including the `equals`/
`hashCode` contract, which nothing currently asserts.

**Recommendation: Branch A.** The method has no caller, this is a teaching-oriented PoC rather than a
published library, and an untested public method upstream of both stores' correctness is the worst
of the three outcomes available. Record the decision in this spec either way.

## Tests

**File:** `src/test/java/dev/samhb/interleave/state/CanonicalEncoderContractTest.java`

- `encode_isInjectiveOverReachableStateSpace` (R1) — collects states by exploring, asserts
  `states.size() == encoded.size()`, requires ≥ 100 states
- `encode_isDeterministicAcrossCalls` (R2)
- `encode_isDeterministicAcrossInstances` (R3) — two `CanonicalEncoder`s, same state, equal bytes
- `encode_distinguishesSingleFieldDifference` (R1) — a falsifiable, human-readable complement: two
  states differing in exactly one field encode differently
- `encode_flushRemovalIsUnobservable` (R5) — pins the flush verdict as a test *or* is replaced by a
  recorded suppression. **Byte count when written [verified 2026-10-01]:** `PetersonState` encodes
  **four** fields — `flag[0]` and `flag[1]` (1 byte each) plus `turn` and `inCriticalSection` (4 bytes
  each) = **10 bytes**. An early draft asserted 6, having miscounted two booleans and one int; the
  correct figure is 10, and an implementation that wrote 6 would have silently dropped
  `inCriticalSection`. Any such assertion SHALL be derived from the field list, not from a count.
- `equals_contractIsPinned` (R4) — only if Branch B is chosen
- `encoder_hasNoUnusedPublicMethods` (R4) — reflection over public methods asserting each has a
  caller or a test. Optional; include only if Branch B is chosen, since Branch A makes it vacuous.

**Falsification check (R6, not a committed test):** truncate the encoding; confirm the injectivity
test goes red; revert.

## Constraints

- **Dependencies:** none. This is the root of the set.
- **Backward compatibility:** `encode` and `hashCode` signatures SHALL NOT change — both stores call
  them. Removing `equals` is an API change to a `public` class in a `public` package, so R4 SHALL
  confirm the consumer surface before deleting rather than inferring it from a repo-wide grep:

  | question | finding |
  |---|---|
  | in-repo callers | **zero**, production and test, verified by repo-wide grep |
  | published artifact | **yes** — `maven-publish` declares `dev.samhb.interleave:interleave` |
  | version | `1.0-SNAPSHOT` |
  | repository | **`mavenLocal()` only** — no remote repository is configured, so no artifact has left this machine |
  | external consumers | **none reachable.** There is no released version and no remote repo, so no published surface can be depended on |

  The grep alone does not establish that; the publishing configuration does. The conclusion holds
  *because* the artifact has never been published remotely, and R4 SHALL re-check that before acting
  — if a remote repository is added, or a version is released, the deletion becomes a breaking
  change and stops being a local decision. Deletion also requires Sam's sign-off (R4).
- **Javadoc:** the `Javadoc` Gradle task runs over `src/main/java` only and is gated on correctness,
  not coverage. Whatever shape `equals` takes after R4, any surviving public method needs a docstring
  describing its contract (R4).

## Commands

```bash
./gradlew test --tests "*CanonicalEncoder*"
./gradlew pitest          # then read the CanonicalEncoder section of the report
./gradlew clean test javadoc
```

## Map

- `src/main/java/dev/samhb/interleave/state/CanonicalEncoder.java` — the whole target (30 lines)
- `src/main/java/dev/samhb/interleave/state/HashingStateStore.java` — `encode`/`hashCode` caller (Spec 12.02)
- `src/main/java/dev/samhb/interleave/state/BitstateStore.java` — `hashCode` caller (Spec 12.03)
- `src/test/java/dev/samhb/interleave/state/StateHashingTest.java` — the existing single-pair smoke test
- `build/reports/pitest/mutations.xml` — the inventory this spec is measured against
