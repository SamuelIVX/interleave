# Spec: Whole-effect parser diagnostic offsets

## Objective and scope

Fix the effect RHS/index position defect tracked by Spec59 and roadmap9-V2. Parser.parseEffect
and its assignment-index subparsers must report positions relative to the complete parser input,
so SourceCompiler can translate those positions from the effect's original source start.
Use one focused change in Parser, source/direct-parser regression tests, this spec, the roadmap
and the Spec16 evidence pointer. No compiler workaround, new public API or dependency is needed.

Preserve successful parsing, AST values, existing categories, non-positional messages, resource
bounds and condition diagnostics. Numeric positions in invalid-effect messages intentionally
change for source, JSON and direct-parser callers. Type/name failures without parser positions
continue to use the fragment start. This does not add positions to every diagnostic.

## Reproduction and root cause

At merged PR101/b01091c, a four-space-indented counter = true && @; reports column13 instead
of23. Parser.parseEffect trims the RHS and starts a fresh Parser at zero. SourceCompiler.block
then adds that RHS-relative position8 to the complete effect start column5. Shared/local
assignment indices have the same lost-origin problem.

A standalone public-loader assertion failed on the compiled baseline before production edits:
expected7:23/position18, actual7:13/position8. The matching regression fixture was written before
the production fix. A second standalone assertion exposed a trim-stripped control prefix
(expected17, actual16); shared/local index regressions protect that case. Both probes pass with
the final fix, along with all twenty-one focused JUnit cases. The LHS origin is located in the
original input instead of reusing the whitespace cursor.
The location assertions use the public SourceProgramLoader seam; direct Parser cases protect
whole-input offsets including leading whitespace, which the JSON loader also receives.

## Fix contract

Each Parser has a private diagnostic origin relative to the complete effect input. Public
construction starts at zero. RHS/index child parsers preserve legacy trimming and receive
the trimmed subfragment's actual origin, including the width of the removed prefix. Only
diagnostics add that origin to the parser cursor; parsing
and nesting accounting continue to use the unchanged local cursor.

The raw RHS slice starts just after the assignment equals. A raw shared/local assignment-index
slice starts just after the opening bracket in the whole trimmed LHS plus the original LHS start.
A shared parseFragment helper trims each nonempty slice and locates the trimmed text inside the
original slice before constructing its child parser. The same origin is used for unexpected-
character, expected-identifier and trailing-input positions.
Empty/malformed shapes retain existing validation and messages. No regex-based rewriting of
exception messages or synthetic source padding is introduced.

## Independent regression oracles

The common source prefix puts effects on line7, column5. Twenty-one cases use three parameterized
methods: ten public-source cases, eight direct-parser position cases and three acceptance cases.

| Source assignment shape | Original source position | Whole-effect parser position |
|---|---|---|
| counter = true && @ | 7:23 | 18 |
| counter =   true && @ | 7:25 | 20 |
| values[true && @] = 0 | 7:20 | 15 |
| local.items[  true && @ ] = 0 | 7:27 | 22 |
| values[values[0] + @] = 0 | 7:24 | 19 |
| RHS on next line, six-space indentation | 8:15 | 24 |
| RHS after a masked block comment | 7:34 | 29 |
| Local index on next line, six-space indentation | 8:15 | 27 |
| counter = local.0 | 7:21 | 16 |
| counter = true false | 7:20 | 15 |

The first eight cases expect Unexpected character '@'; the last two protect Expected identifier
and Unexpected trailing characters. Every assertion includes the complete category/message.
Direct effects with three leading spaces assert positions21/19/24 for RHS/shared/local indices.
Two trim-stripped leading-NUL cases assert positions17/22: String.trim removes that prefix, while
Character.isWhitespace (used by the cursor) does not. Locating the nonempty trimmed LHS in the
original input retains its actual origin without changing existing parsing acceptance. Three additional
invalid RHS/shared/local fragments with boundary NULs assert positions20/17/22 after trimming.
Three valid boundary-NUL cases assert literal Effect/Lhs/Expr ASTs, preserving the old parser's
acceptance as well as its successful values.

Manual prefix-length sums and independent Python character enumeration agree on these literal
offsets. Multiline cases independently count newline boundaries and columns; comment masking
preserves the original width. Java does not recompute expectations from production helpers.
All source delimiters are balanced, including nested index brackets; no EOF-until fixture or
huge expansion is added. The existing Spec59 condition/type/name cases remain intact.

## Verification and mutation scope

Fresh merged-main clean build, strict Javadoc and 1,137 tests pass. The compiled baseline
Parser report is **252/342 KILLED, 75 SURVIVED, 11 NO_COVERAGE, 4 TIMED_OUT**; scoped R6
**fails**. No memory/run/nonviable errors occur. The four pre-existing timeouts are match Math15,
parseIdent.consume NVMC47, parseIntLit.consume NVMC43 and parseWord.consume NVMC23. They are
not assertion kills. Parser progress-bound harness work remains a separate follow-up; no timeout
settings, operators or exclusions are changed. Source edits made while PIT finished did not
recompile or replace the baseline bytecode. Freeze
mutations.xml before later invocations. Parser is a separate population from the historical
outer-compiler707/787→717/787 results in Spec59; do not add overlapping reports or imply full
compiler/checker closure.

Parser bytecode changes in this fix, so final mutation identities/denominators may change.
Report fresh raw statuses and explain that difference; do not claim a fixed-identity kill delta.
Scoped R6 applies, R5/R7 skip as designed. Run the unchanged default gate separately for full
R5/R6/R7. No operator/filter/target-default/exclusion/floor, heap/time, harness or CI parallelism
changes; resource failures never count as assertion kills.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.Parser
./gradlew --max-workers=1 -PpitestThreads=1 test \
  --tests dev.samhb.interleave.format.SourceEffectDiagnosticOffsetTest
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

The final clean build, strict Javadoc and **1,158 tests** pass with zero failures/errors/skips.
The original source, trim-prefix and valid-boundary probes all pass. All18 normalizer cases, relative
Markdown links, credential-pattern scan and diff whitespace checks pass. Final Parser PIT is **271/361 KILLED, 75 SURVIVED, 11 NO_COVERAGE, 4 TIMED_OUT**.
Scoped R6 still **fails** on the same progress-mutant sites: match Math15/block3,
parseIdent.consume NVMC50/block16 (formerly47), parseIntLit.consume NVMC43/block16 and
parseWord.consume NVMC23/block8. Zero memory/run/nonviable errors occur. No timeout earns
kill credit. The new parseFragment helper is5/5 KILLED.

Bytecode changes increase the Parser denominator342→361; this is not a fixed-identity kill
delta. Across unchanged methods, all157 identities/statuses match and all122 prior kills remain.
Changed methods are not claimed to retain one-to-one instruction identities. The raw Parser
residual population remains90 entries (75S/11NC/4TIME), with no new dispositions.

Default full PIT passes **281/291 KILLED, 10 SURVIVED**, with all291 identities/statuses
unchanged from Spec59 and zero uncovered/resource/run errors. Full R5/R6/R7 pass. CodeRabbit completed two bounded six-file reviews: the initial
review had zero findings; the follow-up had two minor findings (stale status prose and preserving
boundary-control trimming). Both were independently validated and patched. The final21-case
regression suite and full build passed after these fixes; no third CLI invocation is needed.
Browser checks are unnecessary for unchanged application/report rendering. Remote CI is unverified.
