# Spec 74: Source cutoff-witness simplification

## Objective and selected scope

Implement active 9-F9 after merged PR #115 and the roadmap-only deferral update b3d1a9d.
9-F5 runtime threads remains deferred; its unapproved local architecture draft is not this spec.
Keep other deferred/conditional features parked. Bundle one dedicated library operation, explicit
CLI presentation, source/property/limit validation, independent tests, examples and documentation.

A shorter INCOMPLETE witness explains an exhausted bound; it is never a failure reproduction or
proof about unexplored executions. DeltaDebugger continues rejecting INCOMPLETE. No search,
reduction, visited-store, limit, verdict, failure-evidence or trace-field change is required.

## Additive bounded shortest-subsequence operation

[Spec 85](85-shortest-cutoff-subsequence.md) adds an opt-in library operation with an explicit
candidate budget and shortest-length proof status, scoped to subsequences of the supplied witness.
Its best-found answer can be unproven and need not be deletion-1-minimal after a budget stop.
The existing simplify method and CLI below retain their original chunk-deletion contract. Both
operations share original authentication and candidate execution/replay; exact cutoff values and
INCOMPLETE meaning remain unchanged. CLI proof presentation belongs to the next B7 stage.

## Input and preserved contract

`CutoffSimplifier.simplify(Program, Trace, Invariant)` accepts source programs and actual INCOMPLETE
traces with non-null LOOP_LIMIT or PREEMPTION_BOUND evidence, exact source digest and no failure.
Authenticate the original trace before attempting changes. Reject legacy/bare/resource-only
incomplete traces, ordinary failures/completions, stale/forged source or cutoff evidence, and
invalid schedules. Null property means no additional global predicate is supplied; otherwise use
the actual source property. Always validate the prefix is nonterminal and the omitted bound action
really remains enabled and reproduces the cutoff.

Retain the **entire original CutoffEvidence value**: reason, owner, instruction PC, limit,
iterations, actual source span and expansion path. LOOP_LIMIT must still reproduce that exact
private/global cutoff on scratch. PREEMPTION_BOUND must retain the same K: all prefix switches fit
within K, and the omitted enabled choice would exceed K under the real enabledness rules. Do not
execute a scheduling-cutoff attempt or relabel it as a loop cutoff.

Deletion may change unrelated values/branch choices, but cannot introduce an earlier assertion,
property failure, runtime error, deadlock, completion or another cutoff. Regenerate actual outcomes,
PC/source labels and ancestry from each accepted prefix. Never copy supplied outcomes or old
executedSteps onto a shortened schedule. Return immutable INCOMPLETE evidence and validate it
through invariant-aware replay. Input traces/programs remain untouched and operation state is local.

## Algorithm and limits

Use bounded, iterative chunk deletion. Start with two chunks (one when only one choice remains),
try deleting each chunk, and restart on a strictly shorter valid candidate. Refine to individual
choices before terminating. Empty prefixes are allowed when the identical omitted action cuts off
at the root. Use long partition arithmetic; no recursive simplification stack.

The result is deletion-1-minimal: no single remaining choice can be removed while preserving this
contract. It is not a globally shortest schedule or a complexity/performance improvement claim.
Bound passes by the original prefix length plus one. Enumerate distinct chunk sizes from powers
1 through 31 capped by the current length, so refinement itself is finite for every int-sized list.
Accept only strictly shorter candidates. Work is opt-in; reuse ExecutionDriver, Trace.create and TraceReplayer rather than another interpreter
or duplicated preemption-cost calculator. Invalid candidate schedules/classifications are rejected;
actual model/property errors are not swallowed as generic RuntimeException failures.

## CLI and existing consumers

