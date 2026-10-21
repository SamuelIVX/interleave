# Spec: Exact-store canonical byte-domain hardening

## Objective

Recheck the nine inherited `HashingStateStore` survivors under roadmap 9-H2 without pinning hash
values. Protect the public exact-store contract across every one-byte canonical state and correct
the historical equivalence argument where an arithmetic mutant can fail to return a hash.

## Scope

- Package: `dev.samhb.interleave.state` test coverage and mutation dispositions.
- Modifies: `HashingStateStoreLifecycleTest`, this spec, the future-work roadmap and the historical
  exact-store/deferred-debt adjudication notes.
- Off-limits: production stores/encoder, dependencies, public APIs, mutation exclusions and gates.

## Non-Goals

Changing hash selection or asserting private hash values; approximate-store selectivity;
encoder-flush mutation closure; broader 9-V1 work. Mutation score alone is not an acceptance target.

## Invariants

- Any valid deterministic canonical state is accepted by both exact-store visitation APIs.
- Unequal canonical states remain distinct even if their prefilter hashes collide.
- Equal canonical states agree on visitation regardless of object identity.
- Bounded visitation includes last-thread identity and uses minimum recorded preemption cost.
- A different total deterministic prefilter hash may affect cost but not exact membership.

## Requirements

1. WHEN any signed-byte state is queried or marked through the ordinary API, THE SYSTEM SHALL
   accept it without arithmetic errors, report unseen before marking and visited after marking.
2. WHEN any signed-byte state is queried or marked through the bounded API, THE SYSTEM SHALL
   accept it without arithmetic errors and preserve cost dominance and last-thread separation.
3. WHEN classifying a changed prefilter, THE SYSTEM SHALL distinguish total replacement functions
   from mutations that can throw before the exact check.

## Acceptance Criteria

- Two regressions cover all 256 signed-byte states with literal domain bounds and public APIs only.
- No assertion names a hash value, inspects a private hash or changes the existing R7 convention.
- Fresh baseline and final scoped mutation identities are compared; no baseline kill is lost.
- Every remaining exact-store survivor has an explicit, appropriately limited disposition.
- Clean build, full tests, Javadoc, default mutation ratchet and visualizer normalization pass.
- CodeRabbit CLI completes review, with actionable findings verified and addressed.

## Design

Use an immutable nested byte-valued `SharedState` record with value equality and a canonical
single-byte encoding. Give each configuration one empty modeled thread, so its counter is a valid
initial zero. Iterate the complete byte domain separately for ordinary and bounded visitation.
Wrap each iteration in `assertDoesNotThrow` so arithmetic failures are explicit assertion failures.
An equal copied state must be visited; bounded queries below the minimum and at another last-thread
identity must remain unseen. Counts after the full domain must be 256.

The historical prefilter proof assumes the replacement hash always returns. `31 / stateHash` and
`31 / configurationHash` violate that premise if their divisor is zero. Single-byte payloads
include those boundaries; no unsupported state representation or hash-reflection seam is needed.

## Current State

- [verified] Baseline is merged PR #59 at `bcc4b4f`; production stores are unchanged by PRs #58–59.
- [verified] State hashing uses canonical signed bytes, and both exact-store APIs call the prefilter
  before checking canonical keys. JVM integer division by zero can interrupt that path.
- [verified] Fresh baseline: 651 passing tests, none skipped; scoped exact-store PIT measures
  43/52 assertion-confirmed kills, nine survivors, zero uncovered and zero timeout/error detections.

## Tests

- `ordinaryVisitationAcceptsEveryCanonicalByte` — R1, including equal copied-state lookup.
- `boundedVisitationAcceptsEveryCanonicalByte` — R2, including equal copied-state lookup, cost
  dominance and last-thread identity.
- Mutation ledger and corrected historical proof — R3.

## Constraints

- Dependencies: canonical configuration-key contract from Spec 13.09 and exact-store R7 from 12.02.
- Backward compatibility: production behavior, key representation and thresholds remain unchanged.
- Local Gradle commands always use `--max-workers=1 -PpitestThreads=1`.
- The seven other historical hash replacements may remain equivalent; do not manufacture assertions
  that pin bucket arithmetic to kill observationally equivalent mutations.

