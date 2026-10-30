# Spec: Source property admission and provenance

## Objective and scope

Continue9-V2 with one property-boundary contract: only shared-state boolean predicates may be
admitted, and their errors/failure evidence retain original source locations. Add
SourcePropertyContractTest, this spec, the roadmap and a Spec16 evidence pointer. No production,
harness, dependency, target/filter/operator/exclusion, denominator/floor, heap/time or CI
parallelism changes. Expression-depth/node limits, delimiter scanning, work arithmetic and lowering
remain separate follow-ups. Completed9-V1 stays closed.

## Existing contract and independent oracles

[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) requires boolean
conditions and rejects property references to locals/tid at any nesting depth. [Spec16.06](16-concurrent-program-parser/06-traces-replay-and-minimization.md)
requires original source provenance in failure evidence. Apply both contracts to always and final.

The22 cases use three parameterized methods:

- Cross both timings with eight type-correct private predicates: direct local/tid, unary local,
  local on either side of a binary expression, local array access, shared-array indexing by a local,
  and shared-array indexing by tid. Declare every local so an unknown-name error cannot mask the
  global-property admission check. Require the exact name diagnostic at the property keyword.
- A scalar integer property must fail boolean type validation at its expression start rather than
  at the keyword. This distinguishes expression diagnostics from forbidden-private-read diagnostics.
- An admitted constant-false property must produce INVARIANT/PROPERTY failure evidence with the
  whole property span, including its semicolon. Use the public DiagnosticInvariant interface and
  a real initial configuration with one already-terminated owner, so both observation modes are due.
  The span cases also exercise DFS-emitted violation traces and indented keywords.
  Do not reflect into SourceProperty or inject malformed/private ASTs.

Unindented fixtures put the keyword at line7 column1. Manual word counts and independent Python enumeration
agree: always expression start is7:19 and final is7:18; the exclusive end of the constant-false
property is7:26 and7:25 respectively. Java expectations use these literals; they do not derive
positions from the production source mapper or source length. Two leading spaces move the keyword
to7:3 and the exclusive ends to7:28/7:27; these are independently counted literals too. Failure-span
equality independently asserts file, both lines and both columns.

## Fresh baseline and reproduction

Merged PR94 at6e1cbde passes the clean build, strict Javadoc and1,066 tests, zero failures/errors/
skips. Fresh outer-compiler baseline is **618/787 KILLED,123 SURVIVED,46 NO_COVERAGE**, zero
resource/run errors. Eleven selected non-kills cover the property-only-read walker, property
admission/provenance and condition-start diagnostic: ten SURVIVED and one NO_COVERAGE. The other
158 raw residuals include the existing scoped EOF-end disposition and157 untriaged entries.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before later PIT invocations. Compare identities by class, method,
descriptor, mutator, bytecode indices and blocks, then statuses; retain all787 identities and
all618 prior kills. Scoped R6 applies; aggregate R5/R7 skip and require a separate default run.
This is outer compiler only, separate from overlapping combined/historical populations.

## Selected mutation ledger

All entries are NonVoidMethodCallMutator against unchanged SourceCompiler bytecode. S means
SURVIVED; NC means NO_COVERAGE. The condition/compile entries below are a selected subset of
those methods, not closure of every expression/compiler mutation. All selected entries except
Objects.requireNonNull are KILLED in the final run; that entry remains SURVIVED.

| Method / removed call /index /block | Baseline | Contract or disposition |
|---|---|---|
| containsLocalOrTid /Objects.requireNonNull /5 /0 | S | Non-null validated AST disposition below |
| containsLocalOrTid /String.startsWith /44 /5 | S | Local arrays cannot become global-property reads |
| containsLocalOrTid /recursive call /48 /8 | S | Shared-array indices cannot hide local/tid reads |
| containsLocalOrTid /recursive call /67 /13 | S | Unary operands cannot hide private reads |
| containsLocalOrTid /recursive call /78 /16 | S | Binary left operand cannot hide private reads |
| containsLocalOrTid /recursive call /82 /19 | S | Binary right operand cannot hide private reads |
| compile /Token.start /360 /107 | S | Forbidden property diagnostic uses the keyword location |
| compile /containsLocalOrTid /395 /114 | S | Validated private predicates must be rejected |
| compile /error /401 /116 | NC | Rejection is a categorized RegistryException |
| compile /location /420 /121 | S | Admitted false predicate retains its original failure span |
| condition /Token.start /8 /2 | S | Non-boolean property error points to the expression start |

