# Spec 12.03 — `BitstateStore` Diagnostic Correctness

## TL;DR

`BitstateStore` reports `estimatedFalsePositiveRate()` — the number a user reads to decide whether
`--store bitstate` is a trustworthy trade for their program. It is a Bloom-filter FPR estimate, and
**five mutants survive inside that one method**. The existing PR #26 metric tests were specified to
"pin density and FPR" but demonstrably do not constrain the formula's arithmetic. Separately, the
constructor's capacity-validation boundaries have no test at the exact boundary, and two trivial
accessors are never called by anything.

This is the highest value-per-line item in the set: user-facing, reachable by ordinary test code,
needs no new corpus program, and requires no production change.

## Objective

Make the bitstate diagnostics *correct and pinned*, so a number a user acts on is a number the code
actually computes — and make the constructor's rejection boundaries exact rather than incidental.

## Scope

- **Package:** `interleave` / `src/main/java/dev/samhb/interleave/state`
- **Modifies:** adds `src/test/java/dev/samhb/interleave/state/BitstateStoreDiagnosticsTest.java`
- **Off-limits:** `state/BitstateStore.java` internals unless a defect is found (then a separate
  scoped fix); `state/HashingStateStore.java` — Spec 12.02; `state/CanonicalEncoder.java` —
  Spec 12.01. `report/BenchmarkHarness.java` consumes these accessors — Spec 12.04 owns that surface.

## Non-Goals

- Changing the FPR formula. It is the standard `(1 - e^(-kn/m))^k`. The bug surface is that we do not
  test it, not that it is wrong [verified by reading the body].
- Improving hash selectivity (the `doubleHash` index arithmetic). Out of scope here, but note the
  reason differs from 12.02 and the verdict does **not** transfer: 12.02's survivors were *equivalent*,
  where these four are not. See §R6 adjudication.
- Changing `--store bitstate` defaults or the `APPROXIMATE_PASS` labelling, which Spec 11.04 settled.

## Current State

All claims [verified] by reading `state/BitstateStore.java` (360 lines) and the mutation report.

**The FPR method, verbatim** (`L293–302`):

```java
public double estimatedFalsePositiveRate() {
    long n = (long) statesMarked + preemptionStatesMarked;
    if (n == 0) {
        return 0.0;
    }
    double m = (double) size * vectorsInUse();
    double k = numHashFunctions;
    double prob = 1.0 - Math.exp(-k * n / m);
    return Math.pow(prob, k);
}
```

**Constructor validation** (`L67–76`), the three guards and their exact boundaries:

| guard | line | rejects | accepts |
|---|---|---|---|
| `size <= 0` | `L68` | `size == 0`, all negatives | `size == 1` |
| `numHashFunctions <= 0` | `L71` | `k == 0`, all negatives | `k == 1` |
| `maxPreemptions < 0` | `L74` | negatives only | **`maxPreemptions == 0`** |

Note the asymmetry in the third guard: `maxPreemptions == 0` is legal (a bound of zero preemptions),
and `preemptionBitsets` is allocated as `new BitSet[maxPreemptions + 1]`, so a zero bound must
allocate a length-1 array. That boundary is the kind of off-by-one an untested validation check hides.

**Accessors:** `size()` at `L200–202`, `numHashFunctions()` at `L209–211`. Neither is called by any
test; `numHashFunctions()` is not called by production code either [verified by grep].

**Callers of the diagnostics:** `BenchmarkHarness` reads `estimatedFalsePositiveRate()`,
`bitCount()`, and `bitDensity()` [verified].

**Mutation inventory — 97 mutants, 84 killed (86.6%), 13 not killed:**

