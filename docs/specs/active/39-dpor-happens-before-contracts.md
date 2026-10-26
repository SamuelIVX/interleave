# Spec: DPOR happens-before bookkeeping and public graph contracts

## Objective

Audit the three remaining outer-DporExplorer survivor sites from roadmap 9-V1 and protect the
public HappensBefore graph's distinct contracts. Explorer bookkeeping is currently write-only;
that does not make observable helper queries interchangeable.

## Scope

Add HappensBeforeContractTest, this specification and a roadmap update. No production, API,
dependency, report-output, mutation-scope/operator/exclusion or gate changes. Do not remove the
bookkeeping, introduce new pruning or expose private state to force mutation kills. Broader helper
selectors, serialization, compiler safeguards and unmeasured expanded9-V1 work remain separate.

Use separate scoped reports for outer-DporExplorer and HappensBefore; do not compare their
percentages or fold their denominators into the unchanged default mutation scope.

## Requirements

1. An empty graph SHALL be reflexive, otherwise disconnected, with null absent steps, -1 absent PCs,
   zero absent counters and an empty direct-predecessor set.
2. Repeated records for the same source/target pair SHALL retain the first recorded step identity
   and source PC. Every event, including repeated edges, SHALL increment that source's counter.
3. Reachability SHALL follow transitive paths and terminate on cycles/converging paths without
   inventing a path to a disconnected vertex. Direct-predecessor queries SHALL exclude sources
   connected only transitively. Previously returned predecessor sets SHALL retain their values
   when later edges are recorded.
4. Copies SHALL preserve recorded steps, PCs, reachability, predecessor sets and counters, while
   isolating later additions to existing per-source maps and counters in both directions and
   from sibling copies. Immutable Step definitions may be shared by identity.
5. The three write-only explorer mutations SHALL retain explicit dispositions, not credited kills.
   Claims SHALL distinguish public helper behavior from the explorer's lack of graph reads and
   exclude process/resource/performance equivalence.

## Design and independent oracles

Five public-API tests use existing immutable WriteFlagStep definitions. They do not execute steps,
mutate the modeled state, use callbacks with hidden state, reflect into private fields or weaken
existing tests. Keep the earlier transitive-closure regression in DporExplorerTest.

- Empty graph: self reachability for vertex4; no0→1 path; literal absence sentinels.
- First record:0→1 at PC2 with stepA, repeated at PC7 with stepB, then0→2 at PC8 with stepB.
  Two records of one pair plus one other edge give2+1=3 events for source0; direct enumeration
  of the three calls independently confirms the literal counter3. Retained edge0→1 is stepA/PC2; edge0→2 is stepB/PC8.
- Direct versus transitive:0→1→2,3→2 and3→4 imply0→2 reachability, but only{1,3} are direct
  predecessors of2. Recording0→2 afterward adds0 only to a fresh query; the earlier set
  remains{1,3}. Separately,4 has only predecessor3 and is unreachable from0; this exposes
  replacing a nonzero predecessor ID with0 in the reachability check.
- Cycles/convergence:0→1,1→0,0→2,2→3,3→1. Reachability1→3 and3→2 follows explicit paths;
  vertex9 has no incident edge and stays unreachable. Predecessors of1 are{0,3}; of3 are{2}.
- Copy isolation: parent0→1(stepA,PC2),1→2(stepB,PC4) gives one event per source. Two copies
  preserve these values. Adding0→2(stepB,PC6) only to one copy gives its source0 counter2;
  adding1→3(stepA,PC8) only to the parent gives its source1 counter2. Counting those explicit
  event lists and enumerating retained edges independently cross-checks each literal oracle.
  The parent, changed copy and sibling retain distinct absent edges/PCs and unaffected counters.

Record order defines "first"; this is not a new minimum-PC selection rule. Public helper graph
cycles do not imply support for cyclic source control flow in the checker.

