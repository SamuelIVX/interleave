# Spec: Source-label metadata boundaries

## Objective

Protect the remaining `SourceInstruction.metadata` origin/truncation contract as a focused
roadmap 9-V1 batch. Check actual labels with independent fixtures rather than reusing producer
metadata or truncation expressions as expectations.

## Scope

- Adds `SourceLabelTest`; updates this spec and the roadmap.
- No production/API, dependency, mutation scope/mutator/exclusion or gate changes.
- Higher-level verdict/reduction/reporting work remains separate. This does not remeasure the
  historical expanded mutation population or prove every possible Unicode/display behavior.

## Invariants and Requirements

1. WHEN an action's first source line has at most 256 UTF-16 code units, THE SYSTEM SHALL preserve
   it without adding an ellipsis, including exactly 256.
2. WHEN the first line exceeds 256 units, THE SYSTEM SHALL retain its first 256 units followed
   by one U+2026 ellipsis, preserving the actual action origin rather than the program prologue.
3. WHEN a multiline action has a short first line and a long later line, THE SYSTEM SHALL use
   only the short first line as its label while retaining the full action's source span.

## Design and Tests

Load valid ASCII atomic fixtures after a distinct program/declaration/thread prologue and inspect
`SourceStep.metadata(0)` for the actual compiled instruction. The comment prefix has 11 characters,
its suffix 17. Comment payloads 227/228/229/300 therefore produce first lines of 255/256/257/328.
Hand character addition and a separate Python text-length calculation agree. Expected labels use
explicit fixture fragments; Java tests never recompute a substring or branch on the production
limit. For overflow, external direct character enumeration confirms the independently specified
retained strings (229 comment characters plus a shortened suffix, or 245 comment characters).

The multiline fixture has first line `atomic {` and a 300-character comment on a later line.
Its expected label is literal; its complete span is line 4 column 5 through line 6 column 6
(exclusive end), independently determined from the wrapper and fixture indentation.

| Requirement | Regression | Cases |
|---|---|---|
| R1 | `preservesLabelsAtAndBelowLimit` | 255 and 256 characters; full original action and literal lengths |
| R2 | `truncatesOnlyOverlongFirstLines` | 257 and 328 characters; complete independent expected labels and literal 257-unit display lengths |
| R3 | `multilineBlockUsesOnlyItsShortFirstLine` | Short first line, long body and full multiline source span |

## Acceptance Criteria

- All five new invocations and the full suite pass without production behavior changes.
- Compare fresh merged-main/final outer-SourceInstruction mutation identities/statuses, preserving
  all baseline kills and explicitly recording new kills and any residuals.
- Full build/tests/Javadoc, unchanged default mutation gate and normalization pass.
- CodeRabbit CLI reviews the three-file bundle; independently disposition any findings.

## Baseline

Merged PR #65 is `ad67c82`. Fresh baseline passes 766 tests, no failures/errors/skips;
outer-SourceInstruction PIT is **110/114 assertion-confirmed kills**, three survivors and one
uncovered, zero timeout/error detections. Existing target override measures that class independently
of default state/CBS gates. Local Gradle always uses `--max-workers=1 -PpitestThreads=1`.

## Verification

All **771 tests** pass (five new invocations), zero failures/errors/skips. Scoped outer
`SourceInstruction` PIT improves **110/114 → 114/114 assertion-confirmed kills**: three survivors
and one uncovered mutation become killed, with zero survivors/uncovered/timeouts/errors. The same
114 semantic identities remain and all 110 baseline kills stay killed. Identity comparison uses
class/method/descriptor/mutator plus instruction indices and blocks, not line numbers.

### Scoped mutation ledger

All identities belong to `dev.samhb.interleave.format.dsl.SourceInstruction.metadata(I)`;
indices identify bytecode instructions. Production bytecode and mutation scope are unchanged.

| Baseline → final | Index | Mutation / distinguishing contract |
|---|---|---|
| SURVIVED → KILLED | 25 | Changed truncation boundary; exactly 256 characters must remain unmodified. |
| SURVIVED → KILLED | 8 | Removed Node.start call; labels must originate at the action, not the program prologue. |
| SURVIVED → KILLED | 23 | Removed String.length call; overlong first lines must actually be truncated. |
| NO_COVERAGE → KILLED | 29 | Removed String.substring call; complete expected overflow labels retain the correct prefix. |

This closes the measured outer-SourceInstruction mutation population, not nested classes, all
possible label behavior or the broader checker. No equivalence claim or exclusion is added.
Roadmap 9-V1 remains open for higher-level verdict/reduction/reporting contracts.
Final `clean build javadoc pitest` passes all 771 tests, zero failures/errors/skips, including
strict Javadoc doclint. Default PIT remains **281/291 kills**, ten survivors, zero uncovered and
zero timeout/error detections; all semantic identities/statuses match merged PR #65's evidence.
The 94% floor, scopes, mutators and exclusions are unchanged. Its inherited informational
per-class baseline notice remains outside this bundle. All 18 visualizer normalization checks
pass. Browser integration is not rerun because production output and browser code are unchanged.
GitHub CI has not been checked.

CodeRabbit CLI completed review of all three changed files with **zero findings**. Only measured
verification/status prose was finalized afterward. Scoped credential-pattern scan, relative
Markdown links and diff whitespace checks pass. No production/API or dependency changes.
Next focused 9-V1 work needs fresh higher-level verdict/reduction/reporting baselines.