| line | symbol | mutator | status |
|---|---|---|---|
| L68 | `<init>` — `size <= 0` | `CONDITIONALS_BOUNDARY` | SURVIVED |
| L71 | `<init>` — `numHashFunctions <= 0` | `CONDITIONALS_BOUNDARY` | SURVIVED |
| L201 | `size()` | `PRIMITIVE_RETURNS` | **NO_COVERAGE** |
| L210 | `numHashFunctions()` | `PRIMITIVE_RETURNS` | **NO_COVERAGE** |
| L298 | `estimatedFalsePositiveRate` — `size * vectorsInUse()` | `MATH` | SURVIVED |
| L300 | `estimatedFalsePositiveRate` | `INVERT_NEGS` | SURVIVED |
| L300 | `estimatedFalsePositiveRate` | `MATH` | SURVIVED |
| L301 | `estimatedFalsePositiveRate` — `Math.pow` | `NON_VOID_METHOD_CALLS`, `PRIMITIVE_RETURNS` | SURVIVED ×2 |
| L333 | `preemptionHashIndices` | `MATH` | SURVIVED |
| L343 | `doubleHash` — index arithmetic | `MATH` ×2 | SURVIVED ×2 |
| L357 | `hashCode` | `MATH` | SURVIVED |

Totals: 11 `SURVIVED` + 2 `NO_COVERAGE` = 13. **Five** of the survivors are inside
`estimatedFalsePositiveRate` (`L298`, `L300` ×2, `L301` ×2) — the highest concentration of
user-facing diagnostics in the set.

`L333`, `L343` and `L357` are hash-arithmetic and out of scope here. `L333`, `L343` and `L357` each
sit on a line that **also** carries killed mutants, so a line-grouped inventory that reports only the
dominant status undercounts by three. Measured after implementation: these four are the *only*
survivors, and they are **not** equivalent — `BitstateStore` has no exact confirmation layer, so a
different hash genuinely changes answers. See §R6 adjudication.

## Invariants

- **`estimatedFalsePositiveRate()` returns the Bloom-filter FPR for the store's current state.**
  For `m` bits, `k` hash functions, and `n` inserted keys, it SHALL equal
  `(1 - e^(-kn/m))^k` within floating-point tolerance.
- **An empty store reports `0.0`,** not `NaN`. The `n == 0` early return exists to avoid `0/0` and
  must survive; a mutation that removes it produces `NaN`, which is worse than a wrong number because
  it silently propagates through formatting.
- **The reported FPR is monotonically non-decreasing in `n`** for fixed `m, k`. A user adding states
  must never see the estimate fall.
- **Constructor guards reject exactly the invalid domain and nothing else.** Each documented bound
  SHALL be rejected at the boundary and accepted one step inside it.
- **`maxPreemptions == 0` is legal** and SHALL allocate `preemptionBitsets` of length 1.
- **Accessors return the constructed values,** not derived or recomputed ones. `size()` is the bit
  capacity; `numHashFunctions()` is `k`. They are the exact quantities the FPR formula consumes, so
  pinning them pins the formula's inputs.

## Requirements

1. **WHEN** `estimatedFalsePositiveRate()` is called on a store with `statesMarked = 0` and
   `preemptionStatesMarked = 0`, **THE SYSTEM SHALL** return exactly `0.0`, never `NaN`.
2. **WHEN** a store has known `size`, `numHashFunctions`, and `statesMarked`, **THE SYSTEM SHALL**
   return `(1 - e^(-kn/m))^k` within `1e-9` for at least three hand-computed non-trivial inputs.
3. **WHEN** `statesMarked` increases on a fixed store, **THE SYSTEM SHALL** produce a
   non-decreasing sequence of `estimatedFalsePositiveRate()` values.
4. **THE SYSTEM SHALL** assert the closed form **directly against literal expected doubles**, not via
   a property that tolerates a wide band. The existing PR #26 tests could not distinguish the L298 /
   L300 / L301 mutants; a property test with a tolerance cannot either.
5. **WHEN** the constructor is given `size <= 0`, **THE SYSTEM SHALL** throw
   `IllegalArgumentException`; **SHALL** accept `size == 1`.
6. **WHEN** the constructor is given `numHashFunctions <= 0`, **THE SYSTEM SHALL** throw
   `IllegalArgumentException`; **SHALL** accept `numHashFunctions == 1`.
7. **WHEN** the constructor is given `maxPreemptions < 0`, **THE SYSTEM SHALL** throw
   `IllegalArgumentException`; **SHALL** accept `maxPreemptions == 0` and SHALL have allocated
   `preemptionBitsets` of length 1.
