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
- Improving hash selectivity (the `doubleHash` index arithmetic). Spec 12.02 §R4 adjudicates those
  mutants as equivalent-or-not; this spec does not re-litigate.
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

`L333`, `L343` and `L357` are hash-arithmetic and out of scope here; Spec 12.02 §R4 adjudicates
them. `L333`, `L343` and `L357` each sit on a line that **also** carries killed mutants, so a
line-grouped inventory that reports only the dominant status undercounts by three.

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

- [ ] `estimatedFalsePositiveRate()` is asserted `== 0.0` on a fresh store (R1).
- [ ] At least three literal closed-form cases pass within `1e-9` (R2), including at least one with
      `preemptionStatesMarked > 0` so the `n` sum at `L294` is exercised.
- [ ] A monotonicity test over an increasing `statesMarked` sequence passes (R3).
- [ ] **Falsification:** all **five** FPR mutants — `L298` (`MATH`), `L300` (`INVERT_NEGS` and
      `MATH`), and both at `L301` (`NON_VOID_METHOD_CALLS`, `PRIMITIVE_RETURNS`) — are individually
      injected in turn, and the corresponding assertion is observed to go red, then reverted. All
      five must be demonstrated; a test that kills only some of the formula mutants is not the test
      this spec asks for. The two accessor mutants (`L201`, `L210`) are demonstrated separately
      under R8.
- [ ] Boundary tests assert rejection at `size == 0` and acceptance at `size == 1`, and likewise for
      `numHashFunctions` (R5, R6).
- [ ] `maxPreemptions == 0` constructs successfully and `bitCount()`/`estimatedFalsePositiveRate()`
      behave (R7).
- [ ] `size()` and `numHashFunctions()` return the constructor arguments (R8).
- [ ] `./gradlew clean test javadoc` passes.
- [ ] `./gradlew pitest` shows `BitstateStore` above 86.6% and specifically L68, L71, L201, L210,
      L298, L300, L301 all `KILLED`.

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
- L298 `MATH` mutates `size * vectorsInUse()`. Use `maxPreemptions > 0` so `vectorsInUse()` is
  greater than 1 and the mutant's effect is multiplied rather than hidden.
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

- `estimatedFalsePositiveRate_emptyStore_isZero_notNaN` (R1)
- `estimatedFalsePositiveRate_matchesClosedForm_forKnownInputs` (R2, R4) — ≥ 3 literal cases, at
  least one with `maxPreemptions > 0`
- `estimatedFalsePositiveRate_includesPreemptionStatesInCount` (R2) — the `L294` sum
- `estimatedFalsePositiveRate_isMonotonicInStatesMarked` (R3)
- `constructor_rejectsNonPositiveSize` / `constructor_acceptsSizeOfOne` (R5)
- `constructor_rejectsNonPositiveNumHashFunctions` / `constructor_acceptsOneHashFunction` (R6)
- `constructor_rejectsNegativeMaxPreemptions` / `constructor_acceptsZeroMaxPreemptions` (R7)
- `constructor_zeroMaxPreemptions_allocatesSinglePreemptionBitSet` (R7) — asserts via observable
  behaviour (`bitCount()`, FPR), not by reflecting into the private field
- `size_returnsConstructedCapacity` (R8)
- `numHashFunctions_returnsConstructedK` (R8)

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