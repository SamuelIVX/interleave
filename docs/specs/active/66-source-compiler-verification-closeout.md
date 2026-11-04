# Spec: Outer source compiler verification closeout

## Objective and scope

Resolve the eight remaining untriaged outer-SourceCompiler entries together: six observable
whole-message diagnostic marker checks and two helper invariants. Extend the existing
SourceExpressionDiagnosticContractTest, add this spec, update the roadmap and Spec 16 pointer.
This is one compiler-ledger closeout, combining related verification without adding production behavior.

No production, harness, dependency, mutation target/filter/operator/exclusion, denominator/floor,
heap/time or CI parallelism changes. Preserve completed 9-V1. This closes only the enumerated outer
compiler census; Parser's separate residuals and other frontend/helper populations remain outside scope.

## Public diagnostic characterization

SourceCompiler.diagnostic documents a whole-message marker policy: name markers take precedence,
then limit markers, then syntax markers, then the caller's fallback. Parser errors can echo malformed
assignment targets, so absence of a built-in helper message is insufficient to prove a marker
unreachable, as [Spec 59](59-source-expression-diagnostic-contracts.md) warned.

Add six public malformed assignment cases, all delimiter-complete and bounded. The exact messages
characterize the existing classifier, including markers inside echoed input:

| Malformed target | Marker | Existing category / helper message |
|---|---|---|
| x Duplicate | Duplicate | name / Invalid identifier |
| x exceed | exceed | limit / Invalid identifier |
| values[<=]tail | <= | limit / Invalid array lhs |
| x 1.. | 1.. | limit / Invalid identifier |
| x depth | depth | limit / Invalid identifier |
| x trailing | trailing | syntax / Invalid identifier |

Every diagnostic points to the effect's start at 5:5. Manual four-space indentation counts and
independent character enumeration agree. Tests assert full literal messages without calling the
classifier or a source mapper to construct expectations.

These are characterization tests of the currently documented message policy, not a claim that
malformed identifiers intrinsically represent resource-limit failures. Echoed text can influence
classification. A future structured-diagnostic change under roadmap 9-F11 should deliberately
replace this heuristic and revise these expectations with its new contract.

## Loader isolation and EOF cursor invariant

Three new cases reuse the same public SourceProgramLoader around an empty input, a truncated
thread and trailing garbage. Each invalid input must retain its literal syntax diagnostic; before
and after it, a fresh valid source compiles and replays one atomic action containing a private IF
and ASSIGN nodes. Every initial counter is zero and the true branch finishes at seven. Statement
and branch enumeration independently confirm one global instruction and the final value seven.

EOF positions are 1:1 for empty input, 1:49 for the truncated thread and 1:53 for trailing garbage.
Manual token/prefix counts and independent Python enumeration agree on these literals. No current()
helper, source-map helper or generated target supplies expected values.

For the SourceProgramLoader pipeline, every compilation constructs a fresh SourceCompiler and calls
compile once. Tokenization appends one immutable EOF token. Before the final expect("") succeeds,
position never exceeds tokens.size() - 1: successful non-EOF consumes advance at most to EOF;
failed matches do not advance; identifier/count/initializer errors stop without consuming EOF;
until stops at EOF without consuming it. The only EOF consumption is that final expect("").
After it, compile uses stored syntax/offsets and never calls current(), peek(), take() or expect().

Thus every current() read in that pipeline has position <= tokens.size() - 1. Changing the clamp's
subtraction to addition changes only its unused upper limit: Math.min still selects position.
The loader bounds source to 1,048,576 UTF-16 units before tokenization; at most one token per
unit plus EOF gives at most 1,048,577 tokens. Adding one cannot overflow int. The retained survivor
has a scoped fresh-loader cursor disposition, not universal equivalence for
arbitrary reuse of the public SourceCompiler object, reflected cursors or a second simultaneous
progress mutation. Revisit the proof if EOF consumption, cursor advancement or compiler lifecycle changes.
The public loader reuse cases check the fresh-instance boundary, without imposing new raw-compiler reuse semantics.

## Non-loop expansion count invariant

expand is called initially on the original block() tree, and recursively on original child lists.
Original node kinds are ATOMIC, IF, ASSIGN, REPEAT and WHILE. The first two expansion branches
handle REPEAT and WHILE; the final branch therefore receives only ATOMIC/IF/ASSIGN. block() gives
each of those kinds count zero, so replacing that branch's n.count() call with zero leaves the
reconstructed node unchanged.

Generated IF/LIMIT nodes and while tails are appended to output, never supplied back to expand.
In particular a generated LIMIT with a nonzero bound cannot reach this branch. Existing loop
provenance and cutoff cases in Specs 58/62, plus the new flat atomic/IF/ASSIGN execution, retain the
public consumers. This proof depends on original parsed input and a single call-removal mutation;
revisit it if expansion accepts previously expanded trees, new kinds or meaningful non-loop counts.
It does not authorize private AST injection or deletion of production fields/guards to force kills.

