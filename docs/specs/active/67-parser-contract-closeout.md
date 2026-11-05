# Spec: Parser public-contract verification closeout

## Objective and scope

Continue 9-V2 with one combined Parser verification bundle rather than a PR per test family.
Add ParserContractTest for exact ASTs, assignment targets, deliberate errors and recursive bounds;
record every remaining Parser identity as a selected observation or a scoped proof. Include the
requested future-work PR grouping plan, this spec and a Spec 16 pointer in the same bundle.

No production, harness, dependency, mutation target/test-filter/operator/exclusion, denominator/
floor, heap/time or CI parallelism changes. Preserve Spec 61's logical work assertions and all
existing tests. This is the enumerated Parser census, separate from Spec 66's outer compiler and
the default state/CBS gate. No whole-checker or universal helper-equivalence claim is made.

## Public contracts and independent oracles

Parser's public grammar distinguishes a signed integer literal from general unary minus, preserves
identifier text, folds precedence/associativity and returns scalar or indexed shared/local targets.
It parses syntax independently of TypeChecker, and its documented recursion cap is 64, distinct
from SourceCompiler's expression-depth cap of 16. Direct Parser inputs are not fabricated private ASTs.

The 61 new cases comprise 11 literal expression ASTs, five literal effect ASTs, 18 exact expression
errors, 16 exact effect errors, two accepted identifier lengths, five recursion-path boundaries and
four sibling-depth-restoration cases. Existing SourceEffectDiagnosticOffsetTest retains whole-effect
and public source-loader offset checks; no production behavior changes are needed for these cases.

- Literal ASTs protect negative IntLit dispatch including -2147483648, unary variable negation,
  digit/underscore names, local/shared indexing and mixed arithmetic/comparison/boolean precedence.
  Whitespace before the local dot and before array brackets is accepted; whitespace after the local
  dot retains its exact expected-identifier diagnostic.
- Empty and whitespace-only expressions/effects have distinct deliberate messages from missing
  operands, missing assignment sides, empty array names/indices and invalid/reserved targets.
  Assert RegistryException and the full message, not merely rejection; incidental indexing errors
  and later fallback diagnostics are insufficient.
- Names of 63 and 64 characters are accepted through shared/local expression entry paths;
  65-character names retain the literal length error. Inputs use repeated fixture characters,
  not a production name validator to derive expectations.
- Parentheses, logical unary, arithmetic unary, shared indices and local indices each accept
  64 wrappers and reject 65 with `Expression nesting depth exceeds 64`. Parentheses preserve one
  literal node/depth one; unary/index wrappers yield 65 nodes/depth 65. These are Parser AST bounds,
  not acceptance promises for the stricter source compiler or downstream loader validation.
- Sixty-five independent unary/index operands must restore depth between operands. Each atom
  contributes two nodes; 64 joins give literal node count 194 and left-associated depth 66. This exceeds the recursion
  allowance if even a missing decrement accumulates across siblings, while every original operand
  has only one active wrapper. Tests
  assert those literals through Expr helpers, without recreating the Parser's cursor algorithm.

Manual AST/character counts and independent Python enumeration agree: 64 wrappers plus a leaf
have 65 nodes; 65 two-node atoms plus 64 joins have 194 nodes and depth 66; the local-dot prefix
has six UTF-16 units and unsupported division begins at offset two in `a / b`. Exact expected
AST records are written independently of Parser helpers. No reflection, cursor mutation or private
method invocation enters application tests. Rejected nesting inputs stop at 65 wrappers rather than
using resource exhaustion as an oracle.

## Fresh baseline and identity discipline

Merged PR 108 at 0f337a1 passes the fresh clean build, strict Javadoc and 1,229 tests. Fresh Parser:
**275/361 KILLED, 75 SURVIVED, 11 NO_COVERAGE**, zero resource/run errors. All 361 identities/
statuses match the frozen Spec 61 final report; no historical count is assumed without comparison.
The fresh combined command took about 34 minutes at one local worker.

