# 78 — Preserve strategy and verdict strings in report JSON

## TL;DR

Implement J1 / bundle A from the approved
[post-task-#10 inventory](../../plans/post-task10-follow-ups.md). Finish the remaining report-string
escaping issue originally recorded in Spec 14. Use existing Gson serialization for strategy and
verdict, preserve the established null conversion and JSON schema, and include strict round-trip
regressions in one focused fix PR.

## Problem and supported contract

`ReportWriter.writeJson` inserts `BenchmarkResult.strategy()` and `verdict()` into quoted format
strings. The public row constructors accept strings without enum validation. A quote, backslash
or control character can make the document malformed or alter its parsed value. JSON-looking
text can also become additional object members rather than one caller-supplied string.
Ordinary CLI/harness tokens are fixed and unaffected. Report bug names and trace program hashes
already use proper escaping; do not reopen those fixed cases.

The report must preserve every non-null caller string as the same parsed JSON string. Standard
DFS/PASS/etc. output, field names/order, numeric types, optional evidence and soundness behavior
stay compatible. Escaping is not validation of the meaning of custom strategy/verdict labels.

The existing percent-s conversion renders Java null as the JSON string `"null"`. Preserve this
rather than switching to a JSON null token or introducing constructor validation. Convert with
`String.valueOf` before the existing `GSON.toJson` instance. The serializer already disables HTML
escaping consistently with the bug-name path. No dependency or result-type changes are needed.

## Implementation scope

- `ReportWriter.writeJson`: replace quoted interpolation for these two values with JSON-encoded
  string output. Document the preserved null semantics in its existing Javadoc.
- `ReportJsonContractTest`: use the existing strict `JsonAssertions.parseObject` helper and public
  row constructors. Check the actual parsed strings, primitive types and complete row/top-level
  key sets; do not derive expected values with the encoder under test.
- Update the approved inventory's J1 status and keep its other boundaries/parked items intact.

No search/state/store/reduction behavior, native serialization UID, CLI options, browser schema,
verdict-policy validation, new enums or general report rewrite belongs in this fix.

## Acceptance

Cross each field selection (strategy only, verdict only, both) with eight literal cases: ordinary,
empty, quotation mark, backslashes, NUL plus JSON control characters, Unicode, JSON-looking text
and HTML-sensitive characters. These 24 cases must strictly parse, preserve the original value
and JSON string type, retain exactly the existing keys, and keep the literal state count seven.
The control-character values are source escapes, not raw binary fixture files.

One additional case checks null in either/both positions and the literal string `"null"`.
Parsed values must remain strings containing `null`; the unaffected DFS/PASS counterparts remain
unchanged. Existing complete report, locale, source, failure, incomplete and bitstate tests stay.

## Verification and evidence

Planning/code baseline: main `df38ad7`, which adds only the approved inventory to `2a53ffc`.
Fresh unchanged ReportWriter scope: **184/191 K + 7 S**, zero uncovered/resource/run errors.
The default state/CBS population is separate and production code in that scope is untouched.
No operator/filter/target-test/floor changes are permitted. Scoped aggregate R5/R7 checks are
not a full default-scope pass.

Before the fix, the expanded report-contract class runs **33 cases with 12 expected failures**:
quote, backslash, control and JSON-looking cases fail for each of the three field selections.
Null and ordinary-value cases pass. Frozen local evidence:
`/private/tmp/interleave-report-string-baseline.xml` and
`/private/tmp/interleave-report-string-red.log`.

After implementation run the full build/Javadoc suite, affected ReportWriter mutation scope,
and browser integration against fresh Java-produced reports. Record the final census, semantic
lineage and retained limitations below. Use one Gradle worker and one PIT thread on this host.
The default mutation gate need not be rerun for unchanged default targets; do not claim a new
full-scope measurement. Run the selective CodeRabbit gate for this spec-driven bundle and stop
after publishing its draft PR; Sam supplies CI/review/merge updates.


## Final local verification

- Full build, **1,640 tests** (25 new cases) and strict Javadoc pass. The 12 pre-fix failures are
  green; the already-green null cases remain green. Log: `/private/tmp/interleave-report-string-final.log`.
- Browser integration against freshly generated Java reports: **49/49 pass**, zero console
  errors. Existing normalization checks also pass. No viewer schema/code or dependency change.
- Final ReportWriter scope: **189/197 K + 8 S**, zero uncovered/resource/run errors; scoped R6
  passes. Same operators, target tests, filters and thresholds. Raw final XML:
  `/private/tmp/interleave-report-string-final.xml`.
- **185 unchanged-source mutations preserve every prior status**, matched by unchanged source,
  method/descriptor, mutator, description and per-status multiplicity. The two changed value
  statements replace six killed mutations with twelve new mutations: eleven killed and one
  whitespace-only survivor. The five additional kills are population changes, not kills of old
  survivors. All seven inherited survivors remain visible.
- CodeRabbit 0.8.1 completed an actual `review_completed` over five files with one minor comment
  about historical-spec status wording. The wording is corrected and documentation is checked
  locally; no CLI rerun for minor feedback. The code/test diff is unchanged after verification.
- No fresh default state/CBS PIT run is claimed: none of its production targets or census/floor
  configuration changed. The scoped score is not a whole-checker score.

### Retained raw mutations

These IDs belong to the measured final ReportWriter scope, not source-line references. S means
SURVIVED and earns no kill credit. The inherited entries retain the prior report-contract
limitations/dispositions; this JSON fix does not reopen their broader contract scope.

| Method / index / block | Mutation | Disposition |
|---|---|---|
| formatSmallDouble / 16 / 3 | Conditional boundary | Inherited S; numeric/format threshold boundary, unchanged by J1. |
| writeJson / 290 / 92 | Remove append | **New S:** removes only the newline after the encoded verdict. The following closing brace still delimits the string value; parsed fields, types and values are unchanged. The report contract does not promise exact whitespace. |
| writeJson / 316 / 98 | Remove append | Inherited S; optional row-separating newline, unchanged parsed payload. |
| writeJson / 364 / 108 | Remove append | Inherited S; optional newline before the document closing brace. |
| writeMarkdown / 47 / 10 | Remove append | Inherited S; Markdown formatting disposition from the existing report-hardening ledger. |
| writeMarkdown / 57 / 12 | Remove isEmpty | Inherited S; empty-content/spacing disposition from the existing ledger. |
| writeMarkdown / 69 / 15 | Remove append | Inherited S; Markdown formatting disposition from the existing ledger. |
| writeMarkdown / 89 / 20 | Remove append | Inherited S; Markdown formatting disposition from the existing ledger. |

The baseline/final class populations are 191/197, distinct from the default state/CBS denominator.
No default census constant or floor is updated for this out-of-default-scope production change.
Bundle A is implemented and locally verified, pending merge. Bundle B remains the next proposed
assurance bundle; none of the optional support expansions starts with this PR.
