# Spec 16 — Bounded Concurrent Modeling Language

## TL;DR

**Status: implemented; local acceptance and CLI reviews complete.** Roadmap item #9 gives authors a readable `.ilv`
language for finite concurrent models. It includes explicit atomic blocks, thread-level and atomic
`if/else`, fixed repetition, bounded `while`, and all four exploration strategies. It models selected
behavior; Java project ingestion remains future item #12. State-space graph work (#8) stays deferred.

The source loader, compiler, shared transitions, all four explorers, result propagation, replay,
minimization, CLI and source-aware viewer are implemented together. The acceptance evidence below
records completed local checks and external reviews. PR CI is tracked separately.

## Baseline and intent

Inspected baseline: `51f7c5448056c1b001a7327e410dcfdfb727ee40`.

- `ProgramLoader` loads typed/declarative JSON; `DslLoader` validates declarations, expressions,
  types, limits, steps and properties. Existing `Parser` handles expressions and assignments.
- `DynamicStep` runs flat assignments with complete derived footprints. `DslInvariant` supports
  final/always observations. These are reusable semantics, not a second language to reimplement.
- `Configuration.successor` assumes PC+1; POR/DPOR proofs assume finite linear threads. Source
  control flow therefore requires the shared execution extension and proof work in this set.
- `TraceReplayer` and `ExecutionDriver` currently execute on live state; the viewer derives step
  indexes by counting thread appearances. Both assumptions need explicit source-program handling.
- `BenchmarkHarness.actualVerdict` currently ignores INCOMPLETE, and CBS deepening reacts to any
  INCOMPLETE trace. Neither behavior is suitable for loop cutoffs without the changes in 04.
- A focused baseline ran 172 format/DSL/registry/CLI tests with zero failures or skips. This is
  existing-code evidence, not verification of the proposed language.

## Documents and ownership

| Document | Owns |
|---|---|
| [01 — Language](01-language-and-semantics.md) | Grammar and observable meaning |
| [02 — Execution](02-control-flow-and-atomic-execution.md) | Forward control flow, transition boundary, atomic execution |
| [03 — Frontend](03-parser-validation-and-lowering.md) | Parsing, validation, lowering, source/resource bounds |
| [04 — Completeness](04-incompleteness-and-verdicts.md) | Cutoff evidence, verdicts, reasons and result compatibility |
| [05 — Reduction](05-por-and-dpor-soundness.md) | Footprints, proof obligations, DFS/POR/DPOR/CBS contracts |
| [06 — Traces](06-traces-replay-and-minimization.md) | Replay, snapshots, minimization and viewer metadata |
| [07 — Integration](07-cli-library-and-acceptance.md) | Public loader/CLI, examples, integrated acceptance |

Read 01, then 02–04 together before implementation. Implement 02's compatibility-preserving execution
foundation first; then 03's compiler and 04's complete result propagation as internal functionality.
Implement 05 and 06 before exposing the source CLI/library in 07. Every slice ships its local tests;
the source language is not released with incomplete reporting or unverified reduction support.

## Fixed decisions

- A model's declarations and thread definitions are immutable and reusable across runs.
- Thread-level condition checks are scheduled actions. Atomic interiors are private computations;
  only the whole atomic block is visible to other threads and global properties.
- Bounded loops are expanded into forward-only control flow; a final true condition causes a
  cutoff. Expanded instruction position records remaining allowance without hidden mutable counters.
- Both state-invariant failures and reachable cutoffs must survive exact-store reductions.
- JSON/Java APIs retain their existing behavior. Source programs share the checker, expression
  evaluator, state representation and footprint rules, rather than using a source-only explorer.
- Implementation adds no dependency by default. Any proposed parser dependency needs demonstrated
  necessity and an explicit change to this design.

## Acceptance and review

The set is implementation-ready only when grammar, limits, execution cases, trace evidence and proof
obligations agree across documents. Each acceptance section below is a required future test ledger.
Proof obligations in 05 must be discharged in the implementation review, not treated as proven by
this proposal. Ordinary local review and fresh-context reader review precede CodeRabbit CLI review.
Follow the selective CodeRabbit gate; a skipped/rate-limited run is not completed review evidence.

Spec-only validation: check relative links, grammar/example agreement, section ownership, limit
arithmetic, absence of unresolved placeholders, and `git diff --check`. See 07 for implementation
checks. No new Java tests or production changes belong in the specification-authoring PR.

## Out of scope

Unbounded control flow, source-level functions, dynamic thread creation, Java/C compatibility,
statement assertions, explicit jumps, nested atomic blocks, weak memory, new liveness guarantees,
and graph rendering. Source files execute no host code or I/O. A PASS describes the finite model
and selected store guarantees, not the original application.

## Implementation evidence

