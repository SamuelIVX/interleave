# Spec: Source declaration pipeline contracts

## Objective and scope

Continue 9-V2 with the source declaration builder and its shared DslLoader.parseStateDecl call.
Add SourceDeclarationPipelineContractTest, this spec, the roadmap and a Spec 16 pointer. Keep
schema construction, source initializer rejection and the shared-validator error-remapping ledger
together because all depend on the same prevalidation guarantee.

No production, harness, dependency, mutation target/filter/operator/exclusion, denominator/floor,
heap/time or CI parallelism changes. Keep the shared validator and its defensive remapping intact.
Remaining expression-diagnostic/helper gaps stay separate; completed 9-V1 remains closed.

## Public contract and independent oracles

[Spec 16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) requires source
declarations to construct the same StateDecl/DynamicState shapes as declarative JSON, with source
names, types, literal counts and initializers validated before execution. Accepted shared types
are int, bool and int[]; locals use the same types with one initialized copy per owner.

Two accepted cases combine all shared/local kinds: signed-int endpoints, true/false, empty arrays
and arrays containing minimum/zero/maximum. The maximum is spelled either 2147483647 or
0002147483647. Populated arrays also use -0 and 000. Literal StateDecl records verify exact names,
order, types and initializers; public DynamicState getters independently verify shared values and
both owners' locals. A direct initial-state mutation then changes owner 0's middle array element
to 42 while owner 1 retains zero, checking allocation independence before any execution copy.
Expected records do not call declaration parsing, source mapping or loader
factories. Decimal spellings normalize through signedInt to Integer JSON numbers before shared
validation, rather than carrying raw decimal text into it.

Six rejected cases cover an incompatible initializer for each type in each scope. The exact
frontend diagnostic must remain type-category `Initializer for 'value' must match ...`, at line 3,
columns 20/21/22 for shared int/bool/int[], or 19/20/21 for locals. Manual declaration-prefix counts
agree with independent character enumeration. Arithmetic powers of two and independent hexadecimal
bit-pattern conversion agree on -2147483648 and 2147483647; decimal conversion confirms prefixed
zero and negative-zero expectations. Java asserts literal values, not formulas under test.

Existing SourceAcceptanceTest.declarationAndEncodingLimitsRejectOversizeInput covers accepted
32 shared declarations, eight locals, 64 array elements and 64-character names, alongside rejection
above those limits. SourceProgramShapeContractTest, SourceLiteralContractTest and
SourceLexicalContractTest retain cardinality, duplicate/range/name diagnostics. Do not duplicate
those suites merely to increase case counts. The new tests focus on the generated schema and exact
initializer mismatch diagnostics.

## Shared-validator precondition proof

The only SourceCompiler call to parseStateDecl receives a call-local JsonObject built directly
from successful declaration() results. No caller-supplied JSON, mutable external builder or raw
private AST enters this path. Against every RegistryException guard in the shared validator:

| Shared-validator requirement | Guaranteed by the unchanged source builder |
|---|---|
| State has fields/locals arrays and no unknown keys | compile creates exactly those two JsonArray members |
| Shared cardinality 1..32, locals at most eight | Required shared prefix and frontend cardinality checks before the call |
| Every declaration is an object with name/type/init only | declaration creates exactly that JsonObject triple |
| Name/type are JSON strings | addProperty receives identifier/type String values |
| Name is nonblank, at most 64, lowercase grammar | identifier checks grammar and length; reserved source words also fail |
| Names are unique across both scopes | The same names set is passed to every declaration |
| local/tid are not declaration names | Both are reserved by identifier before schema construction |
| Type is int, bool or int[] | Only int/bool keywords pass; [] is allowed only after int |
| Scalar initializer has the declared primitive type | declaration checks boolean/number shape before returning |
| Every integer is exact and in signed-int range | signedInt accepts decimal digits with optional minus and uses Integer.parseInt |
| Array initializer is an array of at most 64 exact integers | Array scan checks length and calls signedInt for every element |

Integer JSON primitives are produced from actual Java int values, so parseStrictInt sees canonical
integer text with no fraction or overflow. Boolean primitives come only from true/false tokens.
Declaration constructors copy those validated values; they add no further RegistryException
condition. Thus the shared validator cannot throw RegistryException from this original source
pipeline. Malformed source fails before the call; malformed public declarative JSON still exercises
the shared validator's errors through its own loader and remains outside this source-only claim.

All six selected offset-collection mutations alter only the lists used by the catch handler:
Token.start becomes zero, Integer.valueOf becomes null or List.add is omitted. ArrayList permits
the null value; collecting or skipping it does not alter generated declaration JSON. Those lists
have no successful-path consumer. The unchanged shared validator still accepts the schema, so the
catch stays unreachable even for each of these single mutations.

The other 22 selected mutations are entirely inside that RegistryException handler. They cannot
change successful validation or the earlier source checks. Their original NO_COVERAGE status is
retained with an unreachable-under-this-contract disposition, not converted into an assertion kill.

This is a scoped pipeline proof, not universal helper equivalence. Revisit it if the source builder,
shared validator, offset-list consumers or exception types change, or externally supplied state
JSON becomes possible. It excludes simultaneous mutations, reflected private inputs and fabricated
validator exceptions. Do not weaken the shared validator, inject invalid JSON into private compiler
state or remove defensive code to force 100%. No whole-compilation resource guarantee follows.

