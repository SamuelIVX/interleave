# Search profiling: trace construction and DPOR sleep contexts

## Result and decision

Trace construction is material on this deliberately trace-heavy workload. Select a separate 9-V3
optimization design/implementation bundle for traversal-time metadata capture, including all affected
explorers, failure/cutoff evidence, replay compatibility and before/after regression measurements.
This report itself changes no production behavior and makes no predicted speedup claim.

The sleep-coverage lambda/access proxy is small on these three workloads. Defer a 9-V5
representation rewrite and retain the existing coverage/pruning semantics. This is a workload-limited
decision: the samples do not measure retained memory, peak antichain width or worst-case behavior.
Investigate broader explorer/independence costs separately if a real model demonstrates a need.

## Provenance and reproduction

Measured on main baseline `a9f2c0f4b9b032df5286ebb46343c9d3cbbf23c7` plus the uncommitted
Spec 68 profiling additions. Production classes are unchanged from that baseline; measured runner
and production class hashes are below. All three fresh JVM forks have identical class fingerprints.
The source worktree was dirty with these new files/task/spec; it was not a clean released revision.
No tests or mutation runs were launched concurrently with the profiling forks. Other host activity
was uncontrolled; results are local observations, not portable performance guarantees.

Host: Mac OS X aarch64; OpenJDK 64-Bit Server VM 26.0.2.1; 10 logical processors.
Each child JVM used a fixed 512 MiB initial/maximum heap and one foreground search thread.
Existing Gradle/PIT/CI defaults are unchanged. Sources compile before timing.

```sh
./gradlew --max-workers=1 -PpitestThreads=1 profileSearch -PprofileFork=1
./gradlew --max-workers=1 -PpitestThreads=1 profileSearch -PprofileFork=2
./gradlew --max-workers=1 -PpitestThreads=1 profileSearch -PprofileFork=3
```

Run sequentially. Each invocation writes `results.json` and nine JFR files under
`build/reports/search-profile/fork-N/`. JSON preserves every plain batch operation count, raw
nanoseconds, exact current-thread allocated bytes and sampled family totals. These local artifacts
are ignored build outputs; this Markdown report commits the per-fork summaries and sample counts.
Requested execution sampling period is 1 ms. Each case warms for two seconds, calibrates toward
100 ms (cap 256 searches), measures five plain batches, then runs a separate three-second JFR phase.
The small static-POR case reaches the cap before 100 ms; interpret its short batches accordingly.
Fixture and strategy order rotate together across forks. Validation/replay is outside timing;
result consumption and constant-time counter checks are inside. See [Spec 68](../specs/active/68-trace-and-sleep-context-profiling.md).

| Measured class | SHA-256 |
|---|---|
| `dev.samhb.interleave.dpor.DporExplorer` | `3fee66549c637ddfa374af9ad13efc3f8596a7e3ce1928be4dc1ebd55211796c` |
| `dev.samhb.interleave.dpor.SleepSet` | `acd8c15e13a889bac634ff765467b97a39551db70417ac38ae2d88eb2e6471fc` |
| `dev.samhb.interleave.por.StaticPorExplorer` | `bd4d1114a606f5c6f7360a357d5296f4f34abaca20a59bc4e2945a0a51a8427d` |
| `dev.samhb.interleave.search.DfsExplorer` | `751bcb7433501d5373e091cbe05f29b5c040ab012ab99d2aeb39b94ed49b9724` |
| `dev.samhb.interleave.search.Trace` | `bdb818316db2e3805e4ea72a60d2caa7bb0743dcc8dd6073ead5de45bb936d5f` |
| `dev.samhb.profiling.SearchProfile` | `ea1a9609f571ece8f6ecb57e682756668b1be62d65c5a4188304c184c32a61df` |

## Search work and semantic checks

Every fork/strategy passed invariant-aware replay and independently specified terminal digests
before/after measurement. The dependent workload preserves all 2,520 distinct completion values;
independent/context workloads have one normalized terminal observation after checking all end fields.
No failures, deadlocks, cutoffs or approximate stores are benchmarked.

| Fixture | Strategy | Accepted visits | Unique positions | Raw witnesses | Prefix steps replayed during trace attachment |
|---|---|---:|---:|---:|---:|
| dependent | DFS | 7,365 | 7,365 | 2,520 | 20,160 |
| dependent | POR | 7,365 | 7,365 | 2,520 | 20,160 |
| dependent | DPOR | 7,365 | 7,365 | 2,520 | 20,160 |
| independent | DFS | 729 | 729 | 1 | 12 |
| independent | POR | 13 | 13 | 1 | 12 |
| independent | DPOR | 256 | 256 | 1 | 12 |
| contexts | DFS | 729 | 729 | 1 | 12 |
| contexts | POR | 269 | 269 | 1 | 12 |
| contexts | DPOR | 1,274 | 729 | 1 | 12 |

The dependent DFS tree has 7,364 search edges versus 20,160 trace-prefix steps. This is a
count comparison, not an exact execution-work or speedup ratio: POR/DPOR also probe independence.
Context DPOR has 545 accepted revisits (1,274 visits minus 729 unique positions); no peak context
width or retained-memory measurement follows from that difference.

