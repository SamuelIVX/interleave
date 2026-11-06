# Source cutoff simplification: independent oracles and verification

## Scope and unchanged meaning

Selected active 9-F9 follows merged PR #115 and roadmap-only main b3d1a9d. Dynamic threads and
other deferred/conditional items stay parked. The new CutoffSimplifier removes source schedule
choices while retaining the entire original CutoffEvidence value. It returns INCOMPLETE, not a
failure or completeness proof. Ordinary DeltaDebugger behavior is unchanged.

The operation authenticates the original and every accepted candidate through existing source
execution/replay. Candidate prefixes must stay nonterminal under the supplied actual property.
Loop witnesses execute their omitted action on scratch; scheduling witnesses retain the original K,
actual enabledness/preemption cost and omitted source identity without executing that action.
Outcomes, PCs, labels and ancestry regenerate after deletion; CLI snapshots also regenerate.

## Independent numeric and minimality oracles

Four unrelated noise increments before a root cutoff give prefix [0,0,0,0]; no producer is needed
for a literal true zero-limit loop in owner 1, so the independent minimal prefix is []. Snapshot
count falls from five to one, noise from four to zero, with exactly the same omitted evidence.
A producer enabling an outer guard cannot be deleted. One bounded-loop check/body pair is needed
to reach omitted PC 2/limit 1/iteration 1: prefix [0,0,0,1,1] becomes literal [1,1]. A separate
three-choice fixture [0,1,0] needs both owner-0 actions; coarse deletion fails, but singleton deletion
removes middle noise to yield [0,0]. Both prevent claiming minimality from coarse chunks alone.

With both owners having four unconditional actions, independently count preemptions as switches
away from an owner that remains enabled. At K=0, [0,0] plus omitted owner 1 costs one; [0] retains
that cutoff, while [] costs zero and cannot. At K=1, [0,0,1,1] has cost one, then omitted owner 0
adds one. The target must remain at PC 2, so [0,0,1] is the unique shortest thread-choice sequence
among subsequences (duplicate identical index selections ignored). Its prefix cost is one and
attempted cost two. Independent Python enumeration of every subsequence confirms minima 1 and 3;
manual count supplies the second route. A terminated last owner makes the switch free and rejects
forged scheduling evidence. A prefix already beyond K also rejects.

The result is deletion-1-minimal, not universally globally shortest. Tests independently attempt
each remaining single deletion using existing execution/replay, and assert fixed literal outcomes
above. Additional regressions retain writes avoiding earlier assertions, modulo errors and an
intermediate global-property failure; stale digest, wrong outcomes, changed allowance, missing
source evidence and unsupported verdicts reject. Reuse is isolated; original evidence is immutable.

## Finite search and resource findings

The initial new-class run was 41/49 K + 6 S + **2 TIMED_OUT**. Neither timeout earns kill credit;
scoped R6 correctly failed. They were refinement multiplication→division (index 190/block 39)
and a negated final-level comparison (index 180/block 37) in simplify. The old open-ended refinement
was replaced, without changing mutation timeouts/resources or filters:

- At most N successful deletions are possible from an N-choice original. A long counter permits
  N+1 passes, including a final minimality pass, with no recursive simplification stack.
- Chunk levels are distinct min(N, 2^p), p=1 through 31, sufficient for every int-sized Java list.
  For N=0,1,3,5,512 these are [0], [1], [2,3], [2,4,5] and [2,4,8,16,32,64,128,256,512].
- Adopt only strictly shorter candidates. Every accepted restart therefore decreases length.

Second production population is different: **47/54 K + 7 S**, zero uncovered/timeout/memory/
non-viable/run-error/equivalent statuses; scoped R6 passes, R5/R7 skip. No assertion of retaining
all 49 old identities across the production rewrite, or adding populations, is made. The class did
not exist on merged main, so it has no prior baseline mutation population. Raw local reports:
/private/tmp/interleave-cutoff-{first,second,final}-scoped.xml. The first contains the failed resource run.

Manual review found a counterexample to the witness-null guard disposition: with a legacy Program
and null witness, removing requireNonNull changes NPE into IllegalArgumentException through the
non-source guard. A direct validation-order assertion kills it. Final: **48/54 K + 6 S**, zero
uncovered/resource/run errors. All 54 identities remain; exactly that one S→K change, every other
status unchanged. This is a real missing input-contract case, not a disposition or resource kill.

## Complete retained new-class ledger

All six raw survivors are in simplify with descriptor
`(Program,Trace,Invariant)Trace`; exact fully qualified descriptor is retained in the frozen XML.
NVMC means NonVoidMethodCallMutator; boundary is ConditionalsBoundaryMutator.

