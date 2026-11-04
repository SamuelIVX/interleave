# Spec: Source delimiter contracts

## Objective and scope

Continue 9-V2 with SourceCompiler.until: delimiter nesting, brace rejection and EOF diagnostics.
Extend the existing PIT-only CompilerWorkBudget before adding unfinished-expression fixtures,
so the removed EOF guard fails a logical assertion rather than looping on the clamped EOF token.
Ship the guard, independent harness probes, SourceDelimiterContractTest, roadmap and Spec 16 pointer
as one tightly coupled bundle.

No production, dependency, mutation target/filter/operator/exclusion, denominator/floor, heap/time
or CI parallelism changes. General lowering/validator gaps remain separate; completed 9-V1 stays closed.

## Contract and independent oracles

The public loader rejects EOF before a requested closing parenthesis or semicolon, rejects either
brace encountered inside the expression and rejects negative parenthesis/bracket nesting.
Scanner errors retain the original source location and syntax category. Balanced nested parentheses
and array indices preserve parsing, execution and replay.

Eight malformed public-source cases cover condition/effect EOF, both braces in both contexts,
an unmatched closing parenthesis and an unmatched closing bracket. Literal locations are 4:11
(condition EOF/bracket), 4:12 (condition braces), 5:16 (effect EOF/parenthesis) and 5:17 (effect braces).
Manual line character counts agree with independent Python enumeration of whole-source positions.
No parser diagnostic helper calculates expected messages. One positive case compiles a condition
and an atomic action: two instructions by direct statement enumeration. Reading values[1] = 2,
adding 2 and storing/reading values[0] yields counter 4 and array [4, 2]; a separate arithmetic/event
walk confirms those literal expectations. Exact DFS completion and replay check the final values.

## PIT-only delimiter bound

For T = tokens.size(), including EOF, each original until invocation makes at most T + 1 direct
current() calls. It reads once before scanning, once per visited non-EOF token and once at return
or error. Success/brace/unmatched-delimiter paths stop before EOF; the EOF path visits at most
T - 1 non-EOF tokens plus its initial and terminal reads. Starting later in the list only reduces
work. Indirect current() calls inside peek are not counted. Token input is the compiler's immutable
List.copyOf result in its final instance field; no mutation changes it in the measured operator set.

The transformer initializes a frame-local long from the token count, widens before adding one,
and charges before each of six direct current() sites. Exhaustion throws the exact JDK AssertionError
message `Compiler delimiter scan exceeded token count plus one`. The checkpoint is independent
of the removable EOF peek; a removed guard eventually exhausts it even though current() clamps
at EOF. It adds no application fields, agent references or helper calls. Existing expansion and
tokenization bounds are unchanged. This is a scanner-work proof, not whole-compilation resource equivalence.

Binding requires the exact until instance signature and final nonstatic tokens:List field, alongside
existing method/field checks. Initial definitions require six scan sites; PIT redefinition permits
five for a single removed current() call, whose original progress guards remain intact. Other drift
fails closed through invalid nonempty class bytes. Harness tests verify missing method/field,
mutable field and removed checkpoint behavior. ClassFile.verify checks both the real removed-peek
mutation and its instrumented definition; a child JVM must report the exact assertion. Original
nested expressions and all finite malformed paths run with and without the agent. The child-process
watchdog is startup safety outside PIT's application-test filter, never kill evidence.

## Fresh baseline and selected ledger

Merged PR 104 at ba7d94b passes a fresh clean build, strict Javadoc and 1,176 tests. Fresh compiler
baseline: **721/787 KILLED, 35 SURVIVED, 31 NO_COVERAGE**, zero resource/run errors. Its 66 raw
residuals comprise 13 existing scoped dispositions and 53 untriaged entries. Select twelve until
NonVoidMethodCallMutator entries (one method descriptor), leaving 54 other residuals outside scope:

| Removed call / index / block | Baseline | Protected observation |
|---|---|---|
| peek / 18 / 3 | SURVIVED | EOF progress |
| String.equals / 49 / 15 | SURVIVED | Opening brace rejection |
| String.equals / 53 / 17 | SURVIVED | Closing brace rejection |
| current / 59 / 19 | NO_COVERAGE | Brace error token |
| Token.start / 60 / 20 | NO_COVERAGE | Brace error location |
| error / 64 / 21 | NO_COVERAGE | Brace syntax rejection |
| current / 109 / 37 | NO_COVERAGE | Unmatched delimiter token |
| Token.start / 110 / 38 | NO_COVERAGE | Unmatched delimiter location |
| error / 113 / 39 | NO_COVERAGE | Unmatched delimiter rejection |
| current / 132 / 42 | NO_COVERAGE | EOF error token |
| Token.start / 133 / 43 | NO_COVERAGE | EOF error location |
| error / 137 / 44 | NO_COVERAGE | EOF syntax rejection |

Freeze reports before later PIT invocations. Compare class, method, descriptor, mutator, indices
and blocks, then statuses; retain all 787 identities and all 721 prior kills. Scoped R6 applies;
R5/R7 skip and require the separate default full gate. Resource errors never earn kills. Compiler,
Parser, default and historical populations stay separate. No new equivalence dispositions.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 test \
  --tests dev.samhb.mutationharness.CompilerWorkBudgetTest
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

## Results

All sixteen compiler-harness cases pass (six additional cases), alongside nine new application
cases. Integrated **1,191 tests**, build, strict application/agent Javadoc and extra private-member
doclint pass, zero failures/errors/skips. Final compiler: **733/787 KILLED, 32 SURVIVED,
22 NO_COVERAGE**, zero resource/run errors. All 787 identities and all 721 prior kills remain.
Exactly the twelve selected entries become KILLED (three SURVIVED, nine NO_COVERAGE); all other
statuses are unchanged. Remaining 54 raw residuals comprise 13 existing scoped dispositions and
41 untriaged entries. Scoped R6 passes; R5/R7 skip as designed.

CodeRabbit CLI 0.8.1 completed the seven-file review with zero findings after supported browser
reauthentication. Agent/shipping artifact isolation, all 18 normalizer cases, relative links,
credential-pattern and diff checks pass. The measured agent hash matches the final build.
The separate default full gate passes **281/291 KILLED, 10 SURVIVED**, zero uncovered/resource/run
errors. All 291 identities/statuses match Spec 62; full R5/R6/R7 pass. The agent hash remains unchanged. Browser checks are unnecessary for unchanged
application/rendering; remote CI is unverified.
