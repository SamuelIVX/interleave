# Spec 73: Source statement assertions

## Contract and complete bundle

Implement sequential 9-F4 after merged PR #114 at c19bfaf. Syntax is `assert (booleanExpression);`.
At thread/helper level it is one unconditional scheduled action: evaluate once at its execution
point, advance on true, terminate VIOLATION on false. In an atomic interior it observes tentative
values at that exact statement; true continues privately, false discards the whole outer action.
A runtime expression error remains RUNTIME_ERROR with its original category, rather than false
assertion. First encountered terminal event wins; later assertions/errors/cutoffs never execute.

False assertion evidence adds Kind.ASSERTION, Phase.STEP, null runtime category and the actual
assertion span. It retains the failing scheduled choice, ASSERTION_FAILED outcome, outer action
identity and unchanged committed snapshot. No additional trace fields or outcomes. A private
assertion can fail while the global property remains true after rollback. Assertions do not
replace always/final properties, and initial/post-commit property timing remains unchanged.

`assert` is contextual before `(`, preserving existing program/field/helper identifiers and
scalar/array assignments with that name. Assertions take no message/arguments and return no value.
Their expressions use existing typing, short-circuit, local/tid, array/error and depth rules.
Each scheduled assertion counts one global instruction; each private assertion counts one private
instruction. Count every expanded expression occurrence, including helper calls/repeats/branches.
Existing 64-action/private, 5,000-expression and source/helper limits are unchanged.

## Implementation and affected consumers

SourceCompiler parses ASSERT nodes, costs and expands them through existing finite forward control.
SourceInstruction derives full expression reads and handles true/false/evaluation errors in both
scheduled and private execution. Dependencies include dynamic index/error reads; no new reduction
rule or omitted assertion visibility. The existing independence argument applies to pure assertion
reads: independent transitions preserve its truth/error and finite source execution cannot postpone
it forever. Private assertions share the outer footprint and scratch/rollback boundary.

FailureEvidence validates ASSERTION only at STEP with no runtime category. Replay already compares
actual step evidence; minimization regenerates it from accepted executions. SoundnessAttestation
must accept authenticated assertion failures even when rollback leaves the supplied global
property true. Forged kind/phase/category/span/schedule evidence must still fail replay. Trace
copying, records, JSON reports and CLI retain the same fields with the additional kind value.
The browser normalizer must accept and validate the new kind, render it using existing safe
terminal text, reject malformed combinations, and retain all legacy cases.

Bundle production, all affected consumers, independent regressions, actual CLI/report/browser
verification, examples, language/roadmap docs and mutation evidence in one PR. No separate evidence
foundation is needed; no host assertion flag, Java language ingestion, dependency or new pruning
algorithm. Keep assertions distinct from helper syntax and guarded nested-atomic decisions.

## Acceptance and verification

Check all four exact strategies, reduced and fallback routes, true/false/runtime-error expressions,
scheduled interleavings, private transient values/rollback, local/tid/dynamic arrays, nested atomic
and helper composition, branches/repeat/while, first-event precedence and global property timing.
Replay authentic witnesses, reject tampering, minimize with true/null properties, and retain precise
inner statement/outer step/loop occurrence identities. Cover 64/65 instructions, 5,000/5,001 nodes,
boolean typing, malformed syntax, contextual names and unused invalid definitions.

Use independent literal state/PC/location/count oracles, not production-derived expectations.
Fresh merged-main build/tests/Javadoc and affected mutation baseline precede edits. Run integrated
checks, final affected scope, unchanged default gate, CLI reports and browser/normalization checks.
Retain raw non-kills with scoped dispositions; never combine populations, credit resource errors,
or change mutation targets/filters/operators/floors/PIT resources to improve scores.
Required initial CodeRabbit review must complete. Minor follow-ups use local checks, no CLI repeat.
Publish one draft PR and stop; Sam reports CI/review/merge. Next sequential task: 9-F5.

## Measured results

Fresh baseline passes clean build/strict Javadoc and 1,472 tests. Final integrated 1,504 tests and
strict Javadoc pass. Affected compiler 860/911 K + 29 S + 22 NC, instruction 126/127 K + 1 S,
failure evidence 9/9 K, attestation 116/117 K + 1 S; zero resource/run errors. The two old null-guard
survivors now kill through direct constructor rejection tests; all 1,164 final identities retained
with exactly those two S→K changes. All 849 unchanged-method baseline identities/statuses retained.
Scoped R6 passes; R5/R7 skip. Raw residuals and independent oracles are recorded in
[the evidence note](../../measurements/73-source-statement-assertions.md). All 22 normalizer verdict
checks plus structural cases pass. Default 283/293 K + 10 S retains all 293 identities/statuses,
zero uncovered/resource/run errors, full R5/R6/R7 pass. Fresh final build/strict Javadoc and all
1,504 tests pass again after PIT, regenerating production browser artifacts. Actual Main examples
report all exact PASS for passing assertions and VIOLATION for private rollback, soundness=true.
The complete Playwright suite passes 40 checks with both fresh Java artifacts and zero console
errors. CI workflow YAML/artifact wiring validates. CodeRabbit CLI 0.8.1 completed all 25 files
with zero findings. A final browser test correction checks the verdict badge at its actual DOM
location; all checks pass without a repeat CLI for that trivial test/docs closeout.
