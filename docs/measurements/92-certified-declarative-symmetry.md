<!-- B10/L1 fresh baseline, finite assurance, consumer checks and mutation/review provenance. -->
# 92 — Certified declarative exact symmetry evidence

## Status and baseline

L1 is implemented and locally verified; merge is pending. B10 remains in progress. Declarative
approximate admission is explicitly gated until L2. See [Spec 92](../specs/active/92-certified-declarative-symmetry.md)
and the [set contract](../plans/declarative-symmetry-contract.md).

The fresh baseline is merged spec PR #135 at `c8ee55373701d0a7f131c752397460a61217aaf4`, measured
in a separate untouched worktree. Its build/strict Javadoc and **1,907 tests** passed without
failures/errors/skips. No B9 measurement is substituted for this baseline.

## Implementation and independent checks

| Contract | Evidence and actual boundary |
|---|---|
| Loader trust | Private DslLoader.ValidationPermit authorizes certified steps/properties; public constructors and null permits cannot issue certification. Ordinary constructor execution remains available. |
| Complete admission | All owner/declaration/property bindings checked, including fixed owners. Foreign declarations/callbacks, mixed families and invalid IDs reject; fixed unknown callbacks are rejected before enabledness can execute. |
| Reassembly | Empty/65-step owners reject; 64-step owners admit. Two owners with ten repeated 250-node steps admit at 5,000 nodes; another occurrence or a one-node property rejects. Counts include aliases and property nodes. |
| Independent graphs | Hand-written finite counter/semaphore/split rules enumerate concrete positions; explicit permutations enumerate orbits without production grouping/canonicalization/key helpers. DFS literals: counter 16/5, semaphore 8/5, split increment 13/8, with HASHED/ORDERED orbit-set equality. |
| Typed transitions | All six three-owner permutations and all corresponding action owners check scalar/boolean/array frame transport, guards, ordered effects, short-circuiting, private partial errors and shared-property observations. |
| Exact integration | DFS/static POR/DPOR/CBS across HASHED/ORDERED, direct checked DPOR root-product coverage, FINAL fallback, iterative CBS's real one-preemption failure, store clear/copy and mismatched-run rejection. |
| Positive observations | Actual final step errors with true invariants validate. Disabled/invalid choices, invented errors/violations, trailing actions, earlier property failures and foreign source/cutoff evidence reject. Empty root property failure validates. |
| Isolation/resources | Repeated and simultaneous runners retain counts; root/later state stops and deterministic root deadline preserve interruption identity without JSON source prefixes. |

The semaphore 8/5 and counter 16/5 counts were also cross-checked outside Java by separate finite
constraint/counting routes during spec authoring. The split graph uses independent operational rules,
not recomputation with the implementation under test. The array/guard check is an equivariance test
over real declarative execution with independently transported frames; it is not a separate general
interpreter or a universal proof for arbitrary callbacks.

## Verification and consumers

- Admission, routing, CLI, viewer, attestation and failed-scratch regressions failed before their
  fixes and passed afterward. No test was deleted or weakened to pass a production defect.
- Final full build and strict Javadoc: **1,948 tests**, zero failures/errors/skips.
- **218 browser assertions** pass with zero console errors, using fresh existing source artifacts
  and declarative exact reports, completed/root/later traces and failed-effect library/CLI payloads.
- Private-member doclint passes for eight certification/attestation/state/capture files using
  `-private -Xdoclint:all -Werror`; the ordinary Javadoc task also treats warnings as errors.
- An external Java 26 caller compiled against the actual built jar and Gson passes all four exact
  strategies with both backends, non-source identity, approximate rejection, the stored-state type
  query and committed failure snapshots `[0, 1, 1]`.
- Workflow YAML parses with Ruby's YAML parser. Local Markdown targets/anchors and `git diff --check`
  pass. CI generates fresh report/library artifacts before normalization/browser checks.

The final source compatibility regression initially used an invalid mixed-case source identifier;
correcting the test fixture preserves the lowercase grammar. Earlier integrated logs retain report
wording/test-wrapper failures and the snapshot red/green evidence. Failed commands are not passes.

## Initial L1 mutation measurements

The following measurements precede the PR feedback refinement recorded below.
All runs use one Gradle worker and one PIT minion, the existing operators/test selection, and the
unchanged default mutation threshold. K = KILLED, S = SURVIVED, NC = NO_COVERAGE. Each row below is
an actual run; the scopes and revisions differ deliberately and their denominators are not comparable
as whole-checker percentages. No row has resource, timeout, run-error or other mutation statuses.

