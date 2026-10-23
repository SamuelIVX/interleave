# Spec: Source DPOR sleep coverage and pruning contracts

## Objective

Protect source DPOR's existing sleep-context coverage, visibility and collision-pruning boundaries
with public evidence oracles. Continue roadmap 9-V1 after Spec 32 without changing reduction rules.

## Scope

- Add `DporSleepContractTest`, this specification and a roadmap update.
- Exercise source caching and sleeping through real source programs, exact/default stores and a
  real one-bit Bloom store. Include future-read and intermediate-property witness checks because
  they constrain which actions may sleep.
- No production, API, dependency, report-output, mutation-scope or gate changes. Optional-observation,
  bookkeeping, remaining fallback reachability and helper-class audits remain separate.

## Requirements

1. Independent two-action owners with invisible writes SHALL retain a complete representative,
   suppress already-covered terminal revisits and explore fewer than the nine-position product.
2. A canonical source position reached with incomparable sleep sets SHALL be traversed in both
   contexts. An earlier context can cover an incoming context only when its sleepers are a subset
   of the incoming sleepers. Value-state equality alone is insufficient.
3. Source sleep coverage SHALL preserve approximate-store pruning when a reported visited position
   has no exact coverage entry. Such a collision-pruned run makes no exact absence claim.
4. A write dependent on an owner's future read SHALL remain available across an independent prefix,
   retaining both possible recorded observations and their actual replayable schedules.
5. Known complete observations SHALL retain visible intermediate states and a concrete violation
   from an independent writer, alongside the safe completed order.
6. Preserve baseline semantic mutation identities and existing kills. Record improvements and assign
   retained identities to explicit follow-ups or independently justified scoped dispositions.

## Design and independent oracles

The first four tests run with no property and with a known-empty observation set: eight invocations.
Both modes use reduced traversal; these fixtures deliberately have no unknown-property wrapper.
Two additional tests use compiler-derived nonempty complete observations, giving ten new cases.

- **Independent work:** T0 writes x=1 then x=2; T1 writes y=1 then y=2. The exhaustive product is
  three counters per owner, independently giving nine distinct positions. Empty observations allow
  sleeping at [1,0] and [0,1], so [1,1] is omitted. The eight asserted positions are the two
  serial boundary paths, sharing initial and terminal positions; the first witness is [0,0,1,1].
  Four edges per serial path give five positions each; two shared endpoints give eight total.
- **Incomparable contexts:** T0 writes y=1 then does an empty atomic action; T1 writes y=1 then
  x=1. Both first writes conflict; after them the remaining actions commute. Reaching counters [1,1] via
  owner-choice prefix [0,1] leaves T0 asleep; prefix [1,0] leaves T1 asleep. Neither sleeping singleton
  covers the other. All nine counter pairs occur, but [1,1] is visited twice: ten visits, nine
  unique values/counters. Literal ordered notifications assert the two contexts through public
  traversal evidence, without inspecting private antichains. Other covered merges emit no duplicate
  completion. These counts are fixture-specific, not a general enumeration guarantee.
- **Bloom collision:** one bit and one probe means every mark fills the sole bit. The initial
  position is genuinely unseen; after it is marked every successor collides and has no exact
  coverage entry. Exactly the root is visited, with no terminal witness. This follows both from
  the one-bit construction and the pigeonhole fact that every hash index is zero modulo one;
  it depends on no particular canonical hash or fabricated store callback.
- **Future read:** T0 has an empty prefix followed by y=x; T1 writes x=1. Independent initial work
  must not hide ordering around the later read. Finishing with y=0 and y=1 is possible; the literal
  schedules [0,0,1] and [0,1,0] independently demonstrate both outcomes.
- **Visibility:** observing x and y keeps all nine counter pairs of the independent product.
  A separate one-write-per-owner fixture starts x=y=0 with property x==1 || y==0. T0 then T1
  completes safely; T1 first violates at [0,1]. Literal result and callback witnesses are asserted
  independently. Every successful recorded action is ADVANCED, and source terminal replay uses
  the supplied property or a true replay-only fallback when exploration had no property.

## Acceptance

- All ten new invocations and the existing full suite pass; build and strict Javadoc pass.
- Fresh baseline/final outer-DporExplorer PIT populations match and all old kills remain.
- Keep literal public evidence oracles; do not use private reflection, stateful model callbacks,
  hidden counters, forged observation metadata or synthetic store responses to force coverage.
