<!-- 94C local acceptance evidence; CI/manual review and merge remain publication boundaries. -->
# 94C — Public bytecode release evidence

## Status and baseline

Local implementation and acceptance checks complete under [Spec94C](../specs/active/94C-bytecode-release.md) and the
[shared contract](../plans/bytecode-contract.md). PR140/94B merged at
`6eb939ea19c3938db8052fc1b32d8f157fc913c7`, verified against remote main before creating the isolated
`feat/bytecode-public-release` checkout. The original dirty checkout and prior worktrees are preserved.
Fresh merged-main build/tests/strict Javadoc passes 2066 tests with zero failures/errors/skips.
Configured default mutation baseline: 455 KILLED +13 SURVIVED /468. Affected baseline across bytecode,
runner/result/trace/replay, CLI and benchmark/report/attestation:3163 KILLED +253 SURVIVED +478
NO_COVERAGE /3894, zero resource/run-error statuses. Both completed before production edits; the
larger affected run took 95 minutes. Raw logs/XML are retained in the persistent evidence sibling.
Historical94B counts are not94C baselines.

## Implementation order and approved seams

1. Publish annotation/immutable program/limits/loading interfaces and the runner's dedicated bytecode
route, producing owned BYTECODE identity, real transition metadata and immutable snapshots.
2. Authenticate strict replay and JSON, including initial failures, modeled cutoffs and honest external
prefix replay. Bytecode and source evidence remain separate; native streams have an upgrade boundary.
3. Route CLI, benchmark/report/attestation and viewer consumers, with externally compiled built-jar
lost-update/deadlock examples and complete legacy/browser compatibility.

Tests use the contract's loader/runner, replay, CLI/report and browser seams. Internal metadata/state
stay owned by the existing bytecode package. One narrow integration facade is needed for cross-package
loader/runner/replayer calls; it exposes no target state, generic callback or configurable backend.
Existing source/JSON interfaces and default selections retain their behavior. Runtime limits/mode and
artifact/model/declaration identity are authenticated separately. Only exact DFS is admitted in94C;
95+ and weak memory remain outside this milestone.

## Connected implementation evidence

`BytecodeRuntime` is the narrow cross-package facade. `BytecodeObservations` owns the same check,
terminal and deadlock observation order for exact search and strict replay. Public execution captures
immutable typed snapshots during search; it never reexecutes a cancelled path to construct diagnostics.
The internal 94B measurement route keeps public snapshot capture disabled. No new target opcode or
batching rule is admitted here; the [94B preservation argument and default review](94B-bytecode-concurrency.md)
therefore apply unchanged. Provisional resource defaults remain unchanged; no new performance claim
or broad-heap sharedness measurement is inferred from consumer tests.

Connected public tests cover real lost updates, two-lock deadlocks, committed writes before faults,
cleanup fuel exhaustion with the pending fault and held monitor retained, empty root checks, observer
errors, cancellation/max-state/deadline prefixes, strict identity/operation/state/terminal tampering,
artifact relocation and frozen input, changed debug bytes/limits, unavailable strategies and callbacks.
Lossless tagged LONG/raw-bit transport is tested as reserved schema, not wider primitive execution.
A second reachable annotated model currently rejects during loading; selected-model authentication is
attacked directly with the exact original digest for empty root and interrupted records. This is not
reported as a successful two-model loading experiment.

An independently compiled consumer loads both checked-in examples using only the built library jar,
finds CHECK_FALSE/DEADLOCK, imports JSON, strictly replays the witnesses and round-trips current native
TestResult streams. Native compatibility is build-specific and changed generated UIDs reject old
shapes. The library jar is distinguished from the separate mutation-harness jar. Actual CLI tests
exercise supported selection and reject incompatible/duplicate/missing flags before artifact loading.
Report soundness replays all typed terminal witnesses against the actual frozen model.

The final build and strict public Javadoc pass 2,085 tests with zero failures/errors/skips, including
the report-transport regression. The initial missing
@Model type comment was fixed. The complete browser matrix passes 299 assertions (248 inherited +51
BYTECODE), with zero console errors, including absolute fixture directories, literal untrusted labels
and selected-configuration monitor ownership. Raw evidence: `final-browser.log`.