8. **THE SYSTEM SHALL** kill both accessor mutants by calling `size()` and `numHashFunctions()` and
   asserting they return the constructor arguments.

## Acceptance Criteria

- [x] `estimatedFalsePositiveRate()` is asserted `== 0.0` on a fresh store (R1).
      `estimatedFalsePositiveRate_emptyStore_isZeroNotNaN`.
- [x] At least three literal closed-form cases pass within `1e-9` (R2), including at least one with
      `preemptionStatesMarked > 0` so the `n` sum at `L294` is exercised, **and at least one that has
      marked at two or more distinct preemption levels so `vectorsInUse() > 1`** — the L298 mutant is
      invisible at `vectorsInUse() == 1` (R2, R4).
      Three cases (A/B/C) plus a fourth isolating the `L294` sum. Case B marks at preemption levels 0
      and 1 *and* plain, so `vectorsInUse() == 3`.
- [x] A monotonicity test over an increasing `statesMarked` sequence passes (R3).
      `estimatedFalsePositiveRate_isMonotonicInStatesMarked`, checked at all 400 steps rather than at
      the endpoints, so a non-monotone excursion that returns to its starting value is still caught.
- [x] **Falsification:** all **five** FPR mutants — `L298` (`MATH`), `L300` (`INVERT_NEGS` and
      `MATH`), and both at `L301` (`NON_VOID_METHOD_CALLS`, `PRIMITIVE_RETURNS`) — are individually
      injected in turn, and the corresponding assertion is observed to go red, then reverted. All
      five must be demonstrated; a test that kills only some of the formula mutants is not the test
      this spec asks for. The two accessor mutants (`L201`, `L210`) are demonstrated separately
      under R8.
      All demonstrated; see §Falsification results. `L300` was additionally exercised in a third
      form (`-k/n/m`) beyond the two PIT mutates it, since `INVERT_NEGS` and `MATH` do not obviously
      cover every rewrite of the exponent term.
- [x] Boundary tests assert rejection at `size == 0` and acceptance at `size == 1`, and likewise for
      `numHashFunctions` (R5, R6).
- [x] `maxPreemptions == 0` constructs successfully and `bitCount()`/`estimatedFalsePositiveRate()`
      behave (R7) — **and slot 0 is actually exercised** by marking and querying through the bounded
      APIs `markVisited(config, tid, 0)` / `isVisited(config, tid, 0)`. Constructing the store proves
      only that the array has one slot; it does not prove slot 0 is reachable or correct, and lazy
      allocation means the slot stays `null` until a mark lands in it (R7).
      `constructor_zeroMaxPreemptions_allocatesAndExercisesSlotZero` marks and queries level 0, and
      additionally asserts that level 1 is refused rather than wrapped.
- [x] `size()` and `numHashFunctions()` return the constructor arguments (R8).
      `size_returnsConstructedCapacity`, `numHashFunctions_returnsConstructedK`, and
      `accessors_areTheQuantitiesTheFormulaConsumes`.
- [x] `./gradlew clean test javadoc` passes.
- [x] `./gradlew pitest` shows `BitstateStore` above 86.6% and specifically L68, L71, L201, L210,
      L298, L300, L301 all `KILLED`. Measured 86.6% → **95.9%**; all seven lines fully killed.

## Measured Results

Scoped PIT, `dev.samhb.interleave.state.BitstateStore`, measured 2026-10-03 on `main` at `92b58b6`:

| | killed | survived | no coverage | score |
|---|---|---|---|---|
| before | 84 | 11 | 2 | 84/97 = 86.6% |
| **after** | **93** | **4** | **0** | **93/97 = 95.9%** |

Exactly the nine mutants this spec names were killed, and nothing else moved. Per line:

| line | mutants | before | after |
|---|---|---|---|
| `L68` `<init>` | 2 | 1 `SURVIVED` | both `KILLED` |
| `L71` `<init>` | 2 | 1 `SURVIVED` | both `KILLED` |
| `L201` `size()` | 1 | `NO_COVERAGE` | `KILLED` |
| `L210` `numHashFunctions()` | 1 | `NO_COVERAGE` | `KILLED` |
| `L298` FPR `m` | 2 | 1 `SURVIVED` | both `KILLED` |
| `L300` FPR exponent | 5 | 2 `SURVIVED` | all `KILLED` |
| `L301` FPR `Math.pow` | 2 | 2 `SURVIVED` | both `KILLED` |

