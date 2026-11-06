# Spec 75: Thread-symmetry canonicalization foundations

Foundation merged in PR #117 at ac16444. This document records that phase's internal API;
[Spec 76](76-source-symmetry-exact-search.md) exposes the mathematical canonicalizer to checked
exact DFS while keeping program/property eligibility separate from value canonicalization.

## Scope and release boundary

Begin active roadmap #10 after merged PR #116 at 2d320ae. This first coherent PR owns orbit-key
canonicalization, original/canonical owner mappings, the conditional correctness argument and
independent tests. It does not change visited stores, explorers, source syntax, CLI options or
runtime results. Those integrate in a subsequent PR only after program/property eligibility is
validated. No deferred/conditional follow-up is activated.

A user-supplied group is not automatically a sound program symmetry. The foundation validates
its mathematical partition and produces a value key; it does not infer behavioral equivalence
from names, matching PCs or similar-looking instructions. The next implementation must establish
an equivariant transition/property contract before using these keys to prune exploration.

## Internal value domain and interface

The package-private ThreadSymmetryCanonicalizer has an immutable partition of disjoint owner groups, over one through
eight owners matching the current finite source budget. Omitted owners are fixed points. Accept
singleton groups as fixed points and normalize them away; reject empty groups, duplicate/overlapping
IDs, out-of-range owners and null inputs. Normalize group/member order so equivalent partition
representations have the same key domain. Copy caller collections; no mutable global/run cache.

Canonicalization accepts a validated DynamicState, matching nonnegative PC vector, and optionally
a last scheduled owner (-1 for the initial CBS sentinel). Plain and bounded keys have distinct
context markers. Reject owner-count/PC mismatches, negative PCs, invalid last-owner values and
null inputs. This value primitive does not validate a whole program's control-flow targets.
The caller must not mutate the input during the operation.

Within each group, sort complete owner frames by PC, then all declared local values in declaration
order, then whether that owner is last scheduled. Compare integer/boolean scalars by value and
integer arrays lexicographically. Original owner ID breaks ties for a deterministic witness map;
identical frame ties cannot affect the canonical value. Assign sorted frames to ascending group
positions. Never sort PCs and local columns independently, sort shared arrays, or reinterpret an
integer as a thread reference.

Copy modeled state and move complete local rows from the original into the copy; keep every shared
field/schema/initializer value unchanged. Encode the copy with the existing CanonicalEncoder's
value payload. Combine a partition-scoped orbit-key domain, encoded state, ordered PCs and mapped
last-owner/plain marker into an opaque value key. No default canonical key or byte format changes.
Keys are scoped to one program and partition, not cross-program identity or stable serialization.

Return an immutable result containing the key and canonical-to-original bijection, plus checked
lookup in both directions. Do not execute canonicalized configurations or rewrite trace owners,
source locations/digests or loop ancestry. The mapping is a value identity witness, not an
instruction to relabel already captured diagnostic metadata.

## Canonicalization argument

Let a frame be (PC, ordered typed local values, last-owner marker). Let G be all permutations
within the validated disjoint owner groups. Shared state/schema are fixed. Permuting a position by
pi in G moves each whole frame, and maps the last owner by pi. For any group, the multiset of
frames is invariant under this action. Sorting under the same total order therefore produces the
same canonical frame sequence for every member of the orbit. Original-ID tie breaks affect only
which equal frame witnesses the mapping; canonical bytes/PCs/context remain equal.

Conversely, equal partition-scoped keys imply equal injective modeled-state encoding, PCs and
context, using the existing DynamicState encoding contract. The original-to-canonical bijections
then compose to a within-group permutation taking one position to the other. No local/PC
correlation is lost, and fixed/shared components cannot be merged through an owner permutation.
A position maps to itself under some group permutation; repeated canonicalization preserves the
same key. Both direction lookups are inverses on every owner.

