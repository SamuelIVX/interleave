# Spec: Aggregate source expression work

## Objective and scope

Continue9-V2 with aggregate expression occurrences across a property, guards, branches and loop
copies, including the shared capped arithmetic these callers use. Add SourceExpressionWorkContractTest,
this spec, the roadmap and a Spec16 evidence pointer. No production, harness, dependency, mutation
target/filter/operator/exclusion, denominator/floor, heap/time or CI parallelism changes. Delimiter
scanning, expansion provenance and general instruction-work/lowering closure remain separate.
Completed9-V1 stays closed.

## Contract and independent fixture matrix

[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) caps aggregate
expression occurrences at5,000 inclusive. Count assignment target and RHS, guards, both conditional
arms, repeated occurrences even if immutable ASTs are shared, and all N+1 bounded-loop checks.
Properties share the aggregate budget with owner instructions.

Eight cases use two parameterized methods. Each accepted input reaches exactly5,000; the paired
input adds one property node and must fail at the owner with Expanded expression nodes exceed5000.
This distinguishes aggregate rejection from local expression limits or instruction/expansion limits.

| Owner body | Body expression occurrences | Accepted property nodes | Rejected property nodes |
|---|---|---|---|
| One scalar assignment in atomic | target1+RHS1=2 | 4,998 | 4,999 |
| Guarded atomic assignment | guard1+target1+RHS1=3 | 4,997 | 4,998 |
| if with one assignment per arm | check1+then2+else2=5 | 4,995 | 4,996 |
| while limit0, empty body | final check1 | 4,999 | 5,000 |
| while limit1, one atomic assignment | checks2+body2=4 | 4,996 | 4,997 |
| repeat64 of empty if | 64 copies of check1 | 4,936 | 4,937 |
| repeat1 of integer assignment with2,500 RHS leaves | 1*(RHS4,999+target1)=5,000 | absent | true1 |
| repeat2 of integer assignment with1,250 RHS leaves | 2*(RHS2,499+target1)=5,000 | absent | true1 |

Boolean/integer trees are balanced; odd node counts use a full binary tree, even counts add one
unary node. Python direct-tree counts agree with the full-binary2L-1 identity and the literal
matrix sums5,000/5,001. Maximum local depth is14 and each local expression has at most5,000 nodes;
the RHS trees are depth13/12. Inputs stay well below1MiB. The64-copy case reaches the existing
instruction cap exactly and the PIT ancestry budget remains unchanged. All fixtures are bounded,
typed source; no hostile expansion or resource-failure probe. Java expected counts are literal
fixture data, not production Expr.nodeCount/expressionWork calculations.

One header/shared line places the owner at3:1; independent line enumeration and manual prefix
counts agree. Require the exact aggregate diagnostic there, not a later/earlier rejection. Parsing
has already constructed the bounded AST before source expression validation.

## Fresh baseline and reproduction

Merged PR98 atb583751 passes the clean build, strict Javadoc and1,115 tests, zero failures/errors/
skips. Fresh outer-compiler baseline is **685/787 KILLED,71 SURVIVED,31 NO_COVERAGE**, zero
resource/run errors. All18 selected expressionWork/arithmetic/property-count entries are SURVIVED.
Other84 raw residuals comprise11 existing scoped dispositions and73 untriaged entries outside
this selection. No whole-compiler/checker closure is implied.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before later PIT runs. Compare class, method, descriptor, mutator, indices
and blocks, then statuses; retain all787 identities and all685 baseline kills. Scoped R6 applies;
aggregate R5/R7 skip and need a separate full-default run. Compiler-only results are distinct from
overlapping combined/historical reports. No resource failure earns a kill.

## Selected mutation ledger

NVMC means NonVoidMethodCallMutator, MATH means MathMutator and BOUND means
ConditionalsBoundaryMutator. All entries are baseline SURVIVED against unchanged SourceCompiler
bytecode; each named method has one descriptor. The first16 ledger entries are KILLED in the
final run; the final two remain raw SURVIVED.

