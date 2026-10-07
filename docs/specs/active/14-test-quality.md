# 14 — Meaningful test contracts

> Replace vacuous checks and overstated claims, consolidate demonstrated duplicates, and preserve independent correctness checks. Test count is an outcome, not a goal.

## Baseline and scope

Baseline is `adb52ee`: 526 passing invocations from 514 methods in 47 classes. The complete method ledger lives in [the audit](../../plans/test-quality-audit.md). Decisions use the behavior protected, a plausible defect, and the independence of the oracle; assertion count alone is not a quality measure.

No public API, search algorithm, dependencies, CI settings, mutation floor, or Javadoc gate changes. The one production refactoring is a package-private `InterleaveRunner.run(Program, LongSupplier)` overload. The public method passes `System::currentTimeMillis`. The clock belongs to a call, is never stored on the reusable runner, and leaves existing wall-clock millisecond semantics intact.

## Implemented contracts

- Invariant factories evaluate real configurations: flags, exact critical-section thresholds, nonterminal counters, matching terminal counters and mismatching terminal counters. Literal `assertTrue(true)` placeholders are replaced.
- DFS checks four hand-enumerated positions for two independent writes, actual terminal replay values and two known violating prefixes. No encoder generates the expected set.
- Builder settings are exercised by searches and an observable supplied store. Reuse alternates different known programs. Concurrent runs compare counts, buckets and trace contents to serial references, with bounded waiting and executor cleanup.
- Deadlines are tested below, exactly at and above 10ms; null, zero, all four strategies and retained earlier traces are covered. A test store advances the clock on modeled positions; modeled steps remain deterministic and never sleep.
- JSON is parsed strictly with existing Gson, then checked for typed values, nested traces and stable schemas. Trace and program isolation tests mutate caller-owned data and returned state.
- A known minimization fixture must shrink `[1,0]` to `[0]`. Replay establishes the counterexample independently of the minimizer's label. Original preservation and subsequence checks remain.
- CLI invalid options and filters execute the actual entry point in a child JVM instead of duplicating validator/filter expressions in a test. Processes have bounded waiting, merged output capture and cleanup.
- POR outcome checks compare real verdicts. A null-invariant dynamic-index fixture exercises reduction and requires three hand-enumerated terminal values; invariant-based DPOR checks are explicitly fallback checks, not proof of sleep-set reduction.
- Numeric expectations use literals derived by Python Decimal at 50 digits and independently checked with an exponential series. Occupancy uses a two-vector, one-bit fixture with density exactly 1.

## Consolidation and preserved coverage

Every removed method maps to retained coverage in the ledger. Duplicate factory non-null checks, facade conversions/defaults, resource-limit smokes, corpus-count smoke, the enum inequality tautology, duplicate negative-capacity rejection and duplicate default-final metadata are consolidated. Independent encoder sampling, real collision tests, lifecycle and cost dominance checks, 1,875 generated POR/property cases, CBS differential/monotonicity checks and trace emission contracts remain.

The conjunction test now claims its observable violation behavior. It cannot distinguish skipped evaluation from an error converted to false; evaluation-order coverage is explicitly deferred.

## Verification

The final suite has **524 passing invocations in 48 classes**, with no skips. The ledger classifies the original 514 methods as 433 keep, 65 strengthen (including corrected claim names) and 16 consolidate. Five added methods include three four-strategy parameterized clock tests, so invocation count changes differently from method count. Explicit owning-thread registry execution was also strengthened; the ledger records the final 65 strengthened methods.

Local commands always include `--max-workers=1 -PpitestThreads=1`. Baseline default PIT: **255 KILLED / 268 total**, 13 SURVIVED, no error/timeout statuses. Default scope is `state.*` and `cb.*`; it cannot certify other packages.

