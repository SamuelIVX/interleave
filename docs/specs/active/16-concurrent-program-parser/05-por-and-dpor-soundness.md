# 16.05 — Reduction Contracts and Proof Obligations

## TL;DR

V1 includes static POR and DPOR for source control flow. Extend the argument from finite linear
threads to finite forward instructions and terminal cutoffs. This document states obligations;
it is not evidence that existing algorithms already satisfy them.

## Footprints and independence

Reuse `ExpressionReads` and existing memory-location alias rules. For an atomic tree, conservatively
union reads/writes over both conditional arms, all loop bodies, every condition including the final
cutoff test, guards, array-index expressions and runtime-error dependencies. Owner-qualify locals.
Empty blocks have empty footprints except their optional guard. A thread-level decision reads its
condition, writes no shared state, and changes only its owner's PC.

Independence must preserve execution outcome, cutoff existence/reason and chosen successor in
addition to shared values. For two enabled independent actions on different threads, swapping them
must preserve the full modeled state/PC result when both succeed, and must preserve reachable
terminal evidence when one fails/cuts off. Steps are deterministic and definitions immutable.
An action writing a value controlling another's selected arm, evaluation error or bound exhaustion
is dependent through that read. Over-approximation may lose reduction; under-approximation is invalid.

## Required argument

The implementation supplies a reviewed preservation argument with these components:

1. **Termination:** for committed transitions, rank `sum(threadSize - pc)` strictly decreases.
   Conditional targets may skip ahead; exact +1 progress is unnecessary. Source while loops are
   unrolled and atomic internal work is bounded by 03. Cutoffs/failures are terminal, not back edges.
2. **Future dependencies:** every possible future global instruction lies in the current lexical
   suffix. `PropertyPersistentSetComputer` suffix unions and DPOR's future scan therefore remain
   conservative across skipped branches. State explicitly where branch exits/terminal instructions
   are covered. Code must not insert a backward successor.
3. **Properties:** preserve existing complete state-only observations and exhaustive fallback for
   unknown/configuration-dependent/final properties. Atomic interiors are not property observation
   points. Disjoint writes alone do not justify pruning observable intermediate states.
4. **Terminal preservation:** exact reduced search preserves existence of VIOLATION, DEADLOCK,
   COMPLETED and LOOP_LIMIT evidence. It need not enumerate all traces or identical cutoff source
   locations. If a run is incomplete solely because some path hits a loop bound, reduced search
   must retain a cutoff or a conclusive failure; it cannot report PASS.
5. **Caching/sleep sets:** justify visited-state identity with full state/PC and immutable program
   control flow. Show that sleep-set updates and state deduplication do not hide necessary future
   work or the only cutoff witness. Acyclicity alone is not a complete caching proof.

Update the applicability statements in spec 13.08 and spec 15.02 in the implementation PR that
extends their assumptions. Preserve their historical measurements as historical. If an obligation
fails, fix the algorithm and regression before release; do not relabel an exhaustive fallback as
successful POR/DPOR support. Keep existing supported exhaustive fallbacks and report dispatch honestly.

## Prepared execution and terminal cases

Static POR's prepared outcomes must use 02's shared executor. Prepare once per selected action on
isolated state. A failure, cutoff, blocked result or other nonprogress outcome expands that parent's
candidate set conservatively to all enabled choices; reuse already prepared results rather than
executing an atomic block twice. Sleeping candidates still require a sound terminal-preservation
argument; fallback on only encountered cutoffs is not itself a proof.

CBS retains scheduled-condition actions in preemption counting: switching after a branch decision
while its next action is enabled can cost one preemption. Internal atomic decisions/repeats cost no
extra switches. Forward PCs identify unrolled loop allowance; last-thread identity and min-cost
dominance remain required. A loop-limit cutoff and a K-bound cutoff have different causes in 04.

## Acceptance

Use exact DFS plus independently enumerated tiny state/outcome sets as the baseline. Test:

- Concurrent writes to branch/loop conditions and both arms containing future dependencies.
- Branch merge reached through different schedules and repeated lower-cost CBS revisits.
- Atomic interior branches whose unchosen arm still contributes conservative footprints.
- A bound cutoff dependent on another thread's write, including the final post-N condition.
- A hidden-intermediate-property counterexample and the existing observation regressions.
- A prepared source atomic block with execution counters in the test harness proving once-only
  preparation without putting external mutable behavior into modeled production steps.
- A generated finite grid of small branches/loops checked for violation/deadlock/completion/cutoff
  existence and replay evidence; state-count reduction is tested on selected cases, not asserted
  across every control-flow fixture or against CBS counts.

Derive numeric oracle values outside the implementation under test, following repository AGENTS.md.
Require meaningful algorithm mutation tests for condition footprints, successor selection, final
loop checks and cutoff handling. No claim of soundness rests only on two explorers agreeing.

