# Spec 11 — StateStore Preemption-Aware Extension

## TL;DR
Extend the `StateStore` interface with preemption-aware `isVisited` and `markVisited` methods to support context-bounded search. Legacy implementations fail fast unless they override these methods. All existing explorers (DFS, Static POR, DPOR) continue to work unchanged — they only call the single-arg forms.

## Current State
- `StateStore` interface [verified] has `isVisited(Configuration)`, `markVisited(Configuration)`, `clear()`, `freshCopy()`.
- `HashingStateStore` [verified] uses `Set<String>` with key `state|pcs`.
- `BitstateStore` [verified] uses bloom filter with `k` hash functions.
- `DfsExplorer`, `StaticPorExplorer`, `DporExplorer` [verified] call single-arg forms only.

## Invariants
- **Backward compatibility:** Legacy stores that don't override the new methods fail fast with `UnsupportedOperationException`. This prevents silent incorrect pruning. Only `HashingStateStore` and `BitstateStore` implement preemption-aware storage.
- **Cost-aware pruning semantics:** A state visited at preemption count P **does not** block exploration at P' < P (more budget remaining). State at P=1 **does** block P=2 (already explored with more budget).
- **Key format:** `(config, lastThreadId, preemptions)` triple — implemented as compound key `state|pcs|lastThreadId|preemptions` in exact store; separate bit arrays per preemption level in bitstate store (lastThreadId combined into hash).

## Acceptance Criteria

### 1. StateStore Interface Extension
Add to `StateStore.java`:
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
Add to `StateVisitor.java`:
```java
// NEW: Preemption-aware callback for CBS
default void onStateVisited(Configuration config, int lastThreadId, int preemptions) {
    onStateVisited(config); // delegate to existing
}
```

### 2. HashingStateStore Implementation
- Internal key: `config.state().toString() + "|" + config.programCounters() + "|" + lastThreadId + "|" + preemptions`
- `isVisited(config, lastThreadId, p)` → **check all stored entries with same config/lastThreadId and q ≤ p**: `visited.stream().anyMatch(k -> matchesConfigAndLastThread(k, config, lastThreadId) && extractPreemptions(k) <= p)`
- `markVisited(config, lastThreadId, p)` → `visited.add(key(config, lastThreadId, p))`
- `clear()` → `visited.clear()`
- `freshCopy()` → new `HashingStateStore()`

### 3. BitstateStore Implementation
- Constructor takes `maxPreemptions` parameter (default 2)
- Allocates `BitSet[] bitArrays = new BitSet[maxPreemptions + 1]` — **separate arrays per (lastThreadId, preemptions) or single array with combined index**
- For simplicity: combine `lastThreadId` into hash and check all bit arrays q ≤ p: `for (int q = 0; q <= p; q++) if (bitArrays[q].get(hashIndex(config, lastThreadId))) return true;`
- `markVisited(config, lastThreadId, p)` → `bitArrays[p].set(hashIndex(config, lastThreadId))`
- Memory: O((K+1)×m) — for K=2, ~3× standard bitstate memory
- Warning logged when K>5 with bitstate: "Context bound >5 may increase bitstate memory significantly (O((K+1)×m))."
- `freshCopy()` creates new instance with same `maxPreemptions`, `bitstateSize`, `bitstateK`

## Tests
**File:** `src/test/java/dev/samhb/interleave/state/HashingStateStorePreemptionTest.java`
- `isVisited_exactKey_withPreemptions()` — key includes preemption count
- `markVisited_thenIsVisited_samePreemption_returnsTrue()`
- `isVisited_differentPreemption_returnsFalse()` — P=2 doesn't block P=1
- `isVisited_higherPreemption_afterLower_returnsTrue()` — P=1 blocks P=2
- `clear_clearsAllPreemptionLevels()`
- `freshCopy_preservesBehavior()`

**File:** `src/test/java/dev/samhb/interleave/state/BitstateStorePreemptionTest.java`
- `constructor_maxPreemptions_allocatesArray()`
- `isVisited_markVisited_separateArraysPerPreemption()`
- `freshCopy_preservesMaxPreemptions()`
- `memoryWarning_K5_noWarning_K6_warning()`

## Out of Scope
- Iterative deepening (Spec 14)
- Explorer integration (Spec 12)
- CLI integration (Spec 13)
- INCOMPLETE verdict (Spec 14)

## Commands
```bash
./gradlew test --tests "*StateStore*"
./gradlew test --tests "*HashingStateStore*"
./gradlew test --tests "*BitstateStore*"
```

## Map
- `src/main/java/dev/samhb/interleave/search/StateStore.java` — interface extension (fail-fast defaults)
- `src/main/java/dev/samhb/interleave/search/StateVisitor.java` — add `onStateVisited(config, lastThreadId, preemptions)` default
- `src/main/java/dev/samhb/interleave/state/HashingStateStore.java` — exact implementation
- `src/main/java/dev/samhb/interleave/state/BitstateStore.java` — bitstate implementation with `maxPreemptions` constructor
- `src/test/java/dev/samhb/interleave/state/` — tests for preemption-aware methods