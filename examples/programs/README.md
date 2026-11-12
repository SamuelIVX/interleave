# Example Programs

Curated JSON programs for teaching and verification. Load via `ProgramLoader` or CLI `--file`.

| File | Format | Invariant | Description |
|------|--------|-----------|-------------|
| `lost-update.json` | typed `counter` | `counter_equals(expected=2)` | Two threads read-then-write a shared counter without synchronization — classic lost-update race teaches atomicity violation. |
| `bounded-buffer-declarative.json` | declarative `buf[3], head, tail, count, capacity` | `count >= 0 && count <= capacity && count <= 3 && head >= 0 && head < 3 && tail >= 0 && tail < 3` | Producer/consumer over a capacity-3 ring buffer with guarded `count < capacity`/`count > 0` steps — teaches bounded-buffer safety and circular index correctness. |
| `semaphore-declarative.json` | declarative `permits` | `permits >= 0` | Binary semaphore with `acquire` (`permits > 0` guard, `permits--`) and `release` (`permits++`) — teaches mutual exclusion via permits. |

## Certified declarative symmetry

The semaphore supports `--file examples/programs/semaphore-declarative.json --symmetry --store exact`.
Omitting --strategy runs DFS/static POR/DPOR/CBS. Exact DFS visits 5 orbit representatives rather
than 8 concrete positions. Certification requires matching ordered instructions and initial locals;
loader provenance and existing work limits also apply to reassembled models. Typed JSON/custom
callbacks and declarative bitstate symmetry remain unsupported in this phase. JSON retains its
ordinary guards, sequential effects and error outcomes; source-only witness APIs remain separate.
See [Spec 92](../../docs/specs/active/92-certified-declarative-symmetry.md).

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
| `cutoff-simplification.ilv` | DFS first records four noise actions before a loop cutoff; dedicated simplification removes all four while retaining the same bound, INCOMPLETE. |
| `statement-assertions.ilv` | Scheduled and private assertions all hold; PASS. |
| `assertion-rollback.ilv` | A private assertion sees tentative 7 and fails; rollback leaves counter 0 and the global property true, VIOLATION. |
| `guarded-nested-handoff.ilv` | False inner guard rolls back tentative writes; ready writer runs first, then one commit changes counter 0 to 3, PASS. |
| `nested-atomic.ilv` | Nested load/store grouping shares one outer action per owner: counter 2, PASS. |
| `helper-values.ilv` | Two by-value calls add 2 then 3; six actions retain counter/result 5 and reset parameter slots, PASS. |
| `helper-increment.ilv` | Two calls to one atomic increment helper retain final value 2, PASS. |
| `helper-split-increment.ilv` | Caller-local load/store helper permits final values 1 and 2, VIOLATION. |
| `branching-flag.ilv` | Decision before producer selects value 2, even if producer runs before the body; producer before decision selects 1. PASS. |
| `bounded-loops.ilv` | First while increments twice, its final test exits; second invocation exits immediately. False zero-limit loop exits; true zero-limit loop yields INCOMPLETE with counter 2. |
| `atomic-cutoff.ilv` | A true final private loop test cuts off; none of the tentative writes commit. INCOMPLETE, counter 0. |
| `atomic-runtime-error.ilv` | Modulo by zero aborts the whole atomic block. VIOLATION with unchanged counter 0; the always predicate itself holds. |
| `guarded-handoff.ilv` | Consumer waits for producer, then commits value 8 and clears ready. PASS. Removing producer's action leaves a genuine DEADLOCK, unlike loop cutoff. |

Helpers are scheduled sequences declared before threads; calls may reference earlier helpers.
Optional scalar parameters capture entry values and one final return writes a scalar caller target.
Parameterized calls add entry/exit actions and consume formal slots within the eight-local cap;
legacy zero-argument void calls retain body-only actions. Their bodies bind locals/tid to the caller.
All expanded work limits remain. Calls inside atomic interiors, forward references and recursion
are rejected. See `helper-values.ilv` and [Spec 83](../../docs/specs/active/83-bounded-helper-values.md).

Unguarded nested atomic blocks share the outer action: inner closing braces do not commit writes
or allow interleaving. A reached false inner guard disables the entire outer action and rolls back
tentative writes. See `nested-atomic.ilv` and `guarded-nested-handoff.ilv`.

A bound allows N bodies and then tests once more: true means unexplored behavior, so INCOMPLETE.
Exact-store PASS proves the selected finite model; bitstate absence is APPROXIMATE_PASS. These files
model chosen behavior and do not parse or execute Java projects.

The cutoff example can be shortened with `--source examples/programs/cutoff-simplification.ilv
--store exact --strategy DFS --json --simplify-cutoff` (one command line). Output is one existing-
format trace with fresh snapshots, ready for the viewer. A shorter witness explains the same
omitted action; it does not establish anything about unexplored behavior.

`guarded-nested-handoff.ilv` demonstrates whole-action disablement: a false inner guard rolls back
prior tentative writes. The ready writer runs first, then the entire waiting outer action commits.
The always property sees only counter 0 or 3. Guard failures/cutoffs reached before a false guard
remain observable; see [Spec 82](../../docs/specs/active/82-guarded-nested-atomics.md).
