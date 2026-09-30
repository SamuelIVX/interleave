# Spec 11.01 — StateStore Preemption-Aware Extension

## TL;DR
Extend the `StateStore` interface with preemption-aware `isVisited` and `markVisited` methods to support context-bounded search. Legacy implementations fail fast unless they override these methods. `HashingStateStore` uses an O(1) minimum-preemption-count lookup (no full-set scan). `BitstateStore` gains an explicit `maxPreemptions` capacity that callers must size to the explorer's bound. All existing explorers (DFS, Static POR, DPOR) continue to work unchanged — they only call the single-arg forms.

## Current State
- `StateStore` interface [verified] has `isVisited(Configuration)`, `markVisited(Configuration)`, `clear()`, `freshCopy()`.
- `HashingStateStore` [verified, `state/HashingStateStore.java:14-77`] uses a **two-level scheme**: `Set<Integer> visitedHashes` as a fast-reject prefilter, then `Set<String> visitedStates` holding the exact `state|pcs` encoding. This prefilter must be preserved, not replaced.
- `BitstateStore` [verified, `state/BitstateStore.java:20-189`] uses a single `BitSet` with `k` double-hashed indices. It has **no** `maxPreemptions` concept today.
- `DfsExplorer`, `StaticPorExplorer`, `DporExplorer` [verified] call single-arg forms only.

## Invariants
- **Backward compatibility:** Legacy stores that don't override the new methods fail fast with `UnsupportedOperationException`. This prevents silent incorrect pruning. Only `HashingStateStore` and `BitstateStore` implement preemption-aware storage.
- **Cost-aware pruning semantics:** A state visited at preemption count P **does not** block exploration at P' < P (more budget remaining). State at P=1 **does** block P=2 (already explored with more budget).
- **Lookup is O(1) amortized, never O(V).** `isVisited` must not scan the visited set. See §2 for the equivalence argument.
- **Key format:** `(config, lastThreadId)` — the preemption count lives in the **map value**, not the key. Implemented as compound key `state|pcs|lastThreadId` in the exact store; separate bit arrays per preemption level in the bitstate store (`lastThreadId` folded into the hash).
- **Bitstate capacity is a hard bound, not a hint.** No code path may index `bitArrays[p]` for `p > maxPreemptions`. A store constructed with capacity `C` is only usable with an explorer bound `K ≤ C`.

## Acceptance Criteria

### 1. StateStore Interface Extension
Add to `search/StateStore.java`:
```java
// NEW: Preemption-aware overloads fail fast unless overridden
// Legacy stores that don't implement preemption-aware storage will throw UnsupportedOperationException
default boolean isVisited(Configuration config, int lastThreadId, int preemptions) {
    throw new UnsupportedOperationException("Legacy StateStore does not support preemption-aware visited checks. Use HashingStateStore or BitstateStore for CBS.");
}
default void markVisited(Configuration config, int lastThreadId, int preemptions) {
    throw new UnsupportedOperationException("Legacy StateStore does not support preemption-aware visited checks. Use HashingStateStore or BitstateStore for CBS.");
}
```

**Note:** The new overloads **fail fast** with `UnsupportedOperationException` for legacy stores that don't implement preemption-aware storage. This prevents silent incorrect pruning. Only `HashingStateStore` and `BitstateStore` implement these methods.

### 1b. StateVisitor Interface Extension
Add to `search/StateVisitor.java`:
```java
// NEW: Preemption-aware callback for CBS
default void onStateVisited(Configuration config, int lastThreadId, int preemptions) {
    onStateVisited(config); // delegate to existing
}
```

The delegation is load-bearing, not incidental: `InterleaveRunner.createLimitEnforcingVisitor` (`InterleaveRunner.java:91-116`) overrides only the single-arg `onStateVisited`. Because the 3-arg default forwards to it, `maxStates` / `maxTime` limit enforcement continues to work for CBS without any change to that visitor. Add a test asserting a CBS run with a limit-enforcing visitor still trips the limit.

### 2. HashingStateStore Implementation

**Design: store the minimum preemption count per `(config, lastThreadId)`.**

The naive alternative — scan all stored entries for one matching the config with `q ≤ p` — is O(V) per lookup and is O(V²) across a search that mostly misses. It would also discard the existing `visitedHashes` prefilter (`HashingStateStore.java:29-36`), which exists precisely to make the common "never seen" case O(1). Do not do that.

