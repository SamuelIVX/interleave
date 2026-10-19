# 16.03 — Parsing, Validation and Bounded Lowering

## TL;DR

Build a small source frontend around the existing expression/type/state machinery. Validate source
and expanded work before allocation. [01](01-language-and-semantics.md) owns grammar;
[02](02-control-flow-and-atomic-execution.md) owns compiled instruction behavior.

## Frontend pipeline

1. Read bounded UTF-8 source, tokenize with original source spans, and parse the complete program.
2. Validate declaration shape/order, thread IDs, names, types and literal counts. Construct the same
   `StateDecl`/`DynamicState` shapes used by declarative JSON.
3. Parse expression/assignment substrings with the existing `Parser`; reuse `TypeChecker` and
   shared declaration validation. Map substring offsets back to original source positions.
4. Calculate conservative expanded instruction/expression work using checked arithmetic.
5. Lower valid syntax into 02's forward instructions and immutable atomic trees; resolve targets and
   validate every successor. Retain a source map before returning a `BenchmarkProgram`.

Use a separate source-program parser name so callers cannot confuse it with the existing expression
`Parser`. Tokenization handles comments and brackets structurally, not with regex-based statement
splitting. Keep a mapping when removing comments/normalizing expression whitespace; comments count
as whitespace, not token concatenation. The entire input must be consumed after the final brace.

The flat JSON schema cannot encode branching or atomic control trees. Reuse its shared declaration,
expression and validation functions; do not pretend a source CFG is ordinary flat declarative JSON
or construct it by bypassing trust-boundary checks. Any extraction of shared validation preserves
the JSON path errors and existing public JSON loading contracts.

## Limits

All limits are inclusive. Load errors identify the violated limit and source span; no silent
truncation, hidden splitting of atomic blocks or clamping of counts is permitted.

| Quantity | Maximum / rule |
|---|---|
| Source | 1,048,576 UTF-8 bytes; invalid UTF-8 rejected |
| Identifier | 64 ASCII characters, grammar/reserved rules in 01 |
| Source block nesting | 16; program body is depth 1, every braced body adds 1 |
| Shared declarations | 1–32 |
| Common local declarations | 0–8 |
| Arrays | 0–64 integer elements, shared or per-thread |
| Threads | 1–8, sequential IDs in declaration order |
| Expression depth | 16, following existing Expr.depth convention |
| Aggregate expression nodes | 5,000 after expansion across all thread/global/internal occurrences plus property |
| Global instructions | 64 per thread, 512 across program; zero in a thread is valid |
| Atomic internal instructions | 64 per expanded atomic block |
| Count literals | 0–2,147,483,647; expansion may reject otherwise syntactically valid counts |

Global instruction counts include atomic instructions and every scheduled condition, including the
final bounded-while check. Internal counts include assignments and conditional evaluations on both
arms after expansion; repeat itself adds no action; a bounded while contributes N+1 tests and N
expanded bodies. Empty atomic bodies count zero internally and one globally. Count all branch arms,
even if a condition is a constant. Unreachable `repeat 0` bodies are still parsed and type checked
but contribute zero expanded executable work; original source/nesting limits still apply.

Budget expression occurrences conservatively: each expanded expression in an instruction/atomic
tree counts, even when immutable nodes could be shared in memory. Detect sums/products exceeding
the cap before allocation, using checked long arithmetic or cap-saturating calculations. A huge
zero-work repeat can be eliminated without iterating its literal count. No recursive host traversal
may run beyond validated parser/source depth.

## Types, names and diagnostics

Initializers are literals only; expression-based initialization and lexical shadowing are rejected.
Integer literals must fit signed int; unary arithmetic retains the existing parser semantics.
Conditions/guards/properties must type-check to bool; assignment targets and values must match.
Apply existing constant/dynamic-index alias rules to shared and local arrays. Reject property
references to locals/tid at any depth. Source-only empty threads do not weaken JSON validation.

Expose load failures as `RegistryException` with a stable diagnostic prefix:
`<source>:<line>:<column>: <category>: <message>`.
Categories: `syntax`, `name`, `type`, `limit`, `unsupported`, `io`. Positions are 1-based, columns
count Java UTF-16 code units; CRLF is one newline. File input uses the supplied path for local error
messages; in-memory input uses `<source>`. Exported artifact locations use a basename, not an
absolute machine path. Keep original spans plus the exact UTF-8-byte source digest defined in 06.

Name the offending token/construct or expected token. Missing delimiters point to EOF/opening span
as appropriate. Semantic failures refer to original source, not generated JSON paths. Reject input
with one actionable diagnostic; multi-error recovery and editor tooling are outside v1.

## Acceptance

Test every grammar production and operator precedence; comments around expressions and braces;
CRLF/Unicode comment positioning; truncated input and trailing garbage; invalid UTF-8; duplicate,
reserved and unknown names; local-array typing; every limit at/below/above its boundary; huge and
nested count arithmetic; zero repeats with invalid bodies; and deterministic lowered targets/spans.
Compare simple linear output with independent hand-authored declarative fixtures. For control flow,
use hand-enumerated successors and schedules, not the compiler's own output as the oracle.

## Implementation evidence

`SourceBoundaryTest`, `SourceAcceptanceTest` and `SourceCompletenessTest` exercise malformed grammar, checked expansion, inclusive budgets, Unicode/CRLF identity, local arrays and source-facing diagnostics. `SourceCompiler` reuses the existing declaration/type/expression/evaluation semantics; no parser dependency was added. See [set evidence](README.md#implementation-evidence) for measured checks and outstanding review/CI gates.
