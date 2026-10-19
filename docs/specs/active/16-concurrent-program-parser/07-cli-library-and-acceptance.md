# 16.07 — Public Integration and End-to-End Acceptance

## TL;DR

Expose source loading only after execution, reasons, reduction and replay contracts are complete.
This document owns public entry points and final release gates, not implementation of the feature
in the spec-authoring PR. [README](README.md) records status and document dependencies.

## Public interface

Add `SourceProgramLoader` alongside `ProgramLoader`, with:

```java
public BenchmarkProgram load(String source);
public BenchmarkProgram loadFromFile(Path path);
```

Use `<source>` for in-memory diagnostics. Calls are independent and reusable; a loader retains no
run-specific mutable state. File input validates bounded UTF-8 and reports RegistryException with
source locations/categories from 03. Preserve existing JSON `ProgramLoader.load`/file/resource APIs.
The compiled Program retains immutable instruction/source metadata needed by all standard runners.

Add `--source <path>` as another mutually exclusive program selector alongside named bug, `--file`
and `--all`. Preserve `--file` as JSON; do not sniff source by extension/content. `.ilv` is the
documented source extension, but explicit --source accepts any path. Existing `--json`, strategy,
store, bitstate and CBS options apply normally. Malformed/missing/multiple selectors use existing
CLI error/exit conventions. Expected verdict is optional and cannot convert INCOMPLETE to PASS.

Help/README document assignments and scheduling boundaries, source load errors, loop bounds and
actual/approximate guarantees. Reports include a program-kind marker for source-model result
aggregation while existing legacy constructors default to legacy behavior. This marker is immutable
program/report metadata, not part of state-store identity. Add neither a source-only runner nor
a generated-JSON CLI/export subsystem in v1.

## Required examples and independent oracles

Add teaching fixtures during implementation under `examples/programs/`, paired with documented
expected values/outcomes and source commands:

1. Split-read/write lost update from 01, and its atomic increment counterpart. With property
   removed, independently establish final values `{1,2}` versus `{2}`; then validate property result.
2. A branching shared flag with manually enumerated decision/body schedules, including a concurrent
   write between decision and body. Verify source line labels after skipping an arm.
3. Fixed repetition and a bounded while that exits early, exits exactly at N, or cuts off true at N.
   Include N=0 and nested per-invocation limits, using hand-calculated iteration counts.
4. An atomic nested-control-flow model that writes then cuts off, leaving the preceding committed
   configuration unchanged. A runtime-error variant reports VIOLATION without partial commit.
5. Guarded producer/consumer behavior distinguishing waiting, genuine deadlock and loop cutoff.

Pair compatible linear fixtures with hand-authored declarative JSON; use independent Java fixture
steps or explicit transition tables for control flow. Do not treat compiler-generated fixtures as
independent evidence. Check snapshots, minimization and source-aware replay, not verdict alone.

## Release acceptance matrix

| Layer | Required evidence |
|---|---|
| Parser/compiler | Grammar, diagnostics, all inclusive caps, checked expansion and target validity |
| Execution | Same behavior through search/driver/replay, atomic boundaries/rollback, reusable definitions |
| Exact DFS | Hand-enumerated states, outcomes, loop cutoff witnesses and final values |
| Static POR/DPOR | 05's reviewed argument, terminal existence parity and selected genuine reductions |
| CBS | Matching-bound expectations, correct condition preemptions and reason-specific deepening |
| Bitstate | Approximate source labels, retained real failures/cutoffs, no exact absence-of-bug claim |
| Reporting | Every strategy recognizes incomplete, multiple causes retained, expectation handling |
| Traces/browser | Source labels after branches, escaped JSON, snapshots, metadata and legacy fallback |
| Compatibility | Existing corpus contracts, constructors, JSON flags, replay fixtures and mutation gates |

## Commands and completion

Run from the repository root; keep local Gradle/PIT single-worker flags. Do not weaken CI parallelism.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest
npm test --prefix docs
```

The implementation adds scoped tests in the existing mirrored JUnit layout and extends existing
normalizer/Playwright suites. No new testing framework is needed. Verify process-level `--source`
success, syntax/load failures and conflicting selectors with the actual CLI harness. Use fresh
Java-produced reports in browser integration so source metadata is tested across the boundary.

Run appropriate expanded mutation checks for new compiler/execution/cutoff logic; the default
state/CBS PIT gate alone cannot establish source-language coverage. Record measured populations,
survivors and limitations rather than fabricating a target percentage or weakening the old gate.
Perform the selective CodeRabbit review and disposition valid findings before push. Review failure,
skip or quota exhaustion is reported honestly, not replaced by an ordinary review attestation.

Update this set's status, roadmap #9 and project context only to the achieved state. A spec-only PR
claims documentation validation, not successful implementation commands. Implemented status requires
the whole matrix, green CI and final review evidence. Item #8 and the unrelated roadmap candidates
remain outside implementation scope.

## Implementation evidence

`MainTest` runs real CLI processes for `--source`, invalid input and conflicting selectors. `SourceAcceptanceTest` loads all seven teaching fixtures from disk and checks reports/attestation across strategies/stores. The viewer CI job generates its source trace with Java before running Playwright; CI Gradle parallelism remains unchanged. See [set evidence](README.md#implementation-evidence) for measured checks and outstanding review/CI gates.
