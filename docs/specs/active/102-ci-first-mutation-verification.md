<!-- Verification infrastructure: local/controller implementation; remote calibration and adoption pending. -->
# Spec 102: CI-first mutation verification

## Objective

Reduce local testing load and feedback time while preserving Interleave's mutation-testing capability
and acceptance requirements. Move complete affected mutation analyses to CI, introduce calibrated
class sharding, and retain focused local verification.

**Type: delta/refactor. Status: implementation PR in progress; remote calibration, adoption and
acceleration measurements pending.** See the [implementation ledger](../../measurements/102-ci-first-mutation-verification.md).
This is a separately selected infrastructure workstream. Spec 97 remains incomplete;
this spec neither implements its remaining behavior nor activates Specs 98–101. A specification
merge alone does not enable the new gate or establish a speedup.

## Scope

- Package/module: Interleave verification tooling and GitHub Actions.
- Create during implementation: `tools/mutation.py`, `tools/test_mutation.py`,
  the shared `tools/mutation_evidence.py` and `tools/mutation-inputs.init.gradle` adapters,
  `.github/workflows/affected-mutation.yml` and
  `docs/measurements/102-ci-first-mutation-verification.md`.
- Modify during implementation: `build.gradle.kts`, `AGENTS.md`, `.github/workflows/build.yml`,
  the affected workflow/tooling above and verification/status references introduced by this spec.
- Migrate reusable comparison logic from `docs/measurements/94C-compare-mutations.py`; preserve its
  documented invocation and historical-report reproduction. Do not create a competing comparator.
- Specification publication: this file, `docs/plans/ci-first-mutation-review.md`,
  `docs/plans/future-work.md`, `docs/plans/bytecode-contract.md`, and prospective verification
  references in Specs 97–101. Preserve completed milestone evidence.
- Off-limits: production interpreter, search, storage and consumer semantics owned by Specs 94A–101;
  existing regression assertions and the mutation accounting/floors owned by
  [Spec 12.06](12-mutation-hardening/06-mutation-ratchet.md). Existing `CONTEXT.md` owns the domain
  vocabulary; no additional domain document is introduced.

## Non-Goals

Whole-repository mutation expansion; PIT/Gradle/JUnit upgrades; incremental-history acceptance;
cached mutation verdicts; fixture-compilation caching; commercial accelerators or paid runners;
automatic removal of slow tests; production optimization; changed model-checking bounds; retroactive
correction of process deviations. Faster behavioral tests and fixture-input caching require later
measured, separately reviewable changes, retaining the original integration/oracle cases.

## Invariants

1. Every existing eligible covering test and configured mutation operator remains available.
2. Existing build, strict Javadoc, browser and default mutation gates remain enforced.
3. Only `KILLED` earns an assertion-backed kill. `SURVIVED` and `NO_COVERAGE` remain visible non-kills.
   The new affected gate accepts only those three statuses. The existing default ratchet retains
   Spec 12.06's status policy, including its adjudicated `EQUIVALENT` allow-list; an allowed equivalent
   still earns no assertion-backed kill. This spec adds no default-gate exception or policy change.
4. Scoped results identify their population and never substitute for a larger population's totals.
5. Every accepted report identifies immutable source revisions and its actual analysis protocol.
6. Independent JVM, generated-program, shrinking, replay and consumer tests retain their contracts.
7. Every local Gradle invocation includes `--max-workers=1 -PpitestThreads=1`. CI parallelism remains
   independently configured; do not put local serialization defaults into `gradle.properties`.

## Requirements

1. **R1 — Local feedback.** WHEN a developer requests local mutation feedback, THE SYSTEM SHALL accept
   explicit class selectors, retain the complete eligible test filter and operators, and label the
   result as scoped feedback. Empty selectors SHALL fail before Gradle starts.