For a future exploration quotient, additionally require initial-state invariance and transition
and property equivariance. A conservative source candidate has identical instruction/control/
expression semantics in each group, common local declarations, no tid observations or owner-ID
interpretation, and a pure shared-state property. Source labels/spans may differ; actual execution
must continue in original identities and witnesses must replay there. This paragraph states
eligibility obligations, not an implemented eligibility checker or a mechanically verified proof.
CBS cost preservation also requires mapping last-owner context; sleep-set/reduction contexts
need their own applicability proof before composition. Unknown callbacks and unsupported cases
retain current behavior until explicitly integrated.

## Independent acceptance

- All permutations of small typed frames (integer extrema, booleans, arrays and PCs) within groups
  give equal keys; unrelated partitions, shared values, declarations and PC/local associations
  remain distinguishable. Verify all bijections and fixed owners, including noncontiguous groups.
- Last-owner changes map with frames. Identical frames may exchange last-owner identities; distinct
  frame roles cannot merge different last-owner positions. Plain and CBS-initial contexts differ.
- Equivalent group ordering/singletons normalize identically. Inputs/results remain isolated after
  caller mutation; input state and arrays never change. Returned collections are immutable.
- Four identical one-action counter owners have 16 concrete PC masks and five count-based orbits;
  eight have 256 masks and nine orbits. Derive by Cartesian enumeration and binomial/count arguments
  outside Java, then assert literals. Actual DFS still explores the concrete states: count orbit
  keys offline only, retain real owner traces and replay every witness. No runtime reduction or
  speedup is claimed in this PR.
- Two split load/store owners preserve distinct local observations and both lost-update outcomes;
  check independently enumerated full-state and orbit counts, not only agreement of implementations.

## Verification and staged continuation

Fresh merged baseline clean build/tests/strict Javadoc and default gate precede changes. The new
class has no earlier mutation population; measure it and its mapping result separately from the
inherited default identities, preserve raw residuals with scoped arguments, and credit no resource errors. No
mutation target/filter/operator/floor/resource or CI parallelism change to improve scores. The existing
state.* default scope includes these new classes: account for its added population and update the
denominator only from a measured identity census, retaining the existing 94% floor.
Run integrated checks, deterministic permutation/encoding/census/replay oracles and completed
selective CodeRabbit review. No browser run is required if this foundation changes no wire/UI
contract. Publish one draft PR and stop for Sam's CI/review/merge update.

Next #10 PR owns actual source eligibility and complete opt-in exact-search/store/CLI/report
integration with real original-identity witnesses and measured before/after counts. Reduced/CBS/
approximate combinations remain unchanged or explicitly unsupported until their integration and
proof apply; do not silently claim this value lemma covers every explorer or property callback.

## Results

Implementation and 21 new cases are complete: integrated build, strict Javadoc and 1,543 tests
pass with zero failures/errors/skips. Separate private-member doclint also passes. Final new-class
scope is 150/156 K + 6 S, zero uncovered/resource/run errors; mapping result 11/11. The first
default failure exposed the new classes in the existing state.* scope and one uncredited memory
error. Finite range construction fixes that resource-sensitive loop without changing PIT resources.

Measured default denominator is 449, with the unchanged 94% floor requiring 423 kills. Final
433/449 K + 16 S passes all R5/R6/R7 gates with zero uncovered/resource/run errors. Every inherited
293 identity/status matches the fresh 283/293 baseline; the added 156 exactly match the final
scope. No original survivor is claimed killed. The [evidence note](../../measurements/75-thread-symmetry-foundations.md)
records distinct populations, the failed run, raw residuals (including an unproven comparator
coverage limitation), integer floor/headroom accounting and reproducible commands.

Final CodeRabbit CLI review completed all seven files with zero findings; agent-setup record
saved. Browser/CLI manual checks are omitted because no runtime/wire/UI contract changes.
The foundation merged in PR #117; actual symmetry pruning and eligibility are implemented in
Spec 76 as the next #10 phase. No runtime speedup or unconditional quotient proof is claimed.
