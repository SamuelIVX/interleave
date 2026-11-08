<!-- Scalar helper capture/return semantics, hygienic finite frames and expansion accounting. -->
# 83 — Bounded scalar helper parameters and returns

## TL;DR

Implement the first B5 / G capability from the [post-task-#10 inventory](../../plans/post-task10-follow-ups.md)
after guarded nesting merged in PR #124 at `dff320e`. Add typed read-only scalar parameters and one
final scalar return. Bundle the tightly coupled capture, lifetime, return, admission and evidence
contracts in one feature PR. Preserve existing zero-argument void helper behavior.

```text
shared int counter = 0;
local int saved = 0;
helper add(int amount) -> int {
  atomic { counter = counter + amount; }
  return counter;
}
thread 0 { call add(2) -> local.saved; }
```

Only int/bool scalar parameters/returns and scalar field/local return targets qualify. Arguments
may be existing scalar expressions, including indexed reads. Arrays as parameters/results/return
targets, early/conditional returns, expression-function calls, defaults, forward references,
recursion and helper calls inside atomic interiors remain unselected. No runtime call stack,
new strategy/store, schema, cap increase or new host-code execution.

## Call protocol and lifetime

- Parameter declarations are `(int name, bool other)` after the helper name. No parentheses keeps
  the existing zero-argument syntax; optional empty parentheses are also valid. Optional `-> int`
  or `-> bool` declares a result. `return expr;` is required as the final top-level statement in
  a value helper and forbidden otherwise. It is contextual, preserving existing fields named return.
- A call accepts positional arguments: `call helper(expr, expr);`, with `-> field` or
  `-> local.name` required exactly when a result is declared. Check arity and types even in unused,
  unchosen and zero-repeat bodies. Formal names cannot duplicate each other or shadow existing
  shared/local/helper names. Formal references are bare identifiers and read-only.
- Nonempty arguments are evaluated once, together at a single atomic call-entry action. A failing
  argument rolls back all capture writes and reports its callsite argument span. Capture occurs
  before the scheduled helper body, so intervening shared-state changes cannot alter parameter values.
- Helper body statements retain ordinary scheduling. A final atomic exit evaluates the return
  expression once against completion-time state, assigns the caller's scalar target, then resets
  this helper's parameter slots. Errors retain the definition's return span and roll back that
  entire exit. Parameterized void helpers also have a cleanup exit; zero-argument value helpers
  need only the return exit. Existing zero-argument void calls gain no entry/exit actions.
- Body runtime failure, assertion failure or cutoff terminates before exit/cleanup; committed
  captured values remain part of the authenticated terminal state. A disabled body action waits
  with the already captured values. Caller/owner IDs, actual PCs, helper/callsite labels, loop
  ancestry and original definition/argument spans remain in the existing evidence protocol.

## Hygiene and finite model

Allocate one typed local slot per helper formal, reused by sequential calls of that helper for each
owner. Declaration order already proves acyclicity: an active invocation cannot call that helper
again, and other active helpers have disjoint slots. No dynamic stack is necessary. Count declared
locals plus all helper formal slots toward the existing **eight-local cap**, including unused
helper definitions. Generated identifiers are valid bounded names absent from every written input
token, and unique across slots. User text therefore cannot name/collide with a slot. Compiler-managed
slots appear in ordinary state inspection while calls are active; do not hide state needed by keys.

Bind formal references to those owner-local slots in the validated AST, never by textual substitution.
Check raw expression depth/node bounds before recursive binding and retain read-only LHS checks.
Capture writes only callee-private slots; acyclicity and hygiene prevent any argument from reading
those slots, so sequential private assignments implement simultaneous pre-entry argument reads.
Other owners cannot interleave within capture. Cleanup resets slots to typed zero/false defaults,
allowing after-call positions to merge without stale parameter histories. Active values remain in
DynamicState equality/encoding and symmetry's whole-owner frames, preserving distinct continuations.

## Admission and lowering

Use immutable earlier definitions and cached invocation costs, preserving the DAG work bound.
A parameterized helper costs one entry + body + one exit; a zero-argument value helper costs body
+ one exit. Frame stores/cleanup are charged as actual generated private assignments. Each argument
expression is counted once at each expanded call; return expression/target and reset expressions
also count. All helpers/threads retain the 64-action cap, atomic interiors 64 private operations,
program expansion 5,000 expressions, source/expression depth 16, helper count 16 and existing byte,
token, identifier and owner limits. Reject before expanded-copy allocation; zero-work legacy calls
still short-circuit huge zero-work repeats. Preserve existing work-probe checkpoint counts/bounds; move the delimiter scanner selector
from the until wrapper to the untilAny loop and update binding-drift probes together.

Lower capture/exit into ordinary trusted source ATOMIC/ASSIGN nodes; retain the existing CALL label
wrapper. No SourceInstruction/Runtime/StateStore/replay API change. Typed locals and complete ordinary
read/write footprints account for argument/capture/body/return dependencies. Symmetry compares the
same structural capture/body/exit instructions and full frames; tid-dependent arguments keep the
existing conservative rejection. No new reduction or subset/stabilizer proof is assumed.

## Independent acceptance

Cover int/bool values, atomic capture/error rollback, immutable formals, hygiene, nested calls and
repeated lifetime cleanup, call-entry versus completion-time reads, delayed/disabled body actions,
all strategies and symmetry, actual PCs/source spans, replay/minimization, unused/type/name failures,
slot/work/expression boundaries and huge zero-work repeats. Compare supported programs with explicit
manual capture/body/return schedules and literal final values, not implementation-computed oracles.

For a helper with one atomic body and one parameter, each call has exactly three actions. Twenty-one
calls use 63 actions; twenty-two exceed 64. A parameterized void helper with no body still has two
actions and must evaluate even unused arguments. A one-parameter value identity helper has two
actions and six expression occurrences per literal call (capture two, result two, reset two).
Two callers plus a writer that sets x=1 then x=0 can capture both 0 and 1 at the same PC/shared
position. Plain terminal local-result pairs are (0,0),(0,1),(1,0),(1,1); symmetry retains three
unordered result orbits. This directly checks that active argument values participate in state keys.

## Verification plan

Fresh merged main build/tests/Javadoc and compiler/instruction PIT precede edits. Freeze XML and
compare legitimate population changes, exact unchanged-method identities and all raw residuals.
Default mutation targets/filters/operators/floors and CI parallelism remain unchanged. Every local
Gradle command uses `--max-workers=1 -PpitestThreads=1`. Run full integrated checks, default gate,
actual CLI/report/normalization and selective CodeRabbit review. Publish one draft PR and stop;
Sam supplies CI/review/merge updates. Task #10 and earlier bundles remain complete; #11/#12 and
parked roadmap work stay excluded. Wider B5 helper capabilities are not silently enabled.

## Measured evidence

Fresh main `dff320e` passes build/Javadoc and **1,693 tests**. Compiler baseline is
**859/910 K + 29 S + 22 NC**; instruction baseline **210/214 K + 4 S**. Default baseline
is **436/451 K + 15 S**. The initial new suite runs 44 cases against unsupported original syntax:
22 positive cases fail; 22 rejection cases pass. Final integrated build/strict Javadoc passes
**1,750 tests**, including 47 helper-value cases and ten added literal diagnostic cases.

The final default gate remains **436/451 K + 15 S**, with all 451 mutant identities/statuses
unchanged. Actual CLI `--source examples/programs/helper-values.ilv --store exact --json` emits
four PASS rows with seven visits each and top-level soundness true. All four fresh rows normalize
with the actual viewer function. Its 22 verdict compatibility cases and four symmetry assertions
also pass. No DOM/browser rerun: schema, normalizer and renderer are unchanged.

CodeRabbit 0.8.1 completed all 17 submitted files and raised one trivial dangling test-class
Javadoc finding. The file summary is now a plain comment and class Javadoc attaches to the class;
local integrated validation covers the patch. No CLI rerun for trivial feedback. New measurement
and roadmap cross-references receive local review. Final scoped compiler census is **1,144/1,200 K + 34 S + 22 NC**; instruction remains
**210/214 K + 4 S**. Scoped R6 passes, with zero resource/run errors. All 359 unchanged-method
compiler sites and all 214 instruction sites retain exact identities/statuses. Inherited residual
method/descriptor/operation/status multisets agree; five new supported-source dispositions remain
raw survivors. All 60 residuals and changed-method populations are in the
[measurement ledger](../../measurements/83-helper-values.md). No inherited non-kill is counted as
a new kill; no mutation filter, floor or target-test change. B5/G1 is locally complete, awaiting merge.
