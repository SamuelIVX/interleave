<!-- B9 spec-set index: approved scope, shared interfaces, ownership and independent oracles. -->
# B9 — Approximate symmetry storage contract

## Objective and status

**Planned, not implemented.** This set turns the approved B9 design into three sequential,
complete feature PRs. Inspected baseline: `1db78706314bf6845e9076d98986b91dbbe94570`, merged
PR #130. B8 is complete. No B9 implementation or implementation measurements are claimed here.

Sam selected all four strategies, Java library plus existing CLI/reports/viewer, approximation of
the complete DPOR context, and one shared Bloom budget across CBS costs. Approximation may omit
reachable behavior; found observations must still come from actual concrete execution.

## Set structure and ownership

| Phase | Document | Owns | Release boundary |
|---|---|---|---|
| K1 / 89 | [Approximate DFS/static POR](../specs/active/89-approximate-symmetry-dfs-por.md) | Shared filter, checked-store/precision interfaces, run binding and key encoding; DFS/POR; initial consumers and compatibility | Explicit DFS/POR selections only for approximate symmetry |
| K2 / 90 | [Approximate CBS](../specs/active/90-approximate-symmetry-cbs.md) | Cost membership, iterative termination/isolation, CBS metrics and consumers | Adds CBS; DPOR and an all-strategy request still reject |
| K3 / 91 | [Approximate DPOR](../specs/active/91-approximate-symmetry-dpor.md) | Complete mapped sleep membership and final all-strategy consumers | Adds DPOR; strategy omission now supports all four |

Implement in that order, one complete feature PR per phase. Later specs may update shared call
sites only for their owned behavior. Do not publish an unused foundation in a separate PR.
The spec-authoring PR contains documents only, expressly requested by Sam. Implementations must
update their own spec, tests, measurements and support table in the same feature PR.

## Fixed terminology and decisions

- **Orbit key:** existing complete canonical shared values and whole owner frames, under current
  source/property certification. A hash is not an orbit key.
- **Product context:** orbit plus the scheduling information required by the selected strategy.
- **Approximate membership:** a positive lookup may be false; a marked key remains positive
  until clear. No deletion, eviction or exactness claim is introduced.
- **Budget:** `bits` logical Bloom positions per live checked approximate store, shared across
  its membership domains/costs. JVM allocation overhead, temporary encodings, source metadata,
  traversal, diagnostic configuration maps and retained traces are outside this budget.
- **Observation:** an actual concrete failure/deadlock/cutoff/resource interruption. Approximate
  absence is not exhaustive PASS; a discovered CBS bound is not a proven minimum.

Keep current eligibility, source caps, exact HASHED/ORDERED defaults, original-owner execution,
alias certification, witness authentication and B8 capture/replay behavior. No generic backend
injection, broad memory-management redesign, B10 eligibility expansion, new CLI flags or dependency.

## Shared interface and key contract

`Builder.symmetryBitstate(int bits, int hashFunctions)` selects the new owned storage without
enabling symmetry implicitly. It requires `symmetryReduction(true)` and rejects any explicit
`symmetryBackend`, store or factory selection, in either setter order. Repeated calls to the same
setter follow existing last-value-wins behavior. Bits/probes must be positive.

`CheckedSymmetryStore` is a sealed `StateStore` capability permitting only `SymmetryStateStore`
and `BitstateSymmetryStateStore`. Its checked operations are existing `requireRun(Program,
Invariant, Strategy)` and `sleepCovered(Configuration, Set<Map.Entry<Integer,Step>>)`. Existing
exact factory return types, backend accessor and convenience exact DFS behavior remain intact.
Share certification/canonical owner and action mapping in package-owned `SymmetryRunContext`;
do not duplicate its rules in the approximate implementation.

`ApproximateStateStore extends StateStore` exposes the existing metric/capacity signatures:
`double estimatedFalsePositiveRate()`, `int bitCount()`, `double bitDensity()`, and
`int maxPreemptions()`. Ordinary BitstateStore and the new store implement it. Migrate concrete
type assumptions in runner, harness and CBS capacity checks; do not label the new type EXACT.
Arbitrary factory-returned checked stores remain rejected by the ordinary runner path.

The new direct factory is `BitstateSymmetryStateStore.forRun(Program, Invariant, Strategy,
int bits, int hashFunctions, int maxPreemptions)`. The final argument is zero for non-CBS;
CBS uses its configured capacity. Unsupported stage/strategy combinations and invalid capacity
reject before traversal. `clear` resets membership/statistics; `freshCopy` retains the immutable
binding/configuration and owns empty independent storage.

Extract `state.BitstateFilter` for bit operations/probes over a precomputed int hash. Preserve
ordinary BitstateStore's hash inputs, rotate-left-17 secondary hash, zero-to-one fallback, Java int
overflow, floorMod probes, lazy per-cost vectors, mark counts and metric formulas. New symmetry
keys use `Arrays.hashCode(encodedBytes)` before the same probe operations. This is noncryptographic
lossy hashing, not a new statistical guarantee.

New key bytes use DataOutputStream big-endian fields: version byte `1`, domain byte, UTF-8 orbit
byte length as int, and those bytes. Domains: VALUE=`1` (no suffix); CBS=`2` (cost int suffix,
using the existing context-aware orbit key with mapped last owner); DPOR=`3` (token count int,
then owner/first-occurrence-PC int pairs sorted lexicographically). Integers are nonnegative except
the initial last-owner sentinel already handled by canonicalization. No raw Set.toString, object
identity hash, source label, ambiguous concatenation or canonicalized execution is admitted.

