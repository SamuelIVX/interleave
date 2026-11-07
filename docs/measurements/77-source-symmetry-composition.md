# 77 — Source symmetry strategy composition evidence

## Fresh merged baseline

Measured on PR #118 merge `5f04f83`, before implementation. One Gradle worker and one PIT thread;
same operators, filters, target tests, runtime and 94% floor. `clean build javadoc pitest` passes:
1,588 tests, default 433/449 K + 16 S; zero uncovered/resource errors, R5/R6/R7 pass.
Frozen XML: `/private/tmp/interleave-symmetry-composition-default-baseline.xml`.

A separate pre-edit scope of `DporExplorer,StaticPorExplorer` gives DPOR 277/301 K + 12 S + 12 NC,
static POR 123/132 K + 3 S + 6 NC. Combined 400/433 K + 15 S + 18 NC. These scopes are separate
from the default state/CBS scope and must not be reported as a whole-checker score.
Frozen XML: `/private/tmp/interleave-symmetry-composition-scoped-baseline.xml`.

## Independent acceptance oracles

Four identical one-action dependent increments have 16 binary owner masks. Partition by completed
owner count gives five layers; independently summing binomial coefficients gives 1+4+6+4+1=16.
All strategies must actually visit five representatives and reach shared x=4. CBS costs zero for
any order: each selected owner terminates, so the next switch is forced. DPOR's writes conflict,
so sleepers stay empty. Java asserts literal five over explorers, runner, harness and real CLI.

For two split load/store increments, K=0 forces each owner to finish both actions before switching,
so x=2; attempts to switch while the first owner is enabled retain INCOMPLETE evidence. Schedule
[0,1,1,0] loads zero twice and writes one twice, with exactly one preemption: switch 0 to 1 costs
one, switch back after owner 1 terminates is free. Thus the first lost-update bound is literal one.
Compare iterative ordinary/symmetry bounds, and compare all terminal classes separately at K=0,1,2.
Prior independent enumeration gives final values {1,2}; final x==2 preserves success and violation.

For branch interference, a fixed writer changes x from zero to one. Each identical reader chooses
local r=1 before that change or r=2 after it; two later additions allow final y in {2,3,4}.
Starting x=1 fixes y=4. An independent Python BFS over scheduled branch choices, local
assignments, additions and the fixed write confirms both literal value sets. Tests vary initial x and final predicates y==2/3/4 across all strategies,
compare exhaustive DFS, and replay all emitted witnesses. A fixed writer can also make a guarded
loop terminate before its limit, while scheduling loops first permits a cutoff; starting x=1
permits only completion. These cases exercise future shared dependencies and source final checks.

Direct context tests construct actual committed successors with owner 0/1 exchanged, then check
mapped last ownership, initial -1, lower/equal/greater costs and minimum retention. DPOR contexts
vary canonical owner, instruction PC, empty/nonempty coverage, caller mutation, clear and fresh copy.
Wrong program/property/algorithm bindings reject at each of the four public explorer entries.


## Default population and ratchet

First implementation default run completed PIT with 435/451 K + 16 S, zero NC or resource/run
errors. The old denominator assertion correctly failed (expected 449, generated 451). The two new
mutants are the CBS symmetry capability branch and its `requireRun` call, both killed by actual
wrong-run explorer entry tests. This is production population drift; scope, operators, target tests,
filters and resource settings are unchanged. `EXPECTED_TOTAL_MUTANTS` becomes 451 and CBS's notice
becomes 127/127. The 94% floor remains: 94*451=42394 requires 424 kills, leaving eleven kills of
headroom versus the prior ten at 433/449 (94*449=42206 requires 423). That extra one is explicitly
population-induced slack, not a weakened percentage. Independent integer multiplication and direct
ceiling arithmetic agree.

Identity census includes class, method descriptor, mutator, every nested instruction index and
block (not merely empty container text). All 324 state-package identities/statuses are unchanged.
Of 125 old CBS identities, 116 remain identical; nine killed `explore` identities shift indices and
blocks after guard insertion and are replaced by nine killed counterparts (indices +16, blocks +2) plus the two new guards.
Thus 440 exact shared IDs retain every status, with nine removed killed / eleven added killed IDs.
No old survivor becomes a claimed kill. Frozen final XML and identity comparison:
`/private/tmp/interleave-symmetry-composition-default-final.xml` and
`/private/tmp/interleave-symmetry-composition-default-identity.txt`.

## Local review and browser checks