Freeze reports before subsequent PIT calls. Match class, method, descriptor, mutator, indices and
blocks, then compare statuses. Preserve all 361 identities and all 275 prior kills. Only actual
KILLED entries earn credit; no timeout/memory/run/nonviable status does. Scoped R6 applies; R5/R7
skip and need the separate default full gate. Never add overlapping historical populations together.

The 86 baseline residuals split into 45 selected SURVIVED observations and 41 scoped candidates:
30 SURVIVED and 11 NO_COVERAGE. The latter groups are 15 whitespace removals, 12 EOF guard removals,
two closing-bracket equality boundaries, one initial consume removal and 11 private dead alternatives.
No exclusions, equivalence overrides or production deletions are proposed.

## Scoped proofs

### Redundant whitespace calls: 15 survivors

For each individual mutation, all other skipWs calls remain unchanged. parseMul's essential loop
skip consumes trailing whitespace before it either consumes its operator or returns. Inductively,
parseAdd, parseCmp, parseAnd and parseOr therefore return at a non-whitespace character or EOF.
The selected loose-level loop skips and parseExpr's final skip do no additional work. The selected
post-parseOr skips before a closing parenthesis/shared/local bracket likewise see an already clean cursor.

The selected operator-to-operand skips delegate to an expression descent whose parseUnary entry
skip consumes the same whitespace. The selected parsePrimary entry skip is redundant with that
unchanged parseUnary entry. The selected skip after a unary operator delegates to parseUnary again;
its intervening nesting check depends only on depth, not cursor position. Errors and ASTs retain
their public diagnostics in that single-mutation setting. This does not cover deleting all skips
at once, reusing a parser after a failed descent or changing the caller graph.

Other whitespace calls are observable and selected for tests: whitespace-only public inputs need
their early empty-input errors; unary dispatch inside parentheses needs its leading skip; local-dot
and shared/local array lookahead need their own spacing rules. Do not generalize the 15 proofs into
removing whitespace handling from production.

### EOF sentinel guards: 12 survivors

peek returns NUL at EOF. Character.isLetter, isLetterOrDigit, isDigit and isWhitespace are all false
for NUL, and NUL differs from underscore, dot and closing/opening brackets. Removing the selected
EOF call replaces it with false; at non-EOF the predicate is unchanged, while at EOF the remaining
peek/character test chooses the same stop/error branch. peek never advances the cursor.

In the lexical methods, the removed EOF guard can add terminal peeks. parseIdent needs at most
2*N+3 charged peeks, parseWord at most 2*N+2 and parseIntLit at most N+3, all within the existing
Spec 61 frame-local 2*N+3 budget. In each case N is that instance's immutable input length. The
proof retains the sentinel implementation and unchanged progress calls; it is not a claim about
arbitrary consume/peek mutations combined together.

### Dominated right-bracket equality: two survivors

The selected parseLhs boundaries change rb < 0 to rb <= 0 in the local/shared array branches.
Those branches already require an opening bracket. If rb is zero, the first character is a closing
bracket, so the opening bracket lies later and the string has at least two characters. Therefore
rb != length - 1 is already true, and the same OR condition throws the same invalid-array diagnostic.
All other rb values have identical first comparisons. No well-formed private target is manufactured.

### First identifier character consumed by loop: one survivor

parseIdent checks a non-EOF letter before its initial consume; its return value is unused. Removing
that consume leaves the same first letter for the following letter/digit/underscore loop, which
necessarily consumes it on its first iteration. The start/end positions, identifier text and later
validation are unchanged. The extra iteration fits 2*N+3 charged peeks, including terminal peeks.
This proof applies to that first call only; removing the loop's progress call is the distinct logical
work failure closed by Spec 61 and remains assertion-killed.

### Private primary-minus alternatives: 11 uncovered entries

Every original parsePrimary entry is reached through parseUnary. That caller first skips whitespace,
then either returns a signed literal or consumes a general unary minus before recursing into
parseUnary. It never delegates a current minus to parsePrimary. Parentheses/indices descend through
parseOr and therefore parseUnary again. parsePrimary's final self-recursive minus fallback has no
initial reachable entry, so it cannot create another path.

