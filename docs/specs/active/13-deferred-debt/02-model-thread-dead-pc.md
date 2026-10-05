# 02 — `ModelThread` dead program counter

**Status:** implemented (sign-off given before the delete)
**Closes:** A1 (`12-mutation-hardening/DEFERRED.md`)
**Mutation scope:** none directly — `core.*` is outside the `state.* + cb.*` scope. See
[Mutation impact](#mutation-impact).

## What was dead

`ModelThread` carried a mutable `pc` plus five methods built on it. Enumerating every caller across
`src/`, main and test:

| Member | External callers |
|---|---|
| `pc()` | none |
| `advance()` | none |
| `terminated()` | none |
| `nextStep()` | none |
| `enabled(SharedState)` | one — `Configuration.initial()` |
| field `pc` | written by the constructor and by `advance()`; read only by `pc()` |

`pc` is written in exactly two places. The constructor sets it to 0; `advance()` increments it and has
no callers. Its only reader, `pc()`, also has no callers. So `pc` is **provably always 0**, and
everything built on it is provably constant:

- `terminated()` is always `0 >= steps.size()`, i.e. false for any thread with at least one step
- `nextStep()` is always `steps.get(0)` for such a thread, and never null
- `enabled(state)` is always `steps.get(0).enabled(state)`

### The qualifier that matters

"Always false" is true **only for a thread with a non-empty step list.** `ModelThread`'s constructor
rejects `null` but not an empty list, so `new ModelThread(0, List.of())` is constructible, and for it
`terminated()` returns true, `nextStep()` returns null, and `enabled()` returns false.

That case was handled correctly. `Configuration.successor()` never consulted `terminated()`; it compared
`currentPc < t.steps().size()` directly, which classifies an empty-step thread as terminated on its
own. The dead accessors were therefore not load-bearing even for the degenerate input, which is why
removing them is safe rather than merely lucky. The unqualified version of this claim would have been
false, and the test `initialTreatsAThreadWithNoStepsAsAlreadyTerminated` now pins the real behaviour.

## What was done

Deleted `pc`, `pc()`, `advance()`, `terminated()`, `nextStep()`, `enabled()`, and the already-unused
`java.util.Collections` import. `ModelThread` is now an id and a list of steps, with a class comment
saying why position is deliberately not tracked here.

`Configuration.initial()` was its only caller, and was rewritten to derive `enabledThreadIds` from the
counters the same way `successor()` does.

## The unification

Removing the accessors exposed that `initial()` and `successor()` each had their own copy of the same
derivation, written differently:

- `initial()` asked the dead `enabled()` / `terminated()`, i.e. counter-implicitly 0
- `successor()` compared counters against `steps().size()` directly

Both now call one private `derive(threads, counters, state)` returning the enabled set and the
terminated flag, and one `isDeadlockCandidate(derived)` applying `!allTerminated && enabled.isEmpty()`.
That is the whole of both factories' derived state, expressed once.

### A redundant ternary fell out

`successor()` chose which counter to judge a thread by:

```java
int currentPc = (i == threadId && outcome != StepOutcome.BLOCKED)
    ? nextPcs.get(i)
    : this.programCounters.get(i);
```

`nextPcs` is a copy of `programCounters` with exactly one element changed — `threadId`, and only when
the outcome is not `BLOCKED`. So in all three branches `currentPc` equals `nextPcs.get(i)`:

- `i != threadId` → `nextPcs.get(i)` was never touched, so it equals `programCounters.get(i)`
- `i == threadId`, `BLOCKED` → likewise untouched
- `i == threadId`, not blocked → the branch already picks `nextPcs.get(i)`

The branch was noise that read as if the two counters could diverge. Routing both factories through
`derive` removes it by construction rather than by a separate edit, and
`blockedThreadIsStillJudgedByTheStepItRemainsOn` pins the result it preserved.

## Verification

**Behaviour-preserving, argued and then measured.** The equivalence claims above are checkable by
reading: same enabled set, same terminated flag, same deadlock expression. The suite is the check.

`core/ConfigurationDerivationTest`, 12 tests, covering the rule rather than the two entry points:

- `initial()` puts every counter at 0 and derives enabled from the step at 0
- a thread with no steps is terminated from the outset, and does not hide a live sibling
- an advanced successor advances only the stepping thread; a blocked one advances nothing
- a blocked thread is still judged by the step it remains on, and stepping past a disabled step is
  what enables it — the case the removed ternary special-cased
- advancing onto a disabled step removes the thread from the enabled set
- stepping past the last step terminates the thread
- all threads terminated with nothing enabled is **not** a deadlock candidate
- live threads with nothing enabled **is** one
- the two factories agree about the same counter positions

**Suite: 468 tests, 0 failures** (was 456; +12). `javadoc` clean.

### Two of these failed first, and both were fixture errors

Worth recording, because the second is the exact failure this spec set exists to correct.

`blockedThreadIsStillJudgedByTheStepItRemainsOn` asserted an empty enabled set for threads
`(false, true)` and `(true)`. Thread 1 *is* enabled, so the set is `[1]`. The assertion was written
from the shape of the fixture rather than from what the fixture contains.

`everyThreadTerminatedIsNotADeadlockCandidate` advanced only thread 0 and then asserted
`allTerminated()`. Thread 1 was still on its only step, so `allTerminated()` was correctly `false`.
**The test asserted a conclusion it had not arranged** — it claimed every thread was terminated while
one was live. This is the register's own recurring mistake in miniature: the number was plausible, so
it was written down without checking the precondition that produces it. Fixed by advancing both
threads, with the counter vector asserted so the arrangement cannot drift again.

In both cases the production code was right and the test was wrong, which is the reassuring direction
to fail in and the reason the derivation is now covered directly rather than only through the
explorers that happen to call it.

## Mutation impact

None. `ModelThread` and `Configuration` are in `core`, outside the `state.* + cb.*` scope, so no
mutant in either file is generated. The floor is unmoved, and the 12 new tests are not measurable by
the current scope.

That was **measured, not argued** — the repository rule against carrying PIT numbers forward from an
older spec's notes applies here too, and "no mechanism by which the floor could move" is an argument.
Full-scope runs on `main` (`9ccb4d0`) and on this branch, diffed:

| | total | killed | survived | `NO_COVERAGE` |
|---|---|---|---|---|
| `main` baseline | 270 | 256 | 14 | 0 |
| after 13.01 + 13.02 | 270 | 256 | 14 | 0 |

**94.8% both before and after, and the survivor lists are identical** — no new survivors, none fixed.
Per class, unchanged in both runs:

| Class | killed/total | survived |
|---|---|---|
| `ContextBoundedExplorer` | 104/105 | 1 |
| `BitstateStore` | 94/97 | 3 |
| `HashingStateStore` | 52/61 | 9 |
| `CanonicalEncoder` | 6/7 | 1 |

`ContextBoundedExplorer` at 104/105 independently reproduces the figure the repository's `AGENTS.md`
records, which is a useful check that the run covered what it claims to.

### 14 survivor records are 11 distinct sites

Three sites carry two surviving mutants each, so **14 records / 11 sites** — the two numbers describe
different things and the register has been bitten by this before:

| ×2 | site |
|---|---|
| 2 | `BitstateStore.doubleHash` L343 `MathMutator` |
| 2 | `HashingStateStore.hashCode` L103 `MathMutator` |
| 2 | `HashingStateStore.preemptionHash` L114 `MathMutator` |

Any later spec that counts survivors must state which of the two it is counting. The single
`ContextBoundedExplorer` survivor is `dfs` L187 `NonVoidMethodCallMutator` — the L187 mutant that 13.04
addresses, still open here.

Both reports carry `partial="true"`. Taken with the 104/105 agreement this looks like PIT's usual
partial marking rather than missing analysis, but it is not verified, and later specs should not treat
270 as a floor that cannot move.

