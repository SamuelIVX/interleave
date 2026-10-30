# Spec: Source loader byte boundaries and I/O diagnostics

## Objective and scope

Continue roadmap 9-V2 after the compiler harness in [Spec49](49-compiler-mutation-work-bounds.md).
Protect SourceProgramLoader's existing inclusive UTF-8 limit and path-bearing I/O failures with
six focused entry-point tests. Production behavior, the harness, dependencies, PIT targets,
operators, exclusions, denominators, floors, heap/time settings and CI parallelism do not change.

Only SourceLoaderContractTest, this spec, the roadmap and a Spec16 evidence pointer change.
Compiler behavioral triage remains separate; completed 9-V1 is not reopened.

## Contract and independent fixtures

[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) makes the
1,048,576-byte limit inclusive, requires strict UTF-8 and retains the supplied file path in local
diagnostics. Test valid models at the cap so an unrelated parser rejection cannot hide a missing
byte guard. Use comments as padding, leaving a single zero-action owner and one shared integer.

The ASCII envelope is 17 prefix bytes plus 40 suffix bytes, independently counted by hand and
cross-checked using Python len/UTF-8 encoding. Hence:

- 1,048,519 ASCII comment characters produce exactly 1,048,576 bytes/code units.
- 524,259 instances of é plus one ASCII x produce 1,048,576 UTF-8 bytes but only 524,317 Java
  code units with the envelope. One more x exceeds the byte cap while staying below the character
  cap. Both the accepted and rejected sources otherwise compile.
- The exact-cap ASCII file is accepted regardless of extension. An extra space is rejected with
  the exact path-bearing limit diagnostic, exercising the bounded read's extra-byte detection.
- A malformed UTF-8 lead byte inside an otherwise valid comment must fail decoding, preserving
  the exact path/category and a CharacterCodingException cause.
- A missing file must retain its path/category and original IOException cause. This avoids
  platform-dependent permission fixtures.
- A unique absent relative path must remain relative in the read-failure diagnostic; absolute
  temporary paths alone cannot distinguish the supplied path from an absolute rewrite.

Assert literal fixture lengths and successful model names, then exact rejection messages. Reuse
the existing null/Unicode/parser regressions rather than duplicating already-killed guards.

## Fresh baseline and reproduction

Merged PR #91 at 2dfd138 passes the clean build, strict Javadoc and 1,013 tests. The following
combined eight-outer-class scope measures **1,234/1,465 KILLED,158 SURVIVED,73 NO_COVERAGE**,
with zero resource/run errors. It is the documented initial seven-class focused selector plus
outer SourceCompiler, not a reconstruction of the historical 1,491-mutant selector. Never sum
overlapping reports or overwrite the historical failed result.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.core.StepTransition,dev.samhb.interleave.core.TransitionExecutor,dev.samhb.interleave.dpor.DporExplorer,dev.samhb.interleave.format.SourceProgramLoader,dev.samhb.interleave.format.dsl.SourceInstruction,dev.samhb.interleave.format.dsl.SourceProperty,dev.samhb.interleave.search.TraceReplayer,dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before each subsequent PIT invocation. Compare identities using class,
method, descriptor, mutator, bytecode indices and blocks; compare statuses separately. For loader
iteration use the same command with only SourceProgramLoader as pitestTargetOverride. Run default
PIT separately for the aggregate gates. Scoped R6 applies; R5 total/R7 floor are skipped.

## Loader mutation ledger

The fresh loader baseline is 23/31 KILLED,6 SURVIVED,2 NO_COVERAGE. The focused post-change run
is **30/31 KILLED,1 SURVIVED**, zero uncovered/resource/run errors. All identities and prior kills
remain in the integrated run. Indices below refer to the unchanged production bytecode;
NVMC means NonVoidMethodCallMutator, BOUND means ConditionalsBoundaryMutator.

