# 77 — Source symmetry with reduced and bounded strategies

## Scope and completion boundary

Finish roadmap #10 after Specs 75–76, merged in PRs #117–118. Compose checked source symmetry
with exact DFS, static POR, DPOR and context-bounded search, including iterative CBS. Keep the
existing conservative source/property eligibility, original-identity execution and replay evidence.
No bitstate/custom-store composition, arbitrary Java callbacks, dynamic owners, weak memory,
liveness or unbounded execution is claimed. Stop after #10; #11/#12 are separate workstreams.

## Run ownership and public paths

`SymmetryStateStore.forRun(program, invariant, strategy)` validates `SourceSymmetry.groups` and
binds those exact program/property objects and strategy. All four explorer entry points check the
binding before clearing membership or obtaining an initial configuration. A mismatch rejects.
The final store cannot be subclassed. `freshCopy` retains the binding but owns empty membership;
`clear` empties ordinary, CBS and sleeping coverage. Stores are mutable and not concurrency safe;
the immutable runner obtains a fresh one per run and per iterative bound. Existing ordinary-store
behavior stays unchanged. The `SymmetryDfsExplorer` convenience API delegates to the same store.

The runner's existing boolean opts in for all built-in strategies. Explicit stores/factories reject
so their contracts cannot be silently ignored. An ordinary runner also rejects a symmetry store
supplied through a custom store/factory, including iterative CBS: otherwise quotient counts could
be mislabeled as ordinary exact search. The explicit boolean is the supported runner entry. The harness still snapshots filters before validating
them and requires exactly EXACT storage. A null strategy filter selects all four; unknown/empty
filters reject in symmetry mode. Iterative deepening requires a selection containing CBS.
The CLI requires source and explicit exact storage; strategy omission runs all four. CBS-only flags
require a CBS selection (or the all-strategy default). Existing cutoff simplification remains valid.

## Product keys and operational context

Source instructions have no monitor/wait-queue operations; those configuration fields remain empty.
Enabledness and all-termination derive from the retained values and PCs.
All keys retain shared values and whole owner frames (PC and every typed local), under the groups
certified by Spec 76. Execution never runs a relabeled configuration. Diagnostic source labels,
thread IDs, errors, cutoff attempts and replay choices remain those of the actual traversal.

- DFS and static POR use the exact value-orbit key.
- CBS uses the canonicalizer's context-aware key: the same permutation moves the last owner with
  its frame; initial -1 stays fixed. A minimum preemption cost is retained for each such key. A
  previous cost q covers p iff q <= p. Better costs reopen the representative; greater costs do not
  overwrite the minimum. Negative costs reject. Each iterative bound gets fresh membership.
- DPOR uses value-orbit membership plus exact normalized sleep context. First retain the existing
  all-asleep reset. Then map each sleeping action to `(canonical owner, lexical instruction PC)`
  using the same frame permutation. Immutable sets are compared for equality; symmetry mode does
  **not** lift the ordinary raw-key subset-antichain rule across permutations. Empty, stale and
  distinct action-PC contexts remain distinguishable. Ordinary DPOR keeps its prior antichain path.

The compiler normally creates distinct instruction objects for each PC. Public `Program`
recomposition can repeat the same trusted forward instruction object in one owner's list. DPOR's
existing sleep relation uses action object identity, so this alias pattern is not faithfully
represented by owner/PC tokens. Symmetry DPOR rejects repeated objects per owner before traversal.
Other symmetry strategies retain their previous eligibility; they do not need action identity.
Foreign sleeping owner/action entries reject. This is an intentional narrow applicability condition.

Original-ID tie breaking between equal frames can choose different sleep witnesses and miss some
reduction. It cannot turn distinct product contexts into an equality: equality supplies an explicit
permutation witnessing equal values and equal available sleeping transitions.

## Preservation argument

Assumptions are the immutable forward source instructions, pure compiler shared-state properties,
complete footprints and exact encoding from Specs 16.05, 70, 75 and 76. Each committed source action
strictly increases sum(PC), bounded by sum(thread sizes). Permuting owners preserves that rank.
Thus a repeated orbit cannot be an unfinished ancestor on the current stack. An earlier equal-rank
representative/context finished before pruning; a resource interruption reports a partial result.
No absence claim applies after resource exhaustion or to approximate stores.

