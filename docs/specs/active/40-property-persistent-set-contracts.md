# Spec: Property-aware persistent-set selector contracts

## Objective

Protect the property-aware static-POR selector's literal owner selections in roadmap9-V1.
Separate observable selection gaps from value-preserving boundary work and an unresolved
resource-failing mutation. Do not count a memory failure as an assertion-backed kill.

## Scope

Add PropertyPersistentSetContractTest, this specification and the roadmap update. No production,
API, dependency, report-output, default mutation scope/operator/exclusion or gate changes.
Legacy PersistentSetComputer, independence helpers and serialization remain separate audits.
The component/79 resource-failing mutant remains an open mutation-harness limitation; this PR
neither weakens the ratchet nor implements a new harness. No whole-selector/checker closure.

StaticPorExplorer constructs PropertyPersistentSetComputer for source models without properties
using empty observations, or for properties with complete observations. UNKNOWN properties keep
exhaustive fallback. This package-level selector returns IDs; execution/outcome validation remains
in the explorer and is already covered by Spec31. The new tests do not change that boundary.

## Requirements

1. Selection SHALL choose the smallest enabled invisible suffix component, breaking equal-size
   ties by sorted owner IDs; if no eligible component exists, return every enabled ID.
2. A component's returned IDs SHALL be sorted even when dependency traversal discovers them out
   of order. Distinct nonzero component IDs SHALL remain distinct.
3. Suffix comparisons SHALL use each owner's actual current PC, excluding consumed accesses.
   Disabled but live owners SHALL remain in dependency components and disqualify those components.
4. Completed owners SHALL NOT be selected. Singleton-enabled and all-completed positions SHALL
   retain their literal enabled selections without fabricating configuration metadata.
5. Constructor observations SHALL be a value snapshot. Selection SHALL NOT execute modeled steps
   or change the configuration's state/counters.
6. Report fresh baseline/post-change semantic identities and raw statuses. Resource failures SHALL
   remain unearned, with the scoped gate failure disclosed separately from passing ordinary tests
   and default-scope checks.

## Design and independent oracles

Nine cases use six compiler-produced source fixtures. Calls exercise the existing package-level
selector directly; no reflection, synthetic enabled sets, invalid PCs, mutable model definitions or
stateful predicates are used. Existing explorer/replay cases remain unchanged.

### Sorted components and visibility

The ordering fixture has visible owner0 writing observed. Owner1 writes x; owner2 writes y; owner3
reads x/y and writes z. Suffix edges are1–3 and2–3, so traversal from1 discovers[1,3,2], but the
eligible component's literal output is[1,2,3]. Owner0's visible singleton cannot replace it.

A second fixture has owners0/1 writing x, owner2 writing y and owner3 writing z. The components
are{0,1},{2},{3}, independently obtained by enumerating their disjoint write sets. Literal cases:

| Complete observations | Expected selection | Reason |
|---|---|---|
| Empty | [2] | Smallest invisible singleton, lower ID breaks the tie with3 |
| {y} | [3] | Owner2 is visible |
| {y,z} | [0,1] | Both singleton candidates are visible |
| {x,y,z} | [0,1,2,3] | No invisible component exists |

Component cardinalities2,1,1 follow both direct set enumeration and2 shared-x owners versus one
owner per other field. Expected selections are literals, not recomputed by the selector.

### Current counters, completed owners and disabled members

Owner0 writes x. Owner1 writes x then y. Owner2 writes z. After a genuine successful owner1
transition, counters are[0,1,0] and x=2. Only owner1's y suffix remains; all components are now
independent, so choose[0]. Reading its consumed x write would incorrectly connect owners0/1.
After owner1 finishes, counters[0,2,0] still select[0]. After owner0 finishes,[1,2,0] selects[2].
After owner2 finishes, every owner has terminated and selection is[]. The helper advances these
positions only through TransitionExecutor and asserts ADVANCED/no cutoff/a committed successor.
Counter literals independently enumerate one x action for0, two actions for1 and one z action for2.

The selected-owner counterpart gives owner0 the two actions x=2 then y=1, owner1 writes x,
and owner2 writes z. After owner0's first commit, counters[1,0,0] and x=2 select[0]. Reading
owner0's consumed x write instead would join owners0/1 and incorrectly favor owner2. Keep this
regression distinct from the other-owner-PC fixture above; either lookup can be wrong separately.

In a separate fixture, owner0 writes x, owner1 writes y only when x==2, and owner2 writes z.
Initially enabled IDs are[0,2], but owner1 is live and its x guard connects it to0. Component{0,1}
is ineligible because1 is disabled, so the selector must return[2].

### Observation ownership

Two owners write x and y. Observations initially contain x, so the literal selection is[1].
After construction, the caller replaces its own set with{y}; selection must remain[1] because the
selector owns a snapshot. Root counters remain[0,0], x=0 and y=0. Only the caller-owned input set
changes; model definitions and the snapshot used during selection stay immutable.

