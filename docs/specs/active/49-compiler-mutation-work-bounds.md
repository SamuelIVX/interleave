# Spec: Compiler mutation work bounds

## Objective

Begin roadmap 9-V2 with a reproducible, bounded outer-SourceCompiler mutation run. Retain the
existing huge-count regression fixtures while making faulty unrolling and tokenizer progress
fail explicit logical assertions instead of exhausting memory or relying on wall-clock kills.

## Scope

Extend the existing PIT-only agent with a dedicated CompilerWorkBudget transformer. Its two
invocation-local counters cover expand and tokenize only. Keep the stable agent entry point and
existing selector transformer; no general instrumentation framework or production hooks are added.

Add ten independent harness cases and a child-JVM compiler probe, register the agent JAR as a PIT
task input, and include compiled harness classes in the existing report-freshness check. Keep R6
non-assertion policy, R5 denominator, R7 floor, mutation targets/test filters/operators/exclusions,
heap/time limits, dependencies and CI parallelism unchanged. Production source/API/output is
unchanged. The freshness guard is strengthened to cover the additional executable test source set.
Agent code remains outside published library/application artifacts.

This is a first bounded census of the outer compiler, not a remeasurement of Spec 16's entire
historical 1,491-mutant frontend scope. Residual survivors/uncovered are not declared equivalent or
silently assigned. No whole-compilation resource bound or whole-compiler verification is claimed.

## Requirements

1. A supported expand invocation SHALL perform at most64 unrolling ancestry events. Enforce the
   bound before ancestry allocation with a fresh local integer counter.
2. Tokenize SHALL emit at most clean.length()+1 tokens, including EOF. Enforce that bound with a
   fresh local long counter, widening length before addition to avoid integer overflow.
3. Bind exactly the instance expand(List,List), tokenize() and immutable clean:String layout.
   Initial definitions SHALL have three ancestry and two token-add checkpoints. A PIT redefinition
   may remove one checkpoint in a method for a single-call-removal mutant; the other progress
   guards remain intact. No field, helper-call dependency or schema change is injected.
4. Missing methods/checkpoints/field bindings SHALL reject initial definitions with nonempty invalid
   class bytes. ClassFile.verify SHALL accept generated mutant/instrumented bytes in harness tests.
5. Preserve every kill from the first bounded census and all 787 semantic identities. Resource/run
   errors SHALL remain unearned and fail scoped R6. Aggregate R5/R7 remain skipped in scoped runs.
6. Changes to the agent JAR SHALL invalidate PIT task caching. Standalone mutationRatchet SHALL
   reject a report older than newly compiled harness code. Do not compare source mtimes or use
   agent-JAR mtimes as a replacement for content-based task inputs.

## Independent bounds and fixtures

### Unrolling

compile rejects root work greater than64. Each private atomic body also passes its own64-unit
check before recursion. IF branches and any invoked positive-repeat/while body have no more work
than their enclosing validated list; a zero repetition never invokes its body. Thus every expand
frame reached by the original compiler has work at most64.

A nonempty REPEAT charges one ancestry event per iteration and consumes at least one work unit
per iteration. Empty-work repeats are skipped entirely. WHILE charges its final limit marker plus
one event per unrolled check; these count+1 units are already included in work. Other nodes have
no direct ancestry events in the current frame; child frames own their own counters. Summing the
current frame's events therefore stays within64. This does not bound the total recursive tree or
all compilation resources for arbitrary altered algorithms.

The probe checks literal compiled counts[64,64,1,0]:64 one-action repeats;63 while checks plus one
limit instruction; one scheduled atomic action with64 private assignments; and a huge empty repeat.
Manual enumeration and independent Python range enumeration confirm64,63+1,1 and2147483647*0.
An empty source and a huge nonempty repeat must still be rejected normally. Existing huge-count
application fixtures remain unchanged and remain eligible for PIT.

### Token emission

Each original tokenizer outer iteration advances at least one UTF-16 source unit before emitting
at most one token. Whitespace consumes units without tokens; word and two-character operator scans
advance farther. One EOF token follows, so N source units permit at most N+1 emitted tokens. The
comment mask preserves source length. A long local keeps N+1 exact without adding a public cap.

The two observed memory failures reverse the whitespace and two-character-operator increments.
They revisit text and repeatedly emit tokens; the assertion fires after N+1 emissions. Negative
harness probes alter those actual compiled increments, not a copied lexer or a custom callback.

