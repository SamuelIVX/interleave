# Spec: Source block validation and raw-node staging

## Objective and scope

Continue9-V2 with source-block nesting/EOF guards and the parsed-node fields owned by this stage.
Add SourceBlockContractTest, this spec, the roadmap and a Spec16 evidence pointer. No production,
harness, dependency, mutation target/filter/operator/exclusion, denominator/floor, heap/time or CI
parallelism changes. Delimiter scanning, aggregate work, expansion behavior and lowering remain
separate; raw-node dispositions below do not claim broader downstream closure. Completed9-V1
stays closed.

## Contract and independent fixtures

[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) makes source nesting
16 inclusive, with the program body at depth1 and every braced body adding1. Source depth applies
even when a parsed body contributes no expanded executable work. Missing delimiters retain syntax
categories and original one-based EOF coordinates.

Six cases use two parameterized methods:

- Independently nest if-then, if-else, repeat and bounded while bodies. The program/owner bodies contribute2
  levels;14 nested levels give depth16 and must load,15 give17 and must fail at the rejected
  opening brace. Use no atomic/assignment work, repeat1 counts and false while-limit0 guards to keep
  expansion well below its limits, isolating the source guard from work/iteration accounting.
  Else-chain headers have an empty then arm; the two sibling arms share a depth level.
- Empty unfinished owner and atomic bodies must report Expected '}' before EOF at the actual
  source end. They contain no expression fragment; a single removed block EOF check reaches a
  different categorized error rather than relying on an unbounded expression scan.

Manual line/word counts and independent Python enumeration agree: the rejected15th control starts
on line18, with rejected opening-brace columns11/12/10/23 for then-chain/else-chain/repeat/while.
For the else-chain, the innermost empty then arm is checked first; mutating the enclosing else
depth still bypasses that boundary, so this assertion distinguishes the else-depth edge. Accepted depth16 is not a
recomputed production result; it follows the specified2+14 source braces. EOF positions are4:1
for the owner and5:1 for the atomic body. Java diagnostics assert these literal positions and
messages, never a production source-map result.

## Fresh baseline and reproduction

Merged PR97 atc453ff4 passes the clean build, strict Javadoc and1,109 tests, zero failures/errors/
skips. Fresh outer-compiler baseline is **677/787 KILLED,76 SURVIVED,34 NO_COVERAGE**, zero
resource/run errors. All16 block residuals (13 SURVIVED,3 NO_COVERAGE) are selected for guard
hardening or scoped staging dispositions. Other94 raw residuals comprise the three existing
scoped dispositions and91 untriaged entries outside this selection.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before later PIT invocations. Compare class, method, descriptor, mutator,
indices and blocks, then statuses; retain all787 identities and all677 baseline kills. Scoped
R6 applies; aggregate R5/R7 skip and need a separate full-default run. This is outer compiler only,
distinct from overlapping combined/historical populations. No resource failure earns a kill.

## Guard ledger

MATH means MathMutator; NVMC means NonVoidMethodCallMutator. S means SURVIVED and NC means
NO_COVERAGE. Every entry is in block() with its one descriptor and unchanged production bytecode.
All eight guard entries are KILLED in the final run.

| Mutation /index /block | Baseline | Contract |
|---|---|---|
| MATH /145 /42 | S | else child advances source depth |
| MATH /192 /54 | S | repeat child advances depth even for empty work |
| MATH /241 /67 | S | while child advances depth even for zero iterations |
| NVMC Token.start /9 /2 | S | Depth error points at rejected opening brace |
| NVMC peek /35 /10 | S | EOF gets the missing-brace diagnostic |
| NVMC current /39 /12 | NC | EOF diagnostic uses actual current token |
| NVMC Token.start /40 /13 | NC | EOF location is preserved |
| NVMC error /43 /14 | NC | EOF produces RegistryException, not an incidental exception |

## Raw-node staging dispositions

Keep all eight entries below as raw survivors. SourceCompiler.block is private and exposes no raw tree.
The supported consumers are work, expressionWork and expand before lower/SourceInstruction. These
arguments apply to each single mutation's unchanged callers; they do not permit arbitrary null
nodes, reflection or future consumers.

| NVMC removed call /index /block | Raw field | Scoped reason |
|---|---|---|
| List.of /108 /32 | ATOMIC path | Replaced by expand's explicit ancestry parameter |
| List.of /169 /47 | IF path | Replaced by expand's explicit ancestry parameter |
| List.of /204 /55 | REPEAT no | Ignored for this kind by all pre-expansion consumers; repeat eliminated |
| Token.end /209 /57 | REPEAT end | Budget/expansion use count/start/body, not end; repeat eliminated |
| List.of /210 /58 | REPEAT path | Expansion builds body ancestry from explicit parameter/start/count |
| List.of /253 /68 | WHILE no | Ignored for this kind; expansion builds fresh IF/LIMIT branches |
| List.of /259 /71 | WHILE path | Expansion builds fresh ancestry from explicit parameter/start/count |
| List.of /352 /98 | ASSIGN path | Replaced by expand's explicit ancestry parameter |

For paths, neither work nor expressionWork reads Node.path, and expand never reads the raw path;
its reconstructed nodes receive the enclosing path or ancestry(path,start,iteration). Only those
expanded nodes reach lowering, footprints, execution and evidence. For REPEAT/WHILE no fields,
work/expressionWork use body/count/test for those kinds, not no; expand likewise reconstructs or
eliminates them without reading no. Raw REPEAT end never enters executable nodes: expanded body
spans remain intact and ancestry records start/iteration only. WHILE end is observed by cutoff
evidence and is deliberately outside this disposition.

These are scoped unobserved-field arguments, not assertion kills, exclusions or universal
equivalence. Revisit them if raw ASTs are exported, a new visitor observes those fields, repeat
span evidence is introduced, or ancestry ownership changes. Keep executable branch/path/end
validation intact; do not remove production checks to force100%.

## Verification

The first clean five-case build/Javadoc passed1,114 tests; the first compiler run
measured684/787 KILLED,72 SURVIVED,31 NO_COVERAGE, no resource/run errors. The remaining guard
mutation was the else-depth edge. Adding one else-chain CSV row completes the six-case set.

Final build, strict Javadoc and **1,115 tests** pass, zero failures/errors/skips. Compiler is
**685/787 KILLED,71 SURVIVED,31 NO_COVERAGE**, zero TIMED_OUT, MEMORY_ERROR, RUN_ERROR, NON_VIABLE
or other statuses. All787 identities and all677 baseline kills remain. Exactly eight block guard
entries become KILLED (5S+3NC); every other status is unchanged. All eight retained block survivors
match the scoped staging dispositions above. Scoped R6 passes; aggregate R5/R7 skip. Remaining102
raw compiler non-kills comprise11 scoped dispositions (three prior plus eight staging entries)
and91 untriaged entries; dispositions remain raw non-kills.

All18 normalizer checks, relative file links, credential-pattern and diff checks pass. Initial
CodeRabbit CLI0.8.1 completed all four files with zero findings. The small else-chain fixture row,
its location explanation and final evidence prose followed review; the added case passed ordinary
and mutation checks, with no repeat CLI for that minor fixture extension. No production/harness
logic changed. Final six-case full-default281/291 KILLED,10 SURVIVED passes, zero uncovered/resource/run errors;
all291 identities/statuses match Spec55. Full R5/R6/R7 pass. The earlier five-case default report
is not substituted for this final report. Browser checks are skipped because production/
report output is unchanged; remote CI is unverified. No whole-compiler resource guarantee or
whole-checker verification closure is claimed.
