<!-- Raw revision-scoped mutation outcomes for guarded private atomic blocks. -->
# 82 — Guarded nested atomic mutation evidence

## TL;DR

Fresh main baseline `9a5adb9` and final B4 implementation use the same compiler/instruction targets,
operators and target tests. Final raw outcomes are compiler **859/910 K + 29 S + 22 NC** and
instruction **210/214 K + 4 S**, zero resource/run errors. Dispositions do not count as kills.
See [Spec 82](../specs/active/82-guarded-nested-atomics.md) for execution/admission contracts and commands.

## Population and lineage

| Scope | Baseline K / total | Final K / total | S | NC | Identity/status accounting |
|---|---:|---:|---:|---:|---|
| SourceCompiler | 860 / 911 | 859 / 910 | 29 | 22 | All 750 sites outside block/work match exactly. Block changes 142 to 140 (134 to 132 K, eight S retained); work changes 19 to 20, all K. |
| SourceInstruction | 187 / 189 | 210 / 214 | 4 | 0 | All 98 sites outside constructor/enabled/transition/footprint/executeNodes match exactly. |

Compiler residual method/descriptor/mutator/description/status multisets agree with baseline.
The two instruction metadata/usesTid survivors retain exact identities/statuses. New footprint
survivors are the two preview-work dispositions below. Rewritten methods shift/reuse bytecode
indices; do not claim all raw IDs unchanged or count population growth as inherited kills.
Instruction changed-method populations: constructor 7→7; enabled 11→17; transition 40→42;
executeNodes 17→28; footprint 16→22 (20 K + 2 S). No inherited NC is newly classified unreachable.

An exploratory implementation also checked GUARD bodies as separate atomic-budget boundaries,
adding four compiler survivors. Those duplicate checks were removed: private blocks do not own
budget scopes; original outer-atomic checks already count expanded guards/bodies. Preserve the
existing zero-repeat and unused-helper policy rather than adding a new per-inner admission rule.
Exploratory XML is `/private/tmp/interleave-guarded-atomic-first-scoped.xml`, not the final census.

## New preview-work dispositions

`SourceInstruction.footprint` still collects complete reads/writes when its kind comparison is
negated or its final boolean is forced true. Both mutations make the root ATOMIC report a guarded
interior, causing additional previews for ordinary atomics. Without private guards, executeNodes
returns true on success; errors/cutoffs keep enabledness true. With private guards, normal previews
still determine false guards. Immutable instructions, faithful DynamicState copies and deterministic
model inputs therefore preserve modeled enabledness, traversal and evidence without external
resource stops. Preview allocations/work, wall time, resource limits and host failures can differ;
this is not universal or performance equivalence. Do not manufacture an impure source instruction
or delete the fast path to force a kill. A future preview observer or state/copy contract change
reopens both dispositions; ownership stays with the source execution maintainer.

Other residuals remain visible with their prior scoped dispositions; this feature does not reopen
closed compiler ledgers or claim whole-checker mutation completeness. S = SURVIVED; NC = NO_COVERAGE.
Indices/blocks belong to this source/JDK snapshot, not source-line citations. Class names expand
under `dev.samhb.interleave.format.dsl`; mutators under
`org.pitest.mutationtest.engine.gregor.mutators`. Method descriptors remain in frozen XML; each
listed name is unambiguous in the current source signatures. Artifacts:
`/private/tmp/interleave-guarded-atomic-baseline.xml` and
`/private/tmp/interleave-guarded-atomic-complete-scoped.xml`.

## All 55 raw residuals

| Class | Method | Indices / blocks | Mutator | Raw | Operation |
|---|---|---|---|---|---|
| SourceCompiler | `block` | 120 / 36 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 185 / 52 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 226 / 62 | `NonVoidMethodCallMutator` | S | SourceCompiler$Token::end |
| SourceCompiler | `block` | 227 / 63 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 277 / 76 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 335 / 92 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 460 / 127 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `block` | 554 / 154 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `cappedAdd` | 14 / 2 | `MathMutator` | S | Replaced long subtraction with addition |
| SourceCompiler | `cappedMultiply` | 28 / 5 | `MathMutator` | S | Replaced long division with multiplication |
| SourceCompiler | `compile` | 225 / 67 | `ConditionalsBoundaryMutator` | NC | changed conditional boundary |
| SourceCompiler | `compile` | 726 / 195 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `compile` | 735 / 198 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
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
| SourceCompiler | `expand` | 51 / 15 | `NonVoidMethodCallMutator` | S | java/util/List::of |
| SourceCompiler | `expand` | 260 / 85 | `NonVoidMethodCallMutator` | S | SourceCompiler$Node::count |
| SourceCompiler | `lower` | 68 / 20 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 109 / 30 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 152 / 41 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 178 / 50 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| SourceCompiler | `lower` | 61 / 17 | `NonVoidMethodCallMutator` | S | SourceCompiler$Fragment::start |
| SourceCompiler | `targetExpression` | 5 / 0 | `NonVoidMethodCallMutator` | S | java/util/Objects::requireNonNull |
| SourceCompiler | `tokenize` | 128 / 32 | `NonVoidMethodCallMutator` | S | java/lang/String::length |
| SourceInstruction | `footprint` | 48 / 15 | `NegateConditionalsMutator` | S | negated conditional |
| SourceInstruction | `footprint` | 116 / 34 | `returns.BooleanTrueReturnValsMutator` | S | replaced boolean return with true for SourceInstruction::footprint |
| SourceInstruction | `metadata` | 24 / 8 | `NonVoidMethodCallMutator` | S | java/lang/String::isEmpty |
| SourceInstruction | `usesTid` | 12 / 2 | `NonVoidMethodCallMutator` | S | java/util/Objects::requireNonNull |
