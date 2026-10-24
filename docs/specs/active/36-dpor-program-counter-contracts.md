# Spec: DPOR current-counter metadata and execution

## Objective

Protect current-counter evidence across the main reduced loop, exhaustive fallback and all-asleep
fallback. Continue roadmap 9-V1 with these three related lookup sites; keep unrelated audits separate.

## Scope

Add DporProgramCounterTest, this specification and a roadmap update. No production, API, dependency,
report-output, mutation-scope/operator/exclusion or gate changes. Existing Spec35 dispositions remain
scoped historical evidence; this bundle does not claim whole-class or whole-checker closure.

## Requirements

1. The main reduced loop SHALL use the action at the current PC for sleep/dependency decisions.
   Reusing the first action's metadata SHALL NOT fabricate an extra visit in the specified fixture.
2. Exhaustive fallback SHALL execute the actual action at PC one, preserving its failure prefix,
   rather than executing the successful action at zero.
3. All-asleep fallback SHALL preserve a failure at PC one with its committed prefix and action
   outcomes, alongside the ordinary main-loop failure witness.
4. Both result and callback evidence SHALL independently match literal expected values. Returned
   state maps SHALL be compared by value set, not an undocumented iteration order; ordered visitor
   notifications SHALL retain their independently derived order.
5. Genuine source traces SHALL replay directly with their original evidence; only digest-free
   wrapper evidence may be enriched. Altered source identity SHALL be rejected, not reconstructed.
6. Exhaustive/fallback step lookups used only by a null guard SHALL receive explicit supported-
   contract dispositions if fresh PIT retains them. Do not conflate those with the meaningful
   main-loop PC used by sleep membership, footprints and the future scan.

## Design and independent oracles

There are six new cases: two main-loop property modes, one UNKNOWN exhaustive case, two
all-asleep property modes and one source-evidence corruption regression. Reduced modes are no invariant and the compiler's known-empty property.
The exhaustive callback is pure true with absent observations. No forged metadata, stateful model
callbacks, private reflection or execution counters force coverage.

### Main-loop current footprint

T0 writes x=1 then y=1. T1 conditionally writes y=1 if x==0, then performs an empty atomic action.
Both start at zero. There are three counters per owner, giving nine counter pairs. The pairs [1,1]
and [1,2] can each have y=0 or y=1 depending on whether T1's conditional ran before T0's first write.
Every other pair has one value combination, giving eleven distinct positions.

Direct enumeration confirms that count:

| Counters | x | Possible y |
|---|---|---|
| [0,0] | 0 | 0 |
| [1,0] | 1 | 0 |
| [2,0], [2,1], [2,2] | 1 | 1 |
| [0,1], [0,2] | 0 | 1 |
| [1,1], [1,2] | 1 | 0 or 1 |

The tests assert all eleven literal position tuples, eleven visits and the first completed witness
[0,0,1,1]. Ordered callbacks and the unordered returned value set are independent oracles. These
counts apply to this fixture, not a general guarantee that DPOR never revisits a source position:
Spec33's incomparable contexts legitimately revisit one. A temporary external experiment replacing
only the main loop's current PC with zero produced twelve visits for this eleven-position fixture;
production files were not changed by that experiment. A raw mutation kill must still be measured.

### Exhaustive actual execution

One thread writes x=1 then attempts x=1%0. Exactly two committed positions exist, at PC zero and one;
the failed attempt creates no successor. Both returned and visitor positions assert x=0 at PC zero
and x=1 at PC one. UNKNOWN-property traversal must retain [0,0] with outcomes
ADVANCED/ASSERTION_FAILED and VIOLATION, including its matching callback.

### All-asleep actual execution

T0 writes x=1 then attempts y=1%0. T1 writes x=1 then z=1. Their first actions conflict; T0's
remaining failing action and T1's remaining work have independent footprints. Compiler instructions
use the public digest-free Program wrapper, as in Spec34, so legacy caching retains the all-asleep
fallback path. T0's action at PC one is asleep after prefix [0,1,1] and must still fail.

Literal witnesses are [0,0] ADVANCED/ASSERTION_FAILED and [0,1,1,0] with three ADVANCED outcomes then
ASSERTION_FAILED, both VIOLATION. Six committed counter pairs are [0,0], [1,0], [1,1], [1,2], [0,1],
[0,2]. Equivalently, T0 never commits its failing second action, so two T0 positions times three T1
positions give six. Failed attempts add no positions. Literal visit/trace notifications anchor both
witnesses without hidden state. Literal x/y/z values are checked independently in visitor positions
and the returned value set, including y=0 after the failed atomic attempt. Genuine source traces
replay directly with original evidence. Only digest-free wrapper evidence is enriched against the
original source model; classification uses the actual property or a true replay-only fallback.
The replay path is selected by the exploration Program, not its potentially altered trace digest.
A regression changes a genuine source digest and requires rejection, preventing reconstruction
from masking corruption. Plain legacy replay is not asserted compatible with SourceStep wrappers.