JSON import is bounded to 16 Mi UTF-16 code units, depth 64 and one million nodes before record
construction. Duplicate keys, unknown/conflicting schema, scalar coercions and mutable display leaves
reject. Isolated UTF-16 surrogates are escaped for UTF-8 transport. Viewer validation is structural;
only Java strict replay authenticates execution. External stops retain original INCOMPLETE status and
separate replay observations.

## Publication boundary

The original affected census failed R6 on one MEMORY_ERROR; the corrected scopes pass with
assertion-earned kills and no resource/run-error detections. The configured default gate passes
455 KILLED +13 SURVIVED /468 and exactly matches every baseline fingerprint/status. All listed local
validation and review gates are complete. CI and Sam's manual review/merge remain pending; this is
not a claim that the entire selected #12 workstream is complete. After publishing the 94C PR, report
its link and stop before 95 or CI watchers.

## Documentation and private-member review

Fresh-reader testing answered compilation/selection, current-vs-later support, strict replay/import,
fault/cutoff/observer/stop semantics, native/classpath compatibility and measured-vs-pending evidence.
It identified stale ledger/status prose, an overbroad unverified-measurement statement, missing root
CLI flags/imports, vague native migration guidance, inconsistent JSON size units and weak-memory
wording. These were corrected; the follow-up found no other gaps in those sections. Private-member
Javadoc passes all doclint groups with warnings as errors over all 27 changed production Java files.
This is documentation review, not a claim of independent runtime validation.

## Review disposition

Actual CodeRabbit CLI 0.8.1 completed the full base-commit comparison, including all 43 changed/new
files, with 9 findings (`coderabbit-host.ndjson`). The sandbox invocation failed to bind its callback;
required host execution completed normally. Findings retain the CLI's original severities:

- minor, browser literal-label assertion: valid; require accepted payload and exact literal label,
  alongside the no-image assertion, before checking conflicting-verdict rejection.
- minor, stale ledger checkpoint: valid; replaced the historical first-slice status with current
  connected evidence and explicitly pending gates.
- major, actual report row shape: already satisfied; ReportWriter embeds the complete TestResult
  shape plus singular representative aliases. The real deadlock report regression and fresh Java
  report/CLI normalization/browser artifacts pass. No absent metadata is fabricated.
- major, initial ALWAYS check: already satisfied; shared BytecodeObservations evaluates it at root.
  The public empty-choice failure regression and strict replay pass.
- trivial, runtime API example; trivial, coordinated loader/report/replay examples: valid; one fully
  qualified class-level example links loading with limits, execution, retained-trace JSON and matching
  replay/reporting. Public entry Javadoc refers to that example.
- minor, report deadlock verdict: not applied. Aggregate VIOLATION is the existing benchmark-row
  convention, and precise DEADLOCK plus concrete waits remain in the BYTECODE witness. Changing the
  aggregate would contradict the reviewed consumer contract and existing report behavior.
- major, empty TEXT model String: unreachable in this profile. The loader rejects String constants
  and String fields/operations; emitted TEXT values are nonempty phase/method/resource diagnostics.
  Wider String execution belongs to 97 and must revisit transport and batching there.
- major, newline-sensitive external consumer assertion: valid portability improvement; compare
  logical lines while retaining exit status and both exact expected terminal lines.

Ordinary connected-source review additionally found that the legacy BenchmarkResult copy helper
could drop BYTECODE evidence. It now rejects relabeling a typed bytecode row; a regression covers the
public copy route. Mixed legacy/BYTECODE report normalization retains the existing selected-row order.
Security review traces class input through the unchanged bounded metadata loader, JSON through the
strict bounded duplicate-free codec, and labels through textContent. No target reflection, native
fallback, HTML sink or generic callback execution was introduced. Connected post-fix verification passes.

### CodeRabbit follow-up

The second actual full-scope invocation completed 8 findings, including all 43 changed/new files:
`coderabbit-followup.ndjson`. Valid changes: correct stale review-status prose, accept absolute and
repository-relative BYTECODE fixture directories in the test harness, and classify import/replay
forgeries with exact public rejection types. The jar probe now uses Gradle's exact current library
artifact path, supplied to both test and PIT JVMs; both entry tasks depend on the library jar.
No mutation resource budget, operator, floor or assertion policy changed.