Instead, note that a state is visited at query bound `p` **iff** some recorded preemption count `q` satisfies `q ≤ p`, which holds **iff** `min(recorded q) ≤ p`. So the whole set of recorded counts collapses to its minimum:

```java
// key excludes the preemption count; the value is the minimum budget seen
private final Map<String, Integer> minPreemptions = new HashMap<>();

private String key(Configuration config, int lastThreadId) {
    return encode(config) + "|" + lastThreadId;   // encode() = state|pcs, as today
}

// Composite hash: canonical state + program counters + lastThreadId
private int hashCode(Configuration config, int lastThreadId) {
    int result = encoder.hashCode(config.state());
    result = 31 * result + config.programCounters().hashCode();
    return 31 * result + lastThreadId;
}

@Override
public boolean isVisited(Configuration config, int lastThreadId, int p) {
    // Prefilter first: a (config, lastThreadId) pair never seen costs one int
    // Set lookup and never pays for the string key below.
    if (!visitedHashes.contains(hashCode(config, lastThreadId))) {
        return false;
    }
    Integer min = minPreemptions.get(key(config, lastThreadId));
    return min != null && min <= p;
}

@Override
public void markVisited(Configuration config, int lastThreadId, int p) {
    visitedHashes.add(hashCode(config, lastThreadId));
    minPreemptions.merge(key(config, lastThreadId), p, Math::min);
}
```

- **The prefilter is load-bearing, not an optimization to trim.** `key()` calls `encode()`, which
  Base64-encodes the whole canonical state. The single-argument `isVisited` already avoids paying that on
  the overwhelmingly common miss via `visitedHashes` (`HashingStateStore.java:29-36`). The
  preemption-aware overload must keep the same shape: hash-check first, build the string key only when the
  prefilter hits. Without it the CBS hot path allocates a Base64 string per call, which is the single
  largest avoidable cost in the explorer.
- The existing single-argument `isVisited` / `markVisited` keep using the 2-component hash
  (`hashCode(config)`, no `lastThreadId`) so the prefilter and the map entry stay consistent for both key
  shapes. Do **not** unify them: a 3-component hash in the 1-arg path would invalidate its prefilter
  against entries written before the change.