## Fresh baseline and selected ledger

Merged PR 106 at 90e4bf0 passes fresh clean build, strict Javadoc and 1,212 tests. Fresh compiler:
**733/787 KILLED, 32 SURVIVED, 22 NO_COVERAGE**, zero resource/run errors. All 787 identities/
statuses match Spec 64. Its 54 residuals comprise 18 existing scoped dispositions and 36 untriaged.

Select six offset-collection SURVIVED entries and 22 handler NO_COVERAGE entries, all in compile()
with one descriptor. NVMC means NonVoidMethodCallMutator, BOUNDARY means
ConditionalsBoundaryMutator and NEGATE means NegateConditionalsMutator. Other 26 residuals comprise
18 prior dispositions and eight untriaged entries outside this selection.

| Mutator / index / block | Removed call or changed condition | Baseline |
|---|---|---|
| BOUNDARY / 225 / 67 | changed conditional boundary | NO_COVERAGE |
| NEGATE / 186 / 55 | negated conditional | NO_COVERAGE |
| NEGATE / 194 / 58 | negated conditional | NO_COVERAGE |
| NEGATE / 208 / 62 | negated conditional | NO_COVERAGE |
| NEGATE / 225 / 67 | negated conditional | NO_COVERAGE |
| NVMC / 85 / 23 | Token.start | SURVIVED |
| NVMC / 86 / 24 | Integer.valueOf | SURVIVED |
| NVMC / 87 / 25 | List.add | SURVIVED |
| NVMC / 125 / 38 | Token.start | SURVIVED |
| NVMC / 126 / 39 | Integer.valueOf | SURVIVED |
| NVMC / 127 / 40 | List.add | SURVIVED |
| NVMC / 170 / 49 | Pattern.compile | NO_COVERAGE |
| NVMC / 172 / 50 | RegistryException.getMessage | NO_COVERAGE |
| NVMC / 173 / 51 | Pattern.matcher | NO_COVERAGE |
| NVMC / 178 / 52 | List.getFirst | NO_COVERAGE |
| NVMC / 180 / 53 | Integer.intValue | NO_COVERAGE |
| NVMC / 185 / 54 | Matcher.find | NO_COVERAGE |
| NVMC / 191 / 56 | Matcher.group | NO_COVERAGE |
| NVMC / 193 / 57 | String.equals | NO_COVERAGE |
| NVMC / 207 / 61 | Matcher.group | NO_COVERAGE |
| NVMC / 215 / 64 | Matcher.group | NO_COVERAGE |
| NVMC / 216 / 65 | Integer.parseInt | NO_COVERAGE |
| NVMC / 224 / 66 | List.size | NO_COVERAGE |
| NVMC / 228 / 68 | List.get | NO_COVERAGE |
| NVMC / 230 / 69 | Integer.intValue | NO_COVERAGE |
| NVMC / 236 / 71 | RegistryException.getMessage | NO_COVERAGE |
| NVMC / 239 / 72 | String.replaceAll | NO_COVERAGE |
| NVMC / 247 / 73 | SourceCompiler.error | NO_COVERAGE |

Keep every raw status visible. No kill credit, equivalence override, exclusion or denominator change
is proposed. These 28 scoped dispositions bring the ledger to 46 dispositions and eight
untriaged entries while leaving all 54 raw residuals present.

## Verification

Freeze mutations.xml before subsequent PIT invocations. Compare class, method, descriptor,
mutator, indices and blocks, then statuses; preserve all 787 identities and 733 prior kills.
Resource errors never earn kills. Scoped R6 applies; R5/R7 skip and require the separate default
full gate. Compiler, Parser, default and historical populations remain separate, with no
whole-checker closure or combined-population total.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 test \
  --tests dev.samhb.interleave.format.SourceDeclarationPipelineContractTest
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

## Results

All eight focused cases and integrated **1,220 tests** pass, zero failures/errors/skips. Build and
strict Javadoc pass. CodeRabbit CLI 0.8.1 completed all four files with one trivial finding: check
initial local-array independence before execution copies state. Added a direct owner-0 mutation
and literal owner-0/owner-1 array assertions; the integrated tests pass after that patch. No CLI
repeat for this trivial test addition. All 18 normalizer cases, relative links, credential-pattern
and diff checks pass.

Final compiler: **733/787 KILLED, 32 SURVIVED, 22 NO_COVERAGE**, zero resource/run errors. All
787 identities/statuses and all 733 prior kills are unchanged. The six selected SURVIVED and
22 selected NO_COVERAGE entries retain their raw statuses with the scoped proof above. All 54
residuals remain visible: 46 scoped dispositions (18 prior plus 28 here) and eight untriaged entries.
Scoped R6 passes; R5/R7 skip as designed. The separate default full mutation gate passes **281/291 KILLED,
10 SURVIVED**, zero uncovered/resource/run errors. All 291 identities/statuses match Spec 64; full
R5/R6/R7 pass.
Dispositions are not counted as kills; browser checks are unnecessary for unchanged application/
rendering and remote CI is unverified.