2. **R2 — Local load.** WHEN the supported local mutation entry point runs, THE SYSTEM SHALL permit
   one cooperating Interleave PIT invocation across its worktrees, using one Gradle worker and one
   PIT minion. A competing invocation SHALL fail with an actionable message.
3. **R3 — Automatic acceptance.** WHEN a PR changes production/test Java, the mutation harness,
   Gradle configuration or verification tooling/workflows, THE SYSTEM SHALL run the affected remote
   mutation gate. Unrelated documentation-only changes SHALL not launch the affected census.
4. **R4 — Fresh pairs.** WHEN remote acceptance begins, THE SYSTEM SHALL run fresh baseline and
   candidate full build, strict Javadoc, tests, default PIT and affected PIT checks under one recorded
   analysis protocol. Candidate-only evidence SHALL not replace baseline checks. Restored PIT history
   or verdict reports SHALL not satisfy either run.
5. **R5 — Complete partitioning.** WHEN sharding is enabled, THE SYSTEM SHALL assign every in-scope
   compiled class to exactly one of four deterministic class-family shards, retaining nested-class
   grouping and identical eligible tests/operators across shards.
6. **R6 — Evidence validation.** WHEN reports are collected, THE SYSTEM SHALL require every planned
   analysis job to complete successfully and SHALL validate scope, provenance, report structure,
   engine totals and semantic mutant identities before aggregation.
7. **R7 — Honest comparison.** WHEN baseline and candidate results are compared, THE SYSTEM SHALL
   preserve inherited kills in unchanged compiled methods and separately report changed/moved
   methods, surviving identities and uncovered identities. Changed-method review remains a human
   acceptance obligation, recorded with dispositions in the implementation ledger and linked PR
   description before acceptance. An automated green comparison does not close unresolved review
   obligations or prove all inherited kills survive.
8. **R8 — Calibration.** WHEN sharding is proposed for acceptance, THE SYSTEM SHALL demonstrate
   equality against an unsharded reference on the same revision before enabling it. Measurements
   SHALL report actual elapsed time, runner consumption, memory and variability without invented
   speedup targets.
9. **R9 — Adoption.** WHEN this specification is implemented and calibrated, THE SYSTEM SHALL
   distinguish focused local feedback from complete remote acceptance and replace prospective
   archive-before-edit timing requirements with fresh, reproducible, SHA-pinned CI baselines.

## Failure Modes & Edge Cases

1. WHEN a planned job is missing, cancelled, timed out or unsuccessful, THE SYSTEM SHALL fail the
   affected gate; an `always()` collection job SHALL not turn failed dependencies into success.
2. WHEN XML is truncated, stale, malformed, unsupported or inconsistent with PIT's completed totals,
   THE SYSTEM SHALL reject it. A nonzero file size is not a completed report.
3. WHEN assignments overlap or mutant identities repeat, THE SYSTEM SHALL reject aggregation rather
   than deduplicate silently or select a favorable result.
4. WHEN provenance differs unexpectedly, THE SYSTEM SHALL reject comparison. Expected differences
   between baseline/candidate production and test inputs SHALL remain explicit.
5. WHEN a planned shard has no compiled target classes, THE SYSTEM SHALL record an explicit empty
   result. Missing mutation rows alone SHALL not establish an empty shard. A nonempty assignment
   yielding no mutations SHALL retain PIT's failing diagnostic, not fabricate a successful report.
6. WHEN calibration differs in identities or statuses, THE SYSTEM SHALL keep sharded acceptance
   disabled and retain complete unsharded remote verification.
7. WHEN the specified PR candidate is unavailable or conflicted, THE SYSTEM SHALL report the gate
   as blocked/failing rather than silently analyze a different revision.
8. WHEN the local wrapper is interrupted, THE SYSTEM SHALL terminate or account for its owned child
   before releasing its execution lock. Unexpected orphan-process evidence SHALL require diagnosis,
   not automatic lock removal and a second launch.
9. WHEN baseline and candidate protocols are incompatible, THE SYSTEM SHALL stop with their
   differences; no silent version downgrade, test-filter change or baseline source replacement.