Consequently the eight mutations inside parsePrimary's negative-digit lookahead and the three
inside its final minus fallback remain NO_COVERAGE for ordinary public parseExpr/parseEffect calls.
The earlier digit test bypasses those lookahead sites, and the unchanged current-minus test skips
them otherwise. The mutation that changes that outer test itself is observable for `a1` and is
selected for a literal AST case; it is not included in this dead-code disposition.

Revisit all proofs if callers, sentinel behavior, whitespace ownership, target scanning or unary
dispatch changes. They assume one mutation at a time and current public-input contracts. Retain
all raw statuses and defensive code; these scoped arguments do not establish universal equivalence.

## Selected observations

The table selects 45 baseline SURVIVED entries. BOUNDARY is ConditionalsBoundaryMutator, MATH is
MathMutator, NVMC is NonVoidMethodCallMutator, VMC is VoidMethodCallMutator, NEGATE is
NegateConditionalsMutator and NULL is NullReturnValsMutator. Exact indices/blocks and descriptors
are retained in the frozen XML; each listed method has one descriptor.

| Method / mutator / index / block | Removed call or changed operation |
|---|---|
| checkNesting / BOUNDARY / 6 / 0 | changed conditional boundary |
| parseEffect / BOUNDARY / 24 / 6 | changed conditional boundary |
| parseEffect / MATH / 48 / 11 | Replaced integer addition with subtraction |
| parseEffect / NVMC / 55 / 13 | removed call to java/lang/String::isEmpty |
| parseEffect / NVMC / 58 / 15 | removed call to java/lang/String::isEmpty |
| parseEffect / VMC / 4 / 0 | removed call to dev/samhb/interleave/format/dsl/Parser::skipWs |
| parseExpr / VMC / 4 / 0 | removed call to dev/samhb/interleave/format/dsl/Parser::skipWs |
| parseIdent / NVMC / 44 / 14 | removed call to dev/samhb/interleave/format/dsl/Parser::peek |
| parseIdent / VMC / 67 / 19 | removed call to dev/samhb/interleave/format/dsl/Parser::validateIdent |
| parseLhs / BOUNDARY / 28 / 6 | changed conditional boundary |
| parseLhs / BOUNDARY / 132 / 30 | changed conditional boundary |
| parseLhs / NVMC / 80 / 18 | removed call to java/lang/String::isEmpty |
| parseLhs / NVMC / 184 / 42 | removed call to java/lang/String::isEmpty |
| parseLhs / VMC / 75 / 16 | removed call to dev/samhb/interleave/format/dsl/Parser::validateIdent |
| parseLhs / VMC / 114 / 26 | removed call to dev/samhb/interleave/format/dsl/Parser::validateIdent |
| parseLhs / VMC / 179 / 40 | removed call to dev/samhb/interleave/format/dsl/Parser::validateIdent |
| parsePrimary / MATH / 262 / 66 | Replaced integer addition with subtraction |
| parsePrimary / MATH / 329 / 77 | Replaced integer subtraction with addition |
| parsePrimary / MATH / 377 / 88 | Replaced integer addition with subtraction |
| parsePrimary / MATH / 443 / 99 | Replaced integer subtraction with addition |
| parsePrimary / NEGATE / 129 / 26 | negated conditional |
| parsePrimary / NVMC / 8 / 1 | removed call to dev/samhb/interleave/format/dsl/Parser::eof |
| parsePrimary / VMC / 210 / 51 | removed call to dev/samhb/interleave/format/dsl/Parser::skipWs |
| parsePrimary / VMC / 241 / 60 | removed call to dev/samhb/interleave/format/dsl/Parser::skipWs |
| parsePrimary / VMC / 267 / 66 | removed call to dev/samhb/interleave/format/dsl/Parser::checkNesting |
| parsePrimary / VMC / 352 / 81 | removed call to dev/samhb/interleave/format/dsl/Parser::validateIdent |
| parsePrimary / VMC / 356 / 82 | removed call to dev/samhb/interleave/format/dsl/Parser::skipWs |
| parsePrimary / VMC / 382 / 88 | removed call to dev/samhb/interleave/format/dsl/Parser::checkNesting |
| parseUnary / BOUNDARY / 18 / 4 | changed conditional boundary |
| parseUnary / MATH / 14 / 3 | Replaced integer addition with subtraction |
| parseUnary / MATH / 60 / 16 | Replaced integer addition with subtraction |
| parseUnary / MATH / 101 / 20 | Replaced integer subtraction with addition |
| parseUnary / NEGATE / 18 / 4 | negated conditional |
| parseUnary / NVMC / 8 / 1 | removed call to dev/samhb/interleave/format/dsl/Parser::peek |
| parseUnary / NVMC / 17 / 3 | removed call to java/lang/String::length |
| parseUnary / NVMC / 25 / 5 | removed call to java/lang/String::charAt |
| parseUnary / NVMC / 26 / 6 | removed call to java/lang/Character::isDigit |
| parseUnary / NVMC / 41 / 12 | removed call to dev/samhb/interleave/format/dsl/Parser::peek |
| parseUnary / VMC / 4 / 0 | removed call to dev/samhb/interleave/format/dsl/Parser::skipWs |
| parseUnary / VMC / 65 / 16 | removed call to dev/samhb/interleave/format/dsl/Parser::checkNesting |
| parseWord / NVMC / 17 / 6 | removed call to dev/samhb/interleave/format/dsl/Parser::peek |
| validateIdent / BOUNDARY / 21 / 6 | changed conditional boundary |
| validateIdent / NVMC / 6 / 1 | removed call to java/lang/String::isEmpty |
| validateIdent / NVMC / 19 / 5 | removed call to java/lang/String::length |
| validateIdent / NVMC / 46 / 13 | removed call to java/lang/String::equals |

