# 09 — Canonical configuration value keys (B1/B2)

## TL;DR

Give `CanonicalEncoder` one shared configuration-key interface and migrate explorer result maps,
the exact store, and keying test helpers to it. Close B1 with an explicit diagnostic-only
`SharedState.toString()` contract. Preserve CBS's scheduling identity and the existing store's
preemption dominance rule. This follows 13.03's partial mitigation; it does not implement 13.08/A4.

**Status:** implemented; PR review/CI pending.
**Closes:** B1 and B2 in [the deferred register](../12-mutation-hardening/DEFERRED.md).
**Baseline:** merged PR #42, `main` at `7cdc1ef`. Measure tests/PIT and corpus results before changes.

## Interface and identity

- `CanonicalEncoder.configurationKey(Configuration)` returns an immutable string covering the
  concrete state type, its canonical encoding, and the ordered program counters.
- `CanonicalEncoder.configurationKey(Configuration, int lastThreadId)` additionally distinguishes
  the last scheduled thread, including the initial sentinel `-1`.
- Keys are opaque, per-program search-position identifiers. They are computed afresh: a previously
  returned key is a snapshot, while a later call sees any mutation to the held shared state.
- Preemption count is a cost, not identity. It stays outside the scheduling key; the store continues
  to retain the minimum cost and consider a query visited only when its cost is dominated.
- Program definitions, derived liveness flags, last outcome, and unused configuration lock/wait
  metadata remain outside the key, preserving the existing stores' identity footprint. Cross-program
  reuse is unsupported; each run owns a fresh/cleared store.
- The state type prevents two different `SharedState` implementations with identical byte payloads
  from merging. Equal values of the same type and the same counters must produce equal keys, even
  when object references or diagnostic renderings differ.
- `Configuration.equals/hashCode` keep their existing identity semantics. Introducing value equality
  on an object holding mutable state would create unsafe collection keys.

The implementation reuses `CanonicalEncoder.encode`, not `SharedState.toString()`. String equality
compares the full key, including collisions in any hash prefilter. The Base64 and counter formatting
are implementation details; consumers must not parse or persist the key as a versioned wire format.

## Requirements and tests

| Requirement | Verification through public interfaces |
|---|---|
| Same semantic position has one key | equal `DclState` values with different object references/renderings |
| State/counter differences remain distinct | single-field changes and reordered/per-thread counters |
| Distinct state types stay distinct | two small test state types with the same payload |
| A key is a snapshot | mutate held shared state after inserting the earlier string into a map |
| CBS retains scheduler identity | last-thread `-1`, `0`, and `1`; same position under different last threads |
| Budget stays a dominance relation | existing store preemption tests plus shared-key integration |
| Diagnostic text never defines identity | constant-rendering state with several distinct reachable values |
| All result-map sites use the same definition | DFS, static POR, both DPOR branches, CBS, exact store |
| Corpus behavior stays stable | before/after events, distinct result states, traces, and canonical visited membership for every corpus program × explorer × exact/bitstate × invariant mode |
| Encoder injectivity test remains independent | retain its value-equality store and visitor sampling; only its key-under-test helper moves to the public interface |

Write failing tests before each behavior change. Do not replace independent expected identities with
the new key inside an oracle: that would let a lossy key erase its own evidence.

## Scope and mutation accounting

No dependencies, reduction changes, equality rewrite, new scheduling fields, or changes to the
bitstate hashing algorithm. B1 uses the contract option; diagnostic renderings remain free to change.

`state.*` and `cb.*` are mutated by PIT, so adding key methods and replacing the exact store's private
derivations may change the population. Record baseline versus final mutants, compare survivor
identities, and update `EXPECTED_TOTAL_MUTANTS`/`EXPECTED_PER_CLASS` only from the measured report.
Keep the 94% gate unless the measured population requires a separately justified change.
Re-adjudicate any line-based known-survivor entry moved by the edit; retain the equivalent `flush()`
mutant in the denominator rather than filtering it out.

## Verification

Verified locally against fresh `main` at `7cdc1ef`:

- Baseline: 482 tests; full PIT **255/268**, 13 survivors, no uncovered/error/timeout statuses.
- Final: **496 tests**, build and Javadoc pass; full PIT **255/268 (95.15%)**, same 13 survivors.
- Regressions failed before fixes: DFS/POR maps retained 8 of 10 independently observed values;
  CBS retained 13 of 14 scheduling positions; both exact-store paths merged different state types.
  Restoring the old CBS derivation also failed the real DCL revisit test: 30 entries for 25 positions.
- All **128** corpus runs (8 programs × 4 explorers × 2 stores × 2 invariant modes) preserve visit
  events, canonical visitor membership, and full outcome/thread/outcome traces. Four CBS DCL result
  maps correctly shrink: **30 → 25** without an invariant and **29 → 24** with one, for both stores.
  Events stay **32** and **31** respectively; only duplicate bookkeeping entries disappear.
- The encoder's scoped census is **18/19**, matching the full run. Its only survivor remains
  `CanonicalEncoder.encode`'s `flush()` removal. The unchanged encode implementation and measured
  mutant identify the same 12.01 R5 equivalence; its source location moves from 16 to 26, so the
  machine-readable survivor entry is updated. It remains in the denominator.

| Class | Baseline kills/total | Final kills/total | Change |
|---|---|---|---|
| `CanonicalEncoder` | 6/7 | **18/19** | 12 new key-method mutants, all killed |
| `HashingStateStore` | 52/61 | **43/52** | 13 killed mutants removed; 4 delegation mutants added/killed |
| `ContextBoundedExplorer` | 103/103 | **100/100** | 4 killed concatenation mutants removed; 1 delegation mutant added/killed |
| `BitstateStore` | 94/97 | **94/97** | unchanged |

The removed store mutants belong to private `encode` (7), `preemptionKey` (2), and their four
call sites (4). The shared derivation replaces those four calls. CBS replaces state/counter/string
conversion calls with one shared-key call. Comparing class, method signature, mutator, description,
and multiplicity (ignoring shifted source locations) yields **19 killed removals and 19 killed
additions** overall, with no changed survivor identities. Net population and kill count remain fixed;
this is consolidation accounting, not 12 new net kills. The 94% gate and total expectation stay intact;
per-class expectations and the current spec-12 table follow the measured census.

Local CodeRabbit review and remote CI are recorded in the PR. A4 and E5 remain open.