The eligibility partition is an automorphism of the unreduced transition system. Source guards,
selected targets, local/shared effects and failure/cutoff decisions commute with whole-frame
permutation. A grouped owner cannot observe tid; an omitted fixed owner may observe its unchanged
own tid. Compiler properties observe fixed shared values, with final predicates gated by all-owner
termination. The permutation preserves that gate, deadlock and terminal classification. Diagnostic
multiplicity and all concrete schedules are not preserved or promised.

### Static POR

Do not assume smallest-physical-ID persistent-component selection is permutation equivariant.
Instead use well-founded induction on remaining forward rank. At each concrete representative the
existing suffix-closed persistent-set argument (16.05 and 70) preserves existence of each relevant
bad/completed/deadlocked/terminal-attempt observation. Its selected successful steps can commute
past excluded independent work without changing these observations. Preparation of an error, cutoff
or nonprogress result expands to all enabled actions, retaining the existing once-only attempt.
For each selected successful successor the induction hypothesis preserves those observations under
orbit pruning. A previously finished representative of an equal orbit has the same unreduced
reachable observations by automorphism; its own persistent choices preserve them by the same
induction. It can therefore discharge a revisit even if physical tie breaking chose a different
component. Finiteness is essential; this is not a cyclic-graph cycle-proviso argument.

### DPOR

Use the product of configuration and exact sleep context. Under the explicit permutation witness,
current action identity maps to the same owner/PC occurrence in an identical instruction stream.
Enabledness, complete-footprint independence, visibility, enable/disable interference and the
executing owner's future suffix map with it. The existing sleeping condition and its terminal
preservation lemma therefore transport. Source next-sleep construction examines all other enabled
independent invisible actions, rather than depending on an already-visited-sibling order.
The all-asleep reset also commutes with the permutation. `HappensBefore` is recorded but is not
consulted to choose source branches; it adds no omitted pruning context here.

Equal mapped sleep sets supply identical permitted continuations up to this permutation, including
wakeups and terminal attempts. Finite rank ensures the covered context completed. Exact context
matching avoids claiming that raw-key subset dominance automatically transports under a different
canonical witness. Tie breaking can lose matches, which conservatively costs work. This argument
adds no sleep-set subset optimization and does not claim all symmetry runs are faster.

### CBS

For a transition choosing owner t, the preemption increment depends on whether last is -1, t equals
last, and last remains enabled. All three predicates are invariant when the same permutation maps
the configuration and last owner. Hence corresponding paths have equal cost. At equal orbit/last,
a completed visit with cost q <= p has at least as much remaining bound and subsumes p's reachable
continuations. A lower-cost revisit must explore again. Per-bound isolation prevents information
from an earlier iterative pass from claiming coverage in a later one. Budget and loop-limit cutoff
witnesses remain the actual concrete attempted owner, prefix and source metadata.

These are reviewed source-level arguments, not mechanically checked proofs of the Java code.

## Counts, reports and compatibility

`statesExplored` counts accepted representative visits with strategy context. CBS cost improvements
and DPOR distinct sleep contexts may revisit a value orbit; do not label every count a distinct
orbit count. Existing JSON strategy/store labels remain unchanged, with `symmetryReduction: true`
only for enabled rows. Markdown grouping distinguishes all four symmetry labels from ordinary rows;
the viewer labels representative visits. Default runs omit the flag. No `TestResult` field/API or
native-stream compatibility policy changes in this stage.

## Acceptance and evidence

See [the measurement ledger](../../measurements/77-source-symmetry-composition.md). Regressions cover
actual strategy/runner/harness/CLI calls, literal terminal oracles, original witness replay, branch
interference, independently bounded lost updates, dependent cutoffs, mapped last-owner minimum cost,
exact sleep-context transport and mutation-resistant binding checks. Existing source eligibility,
ordinary/approximate strategy, stream rejection and browser contracts remain part of the full suite.
