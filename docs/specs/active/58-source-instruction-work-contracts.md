# Spec: Global and private source instruction work

## Objective and scope

Continue9-V2 with the shared work calculation at both admission consumers: global source steps
and private instructions inside an atomic action. Add SourceInstructionWorkContractTest, this
spec, the roadmap and a Spec16 evidence pointer. No production, harness, dependency, mutation
target/filter/operator/exclusion, denominator/floor, heap/time or CI parallelism changes. Delimiter
scanning, expansion ancestry and general forward-target/lowering proof remain separate.
Completed9-V1 stays closed.

## Contract and independent fixtures

[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) makes global and
atomic-internal instruction caps64 inclusive. Count every conditional, both arms even when a
condition is constant, and all N+1 bounded-while checks plus N body occurrences. A private body
shares these counting rules but its enclosing atomic remains one global action.

Four cases use one parameterized method, pairing exactly64 with65:

| Scope /control | Accepted conservative work | Rejected work | Accepted global steps | Error location |
|---|---|---|---|---|
| Global if | condition1+then31+else32=64 | one extra else atomic=65 | 64 | owner3:1 |
| Global while limit1 | checks2+body62=64 | one extra body atomic=65 | 64 | owner3:1 |
| Private if | condition1+then31+else32=64 | one extra else assignment=65 | 1 | enclosing atomic4:1 |
| Private while limit1 | checks2+body62=64 | one extra body assignment=65 | 1 | enclosing atomic4:1 |

Global statements are empty atomics, private statements are typed counter=0 assignments. No
optimization can silently replace the specified conservative budget with only the taken arm's
executed work. Assert both successful loading and the literal global instruction count; over-cap
inputs require the exact limit category/message/position. Global rejection must occur at the owner;
private rejection must occur at the enclosing atomic, not at an inner control or arbitrary source
start. This protects work's consumers as well as its arithmetic.

Manual instruction enumeration and an independent Python list count agree with the closed forms
1+31+32 and(1+1)+1*62. The extra statement changes only the body contribution by1. Source-prefix
enumeration confirms the owner at3:1 and atomic at4:1; Java does not derive expected locations or
counts from production work/source-map helpers. Maximum source depth is4, expressions are shallow,
aggregate expressions are far below5,000, and source text is small. While bounds are1 and no huge
expansion is involved. Existing repeat/boundary tests remain intact.

## Fresh baseline and reproduction

Merged PR99 at02f732d passes the clean build, strict Javadoc and1,123 tests, zero failures/errors/
skips. Fresh outer-compiler baseline is **701/787 KILLED,55 SURVIVED,31 NO_COVERAGE**, zero
resource/run errors. All six selected work/private-error-location entries are SURVIVED. Other80
raw residuals comprise13 existing scoped dispositions and67 untriaged entries outside this
selection. No whole-compiler/checker closure is implied.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before later PIT invocations. Compare class, method, descriptor, mutator,
indices and blocks, then statuses; retain all787 identities and all701 baseline kills. Scoped
R6 applies; aggregate R5/R7 skip and need a separate full-default run. This is outer compiler only,
distinct from overlapping combined/historical populations. No resource failure earns a kill.

## Selected mutation ledger

All entries are NonVoidMethodCallMutator against unchanged SourceCompiler bytecode. Each named
method has one descriptor. All six changed from SURVIVED to KILLED; no new dispositions are proposed.

| Method /removed call /index /block | Contract |
|---|---|
| work /work /58 /15 | The unchosen conditional arm contributes |
| work /cappedAdd /60 /16 | Both conditional arm counts are combined |
| work /cappedAdd /62 /17 | Conditional check and arm counts are combined |
| work /Node.count /83 /23 | While body work retains its repetition factor |
| work /cappedMultiply /87 /24 | While body occurrences contribute alongside checks |
| expand /Node.start /182 /59 | Private-budget error keeps the enclosing atomic location |

## Verification

Clean build, strict Javadoc and **1,127 tests** pass with zero failures/errors/skips. Final outer
compiler is **707/787 KILLED, 49 SURVIVED, 31 NO_COVERAGE**, zero resource/run errors. All787
semantic identities and all701 prior kills remain; exactly the six selected survivors became
kills, and all other statuses are unchanged. Remaining80 raw residuals comprise13 existing
scoped dispositions and67 untriaged entries. Scoped R6 passes; R5/R7 skip as designed.

Initial CodeRabbit CLI0.8.1 review completed across all four files with zero findings; only final
status prose and Javadoc spacing followed. Default full PIT passes **281/291 KILLED, 10 SURVIVED**, all291 identities/statuses unchanged
from Spec57 and zero uncovered/resource/run errors. Full R5/R6/R7 pass. All18 normalizer cases, relative Markdown
links, credential-pattern scan and diff whitespace checks pass. Browser testing is skipped because
application/report behavior is unchanged; remote CI is unverified.
No whole-compiler resource guarantee or whole-checker verification closure is claimed.
