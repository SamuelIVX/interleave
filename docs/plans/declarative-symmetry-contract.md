<!-- B10 spec-set contract: trusted declarative eligibility, phase ownership and independent assurance. -->
# B10 — Certified declarative JSON symmetry

## Objective and status

**L1 implemented in this change; locally verified; merge pending. L2 planned. B10 remains in progress.** Planning baseline:
main `b1c7586fc34542e919b3f82d1dece87ade7dec86`, after B9 / PR #134 merged at `3d5c09e`.
Sam selected declarative JSON and the full DFS/static POR/DPOR/CBS exact/approximate matrix.
This approval supersedes B9's stop-before-B10 boundary for this selected scope only.

Use one spec-only PR, followed by two sequential complete implementation PRs. A merged spec does
not enable a feature. Implementation PRs update their spec, tests, measurements and support table.

| Phase | Document | Owns | Release boundary |
|---|---|---|---|
| L1 / 92 | [Certified declarative exact symmetry](../specs/active/92-certified-declarative-symmetry.md) | Loader certification, groups, exact strategy routing, positive-witness attestation and exact consumers | All four exact strategies; declarative approximate selection explicitly rejects |
| L2 / 93 | [Approximate declarative symmetry](../specs/active/93-approximate-declarative-symmetry.md) | Approximate admission, product verification, precision labeling and final consumer matrix | Adds all four approximate strategies; complete B10 only after L2 merges |

## Selected model family and trust

- **Certified declarative instruction/property:** immutable typed objects issued by DslLoader only
  after its explicit format discriminator, parsing, typing, AST/work bounds and shared-only property checks succeed.
  Certification is private immutable state, not a public flag, filename, JSON assertion or class test.
- Existing public DynamicStep/DslInvariant constructors retain ordinary behavior but issue no
  certification. Package-private validated factories require a non-null DslLoader.ValidationPermit:
  a package-visible final nested type with a private constructor and a single private loader-held
  instance, never exposed by an accessor or returned model. Null permits reject before certification.
  No public certificate injection API or caller-declared observation proof is introduced.
- Program.initialStateType() and Program.initialStateInputType() are read-only metadata queries
  of the retained snapshot and original constructor input classes, invoking no model code and
  retaining no input object. Certification requires both types to be DynamicState before another
  copy/initialization; adapters returning DynamicState on their first or later deepCopy cannot
  establish trust. Ordinary Program construction/copy semantics remain unchanged.
- A checked declarative model contains only certified DynamicStep objects, dense actual owner
  bindings, DynamicState with matching owner count, and an absent or certified DslInvariant. Every
  step/property must retain the same StateDecl object as the initial DynamicState. Trusted objects
  reassembled by library callers must still pass every binding, family and initial-state check;
  possession of one certified step does not certify a Program. Reassembly must recheck model-wide
  owner/step bounds and the 5,000-node aggregate AST limit, counting every instruction occurrence
  and the property. Use bounded/early-aborting counts; aliases do not exempt repeated occurrences.
- Source and declarative families use separate certification paths. Mixed families, unknown steps,
  public-constructor objects, foreign declarations and custom properties reject before traversal.
  Correctly bound fixed owners also require trusted instructions; fixed does not mean unchecked.
- No loader cap changes: declarative 1–8 owners, 1–64 steps per owner/at most 512 total, existing
  effect/predicate/array/expression limits. Source caps retain their separate existing limits.

## Group and execution contract

Keep the public SourceSymmetry.groups(Program, Invariant) signature as the common checked entrypoint;
broaden its documentation and dispatch, not its return type. Source behavior remains unchanged.
Declarative candidates compare ordered typed guards and complete ordered effect lists at every PC,
with identical list/control shape and equal typed initial locals (including array contents).
Only diagnostic names are ignored. No algebraic normalization, effect reordering, PC relabeling,
shared-field permutation, or owner-indexed-array renaming is inferred.

Any tid observation in guards, effect RHS or assignment indices makes that owner fixed, even in
short-circuited syntax. Use exhaustive handling of the sealed Expr variants, including local-array
indices. Other matching owners may form disjoint noncontiguous groups. Reject an empty group set.

Initial equality makes owner-frame permutation invariant. Typed structural equality without tid
makes corresponding guards, ordered assignments, errors and next-PC behavior commute with that
permutation. Shared fields stay fixed, locals/PCs move together, and certified shared-only properties
and all-termination are invariant. Fixed owners access only their own locals and shared values.
State this argument for both successful actions and failed attempts, not just final values.

