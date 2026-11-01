# Spec: Source expression diagnostic contracts

## Objective and scope

Continue 9-V2 with the public diagnostics produced by expression parsing and type checking.
Add SourceExpressionDiagnosticContractTest, this spec, the roadmap and a Spec16 pointer.
Keep condition and atomic-effect entry points together because both use SourceCompiler.diagnostic.
No production, harness, dependency, mutation target/filter/operator/exclusion, denominator/floor,
heap/time or CI parallelism changes. Delimiter scanning, ancestry and lowering remain separate.
Completed 9-V1 stays closed.

## Contract and independent fixtures

[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) requires categorized
source diagnostics with original one-based coordinates. Malformed expressions remain syntax
errors, reserved assignment names remain name errors, and type errors retain their fallback
category. Relative parser positions must be added to the condition fragment's original start,
including indentation. An already categorized source error must not acquire another prefix
(the existing expression-budget tests retain that separate regression).

Ten bounded cases use one parameterized method through SourceProgramLoader:

| Fragment / entry point | Expected source position | Category and message |
|---|---|---|
| true && @ / condition | 4:15 | syntax: Unexpected character '@' at pos 8 |
| local.0 / condition | 4:13 | syntax: Expected identifier at pos 6 |
| local / condition | 4:7 | syntax: Expected '.' after 'local' |
| empty / condition | 4:7 | syntax: Empty expression |
| missing / condition | 4:7 | name: Unknown field 'missing' |
| 1 / condition | 4:7 | type: Expression must be bool, got INT |
| tid = 0 / effect | 5:5 | name: Reserved keyword cannot be used as identifier: tid |
| counter / effect | 5:5 | syntax: Effect must contain '=': 'counter' |
| empty / effect | 5:5 | syntax: Empty effect |
| counter = true / effect | 5:5 | type: Assignment type mismatch |

The complete source prefix places the owner on line three, two-space-indented controls on line
four, and four-space-indented effects on line five. Manual prefix lengths give expression column
7 plus relative offsets 8/6, yielding literal columns 15/13. Independent Python character
enumeration of the actual lines agrees; Java never derives expectations using source-map or
diagnostic helpers. Empty expressions start at the closing parenthesis and empty effects at the
semicolon. Name/type cases without parser positions retain the fragment start.

Positive relative-offset coverage is limited to conditions. Effect cases above assert only
fragment-start diagnostics. Effect RHS/index offset mapping remains open in 9-V2:
Parser.parseEffect parses trimmed subfragments, but SourceCompiler.block passes the whole effect
start to diagnostic. For example, four-space-indented counter = true && @; reports column13,
although @ is at column23. Correctly rebasing RHS/index positions, including whitespace, needs
a separate focused fix and regression tests; this spec does not claim that mapping is correct.

All source delimiters are present and balanced. These fixtures do not exercise EOF inside
SourceCompiler.until, where removing the EOF guard needs a separate logical-work harness.
There is no reflection, synthetic exception injection, malformed private AST or huge input.
No test locks message-marker collisions inside arbitrary user text into a new semantic contract.

## Fresh baseline and reproduction

Merged PR100 at f8c7c18 passes the fresh clean build, strict Javadoc and 1,127 tests, with zero
failures/errors/skips. Fresh outer-compiler baseline is **707/787 KILLED, 49 SURVIVED,
31 NO_COVERAGE**, zero resource/run errors. All nine selected entries are SURVIVED. The other
71 raw residuals comprise 13 existing scoped dispositions and 58 untriaged entries outside this
selection. No whole-compiler closure is implied.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

Freeze each mutations.xml before later PIT invocations. Compare class, method, descriptor,
mutator, indices and blocks, then statuses; retain all baseline identities and kills.
Scoped R6 applies, while R5/R7 skip and require the separate default gate. Populations overlap
earlier compiler reports and must not be summed. Resource failures never earn kills.

## Selected mutation ledger

All nine selected baseline entries are in diagnostic, which has one descriptor, and changed
from SURVIVED to KILLED. One additional entry in that method also became KILLED, listed separately
below. No new dispositions are proposed.

| Mutator / index / block | Contract |
|---|---|
| NonVoidMethodCallMutator / 29 / 8 | Reserved assignment target remains a name error |
| NonVoidMethodCallMutator / 65 / 20 | Expected-token message remains syntax |
| NonVoidMethodCallMutator / 69 / 22 | Unexpected character remains syntax |
| NonVoidMethodCallMutator / 73 / 24 | Missing effect assignment remains syntax |
| NonVoidMethodCallMutator / 77 / 26 | Empty fragment remains syntax |
| NonVoidMethodCallMutator / 105 / 34 | Recognize a positive parser offset |
| NonVoidMethodCallMutator / 109 / 36 | Source-length bound does not collapse the location to zero |
| NonVoidMethodCallMutator / 114 / 38 | Parse the relative numeric offset |
| MathMutator / 115 / 39 | Add the relative offset to the original start |

Additional measured kill: NonVoidMethodCallMutator index116/block39 removes Math.min and
returns zero, collapsing a positive original-source position to line one. The in-range positive
offset cases reject this result; they do not establish behavior for hypothetical offsets beyond
the source end. This was one of the 58 baseline untriaged entries outside the selected nine.

Other diagnostic marker entries remain untriaged. Absence of a normal helper message
does not prove unreachability: some messages include arbitrary malformed source text.
No broad equivalence claim is made for the classifier or source-end clamp.

## Verification

Clean build, strict Javadoc and **1,137 tests** pass with zero failures/errors/skips. Final
outer compiler is **717/787 KILLED, 39 SURVIVED, 31 NO_COVERAGE**, zero resource/run errors.
All 787 semantic identities and all 707 prior kills remain. Exactly ten SURVIVED entries became
KILLED (nine selected plus the Math.min removal); all other statuses are unchanged. Remaining
70 raw residuals comprise 13 existing scoped dispositions and 57 untriaged entries. Scoped R6
passes; R5/R7 skip as designed.

CodeRabbit CLI0.8.1 completed an initial four-file review with zero findings; only final evidence
prose followed. All 18 normalizer cases, relative Markdown links, credential-pattern scan and
diff whitespace checks pass. Default full PIT passes **281/291 KILLED, 10 SURVIVED**, with all 291 identities/statuses
unchanged from Spec58, zero uncovered/resource/run errors, and full R5/R6/R7 passing.
Browser testing is unnecessary for unchanged application/report behavior; remote CI is unverified.
No whole-compiler resource guarantee or whole-checker closure is claimed.
