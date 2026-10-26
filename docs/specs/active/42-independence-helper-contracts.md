# Spec: Independence aliases and source-probe boundaries

## Objective

Protect literal alias/dependency results and conservative source enabledness probes in roadmap9-V1.
Distinguish observable helper gaps from duplicate write/write comparisons under valid footprints.

## Scope

Add IndependenceContractTest, this specification and the roadmap update. Clarify the probe's public
return Javadoc for source attempts without committed successors. No executable production, public
API, dependency, report-output, default mutation scope/operator/exclusion or gate changes.
Serialization stays separate. Spec40's property-selector component/79 nontermination remains open;
this helper scope neither remeasures nor replaces that failed scoped ratchet.

The public alias/probe helpers are shared by legacy selection and reduced exploration; the internal
four-footprint overload also serves immutable suffix snapshots. Existing explorer and legacy
assertion-probe compatibility tests stay unchanged. No new pruning or failure conversion is added.

## Requirements

1. Visibility overlap SHALL use symmetric scalar/array aliasing: equal names alias; a base aliases
   its literal elements in either direction; distinct elements and merely similar bases do not.
2. Write/read and write/write dependence SHALL follow those aliases; read/read remains independent.
   Empty writes or observations SHALL produce no visibility overlap.
3. Source probes SHALL use both owners' actual current PCs, the selected action's committed successor
   values, and the other action's before/after guard truth. Enabling and disabling are both interference.
4. Same-owner pairs, either completed owner and a disabled selected owner SHALL return false without
   committing work to the parent. Source failures/cutoffs without successors SHALL conservatively
   return true; they do not establish an actual enabledness change.
5. Every probe SHALL leave parent counters and every shared value unchanged. Fixtures SHALL use
   actual source execution and valid derived configurations, not fabricated enabledness or PCs.
6. Duplicate write/write comparisons SHALL retain narrow selected-Boolean-value dispositions only
   for complete, stable, total valid footprints. No performance/resource or arbitrary metadata-error
   equivalence is claimed.

## Independent fixture oracles

Thirteen cases combine five literal alias rows with eight source-probe cases. The scalar/array rows
exercise visibility in both orientations, write/read from both sides, write/write, read/read and
empty visibility boundaries using immutable non-null MemoryLocation sets:

| Locations | Alias/visibility overlap | Write conflict |
|---|---|---|
| x / x | true | dependent |
| x / y | false | independent |
| arr / arr[0] | true in both directions | dependent |
| arr[0] / arr[1] | false | independent |
| arr / array[0] | false | independent |

These values follow direct name equality and the array-element delimiter, not another invocation
of the helper under test. Read/read independence does not imply safe pruning for every property;
that remains the caller's observation/reduction contract.

All source fixtures compile real thread declarations over shared x/y initially zero. Parent
oracles assert both shared values and the complete two-owner counter vector after every probe.

### Successor guard comparisons

Owner0 writes x=1; owner1 writes y under x==0 or x==1. The literal before/after guard truths are
true→false and false→true, respectively. Both probe results are true. Parent counters[0,0], x=0,
y=0 stay unchanged. Reading a null/old successor instead cannot satisfy the disabling case.

### Actual current counters

- Selected-owner PC: owner0 first executes guarded x=1 when x==0, then can write x=2. Owner1 is
  guarded by x==2. After the first commit, counters[1,0], x=1,y=0 and enabled[0] are literal.
  The current owner0 action enables owner1, giving true. Its consumed guard is now false, so
  replacing the selected PC with0 would incorrectly reject the probe.
- Other-owner PC: owner0 writes x=1. Owner1 first executes an empty action, then waits for x==1.
  After that empty commit, counters[0,1], x=0,y=0 and enabled[0] are literal. The current guard
  becomes true, giving interference; its consumed unconditional action would report none.

Each counter vector is independently enumerated from one actual committed action and checked
against the original ordered instruction lists. Source transitions, not manual successor metadata,
establish these positions.

### Completed, disabled and terminal boundaries

A fixture with owner0 writing x and owner1 writing y when x==0 first commits owner1. At counters
[0,1], x=0,y=1, both pair directions return false because one owner is completed. A self pair also
returns false. This exercises both exact-end PC boundaries and the same-owner return value.

With owner0 guarded by x==1 and owner1 writing y, only1 is initially enabled. Probing disabled0
returns false; parent[0,0]/x0/y0 remains unchanged.