## Fresh baseline and selected ledger

Merged PR 107 at 0e1e8b8 passes fresh clean build, strict Javadoc and 1,220 tests. Fresh compiler:
**733/787 KILLED, 32 SURVIVED, 22 NO_COVERAGE**, zero resource/run errors. All 787 identities/
statuses match Spec 65. Of 54 raw residuals, 46 have prior scoped dispositions and eight are selected:

| Method / mutator / index / block | Baseline | Resolution |
|---|---|---|
| diagnostic / NVMC / 25 / 6 | SURVIVED | Echoed Duplicate marker |
| diagnostic / NVMC / 40 / 11 | SURVIVED | Echoed exceed marker |
| diagnostic / NVMC / 44 / 13 | SURVIVED | Echoed <= marker |
| diagnostic / NVMC / 48 / 15 | SURVIVED | Echoed 1.. marker |
| diagnostic / NVMC / 52 / 17 | SURVIVED | Echoed depth marker |
| diagnostic / NVMC / 83 / 28 | SURVIVED | Echoed trailing marker |
| current / MathMutator / 11 / 1 | SURVIVED | Scoped fresh-loader cursor disposition |
| expand / NVMC / 210 / 69 | SURVIVED | Scoped zero non-loop count disposition |

NVMC means NonVoidMethodCallMutator. Each selected method has one descriptor. Retain the two
helper entries as raw survivors; dispositions earn no kill credit or equivalence override.

## Complete outer-compiler residual ledger

The final run kills exactly the six marker entries; 48 raw residuals remain with scoped dispositions:

| Scope | SURVIVED | NO_COVERAGE | Disposition reference |
|---|---:|---:|---|
| EOF token end | 1 | 0 | [Spec 51](51-source-lexical-contracts.md) |
| Property-walker non-null input | 1 | 0 | [Spec 53](53-source-property-contracts.md) |
| Assignment-target non-null input | 1 | 0 | [Spec 55](55-source-expression-budget-contracts.md) |
| Raw-node staging fields | 8 | 0 | [Spec 56](56-source-block-contracts.md) |
| Saturating arithmetic overflow values | 2 | 0 | [Spec 57](57-source-expression-work-contracts.md) |
| Forward-fragment/target boundaries | 5 | 0 | [Spec 64](64-source-lowering-contracts.md) |
| Declaration offsets/error remapping | 6 | 22 | [Spec 65](65-source-declaration-pipeline-contracts.md) |
| Cursor/non-loop count helpers | 2 | 0 | This spec |
| **Total** | **26** | **22** | **48 raw residuals, all retained** |

Manual addition and independent row summation agree on 26 survivors plus 22 uncovered entries.
These references retain their model, metadata, lifecycle, purity and input preconditions; the
ledger is not a universal equivalence or whole-checker soundness claim. Parser's historical
Spec 61 population (275/361 killed, 75 survived, 11 uncovered) remains separate and untriaged here.
No new Parser measurement or combined-scope total is claimed. 9-V2's broader frontend work stays open.

## Verification

Freeze mutations.xml before subsequent runs. Compare class, method, descriptor, mutator, indices
and blocks, then statuses; preserve all 787 identities and 733 prior kills. Resource errors never
earn kills. Scoped R6 applies; R5/R7 skip and need the separate default full gate. Do not add
historical or overlapping compiler/helper populations together.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 test \
  --tests dev.samhb.interleave.format.SourceExpressionDiagnosticContractTest
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

## Results

All 19 focused cases pass, including nine additions. Integrated **1,229 tests**, build and strict
Javadoc pass, zero failures/errors/skips. Final compiler: **739/787 KILLED, 26 SURVIVED,
22 NO_COVERAGE**, zero resource/run errors. All 787 identities and 733 prior kills remain.
Exactly the six selected diagnostic entries become KILLED; every other status is unchanged.
Each of the 48 remaining semantic identities maps to the consolidated scoped ledger; method
descriptors are unambiguous. Scoped R6 passes; R5/R7 skip as designed.

CodeRabbit CLI 0.8.1 completed all four files with one minor premature README closure claim;
the pointer was changed to target closeout while measurements were pending. No CLI repeat for
the prose-only correction. All 18 normalizer cases, independent residual-count checks, relative
links, credential-pattern and diff checks pass. The separate default full gate passes **281/291 KILLED,
10 SURVIVED**, zero uncovered/resource/run errors. All 291 identities/statuses match Spec 65; full
R5/R6/R7 pass.
Browser checks are unnecessary for unchanged application/rendering; remote CI is unverified.
Outer-compiler closure is limited to the enumerated ledger and all referenced preconditions,
not broader 9-V2 completion.
