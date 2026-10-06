# 08 — Property-aware static persistent sets (A4)

## TL;DR

**Status:** implemented; PR/CI pending.
**Closes:** A4 through opt-in state-only properties, with exhaustive fallback for unknown observations.
**Baseline:** `main` at `9276cd4`; freshly measured 496 tests, full PIT **255/268**, 13 survivors.

Static POR now uses remaining-thread dependency closure and property visibility. Ordinary Java
callbacks and DSL `final` invariants stay exhaustive; DPOR's invariant fallback is unchanged.
This is a conservative specialization for finite linear threads, not the historical pairwise
`source_pair` shortcut or a general stubborn-set implementation.

## Public contract

`Invariant` remains functional. `observedLocations()` defaults to `Optional.empty()`, meaning unknown
or configuration-sensitive observations. A present empty set means a constant state-only property.
`Invariant.observing(Set<MemoryLocation>, Predicate<SharedState>)` copies observations and exposes
only shared state to its predicate. Observations must be complete, stable, and named consistently with
step footprints. Predicates must be pure, deterministic and value-based; counters, termination,
scheduling history, object identity and external mutable state are not supported by this opt-in.

DSL `always` properties automatically derive reads from all conjuncts and operands. Constant array
indices identify elements; dynamic indices include the array base and index-expression reads.
`ExpressionReads` shares these rules with `DynamicStep`. DSL `final` observes termination metadata,
so it supplies unknown observations and retains exhaustive traversal.

With accurate footprints, faithful copies/keys, non-mutating observers and an exact store, reduced
search preserves existence of invariant/assertion violations and terminal outcomes on paths not
already truncated by a violation. It does not enumerate every configuration or schedule, preserve
trace counts, or guarantee shortest counterexamples. Bitstate's independent false-positive risk
remains; early-aborted or exceptional runs provide no exhaustive verdict guarantee.

## Selection and execution

`PropertyPersistentSetComputer` captures every step footprint once per run and precomputes suffix
read/write unions. At each configuration it builds a graph of live threads: an edge means some pair
of remaining actions may conflict. Disabled current actions and future actions participate.

Choose the smallest connected component whose members are all enabled and whose current writes do
not overlap property observations. It must be a proper subset of enabled threads; ties use sorted
thread IDs. Otherwise select all enabled threads. This is conservative: a future conflict can
prevent reduction even when a more sophisticated analysis could prove it unnecessary.

Execute selected candidates once on separate state copies. Accept reduction only when every result
is `ADVANCED` or `TERMINATED`, both of which increment the counter. A `BLOCKED` or `ASSERTION_FAILED`
result expands to all enabled threads; prepared results are reused rather than executed again.
Traverse selected IDs in ascending order with existing visitor, canonical-key and trace handling.
Unhandled execution exceptions continue to propagate.

See [the research and correctness argument](08-property-aware-por-research.md) for primary sources
and the original remaining-step-rank proof. The key facts are cross-component independence,
invisibility of selected actions, and strict counter advancement on reduced edges. A general cycle
proviso is unnecessary for this finite linear model; loops/jumps would require a new proof.

## Footprint prerequisite

`Step` now explicitly requires complete guard, outcome, error and value dependencies, all modeled
writes, and no hidden mutable execution state. Stable over-approximation is permitted.
The audit fixes these concrete omissions while retaining existing conservative dependencies:

| Steps | Added modeled locations |
|---|---|
| `CSEnterStep`, `CSExitStep` | write `inCriticalSection` |
| `DclInitSetInitializedStep` | write `initialized` |
| `DclLockStep`, `DclUnlockStep` | read/write `locked`, `lockOwner` |
| `ReadCounterStep` | write `registers[threadId]` |
| `WriteCounterStep` | read `registers[threadId]` |
| `ReadSnapshotStep` | write `observedHigh`, `observedLow`, `hasObservation` |

Existing lock/control aliases are preserved. Footprint changes can alter the existing unguarded
POR/DPOR exploration counts; that is recorded by corpus comparison, not hidden as an unchanged run.

## Tests and verification

Public seams: invariant observations/evaluation, loaded DSL programs, step footprints/execution,
and explorer results/visitors. Private graph helpers are not the test interface.

- Observation API tests first failed because the methods were absent, then passed after implementation.
- Eight built-in footprint cases first failed on missing dependencies, then passed after corrections.
- Java and DSL reduction fixtures first failed under exhaustive fallback, then passed with reduction.
- Tests cover visible intermediate violations, future/transitive dependencies, disabled component
  members, smallest-component/tie ordering, immutable input state, once-per-run metadata capture,
  progress/termination and assertion fallback, and single execution of prepared candidates.
- DSL tests cover final fallback, constant properties, conjunctions, all expression reads, dynamic
  array aliases and evaluation errors.
- **1,875** differential cases: all 5⁴ two-thread/two-step combinations of writes, copies, guards and
  assertions, each with an independent third thread and three state properties. DFS and static POR
  agree on violation/completion/deadlock outcome existence; every reduced trace independently replays
  actual outcomes and endpoints. The suite explicitly requires actual reduction.