- `clear()` → `visitedHashes.clear(); minPreemptions.clear();`
- `freshCopy()` → `new HashingStateStore()`
- `size()` → now reports the number of distinct `(config, lastThreadId)` pairs, not the number of distinct configurations. Update the Javadoc to say so; the 1-arg `isVisited` path is unaffected because it uses its own key.
- **Hash collisions:** the prefilter may report a false hit, which only costs a wasted `key()` build and
  an exact `Map` lookup. The prefilter can never produce a false *miss* for a marked pair, so the exact
  map remains the sole source of truth and `isVisited` stays exact (no false positives, as
  `HashingStateStore`'s class Javadoc promises).

**Equivalence argument to preserve in review:** the min-count map must agree with a reference `Set<(config, lastThreadId, P)>` implementation. A randomized property test over (config, lastThreadId, P) sequences is required — see Tests.

### 3. BitstateStore Implementation

**Constructors and capacity accessor:**
```java
private final BitSet[] bitArrays;      // length = maxPreemptions + 1
private final int maxPreemptions;

public BitstateStore(int size) { this(size, 4, 2); }

public BitstateStore(int size, int numHashFunctions) { this(size, numHashFunctions, 2); }

public BitstateStore(int size, int numHashFunctions, int maxPreemptions) {
    // ... existing size/k validation ...
    if (maxPreemptions < 0) {
        throw new IllegalArgumentException("maxPreemptions must be >= 0");
    }
    this.maxPreemptions = maxPreemptions;
    this.bitArrays = new BitSet[maxPreemptions + 1];
    for (int q = 0; q <= maxPreemptions; q++) bitArrays[q] = new BitSet(size);
}

public int maxPreemptions() { return maxPreemptions; }
```

- The two existing constructors keep their signatures and default to `maxPreemptions = 2`, so no existing call site or test changes.
- `freshCopy()` **must** propagate `maxPreemptions`: `new BitstateStore(size, numHashFunctions, maxPreemptions)`. Iterative deepening (Spec 11.05) reaches this path via the `stateStoreFactory` when the caller used `.stateStore(BitstateStore)`, and a store that silently reset capacity to 2 would under-report states explored for K > 2 — then trip the §4 capacity assertion.

**Lookup / mark** (CHESS-style cost-aware Bloom filter — probe every level `q ≤ p`):
```java
@Override
public boolean isVisited(Configuration config, int lastThreadId, int p) {
    int[] idx = hashIndices(config, lastThreadId);
    for (int q = 0; q <= p; q++) {
        boolean allSet = true;
        for (int i : idx) {
            if (!bitArrays[q].get(i)) { allSet = false; break; }
        }
        if (allSet) return true;
    }
    return false;
}

@Override
public void markVisited(Configuration config, int lastThreadId, int p) {
    for (int i : hashIndices(config, lastThreadId)) bitArrays[p].set(i);
}
```

- `hashIndices` folds `lastThreadId` into the primary hash alongside the canonical state and program counters, exactly as the single-arg version does today.
- **Cost:** `isVisited` is O((p+1)·k) rather than O(k). For K=2 that is a 3× multiplier on the dominant store operation. This is inherent to CHESS-style cost-aware bitstate and is called out here so the benchmark (Spec 11.04) treats bitstate CBS state counts as approximate in *time* as well as in *result*.
- Memory: O((K+1)×m) — for K=2, ~3× standard bitstate memory.

**Warning** (emitted by the CLI, Spec 11.03, not here): "Context bound >5 may increase bitstate memory significantly (O((K+1)×m))."

### 4. Capacity Contract (Explorer ⇄ Store)

`BitstateStore` allocates `bitArrays[maxPreemptions + 1]`, but `ContextBoundedExplorer.explore(..., int maxPreemptions)` (Spec 11.02) takes an independent bound. With a default-capacity store (2) and an explorer bound of K=3, a state at P=3 indexes `bitArrays[3]` and throws `ArrayIndexOutOfBoundsException`.

**Contract, enforced in two places:**

1. **Construction.** Every producer of a bitstate store for CBS must size it to the bound it will be used at. `BenchmarkHarness` must construct
   `new BitstateStore(bitstateSize, bitstateK, maxPreemptions)` — see Spec 11.04 §"runProgramWithStore".
2. **Exploration-time assertion.** `ContextBoundedExplorer.explore(...)` must, before searching, check
   `stateStore.maxPreemptions() >= maxPreemptions` and throw:
   ```java
   throw new IllegalArgumentException(
       "State store capacity " + stateStore.maxPreemptions()
       + " is below requested maxPreemptions " + maxPreemptions
       + "; construct the store as new BitstateStore(size, k, maxPreemptions)");
   ```

**The explorer must never clamp.** Silently reducing the effective bound to the store's capacity would return a verdict that looks exhaustive-at-K but is actually exhaustive-at-C, which is exactly the class of silent unsoundness this spec set exists to prevent. A wiring bug should be a loud exception, not a quietly weaker search.

### 5. `freshCopy()` and Per-K Store Requirements

`StateStore.freshCopy()` [verified, `search/StateStore.java:42-44`] is a `default` method that **throws
`UnsupportedOperationException`**. It is a convenience, not a guarantee: `HashingStateStore` and
`BitstateStore` both implement it, but a third-party store need not.

Iterative deepening (Spec 11.05 §7) needs a store that is **empty and independent for every bound K**.
This is a soundness requirement, not an isolation nicety, and it has three parts.

**1. Each K must get a genuinely empty store, not merely a distinct object.**

Reusing one store across iterations does not just under-count; it makes deepening *narrow* instead of
wider. Because `isVisited` is `minPreemptions[key] != null && minPreemptions[key] <= p`:

- iteration K=0 marks `(config, t) → 0`
- iteration K=1 queries `isVisited(config, t, 1)` → `min = 0 ≤ 1` → **pruned**

So every state K=0 explored would be pruned at K=1, and each higher K would explore a strict subset of the
one before it. The search would report `PASS` at K=5 having covered less of the state space than K=0 did —
and a `BitstateStore` would additionally carry K=0's marks as false positives. This is exactly the silent
unsoundness this spec exists to prevent, so it must be **rejected, not tolerated**:

```java
// in runIterativeDeepening, per iteration
StateStore freshStore = stateStoreFactory.get();
if (!seenStores.add(freshStore)) {   // Set backed by IdentityHashMap
    throw new IllegalStateException(
        "Iterative deepening requires a fresh StateStore per bound K, but the configured "
        + "stateStoreFactory reused an instance. Iterations would share a visited set and each "
        + "deeper bound would prune states the previous bound already explored. "
        + "Use stateStoreFactory(...) with a supplier that returns a new store per call.");
}
```

The check must test membership of a set of **every store already used**, not just the previous one.
Comparing against `previousStore` alone catches a factory that returns one constant instance, but misses
an alternating `A, B, A, B …` — and iteration K=2 would then inherit K=0's visited set, reintroducing the
exact pruning this rule exists to prevent. A `Set` backed by `IdentityHashMap` (not `HashSet`, which would
use `equals`/`hashCode` and could merge two distinct stores that happen to compare equal) makes the
invariant exact: *no store instance is ever reused across bounds*.

Do not add an `isEmpty()` probe to the interface for this. The bound on memory is trivial —
`maxPreemptions + 1` references, single digits for realistic K — and the only failure that matters is
instance reuse.

**2. `freshCopy()` may throw, so callers must not depend on it.**

`Builder.stateStore(StateStore)` [verified, `InterleaveRunner.java:232-245`] installs a factory that tries
`freshCopy()` and **falls back to returning the shared instance** when it throws
`UnsupportedOperationException`. Combined with (1), that fallback is a hard error for iterative deepening:
it produces a factory that returns the same object every call. It remains correct for single-bound
strategies, so the fix belongs in `runIterativeDeepening` (Spec 11.06), which fails loudly, rather than in
`stateStore(...)`, which would break every existing caller.

For this reason both Spec 11.06 (library) and Spec 11.04 (harness) obtain each iteration's store from the
**factory**, never from `store.freshCopy()` — the factory is the caller's own isolation policy and already
encodes this decision.

**3. When `freshCopy()` IS used, capacity must carry over.**

For `BitstateStore`, a copy that reset `maxPreemptions` to the 2-argument default would under-report states
explored for K > 2 — and the §4 capacity assertion would then **throw** for any K above 2. Loud, not silent;
that is precisely what §4 is for, and it is why this case is safe to leave enforced rather than detected.

**No new interface method is required.** `BenchmarkHarness` already threads a
`Supplier<StateStore> storeFactory` through `runProgramWithStore` (`:123-125`), and the library path uses
`stateStoreFactory` for the same purpose.

**Both iterative-deepening loops must enforce requirement 1, not just the library one.** There are exactly
two, and they are separate code:

| Loop | Spec | Factory | Check |
|---|---|---|---|
| `BenchmarkHarness.runExplorerWithIterativeDeepening` | 11.04 §5 | `storeFactory` | required |
| `InterleaveRunner.runIterativeDeepening` | 11.06 §3 | `stateStoreFactory` | required |

Enforcing it in only one leaves the other silently narrowing, and the two failure modes differ in
reachability: the library path can be handed the shared-instance fallback through the public
`Builder.stateStore(...)` API, while the harness path is only reachable if its own per-store-type factory
wiring regresses. Both are cheap to check (`maxPreemptions + 1` insertions into an identity set) and both
produce plausible-looking wrong numbers when omitted, which is the failure mode worth spending the
allocation on.

**Cross-references:** Spec 11.04 (harness, per-K `storeFactory.get()`), Spec 11.06 (library,
`runIterativeDeepening`), Spec 11.05 §7 (iterative deepening behavior).

## Tests

**File:** `src/test/java/dev/samhb/interleave/state/HashingStateStorePreemptionTest.java`
- `isVisited_exactKey_withPreemptions()` — key includes `lastThreadId`
- `markVisited_thenIsVisited_samePreemption_returnsTrue()`
- `isVisited_differentPreemption_returnsFalse()` — P=2 doesn't block P=1
- `isVisited_higherPreemption_afterLower_returnsTrue()` — P=1 blocks P=2
- `isVisited_matchesReferenceSet_randomized()` — **property test.** For a seeded random sequence of
  `markVisited(config_i, lastThreadId_i, P_i)` calls, assert `isVisited(config_j, lastThreadId_j, p)`
  agrees with a reference `Set<(config, lastThreadId, P)>` implementation for all `(j, p)`. This is the
  guard that the min-count map is equivalent to the "probe q ≤ p" semantics it replaces.
- `isVisited_differentLastThreadId_notPruned()` — the `lastThreadId` dimension is not collapsed
- `isVisited_prefilterMiss_doesNotBuildExactKey()` — a pair never marked returns `false` via the hash
  prefilter alone. Pin this with a `key`/`encode` seam (package-private counter or a spy) rather than by
  timing: it is the guard against a refactor that "simplifies" the prefilter away and makes every CBS
  lookup allocate a Base64-encoded state string.
- `singleArgPath_prefilterStillConsistent()` — the 1-arg `isVisited` / `markVisited` keep using the
  2-component hash, so marking via one overload and probing via the other cannot desynchronize the
  prefilter
- `clear_clearsAllPreemptionLevels()`
- `freshCopy_preservesBehavior()`
- `size_reportsDistinctConfigLastThreadPairs()`

**File:** `src/test/java/dev/samhb/interleave/state/BitstateStorePreemptionTest.java`
- `constructor_maxPreemptions_allocatesArray()` — `bitArrays.length == maxPreemptions + 1`, all non-null
- `constructor_negativeMaxPreemptions_throws()`
- `isVisited_markVisited_separateArraysPerPreemption()`
- `freshCopy_preservesMaxPreemptions()` — **regression guard**; `freshCopy` losing capacity is silent
- `maxPreemptions_accessor_reportsConfiguredCapacity()`
- `memoryWarning_K5_noWarning_K6_warning()`

**File:** `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerCapacityTest.java`
- `explore_bitstateStoreCapacityBelowBound_throws()` — capacity 2, bound 3 ⇒ `IllegalArgumentException`
- `explore_bitstateStoreCapacityAtBound_succeeds()` — capacity 3, bound 3
- `explore_bitstateStoreCapacityZero_boundZero_succeeds()` — K=0 is legal
- `explore_hashStoreHasNoCapacityLimit_anyBound()` — `HashingStateStore` accepts any K (no capacity concept)

**File:** `src/test/java/dev/samhb/interleave/cb/ContextBoundedExplorerVisitorTest.java`
- `limitEnforcingVisitor_cbsRun_stillTripsMaxStates()` — a `StateVisitor` overriding only single-arg
  `onStateVisited` still enforces the limit under CBS (validates the §1b delegation)

**File:** `src/test/java/dev/samhb/interleave/cb/IterativeDeepeningStoreIsolationTest.java`
*(new file; covers §5 requirement 1 — the per-K store soundness rule)*
- `iterativeDeepening_sharedStoreFactory_throwsIllegalState()` — a factory returning one shared instance is
  rejected loudly, not tolerated
- `iterativeDeepening_alternatingStoreFactory_throwsIllegalState()` — a factory returning `A, B, A, B …` is
  also rejected; membership is tested over all stores used, not just the previous one
- `iterativeDeepening_sharedStore_wouldPrunePreviousK_states()` — pins the actual failure mode. Using a
  reference `Set`-backed store, assert that a single shared instance prunes at K=1 everything K=0 explored
  (the `min=0 ≤ 1` path). This documents *why* the identity check exists and fails if a future refactor
  drops it
- `iterativeDeepening_freshFactoryPerK_exploresWiderAtHigherK()` — the positive case: with a real fresh
  store per K, `statesExplored(K=2) >= statesExplored(K=1)`. Guards the property the identity check
  protects, so the check is not just enforced but demonstrably needed
- `iterativeDeepening_bitstateStoreFactory_capacityCarriesOver()` — a `BitstateStore` supplier sized for
  `maxPreemptions` satisfies §4 at every K rather than tripping the assertion after K=2

## Out of Scope
- Iterative deepening — Spec 11.05
- Explorer implementation — Spec 11.02
- CLI integration — Spec 11.03
- `BenchmarkHarness` wiring — Spec 11.04
- Verdict propagation (`INCOMPLETE`) — Spec 11.05
- Library API (`Strategy.CONTEXT_BOUNDED`) — Spec 11.06
- `TraceOutcome` / `VerificationResult` / `TestResult` switch-site updates — Spec 11.07

## Commands
```bash
./gradlew test --tests "*StateStore*"
./gradlew test --tests "*HashingStateStore*"
./gradlew test --tests "*BitstateStore*"
./gradlew test --tests "*ContextBoundedExplorerCapacity*"
```

## Map
- `src/main/java/dev/samhb/interleave/search/StateStore.java` — interface extension (fail-fast defaults)
- `src/main/java/dev/samhb/interleave/search/StateVisitor.java` — add `onStateVisited(config, lastThreadId, preemptions)` default
- `src/main/java/dev/samhb/interleave/state/HashingStateStore.java` — exact implementation (min-preemption map + preserved hash prefilter)
- `src/main/java/dev/samhb/interleave/state/BitstateStore.java` — bitstate implementation with `maxPreemptions` capacity + accessor
- `src/test/java/dev/samhb/interleave/state/` — preemption-aware store tests
- `src/test/java/dev/samhb/interleave/cb/` — capacity contract and visitor delegation tests
