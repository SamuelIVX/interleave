# Spec: Bounded nonrecursive source helpers

## Objective and complete bundle

Implement selected 9-F2 in one coherent PR. The existing atomic-increment and split-increment
teaching models duplicate the same action sequences across owners. Add zero-argument reusable
scheduled sequences, retaining compiler-produced finite forward instructions and all existing
execution/search/replay/report APIs. Bundle syntax, names, expansion limits, owner binding, source
identity, independent oracles, examples, CLI/report verification and roadmap updates. 9-F3 nested
atomic syntax and statement assertions remain separate subsequent features.

Affected implementation: SourceCompiler parsing/costing/expansion/lowering and SourceInstruction
metadata labels. No new runtime call stack, dependency, trace JSON field, scheduling strategy or
reduction rule. Existing helper-free source and legacy JSON retain their behavior.

## Syntax, names and eligibility

After shared/local declarations and before threads:

```text
helper increment { atomic { counter = counter + 1; } }
helper twice { call increment; call increment; }
thread 0 { call twice; }
thread 1 { call increment; }
```

A call is `call name;`, with no arguments/return value. Bodies contain the existing scheduled
atomic/if/repeat/while forms and calls to earlier helpers. Definitions are parsed/type-checked even
when unused or called from a zero-repeat/unchosen branch. Helpers share the existing declaration
namespace with shared/local names and use the same lowercase/length/reserved-word rules. At most
16 helper definitions bound call depth. Calls must resolve to an earlier definition: unknown names,
forward references and direct/indirect recursion are rejected before expansion. This declaration
order is the acyclicity proof; no inferred runtime call graph is needed.

`helper` and `call` are contextual keywords; existing variables/programs with these names remain
legal. Calls are scheduled-sequence statements, including inside thread-level conditionals/loops.
They are rejected inside atomic interiors; scalar/array assignments to an existing field named
`call` remain valid. Helpers take no parameters and are not expression functions. All strategies
support the feature through ordinary lowered instructions; no partial strategy support is exposed.

## Expansion, limits and work accounting

A helper freezes its parsed body and saturated instruction/expression costs once. A CALL syntax
node refers to that immutable earlier definition and contributes its cached costs. Repeated DAG
composition therefore cannot make cost checks recursively traverse an exponential shared tree.
Each helper itself must fit 64 expanded scheduled actions and 5,000 expanded expression nodes.
Per-thread 64 actions, private atomic 64 instructions, global 5,000 expression occurrences,
16-level source braces, source byte/token/identifier/array/thread/declaration limits remain unchanged.
Helper definitions also validate every private atomic body, including unused/zero-repeat branches;
this declaration validation does not change helper-free dead-code handling.
Every actual call contributes expression occurrences to global accounting, including across owners.

Check all budgets before allocating expanded copies. Skip zero-work calls/repetitions without
iterating their declared repetition count; still parse/type-check/name-check their bodies. Positive
work obeys the existing saturation arithmetic. Calls do not create scheduled actions or PCs of
their own. Expansion retains bounded loop ancestry; lowering transparently joins the called fragment
to its continuation. Existing forward-target validation still checks every compiled successor.

Shared/local/tid references bind to the caller's modeled owner when SourceInstruction footprints
and execution are constructed. No state, register, visited-store or property is shared by helper
invocation beyond the model's declared state. The result has the same actions and footprints as
manually duplicating the body at each callsite. Thus the finite-source observation/terminal/sleep
arguments remain applicable without a new pruning rule or runtime call mechanism.

## Source identity and diagnostics

Keep source spans and precise runtime error/cutoff spans at the actual helper definition constructs.
PCs and thread choices identify the expanded occurrence, and existing loop ancestry identifies its
repetition. The existing bounded step label is prefixed with the outermost-first call names and
callsite line/column chain; normal source actions keep their labels unchanged. The existing label
cap still applies. This provides caller context without adding a serialized call-stack schema.

Definition/type errors point into the definition. Missing/forward/recursive names point at the call
name; call syntax/context errors point at the call. Source digest includes the complete exact input,
including definitions. Replay regenerates the same instruction PCs, definition spans, labels and
failure/cutoff evidence; minimization must regenerate all evidence as before. No stale trace may
be transplanted to edited helper source.

