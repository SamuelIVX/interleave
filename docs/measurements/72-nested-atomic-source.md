# Nested atomic source: independent acceptance and verification

## Semantics and numeric oracles

Baseline is merged PR #113 at eb6db82. The change flattens unguarded inner atomic bodies during
parsing; only the outer action remains. It creates no runtime transaction, instruction kind,
observation point or scheduling transition. Existing private execution and footprint derivation
consume the same statements as manually flattened source, with original inner diagnostic spans.

The helper/caller fixture loads counter into owner-local saved, increments counter and writes
values[tid]=tid+10 within one outer action. Enumerating the two serial owner orders gives terminal
counter 2 and values [10,11]. Local saved values distinguish terminal configurations: order 0,1
leaves [0,1], while 1,0 leaves [1,0]. There are five complete states: root, two first-action states,
two terminal states; projecting PCs alone gives four positions. A separate Python transition
enumerator over (pc0,pc1,counter,saved0,saved1,value0,value1) independently confirms five/four.
Java asserts literal five DFS visits, two actions, counter 2 and [10,11]. Footprints also match
manually flattened source for each owner; all four exact strategies replay actual witnesses.

An inner close after counter=9 cannot trigger always(counter==0): a later private reset restores
zero before the sole outer commit. Driver snapshots number two (initial/final), with equal values.
A nested repeat/while/else fixture increments twice, then once, yielding literal counter 3 in one
action; int-max repetition of empty inner blocks contributes zero work without iterating the count.

Depth counts program + owner + 14 atomic bodies = 16 accepted levels; a fifteenth body rejects at
literal source line 18, column 8 (its opening brace). Two repeated groups of 32 assignments give
64 private instructions; one additional assignment gives 65 and rejects. Inner braces contribute
zero. The existing helper-definition check also rejects 65 private assignments in an unused,
zero-repeat helper branch. Expression counting uses the [Spec 71 derivation](71-source-helpers.md):
1,250 literals + 1,249 binary operators + one target = 2,500 nodes. Two nested assignments total
5,000; literal true property adds one, rejecting 5,001. Independent token/tree counts agree.

Runtime modulo error and true zero-limit loop both point at original inner line 8, column 1.
Runtime failure retains one outer attempted action spanning 4:1–11:2, STEP/MODULO_BY_ZERO, and
counter 0 after rollback; minimization regenerates matching evidence. Cutoff retains no attempted
schedule entry, outer PC 0, LOOP_LIMIT with zero iterations and counter 0. Tests cover all four
strategies. Guarded nesting remains rejected in live, unchosen, zero-repeat and unused-helper
source. Only the outer entry guard controls enabledness, including genuine deadlock when false.

## Verification

Fresh merged baseline clean build/strict Javadoc and 1,443 tests pass. Compiler baseline:
841/891 KILLED + 28 SURVIVED + 22 NO_COVERAGE, zero resource/run errors. The 29 new cases initially
fail against the old parser's deliberate nested-atomic rejection, then pass after implementation.
One old negative fixture deliberately changes from unguarded nesting (now supported) to guarded
nesting (still unsupported); other malformed-source cases remain intact.

Final integrated build/strict Javadoc and 1,472 tests pass, zero failures/errors/skips. Compiler
final 845/895 KILLED + 28 SURVIVED + 22 NO_COVERAGE, zero resource/run errors; scoped R6 passes,
R5/R7 skip. Before block: 115 K + 7 S; after: 119 K + 7 S. Every other method retains all 769
complete semantic identities/statuses (class, descriptor, mutator, indexes and blocks). Changed
block indexes relocate, so this is not an all-891-identities-retained claim. All four additional
block mutations kill; the seven retained block entries have the existing Spec 71/56 staging-field
arguments. In particular flattening does not create a new private node kind or meaningful raw
count/path/no-list: earlier helpers remain validated and only original control/effect nodes reach
expansion. The unchanged compiler methods retain their prior scoped residual assumptions.
No target/test-filter/mutator/exclusion/floor/PIT-resource/CI-parallelism changes. Retain raw
non-kills; no resource error earns kill credit. Compiler/default populations are never added.
Frozen local reports: /private/tmp/interleave-nested-{baseline,final}.xml.

All 18 visualizer normalization cases pass. Browser rendering is not rerun because schema and
renderer are unchanged; the feature retains the existing outer-action metadata and terminal
inner-location fields. Default gate passes at 283/293 K + 10 S, zero uncovered/resource/run errors;
R5/R6/R7 pass with the unchanged 94% floor and 293 denominator. All 293 complete identities/statuses
match the frozen Spec 71 default report; that prior report predates its trivial nonempty-trace
assertion follow-up, while this final default run uses the complete merged code and new tests.
Frozen final report: /private/tmp/interleave-nested-default-final.xml. The affected compiler baseline
was rerun fresh on merged eb6db82 before implementation. These populations remain separate.

The actual CLI command is:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 -q run --args='--source examples/programs/nested-atomic.ilv --store EXACT --json'
```

Strict parsing of /private/tmp/interleave-nested-cli.json confirms four exact rows, all PASS,
with five visits each (DFS/static POR/DPOR/CBS K=2), and soundness=true. Timing/heap fields are
not used as benchmarks; no speedup is claimed. The teaching acceptance test also loads this tenth
source example across strategies/stores and validates reporting/attestation with its source model.

CodeRabbit CLI 0.8.1 completed all 15 changed files with zero findings; raw evidence is local
/private/tmp/interleave-nested-review.ndjson. The final documentation clarifies outer-action
wording and records completed results. Build/strict Javadoc pass after that clarification.
No repeat CLI or mutation run for this documentation-only closeout.

## Retained block residual mapping

All entries below are raw SURVIVED under NonVoidMethodCallMutator in block `(ZI)List`.
The [Spec 56 staging arguments](../specs/active/56-source-block-contracts.md) and
[Spec 71 CALL-path argument](71-source-helpers.md) still apply to validated source and single
mutants; flattening inner atomic syntax introduces no new private node variant.

| Index / block | Parsed staging field | Scoped reason |
|---|---|---|
| 122 / 37 | Outer ATOMIC path | Original path is overwritten during expansion. |
| 185 / 53 | IF path | Original path is overwritten during expansion. |
| 226 / 63 | REPEAT end offset | Repeat wrapper is eliminated; iteration provenance uses its start offset. |
| 227 / 64 | REPEAT path | Original path is overwritten during expansion. |
| 277 / 77 | WHILE path | Original path is replaced by generated iteration ancestry. |
| 402 / 112 | CALL path | Original path is replaced by caller expansion ancestry. |
| 496 / 139 | ASSIGN path | Original path is overwritten during expansion. |

No kill credit, exclusions or universal equivalence claim is attached to these dispositions.
Revisit when staging/expansion consumes original paths/end offsets or accepts previously expanded
or fabricated ASTs. All 28 compiler survivors and 22 uncovered entries remain visible; residuals
outside block keep their earlier per-contract assumptions.