`NO_COVERAGE` went to zero. That is the substantive part of the change: those two accessors were not
merely unasserted, they were never called by any test, so the FPR's two inputs had no coverage at all.

### Falsification results — executed, not asserted

Each mutant injected into the production source in turn, the suite run, the file restored. All
injections left `BitstateStore.java` byte-identical afterwards (`git diff` clean).

| injected | tests that went red |
|---|---|
| `L68` `size <= 0` → `size < 0` | `constructor_rejectsNonPositiveSize_atBoundary` |
| `L71` `k <= 0` → `k < 0` | `constructor_rejectsNonPositiveNumHashFunctions_atBoundary` |
| `L201` `return size` → `return 0` | 5, incl. `size_returnsConstructedCapacity` |
| `L210` `return numHashFunctions` → `return 0` | 3, incl. `numHashFunctions_returnsConstructedK` |
| `L298` `size * vectorsInUse()` → `size / vectorsInUse()` | **`estimatedFalsePositiveRate_matchesClosedForm_forKnownInputs` only** |
| `L300` `exp(-kn/m)` → `exp(+kn/m)` | 4, incl. the closed-form test |
| `L300` `-k * n / m` → `-k / n / m` | 4, incl. the closed-form test |
| `L300` `-k * n / m` → `-k * n * m` | 3, incl. the closed-form test |
| `L301` `Math.pow(prob, k)` → `0.0` | 4, incl. the closed-form test and the zero-bound test |

Two rows carry more weight than the rest.

**`L298` is killed by exactly one test.** That is the design working as intended rather than a
coincidence: the mutant flips `*` to `/` in `m`, and at `vectorsInUse() == 1` it evaluates `size / 1`,
which is numerically identical to `size * 1`. Cases A, C and D all sit at `vectorsInUse() == 1` and
would each have passed. This is arithmetic, not an observation — the separation against the mutant is
*exactly zero* for those three cases. Only case B, which drives the store to three vectors in use,
constrains the line at all.

**`L68` and `L71` are each killed by exactly one test**, and only their own. A guard tested solely with
negative inputs would pass against `size < 0`, because that mutant also rejects every negative. The
discriminating input is the boundary itself, `size == 0` and `k == 0`, which is why the tests are
named `_atBoundary` rather than testing rejection generically.

### The literals, and how they were obtained

Four expected values, each computed twice by independent routes that agree to within `1e-45`: a
hand-summed Taylor series for `e^-x` with the final power taken by binary exponentiation, and an
arbitrary-precision library call. Neither route calls `estimatedFalsePositiveRate()`, and none is
recomputed in the test body.

| case | size | k | n | vectors | `m` | `k*n/m` | expected | mutant separation |
|---|---|---|---|---|---|---|---|---|
| A | 1000 | 3 | 100 | 1 | 1000 | 0.3 | `0.017410586496326586` | `L298`: **0** |
| B | 1000 | 3 | 200 | **3** | 3000 | 0.2 | `0.0059562427789458935` | `L298`: 0.5756 |
| C | 1000 | 4 | 250 | 1 | 1000 | 1.0 | `0.15966130015118526` | `L298`: **0** |
| D | 1000 | 3 | 50 | 1 | 1000 | 0.15 | `0.0027025811482068833` | `L300`: 0.0069 |

The `1e-9` tolerance is loose only against double rounding — the true values are irrational, so exact
equality is unavailable. The nearest wrong answer across all nine mutants is ~0.0069 away, leaving
roughly six orders of magnitude of headroom.

Case A's literal also matches the value this spec stated before implementation, computed
independently. Two unrelated derivations agreeing is worth recording; a single derivation agreeing
with itself is not.

### R6 — adjudication of the 4 remaining survivors

`L333`, `L343` (×2) and `L357` survive. **They are not equivalent, and the 12.02 verdict does not
transfer.** That distinction matters, so it is stated rather than glossed:

