<!-- Revision-scoped helper-value mutation census and finite-model residual dispositions. -->
# 83 — Bounded helper values mutation evidence

## TL;DR

Fresh main `dff320e` measured compiler **859/910 K + 29 S + 22 NC** and instruction
**210/214 K + 4 S** before changes. Final scalar helper values measured compiler
**1,144/1,200 K + 34 S + 22 NC** and instruction **210/214 K + 4 S**.
No timeout, memory, nonviable or run-error outcome is counted as a kill. The scoped R6 gate passes;
scoped R5/R7 were not evaluated. The separate default run **436/451 K + 15 S** passes R5/R6/R7, with all
451 exact mutant identities/statuses matching the fresh baseline. Operators, targets, targetTests,
floors, local worker limits and CI parallelism are unchanged.

See [Spec 83](../specs/active/83-bounded-helper-values.md) for syntax, lifetime, state and admission
contracts. K = KILLED, S = SURVIVED, NC = NO_COVERAGE; dispositions never change raw statuses.

## Population and lineage

All **359 compiler sites** in unchanged methods retain exact method descriptor,
mutator, description, bytecode indices/blocks and status. All **214 instruction sites** match exactly.
Rewritten methods can reuse or shift indices; population growth is not evidence of inherited kills.
Unchanged compiler methods: `ancestry`, `cappedAdd`, `cappedMultiply`, `checkExpression`, `checkHelperAtomics`, `containsLocalOrTid`, `count`, `current`, `declaration`, `diagnostic`, `digest`, `error`, `expect`, `identifier`, `location`, `maskComments`, `patch`, `peek`, `previous`, `signedInt`, `take`, `targetExpression`, `tokenize`.

| Changed/new compiler method | Baseline sites | Final sites | Final K / S / NC |
|---|---:|---:|---|
| `<init>` | 2 | 6 | 6 / 0 / 0 |
| `bindEffect` | 0 | 19 | 18 / 1 / 0 |
| `bindParameters` | 0 | 22 | 20 / 2 / 0 |
| `block` | 140 | 159 | 151 / 8 / 0 |
| `boundExpression` | 0 | 4 | 4 / 0 / 0 |
| `capture` | 0 | 24 | 24 / 0 / 0 |
| `compile` | 176 | 202 | 172 / 8 / 22 |
| `complete` | 0 | 26 | 26 / 0 / 0 |
| `condition` | 10 | 9 | 9 / 0 / 0 |
| `expand` | 76 | 90 | 88 / 2 / 0 |
| `expressionWork` | 34 | 34 | 34 / 0 / 0 |
| `freshSlot` | 0 | 7 | 6 / 1 / 0 |
| `invocation` | 0 | 70 | 70 / 0 / 0 |
| `lambda$compile$0` | 0 | 1 | 1 / 0 / 0 |
| `localTarget` | 0 | 3 | 3 / 0 / 0 |
| `lower` | 49 | 50 | 45 / 5 / 0 |
| `parameters` | 0 | 31 | 30 / 1 / 0 |
| `returnType` | 0 | 3 | 3 / 0 / 0 |
| `scalarType` | 0 | 13 | 13 / 0 / 0 |
| `until` | 44 | 3 | 3 / 0 / 0 |
| `untilAny` | 0 | 44 | 44 / 0 / 0 |
| `work` | 20 | 21 | 21 / 0 / 0 |

The old `until` loop moved to `untilAny`; `until` now delegates. The logical-work bytecode agent
and fault-injection tests target the loop's Set/String descriptor. Checkpoint counts, scanner/token
bounds, expansion bounds and binding-drift rejection remain intact. An initial integrated run
correctly rejected stale wrapper targeting; it is not final passing evidence.

## New residual dispositions

- `bindEffect` and `bindParameters`: removing `Map.isEmpty` bypasses a fast path and reconstructs
  equivalent immutable AST nodes outside formal scope. Source semantics and shape remain the same;
  allocation/work, host failures and externally imposed resource stops may differ.
- `bindParameters`: the compiler-generated `Objects.requireNonNull` switch guard is redundant for
  depth-validated nonnull ASTs produced by the source parser. This is a supported-source disposition,
  not a guarantee for fabricated null ASTs or reflection-modified compiler state.
