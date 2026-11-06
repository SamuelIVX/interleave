# Thread-symmetry foundation: orbit and mapping evidence

## Scope

Based on merged PR #116 at 2d320ae. Roadmap #10 begins with a package-private value canonicalizer,
not an enabled search reduction. Existing runtime production files, stores, explorers, source
syntax, CLI/report/wire contracts and trace execution are unchanged. No runtime speedup is claimed.
Deferred and unmet conditional work remains parked.

A supplied partition is mathematical input, not evidence of behavioral interchangeability.
[Spec 75](../specs/active/75-thread-symmetry-canonicalization.md) gives the conditional orbit lemma
and separate initial-state/transition/property obligations for later search integration. Whole
PC/local frames move together, shared values stay fixed, and scheduling keys map the last owner.
Original-ID tie breaks select witnesses between equal frames. Immutable forward/inverse maps cover
all owners, including omitted fixed points. Integer-list text, Base64 and numeric/fixed context
cannot contain the key's vertical-bar delimiter; the key is opaque, not a wire format.

## Independent oracles

For N identical one-action increments, every concrete position is a binary completed-owner mask.
Independent Python Cartesian enumeration gives 16 masks for four owners and 256 for eight.
Symmetry identifies masks exactly by completed-owner count: five or nine orbits. A second route
sums binomial layers: 1+4+6+4+1=16 and 1+8+28+56+70+56+28+8+1=256; each layer is one orbit.
Java asserts literal concrete/orbit counts 16/5 and 256/9 over actual DFS visits.

For two split load/store increments, independent Python BFS tracks (PC0,PC1,shared,r0,r1).
A load copies shared into that owner's r; a store writes r+1. From all zeros until both PCs reach
two, enumeration yields 13 positions, eight (shared, multiset of (PC,r)) orbits and terminal shared
values {1,2}. Hand enumeration by lexicographic PC pair gives sizes 1,1,1,1,1,2,1,2,3 (sum 13).
The six symmetry-paired PC layers have orbit counts 1,1,1,1,2,2 (sum eight).
Actual DFS asserts those literals and both outcomes; source labels, four executed choices and
every original witness replay remain intact. Keys are counted offline, never fed to search.

Six explicit three-owner permutations test PCs, signed integer extrema, booleans and arrays.
Separate mixed local/context cases protect whole-frame correlation. Tests cover bijection
inversion, idempotence, deterministic equal-frame mappings, noncontiguous/partial groups,
partition normalization/singletons, shared/declaration/local distinctions, initial/last/plain
contexts, input isolation and invalid inputs.

## Verification and mutation history

Fresh merged baseline: clean build, strict Javadoc, **1,522 tests**, default 283/293 K + 10 S;
full R5/R6/R7 pass with zero uncovered/resource/run errors. Post-change integrated build, strict
Javadoc and **1,543 tests** pass, zero failures/errors/skips (21 new cases). A separate private-member
Javadoc run for the new production class also passes every doclint group with -Werror.

The new classes have no baseline mutation population. K means assertion KILLED, S raw SURVIVED.
No resource/error status earns kill credit. These different populations/scopes are never summed
as if they were one measurement.

| Run / scope | K / total | S | Other statuses | Disposition |
|---|---:|---:|---|---|
| Fresh main / default | 283/293 | 10 | Zero | R5/R6/R7 pass |
| Initial new classes | 149/157 | 8 | Zero | Scoped R6 pass |
| Added separation assertions / new classes | 151/157 | 6 | Zero | Same 157 identities; exactly two S→K |
| First aggregate / default | 433/450 | 16 | 1 MEMORY_ERROR | Correctly fails denominator gate; resource error uncredited |
| Bounded range / new classes | 149/155 | 6 | Zero | Scoped R6 pass |
| Named PC-null diagnostic / final new classes | 150/156 | 6 | Zero | Scoped R6 pass; R5/R7 skip |

Direct local-boolean and equal-frame partition separation kill two genuine gaps. Added initial
context separation also passes without another status change. The first aggregate disproved the
initial scope assumption: the unchanged state.* glob includes the new classes. Its increment
mutant changes the initial identity-list loop +1 to -1 and grows memory without bound. Production
now uses finite standard IntStream.range construction; indexed frame copying remains bounded.
No mutation settings, targets, filters, operators, targetTests, resource limits, floors or CI
parallelism change. A named PC-list null check follows the completed trivial review finding.