- 12.02 adjudicated nine `HashingStateStore` survivors *equivalent* because a hash change provably
  cannot alter any answer — the prefilter decides only whether an exact check runs, and the exact
  check decides the answer.
- `BitstateStore` has **no exact confirmation layer at all**. `grep` finds no `contains` and no
  `encode` outside the hashing itself: the bitset *is* the store's answer. A different hash
  genuinely produces different answers, because it produces different collisions.

So these four are real, reachable behaviour changes. They survive because **no test pins which
configurations must not collide** — and pinning that requires asserting on hash structure, which R7
forbids. R7 (inherited from 12.02, and correct there) says no assertion may reference a hash value;
the only way to observe `L343` is to construct a pair that collides under the mutant but not the
original, which is exactly a hash-level claim. **R7 and these four mutants are in direct tension,
and R7 wins by design.**

That tension is worth surfacing rather than burying, because it is a genuine constraint on the
remaining work: closing them means either permitting one narrow, deliberately-marked hash-structure
assertion, or accepting them as a documented floor. Left `SURVIVED` deliberately so 12.06's ratchet
records 4 as the known floor for this class rather than losing them from the denominator.

## Design

### R2/R4 — closed-form assertions, not properties

The reason PR #26's tests failed to pin this is worth recording, because it is the general shape of
the mistake:

> `estimatedFalsePositiveRate()` is user-facing diagnostic output. A wrong FPR misleads a user
> choosing `--store bitstate`, which is the decision this number exists to inform.

A property test that says "the FPR is between 0 and 1" passes for every mutant in this method. A
property test that says "the FPR is plausible for this load factor" also passes, because all five
mutants produce plausible-looking numbers — they are wrong by a factor or a sign, not by an order of
magnitude. Only a literal expected value distinguishes them.

So the test computes the answer by hand, offline, and pins it:

```java
// Chosen so that k*n/m is a clean ratio and the result is far from any mutant's output.
// size=1000, k=3, n=100, vectorsInUse()==1  =>  m=1000, k*n/m = 0.3
//   1 - e^-0.3   = 0.2591817793182821
//   0.25918...^3 = 0.017410586496326586
assertEquals(0.0174105865, store.estimatedFalsePositiveRate(), 1e-9);
```

The literal must be computed and checked by hand or an independent tool, **not** by calling the
method under test. Computing the expected value with `Math.pow(1 - Math.exp(...))` inside the test
reproduces the implementation and validates nothing — a classic tautological assertion.

Pick inputs where the five mutants diverge *most*:
- L298 `MATH` mutates `size * vectorsInUse()`. **The store must be driven to at least two vectors in
  use before this matters, and `maxPreemptions > 0` does not do that.** `preemptionBitsets` is
  allocated **lazily** — the constructor only does `new BitSet[maxPreemptions + 1]`, leaving every
  slot `null` — and `vectorsInUse()` counts only non-null entries plus the main bitset
  [verified, `BitstateStore.java:82` and `:267`]. So a store built with `maxPreemptions = 3` that
  never marks a preemption still reports `vectorsInUse() == 1`, and the mutant's effect is invisible.
  The requirement is therefore: **mark at two or more distinct preemption levels** via
  `markVisited(config, tid, p)`, so `allocatedPreemptionVectors()` is genuinely ≥ 2 and the
  multiplier is exercised rather than hidden.
- L300 `INVERT_NEGS` flips the sign of the exponent term, which for `n > 0` produces a *negative*
  intermediate `prob` and a wild final value — easy to distinguish, provided a positive `n` case
  exists.
- L301 `NON_VOID_METHOD_CALLS` replaces `Math.pow` with a default (0.0); `PRIMITIVE_RETURNS` replaces
  the double with a primitive default. Both are far from a real FPR.

### R5–R7 — boundaries, not just rejection

Untested boundary validation is how an off-by-one reaches production. Each guard needs both sides:
rejection *at* the boundary, acceptance *one step inside*. For `maxPreemptions`, the interesting case
is the one that is **legal but near the guard's edge** — `0`. It is easy to write
`assertThrows(..., () -> new BitstateStore(1000, 4, -1))` and call the boundary covered. It is not;
`0` is the boundary that must be *accepted*, and it is the one that exercises the `maxPreemptions + 1`
allocation.