## Explorer bookkeeping dispositions

Production references in src/main/java/dev/samhb/interleave/dpor/DporExplorer.java construct a graph
at reduced entry/fallback, pass it through dporDfs, copy it before successor traversal and record
selected-owner edges. No explorer path calls happensBefore, getThreadsThatHappenBefore, getStep,
getPcAtRecord or getThreadLocalCounter. No result, visitor or external caller receives that graph.
UNKNOWN-property exhaustive traversal does not consume it either.

At the record loop, valid enabled IDs are integers and PC/Step values already come from the chosen
current action. HappensBefore.record stores Step references as map values without executing,
comparing or hashing them; its map keys are integers. The helper operates only on its own maps.
Changing those private graph contents cannot change a later scheduling decision, configuration,
state-store key, witness, failure evidence, visitor notification or property input because none
reads the graph. Copying that data does not introduce a read into scheduling.

| Method / bytecode index | Mutation | Disposition |
|---|---|---|
| dporDfs/540 | Removed Integer.intValue call in the other-owner loop | Replaces that bookkeeping target with0; no consumer reads the resulting edges/counters. |
| dporDfs/546 | Negated other-owner conditional | Records only the self edge instead of other-owner edges; graph queries are never used by exploration. |
| dporDfs/554 | Removed HappensBefore.record call | Omits unobserved ordering records; exploration inputs and emitted evidence are unchanged. |

Indices identify bytecode mutations, not source lines. These are output dispositions under valid
explorer inputs and normal resource availability. Allocation, map/copy work and counter-update
costs can differ; no process/resource/performance equivalence is claimed. The bookkeeping stays
in production. Any future graph reader in exploration requires reopening all three dispositions.
The public helper APIs do expose these distinctions and therefore receive independent tests.

## Retained public-helper reachability work

Two HappensBefore.happensBefore sites change the inner enqueue filter: /96 removes Integer.valueOf
before visited.contains, and /97 removes Set.contains. The former asks whether visited contains
null; valid public records and int arguments create only non-null integer vertices, so it is false.
The latter directly replaces the membership result with false. Both can enqueue an already-visited
neighbor instead of suppressing it.

The outer traversal still checks the target and then visited.add(current); an already-visited
vertex is discarded before expanding outgoing edges again. Every vertex's outgoing edges are
expanded at most once, and every reachable neighbor remains eligible for its first visit. Extra
queued duplicates therefore change neither positive reachability nor termination with false for a
disconnected target, including cyclic graphs. The public method returns only a Boolean and exposes
no traversal observer. Direct-predecessor results therefore remain unchanged as well. These are
output dispositions for the unchanged outer visited guard and valid graph records, excluding
allocation/stack-work/resource/performance behavior. Retain the filter and reopen the argument if
the outer guard or the public result/observer contract changes.

## Acceptance and verification

- Fresh baseline and post-change scoped populations match by class, method, descriptor, mutator,
  bytecode indices and blocks; retain every old kill and report all raw statuses.
- Five new public-helper cases and the full suite pass; clean build and strict Javadoc pass.
- Keep public-helper changed identities and any retained helper dispositions separate from the
  three explorer bookkeeping dispositions. Do not credit exceptions/timeouts as assertion kills.
- Default PIT and normalization pass; complete the substantive three-file CodeRabbit CLI review.
- Browser integration is outside this bundle because executable production/report output is unchanged.
  Remote CI remains unverified until Sam supplies its result; publish as draft and stop.

### Fresh merged baseline

PR #79 merged at74d1e37. All 943 baseline tests pass, zero failures/errors/skips. Fresh outer-DPOR:
265/289 KILLED,12 SURVIVED,12 NO_COVERAGE. Fresh public HappensBefore:35/83 KILLED,15 SURVIVED,
33 NO_COVERAGE. Neither baseline has timeout/error statuses. Freeze each report before adding tests.