The default-FINAL finding is already satisfied by shared observation ordering and the real lost-update
CHECK_FALSE regression. Pure-check cancellation returns an INTERRUPTED evaluation (OpcodeExecutor
throws no cancellation); search records it and marks traversal incomplete while retaining prior
witnesses. Original PREFIX records already reject in BytecodeEvidence's constructor; PREFIX is allowed
only as a separate replay observation, with an added negative import case. The repeated aggregate
DEADLOCK suggestion is not applied for the existing report-contract reason above.

### Mutation resource failure and correction

The first affected census generated 4988 mutants:3956 KILLED,464 SURVIVED,567 NO_COVERAGE and1
MEMORY_ERROR. R6 correctly rejected the run. The error was BytecodeTraceJson.value's recursive array
value call removal (NON_VOID_METHOD_CALLS, instruction index108/block25). Removing that call stopped
reader progress while array growth continued; the old node charge lived inside the removed child.
Move the existing node charge before root/child descent and container growth, preserving exactly one
charge per JSON value. A flat one-million-null input independently checks the documented node ceiling.
The exact bound remains one million nodes; no timeout/heap setting was raised and no mutant is
reclassified by declaration. Only a passing assertion-earned rerun can replace the MEMORY_ERROR.

The corrected focused run passes R6:943 KILLED +134 SURVIVED +18 NO_COVERAGE /1095, with no
resource/run-error detections. All three recursive parser-value call removals, including the array
site that previously exhausted memory, are now KILLED by assertions. This is a scoped result, not a
whole-project percentage. Stronger public assertions independently verify compiler lines/BCIs, root
and terminal snapshot values, monitor identities/owners/depths, actor fuel, declared check timing,
outer result precision, and original-vs-replay external-prefix outcomes. The broad rejected census is
retained rather than overwritten. The final codec/DTO scope passes268 KILLED +59 SURVIVED +3
NO_COVERAGE /330; it includes strict rejection types, malformed control text and original-PREFIX
rejection. Neither scoped run is a whole-project percentage.

## Final comparison and transport correction

The configured default state/cb scope is not the whole checker. Its455 KILLED +13 SURVIVED /468
exactly matches baseline, including all mutant fingerprints. No tests, operators, resource limits,
mutation floors or R6 rules were weakened. The first default attempt was interrupted by a session
restart; its partial XML/log were archived and not scored. The completed rerun is `final-default.xml`.

For the affected comparison, the latest per-class observations replace whole classes from the broad
census with their post-fix scoped results. After the final report-only rerun this is 4013 KILLED +420
SURVIVED +560 NO_COVERAGE /4993. This is an explicitly assembled comparison dataset, **not a single
successful full-scope PIT run**, and was never fed to the build gate. Raw broad/focused reports remain
separate. Normalized javap comparison (constant-pool/BCI-width shifts excluded, control-flow targets
retained as instruction ordinals) verifies 2356 inherited kills in unchanged methods remain killed;
2 inherited survivors improve. The remaining 807 baseline kills occur in deliberately changed/moved
methods; no one-to-one mutant-identity claim is made for those. Their connected legacy and bytecode
behavior is covered by the full suite, independent instruction oracle and strict replay scenarios.
Residual survivors and uncovered cases are retained as residuals, without unsupported equivalence
claims. NO_COVERAGE, MEMORY_ERROR and timeouts are never counted as assertion-earned kills.

A final real class-file regression renames an unused int field to a valid UTF-16 name containing an
isolated surrogate. Generic Gson reparsing/reserialization in ReportWriter reintroduced the raw
surrogate before UTF-8 transport; the exported witness lost its field identity. A direct byte-transport
assertion failed (`report-utf16-assertion-red.log`). BYTECODE report rows now retain the already escaped
run/witness JSON and add only validated model-name/report metadata. The regression passes strict
replay after UTF-8 file transport (`report-utf16-green.log`). An earlier failing Files.writeString probe
stalled in Gradle's failure mapper; only that owned test worker was stopped, and the explicit data
integrity assertion replaced the probe. No interpreter execution hang is claimed.

## Final verification and reproducibility