| Method / mutation / index | Baseline | Assertion or disposition |
|---|---|---|
| compile / BOUND /17 | SURVIVED | Exact-cap ASCII model must compile |
| compile / BOUND /42 | SURVIVED | Exact-cap multibyte model must compile |
| compile / NVMC remaining /36 | SURVIVED | Valid multibyte model one byte above cap must fail with limit category |
| loadFromFile / BOUND /19 | SURVIVED | Exact-cap file must compile |
| loadFromFile / NVMC valueOf /23 | NO_COVERAGE | Above-cap file diagnostic must retain supplied path |
| loadFromFile / NVMC valueOf /91 | SURVIVED | Invalid UTF-8 diagnostic must retain supplied path |
| loadFromFile / NVMC valueOf /105 | NO_COVERAGE | Missing-file diagnostic must retain supplied path |
| compile / NVMC length /15 | SURVIVED | Retained raw survivor: removal bypasses only the early character-count rejection; the strict encoder's byte-count guard remains |

The last disposition is limited: for valid Unicode, UTF-8 byte length is at least UTF-16 code-unit
length, so the later byte guard still rejects over-limit input. It does not establish universal
equivalence: early allocation avoidance and diagnostic precedence for malformed, oversized input
are not exercised here. Keep the early guard and report the survivor; do not allocate hostile
unbounded inputs just to force a kill or credit this reasoning as an assertion-confirmed kill.

## Integrated verification

The clean build, strict Javadoc and **1,018 tests** pass with zero failures/errors/skips. The
combined census is **1,241/1,465 KILLED,153 SURVIVED,71 NO_COVERAGE**, zero resource/run errors.
Every identity and all 1,234 prior kills remain. Exactly seven loader entries change: five
SURVIVED and two NO_COVERAGE become KILLED. Every status outside SourceProgramLoader is unchanged.
Scoped R6 passes; aggregate R5/R7 skip.

| Outer class | Killed | Survived | Uncovered | Total |
|---|---|---|---|---|
| StepTransition | 10 | 0 | 0 | 10 |
| TransitionExecutor | 18 | 0 | 0 | 18 |
| DporExplorer | 265 | 12 | 12 | 289 |
| SourceProgramLoader | 30 | 1 | 0 | 31 |
| SourceInstruction | 114 | 0 | 0 | 114 |
| SourceProperty | 13 | 0 | 0 | 13 |
| TraceReplayer | 202 | 1 | 0 | 203 |
| SourceCompiler | 589 | 139 | 59 | 787 |
| **Total** | **1,241** | **153** | **71** | **1,465** |

The separate full-default gate passes **281/291 KILLED,10 SURVIVED**, zero uncovered/resource/run
errors; all291 identities/statuses match Spec49's default snapshot. R5/R6/R7 pass. The focused
31-entry loader report also exactly matches the loader subset of the final combined report.
All18 normalizer checks, relative file links, credential-pattern and diff checks pass. Initial
CodeRabbit CLI0.8.1 completed all four changed files with one MAJOR documentation finding:
missing directory parameter docs on three tests. Added those docs and meaningful IOException
failure docs; no test logic changed. No second CLI invocation for documentation-only corrections.
Browser checks are skipped because application/report output is unchanged; remote CI is unverified.
Compiler's 198 residuals remain untriaged. Neither this boundary bundle nor the per-frame/token
harness claims whole-compiler resource assurance or whole-checker verification closure.


## PR review follow-up

A trivial review comment identified that absolute temporary paths cannot detect an unwanted
absolute-path rewrite. Added a sixth case using a unique absent relative path, checking its exact
supplied-path diagnostic and IOException cause. It fails against a temporary toAbsolutePath change
to the read-failure diagnostic; production code is restored unchanged. Follow-up build, strict
Javadoc and all1,019 tests pass. Focused loader remains30/31 KILLED,1 SURVIVED with every31
identity/status unchanged and zero uncovered/resource/run errors. Scoped R6 passes; R5/R7 skip.
Prior combined/default reports above were not rerun for this additive test. No CodeRabbit CLI
rerun for trivial feedback.