- `freshSlot`: the boundary mutant stops before the last candidate. The occupied token set includes
  noncandidate keywords and EOF; it cannot occupy every candidate through that bound. The bounded
  search still finds a fresh name on admitted input. Keep the explicit finite loop; do not create a
  fake token set or loosen source admission merely to force this site.
- `parameters`: removing `String.substring` substitutes null into string concatenation, producing
  a still-valid `null_` prefix. The monotonic suffix and absence-from-input check preserve distinct
  inaccessible slots. Readable generated names, bytes/hashes and raw local snapshots can change;
  only projected source semantics are preserved. The exact private prefix is not a promised format.

These five new raw survivors do not imply universal or performance equivalence. Reopen them if
source AST construction, slot visibility/naming, state observations or resource contracts change.
Ownership remains with the source compiler maintainer. Preserve production hygiene/readability
rather than deleting fast paths or asserting an undocumented prefix solely to improve PIT.

Inherited compiler residuals retain their existing scoped metadata/staging/admission dispositions;
the 22 uncovered declaration-error fallback sites are still uncovered, not newly proved unreachable.
Instruction survivors retain the prior [Spec 82 ledger](82-guarded-nested-atomics.md) preview-work,
metadata and validated-input limits. No whole-checker completeness claim is made.

## All raw residuals

Indices/blocks are revision/JDK-scoped, not source-line citations. Method names below are unambiguous
in this revision; full descriptors remain in frozen XML. Classes expand under
`dev.samhb.interleave.format.dsl`; mutators under `org.pitest.mutationtest.engine.gregor.mutators`.
Artifacts: `/private/tmp/interleave-helper-values-baseline.xml`, `-final-scoped.xml`,
`-default-baseline.xml`, `-verified-default.xml` (same prefix).

