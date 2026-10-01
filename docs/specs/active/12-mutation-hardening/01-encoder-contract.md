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

1. **WHEN** the encoder is given N pairwise-distinct `SharedState` instances from the corpus state
   space, **THE SYSTEM SHALL** produce N pairwise-distinct byte arrays.
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

- [ ] A test collects ≥ 100 distinct `SharedState` instances by walking a corpus program's
      reachable configuration space, and asserts all encodings are pairwise distinct (R1).
- [ ] A test asserts two encoder instances produce equal bytes for the same state (R3).
- [ ] A test asserts repeated encoding of the same state is equal (R2).
- [ ] `equals` is either deleted — in which case repo-wide grep returns zero references and the five
      L24 mutants disappear from the next `./gradlew pitest` run — or retained and covered by a test
      pinning its contract (R4).
- [ ] The flush verdict is recorded in this spec's §Current State as `[verified]` with the
      reasoning, and the L16 mutant is either killed by a test or suppressed with that reason (R5).
- [ ] The falsification check in R6 is executed once, observed to fail, and reverted. The revert is
      confirmed by a green suite (R6).
- [ ] `./gradlew clean test javadoc` passes and Javadoc has no errors for the changed file (R1–R6).
- [ ] After the change, `./gradlew pitest` reports `CanonicalEncoder` coverage strictly greater than
      50.0% (50.0% + 5/12 ≈ 91.7% if `equals` is deleted).

## Design

### R1 — injectivity over the reachable state space

The test must draw states from a *large* set, not hand-pick two. Hand-picking cannot distinguish
injectivity from coincidence.

```java
// Collect states by exploring, not by authoring. Hand-authored states can be chosen to collide,
// which is the exact failure being tested for.
Set<SharedState> states = new LinkedHashSet<>();   // LinkedHashSet: deterministic order
// walk a corpus program exhaustively under DFS, adding config.state() at each node
Set<String> encoded = new LinkedHashSet<>();
for (SharedState s : states) encoded.add(new String(encoder.encode(s), StandardCharsets.ISO_8859_1));
assertEquals(states.size(), encoded.size(), "encoder must be injective over the reachable state space");
```

Use `ISO_8859_1` (or base64) rather than `String(byte[])` with the platform charset: the default
charset can map distinct byte arrays to equal `String`s, which would hide exactly the collision the
test exists to find. Compare bytes directly with `Set<List<Byte>>` if that reads cleaner.

Which program to walk: pick the corpus program with the largest reachable state space, since a
2-thread program with few steps may not reach 100 distinct states. Spec 12.05 will add a
higher-thread program precisely to widen this space; until then, assert against whatever count the
chosen program reaches and require that count to be ≥ 100 (R1's floor). If no corpus program reaches
100 states today, that is itself a finding to record, not a reason to lower the floor silently.

### R6 — the falsification check

A property test that cannot fail is decoration. Before landing R1, break the encoder deliberately:

```java
// TEMPORARY: collapse the encoding to its first byte. R1's test MUST go red.
int[] truncated = { out.size() > 0 ? out.get(0) : 0 };
// ... write only that byte ...
```

Confirm `CanonicalEncoderContractTest` fails, then revert. Record the reverted SHA and the observed
failure in the PR body. This is the same procedure Spec 12.05 uses, and the reason the register's
mutant counts could not be trusted until Part B's falsification check existed.

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
  recorded suppression
- `equals_contractIsPinned` (R4) — only if Branch B is chosen
- `encoder_hasNoUnusedPublicMethods` (R4) — reflection over public methods asserting each has a
  caller or a test. Optional; include only if Branch B is chosen, since Branch A makes it vacuous.

**Falsification check (R6, not a committed test):** truncate the encoding; confirm the injectivity
test goes red; revert.

## Constraints

- **Dependencies:** none. This is the root of the set.
- **Backward compatibility:** `encode` and `hashCode` signatures SHALL NOT change — both stores call
  them. Removing `equals` is an API change to a `public` class in a `public` package; acceptable
  because there are no external consumers [verified], and it requires Sam's sign-off (R4).
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