Add `--simplify-cutoff` to source runs. Require one source program, `--json`, `--store exact` and an
explicit `--strategy`, so there is no ambiguous choice among strategy/store witnesses. Select the
first incomplete witness from that one result row; require its verdict to be INCOMPLETE and fail
clearly if no actual cutoff exists. A mixed run that found VIOLATION is not converted into
cutoff-only output. Output the one
simplified TraceRecord JSON with freshly captured committed snapshots, rather than a benchmark
report or changed benchmark counts. Existing run/report behavior is unchanged without the flag.

The output uses the existing direct trace schema, so the viewer loads it without a new normalizer
or renderer. CLI/library documentation must say INCOMPLETE and unchanged bound explicitly; fewer
steps never establish PASS. Existing reporting can still retain the original search evidence.

## Independent acceptance

- Four unrelated noise actions before a root true zero-limit loop reduce from four choices to zero;
  the initial snapshot and identical loop cutoff remain. Noise values may change.
- A required write enabling the target's outer guard stays; removing it disables the cutoff.
- Three noise actions before one bounded-loop body/check pair reduce from five choices to two;
  preserve omitted PC 2, limit/iteration 1 and actual loop ancestry/location.
- At K=0, a prefix of two actions by a still-enabled owner reduces to one, not zero. The omitted
  other owner is the same instruction and its switch costs one. If the last owner terminated,
  its free switch must not be accepted as a preemption cutoff.
- At K=1, prefix [0,0,1,1] with both owners live and omitted owner 0 at PC 2 reduces to [0,0,1].
  Two owner-0 actions are needed for that PC; the owner-1 choice is needed to exceed the same K.
- Re-simplification is stable; each single deletion of the result fails independent witness checks.
  Cover all four explorers' actual source witnesses, guards, assertions/errors, property timing,
  empty/one-choice input, malformed/stale evidence and repeated use. Keep ordinary failure and
  resource-stop behavior unchanged.

Derive literal counts/costs/locations outside Java and confirm by independent schedule enumeration.
Test the actual child-process CLI, its validation errors, exact JSON and fresh snapshots; load its
artifact in the browser. Keep all existing normalization/legacy fixtures intact.

## Verification and publication

Fresh merged baseline clean build/tests/strict Javadoc and default mutation gate precede edits.
The new simplifier has no prior production mutation population; measure its affected scope after
implementation, retain raw non-kills with exact dispositions, and separately remeasure the default
gate. Do not combine populations or alter targets/filters/operators/floors/resources for scores.
Run integrated checks, actual loop/preemption CLI examples, browser/normalization checks, Markdown
validation and required completed CodeRabbit review. Minor follow-ups use local checks without a
CLI repeat. Publish one draft PR and stop for Sam's CI/review/merge update.

## Results

Fresh merged baseline clean build/strict Javadoc and 1,504 tests pass; default 283/293 K + 10 S.
Final integrated build/strict Javadoc and 1,522 tests pass. New simplifier scope: 48/54 K + 6 S,
zero uncovered/resource/run errors; scoped R6 passes, R5/R7 skip. Initial progress-mutant timeouts
were not credited; finite pass/refinement bounds replace the old open-ended loop without changing
PIT settings. A validation-order regression kills the legacy-program/null-witness guard mutation;
all 54 final-population identities are retained with that one S→K change. Default final 283/293
retains all 293 identities/statuses and full gates pass. Independent oracles and all raw residuals
are recorded in [the evidence note](../../measurements/74-source-cutoff-witness-simplification.md).
Existing 22 normalization verdict cases and workflow YAML/artifact wiring pass. Actual CLI loop
prefix 4→0 and preemption prefix 3→1 retain identical cutoff values/digests and stay INCOMPLETE,
with freshly regenerated snapshots. The complete browser suite passes 45 checks, zero console
errors. CodeRabbit CLI 0.8.1 completed all 15 files: two minor duplicate ledger-status findings and
one trivial fixture-write finding. Frozen default XML/log confirm 283/293 and full gate PASS; both
ledgers now record that same completed result. The CLI fixture write creates its parent directory
and documents the side effect. Local follow-up checks verify these minor changes; no CLI repeat.
