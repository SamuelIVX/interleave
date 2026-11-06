# Source statement assertions: acceptance and verification evidence

## Independent behavior and location oracles

Baseline is merged PR #114 at c19bfaf. The assertion feature adds scheduled and private boolean
checks, with distinct ASSERTION/STEP evidence and no runtime category for false predicates.
Expression errors retain their existing runtime kind/category. New kind values use existing
trace fields; legacy shapes and metadata remain supported.

For a scheduled assertion racing a writer, enumerate the three owner-respecting orders of
assert0/write-done0 and write-x1: 0,0,1 and 0,1,0 complete; 1,0 fails the assertion before done is
written. Independent hand execution gives completed x=1,done=1 versus failing x=1,done=0.
Tests assert both outcomes under DFS/static POR/DPOR/CBS, and literal failing schedule [1,0]
with ADVANCED,ASSERTION_FAILED. Complete witnesses contain exactly three actions.

For a private assertion, tentative x=7 makes assert(x==0) false although rollback retains x=0
and always(x==0) remains true. The outer attempted action is the only recorded choice; snapshots
are initial plus unchanged failed final. Fixed source text gives inner assertion span 6:1–6:17
and outer atomic span 4:1–8:2, independently counted character by character. Replay/minimization
use the actual property; removing a noise owner produces literal minimized schedule [0], x=0,
noise=0 and regenerated evidence. Wrong kind and source digest fail replay. Missing assertion
evidence cannot earn attestation through the true-property exception.

Private cutoff before assert(false) is INCOMPLETE with no attempted choice; reversing them is
ASSERTION/STEP. A prior modulo error stays RUNTIME_ERROR; a later modulo never executes after
false assertion. Initial/post-commit global failures retain INVARIANT/PROPERTY timing with no
invented assertion choice. True short-circuit assertions skip an otherwise invalid modulo.

Helper calls retain caller-local/tid/dynamic-array reads. Two repeated scheduled assertions plus
one outer action give exactly three PCs per owner; nested/private branches and loops add no
scheduler action. Contextual names preserve existing program/scalar/assignment/helper names.
Existing always/final metadata and reduction algorithms are unchanged; complete expression reads
preserve assertion truth/errors under independent reordering, with the existing finite-source
progress argument. Agreement among explorers is regression evidence, not a soundness proof.

Budget oracles: 64 repeated assertions accept and 65 reject, both scheduled and private. A balanced
conjunction with 2,500 true leaves has 2,499 binary nodes: 4,999 nodes. One additional true assertion
gives 5,000; a true property adds one and rejects 5,001. Independent leaf/operator token counting
and tree enumeration agree, without calling Java Expr.nodeCount for expected values. Existing
expression depth, source/helper and private/global budgets are unchanged.

## Fresh baseline and affected scope

Fresh merged baseline clean build/strict Javadoc and 1,472 tests pass. Four new scheduled cases
fail on the old unsupported syntax before implementation. New tests cover actual failures,
classification, null/phase/category validation, replay/minimization, attestation/serialization,
control composition, typing/syntax and inclusive work limits. Exact report Markdown expectations
are updated in both existing contract classes; no assertions are removed or weakened.

| Affected class | Baseline killed / total | First final killed / total | First final survived | Uncovered |
|---|---:|---:|---:|---:|
| SourceCompiler | 845/895 | 860/911 | 29 | 22 |
| SourceInstruction | 115/116 | 126/127 | 1 | 0 |
| FailureEvidence | 5/7 | 7/9 | 2 | 0 |
| SoundnessAttestation | 113/114 | 116/117 | 1 | 0 |
| Joint affected run | 1078/1132 | 1109/1164 | 33 | 22 |

All runs have zero timeout/memory/non-viable/run-error/equivalent statuses. The first run exposed
both old non-null guard survivors in FailureEvidence; direct null-kind/null-phase constructor
checks were added. Final affected run is **1111/1164 K + 31 S + 22 NC**: compiler 860/911,
instruction 126/127, FailureEvidence 9/9 and attestation 116/117. All 1,164 first-final identities
remain; exactly two SURVIVED→KILLED changes, every other status unchanged. Across baseline/final,
849 unchanged-method complete identities/statuses remain (class, descriptor, mutator, serialized
indexes and blocks). Changed methods relocate indexes; no all-baseline-identities-retained claim.
Final integrated 1,504 tests/build/strict Javadoc pass; scoped R6 passes, R5/R7 skip. Default, CLI and browser results are recorded below.
Frozen local reports: /private/tmp/interleave-assertions-{baseline,first-final,final}.xml.

