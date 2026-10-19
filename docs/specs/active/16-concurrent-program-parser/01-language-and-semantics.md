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
program     = "program", IDENT, "{", sharedDecl, {sharedDecl}, {localDecl},
              threadDecl, {threadDecl}, [property], [expectation], "}" ;
sharedDecl  = "shared", type, IDENT, "=", initializer, ";" ;
localDecl   = "local", type, IDENT, "=", initializer, ";" ;
type        = "int" | "bool" | "int", "[", "]" ;
initializer = signedInt | "true" | "false" | "[", [signedInt, {",", signedInt}], "]" ;
threadDecl  = "thread", UINT, block ;
block       = "{", {statement}, "}" ;
statement   = atomic | conditional | repetition | boundedLoop ;
atomic      = "atomic", ["when", "(", expr, ")"], atomicBlock ;
atomicBlock = "{", {atomicStmt}, "}" ;
atomicStmt  = assignment, ";" | atomicIf | atomicRepeat | atomicLoop ;
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
existing scalar or indexed shared/local array target. Division, calls, increment syntax and chained
assignment are rejected. Preserve existing precedence, left associativity and short-circuit rules.

## Declarations and properties

Types and literal initializer shapes must match. `int[]` is supported for shared and local arrays,
matching current `DslLocalArrayTest` and implementation, rather than obsolete scalar-only text in
spec 09. All names are unique across shared/local declarations; shadowing is rejected.
Each thread receives its own initialized copy of the common local declaration schema. Reference
locals with `local.name`; a bare identifier denotes shared state. Thread IDs are sequential from 0.
Declarations precede threads, which precede the optional property and expectation.

A property must be boolean and use shared fields only; `local.*` and `tid` are rejected, including
inside array indices. `always` checks the initial state and every committed configuration; `final`
checks only when all threads terminate. Multiple predicates use `&&` within the one property.
The expected verdict is metadata, never evidence and never an input to search pruning.

## Scheduling and blocking

Outside atomic blocks an `if` evaluates its condition once in a scheduled action and advances into
the chosen arm or continuation. False selects else/continuation and never means blocked. Another
thread may execute between that decision and the first selected atomic block. `while` condition
checks use the same scheduling rule. Branch/loop conditions have no side effects.

Each atomic block is one action, including its nested control flow. Assignments execute in source
order; later expressions see earlier tentative writes. Internal decisions, iteration bookkeeping
and assignments add no scheduler choices, snapshots or global-property observations.
An empty atomic block is a successful no-op action. Empty arms, empty threads and `repeat 0` are
valid; expansion inserts no synthetic no-op action for them.

`atomic when (g)` has one outer entry guard. False disables this thread's next action until the
state changes. A guard evaluation error remains enabled so execution can report VIOLATION.
No entry guard or nested atomic block is permitted inside an atomic body. With live threads but no
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