| Mutator / index / block | Scoped disposition |
|---|---|
| boundary / 81 / 20 | remaining>=0→>0 omits only the final extra pass. Reaching that pass requires N successful strict deletions, so best is already empty; N=0 also starts with an authenticated empty witness. |
| boundary / 188 / 38 | candidate.length<length→<=length has the same truth value: real candidate choices delete a nonempty chunk, so their length is strictly smaller. |
| MathMutator / 227 / 46 | remaining subtraction→addition changes only a redundant pass ceiling under otherwise-correct strict progress/no-progress break. At most N reductions and one final pass occur; the long counter cannot overflow from an int-sized N. Keep the ceiling to bound progress-fault variants. |
| NVMC / 5 / 0 | Removing program requireNonNull still throws NPE at program.isSource. Exception-type contract only; exact diagnostic message/stack preservation is not claimed. Retain the named guard. |
| NVMC / 186 / 37 | Removing candidate.length substitutes zero. The guard is reached only at positive current length and a genuinely smaller candidate, so both actual and zero lengths pass. |
| NVMC / 237 / 47 | Final replay repeats authentication: original validated before deletion; every adopted candidate validated; an unchanged best retains that evidence. Scope requires deterministic source steps and pure predicates; no callback-count/resource/universal equivalence claim. Retain final validation. |

No disposition is a kill, equivalent override or justification for deleting guards. Model/metadata
and purity assumptions are explicit; revisit if candidates stop being strict subsequences, state
or evidence becomes mutable, or predicate/step behavior is not replay-deterministic.

## Integrated checks and artifacts

Fresh b3d1a9d baseline clean build/strict Javadoc and 1,504 tests pass; default 283/293 K + 10 S,
zero uncovered/resource/run errors, full R5/R6/R7 pass. The successful CLI regression first failed
against the old unknown flag. Final integrated build/strict Javadoc and 1,522 tests pass (18 new
cases), zero failures/errors/skips. The thirteenth source teaching example also runs through all
existing strategy/store reports. Main flag handling is checked by real child JVMs; it is not
claimed as mutation-covered child-process bytecode.

--simplify-cutoff requires one source model, JSON, exact store and explicit strategy. It outputs
one direct trace only for an INCOMPLETE selected run; an existing VIOLATION or absent actual
cutoff is a clear error. Normal reports remain unchanged. Existing direct-schema browser loading
is reused; a fresh actual CLI artifact is wired into the existing browser CI job without changing
parallelism or dependency versions. Workflow YAML and artifact producer/environment wiring validate.
All 22 existing normalization verdict cases plus structural checks pass.

## Final verified results and review

Final default **283/293 K + 10 S**, zero uncovered/resource/run errors. Full R5/R6/R7 pass with
unchanged 94% floor and 293 denominator; all 293 complete semantic identities/statuses exactly match
the fresh merged baseline. The completed log is /private/tmp/interleave-cutoff-default-final.log;
frozen XML is /private/tmp/interleave-cutoff-default-final.xml. This is a completed gate, not a
pending run. The evidence note and active spec report the same measured status. Default and
new-class scopes remain separate; never add them or credit a timeout.

Fresh final integrated build/strict Javadoc and all 1,522 tests pass. The browser artifacts were
regenerated by normal tests after mutation work. Complete Playwright suite: **45 passed, zero
failed**, zero console errors/exceptions. Five new assertions validate actual CLI direct-trace
loading, INCOMPLETE verdict, empty-prefix one-row timeline, omitted source location and fresh root
noise=0. All 22 normalization verdict cases plus structural checks pass. No viewer schema/renderer
change was necessary. Workflow YAML/producer/environment wiring validates; actionlint unavailable.

Actual Main comparisons use the same source bytes and exact strategy/store selection before and
after enabling --simplify-cutoff:

| Model / strategy | Original representative prefix | Simplified prefix | Snapshots after | Unchanged reason / bound |
|---|---:|---:|---:|---|
| examples/programs/cutoff-simplification.ilv / DFS | 4 | 0 | 1 | LOOP_LIMIT / 0 |
| Two four-action owners / CBS K=0 | 3 | 1 | 2 | PREEMPTION_BOUND / 0 |

The CBS original length three is an observed representative, not a universal count. Independent
subsequence/cost analysis establishes the one-choice minimum for this target. Both comparisons
assert full CutoffEvidence equality, equal exact source digests, INCOMPLETE and aligned actual
outcomes/metadata/snapshots. Normal report soundness=true remains intact before simplification.
A direct witness is not a benchmark report or a claim about unexplored behavior. Timing/heap fields
are not benchmarks; no search speedup or global shortest-schedule claim is made.

```sh
./gradlew --max-workers=1 -PpitestThreads=1 -q run --args='--source examples/programs/cutoff-simplification.ilv --store exact --strategy DFS --json --simplify-cutoff'
```

Preemption fixture: program switches { shared int noise = 0; thread 0 { repeat 4 { atomic {} } }
thread 1 { repeat 4 { atomic {} } } }. Run with --strategy CONTEXT_BOUNDED --max-preemptions 0;
all other flags above stay the same. Local raw /private/tmp/interleave-cutoff-{loop,preemption}-cli-{before,after}.json.
Browser log /private/tmp/interleave-cutoff-browser.log. Existing locked Node Playwright dependencies
were installed with npm ci, with no version change.

CodeRabbit CLI 0.8.1 completed all 15 files. Two minor findings both identified the old pending
sentence conflicting with the completed default gate; frozen XML and the successful log confirm
the PASS above, and both documents now agree. One trivial finding documents the browser fixture
write and creates its parent directory. No production change follows the final mutation runs.
Follow-up integrated/browser checks cover these changes; no repeated CLI review for minor feedback.
Raw review /private/tmp/interleave-cutoff-review.ndjson. Stop after publication; no CI/review watchers.