| Check | Actual result | Evidence |
|---|---|---|
| Fresh baseline build / strict Javadoc | 2,066 tests, zero failures/errors/skips | `baseline-build.log` |
| Publication build / strict Javadoc | 2,085 tests, zero failures/errors/skips | `publication-build.log` |
| Post-mutation fresh producer regeneration / Javadoc | Pass; all tests rerun | `publication-fresh-artifacts.log` |
| Private-member doclint | All groups, warnings as errors, 27 changed production files | `final-private-javadoc.log` |
| Complete browser matrix | 299 assertions; zero failures/console errors | `publication-browser.log` |
| Configured default mutation scope | 455 KILLED +13 SURVIVED /468; exact baseline fingerprint/status match | `final-default.xml` |
| Corrected evidence/replay/result scope | 943 KILLED +134 SURVIVED +18 NO_COVERAGE /1095; R6 passes | `postfix-focused.xml` |
| Final codec/DTO scope | 268 KILLED +59 SURVIVED +3 NO_COVERAGE /330; R6 passes | `final-codec-dto.xml` |
| Final report scope | 231 KILLED +22 SURVIVED +6 NO_COVERAGE /259; R6 passes | `final-report-pit.xml` |
| Documentation | Six changed Markdown documents; 114 local targets/anchors; fences and R1–R5 mapping valid | `validate-docs.py` |
| Reviews | Two completed CodeRabbit invocations; ordinary/security and fresh-reader dispositions above | Saved NDJSON and review records |

The raw files and comparison scripts are retained in the persistent sibling `interleave-94c-evidence`.
`final-affected.xml` is labeled as an assembled comparison dataset; it is not an official build-gate
report. The committed [comparison helper](94C-compare-mutations.py) records unchanged-method checks and
changed/moved methods separately.
No residual is silently declared equivalent. No 94C runtime performance, broader-heap cost, DPOR
reduction or JPF measurement is invented. The unchanged 94B argument/default review remains the
applicable evidence for the admitted executor/scheduler behavior.

### Mutation scopes and test selection

Use JDK 26 and the existing pinned Gradle/PIT dependencies. The baseline was captured at
`6eb939ea19c3938db8052fc1b32d8f157fc913c7` in the untouched baseline checkout, before feature edits.
The later reports were captured at successive implementation snapshots. Running the same scopes
on a newer snapshot reproduces the procedure, **not necessarily the historical counts**. Exact
historical comparison requires the archived XML and matching compiled baseline/feature snapshots;
those raw artifacts remain in the local evidence directory rather than Git.

These are the recorded Gradle scopes. `targetTests` remains `dev.samhb.interleave.*` for **every PIT
run**, as configured in `build.gradle.kts`; no narrowed test selector or target-test override was used.
`-PpitestTargetOverride` changes production mutation targets only. Default state/cb is not the full
project. Save `build/reports/pitest/mutations.xml` after each completed run, before another PIT run
replaces it, using the filenames in the comments. Preserve the rejected census even though R6 fails.

```sh
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc

# Baseline checkout: baseline-default.xml
# Feature checkout: final-default.xml
./gradlew --max-workers=1 -PpitestThreads=1 pitest

# Baseline checkout: baseline-affected.xml (3,894 mutants)
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.bytecode.*,dev.samhb.interleave.InterleaveRunner*,dev.samhb.interleave.TestResult*,dev.samhb.interleave.TraceRecord*,dev.samhb.interleave.search.TraceReplayer*,dev.samhb.interleave.report.BenchmarkHarness*,dev.samhb.interleave.report.BenchmarkResult*,dev.samhb.interleave.report.ReportWriter*,dev.samhb.interleave.report.SoundnessAttestation*,dev.samhb.interleave.cli.Main*'

# Feature checkout: affected-census.xml (historical 4,988-mutant rejected census)
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.bytecode.*,dev.samhb.interleave.BytecodeTraceJson*,dev.samhb.interleave.ResultJson*,dev.samhb.interleave.core.BytecodeEvidence*,dev.samhb.interleave.InterleaveRunner*,dev.samhb.interleave.TestResult*,dev.samhb.interleave.TraceRecord*,dev.samhb.interleave.search.TraceReplayer*,dev.samhb.interleave.report.BenchmarkHarness*,dev.samhb.interleave.report.BenchmarkResult*,dev.samhb.interleave.report.ReportWriter*,dev.samhb.interleave.report.SoundnessAttestation*,dev.samhb.interleave.cli.Main*'

# Feature checkout: postfix-focused.xml (1,095-mutant evidence/replay/result scope)
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.BytecodeTraceJson*,dev.samhb.interleave.ResultJson*,dev.samhb.interleave.core.BytecodeEvidence*,dev.samhb.interleave.bytecode.BytecodeRuntime*,dev.samhb.interleave.bytecode.BytecodeReplay*,dev.samhb.interleave.bytecode.BytecodeSnapshots*,dev.samhb.interleave.bytecode.BytecodeObservations*,dev.samhb.interleave.TestResult*,dev.samhb.interleave.report.BenchmarkResult*'

# Feature checkout: final-codec-dto.xml (330-mutant scope)
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.BytecodeTraceJson*,dev.samhb.interleave.core.BytecodeEvidence*'

# Feature checkout: final-report-pit.xml (259-mutant scope)
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.report.ReportWriter*'
```

