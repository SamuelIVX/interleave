# 06 — Thread-count-general flag arrays

**Status:** implemented
**Closes:** A3 (`12-mutation-hardening/DEFERRED.md`)
**Mutation scope:** none — `core`, `format` and `format.registry` are all outside `state.* + cb.*`.

## The ceiling

`StateRegistry` rejected any `flags` array whose length was not 2, for both `peterson` and
`deadlock`, with *"must have exactly 2 elements"*. Three-thread programs were inexpressible in the
typed format regardless of what the `threads` array said.

The register frames this as a capability ceiling rather than a correctness risk, and that is right: the
failure is loud, and the states that could be built were the ones intended. But the limit sat in the
wrong place. The steps already indexed flags by thread id — `setFlag(threadId, value)`,
`busy_wait(other)`, `cs_enter` — so nothing downstream assumed two. Only the size check and three
methods that hardcoded two elements stood in the way.

**`counter` had the identical ceiling by a different route.** `StateRegistry` called
`CounterState.of(int)`, whose one-argument constructor delegates to `this(counter, 2)`. Not named in
the register, and fixed here because it is the same one-line mistake in the same file: un-capping two of
three identical ceilings and leaving the third would be arbitrary. `lost-update-3t.json` gets three
threads today only because it uses the *DSL* format (`fields`/`locals` → `DynamicState`, up to 8
threads), not the registry.

## What changed

**`StateFactory.create` gains a `threadCount` parameter, and `create` has no one-argument overload.**
That is the load-bearing decision. A defaulting overload would have let every existing call site keep
compiling and silently receive the old two-thread behaviour — the exact failure the item is about,
now hidden behind a convenient signature. Making the count required meant all 8 call sites were
visited, and `ProgramLoader` passes `threadDefs.size()`.

**`peterson`, `deadlock` and `counter`** size from that count. `flags.length` must equal the thread
count; a disagreement is a malformed program and is reported, not padded.

**`PetersonState` and `DeadlockState`** accept any flag array of length ≥ 2 rather than exactly 2, and
`deepCopy`, `encodeTo` and `toString` stopped hardcoding `flag[0]`/`flag[1]`. The two-thread
`of(boolean, boolean, ...)` factories are retained, since most call sites want them.

**A floor of two is kept.** Peterson's algorithm and the deadlock demo are both defined for two or more
threads, so one thread is rejected rather than padded up to two. This is a relaxation, not a change of
kind: length 3 used to throw and now works; length 0 and 1 still throw.

**`encodeTo` writes the flag count before the flags.** Without it a two-thread and a three-thread state
differ only in total byte count, which keeps them apart today but for an incidental reason — exactly the
kind of thing `DynamicState`'s array-boundary gap was. `CounterState.encodeTo` already wrote
`registers.length`, so this follows an existing precedent in the codebase rather than inventing one.
This changes every store key for these two types; nothing depends on the old byte layout.

## Six tests broke, and each was fixed to keep its intent

A signature change that touches validation ordering is where this kind of work actually goes wrong, so
the failures are worth listing rather than summarising:

| Test | Why it broke | Fix |
|---|---|---|
| `loadIncompatibleStep_throws` | 1-thread peterson; my floor fired before the step-compatibility check | added a second thread, so it still tests step compatibility |
| `loadOtherOutOfRange_throws` | same | added a second thread |
| `loadInvariantIncompatibleWithState_throws` | same | added a second thread |
| `loadMutualExclusionPeterson_threeThreads_throws` | 3 threads but only 2 flags, so the flag-count check fired first | gave it 3 flags, so the invariant's own thread-count check is what rejects it |
| `loadMutualExclusionPeterson_oneThread_throws` | the invariant's "two threads" requirement is now shadowed | retargeted at the state-level message, with a comment saying which layer now refuses |
| `CanonicalEncoderContractTest` R5 | asserted a 10-byte PetersonState; the count prefix makes it 14 | updated to 14 with the arithmetic in the message |

The first three are the failure mode worth naming: three tests that were really about *other* validations
had been relying, accidentally, on a state check that let them through first. Adding a thread fixes the
fixture; retargeting them at my new error would have quietly converted three validations into one.

### A coverage loss, stated rather than hidden

`loadMutualExclusionPeterson_oneThread_throws` was the only test asserting that the
`mutual_exclusion_peterson` invariant rejects a one-thread program. The state now rejects it first, so
that invariant's **lower** bound is shadowed for peterson. Its **upper** bound is still covered — that
is what the three-thread case does. Restoring the lower bound's direct coverage would mean testing the
invariant registry without going through a peterson state; noted, not done.

## What is still capped

**The state ceiling fell; the invariant ceiling did not.** `mutual_exclusion_peterson` names
`thread0_cs_pc` and `thread1_cs_pc` and rejects any thread count but two. So a three-thread peterson
program now loads and explores, and cannot be checked for mutual exclusion with the built-in peterson
invariant. `loadPeterson_threeThreads_succeeds` is deliberately written without one.

That is a real remaining limitation and it belongs to `InvariantRegistry`, not to this item. It is
recorded here rather than fixed, because widening an invariant to N threads means deciding what mutual
exclusion even means for the general case — a specification question, not a mechanical one.

## Verification

**Suite: 480 tests, 0 failures** (was 468; +12).

New coverage, in `StateRegistryTest`:

- three-thread peterson, deadlock and counter construct, and `counter`'s third register is reachable —
  which is only true if the array was sized from the thread count
- flag count below or above the thread count is rejected, both directions
- one thread is rejected for peterson and deadlock, and the constructors refuse it directly too
- a longer flag array encodes differently from a shorter one, and equal three-thread states still encode
  identically
- `deepCopy` does not alias the original's flag array at three threads

Plus `loadPeterson_threeThreads_succeeds` in `ProgramLoaderTest`: a three-thread peterson program loads
end to end, which was impossible before this spec.

## Mutation impact

None. `core`, `format` and `format.registry` are all outside the `state.* + cb.*` scope, so
`EXPECTED_TOTAL_MUTANTS` stays **268**.

This one does change how the stores behave — `PetersonState` and `DeadlockState` encodings are longer
and `counter` registers are sized from the program — so the corpus runs in the suite are the check that
exploration is unaffected, and they pass unchanged.