CodeRabbit 0.8.1 completed an actual `review_completed` over 20 files, with three trivial findings:
missing null-strategy/custom-DPOR-factory regression assertions, missing reduced-strategy CBS-option
CLI assertions, and fuller store Javadoc. All independently validated and patched; no CLI rerun for
trivial feedback. Review recorded through agent-setup. Private-member strict doclint for the new
store passes. Later edits are documentation, census accounting and regression coverage, with no
new model semantics.

Full build/tests/Javadoc passed before the first mutation run: 1,609 tests; final public-clear
regression brings the integrated suite to 1,610 passing tests. Browser 49/49, zero console errors;
normalization's existing 22 verdict cases and strict symmetry-identity assertions pass against fresh
Java output. No native-stream protocol, dependency or CI parallelism changes.


## Affected search scope

The additional untouched-merge DFS baseline was measured in a separate detached worktree at
`5f04f83`: 84/85 K + 1 S. XML is frozen at
`/private/tmp/interleave-symmetry-composition-dfs-baseline.xml`.

First affected scope (before the public-clear regression): 577/612 K + 17 S + 18 NC, zero resource/
run errors. Per class: DPOR 283/307, static POR 125/134, DFS 86/87, SymmetryDfsExplorer 5/5,
SymmetryStateStore 78/79. The one new-store survivor removes its unbounded `visited.clear()`.
The former store was private/fresh per call; the new public store can legitimately be reused, so
that disposition cannot be inherited. Added a real regression: mark an orbit, query its swapped
representative, clear it, verify empty membership, then run DFS twice with that same public store.
Focused new-store recheck: **79/79 K**, zero survivors/uncovered/resource errors, all 79 identities
retained and exactly one S→K. Final combined affected counts are **578/612 K + 16 S + 18 NC**:
the four unchanged classes from the first scope plus this final store-only measurement. This is
an explicitly composed scope result, not a second combined run. Frozen XMLs:
`/private/tmp/interleave-symmetry-composition-scoped-first.xml` and
`/private/tmp/interleave-symmetry-composition-store-final.xml`.

Raw identity census (method descriptor + mutator + nested indices/blocks): DPOR shares 117 IDs,
184 removed/190 added; static POR shares 111, 21 removed/23 added; DFS shares 77, 8 removed/10 added.
Insertion changes instruction/block offsets. One DPOR raw ID is reused by a different call:
`dporDfs`, index 708/block 179, NonVoidMethodCallMutator was `writesOverlap` at baseline line 272
(K), now `hasEnableDisableInterference` at line 278 (S). It is not the same semantic mutation.
The latter was already a survivor at its previous offset. Do not describe raw IDs as all unchanged.
Matching unchanged source lines through the actual diff, with method descriptor, mutator,
description and per-status multiplicities, retains **every inherited mutation's status** for all
three explorers. No inherited semantic survivor is counted as a new kill or newly regressed.
The extra populations are six killed DPOR context/binding mutations and two killed binding mutations
each in static POR and DFS. Method/descriptor/mutator/description/status residual multisets agree
exactly with fresh baselines. Census artifacts are under
`/private/tmp/interleave-symmetry-composition-search-identity.txt`.

**Later triage:** [Spec 80](../specs/active/80-explorer-residual-triage.md) remeasures these three
explorers at merged main `b51fcf7`, reconciles all 34 residuals with Specs 30–39 and records their
contract limits and reopening owners. The historical snapshot below stays unchanged; Spec 80
does not credit dispositions as kills or claim universal equivalence.

The 34 inherited non-kills below remain visible as limitations of these broader explorer scopes:
16 raw survivors and 18 uncovered cases. They are not established equivalent or closed by #10;
this stage adds no whole-checker mutation completeness claim. Several lie in legacy/unknown-property
fallback or redundant terminal branches, but that observation is not an equivalence proof. The new
symmetry store and its binding/context paths are covered separately as above. Retaining this ledger
does not silently reopen deferred/unselected work or weaken the default R5/R6/R7 gate.
Lines and indices below belong to the first affected-scope snapshot. Later Javadoc shifts raw
source-line offsets; the listed kernel statements and mutation identities are unchanged.