### Harness checks

Ten cases come from seven test methods. Seven cases launch eight child JVMs; the two missing-
method cases and the mutable-field case validate instrumentation in-process. Both agent and no-agent boundary behavior are checked.
Actual compiled zero-work and instruction-cap comparisons are negated independently, and the two
observed tokenizer increments are reversed independently. Children must report the exact logical
assertion, not merely exit nonzero. Checkpoint-removal cases require verifier-clean redefinition
bytes and actual ClassFormatError on initial drift. The supplied defining loader resolves compiler
and Gson types during frame generation/verification.

These harness tests/probe remain outside PIT's unchanged application-test filter. Their15-second
startup watchdog cannot earn application mutation kills. All child processes are reaped. New
helpers follow the existing isolated-process test pattern; no model state, private callbacks or
synthetic configurations are introduced.

## Baseline, experiments and verification

PR #90 merged ataf78f45. Fresh ordinary baseline:1,003 tests and strict application/agent Javadoc
pass, zero failures/errors/skips. Do not rerun the known unbounded historical scope merely to
reproduce its resource failures; the old 13 MEMORY_ERROR/1 TIMED_OUT figures remain historical.

The first current bounded experiment added only the expansion assertion: SourceCompiler587/787
KILLED,139 SURVIVED,59 NO_COVERAGE,2 MEMORY_ERROR; scoped R6 FAILED. Both failures are tokenize/26
and tokenize/96 IncrementsMutator. Adding the token-emission bound gives the prototype589/787
KILLED,139 SURVIVED,59 NO_COVERAGE, zero resource/run errors. All787 identities match and all 587
prior kills remain; only those two memory failures become KILLED. Neither failed result earns a
kill retroactively. No transition count is asserted against the different historical 1,491 scope.

A changed external prototype JAR initially left pitest UP-TO-DATE and reused the failed report.
Declaring it as a task input forced actual execution and the corrected successful census. The
permanent task now hashes the real agent JAR, while the existing freshness check also considers
compiled mutationHarness classes. An initial freshness rejection correctly identified newer test
classes; an isolated agent-only diagnostic change then produced the expected stale-report rejection
pointing to classes/java/mutationHarness. The exact source was restored before final checks.

The original nine new cases, five existing selector-harness cases and the full 1,012-test suite passed, zero
failures/errors/skips. The clean build and strict application/agent Javadoc pass. Integrated
SourceCompiler remains 589/787 KILLED,139 SURVIVED,59 NO_COVERAGE with zero resource/run errors.
All787 identities match the first bounded census and all 587 prior kills remain; only tokenize/26
and/96 MEMORY_ERROR become KILLED. Scoped R6 passes; aggregate R5/R7 skip. Separate default
full gate passes281/291 KILLED,10 SURVIVED; all291 identities/statuses match the merged V1
closeout. Selector regression remains111/115 KILLED,4 SURVIVED with all115 identities/statuses
unchanged. Both runs have zero uncovered/resource/run errors. Agent byte hashes exactly match
the measured compiler run after restoring the temporary diagnostic edit. Shipping JAR/distribution
exclude harness code. All18 normalizer checks and strict probe Javadoc pass. CodeRabbit CLI0.8.1
completed the eight-file initial review with zero findings; only final status prose and an
ownership comment changed afterward, with identical agent bytecode. Relative-link, credential-
pattern and diff checks pass. All local Gradle commands
use `--max-workers=1 -PpitestThreads=1`. Browser checks are outside this test-infrastructure change
because production/report output is unchanged. Remote CI is unverified; publish a draft and stop.
The 198 raw compiler non-kills require separate triage before broader closure; this PR does not
claim equivalence or silently expand completed 9-V1 work.

### PR review follow-up

The binding now explicitly requires clean:String to be FINAL as well as non-static. A new case
removes only FINAL from the actual compiler, verifies the changed class remains valid bytecode,
and requires rejection for both initial definition and redefinition. It failed before the fix
because instrumentation accepted the mutable field. Final follow-up clean build, strict application/agent Javadoc and all1,013 tests pass, zero
failures/errors/skips. Compiler remains589/787 KILLED,139 SURVIVED,59 NO_COVERAGE with all787
identities/statuses unchanged and zero resource/run errors. Scoped R6 passes; aggregate R5/R7
skip. CodeRabbit CLI is not repeated for this minor feedback. The prior separate default/selector
measurements above were not rerun for this binding-only follow-up.
