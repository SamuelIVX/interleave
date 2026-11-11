<!-- B8/J1 captures actual resource-interrupted source paths without changing cutoff semantics. -->
# 87 — Resource-interrupted source prefixes

## TL;DR

Implement the approved B8 choice A after merged PR #128 at `2f578cf`: capture, replay and present
the actual resource-interrupted prefix. J1 owns capture and explicit replay; J2 / Spec 88 will own
JSON/viewer presentation. Shortening, CLI execution modes and B/C preservation objectives are unselected.

## Terms and guarantees

A resource interruption is a run event (`MAX_STATES` or `MAX_TIME`), not an omitted modeled action.
A captured prefix contains actual committed owner choices/outcomes and source identities. A replay
observation describes that execution under the supplied property; it does not reproduce external
resource accounting or certify exhaustive exploration. Existing Spec 16 trace/cutoff semantics remain
owned there; resource prefixes never enter the ordinary incomplete/failure trace buckets.

## Capture seam and lifetime

Add a default no-op `StateVisitor.onInterruptedPrefix(Configuration, Trace)`. Wrap only the existing
visitor call at the five visit sites across DFS, static POR, both DPOR paths and CBS. On its
LimitExceededException, freeze the source path through TracePrefix.trace with INCOMPLETE, null
property and null cutoff, notify synchronously, then rethrow the same exception after successful
notification. Exceptions from the new notification propagate. The Trace is an
internal unclassified search-prefix carrier, not a modeled-cutoff proof. Do not catch whole recursive
calls, which would freeze parent paths repeatedly. Existing 1/3-argument callbacks remain unchanged.

Only the runner's own known resource reason can attach a ResourcePrefix. Freeze once before mutable
path lists unwind. Ordinary visits add no prefix/snapshot allocations or execution. Capture adds no
modeled action/property evaluations; copying the prefix and final state occurs only at interruption.
Limits retain equality boundaries, states-before-time precedence, clock-call order and visit counts.
Other visitor/store/model/property failures retain their propagation and existing unknown-limit behavior.

## Immutable public result

Add root-package final ResourcePrefix, internally constructed from the source carrier, actual stopped
configuration and observed reason. Reject missing source identity, failure/cutoff evidence, non-INCOMPLETE
carrier outcome and recorded ASSERTION_FAILED attempts. Deep-freeze choices/outcomes, evidence,
final state snapshot and program counters. Store a private canonical configuration key for comparison;
do not retain mutable configurations, callbacks, stores, programs or persistent parent paths.

Expose reason(), threadIds(), outcomes(), evidence(), programCounters(), terminalState(), and
replay(Program, Invariant). TestResult.resourcePrefix() returns Optional<ResourcePrefix>; existing
constructors initialize it absent. Metadata copies preserve it and reject contradictory source/reason
metadata. Symmetry/store identity, outcome buckets, counts and hasIncomplete behavior remain unchanged.
Only source resource stops carry this value, including an empty root. Iterative interruption captures
the active pass while retaining the existing prior-cutoff history. Legacy/unknown stops have no prefix.

## Explicit replay

ResourcePrefix.replay returns privately constructed immutable ResourcePrefix.Replay with kind(),
propertyChecked(), states() and Optional failure(). Reuse TraceReplayer for identity/outcomes and the
existing detailed driver for explicit property/terminal observation. Require all captured choices to
be consumed, matching outcomes and the exact final canonical configuration. Source digest and actual
PC/span/expansion metadata must authenticate; no second interpreter or public raw-Trace escape hatch.

Accept PREFIX, COMPLETED, DEADLOCK or VIOLATION only. The last position can be terminal because the
resource callback runs before the explorer's checks. Earlier termination/failure or a modeled cutoff
that prevents consuming the prefix rejects as corrupted evidence. Null property means unchecked;
unexpected property/model exceptions propagate. Preserve structured property failures from the driver.

Replay neither mutates the original result nor claims PASS. It is an explicit diagnostic operation,
may reuse existing replay passes, and is not covered by the original run's time/state allowance.
Pure deterministic source execution and the actual source property remain caller preconditions.

## Acceptance and publication

Test all strategies, both DPOR routes, root/deeper/branch/iterative stops, source aliases/control metadata,
state/time boundaries, symmetry/approximation, existing trace retention, callback counts/order and
exception identity. Test immutable arrays/state, copy consistency, source/evidence corruption, final-key
mismatch, terminal-at-stop observations, null/wrong/throwing properties and repeated/concurrent reuse.
Numeric expectations use independently derived literals. Keep existing cutoff rejection and JSON intact.

Measure fresh merged-main build/tests/strict Javadoc and affected/default PIT, including anonymous and
nested classes. Compare stable identities/statuses, enumerate residuals and keep resource failures raw.
Run integrated verification, artifact-level library checks, local review and actual completed CodeRabbit.
Ship spec/code/tests/evidence/docs together; publish one J1 draft PR and stop. J2 follows after merge.
No new dependency, mutation operators/targets/tests/filters/resources/floor, source cap, CI parallelism
or consumer-schema change. Update only measured denominator/per-class bookkeeping for the four
new CBS capture mutations; re-derive the unchanged floor and integer kill margin in the ledger.

## Measured evidence

Fresh baseline `2f578cf` passes build/strict Javadoc and **1,815 tests**. Final integrated checks pass
**1,852 tests**, zero failures/errors/skips. ResourcePrefix is **53/53 K**, nested Replay **7/7 K**.
Affected census: **1,025/1,105 K + 58 S + 22 NC**, scoped R6 passes; scoped R5/R7 are not enforced.
All 427 unchanged-method mutation identities remain: 420 statuses unchanged, six S -> K and one
NC -> K. The raw residual operation/status multiset adds none. A final 519-mutant capture/replay
scope matches every corresponding broad-census identity/status after the helper/lifecycle checks.

| Default class/population | Baseline | Final |
|---|---|---|
| ContextBoundedExplorer | 127/127 K | 131/131 K |
| State classes | 309/324 K + 15 S | 309/324 K + 15 S |
| Total | 436/451 K + 15 S | 440/455 K + 15 S |

Default R5/R6/R7 pass. All 324 exact state identities/statuses and fifteen survivor identities match.
Measured denominator/per-class bookkeeping changes to 455/131; the unchanged 94% floor requires
428 kills and retains twelve kills of headroom. No operators, targets, filters, resources or CI
parallelism change. The [ledger](../../measurements/87-resource-prefix-evidence.md) catalogs all
80 affected residuals, scope limits, identity rules, independent oracles and reproduction commands.

The built-jar external consumer and existing normalization checks pass. CodeRabbit completed fifteen
initial files with one minor stale-status finding, patched and locally verified without rerun. Final
lifecycle/helper assertions and bookkeeping/evidence received local review. JSON/viewer delivery
remains J2 after J1 merges; J1 is locally verified, with merge pending.
