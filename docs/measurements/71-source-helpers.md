# Bounded source helpers: acceptance and mutation evidence

## Scope and independent oracles

Baseline is fresh merged main 8f502d7 (PR #112). Final measurements use that revision plus the
Spec 71 patch. Helpers add source syntax and bounded expansion, with no runtime call stack,
new reduction rule, trace field, dependency or raised existing budget.

Two atomic increment owners have the PC Cartesian product {0,1} squared: four configurations,
with terminal counter 2. Independently enumerating transitions over (pc0,pc1,counter,r0,r1) yields
four configurations and {2}. For split load/store bodies, enumerate the six permutations of
0,0,1,1 respecting owner order: two serial orders yield 2; four overlapping-load orders yield 1.
Independent state enumeration with retained owner registers yields 13 configurations and {1,2}.
Java asserts literal 4/13 and {2}/{1,2}; helper/manual-inline footprint comparisons supplement
these independent value oracles. CBS visit counts are observed counters, not unique-state oracles.

Boundaries are counted directly: 16 definitions accept, 17 reject; two calls of 32 actions give
64, adding one gives 65. A doubling DAG from one action has 64 actions at h6 and 128 at h7, also
cross-checked by counting leaf call occurrences. A 1,250-leaf balanced sum has 1,249 binary nodes,
so 2,499 RHS nodes plus one assignment-target node = 2,500. Two occurrences give 5,000; a literal
true property adds one and rejects at 5,001. Counting leaf/operator/target tokens independently
confirms the same totals. Every owner contributes each occurrence. Unused definitions also obey
their individual budgets; private atomic bodies are checked even in zero-repeat/else branches.

Literal source-location oracles count columns in fixed text, independent of SourceText:
helper atomic span 3:20–3:53, outer call 5:12, inner calls 4:15 and 4:31, repeated call 5:34;
PCs 0,1,2,3 and repeat iterations 0,1. Runtime modulo failure points at definition assignment
4:23; loop cutoff points at definition while 4:1, retaining rollback and omitted attempt evidence.
Missing target and argument diagnostics point at literal columns 17 and 21. Depth-16 chains
retain the existing 256-character-plus-ellipsis label cap and replay successfully.

## Integrated and CLI checks

Fresh baseline clean build/strict Javadoc and 1,399 tests pass. Final build/strict Javadoc and
1,443 tests pass, zero failures/errors/skips (44 new cases). Existing teaching acceptance now loads
nine source examples across strategies/stores, checks report attestation and replays evidence.
All 18 visualizer normalization cases pass. Browser rendering was not rerun: renderer and trace
schema are unchanged; helper context uses the existing safely rendered bounded label.

Actual Main CLI, exact store, both new checked-in example files:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 -q run --args='--source examples/programs/helper-increment.ilv --store EXACT --json'
./gradlew --max-workers=1 -PpitestThreads=1 -q run --args='--source examples/programs/helper-split-increment.ilv --store EXACT --json'
```

| Example | DFS visits | Static POR visits | DPOR visits | CBS visits (K=2) | All exact verdicts |
|---|---:|---:|---:|---:|---|
| helper-increment | 4 | 4 | 4 | 5 | PASS |
| helper-split-increment | 13 | 13 | 13 | 17 | VIOLATION |

Both JSON reports parse strictly and have soundness=true. Split witnesses carry callsite labels and
definition spans. Local artifacts: /private/tmp/interleave-helper-{increment,split}-cli.json.
Timing/heap fields were not used as benchmarks; no performance improvement is claimed.

## Separate mutation populations

| Scope | Baseline killed / total | Final killed / total | Final survived | Final uncovered |
|---|---:|---:|---:|---:|
| SourceCompiler | 739/787 | 841/891 | 28 | 22 |
| SourceInstruction | 114/114 | 115/116 | 1 | 0 |
| Affected two-class run | 853/901 | 956/1007 | 29 | 22 |
| Default state/CBS gate (separate) | 283/293 | 283/293 | 10 | 0 |

All runs have zero timeout, memory, non-viable, run-error or equivalent statuses. Affected R6
passes; R5/R7 skip. Default R5/R6/R7 pass with unchanged 94% floor and 293 denominator.
All 293 default semantic identities and statuses match the frozen prior default report exactly.
The default baseline is Spec 70 final at 9c6ff84, whose complete tree equals merged 8f502d7;
the affected two-class baseline was rerun fresh at 8f502d7 before edits.
No target/test filter, mutator, exclusion, floor, denominator, PIT heap/time or CI parallelism changed.
Never add the default and affected populations.

Compiler population grows by 104, instruction by two, due production syntax/cost/lowering/label
changes. Changed methods relocate indexes/descriptors; this is not an all-901-identities-retained
claim. Unchanged method identity/status comparisons are recorded below. First final affected run
was 951/1007 K + 34 S + 22 NC. Four exact diagnostic test cases kill five actionable entries:
missing-target offset, argument offset, helper-cap definition offset, private-cap definition offset,
and omitted else-arm helper validation. All 1,007 identities match between those two final runs;
exactly five SURVIVED→KILLED changes, every other status unchanged. No production edits were used
to force kills. Frozen local XML: /private/tmp/interleave-helpers-{baseline,first-final,final}.xml
and /private/tmp/interleave-helpers-default-final.xml.

## Retained residuals and applicability

The [Spec 66 ledger](../specs/active/66-source-compiler-verification-closeout.md) remains the prior
compiler disposition map. Two raw REPEAT/WHILE unused-no-list survivors now kill: helper declaration
validation walks both child lists, observing their non-null staging invariant. The remaining 24
old survivors and 22 uncovered entries retain their scoped assumptions. CALL expansion is a separate
branch: the old reconstructed non-loop count still receives only ATOMIC/IF/ASSIGN with count zero.
Transparent call fragments preserve the prior forward-entry/target argument: root PC 0 has no
incoming edge, while child fragments after an emitted condition start at least at PC 1.

Four new compiler survivors and one instruction survivor are retained with scoped value arguments:

| Method / mutator / index / block | Disposition |
|---|---|
| block / NVMC / 387 / 106 | Raw CALL path is overwritten during expansion; removing the empty staging path does not change exported ancestry. |
| expand / NVMC / 51 / 15 | Expanded CALL no-list is unused by CALL lowering; callees live in yes. CALL is rejected inside atomic interiors. |
| lower / NVMC / 61 / 17 | Removing Fragment.start only in the empty-callee test substitutes zero. Positive-cost valid calls lower nonempty with nonnegative entry; zero-work calls were already removed. Both values pass this test. |
| lower / boundary / 68 / 20 | Changing start<0 to start<=0 can reassign entry zero only at the global/transparent-call root, whose start is unused and has no incoming edge. Child fragments follow an emitted instruction and start above zero. |
| metadata / NVMC / 24 / 8 | Removing String.isEmpty makes the existing prefix concatenation unconditional. Empty-string concatenation preserves label value; nonempty prefixes already concatenate. Allocation/identity equivalence is not claimed. |

NVMC is NonVoidMethodCallMutator; boundary is ConditionalsBoundaryMutator. These are single-mutant,
validated-loader/model/evidence-value arguments, not universal equivalence, runtime-resource proof,
or whole-checker soundness closure. Keep production guards and raw statuses; no exclusions or kill
credit for dispositions. Revisit them when staging, expansion, atomic-call eligibility, fragment
entry consumption or label contracts change.

Unchanged production methods retain all 491 complete semantic identities and statuses,
including descriptors, mutators, indexes and blocks. Changed methods require source-aware mapping;
coincidentally shared bytecode indexes alone do not prove retained mutation meaning.

CodeRabbit CLI 0.8.1 completed all 17 changed files. One trivial finding is fixed by asserting
nonempty owner-local traces before per-trace checks. One minor roadmap suggestion is not adopted:
the user's “9-f2 and so on” explicitly selects sequential continuation; the table retains that
order and identifies earlier conditional recommendations as tradeoff advice. Follow-up integrated
checks pass; no CLI repeat for this trivial test/documentation change. No production edits follow
the final scoped/default PIT measurements, so another mutation run is not needed for this assertion.