| Class | Method | Line | Instruction index | Raw status | Mutation |
|---|---|---:|---:|---|---|
| DporExplorer | dfsDfs | 405 | 141 | SURVIVED | negated conditional |
| DporExplorer | dfsDfs | 408 | 171 | NO_COVERAGE | negated conditional |
| DporExplorer | dfsDfs | 406 | 153 | NO_COVERAGE | removed call to java/util/List::copyOf |
| DporExplorer | dfsDfs | 406 | 155 | NO_COVERAGE | removed call to java/util/List::copyOf |
| DporExplorer | dfsDfs | 406 | 160 | NO_COVERAGE | removed call to dev/samhb/interleave/search/TracePrefix::trace |
| DporExplorer | dfsDfs | 407 | 166 | NO_COVERAGE | removed call to java/util/List::add |
| DporExplorer | dfsDfs | 423 | 246 | SURVIVED | removed call to java/lang/Integer::intValue |
| DporExplorer | dfsDfs | 409 | 176 | NO_COVERAGE | removed call to dev/samhb/interleave/search/StateVisitor::onTraceCreated |
| DporExplorer | dporDfs | 210 | 329 | NO_COVERAGE | negated conditional |
| DporExplorer | dporDfs | 264 | 591 | SURVIVED | negated conditional |
| DporExplorer | dporDfs | 155 | 20 | SURVIVED | removed call to java/util/List::isEmpty |
| DporExplorer | dporDfs | 171 | 148 | SURVIVED | removed call to java/util/List::removeIf |
| DporExplorer | dporDfs | 208 | 311 | NO_COVERAGE | removed call to java/util/List::copyOf |
| DporExplorer | dporDfs | 208 | 313 | NO_COVERAGE | removed call to java/util/List::copyOf |
| DporExplorer | dporDfs | 208 | 318 | NO_COVERAGE | removed call to dev/samhb/interleave/search/TracePrefix::trace |
| DporExplorer | dporDfs | 209 | 324 | NO_COVERAGE | removed call to java/util/List::add |
| DporExplorer | dporDfs | 263 | 585 | SURVIVED | removed call to java/lang/Integer::intValue |
| DporExplorer | dporDfs | 278 | 708 | SURVIVED | removed call to dev/samhb/interleave/por/IndependenceRelation::hasEnableDisableInterference |
| DporExplorer | dporDfs | 306 | 871 | SURVIVED | removed call to java/lang/Integer::intValue |
| DporExplorer | dporDfs | 211 | 334 | NO_COVERAGE | removed call to dev/samhb/interleave/search/StateVisitor::onTraceCreated |
| DporExplorer | dporDfs | 265 | 599 | SURVIVED | removed call to dev/samhb/interleave/dpor/HappensBefore::record |
| DporExplorer | explore | 98 | 78 | SURVIVED | removed call to java/util/Optional::empty |
| DporExplorer | lambda$dporDfs$2 | 171 | 6 | SURVIVED | removed call to java/util/Set::containsAll |
| DporExplorer | lambda$dporDfs$2 | 171 | 7 | SURVIVED | replaced boolean return with false for dev/samhb/interleave/dpor/DporExplorer::lambda$dporDfs$2 |
| StaticPorExplorer | explore | 97 | 67 | SURVIVED | removed call to java/util/Optional::empty |
| StaticPorExplorer | porDfs | 235 | 490 | NO_COVERAGE | negated conditional |
| StaticPorExplorer | porDfs | 191 | 265 | SURVIVED | removed call to java/lang/Integer::intValue |
| StaticPorExplorer | porDfs | 230 | 455 | SURVIVED | removed call to dev/samhb/interleave/core/Configuration::allTerminated |
| StaticPorExplorer | porDfs | 233 | 472 | NO_COVERAGE | removed call to java/util/List::copyOf |
| StaticPorExplorer | porDfs | 233 | 474 | NO_COVERAGE | removed call to java/util/List::copyOf |
| StaticPorExplorer | porDfs | 233 | 479 | NO_COVERAGE | removed call to dev/samhb/interleave/search/TracePrefix::trace |
| StaticPorExplorer | porDfs | 234 | 485 | NO_COVERAGE | removed call to java/util/List::add |
| StaticPorExplorer | porDfs | 236 | 495 | NO_COVERAGE | removed call to dev/samhb/interleave/search/StateVisitor::onTraceCreated |
| DfsExplorer | dfs | 226 | 334 | SURVIVED | removed call to dev/samhb/interleave/core/Configuration::allTerminated |


## Final local review corrections

