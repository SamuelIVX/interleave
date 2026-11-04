# Spec: Source forward-lowering contracts

## Objective and scope

Continue 9-V2 with SourceCompiler.lower/patch and the successor validation in compile.
Add SourceLoweringContractTest, this spec, the roadmap and a Spec 16 pointer. Keep empty-arm
joins, recursive continuations and their strictly-forward validation together because they share
one fragment invariant. No production, harness, dependency, mutation target/filter/operator/
exclusion, denominator/floor, heap/time or CI parallelism changes. Shared-validator remapping
and remaining syntax metadata gaps stay separate; completed 9-V1 remains closed.

## Public contract and independent oracles

[Spec 16.02](16-concurrent-program-parser/02-control-flow-and-atomic-execution.md) requires
pc < nextPc <= size for a successful instruction, direct continuation edges without scheduled
jumps and an initially terminated empty thread.

Twenty cases enumerate four flat arm shapes (both empty, only true nonempty, only false nonempty,
both nonempty), both condition outcomes and both continuation forms (another atomic action or
thread end), plus all four outcomes of a nested conditional with an empty inner true arm. An
initial assignment makes the root contain multiple instructions and places nested entries above
zero. One additional case checks an empty root thread with no dummy instruction or transition.

The CSV table states literal compiled sizes, complete PC paths including the end sentinel and
counter values at every committed configuration. Direct transitions verify each table first,
including ADVANCED outcomes, absent failure/cutoff, unchanged parents and terminal enabledness.
Then exact DFS must record the independently checked instruction-index path, with no extra jump
steps. Replay must end at the literal sentinel and final counter. Empty-thread search/replay
must complete without choices or executed-step evidence.

Manual instruction/branch enumeration gives flat sizes 2/3/4 without the continuation and 3/4/5
with it; the nested layout has six instructions. Independent Python enumeration of named actions
(set1, outer, optional inner, selected assignments, optional add100) confirms every one of the
twenty literal count/path/value rows. This checker uses no compiler, fragment, patch or source-map
helper. In Java the colon-separated literal table is only decoded, never recalculated from the
compiled targets. Counter values distinguish an incorrect arm or fallthrough as well as extra PCs.

## Fresh baseline and selected ledger

Merged PR 105 at 6dd4d1d passes the fresh clean build, strict Javadoc and 1,191 tests.
Fresh compiler: **733/787 KILLED, 32 SURVIVED, 22 NO_COVERAGE**, zero resource/run errors.
All 787 identities/statuses match Spec 63. Of 54 raw residuals, 13 have existing scoped
dispositions and 41 remain untriaged. Select these five SURVIVED ConditionalsBoundaryMutator
entries; their methods each have one descriptor:

| Method / index / block | Changed equality boundary | Scoped disposition |
|---|---|---|
| lower / 41 / 9 | start < 0 becomes start <= 0 | Only the root return can change; its entry is unused |
| lower / 82 / 20 | yes.start < 0 becomes <= 0 | Recursive fragment entry cannot be zero |
| lower / 108 / 29 | no.start < 0 becomes <= 0 | Recursive fragment entry cannot be zero |
| compile / 613 / 169 | yes <= pc becomes yes < pc | Original lowering never emits a self-target |
| compile / 622 / 172 | no <= pc becomes no < pc | Original lowering never emits a self-target |

These are scoped pipeline dispositions, not assertion kills. Keep their raw SURVIVED statuses,
keep the defensive guards and propose no exclusions or equivalence-count overrides.

## Fragment and successor proof

Every lower invocation starts with entry -1. Empty invocations retain -1. A nonempty invocation
appends its first draft before descending, so its original entry is that first draft's PC. Each
recursive invocation begins after its parent's draft was appended; therefore every recursive
nonempty entry is at least 1. Empty entries are -1. Recursive entries can never equal zero,
so changing either arm test from < 0 to <= 0 is unobservable.

The root may begin at zero. Changing its start test to <= 0 can replace that root entry at the
next top-level node, but neither drafts nor exit lists depend on the variable start. The only root
consumer is compile, which reads whole.exits() and never whole.start(). Recursive entries still
never equal zero, so their behavior is unchanged. Empty roots are unchanged as well.

Inductively, a fragment's unresolved exits describe exactly its unfinished continuation edges.
A non-IF draft adds both edges. An IF points each nonempty arm at a child appended after itself;
an empty arm retains that condition's pending edge. The IF combines child exits with empty-arm
edges. A following node patches the previous exits to its newly appended PC, strictly after their
origin instructions. The root finally patches all remaining exits to drafts.size(), also strictly
after every origin. Thus every completed target lies in (pc, size], including empty arms and
nested joins; there are no self-targets. A single boundary mutation in compile changes only the
defensive check, not that construction, so relaxing <= pc to < pc cannot affect supported input.

This proof assumes the current private pipeline and original, singly mutated compiler. It does
not cover reflected/corrupt drafts, arbitrary private AST injection, a second simultaneous lowering
mutation or a future consumer of the root entry. Revisit the dispositions if those seams change.
Public regression cases exercise the invariant and its consumers; survival alone is not a proof.
No production guard is removed merely because its equality input is unreachable today.

## Verification

Freeze mutations.xml before subsequent runs. Compare class, method, descriptor, mutator, indices
and blocks, then statuses. Preserve all 787 identities and all 733 prior kills. Resource errors
never earn kills. Scoped R6 applies; R5/R7 skip and need the separate default full gate. Compiler,
Parser, default and historical populations stay separate, without a whole-checker closure claim.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 test \
  --tests dev.samhb.interleave.format.SourceLoweringContractTest
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

## Results

All 21 focused cases and integrated **1,212 tests** pass, zero failures/errors/skips. Build and
strict Javadoc pass. Final compiler: **733/787 KILLED, 32 SURVIVED, 22 NO_COVERAGE**, zero
resource/run errors. All 787 identities/statuses and all 733 prior kills are unchanged. The five
selected boundary mutants remain SURVIVED with the scoped proofs above. Remaining 54 raw
residuals comprise 18 scoped dispositions (13 prior plus these five) and 36 untriaged entries.
No kill increase or equivalence override is claimed. Scoped R6 passes; R5/R7 skip as designed.

CodeRabbit CLI 0.8.1 completed the four-file review with zero findings. All 18 normalizer cases,
relative links, credential-pattern and diff checks pass. The separate default full mutation gate
passes **281/291 KILLED, 10 SURVIVED**, zero uncovered/resource/run errors. All 291 identities/
statuses match Spec 63; full R5/R6/R7 pass. Browser checks are unnecessary for unchanged application/rendering; remote CI
is unverified.
