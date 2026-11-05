# Spec: Final source-property observation contract and reduction

## Objective and scope

Continue 9-V4 with a complete, narrow source-language capability: use declared final-only expression
reads for existing source POR/DPOR, while preserving exhaustive unknown-callback and legacy final
fallback. Existing atomic-increment, split-increment, branching-flag and guarded-handoff source
examples already express final postconditions. Independent work unrelated to a postcondition should
not force enumeration of every interleaving merely because observation happens at completion.

Add a separate Invariant.finalObservedLocations contract; do not weaken observedLocations, which
excludes termination metadata. SourceProperty supplies immutable final expression reads. Only
source programs may consume that new contract, using existing persistent sets, sleep contexts and
complete instruction footprints. DslInvariant/typed legacy behavior and opaque callbacks remain
unchanged. No new pruning rule, sleep representation, source syntax, diagnostic, schema or flag.

Affected code: Invariant's optional metadata, SourceProperty, StaticPorExplorer and DporExplorer.
Integration includes library/CLI examples, replay/terminal diagnostics and existing attestation
assumptions. Tests, proof applicability pointers, roadmap and measured dispatch evidence ship in
this same PR. Do not split required callers/tests into additional small PRs. #10–12 and the full
9-V6 mechanical-proof effort remain separate.

## Contracts

observedLocations retains its state-only meaning. A separate present finalObservedLocations means:

- The property is pure and holds at every configuration where allTerminated is false.
- At complete configurations its truth and evaluation-error classification depend only on the
  declared shared-state locations, including array/index/error inputs. No counter values, owner
  identity, history, enabledness or object identity may influence the terminal predicate.
- Termination is the only configuration-dependent observation gate. The locations use the same
  aliases as step footprints and remain immutable/stable for the entire run.

Empty Optional means unknown, not empty reads. A present empty set is a constant final predicate;
it may still be false or fail at completion. Arbitrary callbacks inherit both metadata defaults as
unknown and remain exhaustive. SourceProperty returns exactly one timing contract, selected by its
parsed always/final timing. The algorithms prefer the original state-only contract and consult the
new final contract only for a source model with unknown state-only observations. Legacy programs
ignore final metadata because this extension proves only compiler-produced forward source execution.

## Preservation argument

Let q be the final state predicate (including its deterministic evaluation-error result), with
complete read set O. Define the checked property H(c) as true when c is not all-terminated and
q(state(c)) otherwise. Source steps affect only their owner's PC and shared/local modeled values.
All successful source successors strictly increase that PC; finite rank is the sum of remaining
instruction positions. Definitions, source maps, state encoding and model state are immutable or
faithfully copied as required by Spec 16.05. Unknown/custom impure callback behavior is excluded.

### Visibility and the completion gate

An action whose writes are disjoint from O cannot change q's terminal observation. H need not be
state-only: an action completing the last owner can activate q and change H. That exception cannot
be crossed while commuting it past an excluded action of another nonterminated owner: that other
owner remains nonterminated, so H is true both before and after the selected action. If an action
can make the whole configuration all-terminated, no other owner is live and no independent owner
choice is excluded. Completion is then explored normally and q is evaluated. Thus the old
state-only invisibility condition extends to these final predicates under this explicit gate,
without claiming that termination is an ordinary shared memory location.

Static POR's selected invisible component is closed over complete future suffix dependencies.
Move its successful actions before excluded independent work. The swaps preserve shared state,
PCs, guards, selected branch targets, evaluation errors and final-loop decisions. While other work
is live, neither side introduces a final observation; once all work completes, q observes the same
O values. Preparation of a failure/cutoff/nonprogress result still expands the parent's branch set
and reuses the attempted transition. Induction on finite rank preserves completed success/failure,
deadlock and terminal attempts; no final property is evaluated at deadlock or a cutoff.

DPOR retains its existing stronger sleeping condition: other sleeping work is independent of the
chosen action and its owner's entire remaining lexical suffix, and unobserved under O. A write
visible through O still clears sleepers, conservatively even before completion. Moving independent
sleeping work to its ancestor sibling cannot alter q's final observation; while that work remains
live, its postponement cannot create all-termination. Source all-asleep normalization, wakeups and
exact configuration/sleep-subsumption checks remain unchanged. Rank excludes an unfinished equal
ancestor; earlier covered contexts therefore completed before a revisit. Subsumption retains every
available continuation. The previous finite-source terminal-preservation argument consequently
applies with the completion-gate lemma above. This is a reviewed source-level argument, not a
mechanically checked Java proof or a claim about cyclic/unbounded/legacy programs.

