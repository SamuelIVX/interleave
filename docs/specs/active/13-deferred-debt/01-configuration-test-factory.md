# 01 — `Configuration` test factory

**Status:** implemented
**Closes:** D1 (`12-mutation-hardening/DEFERRED.md`)
**Mutation scope:** none. `core.*` is outside the `state.* + cb.*` PIT scope, so this spec cannot move
the floor in either direction. See [Mutation impact](#mutation-impact).

## Problem

Two tests needed a `Configuration` with threads parked at arbitrary program counters, and could not
build one. `Configuration` exposes exactly two factories, `initial` and `successor`, and neither can
place a thread at a chosen position: `initial` starts every thread at 0, and `successor` only ever
advances the thread that just stepped, following a real traversal.

Both tests reached past the API instead:

| File | Helper | Mechanism |
|---|---|---|
| `state/HashingStateStoreLifecycleTest` | `newConfiguration` | `getDeclaredConstructor` + `setAccessible(true)` |
| `state/BitstateStoreDiagnosticsTest` | `configurationWithCounter` | `getDeclaredConstructor` + `setAccessible(true)` |

Each reached the eight-argument private constructor, whose last three meaningful parameters are
`enabledThreadIds`, `allTerminated`, and `deadlockCandidate`. That constructor is the problem, not the
reflection. It accepts all three independently, so a caller can assemble a configuration whose own
parts contradict each other.

Both helpers then passed `List.of(0, 1), false, false` — at every call site, without exception.
`enabledThreadIds` was a non-empty constant, and `allTerminated` and `deadlockCandidate` were both
`false`. So neither helper had a parameter that varied; it had three that never did.

The consequence is the part worth stating plainly. **Every fixture these helpers could build was
structurally incapable of being all-terminated or a deadlock candidate.** Not by choice — by
construction. A store test that wanted a terminal or deadlocked configuration had no way to ask for
one, so no such test existed, and the absence looked like an absence of interest rather than an
absence of capability.

Reflection is also brittle in the ordinary way: the helper hard-codes eight parameter types in order,
so any constructor change breaks it at runtime, in a test, with a reflection stack trace instead of a
compile error.

## Design

Add a test-visible factory to `Configuration` that **derives** `allTerminated` and `deadlockCandidate`
from the counters, rather than accepting them:

```java
public static Configuration forTest(SharedState state, List<Integer> programCounters,
                                    List<Integer> enabledThreadIds)

public static Configuration forTest(SharedState state, List<Integer> programCounters,
                                    List<Integer> stepsPerThread, List<Integer> enabledThreadIds)
```

The three-argument form treats every counter as a live position (`stepsPerThread = pc + 1`), which
makes `allTerminated` false by derivation rather than by assertion. The four-argument form supplies
the step counts so `allTerminated` is computed properly: a counter at or beyond its thread's step count
is terminated.

Both compute `deadlockCandidate` as `!allTerminated && enabledThreadIds.isEmpty()` — the same
expression `successor()` uses at `Configuration.java:117`.

### Why `enabledThreadIds` is still a parameter

This is the part of the design that gives something up, so it should be argued rather than assumed.

Deciding whether a counter denotes an *enabled* position means evaluating the DSL step sitting at
that counter, which requires the `ModelThread`. Both call sites deliberately build counters that are
**not plausible thread positions** — `HashingStateStoreLifecycleTest` says so in its own comment, and
the R4 colliding pair depends on it. A caller with implausible counters has no thread list to offer.

So the factory derives the two booleans it can and still takes `enabledThreadIds`. That is strictly
more than before: `deadlockCandidate` is now a function of `enabledThreadIds` and the counters, so
those two can no longer disagree. What remains under caller control is the choice of `enabledThreadIds`
itself, which is honest — it is genuinely the input, not a derived fact.

Deriving `allTerminated` requires step counts, which is why the four-argument overload exists rather
than the factory hardcoding `false`.

### What this makes newly expressible

`forTest(state, counters, List.of())` — live positions, nothing enabled — is a deadlock candidate.
Reaching that through the old helpers meant passing `deadlock=true` by hand, which is precisely the
assertion the factory now makes on the caller's behalf. That configuration was unconstructible
before and is one call away now.

## Verification

`core/ConfigurationFactoryTest`, 10 tests. The property under test is not that fields get filled in,
it is that the derived booleans cannot contradict their inputs:

- live positions are neither terminated nor deadlocked
- live positions with an empty enabled set **are** a deadlock candidate
- a counter exactly at its step count is terminated — the boundary, since `>=` and `>` differ here
- all-terminated with nothing enabled is **not** a deadlock candidate (the `!allTerminated` guard; a
  finished exploration and a deadlock are different things and conflating them makes a search stop
  early or report a phantom)
- one live thread among terminated ones: not terminated, and deadlocked
- zero threads is vacuously all-terminated
- mismatched counter/step-count arity is rejected, and the message names both counts
- lock ownership and wait queues are empty
- counters are defensively copied, so a fixture is not mutable through the caller's list afterwards
- `lastOutcome` is unset, because the factory describes a starting position rather than a step

Both migrated call sites pass `Configuration.forTest(state, counters, List.of(0, 1))`, which is
exactly the `List.of(0,1), false, false` they used to hardcode — now derived.

**Suite: 456 tests, 0 failures** (was 446; +10 new). `./gradlew javadoc` clean.

### Two of these tests failed first, and the fixtures were wrong

Worth recording, because the failures were arithmetic rather than conceptual. Two tests asserted
`allTerminated` for counters `[2, 5]` against step counts `[4, 4]`. Thread 0 sits at 2, which is
*before* its step count, so it is live — `allTerminated` is correctly `false` and the factory was
right. The fixtures were corrected to `[4, 7]`, which also puts a counter exactly on the boundary,
which is the case the boundary test should have been testing all along.

## Mutation impact

None, and this is a scope fact rather than a claim. The PIT scope is `state.*` and `cb.*`;
`Configuration` lives in `core`, so no mutant in it is generated either way. The 10 new tests are
therefore not measurable by the current scope and do not appear in any coverage figure.

The honest reading: this spec removes a capability gap and two brittle reflection blocks, and the
mutation floor is unmoved. Nothing here should be described as closing mutants.
