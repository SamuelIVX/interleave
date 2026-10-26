# Spec: Locale-independent report JSON contracts

## Objective

Fix invalid report JSON under non-Latin digit locales and protect complete report-row serialization
in roadmap9-V1. Keep JSON value contracts separate from Markdown formatting and prior helper scopes.

## Scope

Change only the three integer String.format calls in ReportWriter.writeJson to use Locale.ROOT.
Add ReportJsonContractTest, this specification and a roadmap update. Correct StateSnapshotExtractor's
Peterson documentation: inCriticalSection is an owner ID, not a count; -1 means no owner.
No public API, dependency, field/schema, mutation-scope/operator/exclusion or gate changes.
Spec40's property-selector component/79 resource limitation stays open and is not rerun here.
Five writeMarkdown survivors in the measured class remain a separate audit, not JSON dispositions.

## Reproduced bug and smallest fix

The new locale regression passed under en-US and failed under ar-EG before the fix. Strict parsing
reported malformed JSON at the first row's statesExplored. The report used the process FORMAT locale
for unquoted integer tokens, so the Arabic locale emitted non-ASCII digits. JSON number syntax does
not accept those tokens. statesExplored, wallTimeMs and heapDeltaBytes shared this defect.

Add Locale.ROOT only to those three calls. Existing text fields, Integer.toString preemption bounds,
trace arrays and the already-rooted bitstate formatter retain their behavior. Latin-locale output
keeps the same values; non-Latin-locale output becomes valid ASCII-number JSON. The regression then
passes in both locales. Restore the caller's FORMAT locale in finally and use JUnit's locale lock.

## Requirements

1. Every report SHALL be strict JSON with ASCII number syntax independent of the process format
   locale. Ordered rows SHALL preserve count/time/heap values and null versus zero preemption bounds.
2. Complete row/trace objects SHALL retain required keys and omit unrelated optional keys, rather
   than passing through a matching substring. Reuse JsonAssertions.parseObject.
3. Bitstate diagnostics SHALL retain numeric meaning for tiny/scientific, fixed-decimal, zero and
   exact-threshold inputs. Whitespace and equivalent JSON number spellings are not byte contracts.
4. Legacy failures SHALL retain their threadIds/outcomes shape, optional snapshots and real model
   values. Source failures SHALL retain both threads and threadIds, diagnostics and rollback snapshots.
5. Source cutoff rows SHALL retain programKind, incompleteness reasons, representative omitted-action
   evidence and its snapshot, without inventing a failingTrace or compatibility alias.
6. The top-level soundness Boolean SHALL report the attestation result, including missing-failure
   rejection. It describes supplied checks, not a proof of whole-checker soundness.

## Fixtures and independent oracles

Eight cases compare full literal JSON trees. Two locale rows preserve metadata[17,23,-31] and
[19,29,37], report order and null/zero bounds. These are caller-supplied carrier values, not measured
model costs. Unknown PASS/INCOMPLETE rows have no supplied expectation or violation to replay, so
the attestation reports true under its existing policy. Empty reports also report true; a VIOLATION
row lacking its trace reports false. No attestation validation is bypassed or changed.

Two bitstate rows use explicit diagnostics: FPR1.25e-8/density0.123456/bits7 and
FPR0.001/density0/bits3. They are serializer inputs, not a claimed Bloom-filter experiment. Manual
decimal counting and Python Decimal formatting confirm the literals1.25e-8,0.123456,0.001,0.
The threshold comparison may change spelling from0.001000 to1.00e-03 without changing JSON value.

Two legacy rows vary optional snapshots. Two owners independently write different Peterson flags;
the invariant forbids both flags being true. The2×2 counter product gives four configurations,
independently enumerated as00,10,11,01. Actual DFS visits four states and emits genuine failure
prefix[0,1]. Replay snapshots are false/false, true/false, true/true: initial plus two commits gives
three snapshots. turn stays0 and the no-critical-owner sentinel stays-1 throughout.

Source failure/cutoff fixtures reuse Spec43's exact source bytes and independently derived hashes/
spans. Actual DFS evidence, actual replay snapshots and withExploration supply the report inputs;
expected JSON remains literal. A failed source action visits one committed root and captures two
identical x=0 snapshots, including its rollback observation. A cutoff records zero attempted choices
and one root snapshot; its LOOP_LIMIT reason and expansion ancestry remain separate omitted evidence.
The failure payload has both choice aliases; the incomplete payload retains only its public threads
key. Full equality checks absence as well as presence. Source failure replay authenticates runtime
diagnostics even without a declared property expectation.

## Mutation accounting across the production fix

Freeze the fresh merged baseline before editing executable code. The three Locale.ROOT arguments
insert instructions in writeJson, so its later bytecode indices shift. Do not claim raw identities
are unchanged. Match class/method/descriptor/mutator/source operation and corresponding ordered
occurrences, checking equal group sizes and unchanged blocks. The source diff changes only those
three format arguments; relative operation ordering is preserved. Audit the resulting0/1/2/3 index
offsets and retain every prior kill under this explicit correspondence. Non-writeJson identities
remain exact. No operator or method exclusion changes to make counts match.