## Independent acceptance and regressions

- Two callers of one atomic increment retain exactly final counter=2, two one-action owners and
  four PC positions. A helper containing split load/store operations permits final counter in
  {1,2}, by sequential versus interleaved load orders. Local references are owner-qualified.
- Composed helpers, repeated calls and calls inside both thread-level branch arms/while bodies
  have the same literal outcomes, PCs and complete footprints as manually inlined fixtures.
  Calls add zero action positions. Compare all four exact strategies and replay every witness.
- Keep definition spans, bounded caller labels, loop ancestry, precise failure category/span,
  atomic rollback and separate omitted cutoff evidence. Include nested helper context and repeated
  explorer/compiler entry points, source edits, minimization and JSON/browser normalization.
- Reject bad names, collisions, forward/self/indirect calls, arguments, wrong declaration order,
  atomic-interior calls and invalid unused helper bodies. Preserve contextual-name compatibility.
- Accept 16 helpers and reject 17. Accept 64 expanded instructions and reject 65, including composed
  helper multiplication and aggregate thread calls. Check global expression 5,000/5,001 boundaries
  with every helper occurrence counted. Extremely large zero-work repetitions must finish using
  structural work bounds, not timeouts. Existing harness/probe budgets remain intact.

Derive numeric expectations outside Java and cross-check by independent enumeration or manual
counts. No private AST/cursor fabrication or implementation-derived expected values. New bounded
helper examples include actual CLI/library/report/attestation and invariant-aware replay coverage.

## Verification and publication

Fresh merged-main compiler/instruction PIT and clean integrated checks precede edits. Freeze full
semantic identities/statuses before final runs. Record legitimate compiler/instruction population
changes and raw residual dispositions separately from the default state/CBS gate; never add scopes
or credit resource errors. Default target classes, test filters, operators, floor, denominator,
PIT resource settings, local worker flags and parallel CI settings remain unchanged.

Run integrated build/tests/strict Javadoc, fresh affected scopes, default gate, normalization and
source CLI/report checks. Preserve every unrelated default identity/status. Document new source
limits/grammar/evidence semantics alongside the implementation. Complete the required initial
CodeRabbit review, patch minor feedback locally without another CLI run, publish one draft PR
and stop. No CI/review watchers after publication. Continue next with the separately selected 9-F3.

## Existing work-probe compatibility

New helper costing adds an earlier SourceCompiler.work call in compile and a cached Helper.work
getter in expand. Preserve the original defect-injection cases by matching the SourceCompiler owner
and `(List)long` descriptor, checking exactly two genuine compiler work sites per method, and
selecting repeat zero-work site 0 / thread instruction-cap site 1. The agent/checkpoints, bound
constants, assertion text, malformed-boundary cases and expected BOUND_EXCEEDED results are unchanged.
No selector failure is skipped or converted into a timeout/resource credit.

## Measured verification

Fresh merged-main baseline: 1,399 tests, clean build/strict Javadoc, SourceCompiler 739/787 K +
26 S + 22 NC, SourceInstruction 114/114 K. Final: 1,443 tests/build/strict Javadoc pass; compiler
841/891 K + 28 S + 22 NC and instruction 115/116 K + 1 S, zero resource/run errors. Scoped R6
passes, R5/R7 skip. Default 283/293 retains all 293 identities/statuses and passes R5/R6/R7.
No PIT scope/filter/operator/floor/resource or CI parallelism changes. Separate populations are
never added. Five new diagnostic/validation kills retain all 1,007 final-scope identities.

Both actual Main example reports parse and attest soundness=true; all exact strategies report
PASS for atomic helpers and VIOLATION for split helpers. All 18 normalizer checks pass. Browser
rendering was not rerun because schema/renderer are unchanged. Independent numeric/location oracles,
raw mutation counts and residual applicability are in [the evidence note](../../measurements/71-source-helpers.md).
CodeRabbit CLI 0.8.1 completed all 17 files. Its trivial finding adds a nonempty-trace assertion
before owner-local checks. Its minor suggestion to limit roadmap selection to F2/F3 is not adopted:
Sam explicitly selected sequential continuation from 9-F2 “and so on.” The roadmap clarifies that
earlier conditional recommendations do not override that order. No CLI repeat for this minor follow-up.
