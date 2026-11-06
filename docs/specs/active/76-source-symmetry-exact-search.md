# Spec 76: Opt-in source symmetry with exact DFS

## Scope and next boundary

Continue task #10 after Spec 75 / PR #117 merged at ac16444. Bundle conservative source
eligibility, real quotient visited keys, original-identity execution/replay, library and CLI
selection, reporting identity, examples, independent oracles and regression/evidence checks.
The mathematical canonicalizer becomes a public value API for this checked caller; it still
does not certify programs on its own. This is the second #10 PR, not a general explorer or memory-model rewrite. Sam's stopping point
is completion of #10; never start #11/#12 as part of this continuation.

## Eligibility and ownership

Enable only explicitly requested source DFS with the built-in exact symmetry store. Discover
disjoint groups of at least two interchangeable owners automatically. Reject legacy/JSON/custom
source-step models, unknown property callbacks, invalid owner bindings/forward targets, mismatched
initial owner counts and a model with no eligible group. No inference from diagnostic strings,
source labels, similar footprints, or arbitrary callback observation annotations.

All instructions must be trusted immutable SourceInstruction instances bound to their actual
owner. Group candidates have identical structural instruction semantics and forward targets,
identical initial local values and no tid reference, including nested guards, assignments, array
indices and assertions. Compare typed Expr/Effect structures and complete recursive control
shape, ignoring only source spans, labels, callsite names and loop ancestry. Non-grouped owners
remain fixed; a correctly bound fixed owner may use its own tid because that ID does not move.
Properties are absent or compiler-built pure shared-state SourceProperty, always or final timed.
All-termination is invariant under owner permutations. Other callbacks reject even if annotated.

The checked explorer owns a private store, so a caller cannot certify one program/property then
reuse that store against another. Use Spec 75 canonicalization for value identity only. Execute
and record original configurations, enabled owners, outcomes, source digests/locations, loop
ancestry and failure/cutoff evidence. Never execute the canonical copy or relabel witnesses.
Per-run sets are fresh; callback/limit handling remains the existing DFS contract.

## Quotient argument and output meaning

Let G permute owners within the inferred groups. Initial local equality gives initial invariance.
Identical control/Expr/Effect semantics without tid makes each owner action commute with G:
shared fields are fixed, whole local frames move, guards/errors/assertions/loop cutoffs are
preserved, and corresponding PCs/targets agree. Fixed owners access their own locals and shared
state, so their actions also commute. The accepted property observes shared state and optionally
all-termination, hence is G-invariant. Enabledness, termination and deadlock are preserved.

Spec 75 keys identify exactly value orbits. DFS expands one concrete representative per orbit;
transition equivariance preserves every reachable verdict orbit. Diagnostic identity is not a
quotient invariant: report representative concrete witnesses, not every owner-specific failure,
cutoff or execution. Replay authenticates each representative using the unchanged original program.
The proof applies to supported pure source semantics, not arbitrary callbacks, reduced strategies,
approximate stores, fairness/liveness or a mechanically checked formalization.

## User-facing integration

Library builder symmetryReduction(true) is opt-in and accepts DFS only with its built-in exact
store. Reject an explicitly supplied store/factory rather than silently ignoring it. Preserve
existing state/time limits, snapshots and ordinary result classification. Low-level checked
symmetry explorer exposes the same visitor/budget boundary for library callers.

CLI --symmetry requires one source model, --store exact and --strategy DFS; reject unsupported
selections clearly. Keep ordinary CLI runs unchanged. Existing source cutoff simplification may
consume real representative witnesses; it must retain its existing authentication contract.
Harness configuration validates the supported store/strategy before running. Reports distinguish
symmetry-enabled DFS from ordinary DFS with explicit metadata and visible viewer/Markdown identity;
retain the actual DFS strategy and exact-store labels. Counts are orbit representatives, not all
concrete states or all schedules. Do not infer a speedup from state counts or timing fields.

## Independent acceptance and verification

- Real symmetric counters: literal 16→5 states for four owners, 256→9 for eight; split load/store
  increments 13→8 with both final values {1,2}. Reuse independently derived Spec 75 arithmetic,
  and verify actual enabled search rather than offline keys. All witnesses replay in original IDs.
- Mixed noncontiguous groups and fixed owners, shared/local scalars/arrays, helper calls, branches,
  assertions, runtime errors, deadlocks and loop cutoffs retain actual verdicts and authenticated
  representative evidence. Properties include always/final and erroring expressions.
- Reject nested tid observations, semantic/target/initial differences within candidate groups,
  invalid/custom owner bindings, custom steps/properties and unsupported store/strategy options.
  Unequal threads may remain fixed alongside a valid group; do not merge them.
- Check repeat runs, input isolation, independent concurrent runners, original snapshots/labels,
  state/time budgets and default behavior. Exercise real CLI JSON/Markdown and viewer metadata.
- Fresh ac16444 baseline build/tests/Javadoc/default mutation gate before implementation. Preserve
  inherited identities/statuses, measure new classes separately and use actual default population
  accounting without changing filters/operators/resources/parallelism/floors for scores. Raw
  resource errors earn no credit; retain all survivors with scoped dispositions.
- Integrated checks, artifact normalization, browser checks, actual CLI comparisons and completed
  selective CodeRabbit review precede one draft PR. Stop after publication; Sam supplies feedback.

## Remaining #10 work

POR/DPOR/CBS and bitstate combinations reject in this phase. Separate strategy-composition proof
and integration remain the final #10 stage if needed; do not report #10 complete while required
work remains. Deferred/conditional follow-ups remain parked. Stop and explicitly notify Sam when
#10 as a whole is complete, before starting any later workstream.

## Results

Implementation and integrated build/Javadoc/1,585 tests pass (42 new cases), zero failures,
errors or skips. Private-member doclint for the four core files passes. Actual CLI comparisons,
22 verdict normalization cases plus four identity assertions, and 49 browser checks pass with
zero console errors. The workflow produces/consumes the real symmetry artifact.

Final affected-core scope: 281/285 K + 4 S, zero uncovered/resource/run errors. Six real missing
cases changed S→K in the original 285 population. Initial completed CodeRabbit review then found
one major preventive guard issue: usesTid should be exhaustive over sealed Expr. Explicit leaf
cases address it; local-array tid read/assertion tests and status wording address other findings.
After this new-method rewrite, 274 pre-review identities/statuses remain; 11 killed usesTid
identities are replaced by 11 killed identities. All 127 inherited SourceInstruction identities/
statuses match the fresh 126/127 baseline. No original survivor is claimed killed.

The post-fix default gate passes 433/449 K + 16 S with all 449 identities/statuses matching
merged ac16444 and no uncovered/resource/run errors; full R5/R6/R7 pass. Confirmation is tracked in the
[evidence note](../../measurements/76-source-symmetry-exact-search.md), alongside raw residual
arguments and separate scopes. No mutation settings or CI parallelism change. This phase enables
exact DFS only; #10 as a whole is not marked complete.

Final CodeRabbit CLI 0.8.1 review completed all 23 files with zero findings after the major fix;
agent-setup review-record saved. No claim of a skipped review satisfying the gate.

A subsequent local filter-ownership regression failed before the fix (caller mutation selected
DPOR instead of checked DFS). BenchmarkHarness now snapshots filters before validating them.
Final build/Javadoc/all 1,585 tests and the 433/449 default gate pass after this small fix; all
449 inherited identities/statuses remain. It follows the two completed CLI reviews with local
regression/integrated validation, without claiming a third external review.
