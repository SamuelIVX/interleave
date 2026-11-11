<!-- J2: optional JSON and viewer presentation of actual resource-interrupted source execution. -->
# Spec 88 — Resource-prefix presentation

## Contract

Implement J2 of the [approved B8 contract](../../plans/resource-prefix-contract.md) on merged
J1 (`9abbc4f`, PR #129). Choice A remains the entire selected scope. Serialization never runs
the model/property, reproduces resource accounting, shortens a schedule, or changes a run verdict.

`ResourcePrefix.toJson()` emits `outcome: INCOMPLETE`, the original `resourceLimitReason`,
`threads`, `outcomes`, `sourceDigest`, `executedSteps`, `programCounters`, and `terminalState`.
It exports no private configuration key, search carrier, modeled cutoff, or failure. The optional
`TestResult.resourcePrefix` member uses exactly this shape; absence preserves legacy JSON.

Explicit `ResourcePrefix.Replay.toJson()` emits that same captured prefix plus a `replay` object:
`kind`, `propertyChecked`, initial-through-final `states`, and optional actual `failure`.
Each observation retains its own immutable originating prefix; callers cannot pair unrelated values.
No replay is performed by either serializer. One package-owned Gson configuration supplies escaping
and typed nested values for trace records, prefix payloads, and result diagnostics.

## Viewer

Accept standalone raw/replayed prefixes and the optional result member. A raw prefix has only its
captured final snapshot; earlier positions stay explicitly unavailable. Replayed states align with
the existing choices. Display the original resource interruption and INCOMPLETE classification
separately from any explicit replay kind, property-check flag, or discovered final-position failure.
COMPLETED replay never becomes PASS, and VIOLATION replay does not rewrite the original run.

Result selection retains earlier failure/deadlock precedence, then prefers the actual resource
prefix over modeled incomplete/completed paths. Existing buckets remain in JSON. Reject malformed
or conflicting provenance, choices/outcomes/source alignment, counters, snapshots, and replay
observations before rendering. Reuse existing schedule/thread/cell/string/field caps and text-only
DOM insertion. Do not synthesize omitted actions or assign a modeled cutoff to a resource event.

## Verification and scope

Strict Java round trips cover raw/root/time prefixes, optional member absence, typed arrays/locals,
escaped source names, all replay kinds, failure details, original metadata/buckets and no implicit
execution. Fresh Java artifacts exercise normalization and actual browser display, including raw
final-only state, replay state navigation, terminal observations, precedence and hostile text.
Malformed browser inputs exercise the trust boundary and existing rendering limits.

Measure merged-main and final build/strict Javadoc, affected and default mutation scopes; record
exact identities/statuses and residuals rather than inventing equivalence. Run local review and the
selective CodeRabbit gate, publish one PR, then stop for Sam's review/CI/merge update. B8 completes
only when J2 merges. B9/B10 need separate contracts; roadmap #11/#12 and parked work stay excluded.

## Measured evidence

Fresh merged-main baseline passes 1,852 tests/strict Javadoc. Final build/strict Javadoc and
**1,862 tests** pass. Affected baseline **268/271 K + 3 S** becomes **312/313 K + 1 S**; the
ResourcePrefix classes total **97/97 K**, TestResult **133/133 K**. All 187 unchanged-method
identities/statuses match. The sole inherited TraceRecord survivor is catalogued without a new
equivalence claim. Scoped R6 passes; scoped R5/R7 are not enforced.

Independently measured default baseline/final both yield **440/455 K + 15 S**, R5/R6/R7 pass;
all 455 identities/statuses match, with the 94% floor and policy unchanged. The final JSON-only
cutoff-reason regression is covered by the final affected census. Fresh Java-to-browser checks pass
**135 assertions**, zero console errors. The external Java 26 built-jar consumer passes.

CodeRabbit completed thirteen initial files with two minor findings, both fixed and locally verified
without a CLI rerun. The [ledger](../../measurements/88-resource-prefix-presentation.md) records
scope boundaries, raw residual identity, late regression verification and reproduction commands.
J2 is implemented and locally verified; merge remains pending. B8 completes on J2 merge.
