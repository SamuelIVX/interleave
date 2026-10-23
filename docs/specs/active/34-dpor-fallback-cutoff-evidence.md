# Spec: DPOR all-asleep fallback cutoff evidence

## Objective

Protect reachable cutoff evidence in DPOR's all-asleep fallback. Continue roadmap 9-V1 after
Spec 33 with a focused terminal-evidence bundle, without changing reduction or replay behavior.

## Scope and applicability

The public two-argument Program constructor permits immutable compiler-produced SourceStep
instructions without carrying the source digest. TransitionExecutor dispatches by step type, so
these instructions still execute through SourceStep.transition. DporExplorer chooses its source
coverage/normalization path by Program.isSource instead. A digest-free wrapper therefore uses
legacy caching and can reach all-asleep fallback with an enabled source loop cutoff.

These tests use actual compiler instructions and the public constructor, not fabricated transitions,
private reflection, stateful predicates or incomplete footprints. They establish this specific
wrapper boundary; they do not establish universal support for arbitrary custom SourceSteps.
Plain legacy TraceReplayer calls Step.execute directly and is not a supported replay route for
these wrappers. For authenticity checks only, tests enrich the recorded choices/outcomes/cutoff
against the original source program and use source-aware replay with a non-null property.
No replay API behavior changes or wrapper replay compatibility claims are included.

## Requirements

1. An independent source loop asleep after the writer finishes SHALL still emit INCOMPLETE from
   fallback with its first committed prefix. The omitted cutoff attempt SHALL not enter that prefix.
2. Repeated encounters of one loop occurrence SHALL emit only its first witness, even when later
   encountered from a shorter root prefix. Separate loop owners SHALL retain separate witnesses.
3. Both returned evidence and visitor callbacks SHALL independently retain literal prefix, outcomes,
   reason, loop owner, instruction index, bound and completed-iteration count.
4. Omitting the visitor SHALL preserve both distinct witnesses and avoid null-callback failures.
5. The same contract SHALL hold with no property and with the compiler's known-empty property.
   Neither fixture may silently select the unknown-observation exhaustive fallback.
6. Preserve baseline semantic mutation identities and every old kill. Assign remaining identities
   explicitly; do not equate absent coverage with universal unreachability.

## Design and independent oracles

One parameterized matrix has three fixtures in both property modes (six cases). A separate optional-
visitor case uses two loop owners in both modes (two cases): eight new invocations total.

- T0 writes x=1; T1 has a true zero-limit loop with an empty body. Their footprints are independent.
  After T0 finishes, only T1 is enabled and asleep; fallback emits its cutoff at prefix [0]. The
  root attempt encounters that same occurrence again and must not replace or duplicate the witness.
  There are exactly two committed positions: counters [0,0] and [1,0].
- T0 writes x=1 then x=2; T1 has the same loop. The first witness prefix is [0,0], independently
  accounting for both writes. The three committed positions are [0,0], [1,0], [2,0]. A cutoff
  creates no successor; every later encounter of this owner is the same source occurrence.
- T0 has one write and T1/T2 each have a distinct zero-limit loop. At counters [1,0,0], both
  remaining actions sleep, and fallback must emit owners 1 and 2 separately, both at prefix [0].
  Root attempts are duplicates of those respective occurrences, not a reason to collapse owners.
  Exactly two positions are visited; the two omitted attempts create no new positions.

The counts follow both direct counter enumeration and one initial position plus the successful
writer actions. Every recorded action is literally ADVANCED. Each cutoff is LOOP_LIMIT at PC zero,
limit zero, completed iterations zero, and one expansion entry at final-check iteration zero.
Source-aware replay against the original program validates the precise span and expansion offset. Callback
order is asserted using literal ordered visit/cutoff notifications. Results and callbacks each match
literal Witness values, rather than merely comparing the two implementation-produced lists.

## Acceptance

- Eight new cases and the full existing suite pass; build and strict Javadoc pass.
- Fresh outer-DporExplorer baseline/final semantic identity sets match and all old kills remain.
- Report actual new kills, retained survivors and uncovered sites separately; no 100% requirement,
  mutation exclusion, gate adjustment or whole-checker closure claim.
