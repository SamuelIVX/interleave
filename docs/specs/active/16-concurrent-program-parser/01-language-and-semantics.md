# 16.01 — Language and Observable Semantics

## TL;DR

`.ilv` describes atomic actions and explicit finite control flow. This document owns syntax and
scheduling semantics. [02](02-control-flow-and-atomic-execution.md) owns their execution;
[03](03-parser-validation-and-lowering.md) owns numerical limits and diagnostic locations.

## Grammar

Keywords and identifiers are case-sensitive. `UINT` is a nonnegative decimal integer literal;
counts are literals, not expressions. `IDENT` matches `[a-z][a-z0-9_]*`, at most 64 characters.
Reserve `program shared local thread atomic when if else repeat while limit invariant always final
expected int bool true false tid`. Keywords cannot be declaration/program identifiers.
Whitespace, `//` line comments and non-nested `/* ... */` comments separate tokens. Unterminated
comments are errors. Comments may separate expression tokens without concatenating identifiers.

```ebnf
program     = "program", IDENT, "{", sharedDecl, {sharedDecl}, {localDecl}, {helperDecl},
              threadDecl, {threadDecl}, [property], [expectation], "}" ;
sharedDecl  = "shared", type, IDENT, "=", initializer, ";" ;
localDecl   = "local", type, IDENT, "=", initializer, ";" ;
type        = "int" | "bool" | "int", "[", "]" ;
initializer = signedInt | "true" | "false" | "[", [signedInt, {",", signedInt}], "]" ;
helperDecl  = "helper", IDENT, [formals], ["->", scalarType], helperBody ;
formals     = "(", [parameter, {",", parameter}], ")" ;
parameter   = scalarType, IDENT ;
scalarType  = "int" | "bool" ;
helperBody  = "{", {statement}, ["return", expr, ";"], "}" ;
helperCall  = "call", IDENT, ["(", [expr, {",", expr}], ")"], ["->", scalarTarget], ";" ;
scalarTarget= IDENT | "local", ".", IDENT ;
threadDecl  = "thread", UINT, block ;
block       = "{", {statement}, "}" ;
statement   = atomic | conditional | repetition | boundedLoop | helperCall | assertion ;
assertion   = "assert", "(", expr, ")", ";" ;
atomic      = "atomic", ["when", "(", expr, ")"], atomicBlock ;
atomicBlock = "{", {atomicStmt}, "}" ;
atomicStmt  = assignment, ";" | atomicIf | atomicRepeat | atomicLoop | nestedAtomic | assertion ;
nestedAtomic= "atomic", ["when", "(", expr, ")"], atomicBlock ;
conditional = "if", "(", expr, ")", block, ["else", block] ;
repetition  = "repeat", UINT, block ;
boundedLoop = "while", "(", expr, ")", "limit", UINT, block ;
atomicIf    = "if", "(", expr, ")", atomicBlock, ["else", atomicBlock] ;
atomicRepeat= "repeat", UINT, atomicBlock ;
atomicLoop  = "while", "(", expr, ")", "limit", UINT, atomicBlock ;
property    = "invariant", ("always" | "final"), "(", expr, ")", ";" ;
expectation = "expected", ("PASS" | "VIOLATION" | "DEADLOCK"), ";" ;
```

`signedInt`, `expr` and `assignment` use the existing declarative parser's actual operator grammar:
32-bit decimal integers, booleans, `tid`, shared/local references, array access, parentheses, unary
`! -`, and binary `+ - * % == != < <= > >= && ||`. Assignment is `lhs = expr`, where `lhs` is an
existing scalar or indexed shared/local array target. Division, expression calls, increment syntax
and chained assignment are rejected. Preserve existing precedence, left associativity and short-circuit rules.

## Declarations and properties

Types and literal initializer shapes must match. `int[]` is supported for shared and local arrays,
matching current `DslLocalArrayTest` and implementation, rather than obsolete scalar-only text in
spec 09. All names are unique across shared/local/helper declarations; shadowing is rejected.
Each thread receives its own initialized copy of the common local declaration schema. Reference
locals with `local.name`; a bare identifier denotes shared state. Thread IDs are sequential from 0.
Shared/local declarations precede helper definitions, then threads, then the optional property
and expectation.

A property must be boolean and use shared fields only; `local.*` and `tid` are rejected, including
inside array indices. `always` checks the initial state and every committed configuration; `final`
checks only when all threads terminate. Multiple predicates use `&&` within the one property.
The expected verdict is metadata, never evidence and never an input to search pruning.

## Reusable scheduled helpers

`helper` and `call` are contextual words, so existing declarations with those names remain legal.
Legacy helpers take no arguments and return no value. Optional typed scalar formals and one final
scalar return are defined in [Spec 83](../83-bounded-helper-values.md). Formals are read-only bare
names; actual arguments are captured together before the scheduled body. Return expressions read
completion-time state and copy into a required scalar field/local target. A value helper requires
one final top-level return; void helpers cannot return. The contextual return word preserves existing
fields with that name. Arrays as parameters/results/return targets remain unsupported.

