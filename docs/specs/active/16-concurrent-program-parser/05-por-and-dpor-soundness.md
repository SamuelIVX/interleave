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
