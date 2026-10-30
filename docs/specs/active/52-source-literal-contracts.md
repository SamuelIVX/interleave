# Spec: Source literals and declaration diagnostics

## Objective and scope

Continue9-V2 with SourceCompiler's count, signedInt and declaration error contracts. Keep this
focused bundle to SourceLiteralContractTest, this spec, the roadmap and a Spec16 evidence pointer.
No production code, harness, dependency, target/filter/operator/exclusion, denominator/floor,
heap/time settings or CI parallelism change. Expression validation, expansion/work arithmetic,
lowering and shared-validator diagnostic remapping remain separate follow-ups.

## Existing contract and independent fixtures

[Spec16.01](16-concurrent-program-parser/01-language-and-semantics.md) requires literal decimal
counts, signed32-bit integer initializers, int/bool scalar types, int arrays only, and unique
names across shared/local declarations. [Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md)
owns categorized load errors at original one-based UTF-16 coordinates.

Add14 cases in two parameterized methods through the public SourceProgramLoader:

- Reject negative and boolean count tokens with syntax diagnostics; reject2147483648 with a
  limit diagnostic. Use invalid thread-ID literals to reach count before owner-ID validation or
  any loop expansion. Existing huge-empty-repeat cases already protect accepted Int.MAX counts.
- Reject nonnumeric scalar initializers, including after unary minus, and a boolean inside an
  integer array with syntax diagnostics. Scalar true takes a different type-mismatch path, so
  the array case deliberately reaches signedInt's integer-token contract.
- Reject2147483648 and-2147483649 integer initializers with type diagnostics. Existing source
  arithmetic tests already load the inclusive signed-int endpoints; retain those cases rather
  than duplicating them. These literals are one beyond each boundary of [-2^31,2^31-1], independently
  confirmed by Python shifts and boundary addition, not recomputed in the Java tests.
- Reject unknown shared/local types at the offending type token, and bool[] at its closing type
  bracket, before initializer/state validation can mask the guard.
- Reject shared/shared, shared/local and local/local duplicate names at the second identifier.

The declaration fragments begin at line2 column1; count fixtures put the invalid token at line3
column9. Manual prefix counts and Python character enumeration independently give:

| Token | Literal line/column oracle |
|---|---|
| Unknown shared/local type | 2:8 /3:7 |
| Closing bracket of bool[] type | 2:13 |
| Scalar initializer /digits after minus | 2:22 /2:23 |
| Boolean inside integer array | 2:24 |
| Repeated shared /cross-scope /local identifier | 3:12 /3:11 /4:11 |

Assert exact category/message/coordinates and RegistryException, not an incidental runtime
exception. Expected diagnostics do not use the production source map or token helpers.

## Fresh baseline and reproduction

Merged PR93 at5431db3 passes the clean build, strict Javadoc and1,052 tests, zero failures/errors/
skips. Fresh outer-compiler baseline is **603/787 KILLED,125 SURVIVED,59 NO_COVERAGE**, zero
resource/run errors. Fifteen non-kills belong to count/signedInt/declaration: two SURVIVED and
13 NO_COVERAGE. The other169 raw non-kills include Spec51's one scoped EOF-end disposition and
168 entries untriaged outside this bundle. No whole-compiler/checker closure is implied.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before subsequent PIT runs. Compare class, method, descriptor, mutator,
bytecode indices and blocks, then statuses. Retain all787 identities and all603 prior kills.
Scoped R6 applies; aggregate R5/R7 skip and need a separate default run. This is outer compiler
only, not Spec50's overlapping1,465 scope or the historical1,491 population.

## Selected mutation ledger

All entries use NonVoidMethodCallMutator and the unchanged SourceCompiler bytecode. Each named
method has one descriptor. S means SURVIVED; NC means NO_COVERAGE. Every selected entry is KILLED in the final run.

| Method / removed call /index /block | Baseline | Contract |
|---|---|---|
| count /Token.start /15 /4 | S | Invalid count points to its token |
| count /Token.start /44 /10 | NC | Overflow count points to its token |
| count /error /47 /11 | NC | Overflow produces categorized limit failure |
| signedInt /Token.start /20 /5 | NC | Nonnumeric initializer location |
| signedInt /error /23 /6 | NC | Nonnumeric initializer produces syntax failure |
| signedInt /Token.start /59 /14 | NC | Overflow initializer location, including after minus |
| signedInt /error /62 /15 | NC | Overflow initializer produces type failure |
| declaration /current /24 /7 | NC | Unsupported type uses the actual current token |
| declaration /Token.start /25 /8 | NC | Unsupported type location |
| declaration /error /28 /9 | NC | Unsupported type produces type failure |
| declaration /previous /46 /16 | NC | bool[] failure uses its closing type bracket |
| declaration /Token.start /47 /17 | NC | bool[] closing-bracket location |
| declaration /error /50 /18 | NC | bool[] produces type failure |
| declaration /Token.start /61 /22 | S | Duplicate identifier location |
| declaration /error /78 /26 | NC | Duplicate identifier produces name failure |

## Verification

The clean build, strict Javadoc and **1,066 tests** pass with zero failures/errors/skips. The14 new
cases use two parameterized methods. Final compiler is **618/787 KILLED,123 SURVIVED,46 NO_COVERAGE**,
zero TIMED_OUT, MEMORY_ERROR, RUN_ERROR, NON_VIABLE or other statuses. All787 identities and all603
baseline kills remain. Exactly15 entries become KILLED: count1S+2NC, signedInt4NC, declaration1S+7NC.
Every other status is unchanged. All selected error paths are now covered with assertion-backed
outcomes; count, signedInt and declaration have no residual mutations in this outer-class report.
Scoped R6 passes; aggregate R5/R7 skip. Remaining169 raw compiler non-kills include the existing
scoped EOF-end disposition and168 untriaged entries; no new equivalence claim is introduced.

All18 normalizer checks, relative file links, credential-pattern and diff checks pass. CodeRabbit
CLI0.8.1 completed all four changed files with zero findings. Only a thread-ID parameter-comment
clarification and final evidence prose followed the initial review, with no test-logic changes.
Separate full-default281/291 KILLED,10 SURVIVED passes, with zero uncovered/resource/run errors;
all291 identities/statuses match Spec51. Full R5/R6/R7 pass. Browser checks are skipped because production/report
output is unchanged; remote CI is unverified.