The public entry/replay checks and BYTECODE artifact producer also ran with these JUnit selectors.
They are separate test invocations, **not restrictions on PIT's test selection**:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 test --tests '*BytecodeReleaseTest' --tests '*BytecodeReplayTest'
./gradlew --max-workers=1 -PpitestThreads=1 test --tests '*BytecodeReleaseTest.bytecodeViewerUsesFreshProducerArtifacts'
```

### Accessible comparison procedure

The [portable helper](94C-compare-mutations.py) reads `baseline-default.xml`, `final-default.xml`,
`baseline-affected.xml`, `affected-census.xml`, `postfix-focused.xml`, `final-codec-dto.xml` and
`final-report-pit.xml` from the evidence directory. It requires Python 3.8+ and JDK 26 `javap`.
Supply the compiled class directories matching the compared snapshots; do not substitute another
checkout's classes just because its branch name matches.

```sh
python3 docs/measurements/94C-compare-mutations.py /path/to/interleave-94c-evidence /path/to/baseline/build/classes/java/main /path/to/feature/build/classes/java/main --javap /path/to/jdk26/bin/javap
```

It replaces **whole classes** in this order: affected census, evidence/replay/result scope, final
codec/DTO scope, final report scope. It writes `comparison-affected.xml` and `comparison-inherited.json`
as derived artifacts, leaving the raw reports unchanged. Default fingerprints/statuses must match
exactly. For inherited affected kills, it compares descriptor-complete normalized method code and
checks matching mutant identities only in unchanged bodies; changed/moved bodies are listed separately.
Duplicate fingerprints, missing reports, residual resource errors and lost comparable kills fail the
helper's assertions. The composite is never passed to `mutationRatchet` or represented as one PIT run.

The committed helper was checked against the archived inputs: default455/13, composite4013/420/560,
2356 unchanged-body inherited kills retained, zero lost comparable kills and2 survivor improvements.
Those checks do not establish equivalence of residual mutants or a one-to-one mapping for moved code.

For the browser matrix, regenerate the Java artifacts with the test selection/environment in
`.github/workflows/build.yml` after mutation testing, then run `npm test --prefix docs`. BYTECODE_DIR
accepts relative and absolute directories. The built-jar probe uses Gradle's exact current jar path
in normal test and PIT JVMs, avoiding stale or mutation-harness artifact selection.

### PR #141 minor-review follow-up

The unsupported-option matrix now loads the valid compiled fixture. Separate nonexistent-directory
DPOR/bitstate cases require the strategy/store diagnostic, proving rejection before loading.
The strengthened regression fails when the guard is bypassed in the isolated validation checkout
and passes after restoration and on the PR checkout (`pr141-cli-guard-red.log`,
`pr141-cli-restored-green.log`, `pr141-cli-guard-green.log`). No production code changed.
The committed comparison helper reproduces the archived counts and inherited checks above;
documentation links/fences and the exact scope/test-selection commands were checked. Routine
minor feedback verification did not rerun CodeRabbit or the historical mutation censuses.
