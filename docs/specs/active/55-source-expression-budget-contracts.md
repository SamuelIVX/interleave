# Spec: Source expression depth and node budgets

## Objective and scope

Continue9-V2 with local expression validation before recursive semantic visitors and aggregate
work accounting. Add SourceExpressionBudgetContractTest, this spec, the roadmap and a Spec16
evidence pointer. No production, harness, dependency, mutation target/filter/operator/exclusion,
denominator/floor, heap/time or CI parallelism changes. Delimiter scanning, aggregate expression
work, expansion arithmetic and lowering remain separate. Completed9-V1 stays closed.

## Contract and independent fixtures

[Spec16.03](16-concurrent-program-parser/03-parser-validation-and-lowering.md) makes expression
depth16 and expression/aggregate nodes5,000 inclusive, with local validation before recursive
semantic visitors. The parser has already constructed the bounded input AST at this point. Seven cases use four test methods:

- Four RHS spines independently exercise left/right binary children, unary operands and array
  indices. Fifteen edges above a leaf give depth16 and must load; sixteen give depth17 and must
  fail with the exact expression-depth diagnostic at the assignment start. Unary operands end in
  a variable so the parser cannot fold a signed literal instead of constructing the unary node.
- Nested array LHS accesses need their own validation, including the outer access. Pair depth16
  with17 while keeping RHS0 shallow, isolating the target check from the RHS check.
- Boolean condition spines pair depth16 with17 and require the exact original diagnostic,
  without a second source prefix when the condition catches an already-located validation error.
- A balanced boolean tree of2,500 leaves has4,999 nodes; one unary node gives exactly5,000.
  A balanced tree of2,501 leaves has5,001 nodes. Use one property and an empty owner so the
  accepted5,000-node expression has no other instruction expressions consuming aggregate budget.
  For5,001, require the local expression error rather than a later aggregate-budget rejection.

Independent Python direct-tree/token counts agree with the full-binary closed form2L-1. The
accepted tree has depth14 and19,997 expression UTF-8 bytes; rejected tree depth13 and20,004 bytes.
Both are below the depth/source-byte limits. These are bounded grammar-valid, typed fixtures,
not resource-exhaustion probes. Java does not use production Expr.nodeCount/depth as its oracle.
Manual source prefixes and Python enumeration agree on literal positions: assignment6:1,
condition5:5 and property expression5:19. Exact messages also prevent downstream validation from
masking a removed local guard. No wall-clock/resource outcome earns a kill.

## Fresh baseline and reproduction

