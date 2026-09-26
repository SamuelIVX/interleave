# Example Programs

Curated JSON programs for teaching and verification. Load via `ProgramLoader` or CLI `--file`.

| File | Format | Invariant | Description |
|------|--------|-----------|-------------|
| `lost-update.json` | typed `counter` | `counter_equals(expected=2)` | Two threads read-then-write a shared counter without synchronization — classic lost-update race teaches atomicity violation. |
| `bounded-buffer-declarative.json` | declarative `buf[3], head, tail, count, capacity` | `count >= 0 && count <= capacity && count <= 3 && head >= 0 && head < 3 && tail >= 0 && tail < 3` | Producer/consumer over a capacity-3 ring buffer with guarded `count < capacity`/`count > 0` steps — teaches bounded-buffer safety and circular index correctness. |
| `semaphore-declarative.json` | declarative `permits` | `permits >= 0` | Binary semaphore with `acquire` (`permits > 0` guard, `permits--`) and `release` (`permits++`) — teaches mutual exclusion via permits. |