## Plain end-to-end trials

Each row is one fresh fork. Time is median [minimum, maximum] of five per-search batch averages.
Allocation is median [minimum, maximum] current-thread bytes/search expressed in MiB (2^20 bytes).
These are total explorer costs; JFR is disabled during these trials.

| Fixture | Strategy | Fork | Batch searches | ms/search: median [min, max] | MiB/search: median [min, max] |
|---|---|---:|---:|---:|---:|
| dependent | DFS | 1 | 8 | 20.184 [20.035, 20.603] | 79.596 [79.596, 79.596] |
| dependent | DFS | 2 | 8 | 21.461 [21.358, 21.844] | 82.407 [82.407, 82.407] |
| dependent | DFS | 3 | 8 | 21.526 [21.456, 21.815] | 79.769 [79.769, 79.769] |
| dependent | POR | 1 | 8 | 21.015 [20.828, 21.207] | 81.732 [81.732, 81.732] |
| dependent | POR | 2 | 8 | 22.045 [21.965, 22.280] | 84.858 [84.858, 84.858] |
| dependent | POR | 3 | 8 | 22.109 [21.809, 22.376] | 82.219 [82.219, 82.219] |
| dependent | DPOR | 1 | 4 | 29.379 [29.176, 30.716] | 123.093 [123.093, 123.093] |
| dependent | DPOR | 2 | 4 | 30.350 [30.150, 30.888] | 124.589 [124.589, 124.589] |
| dependent | DPOR | 3 | 4 | 30.781 [29.981, 31.289] | 123.003 [123.003, 123.003] |
| independent | DFS | 1 | 32 | 4.056 [4.030, 4.066] | 15.125 [15.125, 15.125] |
| independent | DFS | 2 | 32 | 4.256 [4.240, 4.277] | 15.215 [15.215, 15.215] |
| independent | DFS | 3 | 32 | 4.410 [4.394, 4.428] | 14.991 [14.991, 14.991] |
| independent | POR | 1 | 256 | 0.036 [0.036, 0.038] | 0.155 [0.155, 0.155] |
| independent | POR | 2 | 256 | 0.039 [0.038, 0.040] | 0.157 [0.157, 0.157] |
| independent | POR | 3 | 256 | 0.038 [0.038, 0.038] | 0.154 [0.154, 0.154] |
| independent | DPOR | 1 | 128 | 1.480 [1.476, 1.485] | 6.054 [6.054, 6.054] |
| independent | DPOR | 2 | 128 | 1.518 [1.511, 1.523] | 6.078 [6.078, 6.078] |
| independent | DPOR | 3 | 64 | 1.600 [1.594, 1.604] | 6.069 [6.069, 6.069] |
| contexts | DFS | 1 | 32 | 4.035 [4.009, 4.044] | 15.125 [15.125, 15.125] |
| contexts | DFS | 2 | 32 | 4.238 [4.221, 4.261] | 15.215 [15.215, 15.215] |
| contexts | DFS | 3 | 32 | 4.389 [4.379, 4.403] | 14.991 [14.991, 14.991] |
| contexts | POR | 1 | 128 | 0.833 [0.831, 0.837] | 3.204 [3.204, 3.204] |
| contexts | POR | 2 | 128 | 0.861 [0.858, 0.866] | 3.217 [3.217, 3.217] |
| contexts | POR | 3 | 128 | 0.874 [0.873, 0.878] | 3.186 [3.186, 3.186] |
| contexts | DPOR | 1 | 8 | 17.927 [17.856, 17.963] | 69.217 [69.217, 69.217] |
| contexts | DPOR | 2 | 8 | 17.925 [17.919, 17.974] | 69.579 [69.579, 69.579] |
| contexts | DPOR | 3 | 8 | 18.795 [18.746, 18.818] | 69.197 [69.197, 69.197] |

## Separate sampled attribution

CPU columns show execution sample counts, not exact CPU durations. Coverage means the exclusive
`coverageLambda` plus `coverageAccess` proxy; it omits some list/other bookkeeping. Sleep means
other `SleepSet` operations. Family precedence and thread/phase filtering are specified in Spec 68.
Allocation percentages use sampled `weight` estimates, not the exact plain-trial byte counter.
Absent categories are zero observed samples, never proof of zero cost.

