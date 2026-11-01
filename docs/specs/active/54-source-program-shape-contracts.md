# Spec: Source program shape and expectation metadata

## Objective and scope

Continue9-V2 with required source sections, declaration/owner cardinality, sequential owner IDs
and optional expected-verdict labels. Add SourceProgramShapeContractTest, this spec, the roadmap
and a Spec16 evidence pointer. No production, harness, dependency, mutation target/filter/operator/
exclusion, denominator/floor, heap/time or CI parallelism changes. Expression/depth/delimiter/work/
lowering and shared-validator remapping remain separate follow-ups. Completed9-V1 stays closed.

## Existing contract and independent oracles

[Spec16.01](16-concurrent-program-parser/01-language-and-semantics.md) requires shared declarations
before threads, sequential owner IDs from zero, and optional exact PASS/VIOLATION/DEADLOCK labels.
[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) makes shared1–32,
local0–8 and thread1–8 limits inclusive and requires source-located categorized failures.

Add14 cases across nine test methods:

- A source with threads but no shared declaration must report the missing declaration at the
  first body token. Shared declarations without owners must report the missing thread at the
  program's closing brace, before Program construction could produce an incidental exception.
- The33rd shared and ninth local declaration must report the frontend limit at their keyword.
  Merely asserting rejection is insufficient: bypassing these guards lets the shared state
  validator reject later with a different category/location. Retain existing acceptance cases
  for32 shared declarations,8 locals and8 threads rather than duplicating them.
- Reject an initial ID1, duplicate ID0 and a gap toID2 at the actual ID token. A ninth sequential
  owner is instead a limit failure at its ID.
- Preserve each of the three allowed expectation labels as declared metadata; omission leaves
  it null. Unknown and differently cased labels are syntax errors at their token. An expectation
  is a user's declared label, not a result inferred by executing the model.

Fixtures wrap body text with a single header line. Manual prefix/line counts and independent
Python enumeration agree on the literal expectations below; Java asserts do not call a production
source mapper or derive expected positions from current source text.

| Rejected construct | Literal line/column |
|---|---|
| Missing shared declaration | 2:1 |
| Missing owner | 3:1 |
| 33rd shared declaration | 34:1 |
| Ninth local declaration | 11:1 |
| First ID1 /second ID0 orID2 on the same line | 3:8 /3:20 |
| Ninth sequential owner | 11:8 |
| Unsupported expected label | 4:10 |

## Fresh baseline and reproduction

Merged PR95 atc3305c1 passes the clean build, strict Javadoc and1,088 tests, zero failures/errors/
skips. Fresh outer-compiler baseline is **639/787 KILLED,103 SURVIVED,45 NO_COVERAGE**, zero
resource/run errors. This baseline is newly measured on merged main.

The15 selected compile mutations comprise six SURVIVED and nine NO_COVERAGE. Other133 raw
non-kills comprise the two existing scoped dispositions and131 untriaged entries outside this
bundle. No whole-compiler/checker closure is implied.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before later PIT invocations. Compare class, method, descriptor, mutator,
bytecode indices and blocks, then statuses; retain all787 identities and all639 baseline kills.
Scoped R6 applies; aggregate R5/R7 skip and need a separate full-default run. This is outer compiler
only, separate from the overlapping combined/historical populations. The default result is reported separately; identity/status equality against an unavailable prior
XML is not claimed.

## Selected mutation ledger

All entries use NonVoidMethodCallMutator in compile() against unchanged SourceCompiler bytecode.
S means SURVIVED and NC means NO_COVERAGE. This subset does not close every compile mutation.
Every selected entry is KILLED in the final run.

| Removed call /index /block | Baseline | Contract |
|---|---|---|
| current /52 /10 | NC | Required shared declaration uses the first body token |
| Token.start /53 /11 | NC | Missing-shared diagnostic location |
| error /56 /12 | NC | Missing shared declaration produces a syntax failure |
| JsonArray.size /68 /16 | S | Shared overflow retains the frontend limit category |
| Token.start /74 /19 | S | Shared overflow points at the33rd declaration |
| JsonArray.size /108 /31 | S | Local overflow retains the frontend limit category |
| Token.start /114 /34 | S | Local overflow points at the ninth declaration |
| Token.start /282 /85 | S | Invalid and over-budget owner IDs retain their positions |
| List.isEmpty /331 /98 | S | At least one owner is required before Program construction |
| current /335 /100 | NC | Missing-owner diagnostic uses the closing brace |
| Token.start /336 /101 | NC | Missing-owner diagnostic location |
| error /339 /102 | NC | Missing owners produce a syntax failure |
| current /457 /132 | NC | Invalid expected label uses the supplied token |
| Token.start /458 /133 | NC | Invalid expected-label location |
| error /461 /134 | NC | Invalid expected label produces a syntax failure |

## Verification

The clean build, strict Javadoc and **1,102 tests** pass, zero failures/errors/skips. The14 new cases
use nine test methods. Final compiler is **654/787 KILLED,97 SURVIVED,36 NO_COVERAGE**, zero TIMED_OUT,
MEMORY_ERROR, RUN_ERROR, NON_VIABLE or other statuses. All787 identities and all639 baseline kills
remain. Exactly the15 selected entries become KILLED (six SURVIVED and nine NO_COVERAGE); every
other status is unchanged. Scoped R6 passes; aggregate R5/R7 skip. Remaining133 raw compiler
non-kills comprise the two existing scoped dispositions and131 untriaged entries.

All18 normalizer checks, relative file links, credential-pattern and diff checks pass. CodeRabbit
CLI0.8.1 completed all four changed files with zero findings. Only final evidence/status prose
followed review; no test/production logic changed. Separate full-default281/291 KILLED,10 SURVIVED passes with zero uncovered/resource/run errors;
full R5/R6/R7 pass. No exact cross-run identity/status comparison to the unavailable old XML is
claimed.
Browser checks are skipped because production/report output is unchanged; remote CI is unverified.
No resource failure earns a kill; no unsupported fixture or production cleanup is required to
force100% coverage.
