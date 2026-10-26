# Spec: Legacy persistent-set selection and probe errors

## Objective

Protect legacy static-POR selection against current-PC substitutions and removed interference
probes. A pure deterministic probe may throw; Boolean enabledness equivalence alone is insufficient.

## Scope

Add PersistentSetContractTest, this specification and the roadmap update. Correct the public
computePersistentSet return documentation to acknowledge an empty enabled set. No executable
production, public API, dependency, output, default mutation scope/operator/exclusion or gate changes.
Independence-helper internals and serialization remain separate. Spec40's property-selector
component/79 resource limitation stays open and is not remeasured or hidden by this distinct scope.

StaticPorExplorer uses the legacy PersistentSetComputer only without an invariant and without a
property-aware selector. Compiler-produced source models use the suffix-aware selector instead;
UNKNOWN properties retain exhaustive fallback. The public legacy helper is tested with actual
legacy Program/Configuration inputs, not a source model mislabeled as legacy.

## Requirements

1. The helper SHALL return no owners when none is enabled, the sole enabled owner when only one
   is enabled, and, when pair analysis completes normally, the first enabled owner when every
   current-step pair is independent. Probe exceptions follow requirement4 instead.
2. When current-step conflicts exist, the literal selection SHALL contain the dependent group,
   excluding an unrelated independent owner. This is an implementation selection contract, not
   a claim that every correct reduction must choose identical sets or enumerate identical traces.
3. Both current owner and other owner lookups SHALL use their actual configuration PCs. A consumed
   write SHALL NOT introduce a current-step dependency.
4. Interference probes SHALL preserve deterministic legacy execution exceptions and their messages.
   Successful and failing probes SHALL leave the parent configuration's values/counters unchanged.
5. A retained single-enabled boundary mutation SHALL receive only a valid-model selected-ID-value
   disposition, excluding malformed inputs and resource/performance behavior.

## Independent fixture oracles

Seven cases use immutable legacy definitions and configurations derived by Program/TransitionExecutor.
The initial flag values are false,false with turn0 unless explicitly changed in the error fixture.
No fabricated enabled IDs, null steps, mutable execution counters or incomplete footprints appear.

| Initial threads | Expected IDs | Derivation |
|---|---|---|
| Both completed | [] | No current actions exist |
| Owner0 completed; owner1 reads flag0 | [1] | Only owner1 is enabled |
| Owner0 completed; owners1/2 read separate flags | [1] | Read/read independence; fallback uses the first enabled owner, not literal owner0 |
| Owners0/1 write flag0; owner2 writes flag1 | [0,1] | Only0/1 conflict;2 is independent |

Each initial case also asserts that the two flags and turn retain their literal starting values.
These value oracles are independent of the returned selection and expose accidental parent writes.

### Current-PC pair

One owner writes flag0 then flag1; the other writes flag0; owner2 writes turn. Commit the first
write of owner0 or owner1 through TransitionExecutor. Both rows have ADVANCED/no cutoff,
enabled[0,1,2], flag0=true, flag1=false, turn0. Counters are respectively[1,0,0] and[0,1,0],
independently enumerated from one committed action for the selected owner and none for the others.
Current actions write three disjoint fields in either row, so the literal selection is[0].

| Advanced owner | Correct | Selected-owner PC replaced with0 | Other-owner PC replaced with0 |
|---|---|---|---|
| 0 | [0] | [0] | [1] |
| 1 | [0] | [1] | [0] |

A spurious dependency involving fallback owner0 can leave the same[0] result. Keep both mirror
rows: either one alone masks one lookup substitution. The lists follow direct pairwise comparison
of current versus consumed flag writes, not a second selector implementation. Both rows assert
that probes leave flag0=true, flag1=false and turn0 unchanged.

### Independent probe error

Owner0 reads flag1. Owner1 reads flag0 and throws IllegalStateException("flag[0] is false") exactly
when that flag is false; otherwise it advances. Both actions have empty writes and complete stable
read sets, so they are independent. Both are enabled on either supported flag value.