Final new-class split: outer 139/145 K + 6 S, mapping result 11/11 K. The measured expected default
denominator becomes 449: inherited class populations 19+52+97+125=293, plus 156 new mutants.
The unchanged 94% assertion-backed floor requires 423 kills: 94*449=42206, so 422 fails and 423
passes. Python integer-ceiling and decimal division independently confirm this. At 433 kills,
headroom is ten lost kills versus seven at baseline 283/293. This population-induced slack is
explicit; no percentage weakening is hidden in the update. The first aggregate retains every
inherited identity/status; the final run must confirm that independently.

Final aggregate: **433/449 K + 16 S**, zero uncovered/resource/run errors. Full R5/R6/R7
pass. All 293 inherited semantic identities/statuses exactly match the fresh baseline, and all
156 additional identities/statuses match the final scoped run. Frozen local-only XML:
/private/tmp/interleave-symmetry-bounded-default-final.xml; successful log:
/private/tmp/interleave-symmetry-bounded-default-final.log. This is a completed gate.

## Six retained final residuals

All are NonVoidMethodCallMutator. Fully qualified descriptors/identities remain in local XML;
indices/blocks below identify the final bounded population. No disposition is a kill or a filter.

| Method / index / block | Scoped disposition |
|---|---|
| constructor / 25 / 5 | Removing groups requireNonNull still throws NPE when iterating the null list. No exact message/stack guarantee. |
| constructor / 96 / 23 | Removing owner requireNonNull still throws NPE when unboxing that null owner for range validation. |
| canonicalizePosition / 5 / 0 | Removing state requireNonNull still throws NPE at state.threadCount for valid PCs. Other invalid inputs retain only the documented exception-type contract; no diagnostic precedence claim. |
| canonicalizePosition / 195 / 51 | Removed sentinel-check unboxing becomes zero, so -1 uses indexOf(-1). The validated full permutation contains only nonnegative IDs, making that result -1 anyway. Other owners still take their inverse mapping. Pre-fix identity was 204/52. |
| compareFrames / 153 / 52 | Zero instead of the final equal-frame ID comparison preserves values; stable List.sort retains normalized ascending IDs. This depends on copied/sorted group input and Java stable sorting, not arbitrary comparator callers. |
| compareFrames / 119 / 40 | One last-owner-marker unboxing becomes zero. Bounded sorted-input permutation/context tests retain the same result. No general comparator-equivalence proof or kill is claimed; retain this coverage limitation for search integration. |

The last residual is an unproven coverage limitation, not a claim that an altered non-total
comparator is safe. Production retains both marker comparisons and the total tie order.

## Review, reproduction and limits

CodeRabbit CLI 0.8.1 first completed six files with six trivial findings. Named PC-null diagnostics,
ordinary file-summary comment, delimiter invariant, local-only evidence labels and baseline/final
status wording were addressed. A final core review includes the subsequent bounded range repair
and measured denominator update: **review_completed, all seven files, zero findings**.
Agent-setup review-record saved. No repeated CLI review merely to double-check trivial feedback.

All /private/tmp evidence is local-only forensic data, not shared artifacts. Frozen reports:
interleave-symmetry-default-baseline.xml, interleave-symmetry-first-scoped.xml,
interleave-symmetry-final-scoped.xml (pre-fix 157), interleave-symmetry-first-default.xml (failed),
interleave-symmetry-bounded-precheck-scoped.xml and interleave-symmetry-bounded-final-scoped.xml.
Review logs: interleave-symmetry-review.ndjson and interleave-symmetry-final-review.ndjson.
Reproduce baseline from merged 2d320ae and final from this branch. Capture each report before the
next PIT run overwrites build/reports/pitest/mutations.xml:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.state.ThreadSymmetryCanonicalizer*'
```

Manual browser/CLI and normalization checks are omitted: no runtime, artifact, schema or UI change
is wired. Existing runtime regressions still run in the integrated suite. Future integration must
validate behavioral eligibility and real quotient search; these offline orbit counts prove no
runtime state reduction or performance improvement.
