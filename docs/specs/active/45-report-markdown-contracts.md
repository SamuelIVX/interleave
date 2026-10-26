# Spec: Markdown report section contracts

## Objective

Protect the required Markdown report skeleton and content order in roadmap9-V1 without treating
blank separator counts as correctness defects. Finish the listed writeMarkdown survivor audit
left separate from Spec44's JSON contract.

## Scope

Add ReportMarkdownContractTest, this specification and the roadmap update. No production, API,
dependency, output, mutation-scope/operator/exclusion or gate changes. Existing StatesExploredTable
and SoundnessAttestation behavior remains unchanged. Spec40's nonterminating property-selector
mutation stays open; this report audit does not replace its failed scoped gate.

## Requirements

1. Reports SHALL retain the title, States Explored Reduction Table, Detailed Results and Soundness
   Attestation headings in order, even for an empty row list.
2. Bitstate Summary SHALL appear only when bitstate rows exist, before Detailed Results. Its rows
   SHALL retain the declared metrics. Detailed Results SHALL retain every supplied row in order.
3. The full qualified attestation paragraph SHALL remain unchanged; unknown programs without supplied
   expectations are not evidence that all programs passed a correctness check.
4. Tests SHALL preserve every nonblank content line, including heading levels, tables, row cells and
   paragraph text, while ignoring blank-only separators between blocks. Table rows SHALL stay
   contiguous. They SHALL NOT claim arbitrary Markdown AST or byte-for-byte equivalence.
5. Retained formatting dispositions SHALL be limited to the normal generated heading/list/table/
   paragraph report shape and valid total native rendering operations. Embedded authored Markdown,
   formatting bytes and process/resource/performance behavior remain outside their scope.

## Fixtures and independent oracles

Three cases cover exact-only, exact-plus-bitstate and empty reports. Full expected nonblank-line
sequences are literals; no expected fragment is rendered by the classes under test. The attestation
literal follows Spec29's qualified public text rather than calling formatMarkdown to build an oracle.

The carrier rows use sample/DFS/EXACT with states17,time23,heap-31, and sample/DFS/BITSTATE with
states19,time29,heap37,FPR1.25e-8,bits7,density0.125. These are display inputs, not measured model costs.
Each store's DFS count is its own baseline, so neither row has a reduction arrow. Bitstate FPR is
1.25e-08 and density0.125000: manual decimal counting and an independent Python Decimal formatting
check confirm the scientific/fixed representations. Formatting locale is explicitly ROOT in the
test and restored in finally under JUnit's locale lock; production human-readable locale behavior
is not changed by this test.

The exact report has no Bitstate Summary. The mixed report places that summary between reduction
and detailed sections, then includes both detailed rows. The empty report retains the detailed
heading and table header/separator before attestation. Unknown PASS rows supply no expectation or
violation, so the existing attestation policy reports its limited successful checks.

contentLines requires adjacent table content rows to have no blank lines between them, then
ignores blank lines between blocks. A missing Detailed Results heading still fails; headings
merged onto another line, reordered sections, changed cells or altered attestation also fail.
The helper documents its shortcut: fixtures have no authored Markdown blocks; an AST oracle is
needed before widening that input. This is not a general Markdown parser or a whitespace-byte test.

## Retained writeMarkdown formatting dispositions

Indices identify single bytecode mutations, not source lines. These arguments apply to the report
builder's ordinary generated blocks, with names/cells that do not inject authored Markdown syntax.
They are selected content-line/block contracts, not universal rendering equivalence for arbitrary
caller text or combined mutations. Keep production code unchanged.

| Method / bytecode index | Mutation | Disposition |
|---|---|---|
| writeMarkdown/32 | Removed extra newline after reduction table | The reduction component already ends with blank-line separation for empty and populated input; required lines and boundaries remain. |
| writeMarkdown/42 | Removed String.isEmpty result, always including summary append branch | Nonempty summaries are included as before. Empty summary appends no content and only an extra blank separator; the summary renderer still ran before the condition. |
| writeMarkdown/54 | Removed extra newline after nonempty bitstate summary | The summary component already terminates its table with newlines; required content and next heading stay separated. |
| writeMarkdown/74 | Removed extra newline before attestation | The detailed table already ends with a newline. The next fixed ATX heading starts on its own line and terminates the ordinary generated table block; only blank spacing differs. |

The [GFM tables specification](https://github.github.com/gfm/#tables-extension-) permits a new
block-level structure to terminate a table. That supports the fixed ATX heading boundary in /74
for the generated report shape above.

Native String.isEmpty/StringBuilder operations inspect only already-rendered strings and do not
invoke model code. No partial/custom-renderer errors or process/resource/performance equivalence
is claimed. In particular, these dispositions do not justify removing writeMarkdown/61's actual
Detailed Results heading, which is required report content.

## Baseline and verification

PR #85 merged at5cdb8fc. Fresh baseline:991 tests pass, zero failures/errors/skips. Scoped
ReportWriter176/184 KILLED,8 SURVIVED,0 uncovered/timeout/memory/run-error statuses; scoped ratchet
passes. Five raw survivors belong to writeMarkdown; Spec44 already records three JSON-value
dispositions. The passing baseline does not establish whole-checker closure.

All three targeted cases pass. The full suite passes994 tests with zero failures/errors/skips.
Scoped ReportWriter now has177/184 KILLED and7 SURVIVED, with zero uncovered or resource-error
statuses; the scoped ratchet passes. All184 mutation identities match the fresh baseline, all176
previous kills remain killed, and only writeMarkdown/61 changes from SURVIVED to KILLED. The seven
retained survivors comprise the four Markdown dispositions above and Spec44's three JSON-value
dispositions. All18 normalization checks pass. Post-fix clean build, strict Javadoc and994 tests
pass; default PIT281/291 KILLED,10 SURVIVED, zero uncovered/resource-error statuses, with all291
identities/statuses matching Spec44. Post-fix scoped confirmation again passes at177/184 KILLED,7 SURVIVED with the same184 identities
and single new heading kill. CodeRabbit CLI0.8.1 completed the three-file
review with one MAJOR finding: filtering all blanks could hide a split table. The oracle now checks
table-row contiguity before filtering blank separators. A bounded follow-up reviewed all three
files and completed with zero findings. All local Gradle commands use
`--max-workers=1 -PpitestThreads=1`. Browser integration is outside this test-only bundle because
executable production/report output is unchanged; Spec44's35 source-browser checks remain historical.
Remote CI remains unverified; publish as draft and stop. The historical expanded9-V1 aggregate is
unremeasured, and a passing default scope does not replace Spec40's failed property-selector scope.