While considering owner0, the selector probes other owner1 before selecting/executing any action.
At flag0=false, the exact exception and message must propagate; replacing the probe result with
false would suppress it and incorrectly return[0]. Parent counters stay[0,0] and both flags stay
false. Reuse the same immutable definitions at flag0=true: selection succeeds with[0], counters
stay[0,0], flag0 stays true and flag1 stays false. There is no call-count/history-dependent behavior.

This probe is not the already-successful transition repetition in Spec38's DPOR disposition.
Here the action has not executed before preparation; purity does not imply totality. The legacy
unchecked exception remains an exception, not manufactured source failure evidence or a violation.

## Retained single-enabled boundary

computePersistentSet/11 ConditionalsBoundary changes enabled.size<=1 to enabled.size<1. With one
enabled owner, the altered code looks up that owner's valid current step, skips itself in the inner
loop and adds no dependent owner. Empty persistent then falls back to the same enabled ID. No
footprint comparison, model enabledness call, execution or interference probe runs on this path.
The original code returns the cached enabled list; the altered path returns a singleton with the
same value. List identity/allocation/work/resource/performance behavior is not claimed equivalent.

This disposition assumes matching immutable thread definitions, valid derived enabled IDs/PCs,
non-null current steps and non-mutating model operations. Invalid mismatched thread lists or null
steps cannot be used to establish a supported-contract kill. Keep the production boundary.

## Baseline and verification

PR #81 merged at0beb3d5. Fresh baseline:957 tests pass, zero failures/errors/skips. Scoped legacy
PersistentSetComputer:31/35 KILLED,4 SURVIVED,0 uncovered/timeout/memory/run-error statuses. The
baseline scoped ratchet passes. This scope is separate from Spec40's failed property-selector gate.

All seven new cases and the full964-test suite pass, zero failures/errors/skips. Final scoped
PersistentSetComputer:34/35 KILLED,1 SURVIVED,0 uncovered/timeout/memory/run-error statuses.
All35 semantic identities (class, method, descriptor, mutator, indices and blocks) match the fresh
baseline, and all31 old kills remain killed. The scoped ratchet passes. Three survivors become kills:

| Method / bytecode index | Mutation | Killing regression |
|---|---|---|
| computePersistentSet/56 | Removed selected-owner PC Integer.intValue | pairLookupsExcludeTheConsumedWrite, advanced owner1 |
| computePersistentSet/108 | Removed other-owner PC Integer.intValue | pairLookupsExcludeTheConsumedWrite, advanced owner0 |
| computePersistentSet/148 | Removed hasEnableDisableInterference call | independentProbePreservesDeterministicErrorsAndParentValues |

The retained computePersistentSet/11 has the scoped boundary disposition above. Thus34 kills+1
scoped survivor disposition account for35 identities, without a universal equivalence or whole-checker
closure claim. The first post-change run had33 kills before adding the mirror PC case; final scoped
measurement includes both mirror rows and the return-value Javadoc clarification.

CodeRabbit CLI0.8.1 completed its substantive four-file review with zero findings. Final clean build, strict Javadoc and all964 tests pass, zero failures/errors/skips. Default PIT
killed281/291 mutations and left10 SURVIVED, zero uncovered/timeout/memory/run-error statuses;
all291 semantic identities and statuses match merged Spec40 evidence. Both the legacy-selector
scoped gate and the default gate pass. This does not close Spec40's distinct property-selector
resource limitation. Measured verification/status prose and the existing probe-error qualification
were finalized after the CLI review; executable tests and production behavior were unchanged.
All18 normalization checks, links, credential-pattern scan and diff checks pass. All local Gradle
commands use `--max-workers=1 -PpitestThreads=1`. Browser integration is outside this bundle because
executable production/report output is unchanged. Remote CI remains unverified; publish as draft
and stop. Historical expanded9-V1 totals remain unremeasured; separate scopes are not an aggregate.