## Two lookup dispositions

`dfsDfs` and the all-asleep block of `dporDfs` each fetch a local PC and Step, use the Step only for
`step == null`, then pass Program/config/threadId to TransitionExecutor. The executor independently
fetches the actual PC and instruction; neither of these local Steps supplies execution metadata.

For valid enabled IDs/counters and immutable non-null ModelThread step lists, both the actual PC
and zero select non-null definitions. An enabled thread necessarily has a nonempty list, so zero
is in bounds. ModelThread freezes steps with List.copyOf; reads of these definitions do not execute
model actions or invoke user accessors. Replacing the local Integer.intValue result with zero
therefore cannot change either null guard or the actual action selected by TransitionExecutor.

This is a scope-limited public-evidence disposition for `dfsDfs`/234 and `dporDfs`/825, not a claim
about malformed IDs/counters, null steps, mutable lists, process/resource failures or performance.
The main-loop lookup `dporDfs`/377 is different: its local PC/Step actively control reduction and
cannot inherit this argument. Bytecode indices identify the mutations, not source lines.

## Acceptance

- Six new tests and the full suite pass; build and strict Javadoc pass.
- Fresh baseline/final scoped mutation populations match and every old kill remains killed.
- Report actual mutation deltas; retain old dispositions separately and assign open identities once.
- Default PIT, normalization and the three-file CodeRabbit CLI review pass. Trivial/minor PR
  feedback does not trigger another CLI invocation, per Sam's instruction.

## Baseline and verification

Merged PR #76 is `7a4e991`. Fresh baseline: all 928 tests pass, zero failures/errors/skips;
outer-DporExplorer260/289 KILLED, 17 SURVIVED, 12 NO_COVERAGE, zero timeout/error statuses.
After the major replay-evidence fix, all **934 tests** pass, including six new cases, with zero
failures/errors/skips. Scoped PIT improves **260/289 → 261/289 KILLED**, 16 SURVIVED and 12 NO_COVERAGE,
zero timeout/error statuses. This scoped run precedes only the final minor x-value assertions in
the UNKNOWN fixture; those assertions pass in the final six-case targeted run. Production bytecode
is unchanged, and no final scoped rerun is claimed after that assertion-only addition.
All 289 semantic identities match (class/method/descriptor/mutator/instruction indices/blocks), and
all 260 old kills remain killed. The only changed identity is dporDfs/377, removed Integer.intValue:
it is killed by allSleepingFallbackExecutesTheActualNonzeroCounter. Using the first action instead
of the current action can change sleeping membership and failure prefixes; coarse verdict equality
would not establish equivalence. No other raw status changes.

### Two new supported-contract dispositions

| Disposition | Method / bytecode index | Raw status |
|---|---|---|
| PC used only for a non-null local-step guard; executor retains actual PC | `dfsDfs` / 234 | SURVIVED |
| PC used only for a non-null local-step guard; executor retains actual PC | `dporDfs` / 825 | SURVIVED |

The arguments above apply to these two identities only. Together with Spec35's fourteen scoped
dispositions, sixteen identities have supported-contract dispositions (four survivors and twelve
uncovered). They are not counted as kills. Twelve additional survivors remain open:

| Separate follow-up | Method / indices | SURVIVED |
|---|---|---|
| Empty-enabled normalization | `dporDfs` / 20 | 1 |
| Antichain compaction | `dporDfs` / 130; `lambda$dporDfs$2` / 6, 7 (both return variants) | 4 |
| Visibility refresh and enable/disable interference | `dporDfs` / 569, 570, 582, 663 | 4 |
| Happens-before bookkeeping | `dporDfs` / 540, 546, 554 | 3 |
| **Total open** | **12 identities** | **12** |

Thus 261 kills plus 16 scoped dispositions plus 12 open identities account for all 289 identities.
The raw 16 SURVIVED/12 NO_COVERAGE remain unchanged by disposition prose. No exclusion or score
adjustment closes the remaining work.

All 18 visualizer normalization checks pass. Initial three-file CodeRabbit review reported one
major replay-evidence issue and one trivial state-value assertion issue; both are independently
validated and fixed. Follow-up review completed across the same three files with one minor request
for literal UNKNOWN-mode x values. That request is implemented and the six-case targeted suite passes;
no additional CLI run double-checks the minor assertion patch, per Sam's preference. No finding is
left unaddressed, and the follow-up review is not reported as a zero-finding result.

The corruption regression failed against the original reconstructing helper and passes with the fix.
Final `clean build javadoc pitest` after all fixes passes with **934 tests**, zero failures/errors/
skips, strict Javadoc and default PIT at **281/291**. All 291 default semantic identities and statuses
match the merged Spec35 evidence. Remote CI is unverified; this bundle is published as a draft PR. All local Gradle commands use
`--max-workers=1 -PpitestThreads=1`. Browser integration is not rerun
because executable production and report output are unchanged.