Runtime step failures and loop cutoffs remain terminal before all-termination and are governed by
complete action footprints, not q. Final predicate falsehood/runtime error is emitted only at a
complete configuration with the existing detailed failure source/category. Exact stores and no
resource stop are required for exhaustive absence claims. Approximate collisions/resource limits
retain their independent incompleteness semantics. Unknown final-looking callbacks cannot use this
argument unless they explicitly satisfy the new contract; the default remains exhaustive.

## Independent acceptance oracles

A postcondition writer plus two unrelated two-action owners has 2*3*3=18 exact PC positions, also
enumerated by a Cartesian product. Its five actions plus the initial position give six static-POR
positions, cross-checked by the literal serial schedule. Final x==1 succeeds after the writer;
x==2 fails only at completion. A premature observation would incorrectly fail at the initial state.

Two competing final x writes independently yield x in {1,2}: the last writer determines the value.
Final x==1 must retain both completed success and property violation. Add unrelated independent
work to exercise reduction without changing that observed set. Empty final true/false/error cases,
short-circuit/error inputs and immutability protect the contract itself.

For a bounded loop checking x==0 with a single x=1 body, a competing x=0 action can run before the
body (final success), after exit (final violation) or before the final limit test (cutoff). Hand
schedules and an independent finite transition enumeration establish all three outcomes. Final
false/error predicates on a disabled deadlock or cutoff must not manufacture a property violation.
Independent prefixes, branch/repeat expansion and revisited contexts must preserve these outcomes.

Compare exact DFS, reduced source POR/DPOR and opaque-property wrappers against those oracles;
replay every returned witness with its original diagnostic invariant. Include existing source CLI
examples and report/attestation checks. Preserve state-only always behavior and exhaustive unknown/
legacy fallback explicitly. Search-count reductions are workload-specific; do not infer wall-time
speedups or complete schedule enumeration from counts.

## Verification plan

Fresh merged main baseline: clean build/tests/Javadoc and default state/CBS PIT, frozen before
edits. Final full default gate must preserve every existing mutant identity/status and 94% floor;
this extension does not touch CBS/state production classes. Separately measure the changed source
property metadata scope, preserving raw survivors and zero resource-error credit. Do not add scopes
or change targets/test filters/operators/denominators/PIT resources to improve the score.

Run meaningful new tests plus the integrated suite, strict Javadoc, normalizer and applicable
source CLI/report paths. Record local exact-search counts and independent outcome oracles; no
unmeasured timing claim. CodeRabbit initial substantive review is required. Publish one draft PR,
then stop without CI/review watchers. Minor feedback uses local verification without a CLI repeat.

## Recorded local evidence

Fresh merged-main baseline at 7bc8ee0: clean build/strict Javadoc and 1,380 tests pass. Default PIT
283/293 K + 10 S, zero uncovered/resource/run errors. SourceProperty scoped baseline is 13/13 K.
With the new default metadata method but before exposing final reads/dispatch, eight new cases fail
at the intended metadata/count expectations; the old static fallback visits 18 rather than six.

Final integrated build/strict Javadoc and 1,399 tests pass, including 19 new cases. Every returned
source witness is validated by invariant-aware replay. The runtime-error, branch/repeat, false
constant, last-writer, bounded-loop three-outcome, unknown-callback and legacy-gate cases all pass.
Existing teaching-example tests exercise actual BenchmarkHarness/ReportWriter/attestation and source
input. The actual Main CLI JSON also passes: exact DFS 18, static POR 6, DPOR 17, CBS 38 accepted
visits, all PASS with soundness=true. Baseline CLI was 18/18/18/38 on the identical source text.
These are deterministic workload counts, not runtime speedups or a guarantee of equal trace lists.

Final default PIT retains all 293 semantic identities/statuses exactly, including all ten raw
survivors: 283/293 K, full R5/R6/R7 pass, no floor/denominator/scope/operator/test-filter/resource
change. SourceProperty is separately 17/17 K: all 13 old identities/kills retained, four new kills
in finalObservedLocations; zero uncovered/resource/run errors, scoped R6 passes and R5/R7 skip.
Do not add the metadata scope to the default scope or claim a whole-checker mutation proof.
Snapshots are `/private/tmp/interleave-final-property-{default,metadata}-{baseline,final}.xml`.

Detailed dispatch provenance and reproduction are recorded in the accompanying measurement note.
All 18 visualizer normalization checks and changed Markdown/table/link/diff checks pass. CodeRabbit
CLI 0.8.1 completed all 13 files with one minor verification-status finding, independently verified
and patched by recording these actual results. No CLI repeat for this minor docs follow-up.
The latest build mutations.xml is the scoped metadata report; use the frozen default report for
default-scope comparisons. Browser rendering was not rerun; schema/renderer are unchanged.
No CI/review watcher starts after PR publication.
