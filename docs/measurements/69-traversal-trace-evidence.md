# Traversal trace evidence: before/after measurements

## Result and boundary

Compare three fresh baseline JVM forks with three fresh optimized forks using the unchanged
Spec 68 runner. Source evidence now comes from actual traversal edges; terminal trace emission
does not re-execute those edges. The public replay-based Trace.create factory and source schemas
remain unchanged. Full source witnesses/oracles and per-case search counters match in all forks.

These are local synthetic workloads on one host. Cross-fork timing changes include JIT/host noise;
no portable performance guarantee, reduced search work or peak/live memory claim follows.
Coverage/pruning and DPOR representations are unchanged.

## Provenance and commands

Baseline production source: merged main `460641d` (PR #110). Baseline profiling ran in the clean
prior worktree at `0f150f3`, whose source tree is identical to that merge. Fresh baseline build/tests/
Javadoc/default mutation ran in the new worktree at `460641d` before edits. Optimized profiles ran
on `460641d` plus this uncommitted Spec 69 patch. All three fingerprints match within each phase;
the profiling runner is identical across phases. Record the additional TracePrefix hash below.
No build/tests/mutation runs or external CLI reviews overlapped either measured phase.
Other host activity was uncontrolled.

Host: Mac OS X aarch64, OpenJDK 64-Bit Server VM 26.0.2.1, 10 logical processors.
Fixed child heap 512 MiB initial/maximum. Fixture/strategy order rotates across three sequential
forks. Each case: two seconds warmup, five plain batches calibrated toward 100 ms (cap 256 searches),
followed by a separate three-second JFR phase. Short independent-POR batches hit the cap.
Correctness replay/model loading happen outside timing; result consumption/counter checks remain inside.

```sh
./gradlew --max-workers=1 -PpitestThreads=1 profileSearch -PprofileFork=1
./gradlew --max-workers=1 -PpitestThreads=1 profileSearch -PprofileFork=2
./gradlew --max-workers=1 -PpitestThreads=1 profileSearch -PprofileFork=3
```

Run at the baseline then at this change, sequentially and with identical workload/VM settings.
Raw JSON/JFR: each worktree's ignored `build/reports/search-profile/fork-N/`. Baseline JSON was
frozen locally in `/private/tmp/interleave-traversal-profiles-before/fork-N.json` before subsequent
measurements. This report commits per-fork medians/ranges and comparison results, not binary artifacts.
The old JFR `traceReplay` family matches Trace.create only; it does **not** classify the new
TracePrefix freezing/capture paths. Do not interpret absent old-family samples as zero trace cost.
The raw `replaySteps` field is the sum of recorded prefix lengths, not a count of actual
after-change executions. Plain end-to-end time/current-thread allocation trials are the comparison.

| Class | Baseline SHA-256 | Optimized SHA-256 |
|---|---|---|
| `dev.samhb.interleave.dpor.DporExplorer` | `3fee66549c637ddfa374af9ad13efc3f8596a7e3ce1928be4dc1ebd55211796c` | `5be5996d1902e31f2242df18c21c2891b2741c37ddb6a4adc620a966002bf623` |
| `dev.samhb.interleave.dpor.SleepSet` | `acd8c15e13a889bac634ff765467b97a39551db70417ac38ae2d88eb2e6471fc` | `acd8c15e13a889bac634ff765467b97a39551db70417ac38ae2d88eb2e6471fc` |
| `dev.samhb.interleave.por.StaticPorExplorer` | `bd4d1114a606f5c6f7360a357d5296f4f34abaca20a59bc4e2945a0a51a8427d` | `79339873f1378d87195696f26bc556f12ca91e874f41d924756084ac35d00309` |
| `dev.samhb.interleave.search.DfsExplorer` | `751bcb7433501d5373e091cbe05f29b5c040ab012ab99d2aeb39b94ed49b9724` | `79b582bd303cbbf8878810727964ac6eefe0d9f0219d13723cf220dad0fd640c` |
| `dev.samhb.interleave.search.Trace` | `bdb818316db2e3805e4ea72a60d2caa7bb0743dcc8dd6073ead5de45bb936d5f` | `bdb818316db2e3805e4ea72a60d2caa7bb0743dcc8dd6073ead5de45bb936d5f` |
| `dev.samhb.profiling.SearchProfile` | `ea1a9609f571ece8f6ecb57e682756668b1be62d65c5a4188304c184c32a61df` | `ea1a9609f571ece8f6ecb57e682756668b1be62d65c5a4188304c184c32a61df` |
| `dev.samhb.interleave.search.TracePrefix` | absent | `9bdebb6431bb61d9cca0b5e456b7320101ca10980db1c2acd380acbc75b2ca5d` |

## End-to-end comparison

Each phase value is the median of its three per-fork medians, each based on five per-search batch
averages. Positive reduction means less cost; negative reduction means a regression. Allocation
uses exact current-thread counter totals divided by searches, reported in MiB (2^20 bytes); this
excludes other-thread allocation and does not measure retained heap or GC pause costs separately.

| Fixture | Strategy | Before ms/search | After ms/search | Time reduction % | Before MiB/search | After MiB/search | Allocation reduction % |
|---|---|---:|---:|---:|---:|---:|---:|
| dependent | DFS | 21.685 | 15.355 | 29.19 | 80.856 | 56.295 | 30.38 |
| dependent | POR | 22.008 | 15.576 | 29.23 | 82.992 | 58.716 | 29.25 |
| dependent | DPOR | 30.460 | 23.647 | 22.37 | 123.593 | 98.906 | 19.97 |
| independent | DFS | 4.311 | 4.464 | -3.53 | 15.125 | 15.933 | -5.34 |
| independent | POR | 0.039 | 0.036 | 7.89 | 0.156 | 0.142 | 8.83 |
| independent | DPOR | 1.558 | 1.585 | -1.69 | 6.054 | 6.131 | -1.28 |
| contexts | DFS | 4.305 | 4.492 | -4.35 | 15.125 | 15.933 | -5.34 |
| contexts | POR | 0.873 | 0.897 | -2.75 | 3.191 | 3.286 | -3.00 |
| contexts | DPOR | 18.509 | 18.459 | 0.27 | 68.995 | 70.297 | -1.89 |

## Per-fork plain-trial spread

Every row shows median [minimum, maximum] across five per-search batch averages. Timings are ms;
allocations are MiB. JSON preserves raw operations/nanoseconds/bytes.

| Fixture | Strategy | Phase | Fork | Batch searches | ms/search: median [min, max] | MiB/search: median [min, max] |
|---|---|---|---:|---:|---:|---:|
| dependent | DFS | before | 1 | 8 | 20.877 [20.776, 21.323] | 80.856 [80.856, 80.856] |
| dependent | DFS | before | 2 | 8 | 21.685 [21.577, 22.417] | 81.490 [81.490, 81.490] |
| dependent | DFS | before | 3 | 8 | 21.690 [21.584, 22.046] | 79.634 [79.634, 79.634] |
| dependent | DFS | after | 1 | 8 | 14.160 [13.894, 14.391] | 55.634 [55.634, 55.634] |
| dependent | DFS | after | 2 | 8 | 16.068 [15.281, 17.880] | 56.488 [56.488, 56.488] |
| dependent | DFS | after | 3 | 8 | 15.355 [15.120, 16.358] | 56.295 [56.295, 56.295] |
| dependent | POR | before | 1 | 8 | 21.466 [21.385, 21.885] | 82.992 [82.992, 82.992] |
| dependent | POR | before | 2 | 8 | 22.008 [21.821, 22.165] | 83.941 [83.941, 83.941] |
| dependent | POR | before | 3 | 8 | 22.231 [22.178, 22.750] | 82.055 [82.055, 82.055] |
| dependent | POR | after | 1 | 8 | 14.812 [14.561, 14.963] | 57.990 [57.990, 57.991] |
| dependent | POR | after | 2 | 8 | 15.576 [15.372, 15.654] | 58.938 [58.938, 58.938] |
| dependent | POR | after | 3 | 8 | 15.821 [15.522, 16.390] | 58.716 [58.716, 58.716] |
| dependent | DPOR | before | 1 | 4 | 30.455 [29.756, 30.959] | 125.090 [125.090, 125.090] |
| dependent | DPOR | before | 2 | 4 | 30.460 [29.712, 31.019] | 123.593 [123.593, 123.593] |
| dependent | DPOR | before | 3 | 4 | 31.353 [30.964, 31.634] | 121.337 [121.337, 121.337] |
| dependent | DPOR | after | 1 | 8 | 22.973 [22.754, 22.993] | 98.906 [98.906, 98.906] |
| dependent | DPOR | after | 2 | 8 | 23.647 [23.431, 23.736] | 98.590 [98.590, 98.590] |
| dependent | DPOR | after | 3 | 8 | 24.679 [24.190, 27.053] | 99.929 [99.929, 99.929] |
| independent | DFS | before | 1 | 32 | 4.295 [4.284, 4.314] | 15.125 [15.125, 15.125] |
| independent | DFS | before | 2 | 32 | 4.311 [4.303, 4.336] | 15.125 [15.125, 15.125] |
| independent | DFS | before | 3 | 32 | 4.406 [4.401, 4.422] | 14.991 [14.991, 14.991] |
| independent | DFS | after | 1 | 32 | 4.300 [4.278, 4.312] | 15.716 [15.716, 15.716] |
| independent | DFS | after | 2 | 32 | 4.464 [4.447, 4.467] | 15.933 [15.933, 15.933] |
| independent | DFS | after | 3 | 32 | 4.529 [4.509, 4.662] | 15.933 [15.933, 15.933] |
| independent | POR | before | 1 | 256 | 0.039 [0.038, 0.040] | 0.156 [0.156, 0.156] |
| independent | POR | before | 2 | 256 | 0.039 [0.039, 0.041] | 0.157 [0.157, 0.157] |
| independent | POR | before | 3 | 256 | 0.038 [0.038, 0.039] | 0.154 [0.154, 0.154] |
| independent | POR | after | 1 | 256 | 0.039 [0.037, 0.043] | 0.142 [0.142, 0.142] |
| independent | POR | after | 2 | 256 | 0.036 [0.035, 0.038] | 0.144 [0.144, 0.144] |
| independent | POR | after | 3 | 256 | 0.034 [0.034, 0.036] | 0.141 [0.141, 0.141] |
| independent | DPOR | before | 1 | 128 | 1.546 [1.537, 1.553] | 6.054 [6.054, 6.054] |
| independent | DPOR | before | 2 | 64 | 1.558 [1.556, 1.566] | 6.044 [6.044, 6.044] |
| independent | DPOR | before | 3 | 64 | 1.593 [1.591, 1.597] | 6.091 [6.091, 6.091] |
| independent | DPOR | after | 1 | 128 | 1.591 [1.539, 1.607] | 6.131 [6.131, 6.131] |
| independent | DPOR | after | 2 | 128 | 1.585 [1.547, 1.613] | 6.131 [6.131, 6.131] |
| independent | DPOR | after | 3 | 128 | 1.551 [1.547, 1.556] | 6.172 [6.172, 6.172] |
| contexts | DFS | before | 1 | 32 | 4.282 [4.267, 4.299] | 15.125 [15.125, 15.125] |
| contexts | DFS | before | 2 | 32 | 4.305 [4.270, 4.322] | 15.126 [15.126, 15.126] |
| contexts | DFS | before | 3 | 32 | 4.423 [4.396, 4.437] | 14.991 [14.991, 14.991] |
| contexts | DFS | after | 1 | 32 | 4.492 [4.275, 5.206] | 15.716 [15.716, 15.716] |
| contexts | DFS | after | 2 | 32 | 4.479 [4.444, 4.487] | 15.933 [15.933, 15.933] |
| contexts | DFS | after | 3 | 32 | 4.499 [4.493, 4.514] | 15.933 [15.933, 15.933] |
| contexts | POR | before | 1 | 128 | 0.850 [0.849, 0.857] | 3.121 [3.121, 3.121] |
| contexts | POR | before | 2 | 128 | 0.873 [0.871, 0.876] | 3.219 [3.219, 3.219] |
| contexts | POR | before | 3 | 128 | 0.890 [0.886, 0.894] | 3.191 [3.191, 3.191] |
| contexts | POR | after | 1 | 128 | 0.856 [0.854, 0.861] | 3.268 [3.268, 3.268] |
| contexts | POR | after | 2 | 128 | 0.897 [0.896, 0.900] | 3.309 [3.309, 3.309] |
| contexts | POR | after | 3 | 128 | 0.899 [0.895, 0.906] | 3.286 [3.286, 3.286] |
| contexts | DPOR | before | 1 | 8 | 18.509 [18.492, 18.527] | 69.217 [69.217, 69.217] |
| contexts | DPOR | before | 2 | 8 | 18.426 [18.346, 18.541] | 68.995 [68.995, 68.995] |
| contexts | DPOR | before | 3 | 8 | 19.590 [19.487, 19.650] | 68.657 [68.657, 68.657] |
| contexts | DPOR | after | 1 | 8 | 18.459 [18.366, 18.543] | 70.297 [70.297, 70.297] |
| contexts | DPOR | after | 2 | 8 | 18.440 [18.427, 18.501] | 70.180 [70.180, 70.180] |
| contexts | DPOR | after | 3 | 8 | 18.772 [18.630, 18.968] | 70.833 [70.833, 70.833] |

## Semantic/work checks and decision

All 54 measured case/fork combinations pass independent terminal digests, invariant-aware replay
and stable counters. Dependent: 7,365 visits/keys, 2,520 complete traces and 20,160 recorded prefix
steps for each strategy. Independent: DFS 729, POR 13, DPOR 256 visits/keys, one full witness.
Contexts: DFS 729, POR 269 visits/keys; DPOR 1,274 visits/729 unique positions, one full witness.
Context revisits are necessary accepted traversal work, not a peak antichain-width measurement.
CBS has separate regression coverage, not a performance claim from this three-strategy harness.

The work-count regression independently observes two actual source executions during search versus
four in the old implementation for a two-action fixture. The compatibility factory still adds two
replay executions outside search. Differential tests compare complete serialized evidence to that
factory over 13 scenarios and five traversal routes, plus CBS preemption snapshots/reuse and actual
work-count checks. The gain is avoided duplicate execution at trace emission, not fewer search states.
Persistent metadata adds work on edges without terminal traces; retain the independent/context
results above as the allocation/time tradeoff rather than claiming every model gets faster.
Keep the optimization scoped to trace evidence; no DPOR representation/pruning changes are selected.
Follow [Spec 69](../specs/active/69-traversal-trace-evidence.md) for correctness and gate details.

## Integrated verification

Initial bundle: build/tests/strict Javadoc pass 1,375 tests (72 new cases). Fresh default mutation census:
283/293 KILLED + ten SURVIVED; CBS 125/125 versus fresh 123/123. All 168 state-scope identities/
statuses are retained; 204 identities are exactly shared overall. The CBS descriptor/index changes
replace 87 killed identities with 89 killed identities, net two new call mutants, not old survivors
newly killed. Spec 69 records the full method/operator census and denominator/floor derivation.
The 94% floor preserves the prior seven-kill headroom; mutation target/test filters/operators are
unchanged. Full R5/R6/R7 pass after the measured denominator update, using the frozen default XML.
The separate new TracePrefix scope is 30/31 KILLED, one SURVIVED (List.size used only for an
ArrayList capacity hint), zero uncovered/resource/run errors; scoped R6 passes, R5/R7 skip.
Its successful-allocation trace-value disposition is recorded in Spec 69, without claiming equal
allocation cost or excluding the survivor. All 18 visualizer normalization checks pass.

CodeRabbit CLI 0.8.1 completed ten initial files with one minor census-documentation finding.
The mutation README now records the current four class figures/provenance and preserves historical
measurements. Markdown/table/link/diff checks pass; no CLI repeat for the minor docs patch.
Browser rendering tests and unrelated frontend scoped PIT were not rerun. Remote CI is left for
Sam to report after publication.

PR #111 feedback adds five failed-action work-count cases. Integrated build/Javadoc and all 1,380
tests pass; no production or profiling-code changes. Original measurements and mutation census
remain the recorded runs above; they were not rerun for this test-only follow-up.
