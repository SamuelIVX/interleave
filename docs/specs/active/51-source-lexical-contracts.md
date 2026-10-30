# Spec: Source lexical boundaries and diagnostics

## Objective and scope

Continue 9-V2 with the tightly coupled identifier, comment and token/EOF contracts in
SourceCompiler. Add SourceLexicalContractTest, this spec, the roadmap and a Spec16 evidence
pointer. No production code, mutation harness, dependency, target/filter/operator/exclusion,
denominator/floor, heap/time budget or CI parallelism changes. Numeric literals, expression
typing, work accounting, lowering and other diagnostic remapping remain separate follow-ups.

## Existing contract and independent oracles

[Spec16.01](16-concurrent-program-parser/01-language-and-semantics.md) owns the lowercase ASCII
identifier grammar, inclusive 64-character maximum and 20 reserved words. Test program names
because they do not pass through the declaration loader's separate validation. Accept lengths
1 and64; reject65, reserved keywords and invalid single-token candidates (Bad, _bad, 9bad, é)
with the original name span. Separators tokenized independently may instead produce ordinary
syntax errors after a valid identifier prefix; this spec makes no blanket name-error claim for
those inputs.

Comments separate tokens. Consecutive line comments must remain comments across LF, CR and CRLF;
an unterminated block reports its opening position even when the input ends in a star. A final
lone slash must produce a categorized trailing-token diagnostic rather than an out-of-bounds
lookahead. Two-character operators stay joined: a declaration using == instead of = must report
the unexpected token at its first character, without consuming half of it as an assignment.

[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) requires stable
categories and original one-based UTF-16 coordinates. A missing final program brace reports the
actual EOF after the final newline. Trailing punctuation reports Expected 'EOF', rather than an
empty expected token. Expected messages/positions are literal independent oracles: manual token
counts and Python string enumeration agree on program-name column9, declaration == column38,
trailing slash/open comment line2 column1 and truncated-program EOF line4 column1. No production
source-map or tokenizer helper computes the expectations.

## Fresh baseline and reproduction

Merged PR #92 at2fdd192 passes the clean build, strict Javadoc and1,019 tests, zero failures/
errors/skips. The fresh outer-compiler baseline is **589/787 KILLED,139 SURVIVED,59 NO_COVERAGE**,
zero resource/run errors. Fifteen survivors belong to identifier, maskComments, tokenize and
expect; the other183 raw non-kills are outside this bundle. Do not interpret that partition as
equivalence or silently reopen completed9-V1.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before subsequent runs. Compare identities by class, method, descriptor,
mutator, bytecode indices and blocks, then compare statuses. All787 identities and all589 prior
kills must remain. Scoped R6 applies; full-default aggregate R5/R7 are skipped here and must run
separately. The scope is outer SourceCompiler only, not the historical1,491 population or
Spec50's overlapping1,465 combined selector.

## Mutation ledger

The final run kills14 of these15 baseline survivors; only tokenize/128 remains SURVIVED. NVMC denotes NonVoidMethodCallMutator, BOUND denotes
ConditionalsBoundaryMutator, NEG denotes NegateConditionalsMutator, INC denotes IncrementsMutator
and MATH denotes MathMutator. All selected entries are baseline SURVIVED. Method names below each
have one descriptor in SourceCompiler; indices/blocks refer to unchanged production bytecode.

| Method / mutation / index / block | Contract |
|---|---|
| identifier / NVMC length /15 /5 | Overlong program name rejected before downstream declarations |
| identifier / NVMC contains /21 /8 | All20 source keywords remain reserved |
| identifier / NVMC start /28 /10 | Invalid program-name location is column9 |
| maskComments / BOUND /18 /2 | Lone trailing slash cannot read past the final character |
| maskComments / BOUND /106 /17 | Unclosed block ending in star reports its opening |
| maskComments / INC /145 /23 | First line-comment newline cannot expose the second comment |
| maskComments / MATH /103 /17 | Unclosed block ending in star cannot bypass lookahead bounds |
| expect / NEG /14 /5 | Trailing token expects the spelled-out EOF marker |
| expect / NVMC start /10 /3 | Trailing-token location remains line2 column1 |
| expect / NVMC isEmpty /13 /4 | Trailing token expects EOF rather than an empty quoted token |
| tokenize / MATH /92 /23 | Declaration == stays a single unexpected operator token |
| tokenize / NVMC length /78 /20 | Disabling operator joining changes the declaration diagnostic |
| tokenize / NVMC contains /94 /24 | Disabling operator joining changes the declaration diagnostic |
| tokenize / NVMC length /125 /31 | EOF start remains at the actual source end |
| tokenize / NVMC length /128 /32 | EOF end field; scoped disposition below |

The last entry removes only the end coordinate from the private empty EOF token; its start
coordinate remains intact. Parser errors read current.start. Nodes/spans use previous.end only
after non-EOF grammar tokens; compile consumes EOF last, then builds the model without reading
that token's end. Token lists are private and not exported. This is a scoped unobserved-field
disposition, not an assertion-confirmed kill, a schema exclusion or universal equivalence for
future token consumers. Revisit if EOF end becomes observable; keep the raw survivor.

The operator-joining entries are not assumed equivalent merely because valid expressions still
parse: until passes original comment-masked slices to the expression parser, but malformed
declaration == exposes a different expected token and location when it is split.

## Verification

The clean build, strict Javadoc and **1,052 tests** pass, zero failures/errors/skips. The33 new
cases use eight test methods. The first32-case pass measured600/787 KILLED,128 SURVIVED,
59 NO_COVERAGE; adding the == diagnostic case kills the three operator-joining survivors.
Final compiler is **603/787 KILLED,125 SURVIVED,59 NO_COVERAGE**, zero TIMED_OUT, MEMORY_ERROR,
RUN_ERROR, NON_VIABLE or other statuses. All787 identities/all589 baseline kills remain. Exactly
14 SURVIVED entries become KILLED: identifier3, maskComments4, expect3, tokenize4; no other status
changes. Scoped R6 passes; aggregate R5/R7 skip. One remaining lexical survivor has the scoped
EOF-end disposition above; the other183 compiler residuals remain untriaged.

All18 normalizer checks, relative file links, credential-pattern and diff checks pass. Initial
CodeRabbit CLI0.8.1 completed all four changed files with one MINOR documentation finding:
limit the invalid-name diagnostic claim to single-token candidates, since separators may produce
ordinary syntax errors. The claim is narrowed without code/test changes; no second CLI invocation
for this documentation-only correction. Separate full-default gate passes281/291 KILLED,10 SURVIVED, zero uncovered/resource/run errors;
all291 identities/statuses match Spec50. Full R5/R6/R7 pass. Browser checks
are skipped because production/report output is unchanged; remote CI is unverified.
No global compiler resource guarantee or whole-checker verification closure is claimed.