10. WHEN any test input or runtime dependency changes, THE SYSTEM SHALL require a complete new affected
    population at that input revision before acceptance. A focused class rerun SHALL not mask a lost
    kill in another class by retaining its older test-revision result.

## Acceptance Criteria

- **AC1 (R1/R2):** Local execution preserves configured tests/operators, applies both required Gradle
  flags, rejects empty selections and a second cooperating invocation, and handles cancellation.
- **AC2 (R3/R4):** Relevant PRs launch fresh immutable pairs including full build/strict Javadoc/tests
  and separate default/affected PIT at each revision; unrelated documentation-only changes do not.
  Manual dispatch rejects unsupported revision relationships.
- **AC3 (R5/R6):** Planned class assignments are complete/disjoint; every job is accounted for;
  malformed, stale, overlapping, incomplete and inconsistent evidence fixtures fail validation.
- **AC4 (R7):** An intentionally lost unchanged-method kill fails comparison. Changed methods and
  whole-class replacement reports remain separately identified.
- **AC5 (R8):** One unsharded reference and two independently executed four-shard runs on the same
  revision contain identical semantic mutant identities and statuses. Record all runs, including
  discrepancies; do not retry until a favorable set can be selected.
- **AC6 (R8):** The measurement ledger contains actual resource/performance observations, conditions,
  raw evidence links and limitations. Investigate regressions before enabling sharded mode.
- **AC7 (R9):** Agent guidance and active bytecode clauses consistently describe the adopted gate;
  historical evidence and the Spec 97 timing deviation remain unchanged.
- **AC8:** Existing default mutation, build/Javadoc and browser checks remain operational. The
  infrastructure PR's draft status makes pending remote calibration explicit.

## Refactor Baseline & Rollback

- Pre-change baseline: verify remote main and record its exact SHA before implementation. Authoring
  observed `cccd74197530689df6cd58a2883d90b1b90af983`; this is not a future fresh baseline.
- Historical observations: completed local Spec 97 affected runs took approximately 89 and 96
  minutes. These are run observations, not controlled optimization measurements or CI baselines.
- Preserved invariants: mutation populations/operators/test eligibility, assertion-backed accounting,
  production semantics, consumer checks and historical command compatibility.
- Call-site migration: local agent commands, CI execution/collection, existing comparator invocation,
  measurement guidance and prospective verification clauses in Specs 97–101.
- Rollback criteria: omitted population coverage, unexplained status differences, provenance failures,
  resource errors, invalid aggregation or an uninvestigated performance/resource regression.
- Rollback behavior: retain/set the affected workflow's default mode to `full`, requiring complete
  unsharded remote analyses; preserve the existing default gate. Failed sharded calibration remains
  visible. Later fallback requires a separate fresh successful full analysis and an explicit reason;
  cancellation or partial reports do not satisfy it.

R9 changes prospective policy after adoption. It does not claim that Spec 97 met its original timing
requirement or replace its historical evidence. The dirty Spec 97 worktree and original checkout
remain untouched while this infrastructure is authored and implemented separately.

## Design

### Tooling and local execution

`tools/mutation.py` is verification-only, using Python's standard library:

- `local --classes <comma-separated selectors>`: scoped feedback with locking and both Gradle flags.
- `plan --base <sha> --candidate <sha> --base-classes <directory> --candidate-classes <directory>
  --output <manifest>`: plan assignments from separately compiled revision inventories.
- `validate --manifest <file> --reports <directory>`: validate execution evidence and aggregate it.
- `compare --baseline <directory> --candidate <directory>`: compare validated populations/keys.

Use an OS-backed lock under the canonical Git common directory, shared by the repository's
worktrees. This serializes cooperating wrapper invocations; it is not a system-wide scheduler and
does not claim to intercept arbitrary direct Gradle launches. Keep existing Gradle commands valid;
document the wrapper as the supported routine local workflow. Pass arguments as an argument vector,
not shell-evaluated selector text. No production Java/library/CLI/evidence API changes.

