# Spec: Parser mutation progress bounds

## Objective and scope

Continue 9-V2 by converting the four Parser progress failures measured in Spec 60 into logical
assertion failures. Add a dedicated ParserWorkBudget transformer to the existing PIT-only agent,
independent verifier/child-JVM tests, this spec and roadmap/Spec 16 pointers. Reuse the existing
agent JAR, content-based PIT task inputs and compiled-harness report-freshness check.

No production parsing, API, output, application fixtures, dependencies, mutation targets/test
filters/operators/exclusions, denominator/floor, heap/time settings or CI parallelism changes.
This does not reopen completed 9-V1 or promise a whole-parser/whole-compilation resource bound.

## Logical contracts

### Lexical scan work

Each invocation of parseIdent, parseIntLit or parseWord may perform at most 2 * N + 3 peek operations,
where N is that parser instance's immutable input.length() in UTF-16 units.

- parseIdent has one initial peek, consumes its first character, then at most two peeks per
  remaining consumed unit and at most two terminating peeks: at most 2 * N + 1.
- parseWord performs at most two peeks per consumed unit plus at most two terminating peeks:
  at most 2 * N + 2.
- parseIntLit has two head checks, at most one peek per digit and at most one terminating peek:
  at most N + 3, which is no greater than 2 * N + 3.

Each original consume advances one unit. N bounds the remaining input even when the invocation
starts after earlier tokens. The common 2 * N + 3 bound is conservative for every original invocation.
Widen N to long before multiplying/adding; even a maximal String length cannot overflow it.
Count peeks before their actual calls, independently of the consume call whose removal stalls
the loop. Counters are fresh frame-local longs, not shared state or wall-clock fuel.

### Operator cursor progress

Snapshot pos on entry to each match(String) invocation. On both boolean returns, pos must be at
least that entry position: a miss preserves it and a successful literal match advances it.
This local check permits failed operator alternatives without consuming input; it does not impose
a global cursor invariant on the rest of the parser.

### Binding and isolation

Require the exact Parser class, private instance scan/match signatures, private final input:String
and private mutable pos:int. Initial lexical peek checkpoint counts are 3/3/2, with two match
IRETURN checkpoints. Redefinition may omit at most one peek across the class for a single
call-removal mutant; every other layout/count drift rejects the definition. A removed peek keeps
the original consume/progress logic, so this exception cannot hide the selected consume removals.

Unexpected initial/redefinition shapes return nonempty invalid class bytes because the JVM may
ignore ordinary transformer exceptions. Instrumentation adds locals and JDK AssertionErrors only:
no fields, public hooks, runtime agent-class dependency or schema changes. Unrelated classes are
untouched. Application/library distributions must exclude harness classes.

## Independent verification

Sixteen cases use eight test methods. Eight cases launch nine child JVMs; the other eight validate
binding drift in-process. The startup watchdog is outside PIT's unchanged application-test filter,
always reaps the child and cannot earn application mutation kills.

Positive agent/no-agent probes check literal ASTs for a 64-character underscore-heavy identifier,
shared/local identifiers, signed-int extremes, 129-digit leading-zero input yielding 1,
operator precedence and legacy boundary-control trimming. Empty, malformed, 65-character names
and overflowing integer literals must still raise the original RegistryException.

Negative probes edit actual compiled Parser bytecode: remove the loop's last consume call from
each lexical method, or change match's sole IADD to ISUB. The public expressions local.alpha,
123, alpha and 1 == 2 reach parseIdent, parseIntLit, parseWord and a successful match respectively. ClassFile.verify must accept both the
mutant and rewritten bytes. Children must report the exact independent lexical-work or backward-
cursor assertion, not merely exit nonzero or hit a watchdog.

Binding tests cover one missing peek in each scan (allowed only on redefinition), two missing
peeks across methods (always rejected), each missing required method, mutable input, missing pos
and a missing match-return checkpoint. Initial missing-checkpoint definitions must fail actual
class loading with ClassFormatError. Original parser logic is never copied into a fake scanner.

Manual source inspection and independent enumeration confirm 3/3/2 static peeks, two returns,
64 = 1 + 63 identifier units and 129 = 128 + 1 digit units. Java asserts literal ASTs and checkpoint counts;
it does not rederive the parser's behavior or resource formula as an expected result.

## Baseline and selected ledger

Merged PR 102 at 46af483 passes the fresh clean build, strict Javadoc and 1,158 tests, with zero
failures/errors/skips. Fresh Parser baseline is **271/361 KILLED, 75 SURVIVED, 11 NO_COVERAGE,
4 TIMED_OUT**, zero memory/run/nonviable errors; scoped R6 fails honestly. All 361 semantic
identities/statuses match Spec 60. The four selected raw timeouts earn no credit. Preparing new
source files during baseline PIT did not recompile/replace its original agent or application bytes.

| Method / mutator / index / block | Progress failure |
|---|---|
| match / MathMutator / 15 / 3 | Cursor addition replaced by subtraction |
| parseIdent / NonVoidMethodCallMutator / 50 / 16 | Loop consume removed |
| parseIntLit / NonVoidMethodCallMutator / 43 / 16 | Loop consume removed |
| parseWord / NonVoidMethodCallMutator / 23 / 8 | Loop consume removed |

Freeze mutations.xml before later runs. Compare class/method/descriptor/mutator/indexes/blocks
and statuses. Production bytecode is unchanged, so retain all 361 identities and all 271 prior kills.
Only actual KILLED statuses count. Scoped R6 applies; R5/R7 skip and require the separate default
full gate. No resource error is credited retroactively. Other raw Parser residuals remain untriaged,
not equivalent or assigned by this spec. Compiler, selector and historical populations stay separate.
Re-derive the bounds if lexical/match algorithms or input/cursor contracts change, even when
static checkpoint counts still match.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.Parser
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

## Results

All 16 focused harness cases pass, including nine child JVMs. The integrated clean build,
strict application/agent Javadoc and 1,174 tests pass, zero failures/errors/skips. Published JARs
and distributions exclude harness classes; the agent JAR contains the new transformer only
alongside the existing harness. All 18 normalizer checks, relative links, credential scan and
diff checks pass. CodeRabbit CLI 0.8.1 completed an initial seven-file review with one minor
stale-status finding; it is patched. Extra private-member Javadoc exposed an implicit constructor,
which now has an explicit meaningful comment. Final Parser is **275/361 KILLED, 75 SURVIVED, 11 NO_COVERAGE**, zero timeout/memory/run/
nonviable errors. All 361 semantic identities and all 271 baseline kills remain. Exactly the four
selected TIMED_OUT entries become KILLED; all other statuses are unchanged. Scoped R6 now passes;
R5/R7 skip as designed. No historical timeout is credited retroactively. The remaining 86 raw
Parser non-kills stay untriaged, with no new dispositions or whole-parser closure. Agent byte
hash matches the frozen final build. The separate default full gate passes **281/291 KILLED, 10 SURVIVED**, zero uncovered/resource/
run errors. All 291 identities/statuses match Spec 60; full R5/R6/R7 pass. Agent bytes still match
the measured Parser run after the default gate.
Browser checks are unnecessary for unchanged production/rendering; remote CI is unverified.
