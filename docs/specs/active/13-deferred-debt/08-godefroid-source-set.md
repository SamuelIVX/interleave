# 08 — Godefroid `source` set

**Status:** **NOT implemented — design note.** See [Why this is not done](#why-this-is-not-done).
**Closes:** nothing. A4 remains open.
**Mutation scope if landed:** `por/` is outside `state.* + cb.*`, so no new mutants — but the
explorers' behaviour would change, so a full-scope measurement is still required at set exit.

## What the guard does today

12.07 found that static POR could return a **false pass** when given an invariant. `StaticPorExplorer`
fixed it by branching over every enabled thread whenever an invariant is supplied
(`StaticPorExplorer.java:185–187`), making the traversal identical to `DfsExplorer`'s. That is sound and
it forfeits the reduction exactly when an invariant is being checked.

Correctness is fine and is now pinned by three tests that fail if the guard is removed. **A4's cost is
performance only.**

Note the register says this changes `search/`; it does not. `StaticPorExplorer`,
`PersistentSetComputer` and `IndependenceRelation` all live in **`por/`**. Small inaccuracy, worth
correcting in the register since a reader scoping future work would look in the wrong package.

## Why this is not done

The implementation that 13.08's description invites is **a provable no-op.**

Godefroid's sound persistent set is

```
persistent(c) = source(c) ∪ dep(c)

source(c) = { t ∈ enabled(c) : t is dependent on EVERY other enabled thread }
dep(c)    = { t ∈ enabled(c) : t is dependent on AT LEAST ONE other enabled thread }
```

**When two or more threads are enabled and at least one dependency exists**, `source(c)` is by
definition a subset of `dep(c)`, so `source(c) ∪ dep(c) = dep(c)`. And in that case `dep(c)` is
**what `PersistentSetComputer` already computes** (`PersistentSetComputer.java:60–74`): it adds a
thread to the set when any other enabled thread is dependent on it by data conflict, or could disable
it.

**The degenerate branches are separate cases and neither one is a subset argument.**

- *No dependencies at all* (two or more enabled, all pairwise independent): the "at least one"
  quantifier in `dep(c)` finds nothing, so `dep(c) = ∅` — and so is `source(c)`, since no thread is
  dependent on all the others when none are dependent on any. The union is **empty**, which does *not*
  describe the computed set: the computer falls back to returning `enabled.get(0)`
  (`PersistentSetComputer.java:88–90`). So `dep(c)` alone misdescribes this configuration, and any
  argument resting on "`dep(c)` is what the computer returns" is simply false here.
- *Exactly one enabled thread*: `dep(c) = ∅` for the same quantifier reason, while `source(c) = {t}`
  because "every other" is vacuously true. Here `source(c) ⊄ dep(c)`. The union is not a no-op — it is
  a no-op only because the short-circuit `if (enabled.size() <= 1) return enabled`
  (`PersistentSetComputer.java:42–44`) already returns exactly `{t}`, which is `source(c)`.

So there are three branches, and the no-op conclusion holds in all three for three different reasons —
one subset argument and two fallbacks. Stating it as one unqualified claim would have left two of the
three resting on something that does not hold.

The practical consequence is the same either way: union the `source` set into the computed set and the
result is unchanged for every configuration. The code would compile, pass every test, reduce no state
count, and
close the item on paper. That is the shape of change this register exists to prevent, and it is
why 12.07's note that this is *"a genuine algorithm with its own spec, not a patch"* is correct rather
than dramatic.

## What the real fix requires

The `source` term earns its place only because it is computed by **reverse reachability**, not by a
one-step pairwise test. Two things have to change together:

**1. Multi-step independence.** `source(c)` is defined against *independent successors*, not
independent transitions:

```
t ∈ source(c)  iff  there is no t' ∈ enabled(c)\{t} such that
                     c -t'-> d'   and   c -t-> d   and   d and d' are independent successors
```

Checking whether `d` and `d'` are independent requires exploring *forward* from both successors and
deciding whether their reachable futures can interfere. `IndependenceRelation` is a one-step
read/write/enable-disable test over single steps, so it cannot answer that question. A genuine
`source` set needs a notion of independent *successor states* — a different, larger relation.

**2. `dep` is recursive, not one-shot.** Godefroid's `dep(c)` is defined with respect to the persistent
set being constructed, refined as the set is built. `PersistentSetComputer` computes a single
pairwise pass over all enabled threads and returns. Those are different functions with the same name.

Either change alone is insufficient. Together they are the algorithm, and their interaction is the part
that needs a proof rather than a pattern.

## Before this can land

- **An oracle.** `DfsExplorer` with an invariant, over the whole corpus, is the reference. The known
  figures to hit: `broken-peterson-v2` visits **55** reachable configurations (static POR currently 12),
  `broken-peterson` visits **55** (currently 17). `double-checked-locking` and `torn-counter` likewise.
- **A state-coverability property test**, not just counts. The count matching is necessary and not
  sufficient — spec 12 already recorded a case where `double-checked-locking` had *equal* totals at 17
  with different membership, which a count comparison cannot see. Any 13.08 test has to assert
  membership, not size.
- **The invariant guard must stay as a fallback** until the reduction is proven, and its three regression
  tests must remain green. The guard is what makes shipping a partially-correct reduction survivable.
- **A decision on the residual.** Even a sound `source` set gives state reachability, not trace
  completeness — the existing Javadoc's point at lines 50–63 stands and should not be quietly dropped.

## Recommendation

Take this as its own session with its own spec, as the register originally said. Do not fold it into
set 13.06 or a later cleanup — the payoff is performance, the risk is reintroducing a false pass, and
the guard that prevents that should not be removed in the same commit as something this subtle.

**Meanwhile: no action needed.** The current state is sound. Nothing is miscomputed, no test is
failing, and the cost is wall-clock on runs that pass an invariant — which is the cheaper failure.