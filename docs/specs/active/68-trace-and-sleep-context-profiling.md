# Spec: Trace construction and DPOR sleep-context profiling

## Objective and boundary

Measure 9-V3 and 9-V5 together before selecting optimization work. Add one opt-in JavaExec task,
fixed public source fixtures, independently checked terminal observations, replay validation and a
reproducible measurement report. No production code, trace schema, pruning semantics, dependencies,
mutation filters/operators/floors, PIT heap/time settings or CI parallelism change.

`SearchProfile` and its tests live under `dev.samhb.profiling` in the test source set. They do not
ship in the library or application. The existing `dev.samhb.interleave.*` PIT test filter remains
unchanged; the profiler does not expand mutation-test selection. `profileSearch` depends on
`testClasses`, not `test`, and never attaches the mutation agent.

## Fixed fixtures and independent oracles

All models use source compilation outside timing, exact storage and `invariant always (true)`.
The known empty property observation set enables the actual reduced source paths, rather than the
unknown-property exhaustive fallback. Each owner executes two atomic actions.

| Fixture | Owners | Actions | Independent DFS oracle | Terminal observation |
|---|---:|---|---:|---|
| dependent | 4 | Twice append owner digit 1..4 to shared base-five x | 7,365 prefix states, 2,520 leaves, 20,160 recorded prefix steps | All distinct schedule encodings |
| independent | 6 | Set own xi to 1, then 2 | 729 positions | Every xi=2, every PC=2 |
| contexts | 6 | Set shared flag=1, then own xi=1 | 729 positions | flag=1, every xi=1, every PC=2 |

For dependent models, Python enumerates all words containing each owner twice, constructs each
base-five value both by a positional sum and iterative accumulation, and compares prefix-set size
to the sum of multinomial coefficients over counter vectors. Four-owner values fit signed int.
Sorted decimal values with newline delimiters hash to
`051e302cc07bc145c4c8677f4bd1268fafd6e49f40974980804b43569e6c8a87`.
The smaller two-owner control has 19 states, six schedules and 24 prefix steps; terminal values
are 162, 182, 186, 282, 286, 306. Its digest is
`8ac14501cf689e81f25e74b21d06b47e20eb9368e10d58869e73f857a5e42f90`.
For independent/context products, six independent three-position counters give 3^6=729, also
confirmed by direct counter-vector enumeration. Full completion has just one observation, normalized
to zero after checking every field/PC; SHA-256 of `0` plus newline is
`9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa`.

Every strategy's witnesses replay with the invariant-aware overload, must complete with all owners
terminated, and must match the literal terminal digest. Full validation runs before and after plain
trials and after attribution. Counter drift fails each timed invocation. The context DPOR case must
have accepted visits greater than unique keys. This difference is accepted revisits, **not** retained
antichain width, peak context count or proof that every internal antichain branch was exercised.
The fixtures concern safe completions; they do not establish failure, cutoff or deadlock preservation.

## Measurement contract

Run three fresh JVM forks sequentially, rotating fixture and strategy order. Java 26 launcher,
512 MiB initial/maximum heap. Two seconds of warmup per case; double batch size until one batch
lasts at least 100 ms or reaches 256 operations, then collect five plain batches. Report raw batch
operations/nanoseconds/current-thread allocated bytes and per-search medians/ranges. Loading,
compilation and correctness replay are outside timing. Consumption and constant-time counter checks
remain inside; this is an end-to-end explorer workload, not an isolated method benchmark.

JFR runs separately for three seconds per case, with 1 ms requested execution sampling,
`jdk.ObjectAllocationSample` and a custom duration event marking the main-thread search phase.
Only events within the marker and on its Java thread contribute. Execution samples identify their
thread through `sampledThread`; allocation samples use `eventThread`. Allocation `weight` is a
sampled estimate, separate from exact current-thread trial allocation counters. Raw recordings and
JSON live in ignored `build/reports/search-profile/fork-N/`; binary recordings are not committed.

Classify inclusive stack families once per sample, with this precedence:

1. `Trace#create`: source evidence attachment including its prefix replay.
2. `DporExplorer#lambda$dporDfs$*`: the current computeIfAbsent/anyMatch/removeIf coverage lambdas.
3. `SleepSet#entries` or `SleepSet#copy`: coverage access/copy proxy.
4. Other `SleepSet` frames: other sleep operations, including filtering.
5. Other frames, or unknown for missing stacks.

The coverage families are proxies, **not exact whole-antichain timing**: list bookkeeping can fall
in other, and inlining/sample depth can obscure frames. Missing or sparse samples cannot establish
zero cost. No conversion from sample fraction to exact method nanoseconds. No speedup claim without
a later controlled before/after implementation measurement. SHA-256 class fingerprints identify
measured code; the committed report records its source baseline and dirty-tree status explicitly.
Only safe VM/version/OS/heap/processor metadata is recorded, never full environment or JVM arguments.

## Verification and disposition

Focused tests cover all three explorer paths on externally derived small/full dependent oracles,
independent reductions, context revisits, rejected empty evidence, exclusive stack classification
and actual JFR phase round-trip/missing marker. Sampling counts are deliberately not asserted in
short tests. Full build/tests/Javadoc, default PIT R5/R6/R7, external review and the three measured
forks are recorded in the accompanying [measurement report](../../measurements/68-search-profile.md).

Keep 9-V4 observation/proof work and 9-V6 formal proof separate. Later trace plumbing or DPOR
representation changes require their own coherent implementation PR and before/after evidence.
Roadmap #10–12 remain after the deferred follow-up backlog and bug fixes.