| Scope / point measured | Baseline | Feature | Meaning |
|---|---|---|---|
| Default gate | 455/468 K + 13 S | 455/468 K + 13 S | Pre-feedback production code; all 468 fingerprints and statuses identical. The subsequent source-only regression adds test coverage without changing production. |
| Broad affected census | 2,247/2,853 K + 177 S + 429 NC | 2,421/3,040 K + 184 S + 435 NC | Feature census precedes the later stored-state and committed-snapshot refinements. This is explicitly a point-in-time wide run. |
| Program / SourceSymmetry / SoundnessAttestation (including record) | 225/227 K + 2 S | 343/344 K + 1 S | Final stored-state guard and strict positive-witness checks. Program 29/29 K; SourceSymmetry 106/107 K; attestation plus record 208/208 K. |
| Source partial visitor, final test refinement | 30/43 K + 13 S | 38/47 K + 9 S | Added source partial-failure regression kills the source/JSON selector and source snapshot-call removals; zero NC. |
| Snapshot producers / TraceReplayer / DynamicStep | 756/833 K + 60 S + 17 NC | 839/914 K + 56 S + 19 NC | Final production and guard-outcome regression, before the additional source partial-failure test below. All 30 new capture-method mutants are killed. |

Broad affected targets: DynamicStep, DslInvariant, DslLoader, SourceSymmetry, SourceInstruction,
SymmetryRunContext, SymmetryStateStore, BitstateSymmetryStateStore, DporExplorer, StaticPorExplorer,
InterleaveRunner, TestResult, Main, BenchmarkHarness and SoundnessAttestation, including nested types.
The later focused scopes supplement that run; they do not masquerade as another broad final run.
The source visitor refinement adds two kills to the preceding snapshot run (36/47 K + 11 S
for that class); the earlier snapshot total is retained as the actual run, not silently recomputed.
Separate baseline Program and TraceReplayer measurements are **27/27 K** and **202/203 K + 1 S**.

Broad comparison: 2,209 shared fingerprints, 2,206 unchanged statuses, three changed; 644 baseline-only
and 831 feature-only fingerprints. Focused state/attestation comparison: 82 shared fingerprints with
all statuses unchanged, 145 baseline-only and 262 feature-only. Snapshot comparison: 733 shared,
726 unchanged statuses, seven changed, 100 baseline-only and 181 feature-only. Method rewrites can
reuse instruction indexes for different operations; these matches do not establish semantic lineage
or prove that every new fingerprint is an inherited kill.

Residuals remain visible in raw XML. The focused SourceSymmetry survivor removes an explicit
`Objects.requireNonNull`; the next dereference still throws NPE. Keep the clear boundary check.
Unused DFS bookkeeping was deleted, not counted as a test kill. Main's broad census **33 K + 370 NC**
reflects child-JVM CLI execution outside PIT's instrumentation coverage; CLI/browser assertions do
not turn NC into kills. DynamicStep's prior uncovered guard-outcome mutation is killed by a literal
error-outcome regression. The snapshot population retains legacy/rewritten survivors and uncovered
paths; they are neither all confirmed bugs nor a claim of general equivalence.

A custom SharedState adapter reproduced a real trust gap: a later copy could return DynamicState
although the retained initial object was custom. `Program.initialStateType` now exposes the actual
stored class without invoking another copy, allowing rejection before callbacks/dispatch. A separate
fresh-reader pass confirmed this keeps core independent of DSL types.

## CodeRabbit review and dispositions

CodeRabbit CLI 0.8.1 completed three actual implementation reviews, not heartbeat-only or skipped
reviews. The initial 32-file review returned three Major, one Minor and four Trivial findings:

| Finding | Disposition |
|---|---|
| Major: remove certified INCOMPLETE expectation exception | Declined: approved Spec 92 R7 and the set contract require inconclusive handling, matching existing source policy. INCOMPLETE never fulfills an expected VIOLATION or seeds exact agreement. A focused expected-VIOLATION/actual-INCOMPLETE test checks that the label remains INCOMPLETE and Markdown says inconclusive, not matched. |
| Major: verify property provenance/foreign declaration | Already enforced by DslInvariant.symmetryNodes; public-constructor properties and foreign loader declarations reject before grouping. Public-property, absent-permit and foreign-declaration regressions verify those paths. No bypass fix was needed. |
| Major: verify declarative BITSTATE staging/harness restriction | Retained the explicit factory rejection required by L1 R5. Harness.runProgram now documents unsupported declarative BITSTATE; direct/harness/runner/CLI tests verify rejection across strategies. No premature L2 feature is enabled. |
| Minor: exact factory documentation | Corrected source-only summary/property/exception wording. |
| Trivial: browser verdict substring match | Changed new declarative assertions to exact trimmed badge equality. |
| Trivial: explicit CLI strategy identity | Assert the single returned row names the requested strategy. |
| Trivial: finite failing-property oracle | Added exhaustive split-increment final-value reference and concrete enabled failure checks across all four strategies/backends, without equating reduced visit counts to DFS. |
| Trivial: CLI guard reasons | Assert specific phase/store/source-only witness errors, not exit status alone. |