The Objects.requireNonNull entry is retained as a scoped pipeline disposition. The private helper
receives a successfully parsed, expression-checked, boolean-typed property after read collection.
Parser constructs non-null expression nodes/children on successful returns; recursive calls visit
those children. No supported call supplies null. This does not promise equivalence for null ASTs,
reflection, hand-built malformed trees or future callers. Keep the raw survivor, not an assertion
kill or an exclusion, and revisit if the helper gains another input path.

## Additional diagnostic-category kills

The exact non-boolean-property diagnostic also kills11 baseline survivors in diagnostic's category
marker checks. None of the name/limit/syntax markers occurs in "Expression must be bool, got INT",
so the type fallback must survive intact. Negating any listed check incorrectly changes its
category. These are additional kills outside the selected11-entry partition, not double-counted
property-walker cases. All use NegateConditionalsMutator, baseline SURVIVED→final KILLED:

| Method | Index /block pairs | Assertion |
|---|---|---|
| diagnostic | 26/7,30/9,41/12,45/14,49/16,53/18,66/21,70/23,74/25,78/27,84/29 | Non-boolean property retains the type fallback category |

No broader diagnostic-remapping closure is claimed; remaining diagnostic mutations stay untriaged.

## Initial verification

The clean build, strict Javadoc and **1,086 tests** pass with zero failures/errors/skips. The20 new
cases use three parameterized methods. Final compiler is **639/787 KILLED,103 SURVIVED,45 NO_COVERAGE**,
zero TIMED_OUT, MEMORY_ERROR, RUN_ERROR, NON_VIABLE or other statuses. All787 identities and all618
baseline kills remain. Exactly21 entries become KILLED: nine selected SURVIVED plus one selected
NO_COVERAGE, and eleven additional diagnostic-category SURVIVED. Every other status is unchanged.
Scoped R6 passes; aggregate R5/R7 skip. Remaining148 raw non-kills comprise two scoped dispositions
(existing EOF-end plus non-null property-walker input) and146 untriaged entries.

All18 normalizer checks, relative file links, credential-pattern and diff checks pass. Initial
CodeRabbit CLI0.8.1 completed all four files with one MINOR roadmap-accounting finding: distinguish
selected cases from untriaged residuals. The final measured row now separates both dispositions
and146 untriaged entries, using the actual21 new kills. No repeat CLI for final evidence prose.
Separate full-default281/291 KILLED,10 SURVIVED passes, zero uncovered/resource/run errors;
all291 identities/statuses match Spec52. Full R5/R6/R7 pass. Browser checks are skipped because application/
report output is unchanged; remote CI is unverified. No whole-compiler resource guarantee or
whole-checker verification closure is claimed.


## PR review follow-up

Two trivial comments requested an indented-keyword span and explorer-emitted failure evidence.
The existing span method retains its two unindented cases and adds always/final variants with two
leading spaces, asserting literal keyword column3 and exclusive ends28/27. Every span case now
also runs DFS and asserts VIOLATION plus the complete emitted failure source span; direct API
classification/span assertions remain. The suite now has22 property-contract cases.

The line-start mutation fails only the two indented cases; replacing Trace.create's detailed
property branch with fallback evidence fails all four span cases with null sources. Production
code is restored unchanged. Follow-up build, strict Javadoc and all1,088 tests pass. Compiler
remains639/787 KILLED,103 SURVIVED,45 NO_COVERAGE; all787 identities/statuses retained, zero
resource/run errors. Scoped R6 passes; R5/R7 skip. Prior default report was not rerun. No CodeRabbit
CLI rerun for trivial feedback.
