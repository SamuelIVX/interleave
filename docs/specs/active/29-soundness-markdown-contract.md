# Spec: Qualified soundness attestation Markdown

## Objective

Close the attestation rendering gaps from Specs 27–28 and make successful report prose match the
actual evidence boundary. Execution-only replay must not be called a proven property violation;
passing checks on supplied results must not claim whole-checker soundness.

## Scope

- Update `SoundnessAttestation.formatMarkdown` success prose and its Javadoc.
- Add `SoundnessMarkdownTest` with full-section oracles and ReportWriter integration checks.
- Ship this specification and update the future-work roadmap.
- Preserve verdict/replay algorithms, diagnostic precedence, warning text, JSON schema/flag,
  public APIs, dependencies, mutation scope/operators/exclusions and score floors.

## Non-Goals

New mandatory-invariant policy, report serialization changes, Markdown escaping, explorer/reduction
contracts and new modeling features belong to separate work. The JSON soundness flag retains its
existing meaning; no whole-checker proof is introduced.

## Invariants

- Success prose describes checks on the supplied results, not universal correctness.
- Source inconclusive DFS exemptions and absent expectations remain explicit.
- Replay without an invariant validates execution without establishing a property violation.
- Supplied invariants fail at the replayed configuration, or an authentic source runtime failure
  explains acceptance even when rollback leaves the property true.
- Failure output retains the exact first diagnostic and warning template; verdict checks still
  precede replay checks. Rendering does not change `isSound` or `failureReason`.
- The section heading, paragraph boundaries and final blank line survive standalone and report use.

## Requirements

1. Successful Markdown SHALL retain the checked-EXACT-DFS, source-inconclusive and absent-expectation
   qualifications, the cross-strategy inconclusive exclusions and their absence-of-bug rationale.
2. Successful Markdown SHALL describe execution replay, supplied-invariant/source-runtime conditions
   and the absent-invariant limit. It SHALL report no detected soundness failures in these checks,
   without claiming the checker is universally sound or every replay proves a property violation.
3. Failure Markdown SHALL retain the full expected diagnostic in the existing WARNING template,
   including heading, spacing, punctuation and trailing blank line, without success prose.
4. ReportWriter SHALL include the complete attestation section at the end of its Markdown output
   using the actual registered models; standalone and integrated sections SHALL agree.
5. Preserve unchanged non-rendering semantic mutation identities/statuses. Measure the changed
   rendering population separately rather than compare aggregate percentages with different denominators.

## Design and Tests

A literal full success paragraph is the public rendering oracle. Actual attestation fixtures cover
empty evidence, source inconclusive policy inputs, absent expectations, execution-only replay,
a genuine property violation and an explorer-produced source runtime failure with a true invariant.
The supplied inconclusive labels check report policy rather than claim that the empty source model
actually explores to those verdicts. Failure cases supply literal reasons independently of production.
Both helper assertions check standalone output and the full ReportWriter suffix.

| Requirement | Regression | Cases |
|---|---|---|
| R1, R2, R4 | `emptyEvidenceUsesQualifiedSuccessSection` | Empty evidence cannot establish a whole-checker proof |
| R1, R2, R4 | `sourceInconclusiveRowsKeepExemptions` | INCOMPLETE and APPROXIMATE_PASS source policy rows |
| R1, R2, R4 | `absentExpectationKeepsQualification` | A differing DFS label without an expectation |
| R2, R4 | `absentInvariantKeepsReplayQualification` | Bitstate bounded violation row with execution-only replay |
| R2, R4 | `genuinePropertyViolationUsesQualifiedSuccessSection` | Authentic zero-step false property |
| R2, R4 | `sourceRuntimeViolationKeepsExceptionQualification` | Real source modulo-by-zero with a true property |
| R3, R4 | `dfsMismatchWarningKeepsDiagnostic` | Exact DFS mismatch |
| R3, R4 | `missingTraceWarningKeepsDiagnostic` | Bounded bitstate missing trace |
| R3, R4 | `unknownProgramWarningKeepsDiagnostic` | Missing registered model |
| R3, R4 | `replayedNonViolationWarningKeepsDiagnostic` | Replay ends with a true property |

## Acceptance Criteria

- All 11 new invocations and the full existing suite pass; build and strict Javadoc pass.
- Observe the success-prose regressions fail before changing production; warnings remain green.
- Scoped PIT kills the rendering population while retaining prior non-rendering kills and the
  existing unused-map disposition. Document changed denominators and semantic comparisons precisely.
- Default PIT and normalization pass with their separate scope stated.
- CodeRabbit CLI completes review of this new four-file bundle; independently disposition findings.
  Trivial/minor PR feedback patches do not trigger another CLI review, per Sam's instruction.

## Baseline and failing regression evidence

Merged PR #69 is `2662652`. Fresh baseline: 852 tests pass, zero failures/errors/skips.
Outer-SoundnessAttestation PIT: 100/112 KILLED, nine SURVIVED, three NO_COVERAGE, zero timeout/error
detections. Rendering accounts for 16 identities: five kills, eight survivors and three uncovered.
The unchanged non-rendering population has 96 identities (95 kills and one unused-map survivor).

Before the production edit, all 11 rendering cases run: seven success cases fail with literal
output assertion differences, four warning cases pass, zero errors/skips. This reproduces the
unqualified success claims without weakening existing assertions. Every local Gradle command
uses `--max-workers=1 -PpitestThreads=1`.

## Verification

All **863 tests** pass, including the 11 new invocations; zero failures/errors/skips.
Outer-attestation PIT measures **113/114 KILLED**, one SURVIVED, zero uncovered/timeout/error
statuses. The aggregate is a changed population, not a same-denominator improvement claim:

| Population | Baseline | Final | Comparison |
|---|---|---|---|
| Unchanged non-rendering methods | 95/96 KILLED, one SURVIVED | 95/96 KILLED, one SURVIVED | All 96 semantic identities and statuses match; no prior non-rendering kill lost. |
| `formatMarkdown` | 5/16 KILLED, eight SURVIVED, three NO_COVERAGE | 18/18 KILLED | Two new qualification appends add two mutation sites; full-section assertions cover the revised rendering population. |

Rendering instruction indices after inserted appends are not treated as the same semantic identities.
The former 11 rendering gaps now have a complete-section oracle across success/failure contexts;
all 18 current rendering identities are killed. The sole retained outer-class mutant is the unused
`dfsVerdicts` local-map write described in Specs 27–28. Its disposition concerns returned attestation
values, not resource/work/allocation/performance equivalence; no cleanup or exclusion forces 100%.

The final integrated `clean build javadoc pitest` passes all 863 tests and strict Javadoc.
Default PIT remains **281/291**, with the same semantic identities/statuses as prior merged-main
evidence; all ten retained mutations keep their existing scoped dispositions, no uncovered/error
statuses. All 18 visualizer normalization checks pass.

CodeRabbit CLI completed the full four-file review with zero findings (`review_completed`, not
skipped). Only measured verification/status prose was finalized afterward. Browser integration
was not rerun: the browser consumes JSON, whose schema and attestation flag are unchanged; this
bundle changes Markdown output only. CI is unverified at publication.