Implementation baseline is merged spec PR #56, commit `09cd5dfc5535e8886a2b3021e88cf0da7420b9f7`.
Before production edits, single-worker `clean build javadoc pitest` passed: default PIT measured
268 mutations, 255 killed and 13 inherited survivors. Scope/mutators were unchanged.

Local integrated verification currently passes 644 JUnit cases with no failures or skips and strict
Javadoc. The Java-produced source artifact passes all 35 browser assertions (legacy plus source)
and normalizer checks. The approved execution seam first failed a compile check before its
implementation. Additional parser and CLI regressions exposed and corrected EOF diagnostic and
source-location classification errors during implementation.

| Contract | Executable evidence |
|---|---|
| Language/atomicity | `SourceProgramLoaderTest` independent final-value sets `{1,2}`/`{2}`, chosen-arm schedule; `SourceAcceptanceTest` private transient states, short-circuiting, guards and nested invocation values |
| Frontend/limits | `SourceBoundaryTest`, `SourceAcceptanceTest`, `SourceCompletenessTest` malformed syntax, UTF-8/byte identity, declarations/arrays/threads/depth/count caps, hostile expression chain, zero-work repeats and source diagnostics |
| Transition isolation | `TransitionExecutorTest`, source atomic error/cutoff rollback, detailed-driver and replay assertions |
| Completeness/CBS | Every strategy and report row retains LOOP_LIMIT; source preemption witness at nonzero PC, dedup/callbacks, simultaneous failure and both reasons, resource retention and no loop-only K deepening |
| Reduction | Reviewed argument in 05; independent DFS 64/static POR 10 product, real DPOR reduction, final-condition concurrency, nine-model grid and 24 terminal-thread permutations |
| Trace/minimization | Wrong digest/misalignment rejection, regenerated skipped-arm PCs, runtime-error minimization/attestation, initial and post-commit property errors, actual-PC Java/browser fixture |
| Public integration | Process-level `MainTest` source selector/error/conflict checks; seven disk-loaded teaching fixtures through every report row and attestation |
| Compatibility | Entire legacy suite and unchanged default mutation scope; source metadata remains additive, typed/JSON replay semantics retained |

An initial expanded mutation run targeted the source frontend, execution, DPOR and replay: 1,491
mutations, 1,113 assertion-confirmed kills, 230 survivors, 134 uncovered mutations, 13 memory errors
and one timeout. Its ratchet failed on the timeout; resource exhaustion is not credited as a kill.
The resource failures remove parser expansion guards or zero-work elimination under huge-count
fixtures. They are mutation-induced failures, not successful executions of the unmodified loader.
The run identified real test gaps in atomic footprints, inner array-error spans and cutoff-witness
validation; focused regressions now cover those contracts. Remaining diagnostic/record accessors,
conservative reduction alternatives and uncovered legacy branches are not declared equivalent.
The final execution/replay/reduction scope is measured below.

Mutation census and review disposition are recorded after completed runs; a running or skipped CLI
review is not treated as evidence. CI and publication status are tracked on the implementation PR. No dependencies were added. Source DPOR keeps a run-local sleep-context antichain,
which can revisit the same value/PC configuration when new actions are awake; visit-event counts
may exceed unique configurations, as they already can in CBS.

### Initial implementation mutation census

Before PR feedback, the unchanged default scope measured **281/295 assertion-confirmed kills**, zero uncovered
mutations and zero timeout/error detections. The state packages retain all 13 inherited survivors;
CBS measures 126/127. Its one unassigned survivor removes `Program.isSource` in deferred legacy
incomplete emission; it is recorded, not declared equivalent. Every other covered CBS mutation is
killed, including source cutoff handling and the early visitor notification.

The net population increase from 268 to 295 is new CBS execution/cutoff/reporting logic, with the
same scope, mutators and Java runtime as baseline. An intermediate census was 286/302; deleting
seven obsolete step-lookup mutations after execution moved to the shared boundary accounts for
302 to 295 (five kills and two survivors removed). That implementation census was 281/295; a new count assertion
has not changed that final survivor. The score increase caused by removing dead lookups is not
credited as improved assertion coverage. The existing 94% gate stays unchanged: 278 kills pass
(`94 × 295 = 27730`), 277 do not. `EXPECTED_TOTAL_MUTANTS` and the CBS per-class figure were updated
together only after measurement. The build/Javadoc suite passes 638 cases with no failures/skips.

### Review disposition

Independent parser/semantics reviews corrected source-declaration diagnostics and discharged the
sleep-context/fallback proof gap with source-only subsumption and normalization. A separate result
reader found both resource-interruption evidence-loss paths; failing regressions preceded their fix.

The first CodeRabbit CLI review completed over 72 files with one major, two minor and four trivial
findings. All seven were independently verified and addressed:

| Severity | Finding | Disposition |
|---|---|---|
| major | Timeline used previous action metadata | Changed lookup to the current action index. Stronger browser test first failed rows 0/1 and the first decision, then checks every action label plus the literal first decision. |
| minor | Source report lost legacy `threadIds` | Source failing/deadlock serialization retains both `threads` and `threadIds`; strict parsed-report assertions compare them. |
| trivial | Source browser assertion missed shifted labels | Every action row is checked; repeated adjacent atomic labels no longer hide the first-row error. |
| trivial | Helper documentation incomplete | Added parameter/result/failure/ownership contracts; public strict Javadoc and private non-missing doclint checks pass. |
| trivial | Atomic instruction documentation incomplete | Documented guard errors, scratch ownership, defensive direct-call BLOCKED and noncommitting failure/cutoff results. |
| trivial | Duplicate cutoff metadata replayed needlessly | Check witness deduplication before deriving the trace in all explorer paths. |
| minor | Thread budgets pointed to 1:1 | Preserve each thread's original offset; multiline action/expression cap regressions assert it. Shared declaration diagnostics retain/translate helper messages if fallback validation fails. |

The second CLI review completed over 74 files with one major, five minor and two trivial findings.
Verified code/docs/test findings were addressed and independently retested; no third CLI cycle was
run. Its conditional CI timing finding is evaluated on the fresh-runner PR job before readiness.
Review completion is tracked independently of heartbeat/status events. None of these reviews
substitutes for the fresh mutation gates or CI.

| Severity | Second-review finding | Disposition |
|---|---|---|
| minor | Example used unsupported `--strategy all` | Removed it; omitting the strategy selector runs every strategy. |
| trivial | Remaining helper/Draft documentation | Added precise parameters, return/throws contracts and cap+1 sentinel descriptions; strict public/private documentation checks pass. |
| trivial | Unsafe-label test could pass without loading the label | Assert the actual row's literal malicious string as well as absence of an image element. |
| minor | CLI selector errors omitted `--source` | All duplicate/required/incompatible selector diagnostics now list it. |
| minor | Array named `zero` misclassified as modulo error | Use evaluator message prefixes in step/property classification; a failing-before-fix regression checks both phases. |
| minor | Result docs excluded simultaneous resource/bound evidence | Document source LOOP_LIMIT, retained witnesses, and both flags being true. |
| major | Five-minute visualizer CI budget | PR #57 CI completed visualizer compatibility in 74 seconds, including fresh source compilation/report generation (35 seconds) and the browser suite (one second). Only the Gradle wrapper was restored from cache. Retain the five-minute timeout. |
| minor | Legacy failed-assertion interference probe changed behavior | Restore isolated tentative before/after guard comparison for legacy steps; source terminal behavior stays conservative. Failing-before-fix tests cover changed/unchanged guards. All 64 legacy corpus row visit counts/verdicts match before/after, with soundness true. |

The integrated post-fix suite passes 638 JUnit cases with zero failures/skips and 35 browser
assertions, including actual source-row mapping and literal unsafe labels. Final findings are
resolved by code-informed validation and these checks; this is not a claim of a zero-finding CLI run.

### Initial focused mutation check

The initial implementation focused scope passed its scoped ratchet at **545/689 assertion-confirmed kills**, with
66 survivors, 78 uncovered mutations and zero timeout/error detections. Its population is source
instruction/property execution, the source loader, shared transition/result boundary, DPOR and
source-aware replay. This is a different denominator from the 281/295 default scope; its aggregate
floor is intentionally not enforced, while non-assertion detection checks remain enforced.

| Class | Killed / total |
|---|---|
| StepTransition | 10 / 10 |
| TransitionExecutor | 18 / 18 |
| DporExplorer | 193 / 304 |
| SourceProgramLoader | 23 / 31 |
| SourceInstruction | 108 / 121 |
| SourceProperty | 18 / 20 |
| TraceReplayer | 175 / 185 |

These are measured checks, not complete mutation coverage or equivalence claims. The earlier broad
compiler analysis retains the resource-fault limitation above. No mutation scope, testing filter,
non-assertion rule or existing default threshold was weakened to make these runs pass.

### PR #57 feedback patch

All 12 current CodeRabbit threads were independently reviewed and their fixes explicitly approved.
The patch rejects a digest paired with legacy steps at the public Program boundary, preserves
an incomplete benchmark trace when its enclosing verdict is absent, and checks the exact counter
value in the Java/browser fixture. Optional incomplete snapshots now follow the existing best-effort
failing-snapshot policy: replay errors leave snapshots empty and retain the row's verdict/evidence.
Ordinary source replay remains strict.