An additional baseline measures Interleave, InterleaveRunner, TestResult, TraceRecord, VerificationResult, DporExplorer, SleepSet, InvariantRegistry, DeltaDebugger, StaticPorExplorer, ReportWriter, DfsExplorer and TraceReplayer. DynamicStep and DslInvariant have a separate scoped baseline. Final scoped comparisons use class, method descriptor, mutation operator, instruction index and block index, so added lines do not create false differences. The measured final comparisons are recorded below.

### Deliberate defects rejected

All six checks ran in an isolated checkout after a passing unmodified control. Each defect caused test failures, not a compilation failure. Source was restored after each run.

| Deliberate defect | Failing test contract |
|---|---|
| Peterson factory returns constant true | Both-in-CS rejection and PC boundary |
| DFS skips thread 1 | Exact positions, completion replay and violation prefixes |
| Replayer returns initial configuration | Terminal values and violation replay |
| Runner ignores deadline | Exact/above boundary, zero limit and retained-trace interruption |
| Report writer emits malformed JSON | Strict report parser |
| Minimizer returns original input | Required `[1,0]` → `[0]` shrink |

## Separate production finding

`ReportWriter.writeJson` interpolates free-form bug names, strategy and verdict without JSON escaping; `TraceRecord.toJson` does the same for `programHash`. Two isolated regression probes confirmed that embedded quotes in a benchmark name and a trace hash both cause Gson `MalformedJsonException`. An embedded quote or backslash can produce invalid JSON. Fixing that serialization contract is separate production work. Ordinary fixtures are checked strictly here; their success does not establish arbitrary-string serialization correctness.

## Acceptance

- Clean build, full tests (524 / 524), public Javadoc and private-member doclint pass. No skips or disabled gates.
- Default PIT and `mutationRatchet` pass at **255 / 268 (95.15%)**, unchanged from the fresh baseline. No timeout, memory, non-viable or execution-error statuses. Scope and floor remain unchanged.
- Expanded scoped reports are compared against freshly measured baselines and remaining survivors are reported honestly.
- Visualizer normalization checks pass against existing JSON contracts.
- Audit, spec, source and personal context agree on measured results and remaining limits.

## Mutation comparisons

Baseline: `adb52ee`. Final: test-quality working tree. Statuses use assertion-backed `KILLED` only; no timeout or infrastructure result counts as a kill.

| Class | Baseline killed / total | Final killed / total | Final survived | Final no coverage |
|---|---:|---:|---:|---:|
| `dev.samhb.interleave.Interleave` | 47 / 110 | 47 / 110 | 43 | 20 |
| `dev.samhb.interleave.InterleaveRunner` | 52 / 89 | 68 / 91 | 20 | 3 |
| `dev.samhb.interleave.TestResult` | 56 / 80 | 74 / 80 | 4 | 2 |
| `dev.samhb.interleave.TraceRecord` | 16 / 38 | 37 / 38 | 1 | 0 |
| `dev.samhb.interleave.VerificationResult` | 57 / 104 | 98 / 104 | 5 | 1 |
| `dev.samhb.interleave.dpor.DporExplorer` | 128 / 211 | 133 / 211 | 25 | 53 |
| `dev.samhb.interleave.dpor.SleepSet` | 4 / 24 | 16 / 24 | 0 | 8 |
| `dev.samhb.interleave.format.registry.InvariantRegistry` | 80 / 84 | 80 / 84 | 4 | 0 |
| `dev.samhb.interleave.minimize.DeltaDebugger` | 25 / 61 | 42 / 61 | 11 | 8 |
| `dev.samhb.interleave.por.StaticPorExplorer` | 106 / 121 | 106 / 121 | 8 | 7 |
| `dev.samhb.interleave.report.ReportWriter` | 56 / 138 | 120 / 138 | 15 | 3 |
| `dev.samhb.interleave.search.DfsExplorer` | 70 / 81 | 72 / 81 | 6 | 3 |
| `dev.samhb.interleave.search.TraceReplayer` | 22 / 23 | 23 / 23 | 0 | 0 |
| `dev.samhb.interleave.format.dsl.DslInvariant` | 26 / 36 | 26 / 36 | 3 | 7 |
| `dev.samhb.interleave.format.dsl.DynamicStep` | 84 / 133 | 99 / 133 | 7 | 27 |