The one new compiler survivor is block / NonVoidMethodCallMutator / index 335 / block 93, removal
of the parsed ASSERT node's empty staging path. The validated raw node's path is overwritten by
expand before runtime/export; it is not consumed by work/expression validation. Retain it as a
raw survivor with this scoped single-mutant evidence-value argument. Original ASSERT count is
zero, extending the prior non-loop reconstruction-count lemma. The prior compiler ledger in
Specs 66/71/72 remains applicable, including earlier-call DAG and original-AST preconditions.
The retained SourceInstruction label-concatenation value survivor is in Spec 71; the retained
SoundnessAttestation.checkSoundness Map.put survivor is in Spec 27. No disposition earns kill
credit or universal equivalence; raw statuses remain visible.

## Reporting/browser scope and publication

The visualizer normalizer accepts ASSERTION only at STEP with no runtime category. Four new
positive shape cases cover direct traces, serialized aliases, result and report wrappers; malformed
phase/category combinations reject. All 22 verdict checks plus structural checks pass.
The existing CI browser job now generates both source artifacts using the real Java tests and
passes their paths to the existing Playwright suite. New browser checks require the distinct kind,
phase, actual inner location, one outer attempted action and rolled-back x=0. No dependency version,
local worker/PIT resource, CI parallelism or mutation gate/filter/operator settings changed.

## Final integrated artifacts and review

Final default gate: **283/293 K + 10 S**, zero uncovered/resource/run errors, R5/R6/R7 pass with
unchanged 94% floor and 293 denominator. All 293 complete identities/statuses exactly match the
frozen Spec 72 default report. The affected baseline was rerun fresh at c19bfaf; default comparison
uses that prior recorded gate. Frozen /private/tmp/interleave-assertions-default-final.xml.
Never add default and affected populations or treat raw dispositions as kills.

Fresh final build/strict Javadoc and 1,504 tests pass with zero failures/errors/skips. The final
rerun regenerates browser artifacts from original production classes after all mutation work;
reports written inside PIT are not used as browser provenance. Commands:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc --rerun-tasks
SOURCE_TRACE_JSON=build/source-visualizer.json SOURCE_ASSERTION_JSON=build/source-assertion-visualizer.json npm test --prefix docs
```

All 22 normalization verdict cases plus structural checks pass. Playwright: **40 passed, zero
failed**, including five new checks of Java assertion evidence, visible kind/phase/verdict, actual
inner location, one outer attempted action and rolled-back x=0. Zero console errors/exceptions.
One new browser expectation initially looked for VIOLATION in metadata instead of the existing
verdict badge; it now checks each value at its actual UI location. No renderer change was needed.
Existing locked Node Playwright dependencies were installed with npm ci; no version changed.
Python Playwright was unavailable; the repository's existing Node runner supplies browser evidence.
Workflow YAML and the new artifact environment wiring parse and validate; actionlint is unavailable.

Actual CLI examples, exact store, all four strategies:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 -q run --args='--source examples/programs/statement-assertions.ilv --store EXACT --json'
./gradlew --max-workers=1 -PpitestThreads=1 -q run --args='--source examples/programs/assertion-rollback.ilv --store EXACT --json'
```

Passing assertions report PASS/four visits per strategy. The rollback example reports VIOLATION/
one visit per strategy, ASSERTION/STEP with no category. Both strict-parsed reports have
soundness=true. Local /private/tmp/interleave-assertions-{pass,rollback}-cli.json. Timing and heap
fields are not benchmarks; no speedup, whole-checker soundness or universal equivalence is claimed.

CodeRabbit CLI 0.8.1 completed all 25 files with zero findings, recorded by agent-setup. Evidence:
/private/tmp/interleave-assertions-review.ndjson. Final browser selector/test and documentation
closeout checked locally without another CLI or mutation run; production remained unchanged.
No CI/review monitoring follows publication.