Execute the original Program/owners. DynamicStep guards may disable or error; error outcomes stay
ASSERTION_FAILED. Earlier effects can mutate a failed attempt's private scratch before a later
effect errors. TransitionExecutor publishes no successor for that failure. Do not introduce rollback
inside DynamicStep, commit a failed scratch state, or fabricate a canonical execution/failure state.
Retain predicate order, short-circuiting, always/final timing and existing ordinary error categories.

## Strategy and interface contract

Reuse sealed CheckedSymmetryStore, SymmetryRunContext and the existing canonicalizer. Keep run binding
by exact Program/property/strategy identity, fresh per-run/per-bound storage and HASHED/ORDERED
semantics. DPOR keeps first-occurrence action identity, matching alias partitions and the same
whole-frame permutation witness. CBS keeps mapped last owner and cost dominance.

The existing DPOR checked sleep lookup/all-asleep normalization is currently inside isSource(). L1
must route it for CheckedSymmetryStore independently of input format; ordinary JSON raw-key behavior
and actual source evidence stay unchanged. Certified JSON static POR without a property uses the
existing whole-remaining dependency closure. Declarative FINAL properties keep unknown-observation
exhaustive fallbacks; no new finalObservedLocations implementation is selected. Absent/ALWAYS
properties must exercise the real DPOR product path in tests; a strategy label alone is insufficient.

Use existing library symmetryReduction, symmetryBackend and symmetryBitstate settings. CLI extends
--symmetry to eligible --file input with an explicit singleton exact/bitstate store; omission of
strategy uses all four. Typed/registered JSON, named built-ins, --all, omitted stores and invalid CBS
options do not gain unchecked admission. Keep current defaults and CBS bitstate bound policy.
In L1, BitstateSymmetryStateStore.forRun explicitly rejects non-source inputs before allocation;
L2 removes that one temporary restriction after its tests pass. Source approximate support remains.

## Positive evidence and consumers

Declarative programs remain isSource()==false with no source digest. Do not widen source-only casts,
cutoff simplification, SourceStep metadata, B8 resource-prefix capture/authentication or source-native
replay rules. Existing JSON/source schemas and build-specific Java stream policy remain in force.

L1 emits known storeType for sourceModel OR symmetryReduction, but programKind SOURCE remains
source-only. L1 adds one private SoundnessAttestation helper for symmetry-enabled non-source VIOLATION rows. It
re-certifies groups, validates trace lengths/owner/PC/enabledness and executes actual transitions
through TransitionExecutor. Match every recorded outcome; stop at an actual ASSERTION_FAILED only
when it is the final recorded action, without constructing a successor. Otherwise a supplied
invariant must be false at the observed configuration, including a root/empty trace. Reject trailing
choices after earlier failure/property violation, invented no-property violations, and malformed
traces. Certification/execution errors fail attestation. Do not alter generic public TraceReplayer
semantics or claim uploaded JSON has authenticated source identity.

Viewer production normalization in docs/visualizer.js must use source-or-symmetry for selected
TestResult precision/incomplete handling, including no-trace and completed-trace payloads. L1 owns
exact/INCOMPLETE handling; L2 owns final BITSTATE absence checks. The resourcePrefix authentication
branch remains strictly source-only. Ordinary unmarked JSON display behavior remains unchanged.

Checked declarative producers use the dedicated TraceReplayer.captureDeclarativeSnapshots diagnostic
method: replay enabled original choices through TransitionExecutor, capture committed configurations,
and repeat the previous state for a failed last attempt. Full/partial runner and harness paths share
this policy; legacy replay/capture methods stay unchanged. This closes tentative-write leakage without
inventing a committed successor or adding source identity. Captured values are not terminal proof.

Existing source/ordinary report behavior stays intact. L2 broadens BenchmarkResult approximate-PASS
normalization to sourceModel OR symmetryReduction, retaining the BITSTATE condition. TestResult
retains the precision emission established in L1 without inventing SOURCE identity.
Retain symmetryReduction, representative counts, actual failure/deadlock precedence, bounded/resource
INCOMPLETE and approximate agreement exclusions. Do not upgrade approximate absence or a discovered
CBS bound into an exhaustive/minimality claim. Attestation prose must describe checks actually run.

L2 reuses B9's version/domain/full-orbit encodings, one filter across VALUE/CBS/DPOR, actual insertion
counts, queried-cost diagnostics and lifecycle. No hash/probe change, exact context cache, independent
per-cost budget, total-process memory bound, calibrated coverage percentage or speedup promise.

## Independent oracle and verification contract

1. Build a test-only finite reference using explicit owner permutations and typed values, independent
   of SourceSymmetry, production canonicalization and production key construction. Check enabledness,
   successful transitions, failed-attempt outcomes, termination/deadlock and properties under mapping.
   Pair malformed/adversarial rejection fixtures with eligible positive cases so rejection alone
   cannot make the suite appear sound.
