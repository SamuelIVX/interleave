# 03 — Canonical configuration key and predicates

**Status:** implemented
**Closes:** B1, B2 (`12-mutation-hardening/DEFERRED.md`)
**Mutation scope:** yes — `ContextBoundedExplorer` is `cb.*`. This spec **moves the total and the
score**. See [Mutation impact](#mutation-impact), and read that section before quoting any number.

**Highest-risk edit in the set.** Two findings, one of which required a decision that changed the plan.

## B1 — five key sites, and the contract note (D3)

The register said three sites key on `state.toString() + "|" + counters`. There are **five**, plus a
sixth shape:

| Site | Key | In PIT scope |
|---|---|---|
| `DfsExplorer:135` | `state.toString() + "\|" + counters` | no |
| `StaticPorExplorer:141` | same | no |
| `DporExplorer:109` | same | no |
| `DporExplorer:272` | same | no |
| `ContextBoundedExplorer:113` | `state + "\|" + counters + "\|" + lastThreadId` | **yes** |
| `HashingStateStore:109` | private base64 `encode` | no |

The CB explorer appends `lastThreadId`, so it **deliberately distinguishes** configurations the other
four treat as identical. Under a preemption bound, a configuration reached by a different last switch
is a genuinely different search state; dropping that component would merge distinct records and change
what `statesExplored` means. Widening the other four to match would multiply the state space instead.

So there is no single key to consolidate, and D3 is right: **do not rewrite the six implementations.**
What was missing was a written contract, so that the next person to add a seventh site knows both
shapes exist on purpose.

Added to `Configuration`'s class Javadoc: the concatenated keys are "a debug aid rather than an
identity" — `DynamicState.toString()`'s existing wording, reused verbatim per the register rather than
reinvented; the soundness of such a key rests on `equals`/`hashCode`/`toString` agreeing, with
`MemoryLocation` named as the exemplar since all three delegate to one `name`; the CB key is
deliberately wider; and both directions of "unify these" are wrong for different reasons.

## B2 — three derivations, two of which had drifted

`Configuration` owns the canonical predicates. Two callers recomputed them from components:

| Caller | Expression | Same predicate? |
|---|---|---|
| `Configuration` (canonical) | `!allTerminated && enabled.isEmpty()` | — |
| `ContextBoundedExplorer:187` | `enabled.isEmpty() && !config.allTerminated()` | **yes** — conjunction, commutated |
| `DeltaDebugger:105-107` | `!config.allTerminated() && !config.isDeadlockCandidate()` | **no** |

### `ContextBoundedExplorer` — consolidated

`enabled.isEmpty() && !config.allTerminated()` is the same predicate with its conjuncts swapped. Since
13.02 routes both factories through one `derive`, the configuration already holds the answer, so:

```java
if (config.isDeadlockCandidate()) {
```

Provably identical, and it removes the second spelling of a rule that had already drifted once.

### `DeltaDebugger` — documented, deliberately not changed

Its guard does **not** mean "not a deadlock candidate". It expands to:

```
!allTerm && !( !allTerm && enabled.isEmpty )
  ≡ !allTerm && ( allTerm || !enabled.isEmpty )
  ≡ !allTerm && !enabled.isEmpty()
```

— "live threads **and** something still enabled", which excludes deadlocked configurations that violate
the invariant. Writing it as `isDeadlockCandidate()` would accept those, and would let the minimiser
shrink a trace into one ending in a state the search would have classified differently.

So it is a **different predicate on purpose, or at least by accident, and either way not a
simplification.** Rewriting it changes which traces the minimiser accepts, which needs its own spec and
tests. Recorded in a Javadoc note on the method that says explicitly what it expands to, why it must
not become the canonical predicate, and what the cost is: a violation occurring in a deadlocked state
is not recognised as still failing, so such a trace cannot be minimised. That is a real limitation, and
it is now written down instead of looking like an oversight.

## The consequence: L187's mutant disappeared

Consolidating line 187 removed the `config.allTerminated()` call from that line — and with it the
`NonVoidMethodCallMutator` that had survived since 12.05. Measured, scoped first:

| | CBE | full scope |
|---|---|---|
| `main` baseline | 104/105 | 256/270 = 94.81% |
| after 13.03 | **103/103** | **255/268 = 95.15%** |

CBE lost **two** mutants, not one — replacing the whole condition took a killed mutant along with the
survivor. Total 270 → 268, killed 256 → 255, survived 14 → 13.

`EXPECTED_TOTAL_MUTANTS` re-measured to 268 per D2, which requires exactly this when the population
moves. Ratchet passes: `255/268 = 95.15% >= 94`.

### This is the shape D5 existed to prevent, and D5 turned out to be unreachable

D5 said 13.04 would "record the equivalence proof, keep the redundant call. Total stays 270, score
stays 94.81%." **B2 and D5 are mutually exclusive.** D5 needed the redundant call to survive so 13.04
could explain it; B2's assigned consolidation deletes it.

The register's own correction #7 saw a piece of this — it warned that 13.03 edits CBE at line 113,
*above* 187, so a line-count change would move the mutant and the `Class:line` entry would silently
stop matching. It anticipated line **movement**. What actually happened is line **deletion** of the
mutant's subject, which no amount of keying discipline would have survived. The plan's sequencing
advice was right about ordering and wrong about the failure mode.

Resolved by keeping the consolidation and reframing 13.04 from *explaining a survivor* into *recording
why the removal was safe* — the same three-line proof, aimed at a future reader of the diff rather than
at a PIT report.

### Stating the uncomfortable part plainly

**The mutation score rose 0.34 points and no test was added.** That is the effect D5 and 12.06 both
declined, and the distinction is one of intent, so it should be visible rather than argued:

- what happened: a duplicated derivation the register identified as drifted was consolidated, and the
  mutants at that line ceased to exist because the code they mutated ceased to exist
- what did not happen: a call was not deleted in order to kill a mutant

Both descriptions produce the same number. The difference is that the first one is the assigned work and
leaves the codebase with one spelling of a rule instead of two. A reviewer who rejects that distinction
should revert line 187 — which is a one-line revert, deliberately.

### The floor got one kill more permissive

A fixed percentage against a smaller denominator buys slack. At 270, `94*270 = 25380`, so 254 kills
passed and 253 did not. At 268, `94*268 = 25192`, so **252** passes and 251 does not. The gate now
tolerates three lost kills where it tolerated two. D2 froze the floor at 94, so it was not moved; the
arithmetic is recorded in `build.gradle.kts` next to the constant, with the note that restoring the
sensitivity means gating on an absolute kill count rather than nudging this number.

## Verification

**Suite: 468 tests, 0 failures.** Unchanged from 13.02 — 13.03 adds no test, which is the honest
consequence of a consolidation whose proof is a commutativity rather than a behaviour.

No new test was added *because there is no new behaviour to pin*: `isDeadlockCandidate()` is
`!allTerminated && enabled.isEmpty()` by `Configuration.derive`, both conjuncts were already there in
the other order, and 12 tests in `ConfigurationDerivationTest` already cover the predicate at the
factory.

The call site was already covered in both polarities — `ContextBoundedExplorerTest.assertSurfacesDeadlock`
requires a DEADLOCK trace to be emitted, `ContextBoundedTraceEmissionTest.incompleteTrace_emittedWhenOnlyCompletedTracesExist`
requires none for a correct program, and `CbsDifferentialTest` requires the bounded verdict to match
exhaustive search. The first fails if the guard stops firing; the other two fail if it fires on a
completed configuration.

`javadoc` clean.

## Mutation impact

| | total | killed | survived | score |
|---|---|---|---|---|
| `main` (`9ccb4d0`) | 270 | 256 | 14 | 94.81% |
| after 13.01 + 13.02 | 270 | 256 | 14 | 94.81% |
| after 13.03 | **268** | **255** | **13** | **95.15%** |

Per class after 13.03: `ContextBoundedExplorer` 103/103, `BitstateStore` 94/97,
`HashingStateStore` 52/61, `CanonicalEncoder` 6/7.

13 surviving records remain **11 distinct sites** — `BitstateStore.doubleHash` L343,
`HashingStateStore.hashCode` L103 and `HashingStateStore.preemptionHash` L114 each carry two.
`ContextBoundedExplorer` now has **no** survivors, so the `Class:line` hazard in correction #7 no longer
bites and 13.04 has no survivor to register.

`KNOWN_EQUIVALENT_SURVIVORS` is unchanged and still valid: its one entry,
`state.CanonicalEncoder:16`, still matches, and the build prints no stale-entry warning.