## Implementation preservation argument

The implementation uses `TransitionExecutor` for every attempted search action and replay. Its
scratch copy owns tentative writes. `SourceInstruction` publishes a successor only after the whole
atomic tree succeeds. Its selected target is strictly greater than its pre-action PC and at most
the thread size, checked in both lowering and `Configuration.successorAt`. Thus the nonnegative
rank above decreases on every committed source action. No source action can return BLOCKED.
Disabled entry guards contribute no edge; a true final loop condition produces a terminal attempt.

Instruction definitions and source maps are immutable. The canonical state includes all shared
values, each owner's local values, and every PC. PCs distinguish loop occurrences and selected
branch continuations. The same configuration therefore determines enabledness, chosen targets,
properties and terminal errors/cutoffs without history or hidden loop counters. CBS additionally
retains last-thread and minimum-preemption-cost dominance, as before.

`SourceInstruction.footprint` unions both private arms, conditions, guards, assignment reads and
writes, including array target indices and every expanded final loop test. External decisions
read only their condition; their PC update is owner-local. Existing owner-qualified local and
array alias rules apply. Two enabled actions on different owners with disjoint conflicting
footprints preserve each other's enabledness, selected target and evaluation/cutoff result. They
commute to the same values and PCs on success. If one is terminal, it remains terminal after the
other; no tentative terminal writes become visible. The argument requires these complete footprints,
not merely disjoint writes.

Static POR uses suffix-component selection for source programs even without a property (empty
observations). Every forward continuation is in the lexical suffix, including both branch arms
and the terminal limit test. Cross-component future interference is consequently over-approximated.
A selected invisible successful action commutes earlier across all excluded actions until the
next selected action; moving it does not change property truth. If preparation encounters failure,
cutoff or nonprogress, all enabled candidates are explored, reusing the prepared result. Induction
on rank preserves a terminal witness. Unknown/final observations retain exhaustive fallback.

DPOR sleeps an enabled action only if it is invisible, independent of the chosen action, has no
enable/disable interference, and is independent of *every remaining lexical instruction* of the
chosen owner. This last condition is stronger than current-action independence: sleeping work
cannot require a particular position within that owner's remaining execution. A dependent action
wakes it through `copyFiltering`; a visible action clears sleepers. If all enabled work sleeps, the
existing exhaustive fallback explores it. Otherwise a sleeping action can be postponed past the
awake independent work; swapping it back to the corresponding ancestor sibling branch preserves the terminal
outcome and property. Finite rank prevents indefinite postponement. This covers true final loop
checks as terminal attempts, as well as runtime failure, completion and deadlock.

**Source caching now checks sleep-set subsumption.** At each canonical configuration, a run-local
antichain records copied sleeping sets. An all-enabled-asleep source context is normalized to an
empty set before lookup and ordinary exploration, so fallback work is included in the coverage
comparison rather than introducing a nonmonotone exception. An earlier set S covers incoming T only when S is a subset
of T: the earlier traversal left at least every incoming available action awake. Incomparable sets,
or an incoming smaller set, cause another traversal. Supersets can be removed from the antichain;
they are covered by the new smaller set. Rank ensures that an equal configuration cannot be an
unfinished ancestor, so earlier recorded coverage is complete before a repeated configuration is
encountered. This removes the need to assume that value-state deduplication alone preserves sleep
history. Legacy-program caching is unchanged. For bitstate collisions without an exact coverage
entry, the approximate store may prune; those runs carry approximate guarantees, never exact PASS.
This map participates only in source DPOR, not the exhaustive unknown-observation fallback.

Terminal witness preservation follows by the same commuting and rank induction, with the cache
subsumption condition preserving available continuations. The claim is existence of each terminal
class and of at least one loop cutoff, not enumeration of every schedule or cutoff source span.
Resource stops remain independent incomplete run evidence. Exact-store claims require no resource
stop; collision-pruned bitstate results retain their separately documented limitations.

### Executable evidence

`SourceReductionTest` checks a hand product of three four-position independent threads: exact DFS
has 64 positions and static POR has the ten positions of a serial representative. DPOR is required
to explore fewer than the independently established 64; its implementation-specific count is not
used as an oracle. A separate concurrent-write/final-condition case requires both COMPLETED and
INCOMPLETE and replays every emitted witness. The nine-model initial-value/bound grid compares all
terminal classes across exact DFS, static POR, DPOR and sufficiently bounded CBS. These comparisons
supplement the independent numeric and schedule oracles; agreement alone is not the proof.
`SourceBoundaryTest` covers property runtime errors and loop evidence retained across a resource
stop. Existing property-observation regression suites continue to apply.