## Tests

**File:** `src/test/java/dev/samhb/interleave/state/BitstateStoreDiagnosticsTest.java`

Thirteen tests. The names below are the names that exist; where implementation renamed a planned
name, the old name is kept struck through in the note so the spec still names what it asked for.

- `estimatedFalsePositiveRate_emptyStore_isZeroNotNaN` (R1)
- `estimatedFalsePositiveRate_matchesClosedForm_forKnownInputs` (R2, R4) — three literal cases A/B/C;
  B marks at two preemption levels *and* plain, so `vectorsInUse() == 3`
- `estimatedFalsePositiveRate_includesPreemptionStatesInCount` (R2) — the `L294` sum, isolated by
  making `statesMarked == 0`
- `estimatedFalsePositiveRate_isMonotonicInStatesMarked` (R3)
- `constructor_rejectsNonPositiveSize_atBoundary` / `constructor_acceptsSizeOfOne` (R5) — planned as
  `constructor_rejectsNonPositiveSize`; renamed because the boundary is the whole point of the test
- `constructor_rejectsNonPositiveNumHashFunctions_atBoundary` / `constructor_acceptsOneHashFunction` (R6)
- `constructor_rejectsNegativeMaxPreemptions` / `constructor_zeroMaxPreemptions_allocatesAndExercisesSlotZero` (R7)
  — the planned pair `...rejectsNegativeMaxPreemptions` / `...acceptsZeroMaxPreemptions` collapsed into
  one test, since the acceptance case is only meaningful together with the slot-0 exercise. Asserts
  via observable behaviour (`bitCount()`, FPR), never by reflecting into `preemptionBitsets`; marks
  and queries level `0` through the bounded APIs so the slot is exercised, not merely sized, and
  asserts level `1` is refused rather than wrapped
- `size_returnsConstructedCapacity` (R8)
- `numHashFunctions_returnsConstructedK` (R8)
- `accessors_areTheQuantitiesTheFormulaConsumes` (R8) — **beyond this spec's inventory.** Pins that
  `size()` and `numHashFunctions()` are the values the formula consumes by recomputing case A from the
  accessor readings alone. Without it, both accessors could return swapped or scaled values that every
  other test tolerated, since R8's two tests only compare each accessor to its own constructor argument.

**Falsification (not committed, five runs):** inject each of `L298`, `L300` (×2), and `L301` (×2) in
turn; confirm the matching assertion goes red; revert. All five demonstrated in the PR body, plus
the two accessor mutants under R8.

## Constraints

- **Dependencies:** Spec 12.01 (encoder injectivity feeds `hashCode`, which feeds the FPR's inputs).
  Spec 12.02 §R4 for the `doubleHash` verdict, which this spec cites rather than re-derives.
- **Backward compatibility:** none. Test-only, unless a defect surfaces. No signature changes; no
  formula change.
- **Do not touch the PR #26 tests.** They are not wrong — they are insufficient. Add alongside them.
  If a new test contradicts an existing one, that is a finding about the existing one and needs its
  own analysis, not a deletion.

## Commands

```bash
./gradlew test --tests "*BitstateStore*"
./gradlew test --tests "*StateStore*"
./gradlew pitest
./gradlew clean test javadoc
```

## Map

- `src/main/java/dev/samhb/interleave/state/BitstateStore.java` — `estimatedFalsePositiveRate`, three constructors, `size`, `numHashFunctions`
- `src/main/java/dev/samhb/interleave/report/BenchmarkHarness.java` — the diagnostic consumer (Spec 12.04)
- `src/test/java/dev/samhb/interleave/state/StateStorePreemptionTest.java` — existing bitstate tests
- `src/test/java/dev/samhb/interleave/report/BenchmarkHarnessCBTest.java` — `bitstateMetrics_populatedForContextBoundedRows`,
  the PR #26 test that claims to pin FPR and demonstrably does not reach the formula's arithmetic