### Revision and protocol selection

Automatic PR checks use the event's immutable target-branch SHA as baseline and its corresponding
immutable PR merge revision as candidate. Record the PR head SHA separately and verify the merge
revision's relationship to the recorded base/head. Label results as merge-candidate evidence, not
branch-head evidence. A changed base/head requires a new pair; older runs cannot attest a new pair.

Manual dispatch uses the workflow on main and full base/candidate SHAs reachable from refs in this
repository. The base must be reachable from main and an ancestor of the candidate. Callers cannot
supply an alternate executable, workflow or arbitrary repository. Automatic fork PRs retain ordinary
`pull_request` isolation and their recorded head repository/ref; manual dispatch is same-repository.

A common controller runs outside the separate source checkouts and passes supported overrides to
each build. Never copy candidate production/test sources or build scripts over the baseline. Record
resolved task configuration at execution, not just requested command arguments. Both revisions must
use matching JDK/PIT/plugin/operator/test-selection/harness policy, while their source/test and
compiled hashes remain revision-specific. Calibration is required when analysis policy changes.

Each revision requires a fresh full build, strict Javadoc, test suite and separate default PIT report,
in addition to affected PIT. Run build/Javadoc/tests/default PIT once per revision, not once per shard.
The existing Build workflow calls the affected workflow as a reusable workflow after its candidate
build/default jobs. Those jobs export completion/provenance artifacts for reuse only when their
candidate SHA, inputs, protocol and required checks match exactly; otherwise run the missing checks.
Manual dispatch performs both revisions' checks itself. Always run baseline checks against its own
sources/tests; prior milestone counts or candidate-only checks are insufficient.

Use ordinary read-only permissions, pinned actions and standard hosted runners; no privileged
`pull_request_target` execution, new secrets, paid runners or permission changes. These checks establish
reproducible provenance under the reviewed CI workflow; they are not cryptographic attestation against
a malicious workflow author.

### Scope and scheduling

Freeze this named `bytecode-consumers` selector population for the initial affected gate:

```text
dev.samhb.interleave.bytecode.*
dev.samhb.interleave.BytecodeTraceJson*
dev.samhb.interleave.ResultJson*
dev.samhb.interleave.core.BytecodeEvidence*
dev.samhb.interleave.InterleaveRunner*
dev.samhb.interleave.TestResult*
dev.samhb.interleave.TraceRecord*
dev.samhb.interleave.search.TraceReplayer*
dev.samhb.interleave.report.BenchmarkHarness*
dev.samhb.interleave.report.BenchmarkResult*
dev.samhb.interleave.report.ReportWriter*
dev.samhb.interleave.report.SoundnessAttestation*
dev.samhb.interleave.cli.Main*
```

Keep `targetTests=dev.samhb.interleave.*`, all current operators and the existing mutation harness.
Expand selectors against each revision's actual compiled inventory. Group binary names by the prefix
before the first `$`; sort families and assign the first eight SHA-256 digest bytes, interpreted as
an unsigned big-endian integer, modulo four. A new/missing class is explicit in its revision's
inventory. Validate inventory union/disjointness independently of report rows. Classes with no
eligible mutants need not appear in XML; absence alone proves neither omission nor completion.

Run at most four analysis jobs concurrently, initially one PIT minion per job, with a 180-minute job
timeout. Preserve existing per-test PIT timeout/error policy and the independently parallel existing
CI jobs. Support `full` and `sharded` execution modes; default to `full` until calibration passes and
its evidence is manually reviewed. The four-way hash is an initial partition, not a measured balance
claim. A later partition change requires recalibration.

