# Spec: Replay contract hardening

## Objective

Close the meaningful replay-test gaps recorded under roadmap 9-H1 and the overlapping
TraceReplayer portion of 9-V1. Preserve execution and evidence semantics while distinguishing
observable gaps from mutations that cannot affect compiler-produced models.

## Scope

- Package: `dev.samhb.interleave.search` and source replay tests in `dev.samhb.interleave.format`.
- Modifies: `TraceReplayerTest`, `SourceEvidenceTest`, this specification and the future-work roadmap.
- Off-limits: production execution/reduction algorithms, public APIs, dependencies, CI settings,
  mutation operators, mutation exclusions and acceptance thresholds.

## Non-Goals

JSON escaping, broader state/CBS/compiler mutation hardening, trace construction optimization,
cutoff-witness simplification and new modeling features belong to separate PRs. Do not force
100% mutation coverage with synthetic source-step implementations or implementation-only assertions.

## Invariants

- Legacy replay retains its existing execution and snapshot behavior.
- Plain source replay validates execution and supplied step-failure evidence. Invariant-aware
  replay additionally validates terminal classification and property evidence.
- Scheduling cutoffs identify an enabled omitted instruction; their prefix fits the bound and
  the omitted voluntary switch exceeds it. Loop witnesses retain their separate validation.
- Snapshot enrichment validates supplied source evidence before publishing snapshots.
- Mutation kills require assertion failures; timeout and infrastructure failures do not count.

## Requirements

1. WHEN legacy snapshot replay selects a recorded thread, THE SYSTEM SHALL execute that thread's
   current instruction and capture its actual committed state.
2. WHEN a recorded snapshot outcome disagrees with execution, THE SYSTEM SHALL reject it with
   diagnostics identifying the recorded and actual outcomes.
3. WHEN source enrichment receives stale source identity, THE SYSTEM SHALL reject it; authentic
   enrichment SHALL preserve evidence and capture the initial and committed states.
4. WHEN plain source replay receives a forged STEP failure category, THE SYSTEM SHALL reject it
   even without an invariant, while authentic runtime-error replay preserves committed state.
5. WHEN an invariant is supplied, THE SYSTEM SHALL accept authentic preemption witnesses at an
   advanced instruction and reject terminal classifications that do not reproduce.
6. WHEN classifying replay mutations, THE SYSTEM SHALL retain semantic identities and distinguish
   measured kills, remaining survivors and scope-limited equivalence reasoning.

## Acceptance Criteria

- Each requirement has a named regression or documented mutation disposition below.
- All six baseline gaps receive a disposition. No previously killed replay mutation regresses.
- Focused replay tests, full tests/build/Javadoc, the default PIT ratchet and visualizer
  normalization pass. Record replay-only and default-scope counts separately.
- CodeRabbit CLI completes review of the entire PR; valid feedback is verified and addressed.
- No production behavior, public interface or mutation gate changes are required.

## Design

Use existing typed counter steps for legacy snapshot cases. Generate source evidence through the
loader and explorers, then change one evidence field at a time. For terminal verdict validation,
compare plain and invariant-aware replay against the same real completion trace. Positive
preemption replay supplies a real invariant and uses a CBS-generated witness after a forced switch.

## Current State

- [verified] Baseline `a0658a2`: clean test run passes 644 invocations, none skipped.
- [verified] Fresh replay-only PIT: 197 KILLED / 203 total, 4 SURVIVED, 2 NO_COVERAGE,
  zero timeout/error detections. The scoped mutation ratchet passes.
- [verified] `TraceReplayer` callers include enrichment, reporting, runner snapshots, minimization
  and soundness attestation; minimization/attestation already supply invariants.
- [verified] `SourceInstruction.metadata(pc)` stores `pc` only in the returned instruction index;
  source span, label and expansion ancestry derive from its immutable node and source text.

## Tests

| Requirement | Regression |
|---|---|
| R1 | `TraceReplayerTest.captureSnapshots_usesRecordedThread` |
| R2 | `TraceReplayerTest.captureSnapshots_divergentOutcome_reportsMismatch` |
| R3 | `SourceEvidenceTest.enrichmentRejectsStaleSourceEvidence` |
| R4 | `SourceEvidenceTest.plainReplayRejectsForgedStepFailureCategory` |
| R5 | `SourceEvidenceTest.schedulingWitnessUsesAdvancedInstructionMetadataWithInvariant` |
| R5 | `SourceEvidenceTest.terminalVerdictValidationRequiresTheInvariant` |