- Default PIT and visualizer normalization pass with separate scopes reported.
- Complete a CodeRabbit CLI review of the substantive three-file bundle. Trivial/minor PR feedback
  does not require another CLI invocation, per Sam's instruction.

## Baseline

Merged PR #73 is `239a388`. Fresh baseline: all 902 tests pass, zero failures/errors/skips.
Outer-DporExplorer PIT is 234/289 KILLED, 35 SURVIVED and 20 NO_COVERAGE, with zero timeout/error
statuses. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`.

## Verification

All **912 tests** pass, including ten new invocations, with zero failures/errors/skips.
Outer-DporExplorer PIT improves **234/289 → 251/289 KILLED**. Seventeen former survivors become
killed; all 20 previously uncovered identities remain uncovered. All 289 semantic identities match
(class/method/descriptor/mutator/instruction indices/blocks), all 234 old kills remain killed, and
every new kill names DporSleepContractTest. There are zero timeout/error statuses.

### Changed-identity ledger

All identities belong to `dev.samhb.interleave.dpor.DporExplorer`; indices are bytecode instructions.

| New kills | Method / indices | Public oracle |
|---|---|---|
| 7 | `dporDfs` / 10, 11, 21, 58, 59, 67, 68 | Source dispatch and all-asleep normalization preserve the second incomparable visit |
| 4 | `dporDfs` / 98, 99, 121, 137 | Exact coverage storage/pruning, deduplicated completion and real Bloom-collision boundary |
| 3 | `lambda$dporDfs$1` / 6, 7 (both boolean-return variants) | Coverage must distinguish incomparable sleepers and prune previously covered work |
| 1 | `dporDfs` / 632 | Correct other-owner PC retains both future-read observations and literal schedules |
| 2 | `dporDfs` / 682; `explore` / 131 | Complete observations retain the visible nine-position grid |

These rows sum to 17. The visible grid and incomparable-context cases distinguish the two directions
of the coverage predicate; neither a constant true nor a constant false answer preserves both.

### Retained follow-up census

These are explicit follow-up gaps/audits, **not** blanket equivalence declarations or exclusions.
The census includes every retained identity; counts are identities rather than unique indices.

| Follow-up | Method / indices | SURVIVED | NO_COVERAGE |
|---|---|---|---|
| Exhaustive-fallback metadata and duplicate-property reachability | `dfsDfs` / 135, 146, 148, 151, 157, 162, 167, 234 | 2 | 6 |
| Empty-enabled normalization boundary | `dporDfs` / 20 | 1 | 0 |
| Antichain compaction observability | `dporDfs` / 130; `lambda$dporDfs$2` / 6, 7 (both boolean-return variants) | 4 | 0 |
| Visibility refresh, enable/disable and current-PC pruning | `dporDfs` / 377, 569, 570, 582, 663 | 5 | 0 |
| Happens-before bookkeeping observability | `dporDfs` / 540, 546, 554 | 3 | 0 |
| All-asleep fallback PC and cutoff selection | `dporDfs` / 825, 851 | 2 | 0 |
| Reduced duplicate-property reachability | `dporDfs` / 285, 287, 290, 296, 301, 306 | 0 | 6 |
| All-asleep fallback cutoff reachability/evidence | `dporDfs` / 857, 858, 859, 868, 869, 875, 880, 883 | 0 | 8 |
| Optional-observation handling | `explore` / 62 | 1 | 0 |
| **Total** | **38 retained identities** | **18** | **20** |

Source all-asleep normalization does not by itself prove the fallback sites universally unreachable:
custom SourceStep instructions and source steps wrapped in legacy Programs require an explicit
supported-contract audit. Antichain compaction can affect representation cost; do not infer broad
output/error/performance equivalence from these passing finite fixtures. Pure model operations can
still throw, so preparation and error/observer ordering remain part of any such audit.

Final integrated `clean build javadoc pitest` passes: all 912 tests, strict Javadoc and default PIT
at 281/291 kills. All 291 default semantic identities and statuses match the merged Spec 32 evidence.
All 18 visualizer normalization checks pass. CodeRabbit CLI completed review of all three changed
files with zero findings; only measured verification/status prose was finalized afterward. Browser
integration was not rerun because executable production and report output are unchanged. Remote CI
is unverified; this bundle is published as a draft PR. No whole-class or whole-checker closure is
claimed; the historical expanded-scope aggregate remains unremeasured.
