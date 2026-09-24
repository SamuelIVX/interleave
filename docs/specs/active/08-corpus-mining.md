# Spec 08 — Property-Based Corpus Mining

## TL;DR
Generate concurrent programs from curated templates instead of hand-writing them. A seeded, bounded generator produces reproducible `Program`s that reuse existing `Step`/`SharedState` types. The generator is the **fuzzing layer** for interleave: it finds edge cases, builds a regression corpus, and validates oracles without new explorer semantics.

## Acceptance Criteria
- `Template` interface defines `String id()`, `Program generate(Random rng, GeneratorConfig cfg)`.
- `TemplateRegistry` holds curated templates (at least `lost-update` and `counter-race` variants); unknown template id throws `IllegalArgumentException`.
- `GeneratorConfig` is validated: `seed` any long, `count` in `[1, 10000]`, `maxStepsPerThread` in `[1, 20]`, `threadCount` in `[1, 4]` if supplied. Invalid values throw `IllegalArgumentException`.
- `CorpusGenerator.generate(cfg)` produces exactly `count` programs, deterministically (same seed + config → byte-equal `Configuration` encodings), bounded (`stepsPerThread <= maxStepsPerThread`, `threadCount` respected).
- Oracle mitigation: `CorpusResult` records model-checker verdict via exact `DfsExplorer` and includes `statesExplored` for explosion control. Caller can set `maxStates` to cap exploration; programs exceeding it are labeled `TRUNCATED`.
- Persisted form: `CorpusEntry` (program + seed + verdict) serializes to JSON via existing `Gson` and deserializes losslessly (used for flakiness mitigation).
- CLI `generate` subcommand: `interleave generate --template lost-update --count 10 --seed 42 --max-steps 5` prints JSON array to stdout.
- A passing `build` and `test` suite.

## Template Shape (frozen for implementation)

### GeneratorConfig
| field | type | default | constraints |
|---|---|---|---|
| `templateId` | String | required | must exist in registry |
| `seed` | long | `42` | any |
| `count` | int | `10` | `1..10000` |
| `maxStepsPerThread` | int | `5` | `1..20` |
| `threadCount` | int | template default or `2` | `1..4` |
| `maxStates` | int | `10000` | `1..1000000` |

### Template contract
- Curated realism: only templates that mirror classic patterns (lost-update, counter-race, bounded-buffer, etc.) are registered. No arbitrary random step soup.
- Each `generate` draws `threadCount` (if not fixed) and per-thread step counts `<= maxStepsPerThread` from `rng`, builds `CounterState`-based steps (`ReadCounterStep`/`WriteCounterStep` and variants), returns a fresh `Program`.
- Templates are pure functions of `(rng, cfg)` — no global state — so seeding guarantees reproducibility.

### Mitigations for negatives (from Future Work analysis)
- **Oracle problem:** `CorpusGenerator` runs `DfsExplorer` (exact) as oracle and stores `expectedVerdict` (`VIOLATION`/`SAFE`/`TRUNCATED`). Consumers compare later runs against this oracle.
- **Explosion control:** `count` capped at 10k, `maxStepsPerThread` at 20, `maxStates` at 1M, `threadCount` at 4. `CorpusResult.truncated` signals when bound hit.
- **Flakiness:** `seed` is mandatory persisted field; `CorpusEntry` JSON round-trips losslessly; `CorpusGenerator` uses only `java.util.Random` (not thread-local) so replay is byte-deterministic.
- **Realism:** Only curated templates in `TemplateRegistry`; no generic “random steps” template.

## Out of Scope
- New `Step` type families (reuses existing `CounterState` steps).
- Web UI visualization (#6) — corpus JSON is the handoff.
- Parallel generation.

## Commands
```bash
./gradlew test --tests "*corpus*"
./gradlew run --args="generate --template lost-update --count 10 --seed 42"
```

## Map
- `src/main/java/dev/samhb/interleave/corpus/GeneratorConfig.java`
- `src/main/java/dev/samhb/interleave/corpus/CorpusTemplate.java`
- `src/main/java/dev/samhb/interleave/corpus/CorpusGenerator.java`
- `src/main/java/dev/samhb/interleave/corpus/CorpusEntry.java` + `CorpusResult.java`
- `src/main/java/dev/samhb/interleave/corpus/templates/LostUpdateTemplate.java`
- `src/main/java/dev/samhb/interleave/corpus/templates/CounterRaceTemplate.java`
- `src/main/java/dev/samhb/interleave/corpus/TemplateRegistry.java`
- `src/main/java/dev/samhb/interleave/cli/Main.java` — `generate` subcommand
- `src/test/java/dev/samhb/interleave/corpus/CorpusGeneratorTest.java`
- `src/test/java/dev/samhb/interleave/corpus/TemplateRegistryTest.java`