## Raw scoped residual inventory

All 41 final residuals retain their baseline statuses with the proofs above; they earn no kills.
Final comparison maps each semantic identity and status exactly, rather than inferring closure from totals.

| Method / mutator / index / block | Baseline | Proof group |
|---|---|---|
| parseAdd / VMC / 10 / 2 | SURVIVED | Redundant whitespace call |
| parseAdd / VMC / 30 / 8 | SURVIVED | Redundant whitespace call |
| parseAnd / VMC / 10 / 2 | SURVIVED | Redundant whitespace call |
| parseAnd / VMC / 20 / 5 | SURVIVED | Redundant whitespace call |
| parseCmp / VMC / 10 / 2 | SURVIVED | Redundant whitespace call |
| parseCmp / VMC / 81 / 22 | SURVIVED | Redundant whitespace call |
| parseExpr / VMC / 24 / 6 | SURVIVED | Redundant whitespace call |
| parseIdent / NVMC / 9 / 0 | SURVIVED | EOF sentinel makes guard redundant |
| parseIdent / NVMC / 31 / 7 | SURVIVED | First character consumed by loop |
| parseIdent / NVMC / 37 / 9 | SURVIVED | EOF sentinel makes guard redundant |
| parseIntLit / NVMC / 19 / 4 | SURVIVED | EOF sentinel makes guard redundant |
| parseIntLit / NVMC / 36 / 11 | SURVIVED | EOF sentinel makes guard redundant |
| parseLhs / BOUNDARY / 38 / 8 | SURVIVED | Dominated closing-bracket equality |
| parseLhs / BOUNDARY / 142 / 32 | SURVIVED | Dominated closing-bracket equality |
| parseMul / VMC / 30 / 8 | SURVIVED | Redundant whitespace call |
| parseOr / VMC / 10 / 2 | SURVIVED | Redundant whitespace call |
| parseOr / VMC / 20 / 5 | SURVIVED | Redundant whitespace call |
| parsePrimary / BOUNDARY / 137 / 28 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / MATH / 133 / 27 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / MATH / 143 / 29 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / NEGATE / 137 / 28 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / NEGATE / 146 / 31 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / NVMC / 55 / 11 | SURVIVED | EOF sentinel makes guard redundant |
| parsePrimary / NVMC / 136 / 27 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / NVMC / 144 / 29 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / NVMC / 145 / 30 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / NVMC / 214 / 52 | SURVIVED | EOF sentinel makes guard redundant |
| parsePrimary / NVMC / 245 / 61 | SURVIVED | EOF sentinel makes guard redundant |
| parsePrimary / NVMC / 280 / 69 | SURVIVED | EOF sentinel makes guard redundant |
| parsePrimary / NVMC / 360 / 83 | SURVIVED | EOF sentinel makes guard redundant |
| parsePrimary / NVMC / 395 / 91 | SURVIVED | EOF sentinel makes guard redundant |
| parsePrimary / NVMC / 466 / 104 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / NVMC / 471 / 105 | NO_COVERAGE | Unreachable primary minus alternatives |
| parsePrimary / VMC / 4 / 0 | SURVIVED | Redundant whitespace call |
| parsePrimary / VMC / 51 / 10 | SURVIVED | Redundant whitespace call |
| parsePrimary / VMC / 276 / 68 | SURVIVED | Redundant whitespace call |
| parsePrimary / VMC / 391 / 90 | SURVIVED | Redundant whitespace call |
| parsePrimary / NULL / 480 / 107 | NO_COVERAGE | Unreachable primary minus alternatives |
| parseUnary / VMC / 53 / 15 | SURVIVED | Redundant whitespace call |
| parseWord / NVMC / 10 / 1 | SURVIVED | EOF sentinel makes guard redundant |
| skipWs / NVMC / 5 / 0 | SURVIVED | EOF sentinel makes guard redundant |