| Method /mutation /index /block | Contract or disposition |
|---|---|
| compile /NVMC Expr.nodeCount /427 /123 | Property nodes share the aggregate budget |
| expressionWork /MATH /53 /21 | Assignment target and RHS add rather than subtract |
| expressionWork /MATH /88 /31 | While has N+1 checks even for zero iterations |
| expressionWork /NVMC Node.test /22 /5 | Control/guard expression remains counted |
| expressionWork /NVMC Expr.nodeCount /30 /9 | Control/guard node count remains counted |
| expressionWork /NVMC Expr.nodeCount /52 /20 | Assignment target remains counted |
| expressionWork /NVMC Node.count /85 /30 | While check multiplier retains the bound |
| expressionWork /NVMC cappedMultiply /91 /31 | While checks contribute their multiplied count |
| expressionWork /NVMC Node.count /93 /32 | While body multiplier retains the bound |
| expressionWork /NVMC cappedMultiply /97 /33 | While body expressions contribute |
| expressionWork /NVMC cappedAdd /99 /34 | While check/body contributions are combined |
| expressionWork /NVMC expressionWork /109 /37 | The unchosen arm contributes |
| cappedMultiply /BOUND /20 /3 | Multiplier equal to its cap is admissible when product fits |
| cappedMultiply /BOUND /24 /4 | Multiplicand equal to its cap is admissible when product fits |
| cappedMultiply /BOUND /30 /5 | Product exactly at the cap is admissible |
| cappedMultiply /MATH /41 /7 | Actual product is not replaced by division |
| cappedAdd /MATH /14 /2 | Scoped overflow-value disposition below |
| cappedMultiply /MATH /28 /5 | Scoped overflow-value disposition below |

## Scoped overflow-value dispositions

Keep the final two entries raw SURVIVED, not killed. They can return a different positive over-cap
number instead of exactly cap+1, so this is **not helper return-value equivalence**. The claim is
limited to current source-admission consumers under each single mutation's unchanged callers.

Normal inputs are nonnegative, with private caps64 or5,000. Multiply handles either zero operand
first; an already-over-cap operand otherwise takes an unchanged early clamp. When both operands
are within the cap, their sum is at most10,000 and product at most25,000,000, safely within long.
The weakened subtraction/division threshold still computes the same exact in-range sum/product;
only over-cap results may differ. Thus both retain zero, every exact in-range value and the
positive-over-cap class. No arithmetic wraparound is introduced within these caller domains.

Callers either feed results into same-cap capped arithmetic, test against that cap, or test work
for zero before empty-repeat elimination. The64-instruction and5,000-expression count streams do
not mix helper results. Compile rejects over-cap work before relevant expansion; atomic/private
work likewise tests64, and expression totals test5,000. Diagnostics use fixed cap messages, not
the internal overflow number; no public count report exports it. Zero repetition still annihilates
an over-cap body. Admission and empty-work decisions therefore remain the same for these two
mutations under the supported pipeline.

Retain production saturation checks. Revisit for configurable/larger caps, signed inputs, raw
helper results becoming observable, cross-cap numeric reuse or new callers. No equivalence is
claimed for reflection, arbitrary private arguments, combined mutants or future observers.

## Verification

The clean build, strict Javadoc and **1,123 tests** pass, zero failures/errors/skips. The eight new
cases use two parameterized methods. Final compiler is **701/787 KILLED,55 SURVIVED,31 NO_COVERAGE**,
zero TIMED_OUT, MEMORY_ERROR, RUN_ERROR, NON_VIABLE or other statuses. All787 identities and all685
baseline kills remain. Exactly16 selected SURVIVED entries become KILLED; every other status is
unchanged. expressionWork has no residual mutations in this outer-class report. Scoped R6 passes;
aggregate R5/R7 skip. Remaining86 raw non-kills comprise13 scoped dispositions (11prior+2arithmetic)
and73 untriaged entries. Dispositions remain raw survivors, not assertion kills.

All18 normalizer checks, relative file links, credential-pattern and diff checks pass. Initial
CodeRabbit CLI0.8.1 completed all four changed files with zero findings. Only final evidence/status
prose followed; no test/production logic changed. Separate full-default281/291 KILLED,10 SURVIVED passes, zero uncovered/resource/run errors;
all291 identities/statuses match Spec56. Full R5/R6/R7 pass. Browser checks are skipped
because production/report output is unchanged; remote CI is unverified. No whole-compiler resource
guarantee or whole-checker verification closure is claimed.