All five targeted cases and the full 948-test suite pass after adding the tests; zero failures,
errors or skips. All18 normalization checks pass. Post-change scoped results below include the final predecessor assertion.
The substantive three-file CodeRabbit CLI0.8.1 review completed with zero findings.
Final clean build, strict Javadoc and all948 tests pass, zero failures/errors/skips. Default PIT
killed281/291 mutations and left10 SURVIVED, with zero uncovered/timeout/error statuses. All291
semantic identities and statuses match merged Spec38 evidence. The final scoped and integrated
runs include the nonzero-predecessor assertion; only measured verification/status prose was
finalized after the CLI review. Relative links, credential-pattern scan and git diff --check pass.
Browser integration was not rerun; remote CI remains unverified. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`.
Historical expanded9-V1 totals remain unremeasured.

### Post-change scoped measurement

Outer-DPOR remains265/289 KILLED,12 SURVIVED,12 NO_COVERAGE, with all289 semantic identities
and statuses identical to the fresh baseline. The three bookkeeping sites remain survivors.
Together with the21 prior scoped dispositions,24 dispositions (12 survivors,12 uncovered) account
for the remaining outer-DPOR identities:265+24=289. No listed outer-DPOR survivor audits remain
open; this is not a universal equivalence or whole-checker closure claim.

Public HappensBefore improves35/83→81/83 KILLED, with2 SURVIVED and0 NO_COVERAGE. All83
semantic identities match; every old kill remains killed. Exactly46 new kills comprise13 prior
survivors and33 previously uncovered identities. The two retained sites are happensBefore/96 and/97,
with the enqueue-filter dispositions above. Both scoped classes have zero timeout/error statuses.
These are separate populations, not a revised default-scope score. The first post-change run was
80/83 with three survivors: an isolated nonzero predecessor assertion then killed getThreadsThatHappenBefore/36
(removed Integer.intValue). The final scoped measurement includes that assertion and all five cases.

The following ledger groups every newly killed helper identity by method, mutator and baseline
status. Comma-separated numbers are individual bytecode indices; method descriptors and block
lists were also compared in the complete83-entry semantic population. No method here is overloaded.

| Method | Mutator | Bytecode indices | Baseline → post-change |
|---|---|---|---|
| copy | NonVoidMethodCallMutator | 28,36,63,71,98,101,103 | SURVIVED → KILLED |
| getPcAtRecord | NegateConditionalsMutator | 13,30 | NO_COVERAGE → KILLED |
| getPcAtRecord | NonVoidMethodCallMutator | 6,7,23,24,36 | NO_COVERAGE → KILLED |
| getPcAtRecord | PrimitiveReturnsMutator | 17,39 | NO_COVERAGE → KILLED |
| getStep | NegateConditionalsMutator | 13 | NO_COVERAGE → KILLED |
| getStep | NonVoidMethodCallMutator | 6,7,23,24 | NO_COVERAGE → KILLED |
| getStep | NullReturnValsMutator | 26 | NO_COVERAGE → KILLED |
| getThreadLocalCounter | NonVoidMethodCallMutator | 6,8,9,11 | NO_COVERAGE → KILLED |
| getThreadLocalCounter | PrimitiveReturnsMutator | 12 | NO_COVERAGE → KILLED |
| getThreadsThatHappenBefore | EmptyObjectReturnValsMutator | 56 | NO_COVERAGE → KILLED |
| getThreadsThatHappenBefore | NegateConditionalsMutator | 31,39 | NO_COVERAGE → KILLED |
| getThreadsThatHappenBefore | NonVoidMethodCallMutator | 11,26,29,30,34,36,38,44,46 | NO_COVERAGE → KILLED |
| happensBefore | BooleanFalseReturnValsMutator | 9 | NO_COVERAGE → KILLED |
| record | NonVoidMethodCallMutator | 22,29,31,32,39,43 | SURVIVED → KILLED |