Merged PR96 atdc45a58 passes the clean build, strict Javadoc and1,102 tests, zero failures/errors/
skips. Fresh outer-compiler baseline is **654/787 KILLED,97 SURVIVED,36 NO_COVERAGE**, zero
resource/run errors. The20 selected entries comprise19 SURVIVED and one NO_COVERAGE. Other113
raw non-kills include the two existing scoped dispositions and111 untriaged entries outside this
selection. No whole-compiler/checker closure is implied.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
```

Freeze mutations.xml before later PIT runs. Compare class, method, descriptor, mutator, indices
and blocks, then statuses; retain all787 identities and all654 baseline kills. Scoped R6 applies;
aggregate R5/R7 skip and need a separate full-default run. This outer-class scope is distinct from
overlapping combined/historical reports.

## Selected mutation ledger

NVMC means NonVoidMethodCallMutator, VMC means VoidMethodCallMutator, BOUND means
ConditionalsBoundaryMutator, INC means IncrementsMutator and MATH means MathMutator. S means
SURVIVED; NC means NO_COVERAGE. All sites use unchanged SourceCompiler bytecode. Each method has
one descriptor. All selected entries except targetExpression/requireNonNull are KILLED in the final
run; that entry remains raw SURVIVED.

| Method /mutation /index /block | Baseline | Contract or disposition |
|---|---|---|
| checkExpression /BOUND /49 /11 | S | Exactly5,000 nodes are accepted |
| checkExpression /INC /46 /11 | S | A5,001-node expression must hit its local guard |
| checkExpression /NVMC error /54 /12 | NC | Node overflow is a categorized RegistryException |
| checkExpression /MATH /89 /22 | S | Right-child depth advances |
| checkExpression /MATH /114 /29 | S | Unary-child depth advances |
| checkExpression /MATH /139 /36 | S | Array-index depth advances |
| checkExpression /NVMC right /85 /20 | S | Right subtree is validated |
| checkExpression /NVMC depth /87 /21 | S | Right subtree retains parent depth |
| checkExpression /NVMC expression /97 /25 | S | Unary node is inspected |
| checkExpression /NVMC operand /110 /27 | S | Unary operand is validated |
| checkExpression /NVMC depth /112 /28 | S | Unary operand retains parent depth |
| checkExpression /NVMC expression /122 /32 | S | Array-access node is inspected |
| checkExpression /NVMC index /135 /34 | S | Array index is validated |
| checkExpression /NVMC depth /137 /35 | S | Array index retains parent depth |
| checkExpression /VMC push /91 /23 | S | Right subtree is enqueued |
| checkExpression /VMC push /116 /30 | S | Unary operand is enqueued |
| checkExpression /VMC push /141 /37 | S | Array index is enqueued |
| condition /VMC checkExpression /31 /7 | S | Conditions receive their own depth validation |
| block /VMC checkExpression /299 /82 | S | Assignment target receives its own depth validation |
| targetExpression /NVMC requireNonNull /5 /0 | S | Parsed non-null LHS disposition below |

The last entry is retained under the supported parsing pipeline. Both private helper call sites
(block validation and expressionWork) use the LHS returned by Parser.parseEffect, directly or
through the stored assignment node. Parser.parseLhs returns a concrete non-null LHS or throws;
no supported input supplies null. Name/type resolution need not have completed for that non-null
guarantee. This is a scoped disposition, not an assertion kill, exclusion or equivalence for
reflection, malformed hand-built effects or future callers. Revisit if an input path changes.

## Additional kills from the same fixtures

Four baseline non-kills outside the selected20-entry partition also become KILLED. The local
5,000-node property simultaneously reaches aggregate equality; exact validation diagnostics pin the
already-located error; the valid empty conditional body exercises its forward join. These are
measured additional kills, not broader closure of aggregate work, diagnostics or lowering.

| Method /mutation /index /block | Baseline | Assertion |
|---|---|---|
| compile /BOUND /545 /152 | S | Exactly5,000 aggregate expression nodes are accepted |
| diagnostic /NVMC startsWith /12 /1 | S | Existing source prefix is retained exactly once |
| lower /NVMC Fragment.start /81 /19 | S | Valid empty conditional arm must compile |
| lower /NVMC List.add /89 /22 | NC | Empty true arm retains its pending forward edge |

## Verification

The clean build, strict Javadoc and **1,109 tests** pass, zero failures/errors/skips. Final compiler
is **677/787 KILLED,76 SURVIVED,34 NO_COVERAGE**, zero TIMED_OUT, MEMORY_ERROR, RUN_ERROR, NON_VIABLE
or other statuses. All787 identities and all654 baseline kills remain. Exactly23 entries become
KILLED:19 selected (18S+1NC) and four additional (3S+1NC). Every other status is unchanged.
checkExpression and condition have no residual mutations in this outer-class report; the
selected block entry is a subset and targetExpression retains its scoped non-null-input survivor.
Scoped R6 passes; aggregate R5/R7 skip. Remaining110 raw non-kills comprise three scoped
dispositions (existing EOF-end/property-walker plus parsed-LHS input) and107 untriaged entries.

All18 normalizer checks, relative file links, credential-pattern and diff checks pass. Initial
CodeRabbit CLI0.8.1 completed all four changed files with zero findings. Only final evidence/status
prose and a clarification that parsing precedes local AST validation followed review; no test/
production logic changed. Separate full-default281/291 KILLED,10 SURVIVED passes, zero uncovered/resource/run errors;
all291 identities/statuses match Spec54. Full R5/R6/R7 pass. Browser checks are
skipped because production/report output is unchanged; remote CI is unverified. No whole-compiler
resource guarantee/checker closure. Do not count dispositions as kills or label untriaged residuals
equivalent.