Run the affected gate on changes under `src/main/java/`, `src/test/java/`, `src/test/resources/`,
`src/mutationHarness/`, `tools/`, `gradle/`, the Gradle wrapper/settings/properties/build files and
`.github/workflows/`. Compute the changed-path decision from the immutable PR pair. Always expose
an affected-gate summary: a documentation-only skip must state the checked revisions/reason, not
claim a completed mutation run. Do not alter repository branch protection in this workstream.

### Evidence validation and comparison

Use a version-1 JSON manifest containing source/controller revisions, mode/shard identity, planned
class lists, resolved class/test selectors, toolchain/operator/harness/dependency identities and
compiled-class fingerprints. Record completion status, PIT summary, XML and logs per job. The
controller creates the plan; validation compares worker observations with the plan and each other.
Unknown manifest versions, absent fields and unexpected revision/protocol mismatches fail closed.
Completion is committed only after artifact collection and validation succeed; a zero Gradle exit
alone cannot mark the execution complete. Build evidence records observed build/Javadoc/test task
outcomes, rejecting skipped, no-source, missing or failed checks.

Begin with an empty report directory. Require successful Gradle completion, PIT's completed summary,
the expected resolved selectors and closed XML whose generated/status totals agree with that summary.
For a nonempty successful mutation report, require a `mutations` root and at least one valid mutation
row. Preserve the pinned PIT XML shape; required fields are class, method, descriptor, mutator,
instruction indexes, blocks and status. Incompatible future shapes need an explicit reviewed adapter.
Reject XML DTD/entity declarations and duplicate identities before aggregation. These are real
execution/completeness checks, not an inference from class inventory or a file existing.

Semantic identity is `(class, method, descriptor, mutator, ordered indexes, ordered blocks)`; source
line numbers are diagnostic only. Accept only `KILLED`, `SURVIVED` and `NO_COVERAGE` for successful
affected populations. The separate default population retains Spec 12.06's status policy. Sum counts,
never percentages. Keep raw reports immutable. Acceptance aggregates require identical full production,
compiled-test, test-resource and runtime-dependency inputs within each revision, plus matching protocol.
Whole-class replacement reports may replace all rows of a class only under those identical inputs,
never selected favorable rows. A test edit requires a fresh complete affected population; focused
replacements after test edits remain diagnostic and are excluded from acceptance aggregates. Baseline
and candidate may differ in test inputs, but each must have its own internally consistent census.
Record effective PIT features in their actual order, both coordinator and minion JDK identities,
and ordered launch/test classpaths, including directory/file interleaving. Bind the actual harness
agent and release JAR's logical entry contents (including manifests), ignoring only ZIP timestamps
and entry order. Input comparison includes declared empty classpath directories.

Deepen the existing compiled-method comparison rather than copy its current parser unchanged. Retain
descriptors, executable instructions, constant values/member references and handlers; normalize only
actual constant-pool operand indexes, `ldc` widths and branch/handler BCIs to instruction positions.
Ignore debug values in method-body comparison. Never apply global `#digits` replacement to resolved string contents: `"#12"`
and `"#34"` must remain different constants. A pool-index relocation with unchanged contents must
remain comparable. Compare the union of both compiled class/method inventories, including candidate-only
classes/methods and synthetic methods. Added, changed, removed or moved methods are unmatched, reported
with their available mutation counts and require R7's human disposition, including zero-mutant methods.
Only methods present and understood in both revisions can establish inherited-kill preservation.

Method-body correspondence alone does not prove PIT coordinate correspondence. Raw PIT indexes can
count ASM line/frame/label nodes, so moved debug nodes can change identities without changing the
executable instructions. Establish coordinate stability separately before claiming a lost or preserved
kill. The initial adapter proves raw coordinates only for identical defining class bytes under the
same PIT protocol. Pool relocations still compare equal at the normalized body level, but without a
proved coordinate mapping their mutant correspondence remains explicitly `unresolved-coordinates`,
requiring human disposition. Do not guess correspondence by matching raw indexes across changed
classes. Javap may omit literal trailing whitespace; rendered constants alone cannot prove equality.

