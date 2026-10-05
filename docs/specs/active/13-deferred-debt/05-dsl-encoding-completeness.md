# 05 — DSL encoding completeness

**Status:** implemented
**Closes:** A2 (`12-mutation-hardening/DEFERRED.md`)
**Mutation scope:** none directly — `DynamicState` is in `format.dsl`, outside the `state.* + cb.*`
scope. But `CanonicalEncoder.encode` is `state.*` and *calls* `encodeTo`, so the change alters what
every store key looks like. See [Mutation impact](#mutation-impact).

## The gap

`DynamicState.equals` compared `decl` and `threadCount`. `DynamicState.encodeTo` wrote neither. So two
states differing only in their declaration, or only in their thread count, compared **unequal** yet
**encoded identically**.

That direction is the dangerous one. The encoding *is* the visited identity — `CanonicalEncoder.encode`
returns `state.encodeTo(out)` bytes and `HashingStateStore` keys on them — so a collision makes the
store answer "already visited" for a configuration it has never seen, and a reachable violation is
pruned from the search. Nothing crashes. Nothing reports an error.

Unreachable today because a store never spans two programs: `InterleaveRunner` builds a fresh store per
run and `BenchmarkHarness` gets one per strategy per program. The masking reason is *structural*, not
incidental, which is the register's point — it will not surface on its own, and when it does it will
present as a search quietly missing states rather than as an encoding bug.

## What was written

Ahead of the values, `encodeTo` now writes the thread count, then the whole declaration:

- `threadCount`
- field count, then per field: name (`writeUTF`), type ordinal, `intInit`, `boolInit`, and `arrayInit`
  as a length prefix plus elements (`-1` for the null case, since that is what a non-array field holds)
- local count, then per local: name, type ordinal, `intInit`, `boolInit`

Every one of those is a component `equals` compares. `FieldDecl` is a record with a custom `equals`
covering all five of its components and a `hashCode` that hashes all five consistently, so the
declaration is encoded at exactly the granularity at which it is compared — no finer, no coarser.

**Names are written, not just structure.** R3a demonstrated the original gap with two declarations
differing only in a field's *name*, so a structure-only encoding (counts and type ordinals) would have
left the gap half closed. Local names are covered for the same reason.

**Writing nothing finer than `equals`** matters as much as writing enough. Two states that are `equals`
must encode identically or the store treats one as unvisited and re-explores it — wasteful, and it
would make `equalStates_encodeIdentically` fail. `dynamicStateDeclAndThreadCount_reachTheEncoding`
asserts that direction explicitly, so the fix cannot be satisfied by over-separating.

## The sentinel, and what inverting it cost

`StateEncodingFidelityTest` tracked this gap three ways, and all three had to move:

1. **`TRACKED_GAPS`** held `DynamicState → [decl, threadCount]`. Now empty. The map is kept rather than
   deleted, so a future omission has somewhere to be recorded and "nothing is known missing" stays an
   explicit, checkable statement instead of an absence.
2. **`everyStateField_hasAnEncodingCase`** (R3) sweeps every instance field of every discovered
   `SharedState` and demands a parity case or a tracked-gap entry. Closing the gap without moving
   `decl` and `threadCount` into `coveredFieldsByClass` would have failed it — the sweep is
   reflective, so it would have caught the omission.
3. **`trackedGaps_areStillRealGaps`** (R3a) asserted the *opposite* of what is now true: that
   differently-named declarations encode identically. It existed precisely so the allowlist could not
   outlive its reason, and it failed the day the fix landed. Replaced by
   `dynamicStateDeclAndThreadCount_reachTheEncoding`, which asserts closure across eight dimensions —
   field name, field type, field init, array init, local name, local count, thread count, and the
   equal-states converse.

## R2e lost its teeth, and that was measured rather than assumed

`encodeDynamicState_arrayLengthPrefixesSeparateAdjacentArrays` (R2e) was the only probe that could
catch a dropped **value-side** array length prefix, and it was built to: two declarations differing in
array length collide across a field boundary without the prefix, and its Javadoc recorded a 2026-10-01
experiment demonstrating it.

**It can no longer detect that mutation.** Verified by deleting `out.writeInt(arr.length)` from the
value loop and re-running: R2e still passes, because the two declarations already differ in the
`arrayInit` the declaration now encodes. The probe is satisfied by a different part of the encoding
than the one it interrogates.

This is not a hole in the encoding — it is the gap closing one level up. Array lengths are now carried
twice, so the value prefix cannot be load-bearing for injectivity. And there is no longer any probe
that *could* cover it: with the declaration written, two states sharing a declaration cannot differ in
array length, which is the only way a dropped value prefix could collide.

So the prefix is **kept as defence in depth, not necessity**, and both the Javadoc claim that it "cannot
be dropped" and R2e's claim that it is "the only probe that can catch" a dropped prefix are now false
and were corrected. What R2e now checks is the observable property — distinct lengths must not share an
encoding — rather than one means of achieving it.

The honest gap in the suite: a future change that stopped writing the declaration would restore the old
failure mode, and R2e would go quiet with it. `dynamicStateDeclAndThreadCount_reachTheEncoding` is what
would catch that, which is why it exists as a test rather than only as prose.

## Verification

**Suite: 468 tests, 0 failures** (unchanged count; R3a was inverted in place rather than added to).

No new test file. The gap already had the strongest possible instrumentation — a reflective sweep, a
direct assertion, and a deliberate sentinel — and closing it meant redirecting that instrumentation,
which is a stronger outcome than a fourth test would have been.

`javadoc` clean.

## Mutation impact

None expected: `DynamicState` is in `format.dsl`, outside `state.* + cb.*`, so no new mutants are
generated and `EXPECTED_TOTAL_MUTANTS` should stay **268**.

But this is an inference about *population*, not about *behaviour*, and the two come apart here: the
encoding feeds `CanonicalEncoder` and therefore every `HashingStateStore` key, so `CanonicalEncoder`'s
7 existing mutants are exercised differently even though none were added. Per the repository rule
against carrying numbers forward, a full-scope run is taken at set exit rather than asserted here.