## Verification

Both domain regressions pass. Scoped PIT improves **43/52 → 45/52 assertion-confirmed kills**,
with all 52 semantic identities unchanged and all 43 original kills retained. Both division mutants
are killed by their corresponding named domain regression. Seven survivors remain, with zero
uncovered or timeout/error detections. No mutation exclusion or threshold changes are made.

### Semantic mutation ledger

All rows belong to `dev.samhb.interleave.state.HashingStateStore`. `hashCode` has descriptor
`(Ldev/samhb/interleave/core/Configuration;)I`; `preemptionHash` has descriptor
`(Ldev/samhb/interleave/core/Configuration;I)I`. Index/block pairs identify bytecode mutations
independently of source line numbers. Every row was SURVIVED in the fresh baseline.

| Method | Operator | Index / block | Change | Final status / disposition |
|---|---|---|---|---|
| hashCode | MathMutator | 13 / 2 | Multiplication → division | KILLED by ordinary byte-domain regression; a valid state can make the divisor zero. |
| hashCode | MathMutator | 17 / 4 | Addition → subtraction | SURVIVED; total deterministic replacement, exact confirmation remains authoritative. |
| hashCode | NonVoidMethodCallMutator | 7 / 1 | Remove CanonicalEncoder.hashCode | SURVIVED; state contribution becomes zero, exact canonical keys remain authoritative. |
| hashCode | NonVoidMethodCallMutator | 16 / 3 | Remove List.hashCode | SURVIVED; counter contribution becomes zero, exact canonical keys remain authoritative. |
| hashCode | PrimitiveReturnsMutator | 22 / 4 | Return zero | SURVIVED; all prefilter buckets may collide, exact canonical keys remain authoritative. |
| preemptionHash | MathMutator | 7 / 1 | Multiplication → division | KILLED by bounded byte-domain regression; a valid configuration can make the divisor zero. |
| preemptionHash | MathMutator | 9 / 1 | Addition → subtraction | SURVIVED; total deterministic replacement, canonical keys still include last-thread identity. |
| preemptionHash | NonVoidMethodCallMutator | 6 / 0 | Remove HashingStateStore.hashCode | SURVIVED; only the last-thread contribution remains in the prefilter; exact keys retain the configuration. |
| preemptionHash | PrimitiveReturnsMutator | 10 / 1 | Return zero | SURVIVED; all bounded buckets may collide, canonical keys and minimum costs remain authoritative. |

Operator names above are suffixes of `org.pitest.mutationtest.engine.gregor.mutators`, except
`PrimitiveReturnsMutator`, which is in its `returns` subpackage.

For the seven survivors, equal canonical keys imply equal inputs to each replacement hash; a marked
key therefore cannot be lost by the prefilter. A prefilter miss cannot hide a marked key, and a hit
still runs the exact canonical-key check (and bounded minimum-cost check). Counts and lifecycle
operations read exact collections, not bucket values. This is a membership-equivalence argument
for deterministic, successfully encodable valid states, not a claim of identical performance or
behavior for failing callbacks. Division replacements are excluded because they are not total.

Final post-feedback clean build and strict Javadoc pass with **653 tests**, none failed or skipped.
The default mutation ratchet passes **280/291 assertion-confirmed kills**, with eleven survivors,
zero uncovered and zero timeout/error detections. The exact-store portion remains **45/52** with
the same semantic identities, seven total-function survivors and no lost baseline kills. The
94% floor, operators and all ratchet rules remain unchanged. All **18 visualizer normalization
checks** pass.

CodeRabbit's first review completed with one minor ledger-format finding and one trivial
bounded-copy coverage finding; both were verified and addressed. The follow-up CLI review
completed across all five files with **zero findings** (`review_completed`, not skipped).
Independent reader review verified the byte-domain boundaries, the public assertions and the
scope of the equivalence proof. Only measured verification prose was finalized after follow-up
review; no production or test code changed afterward.

Browser integration is not rerun locally because production output and metadata propagation are unchanged.