The six loop-cutoff sites use per-run set membership instead of scanning all terminal traces.
Stateful explorers reset their set with the trace list; static POR/DPOR keep it local to the run.
A regression reuses every explorer and checks that the first witness and visitor callbacks agree.
Step/property errors share the existing evaluator's classifier, with no new utility class.
Compiler, replay, minimizer, trace and result documentation now describes the actual preconditions
and failures. The first patch build passed 641 JUnit cases, strict public/private Javadoc checks and 35 browser assertions.
A subsequent mutation-guided regression also checks two distinct loop occurrences across all explorers.
An isolated report with counter 12 instead of 2 fails exactly the strengthened counter assertion.

Graphify's relative-path advisory is refuted: the browser test resolves SOURCE_TRACE_JSON against
its module-derived repository root, independent of npm's working directory. The successful CI run
used that exact relative path and executed the fresh source-report assertions. Graphify's review was
partial and did not run tests or formal proofs; its status is not treated as complete verification.

The follow-up CodeRabbit CLI review completed over 20 files with one minor documentation finding:
replay without an invariant checks supplied step-failure evidence, but does not validate any terminal
verdict or establish completion. The public contract now states that limitation explicitly. No
zero-finding review is claimed. This documentation-only correction does not justify another CLI run.

The first follow-up default mutation run measured 276/291, with 15 survivors and zero uncovered or
non-assertion detections. The total fell from 295 because replacing the CBS stream/lambda removes
seven previously killed mutations and generates three set-boundary mutations. One new mutation
replaces the cutoff passed to Set.add with null, collapsing distinct loop witnesses. A new regression
checks both distinct witnesses in all four explorers; the final assertion-confirmed census follows
its completed run. The mutation scope/mutators and 94% floor remain unchanged.


### Feedback patch verification

All 16 PR threads have now been triaged: 15 unique findings were approved and patched, and the
second cutoff-set request duplicates the first. Later regressions first failed on the original
misleading attestation text and acceptance of forged scheduling-cutoff metadata, then passed after
fixes. Scheduling cutoff replay now checks source span, expansion ancestry and absent loop iterations
as well as enabled PC and preemption cost. The public ExecutionDriver.run contract documents its
source-only terminal exception and retained evidence.

The final default gate passes **277/291 assertion-confirmed kills** (95.19%), with 14 survivors,
zero uncovered mutations and zero timeout/error detections. CBS measures **122/123**. All 13
inherited state-package survivors and the same unassigned CBS survivor remain; the temporary new
null-cutoff-key survivor is killed by the distinct-loop regression. Relative to 281/295, seven old
killed stream/lambda mutations disappear and three new set-boundary mutations are killed. This
population change is not credited as stronger assertions. The unchanged 94% floor requires 274
kills (`94 × 291 = 27354`), with the same three-kill margin above the floor as before.

The full follow-up build/Javadoc suite passes **644 JUnit cases**, zero failures/skips.

The final patch CLI verification completed over all 26 changed files with **zero findings**. Its
first transport attempt failed with a closed WebSocket and is not credited; the same-scope retry
completed with `review_completed`. This is separate from the earlier completed patch review's
one minor replay-documentation finding, which was corrected and independently validated.

### Expanded feedback mutation check

The expanded feedback scope passed its scoped ratchet at **1,013/1,299 kills**, with 130 survivors,
156 uncovered mutations and zero timeout/error detections. This scope adds the constructor,
evaluator, result/reporting and DFS/static-POR paths; it is different from both the default scope
and the initial 689-mutant execution scope. Its percentages are not compared with either one.

| Class | Killed / total |
|---|---|
| Program | 26 / 27 |
| DporExplorer | 192 / 289 |
| Evaluator | 203 / 282 |
| SourceInstruction | 102 / 114 |
| SourceProperty | 13 / 13 |
| StaticPorExplorer | 106 / 122 |
| BenchmarkResult | 41 / 54 |
| SoundnessAttestation | 64 / 112 |
| DfsExplorer | 74 / 83 |
| TraceReplayer | 192 / 203 |

These are the measured expanded-run counts, not complete coverage or equivalence claims. The
Program survivor removes the existing empty-thread-list guard. A replay prefix-cost increment was
uncovered because the stricter source-identity check caused an older invalid-prefix fixture to
reject before reaching cost accounting. The fixture now carries authentic omitted-action metadata,
and a positive three-choice prefix checks two voluntary switches with an omitted third switch.
The full suite passes 644 cases after that strengthening; a targeted replay mutation rerun records
its separate result below without repeating the entire expanded scope.

The subsequent replay-only run passes its scoped ratchet at **197/203 kills**, with
4 survivors, 2 uncovered mutations and zero timeout/error detections. The
previously uncovered prefix-cost increment is now killed. These counts apply to the strengthened
replay tests; they do not silently replace the earlier expanded-run aggregate.