Retain declaration modifiers such as `synchronized`, static/native flags and access changes in the
comparison. A javap NaN rendering does not expose its raw constant-pool payload; treat that method as
unmatched rather than infer equality from the rendered text.

If the chosen disassembly omits behavior-relevant bootstrap/constant-dynamic data or a construct cannot
be resolved safely, classify the method as unmatched with a reason. Do not infer equality from identical
`invokedynamic` descriptors while ignoring changed bootstrap recipes. The initial adapter may conservatively
leave such methods unmatched; broader normalization is not required to enable calibrated execution.

Keep dependency caches. Never restore PIT history/verdict reports as fresh acceptance evidence.
Upload logs/manifests/XML/compiled inputs even after failures; artifact upload does not confer success.
Retain artifacts for 30 days and commit a concise measurement ledger with revision/run links, counts,
residual identities, calibration outcomes and resource/performance observations.

### Measurements and rollout

Perform one unsharded reference and two fresh sharded executions on one exact revision using the same
protocol and no PIT history. Compare semantic identities/statuses exactly. Record elapsed time,
aggregate runner time, peak resident memory where supported, runner/JDK conditions, shard duration
imbalance, initial coverage cost and observed variability. Label unavailable metrics unavailable;
heap limits are not resident-memory measurements. Do not claim a general speedup from one population.

Only enable sharding after AC5–AC8 pass and Sam reviews the calibration evidence. Initial infrastructure
CI may collect calibration while the PR remains draft. Publish its link and stop; Sam supplies CI and
review results. Do not begin a watcher. Later test optimization must retain original regressions and
use measured expensive paths; it is not bundled into this infrastructure implementation.

## Current State

- [verified] Authoring remote main is `cccd74197530689df6cd58a2883d90b1b90af983`; 102 was available.
- [verified] Default PIT targets `state.*` and `cb.*`. The broad bytecode/consumer census is distinct.
- [verified] `pitestTargetOverride` changes mutation classes while broad eligible tests remain enabled.
- [verified] PIT 1.30.0, Gradle PIT plugin 1.19.0 and JUnit plugin 1.2.3 are configured; history is off.
- [verified] The plugin exposes existing overrides but not every engine tuning option. This design
  requires neither an invented `mutationUnitSize` property nor a version change.
- [verified] The original and Spec 97 worktrees contain uncommitted work and remain preserved.
- [verified] Existing domain vocabulary belongs to `CONTEXT.md`.
- [verified] Local implementation includes the supported controller/wrapper, common resolved-input
  exporter, shared historical/CI comparator and Python checks; workflow syntax validation passes.
- [unverified] Four shards' performance, balance and status stability on GitHub runners.
- [unverified] Whether fixture compilation or individual integration tests dominate mutant execution.
- [unverified] Remote execution, complete CI acceptance, acceleration, reduced memory consumption and
  successful calibration. Local parser/process fixtures do not establish these results.
- [deferred] Incremental-history acceptance, fixture caching and commercial acceleration.

## Tests

Proposed names in `tools/test_mutation.py`, using standard-library `unittest`:

- `test_local_preserves_protocol_and_limits` (R1/R2).
- `test_local_lock_rejects_overlap_and_handles_interrupt` (R2).
- `test_pr_revision_and_scope_selection` (R3/R4).
- `test_partition_is_complete_disjoint_and_deterministic` (R5).
- `test_nested_classes_share_a_shard` (R5).
- `test_missing_duplicate_stale_and_truncated_reports_fail` (R6).
- `test_wrong_revision_protocol_or_engine_total_fails` (R6).
- `test_unknown_and_resource_statuses_fail` (R6).
- `test_aggregate_sums_counts_instead_of_percentages` (R6).
- `test_lost_unchanged_method_kill_fails` (R7).
- `test_changed_methods_and_class_replacements_remain_explicit` (R7).
- `test_pool_index_normalization_preserves_hash_digit_string_contents` (R7): `"#12"` versus `"#34"`
  is changed; relocating a real pool operand with unchanged contents is comparable.