2. Literal exact DFS fixtures: the existing two-owner semaphore has 8 concrete positions and 5 orbits;
   four one-step shared increments have 16 positions and 5 orbits. Independently enumerate transitions
   and cross-check permutation classes by a second route before asserting literals. These are bounded
   fixture counts, not universal reduction percentages or expected reduced-strategy counts.
3. Compare exact HASHED/ORDERED symmetry verdicts against unreduced finite DFS and appropriate ordinary
   bounded CBS references. Preserve actual owner choices; independently check enabled outcomes and
   terminal observations, including errors with a still-true invariant. Do not use weak legacy replay
   alone as proof of a failure or owner identity.
4. For approximate equality tests establish zero false-positive decisions on the finite fixture's
   complete relevant VALUE/CBS/DPOR context universe before using a fresh store in exploration. Use
   independent context enumeration and include accepted VALUE interference. Do not wrap a checked
   store in an ordinary adapter that bypasses sealed dispatch. Collision tests need not match exact
   counts/verdict absence, but every found positive observation must be real.
5. Remeasure each implementation baseline from its actual merged parent. Run full build/strict
   Javadoc/tests, affected PIT scopes and the unchanged default gate; retain all residuals and distinguish
   replaced fingerprints from inherited kills. Numeric formula expectations use independent external
   derivation and literal assertions. Resource/run errors are not kills; no floor/operator changes.
6. Exercise public built-jar callers, CLI emitted JSON/Markdown, normalization and browser checks with
   fresh producer artifacts. Include mixed exact/approximate reports, tampering, repeated/concurrent
   runners, clear/copy isolation and root/later resource stops. Preserve incomplete classification;
   do not require new source resource-prefix payloads from declarative runs.

Every local Gradle command uses --max-workers=1 -PpitestThreads=1. Existing docs/visualizer test
commands and actual baseline measurements must be discovered at implementation time; planning counts
are not a new test/PIT run. Record results in phase-specific ledgers created with implementation.

## Authoring, review and completion

Use the existing spec template at Sam's context/project-ideas/SPEC-TEMPLATE.md as the structural
convention.
Each phase has objective, scope/ownership, non-goals, invariants, event-based requirements, edge cases,
acceptance criteria, baseline/rollback, design, tagged current state, mapped tests and constraints.
Relevant skills are declared per spec, not used as a substitute for executable acceptance evidence.

Harness expected-verdict checks and attestation's exact DFS expectation checks must accept an
INCOMPLETE certified declarative symmetry result as inconclusive, retaining the existing source
exception and ordinary JSON policy. Verify the marked non-source Program/property with the shared
certifier before granting this exception; a symmetry marker alone is not proof. Do not call the
VIOLATION-only helper to authenticate an INCOMPLETE row or seed exact agreement from that row.

Run actual CodeRabbit after completing each spec before publishing the spec-only PR. Review the
complete shared contract/set with those specs; fix valid findings. Substantive implementation PRs
also require local review and actual CodeRabbit. Minor/trivial feedback fixes do not require redundant
CLI reruns. Record applicable evidence with agent-setup review-record. Stop after each published PR;
Sam supplies review, CI and merge updates. Do not start implementation while this spec PR is pending.

Complete B10 only after L2 merges, then update both work tables and personal context and stop.
Roadmap #11/#12, typed/registered/custom Java models, source normalization, dynamic owners, cyclic
execution, larger caps and other deferred/conditional work remain unselected.

## Specification-authoring verification

Separate fresh-reader passes checked L1 and L2 against this contract and current source. The readers
confirmed phase ownership, certificate/reassembly checks and strict positive-witness semantics;
their viewer production-scope and empty-owner observations were incorporated. These are document
reviews, not runtime/feature verification.

| Completed spec | CodeRabbit CLI result | Disposition |
|---|---|---|
| 92 | Completed review of initial spec/contract; three Major findings | Added reassembled-model aggregate limits and inaccessible loader permit. Verified the inconclusive-result gap; broadened certified INCOMPLETE expectation handling without misusing the VIOLATION-only helper. |
| 93 | Completed integrated five-file review; zero findings | Reviewed revised Spec 92, shared contract, Spec 93 and both tracking documents, including all first-review corrections. |

Canonical sections, requirement/test mappings, 121 relative links/anchors, 17 existing production
Java paths, table shape and whitespace pass authoring checks. The 8/5 semaphore and 16/5 counter
fixtures were cross-checked outside Java through transition/permutation enumeration and separate
finite constraints/counting routes.
No production code, Java tests, Gradle/PIT or browser runs belong to this spec-authoring task;
those remain mandatory implementation gates. Document review cannot satisfy them.