The second review completed 35 files with one Major and two Trivial findings. The Major was valid:
legacy snapshot capture could expose failed-attempt scratch values through checked full/partial
runner results and harness reports. The dedicated `TraceReplayer.captureDeclarativeSnapshots` now
validates actual enabled outcomes, records only committed states and repeats the last committed
snapshot for a failed final attempt. All checked JSON producer paths select it; ordinary replay and
source capture retain their existing APIs and semantics. The Trivial findings were patched with a
nonempty completion witness and meaningful false public-constructor property/root provenance tests.

The third review completed 38 files with one Minor and two Trivial findings. All were patched:
remove contradictory pending-review ledger prose, assert CLI failure-report soundness explicitly,
and assert independently derived true/false property observations during typed permutation checks.
No CLI rerun is used merely to double-check these Minor/Trivial documentation/assertion changes.

## Raw evidence

Local artifacts use `/private/tmp/interleave-l1-` as a prefix. These are diagnostic artifacts, not
committed build products:

- `baseline-build.log`, `baseline-default.xml`, `baseline-affected.xml`, `baseline-program.xml`,
  `baseline-replayer.xml`, `baseline-focused.xml`, `baseline-snapshot-scope.xml`.
- `wide-affected.xml`, `final-focused.xml`, `final-snapshot-scope.xml`, `release-default.xml`;
  `final-source-visitor.xml` and corresponding `.log` files and `focused-comparison.txt`, `snapshot-comparison.txt`,
  `default-comparison.txt` preserve measurement/comparison details.
- `release-source-build.log`, `snapshot-browser.log`, `private-javadoc-final.log`;
  `snapshots-red.log`, `snapshots-green.log` and `snapshots-contract.log` retain regression history.
- `coderabbit.ndjson`, `coderabbit-final.ndjson`, `coderabbit-snapshots.ndjson` retain completed
  review output and finding severities. This ledger records independent dispositions.

## PR #136 feedback refinement

All three posted findings were independently validated and patched:

| Severity / finding | Fix and regression |
|---|---|
| Minor: direct DslLoader calls bypassed the format discriminator | Require exactly `declarative` before validation/certification. Direct calls with `typed`, empty or missing format all failed the new regression before the fix; explicit declarative definitions still certify. |
| Major: a custom first-copy adapter could retain a trusted-looking snapshot | Record only the constructor input class in Program before deepCopy and expose `initialStateInputType()`. Require both input and stored classes to be DynamicState. A retained-snapshot adapter demonstrates post-construction root mutation and now rejects without another adapter callback; ordinary Program copying remains unchanged. The regression failed before the fix. |
| Trivial: missing no-property POR oracle | Checked static POR and DPOR, both exact backends, retain terminal x values `{1, 2}` from the independent split graph. Every completion witness checks actual enabled choices/outcomes and termination. This test passed before the admission fixes; it strengthens coverage rather than claiming another production defect. |

Post-feedback `build javadoc` passes **1,948 tests**, zero failures/errors/skips. All **218 browser
assertions** pass on regenerated artifacts, and the external built-jar consumer passes including the
new original-input type query. A focused CodeRabbit review of all seven feedback files completed
with **zero findings**. It was required for the Major correctness-sensitive provenance fix, not a
repeat check of minor/trivial feedback. Raw artifacts use `/private/tmp/interleave-pr136-feedback-`:
`red.log`, `build.log`, `browser.log`, `coderabbit.ndjson`, and the focused `pitest.log`/`pitest.xml`.
The post-feedback focused census passes **602/712 K + 89 S + 21 NC**, with zero resource/run-error
or other statuses: Program **31/31 K**, SourceSymmetry **108/109 K + 1 S**, DslLoader **463/572 K
+ 88 S + 21 NC**. All mutations on the new discriminator guard and original-input type check are
killed. The sole SourceSymmetry survivor remains the explicit null-check removal whose next
dereference still throws. Loader residuals remain in the raw XML; this patch makes no general
equivalence or whole-loader closure claim. The comparison uses the previously recorded disjoint
per-class populations (594/705 K + 90 S + 21 NC), not a newly measured full PR-head baseline. Of 273
matching instruction fingerprints, 272 retain status and one changes; 432 old and 439 new identities
mostly reflect shifted instruction indexes. Do not infer semantic mutant lineage from reused indexes.
Comparison artifacts: `baseline.xml`, `comparison.txt` under the same feedback prefix.

The previous broad/default measurements above retain their original scope and revision. The
default gate was not repeated for this narrow feedback patch; the three changed production classes
receive a focused census in addition to the full integrated build and consumer checks.

## Assurance limits

Admission covers one trusted declarative family, not general Java, typed JSON, arbitrary callbacks
or semantic expression normalization. Generic non-source replay remains unchanged. The dedicated
snapshot API checks state/action execution; strict positive report attestation separately validates
property/terminal observations and provenance. Failed attempts publish no successor. FINAL properties
may conservatively fall back to exhaustive search. Representative visit counts are not general
speedups or counts of all concrete schedules, and no whole-process memory or whole-checker mutation
closure is claimed. Source resource-prefix authentication remains source-only. L2 approximate
support, CI results and merge are pending; none is claimed here.