## Verification commands

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.Parser
./gradlew --max-workers=1 -PpitestThreads=1 test \
  --tests dev.samhb.interleave.format.dsl.ParserContractTest
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.Parser
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

## Results

All 61 focused cases and integrated **1,290 tests**, build and strict Javadoc pass after the review
patch, zero failures/errors/skips. CodeRabbit CLI 0.8.1 completed all four files with one trivial
suggestion: use 65 sibling operands to detect a missing decrement as well as an inverted decrement.
Independently derived 194 nodes/depth 66, strengthened the four cases and reran integrated validation.
No CLI repeat for this trivial test-only correction. All 18 normalizer cases, relative links,
credential-pattern and diff checks pass. The requested roadmap grouping plan ships in this
implementation PR, with no separate documentation PR.

Final Parser: **320/361 KILLED, 30 SURVIVED, 11 NO_COVERAGE**, zero resource/run errors. All 361
identities and 275 prior kills remain. Exactly the 45 selected SURVIVED observations become KILLED;
every other status is unchanged. Every one of the 41 remaining semantic identities matches its
scoped inventory entry; method descriptors are unambiguous. Scoped R6 passes; R5/R7 skip as designed.
The separate default full mutation gate passes **281/291 KILLED, 10 SURVIVED**, zero uncovered/
resource/run errors. All 291 identities/statuses match Spec 66; full R5/R6/R7 pass. Browser checks are unnecessary for
unchanged application/rendering; remote CI is unverified.

## Tracked 9-V2 completion boundary

This completes the remaining recorded Parser ledger alongside the outer-compiler ledger in
[Spec 66](66-source-compiler-verification-closeout.md) and source-entry ledger in
[Spec 50](50-source-loader-contracts.md). Those recorded scopes now have explicit residual
dispositions. Earlier shared execution, DPOR, replay and Program work remains under the tracked
9-V1 census in [Spec 47](47-verification-census.md); selector progress remains under
[Spec 48](48-selector-mutation-work-bound.md). The older broad source-program-parser census and
these later scopes overlap and are not summed into a new measurement.

Tracked 9-V2 work is implemented, pending merge; the separate default gate passes. Completion is
limited to the enumerated ledgers and every referenced input/lifecycle/purity/metadata assumption;
all raw survivors/uncovered remain visible. It is not universal equivalence, verification of all
nested/helper classes or a whole-checker soundness claim. Reopen for a concrete contract regression.
Future diagnostics, profiling and modeling features keep their separate roadmap selection rules.
