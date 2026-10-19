# Example Programs

Curated JSON programs for teaching and verification. Load via `ProgramLoader` or CLI `--file`.

| File | Format | Invariant | Description |
|------|--------|-----------|-------------|
| `lost-update.json` | typed `counter` | `counter_equals(expected=2)` | Two threads read-then-write a shared counter without synchronization — classic lost-update race teaches atomicity violation. |
| `bounded-buffer-declarative.json` | declarative `buf[3], head, tail, count, capacity` | `count >= 0 && count <= capacity && count <= 3 && head >= 0 && head < 3 && tail >= 0 && tail < 3` | Producer/consumer over a capacity-3 ring buffer with guarded `count < capacity`/`count > 0` steps — teaches bounded-buffer safety and circular index correctness. |
| `semaphore-declarative.json` | declarative `permits` | `permits >= 0` | Binary semaphore with `acquire` (`permits > 0` guard, `permits--`) and `release` (`permits++`) — teaches mutual exclusion via permits. |

## Readable source models

Load `.ilv` files with `--source`; `--file` continues to mean JSON. All strategy and store options
apply. Each outer `atomic` is one scheduling action; an outer condition is a separate action.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 run --args="--source examples/programs/split-increment.ilv --json"
./gradlew --max-workers=1 -PpitestThreads=1 run --args="--source examples/programs/bounded-loops.ilv --json"
```

| Source fixture | Independent expected behavior |
|---|---|
| `split-increment.ilv` | Two reads before either write leave 1; serial increments leave 2. Final `counter == 2` can fail. Matches the hand-authored typed `lost-update.json` teaching case. |
| `atomic-increment.ilv` | Either ordering increments twice: final value 2, PASS. |
| `branching-flag.ilv` | Decision before producer selects value 2, even if producer runs before the body; producer before decision selects 1. PASS. |
| `bounded-loops.ilv` | First while increments twice, its final test exits; second invocation exits immediately. False zero-limit loop exits; true zero-limit loop yields INCOMPLETE with counter 2. |
| `atomic-cutoff.ilv` | A true final private loop test cuts off; none of the tentative writes commit. INCOMPLETE, counter 0. |
| `atomic-runtime-error.ilv` | Modulo by zero aborts the whole atomic block. VIOLATION with unchanged counter 0; the always predicate itself holds. |
| `guarded-handoff.ilv` | Consumer waits for producer, then commits value 8 and clears ready. PASS. Removing producer's action leaves a genuine DEADLOCK, unlike loop cutoff. |

A bound allows N bodies and then tests once more: true means unexplored behavior, so INCOMPLETE.
Exact-store PASS proves the selected finite model; bitstate absence is APPROXIMATE_PASS. These files
model chosen behavior and do not parse or execute Java projects.
