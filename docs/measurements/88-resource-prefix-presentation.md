<!-- J2 JSON/viewer verification, exact mutation lineage, and scope limits. -->
# 88 — Resource-prefix presentation evidence

## Revision and scope

[Spec 88](../specs/active/88-resource-prefix-presentation.md) implements approved B8/J2 against
fresh merged main `9abbc4f` (PR #129). The baseline includes its test-latch review fix. The selected
scope is actual captured-prefix JSON, explicitly computed replay JSON, optional result JSON, and
the existing viewer. No shortening, new CLI, source-cap change, dependency, or mutation-policy change.

Baseline ordinary/strict Javadoc and **1,852 tests** pass. Final build/strict Javadoc and
**1,862 tests** pass, zero failures/errors/skips. Ten invocations were added; no cases were removed.
An external Java 26 consumer of the built jar, using the existing Gson runtime dependency, passes
raw export, explicit replay export, original INCOMPLETE classification and optional-member checks.

## Affected mutation census

The exact affected scope is `ResourcePrefix*`, `TestResult`, and `TraceRecord`; final additionally
names the new package-owned `ResultJson`. The configuration holder has no eligible mutation sites.
K = KILLED; S = SURVIVED. Both runs enforce scoped R6; scoped R5/R7 are not enforced or claimed.

| Class (`dev.samhb.interleave.`) | Merged baseline | Final |
|---|---|---|
| `ResourcePrefix` | 53/53 K | 77/77 K |
| `ResourcePrefix$Replay` | 7/7 K | 20/20 K |
| `TestResult` | 126/128 K + 2 S | 133/133 K |
| `TraceRecord` | 82/83 K + 1 S | 82/83 K + 1 S |
| Total | 268/271 K + 3 S | 312/313 K + 1 S |

All **187 unchanged-method identities and statuses** match exactly. Identity includes class,
method/descriptor, mutator, description, bytecode indices and blocks; shifted source lines are
excluded. Exclude rewritten `TestResult.toJson`, `ResourcePrefix.replay` and the changed nested
Replay constructor descriptor, plus new methods. No site-for-site lineage is claimed there.

The population grows by 42 sites; kills grow by 44 because the new mixed cutoff/resource JSON
regression also detects both inherited TestResult survivors in reason emission. The initial
affected run had 310/313 K + 3 S; the final affected run includes the last regression and has
312/313 K + 1 S. Both ResourcePrefix classes total **97/97 K**. All other statuses are KILLED or
SURVIVED; zero uncovered/time/resource/run-error outcomes.

The sole raw residual is inherited and exactly unchanged: the four-argument `TraceRecord`
constructor, descriptor `(List,List,TraceOutcome,String)V`, `NonVoidMethodCallMutator`,
`removed call to java/util/List::of`, bytecode index `[8]`, block `[0]`, status SURVIVED.
No new equivalence or complete explorer-coverage claim is made.

## Default mutation gate

Fresh merged main and the feature both measure **440/455 K + 15 S**, R5/R6/R7 pass. All **455
identities and statuses** match exactly, including all fifteen survivors. CBS remains 131/131 K;
the state population remains 309/324 K + 15 S. Operators, targets, tests, filters, resources,
denominator and 94% floor are unchanged: 428 required kills retain twelve kills of headroom.

The default feature gate measures the final production code before the last JSON-only regression;
the final affected census includes that regression. No default-target production or ratchet-policy
change follows the default run. A scoped JSON census is not a whole-explorer coverage claim.

## Consumer and review evidence

Fresh Java tests emit nine actual payloads under `build/source-resource-*.json`: raw result, raw
prefix, root, time, explicit replay failure, completion, deadlock, hostile source filename, and an
earlier failure retained alongside a later resource interruption. Other existing source/CLI
generators remain enabled. Normalization and **135 browser assertions pass**, zero console errors.
Raw snapshots are final-only, replay states are navigable, and original INCOMPLETE remains separate
from reported replay kind/property/failure. Earlier conclusive failures keep priority. Hostile labels
render literally, and malformed replay alignment is rejected.

The normalizer additionally rejects invalid resource provenance, source/choice/outcome alignment,
owner counters/local-owner IDs, snapshot shapes, replay failures/flags/kinds and conflicting final
values. The harness reads production cap declarations rather than silently restating them.
Existing schedule/thread/cell/string/field and input-byte limits remain active.

CodeRabbit 0.8.1 completed thirteen initial source/test/spec files with two minor findings. Both
were independently confirmed and fixed: browser assertions now compare the exact field value cell,
and captured/replayed local-owner IDs must belong to the program. Normalization and all 135 browser
checks pass after the fixes. No CLI rerun for minor findings. Later regression/evidence/bookkeeping
changes receive local review. The OWASP input/output pass checks bounds, safe DOM insertion and
separation of reported producer observations from authenticated Java replay; no whole-site audit
or browser-side model execution is claimed. No subagents or CI watchers.

## Reproduction

From separate clean baseline (`9abbc4f`) and feature checkouts, retain each XML report before the
next PIT command overwrites it. The baseline lacks `ResultJson`; omit that name for its scoped run.

```sh
./gradlew --max-workers=1 -PpitestThreads=1 test javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest \
  '-PpitestTargetOverride=dev.samhb.interleave.ResourcePrefix*,dev.samhb.interleave.ResultJson,dev.samhb.interleave.TestResult,dev.samhb.interleave.TraceRecord'
# Preserve build/reports/pitest/mutations.xml for this affected census.
./gradlew --max-workers=1 -PpitestThreads=1 pitest
# Preserve build/reports/pitest/mutations.xml for this default census.

npm ci --prefix docs
npx --prefix docs playwright install chromium
SOURCE_TRACE_JSON=build/source-visualizer.json \
SOURCE_ASSERTION_JSON=build/source-assertion-visualizer.json \
SOURCE_SIMPLIFIED_CUTOFF_JSON=build/source-simplified-cutoff.json \
SOURCE_SYMMETRY_JSON=build/source-symmetry-visualizer.json \
SOURCE_SHORTEST_CUTOFF_PROVEN_JSON=build/source-shortest-cutoff-proven.json \
SOURCE_SHORTEST_CUTOFF_PARTIAL_JSON=build/source-shortest-cutoff-partial.json \
SOURCE_RESOURCE_PREFIX_DIR=build npm test --prefix docs
```

Java expectations are explicit wire values and classified execution observations; typed integer
arrays use independent `[9,2]` / `[3,8]` literals. Full object/array comparisons retain escaping and
key presence/absence. A counted property confirms serialization never evaluates it. A zero-choice
replay remains PREFIX before a pending omitted action; exporting it cannot invent a loop cutoff.