### Baseline mutation ledger

All entries belong to `dev.samhb.interleave.search.TraceReplayer` and use
`NonVoidMethodCallMutator`. Identify by method, JVM descriptor, instruction index and block,
not source line. Descriptors are listed separately to keep the table readable.

- `captureSnapshots`: `(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/search/Trace;)Ljava/util/List;`
- `enrich`: `(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/TraceRecord;)Ldev/samhb/interleave/TraceRecord;`
- `sourceExecution`: `(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/search/Trace;Ldev/samhb/interleave/search/Invariant;)Ldev/samhb/interleave/core/ScheduleExecution;`
- `validateCutoff`: `(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/search/Trace;Ldev/samhb/interleave/core/Configuration;)V`

| Method | Index / block | Removed call | Baseline | Targeted contract / disposition |
|---|---|---|---|---|
| captureSnapshots | 51 / 16 | Integer.intValue | SURVIVED | R1: recorded thread 1 must execute; replacing its ID with zero changes the counter trajectory. |
| captureSnapshots | 105 / 29 | String.valueOf | NO_COVERAGE | R2: mismatch diagnostic retains recorded BLOCKED. |
| captureSnapshots | 107 / 30 | String.valueOf | NO_COVERAGE | R2: mismatch diagnostic retains actual ADVANCED. |
| enrich | 12 / 3 | TraceRecord.evidence | SURVIVED | R3: dropping evidence must not allow a stale digest to pass enrichment. |
| sourceExecution | 88 / 36 | FailureEvidence.phase | SURVIVED | R4: a STEP category must be checked without an invariant. |
| validateCutoff | 81 / 30 | CutoffEvidence.instructionIndex | SURVIVED | Scoped equivalence candidate: only the argument to metadata is zeroed; selection of the instruction remains correct. |

For the last entry, bytecode inspection distinguishes two index reads: selecting the source step
and passing its PC to `metadata`. This mutation removes only the latter. The validator compares
the returned span and expansion path, not its instruction index. Compiler-produced instructions
derive those compared fields independently of the metadata argument, and the cutoff PC was already
checked against the actual configuration. This reasoning is limited to compiler-produced source
instructions; it makes no equivalence claim about arbitrary custom `SourceStep` implementations.
Retain the survivor and do not exclude it from PIT.

## Constraints

- Dependencies: shipped Spec 16, especially its replay contract in 16.06.
- Backward compatibility: preserve all existing replay overloads and trace representations.
- Local Gradle commands use `--max-workers=1 -PpitestThreads=1`; target overrides retain all tests.
- The 9-V1 replay scope overlaps H1 and is counted once; the earlier expanded aggregate is historical.

## Verification

Focused regressions pass. Replay-only PIT improves from **197/203 to 202/203 assertion-confirmed
kills**: three SURVIVED and two NO_COVERAGE mutations become KILLED by the corresponding named
regressions above. All 203 semantic identities are unchanged; all 197 baseline kills remain killed.
There is one SURVIVED mutation (the scoped metadata-argument case above), zero NO_COVERAGE and zero
timeout/error detections. The scoped mutation ratchet passes. No mutation exclusions were added.

The clean integrated suite passes **650 invocations**, none failed or skipped. Build and strict
Javadoc pass. Visualizer normalization passes all **18 checks**. Browser integration was not rerun
locally because production trace output and metadata propagation are unchanged; the existing CI
visualizer job still covers that boundary. The default state/CBS mutation gate passes at
**277/291 assertion-confirmed kills**, 14 SURVIVED, zero NO_COVERAGE and zero timeout/error
detections. Its 94% floor and all ratchet rules remain unchanged. This is a separate scope from
the replay-only result and must not be added to it or substituted for the expanded 9-V1 aggregate.

CodeRabbit CLI 0.8.1 completed review of all four changed files with **zero findings**
(`review_completed`, not skipped). Independent specification reader review identified a weak
initial-state-only rollback fixture; the final fixture commits 7, attempts 9 and then fails,
requiring replay to retain 7. The reader rechecked the revised tests and ledger with no remaining
inconsistencies. Only measured verification/disposition prose was finalized after the CLI review;
no production or test code changed after it.