- `test_candidate_only_classes_and_methods_require_disposition` (R7).
- `test_unresolved_bootstrap_data_is_unmatched` (R7).
- `test_changed_tests_cannot_reuse_another_class_old_kills` (R6/R7): two classes share a test; after a
  test edit, rerunning A cannot retain B's old kill. Reject mixed inputs and require a new full census.
- `test_baseline_checks_cannot_be_replaced_by_candidate_results` (R4).
- `test_candidate_check_reuse_requires_exact_provenance` (R4/R6).
- `test_calibration_mismatch_keeps_full_mode` (R8).

Use fixture subprocesses for wrapper lifecycle tests, not a real heavy PIT job. Small compiled-method
fixtures exercise constant-pool/BCI/debug changes and real changed instructions. High-risk defects
include omitted-job validation, duplicate acceptance, inverted status checks, summing percentages and
removed inherited-kill checks. Derive literal partition/count expectations independently, per AGENTS.md.
Python tool tests join ordinary CI; full PIT calibration is separate evidence, not mocked execution.

## Constraints

- Dependencies: Spec 12.06's ratchet and the existing comparison machinery; no dependency on completed
  Spec 97 implementation. Keep the shared bytecode verification/oracle contracts intact.
- Ordering: specification-only PR, then separate infrastructure implementation, then resume Spec 97
  after Sam's merge update. Specs 98–101 and weak-memory work remain parked.
- Compatibility: preserve existing commands, historical comparator invocation and report reproduction;
  adopt the new gate prospectively after implementation/calibration, not upon specification merge.
- Oracle contract: a fresh unsharded run is the shard-calibration reference. Synthetic malformed
  evidence tests separately validate tooling; they do not establish real PIT completeness or speed.
- Skills/workflow: use doc-coauthoring and fresh-reader testing for the spec; codebase-design,
  domain-modeling using the existing owner, ordinary review and relevant security review for tooling.
  Use meaningful behavioral tests for nontrivial logic and preserve distinct regressions.
- Review gates: run actual CodeRabbit CLI after completing this specification and for substantial
  implementation. Address valid findings and record actual completion/quota/error through
  `agent-setup review-record`; ordinary review cannot replace required CLI evidence. Do not purchase
  credits, change review configuration or fabricate a completed review.
- Specification publication: validate structure, relative links, configuration where changed and
  whitespace; record reviews/skips in the linked authoring record. No heavy Gradle/PIT runs are
  required for this documentation-only bundle.
- Implementation publication: complete appropriate tool tests, integrated build/strict Javadoc,
  default PIT and relevant compatibility checks, with remote affected/calibration status explicit.
  Local runs always use both required flags; avoid repeating unchanged gates without a cause.
- Include `Co-authored-by: Codex <noreply@openai.com>`. After each PR publication, report its link and
  stop. Sam supplies CI/review/merge updates; no watcher or next milestone starts automatically.

## Context

- [PIT coverage-driven test selection](https://pitest.org/quickstart/basic_concepts/).
- [PIT performance guidance](https://pitest.org/faq/).
- [Incremental-history dependency limitations](https://pitest.org/quickstart/incremental_analysis/).
- [Pinned Gradle plugin task implementation](https://github.com/szpak/gradle-pitest-plugin/blob/release/1.19.0/src/main/groovy/info/solidsoft/gradle/pitest/PitestTask.groovy).
- [GitHub bounded matrix execution](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/run-job-variations).
- [Specification authoring/review evidence](../../plans/ci-first-mutation-review.md).

Incremental history, cheap behavioral killers, fixture-input caching and commercial acceleration
were evaluated as alternatives. History's incomplete dependency invalidation prevents fresh-gate
equivalence; the others require measured follow-up work. No alternative contributes invented savings.