## JSON-value dispositions and separate Markdown work

- formatSmallDouble/16 ConditionalsBoundary moves exactly0.001 between fixed/scientific branches.
  Both produce valid JSON numbers with value0.001. This is not byte-for-byte output equivalence.
- writeJson old299/new302 and old347/new350 remove optional newline appends. Required commas,
  brackets and tokens remain; strict parsed JSON values are unchanged. Whitespace-byte identity,
  allocation/work/resource/performance behavior is not claimed.

Keep the code and gates. These three single-site JSON-value dispositions do not cover combined
mutations or arbitrary non-finite/unsupported diagnostics. Five writeMarkdown identities32,42,54,61,74
remain open for a separate output-contract audit. Do not claim all ReportWriter survivors disposed.

## Baseline and verification

PR #84 merged atbf44081. Fresh baseline:983 tests pass, zero failures/errors/skips. ReportWriter:
154/184 KILLED,27 SURVIVED,3 NO_COVERAGE, zero timeout/memory/run-error statuses; scoped ratchet passes.
The completed regression run passed en-US and failed ar-EG at JSON parsing; both passed after the fix.

All eight targeted cases and the full991-test suite pass. Post-fix scoped measurement:176/184 KILLED,
8 SURVIVED,0 uncovered/timeout/memory/run-error statuses. Explicit operation correspondence covers
all184 entries and retains all154 old kills. Exactly22 new kills comprise19 prior survivors and
three uncovered formatter sites. CodeRabbit CLI0.8.1 completed its five-file review with one minor documentation finding: add the
explicit -1 unowned sentinel to the corrected snapshot Javadoc. Verified against PetersonState.of
and patched locally; no extra CLI for minor feedback. Final clean build, strict Javadoc and all991 tests pass, zero failures/errors/skips. Default PIT
killed281/291 mutations and left10 SURVIVED, zero uncovered/timeout/memory/run-error statuses;
all291 semantic identities and statuses match merged Spec43 evidence. The post-comment strict
Javadoc rerun passes; no executable code/test changed after the completed five-file review.
Only documentation/status prose followed. Both ReportWriter-scoped and default gates pass;
Spec40's distinct failed property-selector scope remains open.
The three JSON-value dispositions plus five open Markdown sites account for the eight raw survivors.

The existing Playwright suite passed24 legacy checks and then35 checks with SOURCE_TRACE_JSON set
to the fresh Java report, including source metadata and zero console errors. The second invocation
matches CI's source-integration environment; do not count the first as source coverage. Locked docs
dependencies were installed without changing manifests. Normalization checks also pass.

All local Gradle commands use `--max-workers=1 -PpitestThreads=1`; CI configuration stays unchanged.
Remote CI remains unverified; publish as draft and stop. Historical expanded9-V1 totals remain
unremeasured, and a passing default scope does not replace Spec40's failed property-selector scope.

### Mutation correspondence and changed-operation ledger

There are109 unchanged raw index identities and75 shifted writeJson identities:4 shift by1,
4 by2 and67 by3. All basic-block lists remain unchanged. Non-writeJson has91 exact identities;
writeJson has18 exact plus the75 shifted entries. All184 operations map bijectively by method,
descriptor, mutator, description, unchanged source position and ordered occurrence. Counts alone
are not treated as proof that the populations match; source diff and actual offsets were checked.

The table lists all22 newly killed operations. Numbers are old→new bytecode indices when shifted;
comma-separated entries are separate mutations. Mutator identity distinguishes coincident indices.

| Method | Mutator | Old→new bytecode indices | Baseline → post-fix |
|---|---|---|---|
| formatBitstateMetrics | NonVoidMethodCallMutator | 12,20,21 | SURVIVED → KILLED |
| formatFailingTrace | NegateConditionalsMutator | 146,149 | SURVIVED → KILLED |
| formatFailingTrace | NonVoidMethodCallMutator | 148,157 | SURVIVED → KILLED |
| formatSmallDouble | EmptyObjectReturnValsMutator | 43 | NO_COVERAGE → KILLED |
| formatSmallDouble | NegateConditionalsMutator | 16 | SURVIVED → KILLED |
| formatSmallDouble | NonVoidMethodCallMutator | 40,42 | NO_COVERAGE → KILLED |
| formatSmallDouble | NonVoidMethodCallMutator | 13 | SURVIVED → KILLED |
| writeJson | NegateConditionalsMutator | 148→151,159→162 | SURVIVED → KILLED |
| writeJson | NonVoidMethodCallMutator | 147→150,151→154,158→161,167→170,168→171,196→199,198→201,224→227 | SURVIVED → KILLED |