- Default PIT and visualizer normalization pass with separate scopes stated.
- Complete the CodeRabbit CLI review of this substantive three-file bundle. Trivial/minor PR
  feedback does not trigger another CLI run, per Sam's instruction.

## Baseline

Current merged main is PR #74 at `1a994e1`. Fresh baseline: all 912 tests pass, zero failures/
errors/skips. Outer-DporExplorer PIT measures 251/289 KILLED, 18 SURVIVED and 20 NO_COVERAGE,
with zero timeout/error statuses. All local Gradle invocations use `--max-workers=1 -PpitestThreads=1`.

## Verification

All **920 tests** pass, including eight new invocations, with zero failures/errors/skips.
Outer-DporExplorer PIT improves **251/289 → 260/289 KILLED**: one former survivor and all eight
previously uncovered fallback-cutoff identities become killed. Final totals are 17 SURVIVED and
12 NO_COVERAGE, with zero timeout/error statuses. All 289 semantic identities match (class/method/
descriptor/mutator/instruction indices/blocks), all 251 baseline kills remain killed, and every new
kill names DporFallbackCutoffTest.

### Changed-identity ledger

All nine identities belong to `dev.samhb.interleave.dpor.DporExplorer.dporDfs`; indices are bytecode
instructions, not source lines.

| New kills | Indices | Public evidence oracle |
|---|---|---|
| 1 | 851 | Omitted attempts must remain terminal cutoffs rather than enter ordinary successor handling |
| 3 | 857, 858, 859 | First-prefix deduplication keeps distinct loop owners and suppresses repeated occurrences |
| 3 | 868, 869, 875 | Returned witnesses retain complete cutoff evidence and the committed prefix |
| 2 | 880, 883 | Optional-visitor guard and actual cutoff notification remain correct |

The rows sum to nine: one former survivor and eight formerly uncovered sites. The real compiler
instructions and public digest-free constructor reach these sites without hidden step state or
private mutation. Source normalization therefore cannot justify calling this fallback universally
unreachable. The tests do not broaden plain legacy replay to SourceStep wrappers.

### Retained follow-up census

These remain explicit follow-up audits, **not** blanket equivalence claims or exclusions. Counts are
semantic identities, not unique instruction indices; each lambda index 7 has two return variants.

| Follow-up | Method / indices | SURVIVED | NO_COVERAGE |
|---|---|---|---|
| Exhaustive-fallback metadata and duplicate-property reachability | `dfsDfs` / 135, 146, 148, 151, 157, 162, 167, 234 | 2 | 6 |
| Empty-enabled normalization boundary | `dporDfs` / 20 | 1 | 0 |
| Antichain compaction observability | `dporDfs` / 130; `lambda$dporDfs$2` / 6, 7 (both return variants) | 4 | 0 |
| Visibility refresh, enable/disable and current-PC pruning | `dporDfs` / 377, 569, 570, 582, 663 | 5 | 0 |
| Happens-before bookkeeping observability | `dporDfs` / 540, 546, 554 | 3 | 0 |
| All-asleep fallback PC lookup | `dporDfs` / 825 | 1 | 0 |
| Reduced duplicate-property reachability | `dporDfs` / 285, 287, 290, 296, 301, 306 | 0 | 6 |
| Optional-observation handling | `explore` / 62 | 1 | 0 |
| **Total** | **29 retained identities** | **17** | **12** |

All 18 visualizer normalization checks pass. CodeRabbit CLI completed its review of all three changed
files with zero findings; only measured verification/status prose was finalized afterward. Final
integrated `clean build javadoc pitest` passes with all 920 tests, strict Javadoc and default PIT
at 281/291 kills, matching the previously recorded default score. Older default mutation XML was
unavailable for an identity comparison; the 289-identity comparison above uses this bundle's fresh
scoped baseline/final reports. Browser integration was not rerun because executable production and
report output are unchanged. Remote CI is unverified; this bundle is published as a draft PR. Production, public API, dependencies,
report output, mutation scope/operators and gates are unchanged. No whole-class/whole-checker closure
is claimed; the historical expanded-scope aggregate remains unremeasured.