## Retained boundary dispositions

These arguments apply to valid compiler-produced programs, stable complete footprints/observations,
valid current PCs and the non-null built-in independence relation. They preserve selected ID values,
not allocation/work/resource/performance behavior. Production guards remain unchanged.

| Method / bytecode index | Mutation | Scoped disposition |
|---|---|---|
| compute/14 | Enabled-count boundary <=1 becomes <1 | With one enabled ID, every nonempty component has size at least1 and is rejected by component.size>=best.size before becoming best. The result keeps that one enabled ID; extra graph work is unobserved. |
| live/16 | PC<size becomes PC<=size | Only completed owners are added to the live set. Their suffix at size is empty, giving isolated disabled components that fail enabledIds.containsAll. |
| live/8 | Removed Integer.intValue replaces the current PC with0 | Nonempty completed owners can be treated as live, but their actual-PC suffix remains empty and they cannot be selected. Already-live nonempty owners remain live; empty threads still fail0<0. |
| live/24 | Replaced live return with true | Completed owners, including empty threads, contribute only empty suffix components and are excluded by the enabled-membership check. |

Suffix storage includes the empty terminal entry. No steps().get(terminalPc) occurs for these
ineligible components: compute rejects size/membership before testing current-action visibility.
Normal execution may produce disabled live owners; those retain actual nonempty suffixes and are
not covered by a claim that every disabled owner can be ignored. No malformed PCs or custom
access/equality/error behavior inherit these arguments.

## Open resource-failing mutation

component/79 NegateConditionals reverses the seen-owner skip. A visited writing seed can then be
compared with itself, classified dependent, and queued again forever. The changed code allocates
pending/component entries until its PIT minion exhausts memory. This is deliberately mutated code,
not a production failure. The existing ratchet rejects MEMORY_ERROR as unearned; keep that result
and the failed scoped task visible. Raising heap capacity does not make this loop finite.

Do not manufacture an assertion kill with impure footprints, invalid metadata, reflection or a
model callback that aborts the selector. A separately scoped bounded-execution harness audit is
needed before claiming this mutation is assertion-detected. No new exclusion, operator filter,
threshold adjustment or test-only execution hook is introduced here.

## Baseline and verification

PR #80 merged at96c91ca. Fresh baseline ordinary tests:948 passed, zero failures/errors/skips.
Scoped PropertyPersistentSetComputer:107/115 KILLED,7 SURVIVED,0 NO_COVERAGE,1 MEMORY_ERROR.
The console's detected count includes the memory failure; only107 are KILLED in mutations.xml.
The scoped command exits1 at mutationRatchet because of that unearned resource result. Preserve
this baseline as measured rather than describing the scoped gate as passed.

All nine new cases and the full957-test suite pass, zero failures/errors/skips. Final scoped
PropertyPersistentSetComputer:110/115 KILLED,4 SURVIVED,0 NO_COVERAGE,1 TIMED_OUT. All115
semantic identities (class, method, descriptor, mutator, indices and blocks) match the baseline;
all107 previous kills remain killed. Only three SURVIVED identities become KILLED:

| Method / bytecode index | Mutation | Killing regression |
|---|---|---|
| component/59 | Removed Integer.intValue for the selected owner's PC | selectedOwnerUsesItsCurrentSuffix |
| component/100 | Removed Integer.intValue for the other owner's PC | consumedFootprintsDoNotConnectRemainingComponents |
| component/145 | Removed component List.sort | componentOrderIsIndependentOfDiscoveryOrder |

The four retained survivors have the boundary dispositions above. component/79 changes from baseline MEMORY_ERROR to final TIMED_OUT,
not a kill or equivalence disposition; the same nonterminating mutant can reach either resource limit. Thus110 kills+4 scoped survivor dispositions+1 open resource
failure account for115 identities. The post-change scoped command also exits1 at mutationRatchet
for that single timeout; it is not a passing scoped gate. The final report has no memory-error,
run-error or uncovered statuses. Final scoped results include the separate selected-owner-PC regression. The first
post-change run had109 kills and5 survivors before adding that ninth case.

CodeRabbit CLI0.8.1 completed its substantive three-file review with zero findings. Final clean
build, strict Javadoc and all957 tests pass, zero failures/errors/skips. Default PIT killed281/291
mutations and left10 SURVIVED, with zero uncovered/timeout/memory/run-error statuses; all291 semantic
identities and statuses match merged Spec39 evidence. This passing default scope does not replace
the failed selector-scoped gate. Only measured verification/status prose was finalized after CLI. All18 normalization checks pass; relative links, credential-pattern scan and diff checks
pass. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`. Browser integration is
outside this bundle because executable production/report output is unchanged. Remote CI remains
unverified; publish as draft. Historical expanded9-V1 totals remain unremeasured and must not be
summed with this scoped slice.
