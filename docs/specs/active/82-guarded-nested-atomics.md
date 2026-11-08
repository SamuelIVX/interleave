<!-- Guarded private atomic semantics, bounded enabledness and reduction/evidence preservation. -->
# 82 — Guarded nested atomic blocks

## TL;DR

Implement B4 / bundle F from the approved [follow-up inventory](../../plans/post-task10-follow-ups.md)
after PR #123 merged at `9a5adb9`. A reached false inner guard disables the **entire outer action**:
no tentative shared/local/array write commits and the outer PC stays unchanged. The next scheduler
position is the same committed state. A later committed action by another owner may enable it.
Inner blocks add no scheduling, observation, commit or rollback boundary.

One feature PR combines the private guard representation, bounded enabledness, budgets, dependency
footprints, original evidence/replay and all built-in exact strategies. No helper-interior calls,
new caps, dynamic owners, weak memory, syntax beyond nested `atomic when`, schemas or native-stream
changes. Task #10 remains complete; parked work and #11/#12 stay excluded.

## Ordered semantics

- Evaluate the outer entry guard against committed state. False disables without entering its body;
  a modeled guard error stays enabled and becomes a STEP failure when attempted.
- Evaluate reached private statements in source order on one tentative copy. Inner guards read
  preceding tentative writes, including owner-local arrays. A true guard executes its private body;
  a false guard aborts the entire attempt, including writes before the guard.
- A false guard is **not** an if/else condition: do not skip it and continue the outer block. Unchosen
  branches and zero repetitions never reach their guards. Repeated/looped guards are evaluated at
  every reached expanded occurrence against that occurrence's tentative values.
- The first reached terminal condition wins. Assertion/runtime failure or loop cutoff before a
  later false guard remains enabled and observable. False guard before a later error/cutoff disables
  without observing the later code. Errors in a reached guard are classified at its original span.
- Disabled actions create no search edge or failure/cutoff evidence. A direct defensive transition
  returns BLOCKED with parent values and PC. Fixed schedules reject selecting a disabled action.
  Failure/cutoff discards the entire tentative state as before; only complete success advances.

## Representation, work and enabledness

Keep unguarded inner braces flattened. Represent a guarded inner body as a private GUARD node,
retaining its condition, source span, children and expansion ancestry. Lower only the outer ATOMIC
as one scheduled instruction. Each expanded private guard contributes one conditional evaluation
plus its body to the existing outer 64-instruction budget. Inner blocks have no separate work
budget scope; the original outer-atomic admission checks account for their complete expanded work.
Braces alone still cost zero. All guard expressions and both
conditional arms contribute to the existing 5,000-node expansion budget. Source depth remains 16.
Scheduled action counting is unchanged: private work must not inflate the global 64-action count.
Unused helpers and zero-repeat/unselected code still undergo the existing parsing/type checks.

While collecting complete read/write footprints, note whether the atomic tree contains private
guards. Ordinary atomics keep their cheap outer-guard enabledness path. Guarded interiors require
one bounded dry run on a deep copy to determine enabledness. It observes no intermediate state,
creates no scheduling edges, mutates no parent values and records no trace. A false result disables;
a modeled failure/cutoff during this preview keeps the action enabled for its actual evidence.
Actual transition execution repeats the deterministic body on its attempt-owned scratch and uses
its result, rather than publishing preview state or preview evidence. Bound each preview by the
same admitted private/expression work; do not raise caps or claim unchanged performance.

GUARD footprints include its test and complete nested effects/error dependencies. Existing static
POR/DPOR independence and enable/disable probes therefore see every dependency that can change a
preview result. Existing whole-frame source symmetry compares the GUARD semantic node and its
owner-relative expressions, rejecting tid-sensitive groups as before. No unchecked new callback
or reduction rule is introduced; exact DFS remains the differential oracle.

## Scope and independent verification

Change SourceCompiler parsing/work and SourceInstruction footprint/private execution/enabledness.
Update the previous guarded-nesting rejection fixtures deliberately to positive coverage; preserve
malformed guard/type/helper-call rejections. Update language/control/budget docs, README, example,
teaching acceptance and follow-up status together. No explorer, store or replay API change.