| Fixture | Strategy | Fork | Searches | CPU samples total | Trace | Coverage | Sleep | Other/unknown | Sampled alloc: trace % | Coverage % |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| dependent | DFS | 1 | 145 | 1,797 | 464 | 0 | 0 | 1,333 | 34.67 | 0.00 |
| dependent | DFS | 2 | 136 | 1,815 | 514 | 0 | 0 | 1,301 | 37.63 | 0.00 |
| dependent | DFS | 3 | 137 | 1,803 | 457 | 0 | 0 | 1,346 | 36.06 | 0.00 |
| dependent | POR | 1 | 141 | 1,768 | 461 | 0 | 0 | 1,307 | 34.78 | 0.00 |
| dependent | POR | 2 | 133 | 1,781 | 419 | 0 | 0 | 1,362 | 38.16 | 0.00 |
| dependent | POR | 3 | 133 | 1,796 | 409 | 0 | 0 | 1,387 | 35.11 | 0.00 |
| dependent | DPOR | 1 | 101 | 1,828 | 341 | 3 | 19 | 1,465 | 23.33 | 1.04 |
| dependent | DPOR | 2 | 97 | 1,865 | 357 | 7 | 3 | 1,498 | 25.06 | 0.98 |
| dependent | DPOR | 3 | 95 | 1,815 | 337 | 12 | 12 | 1,454 | 23.26 | 1.03 |
| independent | DFS | 1 | 726 | 1,930 | 1 | 0 | 0 | 1,929 | 0.10 | 0.00 |
| independent | DFS | 2 | 689 | 1,975 | 1 | 0 | 0 | 1,974 | 0.00 | 0.00 |
| independent | DFS | 3 | 666 | 1,966 | 2 | 0 | 0 | 1,964 | 0.05 | 0.00 |
| independent | POR | 1 | 79,579 | 2,030 | 147 | 0 | 0 | 1,883 | 10.55 | 0.00 |
| independent | POR | 2 | 75,950 | 2,016 | 163 | 0 | 0 | 1,853 | 10.95 | 0.00 |
| independent | POR | 3 | 75,377 | 2,017 | 154 | 0 | 0 | 1,863 | 10.58 | 0.00 |
| independent | DPOR | 1 | 1,977 | 1,910 | 7 | 21 | 89 | 1,793 | 0.25 | 2.80 |
| independent | DPOR | 2 | 1,936 | 1,874 | 2 | 18 | 42 | 1,812 | 0.67 | 2.19 |
| independent | DPOR | 3 | 1,830 | 1,899 | 2 | 30 | 75 | 1,792 | 0.24 | 2.08 |
| contexts | DFS | 1 | 730 | 1,944 | 1 | 0 | 0 | 1,943 | 0.10 | 0.00 |
| contexts | DFS | 2 | 693 | 1,975 | 4 | 0 | 0 | 1,971 | 0.01 | 0.00 |
| contexts | DFS | 3 | 669 | 1,933 | 2 | 0 | 0 | 1,931 | 0.05 | 0.00 |
| contexts | POR | 1 | 3,520 | 2,074 | 7 | 0 | 0 | 2,067 | 0.54 | 0.00 |
| contexts | POR | 2 | 3,403 | 2,081 | 12 | 0 | 0 | 2,069 | 0.61 | 0.00 |
| contexts | POR | 3 | 3,349 | 2,103 | 5 | 0 | 0 | 2,098 | 0.79 | 0.00 |
| contexts | DPOR | 1 | 164 | 1,833 | 0 | 33 | 61 | 1,739 | 0.02 | 1.08 |
| contexts | DPOR | 2 | 163 | 1,860 | 1 | 23 | 71 | 1,765 | 0.04 | 0.64 |
| contexts | DPOR | 3 | 156 | 1,879 | 0 | 32 | 70 | 1,777 | 0.03 | 1.16 |

Execution sample shares for Trace.create in the `dependent` fixture across forks:

- DFS: 25.35–28.32%.
- POR: 22.77–26.07%.
- DPOR: 18.57–19.14%.

These inclusive trace frames include metadata reconstruction and deterministic prefix replay,
not just transition execution. Stack inlining/depth and sampling affect attribution. No sample
fraction is converted to exact method nanoseconds, and no hypothetical optimization is claimed
correct or faster. Allocation totals exclude other threads and do not measure peak/live memory.
The workload set is intentionally small and synthetic; production modeling needs can change the
priority. Broader observation contracts, formal proof and roadmap #10–12 are separate workstreams.

## Verification

All checks below used baseline `a9f2c0f` plus this profiling patch; production and mutation-harness
sources are unchanged. The measured runner fingerprints above still match the compiled classes.

- `build javadoc pitest` with both local worker flags: PASS. 1,303 tests, zero failures/errors/skips;
  build and strict production/harness Javadoc pass. Thirteen new cases cover the profiling tool.
- Default PIT state/CBS scope: 281/291 KILLED, ten SURVIVED, zero NO_COVERAGE or resource/run
  errors; full R5/R6/R7 gate passes. All 291 semantic identities and statuses match the frozen
  Spec 67 default report. No scoped Parser/compiler rerun: those sources and selectors are unchanged.
- Three separate `profileSearch` invocations: PASS, all 27 fixture/strategy/fork combinations,
  matching fingerprints, literal terminal oracles, full invariant-aware replay and stable counters.
- `node docs/visualizer-normalize-check.mjs`: all 18 checks pass. No browser rendering changes;
  the Playwright browser suite was not rerun locally.
- CodeRabbit CLI 0.8.1: actual `review_completed` on all six changed files, two minor report findings.
  Independently verified and patched the dependent-fixture label and this verification ledger;
  Markdown links/table shapes/diff checks pass. No CLI repeat for these minor documentation fixes.
- Remote PR CI is not observed here; Sam supplies CI/review/merge status after publication.
