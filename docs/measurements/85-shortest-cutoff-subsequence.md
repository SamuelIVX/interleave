<!-- Exact subsequence-domain proof, finite budget boundaries and retained mutation limits. -->
# 85 — Bounded shortest cutoff subsequence evidence

## TL;DR

Fresh merged main `7c0072d` passes build/strict Javadoc and **1,786 tests**. The actual simplifier
baseline is **48/54 K + 6 S**; it is not copied from historical Spec 74 figures. Final integrated
build/strict Javadoc passes **1,811 tests**, adding 25 new invocations without removing existing
simplifier or CLI cases. Final affected census is **94/102 K + 8 S** in CutoffSimplifier and
**5/5 K** in its nested ShortestResult. Scoped R6 passes with zero NC, timeout, memory, nonviable
or run-error statuses. Scoped R5/R7 are not evaluated.

The separate default gate is **436/451 K + 15 S**, R5/R6/R7 pass. All 451 exact baseline
identities/statuses are unchanged. No mutation operators/filters/target tests/floors/resources,
source caps, strategy/store algorithms or CI parallelism changed. Raw survivors remain survivors.
See [Spec 85](../specs/active/85-shortest-cutoff-subsequence.md) for the selected I1 library contract;
I2 CLI proof presentation remains pending.

## Independent oracles and proof limits

Four separate owners add weights 1,2,4,8. The target zero-limit loop enables only at x=5 or 15.
The original [0,1,2,3] reaches 15. Single deletions produce 14,13,11,7 and half-chunk deletions
produce 12 or 3, so existing simplify stays at four. The only proper valid subset is [0,2],
which reaches 5. Singleton values 1,2,4,8 and empty 0 cannot enable the cutoff. An independent
sixteen-mask execution/replay oracle confirms exactly these two valid schedules, including original.

Descending-length search checks four triples, two pairs up to [0,2], four singletons and empty:
4+2+4+1 = **11**. Budget six returns [0,2] unproven; ten leaves empty unchecked; eleven proves
length two. Budget twelve still reports eleven evaluations. A timed x!=5 property invalidates
the pair; rejecting all fifteen proper index subsets then proves the original length four.

For four repeated noise choices, one valid candidate per descending length reaches empty in four
evaluations, even with Long.MAX_VALUE allowance. An authenticated empty original is proven with
zero evaluations. At K=0 one committed choice is necessary; K=1 requires [0,0,1] at target PC 2.
Loop body/check tests retain omitted PC 2, limit/iteration 1 and actual ancestry. Early assertions,
modulo failures, property violations, corrupt originals and unexpected predicate errors retain the
shared authentication/execution semantics. No source interpreter or cost calculator is duplicated.

A separate fixture needs all five supplied owner-0 choices to enable the target, while an outside
owner can enable it in one. The operation rejects all 31 proper index subsets and proves length
five only within its original domain; the authentic one-choice outside witness proves why no global
shortest-schedule claim is made. Repeated owner IDs can duplicate schedule values across index
subsets; those evaluations still count. Interrupted best-found output is not promised 1-minimal.

## Population and inherited lineage

The candidate execution/replay helper retains all **eight exact identities/statuses**, all K.
Shared authentication moves original guards and the pure absent-property predicate out of simplify.
Simplify changes 42 to 30 sites; authenticate has 14. Compiler-generated lambdas are renamed:
the old one-site true predicate moves to lambda$authenticate$0, and the three-site chunk-level
lambda becomes lambda$simplify$0. Do not treat equal/reused lambda names as semantic lineage.

New shortestSubsequence has 30 sites (28 K, two S); advance has 16 K. Existing candidate has eight K,
chunk lambda three K, absent-property lambda one K. New immutable result has five K. The enclosing
population grows 54 to 102, separately from the result's five. Population growth is not credited
as inherited kills; the six prior survivor arguments remain as explicit semantic lineage below,
with changed indices/descriptors where guards moved. First and final post-implementation populations
have all 107 exact identities/statuses unchanged after minor documentation and latch-test fixes.

## All eight raw survivors

