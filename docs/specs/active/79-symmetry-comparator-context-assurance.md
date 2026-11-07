# 79 — Symmetry comparator and mapped-context assurance

## TL;DR

Implement bundle B / V1 and B2 from the approved
[post-task-#10 inventory](../../plans/post-task10-follow-ups.md), after bundle A merged in PR #120
at `db4467c`. Characterize the comparator's ordering laws directly, retain independent public
key/mapping oracles, and protect exact DPOR context handling under equal-frame ties and stale
sleeping actions. Production search/canonicalization/store behavior is unchanged.

The formerly unproven `compareFrames /119/40` mutation is now assertion-killed. It violates the
comparator contract even when a particular collection-sort path happens to avoid exposing it.
The previously justified stable-ID fallback mutation remains a survivor; this work does not add
an artificial strictness requirement just to improve the score.

## Comparator finding and independent evidence

`compareFrames` compares PC, typed local values, last-owner marker and an original-ID fallback.
The target NonVoidMethodCallMutator replaces the left marker's `Integer.intValue()` with zero.
Its source-level analogue is changing `left == last` to `left == 0` under the existing null guard.
With last owner 1, comparing owner 1 with itself then returns -1 rather than zero. That violates
reflexivity independently of frame values or a reference implementation of the comparator.
The production comparator retains the full expression and satisfies the new cases.

Existing public canonicalization tests exercise only comparisons chosen by `List.sort`.
Normalized groups enter sorting in ascending original-ID order, with at most eight owners.
Inspection of the local OpenJDK 26.0.2.1 sources showed `ArrayList.sortRange` delegates to
`Arrays.sort`; its default small TimSort path uses run detection and binary insertion rather than
merging. Those comparisons can hide the asymmetric mutant for these inputs. This observation is
not a general comparator-equivalence proof or a new runtime assumption for the checker.

An isolated, temporary source-level mutant probe gave the following results for three equal
frames, last owner 1. The independent expected canonical owner order is [0,2,1]: non-last owners
0 and 2 first, then last owner 1. Self-comparison must return zero.

| Version | Default OpenJDK sort | `java.util.Arrays.useLegacyMergeSort=true` | compare(1,1) |
|---|---|---|---:|
| Actual production | [0,2,1] | [0,2,1] | 0 |
| Left-unboxing analogue | [0,2,1] | [0,1,2] | -1 |

The alternate runtime mode exposes a wrong public mapping; the direct comparator-law regression
exposes the problem in either mode. No production file or test-JVM sort mode was changed for the
probe. It used a separate temporary classpath and was deleted afterward. Results remain at
`/private/tmp/interleave-symmetry-assurance-sort-probe.json`. The local runtime identified itself
as Homebrew OpenJDK 26.0.2.1; the actual PIT result below, not this source analogue, establishes
the mutation kill.

## Comparator coverage and reflection tradeoff

Strengthen the four existing PC/integer/boolean/array fixtures with sign reflexivity,
antisymmetry and transitivity checks. Add ten maximum-size equal-frame cases with independently
listed mapping sequences: plain context, initial -1, and last owners 0 through 7. The last owner
occupies canonical slot 7; other original IDs stay ascending. Verify both the public mapping/key
contract and the comparator laws across every pair/triple in each finite fixture.

The tests access the existing private comparator reflectively. This is deliberate: public sorting
need not ask self-comparisons or both argument orientations, while exposing a new production API
solely for testing would add unnecessary surface. The comparator's existing mathematical contract
is the test target. A future extraction/rename requires updating this small test lookup.

The law checks allow equal values to compare equal. Literal public mapping oracles separately
protect deterministic stable witnesses. An exploratory version that required every equal-frame
ID pair to compare strictly killed both the marker and the known stable-ID fallback mutations.
That extra private strictness requirement was dropped: it is unnecessary for the public stable-sort
contract, and its former redundancy argument remains applicable. The final score below counts
only the genuine marker-contract observation. Exploratory XML is retained separately at
`/private/tmp/interleave-symmetry-assurance-exploratory-strict-order.xml`; it is not the final run.

These finite comparator fixtures are characterization evidence, not a mechanically checked proof
or a proof covering arbitrary Java callbacks, cyclic programs or all modeled values.

## Missing mapped-context cases

The existing store tests already cover owner/PC transport, copying, clear/fresh-copy and validation.
Add only two focused cases in `SymmetryCompositionTest`:

1. Two owners both reach PC 1 in a two-action empty source model. Their complete frames tie.
   Record empty, owner-0 stale-PC-0, owner-1 stale-PC-0 and both-stale contexts. Every first distinct
   context must be uncovered; exact repeats must be covered. A current-PC-1 action remains distinct
   from the stale PC 0 action. Empty/subset contexts do not cover supersets. Both stale actions
   leave the current actions awake, respecting the caller's all-asleep normalization contract.
2. Three identical owners first set local r=1, then have an empty action. Advancing owner 0 gives
   canonical-to-original [1,2,0]; advancing owner 2 gives [0,1,2]. Advanced-owner stale actions
   map to canonical slot 2 and match. The equal unadvanced owners expose conservative tie matching:

   | Incoming configuration/action | Canonical sleeping owner | Coverage after recording left owner 1 |
   |---|---:|---|
   | Left configuration, owner 1 / PC 0 | 0 | First visit: uncovered |
   | Right configuration, owner 1 / PC 0 | 1 | Uncovered, even though one valid frame permutation keeps owner 1 fixed |
   | Right configuration, owner 0 / PC 0 | 0 | Covered through another valid complete permutation |

The fixture PCs and local values come from actual source transitions. The constructed sleep sets
exercise the store's documented input domain; this does not claim that every set is generated by
the current DPOR traversal history. Expected mappings, tokens
and Boolean outcomes are derived from the literal frame order, not computed through the production
normalizer. This preserves the deliberate conservative policy; it does not implement stabilizer
search, subset transport or any 9-V5 representation optimization. No alias-support feature starts.

## Fresh baseline and final scoped evidence

Baseline is merged main `db4467c`, with no production edits. All local Gradle commands use one
worker and one PIT thread. Fresh build/Javadoc and default PIT pass at **435/451 K + 16 S**,
zero uncovered/resource/run errors, R5/R6/R7 pass. Fresh store-only baseline is **79/79 K**.
The canonicalizer/result subset of that default baseline is **150/156 K + 6 S**. These overlap
with the default scope; do not sum them into a whole-checker figure.

Final full build/Javadoc and the expanded regression suite pass. Final focused combined scope is
**230/235 K + 5 S**, zero uncovered/resource/run errors; scoped R6 passes. It comprises:

| Class | Fresh baseline | Final |
|---|---:|---:|
| ThreadSymmetryCanonicalizer | 139/145 K + 6 S | 140/145 K + 5 S |
| CanonicalPosition | 11/11 K | 11/11 K |
| SymmetryStateStore | 79/79 K | 79/79 K |

All **235 exact identities** (class, method descriptor, mutator, every instruction index/block)
are retained. Exactly one status changes: `compareFrames /119/40`, SURVIVED to KILLED. All other
statuses and every prior kill remain unchanged. No changed production statements, new mutant
population, exclusions, operator/target-test/resource changes or denominator/floor changes exist.
Only the per-class census notice changes from 139/145 to 140/145.

Frozen artifacts:
`/private/tmp/interleave-symmetry-assurance-baseline-default.xml`,
`/private/tmp/interleave-symmetry-assurance-baseline-store.xml`,
`/private/tmp/interleave-symmetry-assurance-scoped-final.xml` and
`/private/tmp/interleave-symmetry-assurance-scoped-identity.txt`.

## Five retained canonicalizer residuals

All remain raw SURVIVED; none earns kill credit. IDs refer to measured instructions, not mutable
source-line references. The original [Spec 75 ledger](../../measurements/75-thread-symmetry-foundations.md)
remains historical; its targeted marker entry is superseded by this measured kill.

| Method / index / block | Retained scoped disposition |
|---|---|
| constructor /25/5 | Removing the group null check still fails when iterating null; no exact diagnostic guarantee. |
| constructor /96/23 | Removing the owner null check still fails on unboxing null. |
| canonicalizePosition /5/0 | Removing the state null check still fails at threadCount for valid PCs; invalid-input precedence is not claimed. |
| canonicalizePosition /195/51 | The sentinel's altered unboxing falls through to indexOf(-1), still -1 on the validated permutation. |
| compareFrames /153/52 | Removing the equal-value original-ID comparison preserves public values/witnesses under ascending normalized input and stable sorting. No new strict private-ID requirement is imposed. |

## Completion boundary

Bundle B covers V1 and the identified missing B2 cases. Bundle C's historical 34 explorer non-kills
still need their own fresh census and triage. General formalization (9-V6), DPOR representation or
subsumption optimization (9-V5), optional support expansions and roadmap #11/#12 remain excluded.
No browser/report/CLI/schema/native-stream behavior changes; browser checks are not rerun for
this test/metadata/documentation-only bundle. Do not carry earlier browser figures forward as a
new measurement. The final default gate and selective CLI review are complete as recorded below.


## Final publication record

- Full build, **1,652 tests** (12 new cases) and strict Javadoc pass.
- Final default PIT: **436/451 K + 15 S**, zero uncovered/resource/run errors, R5/R6/R7 pass.
  All 451 exact identities remain. Exactly `compareFrames /119/40` changes S to K; every other
  status and previous kill is unchanged. The 94% floor still requires 424 kills. The margin rises
  from eleven to twelve through this assertion-earned kill, not a population or floor change.
- The combined focused scope remains **230/235 K + 5 S**, with all 235 identities retained and
  that same single status change. Do not add this overlapping scope to the default total.
- CodeRabbit 0.8.1 completed an actual `review_completed` over six files, with one minor request
  to record default-gate/review completion explicitly. This section addresses it; no CLI rerun
  for minor documentation feedback. Code/tests are unchanged since their final passing checks.
- Browser checks are skipped because no runtime, schema or presentation code changes. No new
  benchmark, runtime-speedup or broad proof claim is made.
- Default XML and identity comparison are frozen as
  `/private/tmp/interleave-symmetry-assurance-default-final.xml` and
  `/private/tmp/interleave-symmetry-assurance-default-identity.txt`.

V1/B2 are implemented and locally verified, pending PR merge. V2/bundle C remains next; all other
optional expansions and explicitly parked roadmap items remain outside this bundle.
