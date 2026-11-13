<!-- Specification-authoring assurance only; runtime and implementation acceptance remain pending. -->
# #12 specification authoring review

## Scope and evidence boundary

Planning base: freshly fetched remote main `2a242c4`. Authored one
[shared contract](bytecode-contract.md), ten sequential milestone specs (94A–94C,95–101), root
vocabulary and current roadmap pointers. No production/test/configuration code changes.
The original dirty checkout and existing context-store staged work were preserved.

## Independent reader testing

Two fresh readers received only named repository documents, without conversation history. One read
contract/94A–96; one read contract/97–101. They answered public-release, fuel/cleanup, setup/observer,
seam/measurement, intrinsic/cell, CBS/DPOR/bitstate and JPF questions and searched for contradictions.
Their findings and revisions:

- Moved minimal immutable exception-message String storage to96; broader computation stays97.
- Marked nontrivial publication/history/allocator adversarial measurements as95 work;94B reports
actual zero/not-applicable costs where its setup-only profile cannot exercise them.
- Specified public types/limits/annotation shapes and selected existing EXACT rather than accidental
symmetry-backend expansion. A final local compatibility pass uses runBytecode to preserve run(Program).
- Defined full-cost intrinsic preflight, omitted invoke identity and unchanged counters on insufficient
allowance; qualified ordinary unit-opcode N/N+1 rules. Reader rechecks found these decisions clear.
- Removed cause-only Throwable constructors that could trigger an unmodeled overridden toString;
explicit message/cause constructors remain. Added finite descriptor/layout/cost contracts.
- Selected one101 ledger path and a deterministic modeled identity hash; readers found no remaining
material contradictions in their revised document subsets. This is document testing, not an interpreter proof.

Local connected-source/ponytail review additionally checked loader/runner/store seams, DPOR future
scan, source forward validation, terminal ordering, resource ownership and all phase dependencies.
94B is a coherent concurrency/preservation gate;97 and101 are broad but can split only for distinct
behavior/correctness arguments under the approved target, without enabling partial consumers.

## Actual CodeRabbit CLI invocations

CLI0.8.1, existing Homebrew installation. Each invocation occurred after the named specification was
complete and reviewed the available integrated set with:

```sh
coderabbit review --agent --uncommitted --include-untracked
```

No paid-credit flag, account changes or invented substitute review. Raw per-invocation NDJSON remains
local as `/private/tmp/interleave-bytecode-<phase>-coderabbit.ndjson`; the outcome table below is the
portable result summary. Later reviews include revisions to the prior set; a heartbeat is not completion.

| Completed document | Actual invocation result | Finding events |
|---|---|---|
| 94A | complete: review_completed | major, major |
| 94B | complete: review_completed | minor, major |
| 94C | complete: review_completed | major |
| 95 | complete: review_completed | major |
| 96 | complete: review_completed | minor, major |
| 97 | complete: review_completed | major, major, major, major, major |
| 98 | complete: review_completed | minor, major |
| 99 | complete: review_completed | major, major |
| 100 | complete: review_completed | major, major, major, major, major |
| 101 | complete: review_completed | major, major |
| contract | complete: review_completed | major, major |

## Validated dispositions

- 94A review, Major runtime requirement: verified Gradle toolchain/release26, README JDK26+ and CI
setup-java26. Contract now states the already-supported runtime and distinguishes Java17 input floor;
no runtime compatibility change was made.
- 94A review, Major CBS dominance:98 now requires independent two-actor/two-action cost/cutoff enumeration,
including cutoff obligations below dominated states. Minimum-cost positive coverage cannot erase
incompleteness or imply cutoff-equivalence.
- 94B review, Major Throwable cause constructor: independently confirmed the reader's callback concern;
cause-only descriptors explicitly reject. Non-null message/cause support needs no implicit callback.
- 94B review, Minor stale follow-up summary: marked completed A/B/C and selected later bundles historical;
current selected work points to12 and parked scope remains parked.
- 94C review, Major artifact versus model selection: retained digest definition and separately bound
selected modelClass and entry-point declarations in strict replay, including empty interrupted traces.