Semantic identities include class, method descriptor, operator, instruction index and block index; source line numbers are excluded. The runner clock seam moves instructions to a new overload and changes calls, so added/removed runner identities are reported separately.

- `expanded`: 1164 → 1166 mutants; 1125 unchanged identities, 39 removed, 41 added. Status transitions: `{"('SURVIVED', 'KILLED')": 178, "('NO_COVERAGE', 'KILLED')": 8, "('SURVIVED', 'NO_COVERAGE')": 1, "('NO_COVERAGE', 'SURVIVED')": 1, "('KILLED', 'SURVIVED')": 1}`.
- `dsl`: 169 → 169 mutants; 169 unchanged identities, 0 removed, 0 added. Status transitions: `{"('SURVIVED', 'KILLED')": 15}`.

### Remaining limits

- No assertion-backed regression is accepted for a meaningful contract. An empty-array serializer mutant can now survive because `[]` and `[\n]` parse to the same JSON array; the previous substring check pinned whitespace, and no wire contract requires it.
- `Interleave`, result/trace containers and reporting still have survivors and uncovered operations, including unused overloads, defensive-copy paths and malformed/edge inputs. This audit does not declare these equivalent merely because the suite passes.
- `DporExplorer` still has uncovered reduction/backtracking paths. Generated property tests, dependent-order fixtures and fallback verdict checks have different obligations and do not establish complete backtracking coverage.
- `DeltaDebugger` still has uncovered failure handling and surviving search-loop mutations. The known shrink and replay checks reject the no-op minimizer; they do not prove globally minimal output.
- Remaining `InvariantRegistry` mutants include null recognition and diagnostic formatting. The earlier placeholders were real defects in test claims even though their replacement does not change this class's current kill count.
- Exact survivor XML is attached as [mutation evidence](test-quality-mutations.json); statuses remain open unless a contract or equivalent-behavior argument establishes otherwise.

## Review disposition

CodeRabbit completed a review of 29 changed files: three minor findings and one trivial finding, no major or critical findings. The subprocess classpath now derives resources from a known corpus URL, and the orphaned density Javadoc is removed. Measured scoped results replace pending text. The conjunction finding claimed a fixture had been removed; comparison with `adb52ee` confirms both fixtures are retained, and the ledger states their limited observable claim.

The second review completed with one minor finding and five trivial findings, no major/critical findings. The JSON helper now rejects null/blank input with a descriptive syntax exception; fallback-only verdict comments and diagnostic state text are corrected. The audit separately lists all five additions with invocation accounting. The completed verification status was already updated by the time that status finding arrived. README corpus and documentation/test censuses now distinguish current counts from historical measurements. These small corrections were manually checked and the full suite rerun; no third remote review was requested.

## PR #49 feedback follow-up

Both inline findings were verified. The README now lists all eight resource definitions and distinguishes resource files from runnable examples. `writeJson_shapeIndependentOfVerdict` now compares all 16 matching DFS row schemas across DFS-only and mixed reports, including present/null `preemptionsUsed`. An isolated omission only in DFS-only output fails that assertion; the control reporting suite passes. No tests were added or removed. The ledger's corresponding baseline row is updated.

The [CodeRabbit configuration audit](../../plans/coderabbit-configuration-audit.md) records current-schema validation, actual remote use of repository YAML plus inherited organization settings, and 14 representative minimatch checks. The YAML and review/check thresholds are unchanged. The local feedback review completed with zero findings; its scope was README, ledger and reporting test. Previous mutation results describe the measured cleanup snapshot, not a new mutation run after this follow-up.