Two atomic bodies tentatively write x=1 before either modulo-by-zero or a true loop at limit0.
The actual executor records no successor: RUNTIME_ERROR/MODULO_BY_ZERO or LOOP_LIMIT, respectively.
Both probes return true conservatively, even though the other owner's x==0 guard remains true in
the unchanged parent. Direct executor evidence distinguishes actual source failures/cutoffs from
an accidentally disabled fixture. No synthetic SourceStep, history counters or private reflection
force these paths. A conservative helper Boolean is not a newly committed failure state.

## Retained duplicate write/write comparisons

The four-footprint areIndependent implementation checks aWrites against bWrites in its first pass
and bWrites against aWrites in its second. areIndependent/23 and/57 remove one respective
conflictsWithAny result, replacing it with false. For valid MemoryLocation names, aliasing is
symmetric: equality, a-prefix-of-b-element and b-prefix-of-a-element form the same Boolean relation
under swapping. If any write/write pair aliases, the other pass still finds it; if none aliases,
the dropped comparison was false anyway. Write/read checks remain intact in each mutant.

This argument applies separately to each single-site mutation, not their combination. It assumes
complete stable non-null footprints with total iteration/name operations, as in compiler snapshots
and built-in immutable footprint sets. It excludes arbitrary custom metadata/set errors, concurrent
mutation, impure iteration and process/resource/performance behavior. Metadata acquisition is not
removed by these mutations. Keep both production comparisons; no adjusted scores or exclusions.

## Baseline and verification

PR #82 merged ate646c13. Fresh baseline:964 tests pass, zero failures/errors/skips. Scoped
IndependenceRelation:66/79 KILLED,9 SURVIVED,4 NO_COVERAGE, zero timeout/memory/run-error statuses.
The baseline scoped ratchet passes. This is separate from the property-selector resource limitation.

All thirteen new cases and the full977-test suite pass, zero failures/errors/skips. Final scoped
IndependenceRelation:77/79 KILLED,2 SURVIVED,0 uncovered/timeout/memory/run-error statuses.
All79 semantic identities (class, method, descriptor, mutator, indices and blocks) match the fresh
baseline, and all66 old kills remain killed. The scoped ratchet passes. Eleven identities become
kills: seven prior survivors and four previously uncovered sites.

| Method / bytecode index | Mutation | Baseline → final |
|---|---|---|
| conflicts/25 | Removed first String.startsWith call | SURVIVED → KILLED |
| conflicts/28 | Replaced first alias-branch return with false | SURVIVED → KILLED |
| hasEnableDisableInterference/47 | Selected-owner end-PC boundary | SURVIVED → KILLED |
| hasEnableDisableInterference/52 | Other-owner end-PC boundary | SURVIVED → KILLED |
| hasEnableDisableInterference/30 | Removed selected-owner PC unboxing | SURVIVED → KILLED |
| hasEnableDisableInterference/39 | Removed other-owner PC unboxing | SURVIVED → KILLED |
| hasEnableDisableInterference/118 | Removed successor state accessor | SURVIVED → KILLED |
| hasEnableDisableInterference/112 | Replaced no-successor return with false | NO_COVERAGE → KILLED |
| hasEnableDisableInterference/7 | Replaced self-pair return with true | NO_COVERAGE → KILLED |
| hasEnableDisableInterference/57 | Replaced completed-owner return with true | NO_COVERAGE → KILLED |
| hasEnableDisableInterference/85 | Replaced disabled-owner return with true | NO_COVERAGE → KILLED |

The only retained identities are areIndependent/23 and/57 with the separate single-site write/write
comparison dispositions above:77 kills+2 scoped survivor dispositions=79. No whole-checker closure
or arbitrary custom-metadata equivalence follows. Final scoped results include the added write/write
matrix assertion; no executable source/test changes followed that measurement.

CodeRabbit CLI0.8.1 completed the substantive four-file review with zero findings. Final clean build, strict Javadoc and all977 tests pass, zero failures/errors/skips. Default PIT
killed281/291 mutations and left10 SURVIVED, zero uncovered/timeout/memory/run-error statuses;
all291 semantic identities and statuses match merged Spec41 evidence. Both helper-scoped and
default gates pass; Spec40's distinct property-selector resource limitation stays open. Only
measured verification/status prose was finalized after CLI review. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`. Browser integration
is outside this bundle because executable production/report output is unchanged. Remote CI remains
unverified; publish as draft and stop. Historical expanded9-V1 totals remain unremeasured; separate
scoped populations must not be added into a claimed whole-checker score.