Helpers may call earlier definitions only; forward references and recursion are rejected. Calls
are allowed in scheduled control flow, including branches/loops, but not inside atomic interiors.
Unused definitions/zero-repeat calls are still validated. Formals use hygienic per-owner slots,
charged with declared locals toward eight total slots and reset on successful completion. Acyclic
calls cannot reenter the same helper while its slots are active. Active values participate in
state identity and symmetry frames. Nonempty parameters add one atomic entry and one exit; a
zero-argument value helper adds only a return exit. Zero-argument void calls retain body-only PCs.
Definition/argument/return spans and actual generated PCs remain replayable. Existing zero-argument
bounds and evidence are in [Spec 71](../71-bounded-source-helpers.md).

## Statement assertions

`assert (condition);` checks a boolean at that statement. At thread/helper level it is one
scheduled action; inside an atomic body it checks tentative values privately. True advances or
continues; false is VIOLATION with ASSERTION/STEP evidence and the actual assertion span. A private
failure rolls back the entire outer action, even if the global property stays true. Evaluation
errors retain RUNTIME_ERROR classification. First encountered terminal event wins.
`assert` is contextual before `(`; existing declaration/program names and assignments remain valid.
Messages/extra arguments are unsupported. See [Spec 73](../73-source-statement-assertions.md).

## Scheduling and blocking

Outside atomic blocks an `if` evaluates its condition once in a scheduled action and advances into
the chosen arm or continuation. False selects else/continuation and never means blocked. Another
thread may execute between that decision and the first selected atomic block. `while` condition
checks use the same scheduling rule. Branch/loop conditions have no side effects.

Each outer atomic block is one action, including its nested control flow. Assignments execute in source
order; later expressions see earlier tentative writes. Internal decisions, iteration bookkeeping
and assignments add no scheduler choices, snapshots or global-property observations.
An empty atomic block is a successful no-op action. Empty arms, empty threads and `repeat 0` are
valid; expansion inserts no synthetic no-op action for them.

`atomic when (g)` has one outer entry guard. False disables this thread's next action until the
state changes. A guard evaluation error remains enabled so execution can report VIOLATION.
An unguarded nested `atomic { ... }` groups private statements within the outer action; it adds no
scheduling, observation, commit or rollback boundary. Empty nested blocks add no work. A reached
inner `atomic when (g)` reads tentative values in source order. False disables the entire outer
action, discarding all tentative writes and retaining its PC. Earlier failure/cutoff wins; later
code is not reached. Unchosen branches/zero repetitions do not evaluate their guards. Enabledness
previews guarded interiors on a private copy; errors/cutoffs stay enabled for actual evidence.
See [Spec 82](../82-guarded-nested-atomics.md). With live threads but no
enabled action, the configuration is a deadlock; a loop cutoff is a different terminal event.

## Repetition and errors

`repeat N` runs exactly N copies. A bounded while tests before each body and after its last allowed
iteration: false exits normally; true after N completed bodies emits LOOP_LIMIT. Thus at most N
bodies and N+1 checks occur per invocation. `limit 0` still evaluates once. A nested loop receives
fresh allowance on every invocation, including every enclosing repeat/while iteration.

For an atomic block, the entire block commits only after successful completion. Runtime evaluation
errors produce VIOLATION; internal cutoff produces INCOMPLETE. Both discard tentative writes.
An error encountered before a bound cutoff wins; code after a cutoff is not executed. A global
`always` predicate false only in an atomic interior does not fail the model.
Arithmetic wraps as Java `int`; array bounds and modulo-by-zero errors retain existing failure
semantics. Source/typing errors are load failures, not modeled violations or incomplete searches.

## Teaching anchor

```text
program lost_update {
    shared int counter = 0;
    local int r = 0;
    thread 0 {
        atomic { local.r = counter; }
        atomic { counter = local.r + 1; }
    }
    thread 1 {
        atomic { local.r = counter; }
        atomic { counter = local.r + 1; }
    }
    invariant final (counter == 2);
    expected VIOLATION;
}
```

With no property, final counter values are 1 and 2. The schedule `[0,1,0,1]` reaches 1. Replacing each
thread body by `atomic { counter = counter + 1; }` makes only final value 2 reachable.

## Acceptance

Require syntax acceptance/rejection cases for every production; empty constructs; local-array
support; property timing; false/erroring guards; branch decision/body interleavings; ordered atomic
effects; zero/early/exact-N loop exits and true final cutoff; and both lost-update variants.
Validate values and scheduling boundaries against hand-written expectations, not generated output.

## Implementation evidence

`SourceProgramLoaderTest` and `SourceAcceptanceTest` exercise final-value/schedule oracles, nested allowance reset, atomic observation boundaries, guards and runtime rollback. The thirteen `.ilv` examples are loaded through all benchmark rows by `SourceAcceptanceTest`. See [set evidence](README.md#implementation-evidence) for measured checks and outstanding review/CI gates.