Method names are unambiguous in this revision. Class prefix is dev.samhb.interleave.minimize;
mutator prefix is org.pitest.mutationtest.engine.gregor.mutators. Indices/blocks are revision/JDK
artifacts, not source-line citations. Full descriptors remain in frozen XML. Each disposition is
scoped to admitted immutable source traces, deterministic steps and pure deterministic properties.
Owner: cutoff-simplification maintainer; reopen when its stated guard/domain/purity assumptions change.

| Method | Indices / blocks | Mutator | Raw | Disposition |
|---|---|---|---|---|
| authenticate | 5 / 0 | NonVoidMethodCallMutator | S | Program requireNonNull removal still reaches NPE at program.isSource. Exception type only; exact host message/stack is not promised. Retain the named guard. Inherited program-guard argument, moved from simplify. |
| shortestSubsequence | 68 / 11 | ConditionalsBoundaryMutator | S | remaining>0 becomes >=0; checked<limit independently prevents an additional candidate, including limit zero. New bounded-work redundancy, not a reason to remove either progress guard. |
| shortestSubsequence | 72 / 12 | ConditionalsBoundaryMutator | S | checked<limit becomes <=limit; remaining>0 independently limits evaluations. Counter increment cannot overflow before the limit; a proof can stop earlier. Keep both guards for single-counter progress faults. |
| simplify | 29 / 4 | ConditionalsBoundaryMutator | S | remaining>=0 becomes >0, skipping only the final redundant pass after N strict deletions; best is already empty. Inherited old index 81. |
| simplify | 136 / 22 | ConditionalsBoundaryMutator | S | Strict candidate-length comparison becomes non-strict; a real deleted nonempty chunk already makes its candidate shorter. Inherited old index 188. |
| simplify | 175 / 30 | MathMutator | S | Redundant pass ceiling increments instead of decrements; strict deletion/no-progress break still finishes in at most N+1 passes on int-sized N. Preserve the ceiling for progress faults. Inherited old index 227. |
| simplify | 134 / 21 | NonVoidMethodCallMutator | S | Candidate length defaults to zero only after a valid strict deletion at positive current length, preserving the guard outcome. Inherited old index 186. |
| simplify | 185 / 31 | NonVoidMethodCallMutator | S | Final replay repeats original/adopted-candidate authentication. No claim for impure callbacks, mutable evidence, callback counts or externally imposed resource stops. Retain final validation. Inherited old index 237. |

These are scoped arguments, not universal/performance equivalence or mechanically checked proofs.
No fabricated mutable callbacks, widened admission or weakened tests are used to force kills. The
candidate limit measures attempted prefixes, not elapsed time, memory or individual model steps;
mandatory original authentication is outside that count. Exponential work remains possible within
a deliberately large allowance. Partial optimality never changes trace INCOMPLETE into PASS.

## Artifact, consumer and review evidence

A standalone public-library probe against the built distribution checks budgets 0,6,11,12, including
partial/proven flags, literal work counters and unchanged cutoff. It exports existing direct trace
payloads with fresh snapshots; its temporary diagnostic envelope is not a new production schema.
The probe's initial missing import was corrected before these passing runs. Actual legacy CLI calls
under DFS/static POR/DPOR/CBS retain INCOMPLETE, empty simplified prefix and one snapshot. All eight
fresh direct payloads normalize, plus 22 compatibility verdict cases/four symmetry assertions.
No DOM rerun: no schema/normalizer/renderer change. I1 does not expose proof metadata through the CLI.

CodeRabbit 0.8.1 completed all eight submitted files with two minor documentation findings: numeric
spacing and pending measured evidence. Both are patched with the actual values. No CLI rerun for
minor feedback. A separate local pass checks index progression, original-domain retention, stopping
proof, zero-budget authentication, exception propagation and invocation-local data; concurrent tests
now release both tasks through a shared start gate. Later measurement/status references receive local
review. No outstanding supported-path correctness defect was found in this selected scope.

Artifacts use `/private/tmp/interleave-shortest-cutoff-`: baseline.xml, first-scoped.xml,
final-scoped.xml, default-baseline.xml, default-final.xml, corresponding logs and review.ndjson.
Temporary artifacts may expire; this document retains the counts, lineage and raw dispositions.
