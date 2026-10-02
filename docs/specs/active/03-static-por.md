# Spec 03 — Static Partial Order Reduction

## TL;DR
Implement static POR (Partial Order Reduction) for explicit-state model checking.
Compute persistent sets from read/write access sets to avoid exploring equivalent
interleavings. The static POR explorer must produce exactly the same final
verdict as Spec 02's `DfsExplorer`, but explore fewer states.

**Amended 2026-10-02 after Spec 12.07's review surfaced a soundness defect.** Two
corrections to this spec, both of which are load-bearing rather than editorial:

1. **The set computation required here is the *ample set*, and the code computes the
   *acyclic set*.** These are different constructions with different guarantees — see
   §Soundness of the reduction. The acyclic set preserves the existence of a deadlock
   but not state reachability.
2. **This spec had no criterion covering invariant soundness**, which is why
   `StaticPorExplorer` could report `COMPLETED` for `broken-peterson-v2` — a false pass
   on a program whose expected verdict is `VIOLATION` — while satisfying every criterion
   written below. R3 is new and is the criterion that would have caught it.

## Acceptance Criteria
- **R1** `IndependenceRelation` classifies two steps as independent or dependent
  based on their read/write sets.
- **R2** `PersistentSetComputer` computes the ample set for each enabled step at
  each configuration using the static algorithm. **Amended:** "ample set" here means
  the reachability-sound construction — an ample set parameterized by a cut, with the
  *persistent set* being the ample set whose cut is the source set. The *acyclic set*
  does not satisfy this criterion; it is a weaker construction that satisfies only the
  deadlock-preservation property in §Soundness of the reduction. R2 is currently
  **not met** and is tracked as a known gap.
- **R3** **New.** When an invariant is supplied, the reduction is not sound and must not
  be used: no configuration reachable only through a violating one may be silently
  unchecked while a pruned configuration hides a violation. Either the explorer
  disables the reduction under an invariant — as `DporExplorer` does — or it computes a
  reachability-sound set. A `StaticPorExplorer` verdict must equal the `DfsExplorer`
  verdict on **every** corpus program carrying an invariant, not on one selected program.
  Verified by `StaticPorExplorerTest.withAnInvariantEveryExplorerAgreesWithDfsOnTheCorpus`;
  removing the guard fails 3 tests including that one.
- **R4** `StaticPorExplorer` explores configurations using persistent sets instead
  of all enabled steps. **Amended:** applies to the no-invariant case only. Under an
  invariant it branches over all enabled threads, so the states-explored reduction is
  given up; `supplyingAnInvariantDisablesTheReduction` asserts that trade-off
  explicitly rather than leaving it implicit.
- **R5** States-explored count from `StaticPorExplorer` is strictly less than or
  equal to `DfsExplorer` for the same program. **Amended:** counts alone are
  insufficient to establish anything. On `double-checked-locking` the acyclic set visits
  the *same total* as `DfsExplorer` (17) with 9 configurations substituted, so a count
  comparison passes while the visited sets differ. Any criterion relying on state counts
  must compare visited-set membership, not totals.
- **R6** Final invariant verdict is identical to `DfsExplorer`. **Amended:** holds over
  the whole corpus with an invariant. It previously passed on `lost-update` alone,
  which is one of only two corpus programs where the reduction happens to be correct.
- A passing `build` and `test` suite.

## Soundness of the reduction

Three constructions are easily conflated, and conflating them produced the defect:

| Construction | Cut | Sound for |
|---|---|---|
| **Ample set** | a cut — transitions deemed unable to reach the target | state reachability |
| **Persistent set** | the source set, from reverse-reachability analysis | state reachability |
| **Acyclic set** | none | deadlock preservation only |

`PersistentSetComputer.computePersistentSet` implements the **acyclic set**: a thread is
retained only when it is dependent on another enabled thread, and otherwise one arbitrary
enabled thread is returned. That is sound for what deadlock tracing actually needs — a
single representative suffices to preserve the *existence* of a deadlock — but it is not
sound for state reachability, and therefore not sound for invariant checking.

**The reachability shortfall is large and was measured**, with no invariant supplied:

| Program | `DfsExplorer` configurations | `StaticPorExplorer` visits | not visited |
|---|---|---|---|
| `broken-peterson-v2` | 55 | 12 | **43** |
| `broken-peterson` | 55 | 17 | 38 |
| `peterson` | 42 | 18 | 24 |
| `double-checked-locking` | 17 | 14 | 3 |
| `lost-update` | 13 | 9 | 4 |
| `deadlock` | 15 | 15 | 0 |
| `torn-counter` | 9 | 9 | 0 |

So R2 is unmet, and any property requiring an arbitrary reachable configuration to be
visited does not currently hold.

## Known gap — the invariant guard, and the real fix

**Current mitigation.** `porDfs` branches over the persistent set only when
`invariant == null`; with an invariant it branches over every enabled thread, making the
traversal identical to `DfsExplorer`'s. Violation **detection** is then exhaustive: every
configuration reachable without first passing through a violating one is visited and
checked. That is deliberately *not* stated as complete configuration coverage — a
violating configuration's successors are never explored, which costs 9 unvisited
configurations on each of `broken-peterson`, `broken-peterson-v2`, and
`double-checked-locking`, and 1 on `torn-counter`. The shortfall is immaterial for
detection, because the search truncates only after a violation has already been reported.

**Why the guard is a mitigation and not the fix.** It is sound but gives up the speedup
exactly when invariants are in play, which is the case users care most about. The
proper fix is a real Godefroid source set, restoring reachability-completeness and the
reduction together. Requirements when that work happens:

- `source(c)` computed by reverse-reachability analysis to a fixpoint, not a one-pass
  approximation.
- R2 becomes genuinely satisfiable and R4 regains its reduction under an invariant.
- The reachability table above must show every reachable configuration visited; a
  reduction that is merely verdict-equivalent is not sufficient and is what R3 exists to
  prevent regressing to.
- The reduction remains unsound for **trace completeness** regardless — every reported
  execution is real, but not every real execution appears. That is what reduction *is*;
  no source set, sleep set, or DPOR method restores them. A caller needing a specific
  schedule must use `DfsExplorer`.

## Out of Scope
- Dynamic POR / DPOR (Spec 04).
- Sleep sets or cycle provisos beyond basic static ample-set computation.
- Persistent-set optimality proofs.
- **Trace completeness** — deliberately not a goal, for the reason above.

## Commands
```bash
./gradlew test --tests "*StaticPor*"
```

## Map
- `src/main/java/dev/samhb/interleave/core/...` — existing types from Specs 01-02
- `src/main/java/dev/samhb/interleave/por/` — POR types
  (`IndependenceRelation`, `PersistentSetComputer`, `StaticPorExplorer`)
- `src/test/java/dev/samhb/interleave/por/StaticPorExplorerTest.java` — Spec 03 tests
- `src/main/java/dev/samhb/interleave/dpor/DporExplorer.java` — the invariant guard
  this spec's fix mirrors