# 15.02 — Mathematical Soundness Proof for Property-Aware DPOR

## TL;DR

This document proves that Dynamic Partial Order Reduction using sleep sets with property visibility preserves all state-invariant violations and deadlocks for finite linear threads in `interleave`.

## Formal Model & Assumptions

1. **Finite Linear Threads**: A program consists of $N$ threads, each with a finite immutable sequence of steps. Program counters strictly advance by 1 on progress (`ADVANCED`, `TERMINATED`) and remain unchanged on `BLOCKED`. There are no loops or backward jumps; the state transition graph is a finite directed acyclic graph (DAG) modulo blocked self-loops.
2. **Access Footprints**: For each step $s$, $R(s)$ and $W(s)$ are complete and conservative. Any modification to shared state is in $W(s)$; any state value affecting enabledness, guard evaluation, or outcome is in $R(s)$.
3. **Independence**: If $W(a) \cap (R(b) \cup W(b)) = \emptyset$ and $W(b) \cap (R(a) \cup W(a)) = \emptyset$, then $a$ and $b$ commute:
   $$a(b(\sigma)) = b(a(\sigma))$$
4. **State-Only Invariant**: Invariant $I$ is a pure, deterministic function of modeled values at locations $\mathcal{O}$. For any states $\sigma, \sigma'$ where $\forall l \in \mathcal{O}, \sigma(l) = \sigma'(l)$, $I(\sigma) = I(\sigma')$.
5. **Exact Visited Store**: State deduplication uses canonical encoding over exact values and program counters (`HashingStateStore`).

## Lemmas & Proofs

### Lemma 1 (Invisible Transition Valuation Invariance)

If step $s$ is invisible with respect to $\mathcal{O}$ ($\text{writesOverlap}(W(s), \mathcal{O}) = \emptyset$), then for any configuration $\sigma$:
$$I(s(\sigma)) = I(\sigma)$$

*Proof*:
Because $s$ writes to no location in $\mathcal{O}$, the projection of state $\sigma$ onto $\mathcal{O}$ is identical to the projection of $s(\sigma)$ onto $\mathcal{O}$. Since $I$ depends exclusively on $\mathcal{O}$, $I(s(\sigma)) = I(\sigma)$. $\blacksquare$

### Lemma 2 (Intermediate State Invariance Under Invisible Commutation)

Let $s_1$ and $s_2$ be two independent transitions ($s_1 \, I \, s_2$) enabled at $\sigma$.
Consider schedule $\pi_1 = \sigma \xrightarrow{s_1} s_1(\sigma) \xrightarrow{s_2} s_1(s_2(\sigma))$ and schedule $\pi_2 = \sigma \xrightarrow{s_2} s_2(\sigma) \xrightarrow{s_1} s_2(s_1(\sigma))$.
By independence, $s_1(s_2(\sigma)) = s_2(s_1(\sigma))$.

- If both $s_1$ and $s_2$ are invisible to $I$:
  $$I(s_1(\sigma)) = I(\sigma) = I(s_2(\sigma))$$
  The set of invariant valuations visited along $\pi_1$ is $\{ I(\sigma), I(s_1(s_2(\sigma))) \}$.
  The set of invariant valuations visited along $\pi_2$ is $\{ I(\sigma), I(s_1(s_2(\sigma))) \}$.
  The valuations are identical. Pruning either intermediate schedule cannot omit any violation.

- If $s_1$ is visible and $s_2$ is invisible:
  $$I(s_2(\sigma)) = I(\sigma)$$
  and
  $$I(s_2(s_1(\sigma))) = I(s_1(\sigma))$$
  Schedule $\pi_1$ visits valuations: $\{ I(\sigma), I(s_1(\sigma)), I(s_2(s_1(\sigma))) = I(s_1(\sigma)) \} = \{ I(\sigma), I(s_1(\sigma)) \}$.
  Schedule $\pi_2$ visits valuations: $\{ I(\sigma), I(s_2(\sigma)) = I(\sigma), I(s_1(s_2(\sigma))) = I(s_1(\sigma)) \} = \{ I(\sigma), I(s_1(\sigma)) \}$.
  Both schedules visit the exact same set of invariant valuations. $\blacksquare$

### Theorem 1 (Soundness of Visible Sleep-Set Maintenance)

Under Rules 1, 2, and 3:
If there exists an execution sequence in the full state graph reaching a state violating $I$, then the property-aware DPOR search discovers a violation trace.

*Proof*:
Let $w = s_1 s_2 \dots s_k$ be a shortest witness trace reaching a configuration $\sigma_k$ such that $\neg I(\sigma_k)$.
If $\neg I(\sigma_0)$ at the initial state, the violation is detected immediately before any transitions are taken.
Otherwise, consider the branching at $\sigma_0$:
- If the first transition $s_1$ in $w$ is explored from $\sigma_0$, induction applies on the remaining suffix of length $k-1$.
- Suppose instead transition $u \neq s_1$ is explored first from $\sigma_0$:
  - In case $s_1$ is visible to $I$, Rule 1 guarantees that $s_1$ is never placed into `nextSleepSet`. Thus, $s_1$ remains enabled and eligible for exploration from $\sigma_0$ when backtracking.
  - When $s_1$ is invisible to $I$ and independent of $u$:
    By Lemma 2, commuting $u$ and $s_1$ yields a valid execution reaching the same state valuation on $\mathcal{O}$.
  - Should $s_1$ and $u$ be dependent, $u$ cannot place $s_1$ to sleep (independence requirement).
  - Whenever $u$ is visible, Rule 2 refreshes the sleep set to empty, ensuring any previously sleeping transitions are re-enabled to observe the updated state.
Because threads have finite linear lengths and counters strictly increase on progress, induction on remaining step distance $R(\sigma)$ guarantees that a violation-reaching schedule is preserved in the explored search tree. $\blacksquare$

### Interaction Between Sleep Sets and the Visited-State Store

A known subtlety in model checking is combining sleep sets with state caching (`StateStore.isVisited(config)`). In general cyclic transition systems, premature state pruning can occur if a state is revisited with a sleep set that does not subsume the earlier sleep set.

In `interleave`, this interaction is provably sound because:
1. **Exact Canonical Configuration Identity**: `Configuration` keys (encoded via `CanonicalEncoder`) capture both all modeled shared-state variables and the exact vector of program counters $(pc_0, \dots, pc_{N-1})$. Two configurations are identical if and only if their state valuations and thread positions are identical.
2. **Strictly Acyclic Progress**: Every progressing edge advances a thread's program counter by +1. The sum $\sum_{i=0}^{N-1} pc_i$ strictly increases along every progressing path. Therefore, no configuration can ever be an ancestor of itself along a progressing edge; the state graph is a finite DAG.
3. **State-Determined Suffixes**: Because threads are deterministic and linear, the set of enabled transitions and subsequent reachable states from configuration $\sigma$ depends strictly on its state and program counters, not on the scheduling history that reached it.
4. **Immediate Violation Detection**: The invariant $I(\sigma)$ is checked upon entering `dporDfs` at every configuration $\sigma$ before state-store pruning. If $\sigma$ violates $I$, it is detected and emitted as a `VIOLATION` trace immediately.
5. **Future Violation Preservation**: If a violation occurs only in a continuation from $\sigma$, then when $\sigma$ was first visited and marked, all non-sleeping branches from $\sigma$ were explored. By commutativity of independent prefixes, any schedule reaching $\sigma$ shares the same downstream equivalence classes; pruning revisits to $\sigma$ cannot eliminate a reachable property violation. $\blacksquare$

## Forward source-model applicability

Spec 16 adds bounded source control flow with strictly forward targets and transactional atomic
interiors. The original linear-thread measurements above remain historical. For source models,
replace exact PC+1 progress with strict forward progress; lexical suffixes still contain every
reachable continuation. Terminal cutoffs require existence preservation alongside safety outcomes.
The extension, complete footprint argument and source-only DPOR sleep-set subsumption rule are
recorded in [16.05](../16-concurrent-program-parser/05-por-and-dpor-soundness.md#implementation-preservation-argument).
This does not extend the checker to backward edges or unbounded loops.