| Class | Method | Indices / blocks | Mutator | Raw | Operation |
|---|---|---|---|---|---|
| SourceCompiler | `bindEffect` | 5 / 0 | `NonVoidMethodCallMutator` | S | java/util/Map::isEmpty |
| SourceCompiler | `bindParameters` | 5 / 0 | `NonVoidMethodCallMutator` | S | java/util/Map::isEmpty |
| SourceCompiler | `bindParameters` | 14 / 3 | `NonVoidMethodCallMutator` | S | java/util/Objects::requireNonNull |
| SourceCompiler | `block` | 120 / 36 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 185 / 52 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 226 / 62 | `NonVoidMethodCallMutator` | S | SourceCompiler$Token::end |
| SourceCompiler | `block` | 227 / 63 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 277 / 76 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 438 / 121 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 550 / 150 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 652 / 180 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `cappedAdd` | 14 / 2 | `MathMutator` | S | Replaced long subtraction with addition |
| SourceCompiler | `cappedMultiply` | 28 / 5 | `MathMutator` | S | Replaced long division with multiplication |
| SourceCompiler | `compile` | 225 / 67 | `ConditionalsBoundaryMutator` | NC | changed conditional boundary |
| SourceCompiler | `compile` | 851 / 225 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `compile` | 860 / 228 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `compile` | 186 / 55 | `NegateConditionalsMutator` | NC | negated conditional |
| SourceCompiler | `compile` | 194 / 58 | `NegateConditionalsMutator` | NC | negated conditional |
| SourceCompiler | `compile` | 208 / 62 | `NegateConditionalsMutator` | NC | negated conditional |
| SourceCompiler | `compile` | 225 / 67 | `NegateConditionalsMutator` | NC | negated conditional |
| SourceCompiler | `compile` | 85 / 23 | `NonVoidMethodCallMutator` | S | SourceCompiler$Token::start |
| SourceCompiler | `compile` | 86 / 24 | `NonVoidMethodCallMutator` | S | java/lang/Integer::valueOf |
| SourceCompiler | `compile` | 87 / 25 | `NonVoidMethodCallMutator` | S | java/util/List::add |
| SourceCompiler | `compile` | 125 / 38 | `NonVoidMethodCallMutator` | S | SourceCompiler$Token::start |
| SourceCompiler | `compile` | 126 / 39 | `NonVoidMethodCallMutator` | S | java/lang/Integer::valueOf |
| SourceCompiler | `compile` | 127 / 40 | `NonVoidMethodCallMutator` | S | java/util/List::add |
| SourceCompiler | `compile` | 170 / 49 | `NonVoidMethodCallMutator` | NC | java/util/regex/Pattern::compile |
| SourceCompiler | `compile` | 172 / 50 | `NonVoidMethodCallMutator` | NC | dev/samhb/interleave/format/registry/RegistryException::getMessage |
| SourceCompiler | `compile` | 173 / 51 | `NonVoidMethodCallMutator` | NC | java/util/regex/Pattern::matcher |
| SourceCompiler | `compile` | 178 / 52 | `NonVoidMethodCallMutator` | NC | java/util/List::getFirst |
| SourceCompiler | `compile` | 180 / 53 | `NonVoidMethodCallMutator` | NC | java/lang/Integer::intValue |
| SourceCompiler | `compile` | 185 / 54 | `NonVoidMethodCallMutator` | NC | java/util/regex/Matcher::find |
| SourceCompiler | `compile` | 191 / 56 | `NonVoidMethodCallMutator` | NC | java/util/regex/Matcher::group |
| SourceCompiler | `compile` | 193 / 57 | `NonVoidMethodCallMutator` | NC | java/lang/String::equals |
| SourceCompiler | `compile` | 207 / 61 | `NonVoidMethodCallMutator` | NC | java/util/regex/Matcher::group |
| SourceCompiler | `compile` | 215 / 64 | `NonVoidMethodCallMutator` | NC | java/util/regex/Matcher::group |
| SourceCompiler | `compile` | 216 / 65 | `NonVoidMethodCallMutator` | NC | java/lang/Integer::parseInt |
| SourceCompiler | `compile` | 224 / 66 | `NonVoidMethodCallMutator` | NC | java/util/List::size |
| SourceCompiler | `compile` | 228 / 68 | `NonVoidMethodCallMutator` | NC | java/util/List::get |
| SourceCompiler | `compile` | 230 / 69 | `NonVoidMethodCallMutator` | NC | java/lang/Integer::intValue |
| SourceCompiler | `compile` | 236 / 71 | `NonVoidMethodCallMutator` | NC | dev/samhb/interleave/format/registry/RegistryException::getMessage |
| SourceCompiler | `compile` | 239 / 72 | `NonVoidMethodCallMutator` | NC | java/lang/String::replaceAll |
| SourceCompiler | `compile` | 247 / 73 | `NonVoidMethodCallMutator` | NC | SourceCompiler::error |
| SourceCompiler | `containsLocalOrTid` | 5 / 0 | `NonVoidMethodCallMutator` | S | java/util/Objects::requireNonNull |
| SourceCompiler | `current` | 11 / 1 | `MathMutator` | S | Replaced integer subtraction with addition |
| SourceCompiler | `expand` | 101 / 32 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `expand` | 311 / 102 | `NonVoidMethodCallMutator` | S | SourceCompiler$Node::count |
| SourceCompiler | `freshSlot` | 11 / 2 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 69 / 21 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 110 / 31 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 153 / 42 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 179 / 51 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 62 / 18 | `NonVoidMethodCallMutator` | S | SourceCompiler$Fragment::start |
| SourceCompiler | `parameters` | 91 / 25 | `NonVoidMethodCallMutator` | S | java/lang/String::substring |
| SourceCompiler | `targetExpression` | 5 / 0 | `NonVoidMethodCallMutator` | S | java/util/Objects::requireNonNull |
| SourceCompiler | `tokenize` | 128 / 32 | `NonVoidMethodCallMutator` | S | java/lang/String::length |
| SourceInstruction | `footprint` | 48 / 15 | `NegateConditionalsMutator` | S | negated conditional |
| SourceInstruction | `footprint` | 116 / 34 | `BooleanTrueReturnValsMutator` | S | replaced boolean return with true for SourceInstruction::footprint |
| SourceInstruction | `metadata` | 24 / 8 | `NonVoidMethodCallMutator` | S | java/lang/String::isEmpty |
| SourceInstruction | `usesTid` | 12 / 2 | `NonVoidMethodCallMutator` | S | java/util/Objects::requireNonNull |