- 95 review, Major String ownership:96 now directly owns literal/message layout and intern-table
semantics;97 explicitly retains and extends that prior representation rather than owning its definition.

- 96 review, Minor actor syntax: Java annotation example now uses explicit `@Actor(id = 0)`.
- 96 review, Major StringBuilder atomicity: atomic shared append would mask real races. Restrict the
selected builder profile to conservatively proven nonescaping builders; unknown/shared escape rejects
before search. Immutable String results may publish. Add a shared-two-append/final-length negative
case rather than a false serialized PASS. General shared-builder microsteps are not claimed.

- 97 review: five Major findings. Running/uninvoked status rows are updated from actual NDJSON at
publication, never upgraded speculatively. StringBuilder finding repeats the prior snapshot and is
resolved by the nonescape restriction. Pinned OpenJDK26 source independently confirmed boolean
String.valueOf literal identity, getSuppressed shared-empty identity and constructor fillInStackTrace
callback. Specs now preserve literal/empty-array identities and setup charges; reachable Throwable
fillInStackTrace overrides reject before search with an explicit negative/differential fixture.

- 98 review: Minor review-table status is finalized from actual logs. Major shared array clone finding
is valid: a bulk snapshot can omit mixed copies. Restrict admitted cloning to sources conservatively
proven free of concurrent element mutation; unknown/racy sources reject before search. Immutable
String copy remains supported. Negative two-write/clone and positive local/readonly cases are required.

- 99 review: Major array-clone finding repeats98's prior snapshot and is resolved by source-mutation
admission restrictions. Major status-table concern is resolved only by final actual-outcome refresh.
- 100 review: five Major findings. Status table remains actual-outcome-only. Implicit exception messages
cannot be replaced with null when target code can inspect them; such unsupported/unknown-origin reads
now reject before search. Pinned Throwable source confirms initCause overwrite can invoke user toString;
this descriptor is excluded, preserving explicit message+cause constructors. Synchronized Throwable
intrinsics require observable receiver-monitor entry/body/exit and competing-monitor tests. Pinned
StringBuilder source confirms empty toString returns the interned empty literal; identity and no-allocation
semantics plus exact-budget tests are specified. No runtime differential experiment is claimed here.

- 101 review: two Major findings repeat the prior Throwable monitor/initCause snapshot.96 now states
entry/body/exit, reentrance, blocked zero-charge and release-on-fault rules; initCause is excluded.
No nonexistent implementation evidence was asserted to answer either finding.

- Contract review: two Major administrative findings required actual101 outcome and both99
findings in the record. Final refresh records completed101 analysis plus both99 dispositions above.
These are specification review outcomes; every implementation and runtime gate remains pending.

All eleven required invocations completed actual analysis; none was quota-limited, skipped or replaced
with ordinary review. All substantive findings are resolved in the contracts or explicit admission
restrictions. Administrative status corrections reflect the final NDJSON rather than future claims.

Final focused reader pass also found a synchronized-cost sequencing ambiguity.96 now recomputes
suppression-dependent cost at actual monitor entry, freezes the plan under the held monitor, and
rechecks current allocation/cell counters at body execution. Blocked attempts cache no stale cost.
97 explicitly inherits this entry/body/exit exception to its atomic-body wording. This focused
clarification and final administrative status refresh received local review after the completed CLI
analyses; no additional CLI completion is claimed for them.

## Validation and deliberate skips

Documentation validation checks the ten canonical milestone sections and sequential dependencies,
requirement/acceptance mappings, local Markdown files/anchors, table shape, spec-only changed paths and
whitespace. Context structural validation has zero errors and one preexisting external-link warning.
Validation scripts/raw review logs are local authoring tools, not new production infrastructure.

No Gradle, PIT, browser or interpreter measurement was run: the change is specifications only and the
bytecode implementation does not exist.94B batching/sharedness measurements,99 genuine reduction and101
pinned-JPF compatibility remain mandatory unverified implementation gates. No old baseline count was
reused as a fresh measurement. Sam supplies remote CI/review/merge status after publication; no watcher.
