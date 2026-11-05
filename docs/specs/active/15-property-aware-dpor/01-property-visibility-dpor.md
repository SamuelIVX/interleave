# 15.01 — Dynamic Property-Aware Partial Order Reduction: Visibility Contract & Algorithmic Dispatch

## TL;DR

Dynamic partial order reduction in `DporExplorer` uses sleep sets to avoid exploring redundant interleavings of commuting transitions. This specification defines how state-property visibility integrates with sleep set maintenance, allowing pruning of invisible transitions while guaranteeing that all property state changes and intermediate invariant evaluations remain soundly observed.

## Operational Contracts

### 1. Invariant Observation Classification

An invariant `Invariant` evaluates safety predicates against modeled configurations:

```java
public interface Invariant {
    boolean holds(SharedState state, Configuration config);
    default Optional<Set<MemoryLocation>> observedLocations() { return Optional.empty(); }
}
```

When `DporExplorer.explore(program, invariant, stateStore, stateVisitor)` is invoked:
1. **Case A: `invariant == null`**
   - Standard DPOR exploration.
   - Observations $\mathcal{O} = \text{null}$.
   - Pruning operates under classic Mazurkiewicz equivalence for terminal completion and deadlock freedom.
2. **Case B: `invariant != null && invariant.observedLocations().isPresent()`**
   - Known, complete state observation footprint $\mathcal{O} = \text{invariant.observedLocations().get()}$.
   - Executes `dporDfs(...)` with property-aware sleep-set maintenance.
   - Invisible transitions commute and sleep; visible transitions are never put to sleep and visible executions refresh sleeping transitions.
3. **Case C: no applicable observation contract**
   - The invariant is non-null, state-only observations are unknown, and either the program is legacy or final observations are also unknown.
   - Unknown observations, configuration-dependent callbacks, or legacy DSL `final` checks.
   - The explorer cannot verify whether an arbitrary step might alter the predicate's truth value.
   - Fallback to unreduced, exhaustive depth-first search (`dfsDfs`) is strictly preserved.

4. **Case D: declared final source observations (Spec 70)**
   - `program.isSource()` is true, the original state-only observations are unknown, and `finalObservedLocations()` is present.
   - Use the final expression's complete read set as O under the separate [completion-gate argument](../70-final-source-property-reduction.md).
   - The existing visibility/sleep rules remain unchanged. This does not infer metadata for opaque callbacks or broaden legacy dispatch.

### 2. Transition Visibility Rules

Given complete observation set $\mathcal{O}$ and step $s$:
- **Visibility Test**:
  $$\text{Visible}(s, \mathcal{O}) \iff \text{relation.writesOverlap}(s.\text{writes}(), \mathcal{O})$$
- A step $s$ is **visible** if any of its declared writes overlaps with any location in $\mathcal{O}$ (accounting for array base-vs-element aliasing).
- A step $s$ is **invisible** if none of its declared writes overlaps with $\mathcal{O}$.

### 3. Sleep Set Maintenance Rules

In `dporDfs`, when iterating over enabled threads:
1. **Rule 1: Visible Transitions Never Enter Sleep Sets**
   When preparing `nextSleepSet` for an explored transition `step`, candidate peer step `otherStep` may only be added to `nextSleepSet` if:
   $$\neg\text{Visible}(\text{otherStep}, \mathcal{O})$$
   along with existing conditions (independence with `step`, no enable/disable interference, and no future dependent step in the stepping thread).
2. **Rule 2: Executing a Visible Transition Refreshes Sleep Sets**
   If the executed step is visible ($\text{Visible}(\text{step}, \mathcal{O})$), executing it modifies the valuation of the property's state footprint $\mathcal{O}$. Any transitions previously sleeping were put to sleep under the prior valuation. Therefore:
   $$\text{nextSleepSet} = \begin{cases} \text{new SleepSet}() & \text{if } \text{Visible}(\text{step}, \mathcal{O}) \\ \text{sleepSet.copyFiltering}(\text{relation}, \text{step}) & \text{if } \neg\text{Visible}(\text{step}, \mathcal{O}) \end{cases}$$
3. **Rule 3: Filtering Retains Independence**
   For invisible executed steps, `copyFiltering` retains only sleeping transitions that are pairwise independent with `step`. Any step dependent on `step` is immediately woken.

### 4. Progress and Safety Fallback

If every enabled transition at a configuration is currently in `sleepSet` (`!explored`), the progress fallback executes all enabled transitions with `new SleepSet()`. This guarantees that linear model execution never deadlocks prematurely due to sleep-set saturation.