## Metrics and consumers

New stores own one filter. Mark count is a long insertion count, including duplicate/domain
marks; it is not distinct states or orbit count. Physical bit cardinality is at most `bits`, so
existing int metric interfaces remain representable. Density is cardinality/bits.

For the new mode only, the single-lookup diagnostic is `F=(1-exp(-k*n/m))^k`; empty is zero.
CBS reports `min(1,(pMax+1)*F)` where pMax is the largest queried cost in that producing pass.
This is an idealized union-model estimate, not a proven bound, calibrated probability, or search
coverage percentage: probe correlations, duplicates and pruning matter. Ordinary diagnostics retain
their existing formulas. Reports describe the producing pass rather than aggregating cleared filters.

Retain `storeType: BITSTATE`, `symmetryReduction: true`, current `APPROXIMATE_PASS`, actual
failure/deadlock precedence, INCOMPLETE and resource provenance. Approximate rows are excluded from
exact verdict-agreement checks; reported violations still undergo actual execution replay. Preserve
the existing CLI bitstate CBS bound limit and require an explicit singleton EXACT or BITSTATE store
selection with symmetry. No additional wire field or CLI option is selected by this set.

## Oracle and verification contract

- Check ordinary filter compatibility against independent existing literals. New numeric oracles
  must be derived outside Java by two routes and asserted as literals, as AGENTS.md requires.
  Example uniform-model values independently checked with Python math and Decimal: n=2, m=8,
  k=2 gives F approximately `0.15481812174617549`; pMax=2 gives approximately
  `0.46445436523852646`. Compare with an explicit floating tolerance, not exact decimal equality.
- Use an independent finite set model of full typed frames/product contexts and a separate
  brute-force owner permutation oracle. Do not use the production canonicalizer to generate its
  own expected keys. Test helper serialization directly against the specified byte layout.
- Differential verdict/count checks against exact search require observed zero false-positive
  decisions in the fixture, confirmed by a test-only exact shadow at the membership seam. Do not
  assume a large filter cannot collide or require approximate runs always to match exact counts.
  For finite fixtures, enumerate the complete relevant context universe independently and drive
  the public store directly; use a fresh store for the actual explorer comparison. A sufficient
  monotonicity check probes each absent target after inserting every noncovering candidate key.
  CBS excludes intended q<=p covering marks from those negative cases; DPOR includes VALUE marks.
  If that check finds a false positive, the fixture is not collision-free. Do not wrap a sealed
  checked store as an ordinary StateStore and thereby exercise a different explorer dispatch.
  A fixed fixture intended to be collision-free must fail the test if this check detects a false
  positive; it must not skip differential assertions or select different runtime parameters.
  DPOR oracles retain the existing value witness and physical-ID
  tie break; they do not minimize sleeping tokens over further equal-frame permutations.
  The package-owned encoding/mapping module is also an agreed internal test interface; no private
  reflection or production callback hook is needed to check the specified byte layout.
- Force collisions with tiny filters. Missing behavior is permitted; invented witnesses, exact
  PASS, changed original source identity and false resource/cutoff provenance are not.
- Fresh merged-main and feature measurements are required for each implementation PR. Save raw
  affected/default XML independently; compare unchanged identities/statuses excluding shifted lines.
  Record moved/rewritten sites separately. Update only measured bookkeeping; no weakened operators,
  targets, tests, resources or 94% floor. Scoped R5/R7 are not full-default checks.
- Local Gradle commands always include `--max-workers=1 -PpitestThreads=1`. CI stays parallel.
  Include ordinary build/tests/strict Javadoc, actual source/CLI JSON, normalization and browser
  checks, external library usage, and explicit residual/coverage limits.

## Skill and publication workflow

Each spec lists task-specific skills and the agreed test interfaces. Use `ponytail` full for the
smallest complete implementation, `ponytail-review` for independent connected-code review, and
`code-review` for the selective external gate. Reuse approved decisions; skills do not reopen
settled scope or authorize unrelated changes. `autofix`/`receiving-code-review` apply when actual
review findings arrive. Only the extraction work is behavior-preserving refactoring; feature changes
must not be disguised as refactors.

For this authoring task, complete and inspect each spec, perform fresh-reader review, then invoke
CodeRabbit CLI after that spec is complete and before pushing. Count only an actual completed review
covering the document; rate limits/errors/skips do not establish review. Fix valid findings locally;
do not rerun merely for minor fixes. Record actual evidence without claiming implementation tests.
The same complete-spec/implementation review gate applies to each future feature PR. Publish and
stop for Sam's review/CI/merge update; no watcher or automatic next-phase implementation.

## References

[Spec 76](../specs/active/76-source-symmetry-exact-search.md),
[Spec 77](../specs/active/77-source-symmetry-strategy-composition.md),
[Spec 81](../specs/active/81-aliased-source-symmetry.md),
[Spec 84](../specs/active/84-certified-symmetry-backends.md), and
[Spec 88](../specs/active/88-resource-prefix-presentation.md) retain their existing ownership.
[SPIN bitstate guidance](https://spinroot.com/spin/Man/Pan.html) supports the approximation distinction;
it supplies no quantitative guarantee for this implementation's hash scheme.
