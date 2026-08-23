# Spec 07 — Bug Corpus, Reporting, and CLI

## TL;DR
Complete the model checker with a small concurrency bug corpus, benchmark
harness, and command-line interface. The final artifact is a deterministic
states-explored reduction table comparing all implemented strategies against
a common set of benchmarks.

## Acceptance Criteria
- `bugs/` contains at least one additional concurrency classic beyond Peterson
  (for example, a Dekker-style or producer/consumer bug).
- `BenchmarkHarness` runs all strategies (DFS, static POR, DPOR) against each
  bug program and records wall-clock time, peak memory, and states explored.
- `StatesExploredTable` prints a deterministic reduction table, for example:
  `NAIVE: 4,201,003 → HASHING: 31,004 → STATIC_POR: 4,203 → DPOR: 901`.
- `SoundnessAttestation` verifies that every strategy reports the same verdict
  (pass/fail) for every benchmark.
- `ReportWriter` produces machine-readable output (JSON or Markdown) containing
  the reduction table and soundness attestation.
- `Main` provides a CLI entry point that accepts a bug name and optional
  strategy flags.
- A passing `build` and `test` suite.

## Out of Scope
- GUI or web interface.
- Parallel / distributed execution.
- Dynamic benchmark scaling beyond the included corpus.

## Commands
```bash
./gradlew test --tests "*Bug*"
./gradlew run --args="broken-peterson"
```

## Map
- `src/main/java/dev/samhb/modelcheck/bugs/` — benchmark bug corpus
- `src/main/java/dev/samhb/modelcheck/report/` — reporting and benchmarking
- `src/main/java/dev/samhb/modelcheck/cli/` — CLI entry point
- `src/test/java/dev/samhb/modelcheck/report/BenchmarkHarnessTest.java` — Spec 07 tests