A final independent pass reproduced an entry-path reporting defect with the pre-fix compiled
runner: supplying the public symmetry store through `.stateStore(...)` returned three quotient
visits with `symmetryReduction=false`. Runner store creation now rejects this ordinary-custom-store
path and directs callers to the explicit boolean. The shared helper covers all four strategies and
iterative CBS. Four parameterized regressions check both concrete-store and factory paths; an
independent JVM probe reproduces the old result and confirms the corrected rejection. Logs:
`/private/tmp/interleave-symmetry-marker-red.log` and `interleave-symmetry-marker-green.log`.
No TestResult layout/API or serialization protocol change is involved.

This substantive local correction warranted the second/final CLI review, rather than a rerun for
the earlier trivial feedback. CodeRabbit completed `review_completed` on all 23 files: 13 trivial
and two minor findings, no major/critical findings. Valid documentation, table/CLI status and option
regressions were patched locally. Strategy-null exception prose was not added to the private store
helper: the builder already rejects null strategies in checked mode. No speculative exception
contract is claimed for an unreachable private path.

The optional suggestion to move DFS binding validation ahead of its private field assignments was
not taken: each successful explore fully resets those fields, no rejected result is returned, and
no observable partial run is exposed. The binding already rejects before clearing membership or
executing callbacks. Its actual exception contract is now documented. Unbounded membership on a
CBS-bound store remains a supported independent value-orbit map, just as the StateStore overloads
are separate contracts; the docs explicitly say it does not mark/check CBS cost coverage. CBS
explorers use the required cost/last-owner overload. No new strategy rejection is invented.

CLI validation now records whether `--max-preemptions` was actually parsed instead of scanning
all argument values. An isolated old-CLI probe reproduced rejection when the source filename itself
was literally `--max-preemptions`; a child-JVM regression uses that filename in a temporary working
directory. CBS JSON tests assert actual strategy, count, marker and bound: the one-action counter
is exhaustive at K=0 (every switch follows termination), so iterative CBS also returns bound zero.
After these regressions the suite has 1,615 cases. No third CLI review is run for minor/trivial
feedback.


## Publication verification

Final integrated core correction: `build javadoc pitest` passed with default **435/451 K + 16 S**,
zero NC/resource/run errors, R5/R6/R7 pass. The later CLI explicit-flag correction, documentation
and stronger option/JSON assertions passed the complete **1,615-test** suite, build and Javadoc;
private-member doclint also passes. These later edits change no default mutation-target semantics
or mutation identities. All 451 IDs/statuses match the preceding implementation default report.
Publication XML: `/private/tmp/interleave-symmetry-composition-publish-default.xml`; build/test log:
`/private/tmp/interleave-symmetry-composition-feedback-final.log`.

Bitstate/custom-store symmetry, arbitrary callbacks, dynamic owners, weak memory and liveness stay
outside this completed #10 source/exact-strategy boundary. Merge of this final stage completes #10;
do not start #11/#12. Publication is draft; no CI/review watching is authorized after publication.


## PR #119 trivial feedback follow-up

The DPOR symmetry path no longer calls ordinary `isVisited` before exact mapped sleep coverage;
its result was unused and incurred one extra whole-state canonicalization for every visit. The
checked store factory and explorer binding guarantee source-only symmetry, so testing the final
store type is sufficient. Other stores retain the same lookup and source/approximate coverage
behavior. Successful symmetry visits still mark membership as before.

The existing independently derived four-owner regression now also calls each explorer directly,
asserts literal five representative visits and only completion, and replays each witness to shared
x=4. These are the same five completed-owner layers and four increments justified above; no new
oracle formula or state-count claim is introduced. This is an optimization of unused work, not a
new pruning rule. Earlier mutation figures remain snapshots of their stated revisions/scopes.

Validation for this follow-up: build, all 1,615 tests and strict Javadoc pass. No CodeRabbit CLI,
PIT or browser rerun: this removes an unused read, changes no default PIT targets or viewer code,
and retains the original pruning decisions. Prior mutation/browser figures remain historical.


A second trivial PR #119 comment strengthens original-owner evidence: the four-strategy counter
fixture now asserts the literal emitted schedule `[0,1,2,3]`, including its single-trace multiplicity,
independently of replay and source metadata. All enabled choices are ordered by original ID;
shared conflicting writes prevent sleeping/component exclusion, and one-action owners terminate
before every CBS switch. The first path therefore chooses 0, then 1, then 2, then 3; the other
orders reach covered orbits. Consistent relabeling of both trace and metadata now fails this literal
assertion. Existing DFS identity assertions and replay checks remain intact. No production changes.

Owner-ID follow-up validation: build and all 1,615 tests pass; Javadoc task succeeds unchanged.
No CodeRabbit CLI, PIT or browser rerun for this test/documentation-only change.
