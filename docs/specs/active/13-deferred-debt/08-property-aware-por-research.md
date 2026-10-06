# 13.08 — Property-aware static POR: research and proof obligations

## TL;DR

The proposed remaining-step component rule preserves state-invariant violation detection for
this project's finite linear threads, subject to complete footprints, pure value-based
semantics, faithful state copying/identity, and an exact visited store. The argument below is
an original specialization to interleave; it is not a claim that Godefroid implemented this
component algorithm. Ordinary configuration-aware callbacks retain exhaustive branching.

Research inspected source at `9276cd4daef66589dac2ee72f3e0a927661c98ad`; implementation
changes must maintain the stated assumptions. This note is proof guidance, not a test report.

## Primary-source grounding

Godefroid's dependency definition requires independent transitions to preserve each other's
enabledness and commute. Persistence considers entire transition sequences outside the
selected set, not just currently enabled pairs. His first construction closes dependencies
and falls back when disabled transitions enter the set. See [the thesis, Definition 3.1 and
§§4.1–4.3](https://patricegodefroid.github.io/public_psfiles/thesis.pdf#page=28).

Persistence preserves deadlocks but does not generally preserve intermediate predicates.
The thesis identifies state-visible transitions through their ability to change predicate
truth, and discusses cycle provisos to prevent ignored actions in cyclic systems. See
[§6.1 and §§7.2–7.3](https://patricegodefroid.github.io/public_psfiles/thesis.pdf#page=82).

## Preconditions specific to interleave

These are obligations on the implementation and its callers, not conclusions borrowed from
the thesis:

- A thread has a finite immutable step list. Its counter only advances by one or remains
  unchanged on `BLOCKED`; there are no jumps, new threads, or external state changes.
  [`ModelThread`](../../../../src/main/java/dev/samhb/interleave/core/ModelThread.java) and
  [`Configuration.successor`](../../../../src/main/java/dev/samhb/interleave/core/Configuration.java)
  provide these mechanics.
- A step's read set includes every state dependency of enabledness, result, and returned
  outcome, including guards and errors. Its write set includes every changed modeled field.
  Sets are stable for the run and use the same alias-overlap convention as property reads.
  Determinism includes state values written, not merely the returned enum. Steps do not
  change internal/external state that is absent from the supplied modeled state.
- Disjoint declared accesses must imply semantic independence: neither step changes the
  other's enabledness/outcome and either execution order has the same modeled result.
  No hidden allocation identity, aliasing, thread-global state, or collection-shape dependency
  may invalidate that implication. Conservative overestimation only loses reduction.
- `BLOCKED` leaves state untouched, as required by
  [`Step.execute`](../../../../src/main/java/dev/samhb/interleave/core/Step.java). It is an
  identity self-loop for the state/counter key and cannot reveal a new state-only property.
- The opted-in invariant is pure, deterministic, total as a boolean check, and depends only
  on the values at declared locations. It cannot inspect counters, scheduling history,
  termination, last outcome, object identity, or external mutable state. A DSL evaluation
  error returning false is part of that boolean result; all inputs to that error are reads.
- Copies are isolated and value-faithful. Canonical keys distinguish all modeled values and
  counters that affect future behavior. A visitor must not mutate configurations or captured
  analysis. Duplicate suppression is exact, and traversal is allowed to finish.
  [`SharedState`](../../../../src/main/java/dev/samhb/interleave/core/SharedState.java) defines
  copying/encoding; [`HashingStateStore`](../../../../src/main/java/dev/samhb/interleave/state/HashingStateStore.java)
  is exact. [`BitstateStore`](../../../../src/main/java/dev/samhb/interleave/state/BitstateStore.java)
  independently permits false positives, so it cannot support an exhaustive-verdict guarantee.

## Component rule and persistence

At configuration `c`, include every live thread in a graph. Connect two threads when any
pair of their remaining steps has overlapping write/read or write/write footprints. Include
disabled current steps and all future steps. Choose a nonempty component `K` only when all
its current steps are enabled and invisible to the opted-in property. Its current transitions
form `T`. If `T` is not a proper subset of enabled transitions, explore all enabled transitions.

Any execution prefix avoiding `T` cannot advance a thread in `K`: the first action of each
such thread is exactly its current transition in `T`, and each thread has one next step.
Therefore that prefix consists only of actions from outside `K`. No such action conflicts
with any remaining action of `K`, by construction. The current transitions in `T` remain
enabled, keep the same outcomes, and commute with the whole outside prefix. This establishes
the path-level persistence condition; checking only current pairs would not establish it.

Prepare every candidate current transition on a distinct deep copy, once. Accept reduction
only if every outcome is `ADVANCED` or `TERMINATED`. If any is blocked or assertion-failed,
use all enabled transitions and reuse prepared results. This also avoids performing a
potential assertion and then silently dropping it from the traversal.

## State-invariant preservation: original finite-rank argument

Define `R(c)` as the total number of steps remaining over all threads. Every accepted reduced
edge strictly decreases `R`; ordinary progressing edges do too. Valid blocked edges preserve
both state and counters and can be removed from any finite reachability witness. Consequently
strong induction on `R` is available even when exhaustive fallback includes blocked self-loops.

Suppose the invariant holds at `c`, but there is a finite full-search witness `w` reaching a
violating state. If full branching is selected, follow the witness's first progressing edge
and apply induction to its successor.

If reduction selects `T`, consider the first action of `T` appearing in `w`:

1. **One appears.** Its preceding prefix consists only of outside-component actions. Move
   that selected action to the front by independence. It is invisible, so inserting its
   effect before each prefix state leaves the invariant's value unchanged. After the action's
   original position, the states agree by commutativity. A violation remains reachable from
   the selected successor, whose rank is smaller; induction applies.
2. **None appears before the violation.** Choose any action in `T`, execute it first, then
   replay `w`. It remains independent of the outside-only witness. Its invisible writes do
   not repair the bad predicate, so the witness still reaches a violating state. Again the
   selected successor has smaller rank, and induction applies.

Every selected edge is an actual execution, so reduction cannot invent violations. Together
the two directions preserve existence of a violation, not its precise schedule, shortest
length, number of failing states, or complete configuration enumeration.

## Assertions, terminal outcomes, and merging

An outside assertion remains reachable after moving an invisible selected action earlier:
complete reads include the assertion/outcome dependencies, so its failure is unchanged. A
selected assertion forces full fallback. Thus violation existence includes assertion outcomes;
the proof does not require identical trace lists. Unhandled Java exceptions still abort the
search, and no verdict guarantee is claimed for such aborted runs.

A witness reaching completion or deadlock must contain a transition of `T`: otherwise every
selected thread remains live and enabled at its endpoint. Move the first such transition
forward and apply rank induction. The terminal state is the same modeled state/counter
position after commutation. This preserves reachable completion/deadlock endpoints along
paths not already truncated by invariant or assertion failure.

State merging is safe because the key represents the state/counters that determine future
execution, the invariant is history-independent, and selection depends on that same position
plus fixed run analysis. Every progressing edge increases counters; no distinct-state cycle
can be created. A duplicate successor cannot be an ancestor after strict counter advancement.
The only non-progress edges are valid blocked self-loops, selected exclusively under exhaustive
fallback. No general cycle-proviso implementation is needed for this finite linear model.
Adding loops/jumps, history-sensitive observations, or coarse identity would invalidate this
reasoning and require a new proof.

## Required validation and limits

Pin the existing independent-write false-pass example with both unknown observations and
explicit observations. Exercise future/transitive conflicts, disabled component members,
array-base aliases, and assertion/blocked fallback. Demonstrate genuine reduction on an
invisible independent component. Differential tests against unrestricted DFS must use an
exact store, independent fixtures/oracles, and no truncation, comparing violation existence
and replayability rather than equal state counts.

DSL `always` observations must include every expression input (including both operands,
all conjuncts, and dynamic indices). DSL `final` remains unknown: its evaluation depends
on termination metadata, which invisibility of shared-memory writes cannot describe.
Likewise, documentation alone cannot infer observations for arbitrary Java callbacks.
The new opt-in contract is a caller correctness obligation; the implementation cannot
mechanically verify complete user footprints or predicate purity.

No additional logical hole was found under these preconditions. Missing footprints,
approximate stores, impure callbacks/visitors, incomplete identities, or early termination
are explicit exclusions rather than silently guaranteed cases. The existing no-invariant
selector and DPOR fallback are outside this proof and receive no new guarantees from it.
