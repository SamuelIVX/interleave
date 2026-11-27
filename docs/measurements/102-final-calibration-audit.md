<!-- Completed CI evidence audit for Spec 102; full acceptance mode remains active. -->
# Spec 102: final CI calibration audit

## Decision and scope

The final [PR #145](https://github.com/SamuelIVX/interleave/pull/145) run passed full paired acceptance
and both exact calibration trials. Independent revalidation of its retained artifacts reproduces the
uploaded aggregates and comparison. D1 is a **post-merge** human disposition: it was not recorded
before PR #145 merged, so this audit does not establish that its manual pre-acceptance obligation
was completed on time. The bootstrap/constant obligations are now dispositioned below using
complete archived class bytes and raw results, without broadening the automated comparator.

Keep **full** acceptance mode. Calibration correctness is established for this revision/profile;
sharded acceptance and prospective baseline-policy adoption still require Sam's review. This audit
changes no workflow, interpreter behavior, resource limits, tests or mutation policy. Spec 97 remains
unfinished; no later bytecode milestone is activated by this record.

## Immutable evidence

- [Build run 45942608351](https://github.com/SamuelIVX/interleave/actions/runs/45942608351), head
  `d5f35e3f2aacf518a43e4ffb08c3798e0f31b920`.
- Baseline: `b12c888190c098170971993d7b0ca98f4832945f`, including prerequisite PR #146.
- Candidate/controller: merge candidate `fb14703ff8c4fe1fe490faa696e6a15b5cfe277b`.
  PR #145 later merged as `e3a9afeea7b4898acd77545af61e43e3be6dd8ea`; this is not a claim that
  the measured run executed the squash-merge SHA.
- Validated artifact `15181575756` (`mutation-validated`), SHA-256
  `99d4193d54bfe6ff9b795b28fa6c5ad53ea9aabd1f301f110f11a17c4a076874`.
  It contains `validated/comparison.json`, both aggregates/classes and `measurements.json`.
- Preparation artifact `15173789336` (`mutation-prepared`) contains the exact plan, build/default
  records and logs. Ten `mutation-analysis-*` artifacts retain two full reports and eight shards.
  All were available at audit time; these artifacts expire on 2026-12-27. The
  [durable identity appendix](102-final-calibration-identities.md) retains every non-kill identity and
  unmatched method after that expiry.

Build, default mutation ratchet, browser compatibility, preparation, all ten affected executions and
final validation jobs succeeded. Secrets, dependency and CodeQL PR workflows also reported success.
Recorded build tasks include executed tests and strict Javadoc at both revisions; no skipped task
was promoted to acceptance. Coordinator and minion report Eclipse Adoptium `26.0.2.1+1`; runners are
GitHub-hosted `ubuntu-latest`. This label does not establish identical physical machines or load.

## Exact outcomes

| Population | KILLED | SURVIVED | NO_COVERAGE | Total |
|---|---:|---:|---:|---:|
| Baseline affected full | 5709 | 781 | 604 | 7094 |
| Candidate affected full | 5709 | 781 | 604 | 7094 |
| Candidate calibration 1 union | 5709 | 781 | 604 | 7094 |
| Candidate calibration 2 union | 5709 | 781 | 604 | 7094 |
| Baseline default | 455 | 13 | 0 | 468 |
| Candidate default | 455 | 13 | 0 | 468 |

Each shard union equals the candidate reference in every raw identity and status, with disjoint
assignments and complete reports. There are no timed-out/error rows. Baseline and candidate raw
maps also agree exactly in each population. Affected and default are separate scopes; neither is a
whole-project score. NO_COVERAGE and SURVIVED receive no assertion-backed kill credit. The default
ratchet's existing equivalent-mutant treatment is unchanged.

The [earlier failed run](bytecode-mutation-progress.md) remains failed historical evidence.
This run uses the corrected common harness and is not a selective retry of the old shards. Both
required new trials are included. The removed-pop and zero-deadline boundary failures were not waived.

## Comparison obligations and disposition

The automated adapter proves 2703 affected and 334 default inherited kills, with zero lost kills.
It conservatively leaves 273 affected methods (4005 mutants) and eight default methods (125 mutants)
`unresolved-bootstrap-or-constant`. Of those affected methods, 157 have zero mutants. They remain
explicit obligations; an empty mutant list does not remove a method from review.

Disposition **D1** applies to every method listed in the retained `comparison.json` unmatched arrays,
including the zero-mutant methods and every class summarized below. Exact method descriptors and
all survivor/uncovered identities are retained in the [identity appendix](102-final-calibration-identities.md):

1. Independently compare all 312 archived production `.class` files byte for byte, with identical
   inventories. All match. This includes complete constant pools, BootstrapMethods, debug nodes,
   synthetic methods and executable definitions, not only javap's rendered method bodies.
2. Compare complete recorded runtime inputs and PIT protocols across baseline/candidate. Both match,
   including the corrected harness, ordered test/launch classpaths, arguments and actual JDK identities.
3. Compare all raw mutation identity/status maps at both revisions: all 7094 affected rows and all
   468 default rows match exactly. The unmatched subsets therefore retain 3006 affected and 121 default
   kills as well as all non-kill statuses. The zero-mutant methods are unchanged complete definitions.

For this byte-identical pair, there is no unresolved changed behavior or lost raw outcome in these
methods. D1 resolves the human preservation obligation for this run; it does not teach the adapter
new normalization rules or establish correspondence across changed bootstrap/constant bytes. Future
changed or unproven methods still require their own disposition. The original adapter output remains
unchanged, with its unresolved classifications visible.

| Population / class (relative to `dev.samhb.interleave`) | Methods under D1 | Zero-mutant methods | Mutants |
|---|---:|---:|---:|
| affected / `BytecodeTraceJson` | 3 | 0 | 93 |
| affected / `InterleaveRunner` | 4 | 0 | 80 |
| affected / `InterleaveRunner$1` | 2 | 0 | 39 |
| affected / `InterleaveRunner$Builder` | 3 | 1 | 18 |
| affected / `TestResult` | 1 | 0 | 10 |
| affected / `bytecode.BytecodeAccessVerifier` | 2 | 0 | 218 |
| affected / `bytecode.BytecodeArrays` | 1 | 0 | 80 |
| affected / `bytecode.BytecodeChecks$Evaluation` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeChecks$Failure` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeExplorer` | 2 | 0 | 91 |
| affected / `bytecode.BytecodeExplorer$Budget` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeExplorer$Cell` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeExplorer$Metrics` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeExplorer$Outcome` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeExplorer$Path` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeExplorer$Result` | 4 | 3 | 5 |
| affected / `bytecode.BytecodeExplorer$Selection` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeExplorer$Wait` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeExplorer$Witness` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeHierarchy` | 10 | 0 | 164 |
| affected / `bytecode.BytecodeLimits` | 4 | 3 | 6 |
| affected / `bytecode.BytecodeLoadException` | 1 | 0 | 2 |
| affected / `bytecode.BytecodeLoader` | 14 | 0 | 802 |
| affected / `bytecode.BytecodeLoader$Work` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeLocation` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeMethod` | 1 | 0 | 40 |
| affected / `bytecode.BytecodeMethod$Id` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeMethod$Op` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeObservations` | 3 | 0 | 66 |
| affected / `bytecode.BytecodeObservations$Evaluation` | 3 | 3 | 0 |
| affected / `bytecode.BytecodePlatform` | 2 | 0 | 68 |
| affected / `bytecode.BytecodeProgram$Check` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeProgram$Field` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeProgram$FieldId` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeReplay` | 2 | 0 | 116 |
| affected / `bytecode.BytecodeRuntime` | 4 | 0 | 71 |
| affected / `bytecode.BytecodeScheduler$Executed` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeScheduler$Transition` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeSharing` | 2 | 0 | 90 |
| affected / `bytecode.BytecodeSharing$Work` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeSnapshots` | 14 | 0 | 175 |
| affected / `bytecode.BytecodeState` | 5 | 0 | 68 |
| affected / `bytecode.BytecodeState$Cutoff` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeState$Execution` | 1 | 0 | 2 |
| affected / `bytecode.BytecodeState$Fault` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeState$Int` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeState$Literal` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeState$Ref` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeState$Thrown` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeState$WideZero` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeStrings` | 3 | 0 | 51 |
| affected / `bytecode.BytecodeThrowables` | 3 | 0 | 134 |
| affected / `bytecode.BytecodeThrowables$Body` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeThrowables$Call` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeThrowables$Payload` | 3 | 3 | 0 |
| affected / `bytecode.BytecodeVerifier` | 3 | 0 | 254 |
| affected / `bytecode.OpcodeExecutor` | 6 | 0 | 333 |
| affected / `bytecode.OpcodeExecutor$Probe` | 3 | 3 | 0 |
| affected / `bytecode.OpcodeExecutor$Result` | 3 | 3 | 0 |
| affected / `cli.Main` | 5 | 0 | 415 |
| affected / `core.BytecodeEvidence` | 4 | 3 | 35 |
| affected / `core.BytecodeEvidence$Actor` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Check` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$CheckFailure` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Cutoff` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Fault` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Identity` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Method` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$ObjectState` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Operation` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Snapshot` | 4 | 3 | 5 |
| affected / `core.BytecodeEvidence$Terminal` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Transition` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Value` | 3 | 3 | 0 |
| affected / `core.BytecodeEvidence$Wait` | 3 | 3 | 0 |
| affected / `report.BenchmarkHarness` | 8 | 0 | 164 |
| affected / `report.BenchmarkHarness$CbRun` | 3 | 3 | 0 |
| affected / `report.BenchmarkHarness$DfsResultWithTiming` | 3 | 3 | 0 |
| affected / `report.BenchmarkResult` | 2 | 0 | 28 |
| affected / `report.ReportWriter` | 1 | 0 | 40 |
| affected / `report.SoundnessAttestation` | 2 | 0 | 143 |
| affected / `search.TraceReplayer` | 3 | 0 | 99 |
| default / `cb.ContextBoundedExplorer` | 2 | 0 | 28 |
| default / `state.BitstateStore` | 1 | 0 | 4 |
| default / `state.CanonicalEncoder` | 2 | 0 | 12 |
| default / `state.HashingStateStore` | 1 | 0 | 6 |
| default / `state.ThreadSymmetryCanonicalizer` | 2 | 0 | 75 |

## Measured execution costs

Values below are each wrapper's elapsed seconds, including its Gradle launch/export work. They are
not end-to-end PR latency or simultaneous process-tree CPU/memory measurements. Affected jobs use
one PIT minion; default jobs use three. The workflow allows at most four affected jobs concurrently,
so full references and calibration jobs also contend for scheduling slots.

| Execution | Elapsed seconds | Coverage seconds (PIT log) |
|---|---:|---:|
| baseline acceptance / all | 7458.158 | 115 |
| candidate acceptance / all | 8440.864 | 105 |
| candidate calibration-1 / 0 | 2339.874 | 132 |
| candidate calibration-1 / 1 | 682.094 | 143 |
| candidate calibration-1 / 2 | 2094.711 | 138 |
| candidate calibration-1 / 3 | 3219.792 | 94 |
| candidate calibration-2 / 0 | 1569.602 | 67 |
| candidate calibration-2 / 1 | 527.250 | 109 |
| candidate calibration-2 / 2 | 1699.347 | 110 |
| candidate calibration-2 / 3 | 4045.659 | 137 |

Calibration 1 totals 8336.471 worker-seconds, with a longest shard of 3219.792 seconds and a
4.720 longest/shortest ratio. Calibration 2 totals 7841.858 worker-seconds, with a longest shard of
4045.659 seconds and a 7.673 ratio. Shard 3 dominates both trials. Repeated coverage collection costs
507 seconds in trial 1 and 423 seconds in trial 2, versus 105 seconds in the candidate full run.
The candidate full run took 8440.864 seconds; baseline full took 7458.158 seconds despite matching
inputs and outcomes. These observations show substantial runner/run variability and partition
imbalance. Do not turn the maximum shard duration into a claimed delivered PR speedup: actual jobs
were staggered and competed with references and the other trial under the four-job limit.

Baseline/candidate build preflights took 147.033/155.740 seconds; default PIT took 689.537/687.053
seconds. Calibration totals are sums of worker wrapper intervals, not billable minutes or measured
CPU time. Peak process-tree RSS is null in every record, so no memory saving is established. There
is no local hardware before/after experiment or additional throughput target in this audit.

## Audit verification and remaining work

The audit downloaded all run artifacts without launching Gradle/PIT, reconstructed all 14 execution
records, reran `tools/mutation.py validate`, then reran `compare` using JDK 26 javap. Both aggregates
and the comparison match the uploaded validated JSON. Independent byte comparison and exact raw
map/input/protocol assertions also pass. External working evidence resides in
`interleave-102-evidence/pr145-final-ci`, including `audit-summary.json`, `audit-validated` and
`audit-comparison.json`; it is not a new PIT execution or an acceptance source mixed from revisions.

CodeRabbit0.8.1 completed the actual three-file review with two minor findings: identify D1 as
post-merge (rather than imply timely pre-merge acceptance), and preserve exact residual identities
beyond artifact expiry. Both are addressed here and in the decoded/validated identity appendix.
An independent reader verified revision attribution, disposition scope and adoption/performance
limitations; its stale-status findings were corrected. No new CLI execution is claimed for those
localized follow-ups.

Sam can now review correctness, D1, the timing deviation and the measurement limitations. Sharding remains disabled pending
that review. If adopted, keep class grouping/profile unchanged or recalibrate after repartitioning;
prospective baseline guidance must change consistently in its own reviewed change. No historical
Spec 97 timing deviation is rewritten. After publishing this evidence-only PR, stop; no CI watcher
or Spec 97 work follows automatically.