Regression oracles cover tentative guards, rollback, later enablement, hidden intermediates, nested
if/repeat/while paths, unchosen guards, outer false guards, ordered failures/cutoffs, original guard
error span, deep-copy isolation, budget/depth boundaries, helper definitions and symmetry/replay.
One handoff model has exactly states `(x,ready) = (0,false), (0,true), (3,true)` and first schedule
`[1,0]`: only the ready writer is initially enabled, then the one outer commit. Two source owners
that each increment once have terminal x=2; serial orders `[0,1]` and `[1,0]` independently confirm
it. Literal expectations must not be computed by the production encoder/replayer.

Measure fresh main build/tests/Javadoc and compiler/instruction PIT before edits, freeze XML, then
measure final populations and unchanged-method lineage. Keep mutators, filters, targetTests, floors
and CI parallelism fixed. All Gradle runs use `--max-workers=1 -PpitestThreads=1`. Run integrated
checks, exact CLI/report/normalization validation and the selective CodeRabbit review; retain raw
survivors and unresolved scope honestly. Publish a draft PR and stop for Sam's update.

## Verification

Fresh merged main `9a5adb9` passes build, strict Javadoc and **1,660 tests**. Baseline affected
PIT: compiler **860/911 K + 29 S + 22 NC**, instruction **187/189 K + 2 S**, zero resource/run
errors. Default baseline **436/451 K + 15 S**, R5/R6/R7 pass. Baseline XMLs are
`/private/tmp/interleave-guarded-atomic-baseline.xml` and
`/private/tmp/interleave-guarded-atomic-default-baseline.xml`.

All **29 initial positive regression invocations fail** against the former guarded-nesting rejection.
Four additional strategy cases then protect the initially-true guard's disabling race with complete
footprints and known-empty property observations. The final integrated build, **1,693 tests** (33
additional invocations), zero failures/errors/skips, and strict Javadoc pass. The existing depth boundary
remains 14 atomic bodies plus owner/program = 16 levels; no cap changed.

Private work boundary: one guard + 63 assignments = 64, accepted; one more assignment = 65,
rejected. For expression admission, each balanced sum of 1,249 literal ones has 2,497 nodes. Two
sums plus equality (4,995), assignment target/value (2) and final-property expression (3) total
**5,000**; an extra true guard makes **5,001**. Independent token enumeration gives 2,502 leaf
occurrences plus 2,498 operator occurrences = 5,000, confirming the literal acceptance boundary.

Final affected PIT: compiler **859/910 K + 29 S + 22 NC**; instruction **210/214 K + 4 S**.
[The complete residual ledger](../../measurements/82-guarded-nested-atomics.md) records all 55 raw
residuals, changed populations and exact unchanged-method identity accounting. Scoped R6 passes;
R5/R7 are not scoped aggregate results. No resource/run-error detection is credited as a kill.
Final frozen affected XML: `/private/tmp/interleave-guarded-atomic-complete-scoped.xml`.

The actual handoff CLI artifact at `/private/tmp/interleave-guarded-atomic-cli.json` has four exact
PASS rows, three visits each and soundness=true. All four rows normalize to PASS/three visits using
the actual visualizer function. All 22 existing compatibility verdict cases and four symmetry-mode
assertions pass. Browser rendering is not rerun because the producer schema, normalization and
renderer are unchanged; actual new Java output is checked through the normalization boundary.

CodeRabbit CLI 0.8.1 completed an actual 16-file review with one minor and one trivial status-doc
finding, independently validated and resolved by recording measured results and consistent final
status. No production defect was reported. Do not rerun the CLI for that minor documentation
feedback; subsequent changes are the duplicate-budget-check removal, guard-race regression,
measurement ledger, historical roadmap cross-reference and final evidence prose. Integrated checks
cover the final production/tests. Remote CI remains unverified; publish one draft PR and stop.

Final default gate passes **436/451 K + 15 S**, with all **451 exact mutation identities/statuses
unchanged** from the fresh baseline, zero uncovered/resource/run errors and unchanged R5/R6/R7
passing. Frozen final XML: `/private/tmp/interleave-guarded-atomic-default-final.xml`; command log:
`/private/tmp/interleave-guarded-atomic-default-final.log`. All local runs retain one worker/thread;
operators, filters, targetTests, floors, per-class notices and CI parallelism are unchanged.

Changed Markdown links, raw residual counts and whitespace checks pass. The actual CodeRabbit
completion is saved through agent-setup review-record. Final ledger/status prose follows measured
results; remote CI is pending. One feature PR includes both semantics and integration.
