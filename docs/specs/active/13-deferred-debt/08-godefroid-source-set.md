# 08 — Godefroid `source` set

**Status:** **NOT implemented — design note.** See [Why this is not done](#why-this-is-not-done).
**Closes:** nothing. A4 remains open.
**Mutation scope if landed:** `por/` is outside `state.* + cb.*`, so no new mutants — but the
explorers' behaviour would change, so a full-scope measurement is still required at set exit.

## What the guard does today

12.07 found that static POR could return a **false pass** when given an invariant. `StaticPorExplorer`
fixed it by branching over every enabled thread whenever an invariant is supplied
(`StaticPorExplorer.porDfs`), making the traversal identical to `DfsExplorer`'s. That is sound and
it forfeits the reduction exactly when an invariant is being checked.

Correctness is fine and is pinned by regression tests that fail if the guard is removed. **A4's cost is
performance only.**

The register identifies `por/` as the affected package. `StaticPorExplorer`,
`PersistentSetComputer` and `IndependenceRelation` all live in **`por/`**.

## Why this is not done

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

## What the real fix requires

**A path-level persistence condition.** Godefroid's Definition 4.1 considers sequences outside the
chosen set, including later enabled transitions; Algorithm 1 grows a seed set until dependency closure
or a conservative fallback. Current enabled-step comparisons do not establish that condition.
See [Godefroid's thesis, §§4.1–4.3](https://patricegodefroid.github.io/public_psfiles/thesis.pdf#page=42).

**A property-preservation argument.** Persistence alone does not imply visiting every intermediate
state. State-predicate verification additionally needs dependencies that respect what the property
can observe; see [the same thesis, §7.3](https://patricegodefroid.github.io/public_psfiles/thesis.pdf#page=104).
Those are separate obligations, not a specified reverse-reachability implementation.

For this API the distinction is concrete. Start with flags `[false, false]` and two one-step threads:
thread 0 writes flag 0, thread 1 writes flag 1. The writes are independent and both orders end at
`[true, true]`. `P_current` chooses thread 0 first, visiting three of DFS's four configurations and
omitting `[false, true]`. The invariant `flag(0) || !flag(1)` rejects precisely that omitted state.
Even a persistent singleton can therefore miss this violation without property-aware handling.

`StaticPorExplorerTest.independentWritesStillNeedTheInvariantGuard` pins the missing membership and
the violation found by both guarded POR and DFS. `Invariant` supplies an arbitrary callback with no
read-set or visibility contract, so the current exhaustive fallback remains justified. A future spec
must define how properties are preserved before it chooses a reduction algorithm or removes the guard.

## Before this can land

- **An oracle.** `DfsExplorer` with an invariant, over the whole corpus, is the reference. The known
  figures to hit: `broken-peterson-v2` visits **55** reachable configurations (static POR currently 12),
  `broken-peterson` visits **55** (currently 17), measured without an invariant in 12.07.
  Runs with an invariant stop at violations, so their state membership must be compared against DFS
  with the same invariant rather than against the untruncated totals.
- **Property-preservation tests**, not just counts. Spec 12 recorded a case where
  `double-checked-locking` had *equal* totals at 17 with different membership. Compare invariant
  verdicts against DFS and pin the relevant violating-state membership with targeted examples.
  Require complete membership equality only if the future spec promises full configuration coverage.
- **The invariant guard must stay as a fallback** until the reduction is proven, and its regression
  tests must remain green. The guard is what makes shipping a partially-correct reduction survivable.
- **A decision on the residual.** Property-preserving reduction does not promise complete state or
  trace enumeration. If the intended contract is full configuration coverage instead, state that
  stronger requirement explicitly and prove it; do not infer it from persistence.

## Recommendation

**Counterexample verification:** the new test passes with the guard, fails when `porDfs` is temporarily
forced to use the reduced branch set under an invariant (three visited configurations instead of the
oracle's four), and passes again after restoring the guard. No reduction algorithm changed here.

Take this as its own session with its own spec, as the register originally said. Do not fold it into
set 13.06 or a later cleanup — the payoff is performance, the risk is reintroducing a false pass, and
the guard that prevents that should not be removed in the same commit as something this subtle.

**Meanwhile: no action needed.** The current state is sound. Nothing is miscomputed, no test is
failing, and the cost is wall-clock on runs that pass an invariant — which is the cheaper failure.