- Deliberate falsifications of future closure, visibility, disabled-member filtering, progress
  fallback and prepared-result reuse are all rejected by their behavioral regressions. Source is
  restored after each experiment.

The initial full build/Javadoc run passes **523 tests**. The 128-case before/after corpus comparison
preserves every complete trace list. Ten case rows change counts/membership because the corrected
critical-section write footprint prevents formerly permitted unguarded reorderings:

| Program / unguarded strategy | Exact visits before → after | Bitstate visits before → after |
|---|---|---|
| Peterson / DPOR | 38 → 42 | 38 → 42 |
| Broken Peterson / static POR | 17 → 26 | 17 → 25 |
| Broken Peterson / DPOR | 42 → 55 | 42 → 54 |
| Broken Peterson v2 / DPOR | 49 → 55 | 49 → 54 |

Peterson has no corpus invariant, so its nominal with/without-invariant rows both use the unguarded
path (four rows). The other changes are the six unguarded exact/bitstate rows. Every run with an
actual supplied corpus invariant is unchanged. The new bitstate count differences reflect its
existing approximate suppression; they do not carry the exact-store guarantee.

Both freshly measured baseline and implementation full-scope PIT reports contain **268 mutants,
255 killed, 13 survived**, with every mutation identity and survivor identity unchanged. There are
zero uncovered, timeout, memory, non-viable, run-error or equivalent statuses. Per-class census:
encoder **18/19**, exact store **43/52**, CBS **100/100**, bitstate **94/97**. No per-class drift notice.
This is measured equality, not a carried-forward assumption: baseline was run on clean `main` before
source changes and its XML saved separately for comparison.

Additional supported-property checks preserve real DCL/torn-counter violations, with and without
independent work, and replay all resulting traces. An eight-thread DSL grid demonstrates **6,561 DFS
positions → 17 reduced positions**, one completed schedule (eight threads with two independent writes).
The count is independently derived from eight three-position counters and enumerated outside Java.

First local CodeRabbit review: the metadata-reporting request is addressed by the measured census
above. The local-array index-read finding is rejected: `TypeChecker.check` forbids local arrays and
`Evaluator.eval` throws before evaluating their index, so that index is not an execution dependency.
A loader regression pins the rejection; shared-array dynamic indices are covered separately.
The second local CodeRabbit review completed with **zero findings**. The expanded full suite
passes **526 tests**; the final expanded-suite PIT rerun also passes **255/268**, with identical
mutation/status identities to the fresh baseline. Remote CI remains pending.
The PIT source scope remains `state.* + cb.*`; the 94% floor is unchanged. **E5 remains open.**

## Historical rejected pairwise patch

The following proof records why unioning the former pairwise source term was not a fix. It does not
specify the implemented property-aware algorithm.

**Only the pairwise shortcut below is proven to be a no-op.** It is not a definition of a sound
Godefroid construction. An earlier version used `source(c)` for both this shortcut and an undefined
relation between successor states; the subset proof for the first cannot establish anything about
the second. The claims that reverse reachability is necessary and that adding a source term alone
restores every reachable configuration are withdrawn.

Let `E = enabled(c)`. For valid configurations, each enabled thread has a non-null next step.
Define `D_c(t,u)` using exactly the checks in `PersistentSetComputer.computePersistentSet`:

```text
D_c(t,u) = !areIndependent(nextStep(t), nextStep(u))
           OR hasEnableDisableInterference(c, u, t, threads)

source_pair(c) = { t ∈ E : ∀ u ∈ E\{t}, D_c(t,u) }
dep_pair(c)    = { t ∈ E : ∃ u ∈ E\{t}, D_c(t,u) }

P_current(c) = E                       if |E| <= 1
               dep_pair(c)             if |E| >= 2 and dep_pair(c) ≠ ∅
               { E[0] }                otherwise
```

Enable-disable interference is directional; the argument order above matches the implementation.
No successor-state membership rule is assigned to `source_pair` elsewhere in this note.

The precise claim is **`P_current(c) ∪ source_pair(c) = P_current(c)`**:

- *No enabled threads*: both sets are empty.
- *Exactly one enabled thread*: `dep_pair` is empty, `source_pair = E` by vacuous truth, and the
  existing short-circuit returns `E`.
- *At least two enabled threads and a dependency*: each thread has at least one other thread, so
  the universal condition implies the existential one. Thus `source_pair ⊆ dep_pair = P_current`.
- *At least two enabled threads and no dependencies*: both pairwise sets are empty. The existing
  nonempty fallback returns `{E[0]}`, and unioning the empty `source_pair` leaves it unchanged.

Notice that `source_pair ∪ dep_pair` alone is empty in the last case and does **not** describe the
computed set. The proof retains both implementation fallbacks. It rejects this particular local
patch; it neither proves nor disproves a future reduction based on different information.
