<!-- Fresh explorer census and bounded revalidation of inherited residual dispositions. -->
# 80 — Explorer residual census and disposition reconciliation

## TL;DR

Complete bundle C / V2 from the approved [follow-up inventory](../../plans/post-task10-follow-ups.md)
after bundle B merged in PR #121 at `b51fcf7`. A fresh run at that merge reports **496/530 KILLED,
16 SURVIVED and 18 NO_COVERAGE** across the three outer explorer classes. These are raw outcomes;
a disposition does not turn a survivor or uncovered mutation into a kill.

Specs 30–39 supply bounded arguments for these residual families. Recheck those arguments against
current control flow and reconcile them here. No new production defect or missing supported-path
regression was established, so this bundle changes documentation only. It adds no artificial
stateful predicate, malformed instruction, private-path fixture or redundant test to force a score.
This closes the initial census/triage task, not a universal equivalence or mutation-completeness proof.

## Scope and acceptance

1. Measure current main before changes, preserving operators, target tests and gate configuration.
2. Account for every current residual, its historical lineage, contract limits, owner and reopening
   condition. Keep raw outcomes visible. A bounded argument cannot close behavior outside its scope.
3. Preserve all existing runtime behavior, tests, targets, exclusions, floors and CI parallelism.
4. Add a regression only for a demonstrated supported-path gap. Independently confirmed defects
   would receive separately scoped fixes; none was identified in this triage.
5. Keep optional boundary expansions, parked work and roadmap #11/#12 inactive. Task #10 stays complete.

## Reproduction and population lineage

Run from merged main `b51fcf7`, with no source/test/build edits:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc pitest \
  '-PpitestTargetOverride=dev.samhb.interleave.search.DfsExplorer,dev.samhb.interleave.por.StaticPorExplorer,dev.samhb.interleave.dpor.DporExplorer'
```

| Class | Historical Spec 77 K / total | Current K / total | SURVIVED | NO_COVERAGE |
|---|---:|---:|---:|---:|
| DporExplorer | 283 / 307 | 285 / 309 | 12 | 12 |
| StaticPorExplorer | 125 / 134 | 125 / 134 | 3 | 6 |
| DfsExplorer | 86 / 87 | 86 / 87 | 1 | 0 |
| **Total** | **494 / 528** | **496 / 530** | **16** | **18** |

The historical XML is `/private/tmp/interleave-symmetry-composition-scoped-first.xml`; its three
outer explorer classes form the 528-identity subset above. The new frozen XML is
`/private/tmp/interleave-explorer-triage-baseline.xml`, with command output at
`/private/tmp/interleave-explorer-triage-baseline.log`. These are local evidence artifacts, not
portable repository dependencies. The command reproduces a census; this document retains its
counts, residual identities and reasoning without requiring those temporary files.

Compare class, method descriptor, full mutator, instruction indices, blocks and mutation description.
Within `DporExplorer.dporDfs`, PR #119's final guard avoids `isVisited` for a checked symmetry store;
its mapped sleep coverage decides revisits. Relative to the historical XML, that call moves from
index/block 5/0 to 8/1. Every later inherited site moves by +11 instructions and +4 blocks. Other
methods/classes retain their identities. Applying that translation matches **all 528 historical
identities with identical descriptions and statuses**, including every old kill. The only extra
identities are killed `NegateConditionalsMutator` sites at `dporDfs` 5/0 and 9/2 in the new guard.
There are **zero new kills of inherited survivors**, and no inherited status regressions.

This is a three-class scoped result, not the default state/CBS aggregate. Scoped R6 passes with
zero timeout, memory, run-error or other non-assertion detections. R5/R7 are intentionally not
applied by the scoped task. Its warning about an unrelated stale `CanonicalEncoder` reason does
not adjudicate these explorer mutations and was not patched in this documentation bundle.

## Current residual identities

Indices/blocks below are bytecode identities at `b51fcf7`, not source-line references. Method names
identify the unique overload in their class. Full JVM descriptors are those of these unchanged
source signatures in [DporExplorer](../../../src/main/java/dev/samhb/interleave/dpor/DporExplorer.java),
[StaticPorExplorer](../../../src/main/java/dev/samhb/interleave/por/StaticPorExplorer.java) and
[DfsExplorer](../../../src/main/java/dev/samhb/interleave/search/DfsExplorer.java); the frozen XML
retains them explicitly. Mutator names expand under `org.pitest.mutationtest.engine.gregor.mutators`:
**N** = `NegateConditionalsMutator`, **C** = `NonVoidMethodCallMutator`, **V** = `VoidMethodCallMutator`,
**F** = `returns.BooleanFalseReturnValsMutator`. **S** = SURVIVED; **NC** = NO_COVERAGE.

| Class | Method | Index / block | Mutator / affected operation | Raw | Argument |
|---|---|---:|---|---|---|
| DporExplorer | `dfsDfs` | 141 / 31 | N: nested invariant null guard | S | D |
| DporExplorer | `dfsDfs` | 171 / 39 | N: negated conditional | NC | D |
| DporExplorer | `dfsDfs` | 153 / 35 | C: java/util/List::copyOf | NC | D |
| DporExplorer | `dfsDfs` | 155 / 36 | C: java/util/List::copyOf | NC | D |
| DporExplorer | `dfsDfs` | 160 / 37 | C: dev/samhb/interleave/search/TracePrefix::trace | NC | D |
| DporExplorer | `dfsDfs` | 166 / 38 | C: java/util/List::add | NC | D |
| DporExplorer | `dfsDfs` | 246 / 59 | C: Integer.intValue (null-check lookup) | S | C |
| DporExplorer | `dfsDfs` | 176 / 40 | V: dev/samhb/interleave/search/StateVisitor::onTraceCreated | NC | D |
| DporExplorer | `dporDfs` | 340 / 91 | N: negated conditional | NC | D |
| DporExplorer | `dporDfs` | 602 / 153 | N: negated conditional | S | H |
| DporExplorer | `dporDfs` | 31 / 8 | C: List.isEmpty (enabled work) | S | F |
| DporExplorer | `dporDfs` | 159 / 48 | C: java/util/List::removeIf | S | E |
| DporExplorer | `dporDfs` | 322 / 87 | C: java/util/List::copyOf | NC | D |
| DporExplorer | `dporDfs` | 324 / 88 | C: java/util/List::copyOf | NC | D |
| DporExplorer | `dporDfs` | 329 / 89 | C: dev/samhb/interleave/search/TracePrefix::trace | NC | D |
| DporExplorer | `dporDfs` | 335 / 90 | C: java/util/List::add | NC | D |
| DporExplorer | `dporDfs` | 596 / 152 | C: java/lang/Integer::intValue | S | H |
| DporExplorer | `dporDfs` | 719 / 183 | C: hasEnableDisableInterference | S | G |
| DporExplorer | `dporDfs` | 882 / 222 | C: Integer.intValue (null-check lookup) | S | C |
| DporExplorer | `dporDfs` | 345 / 92 | V: dev/samhb/interleave/search/StateVisitor::onTraceCreated | NC | D |
| DporExplorer | `dporDfs` | 610 / 154 | V: dev/samhb/interleave/dpor/HappensBefore::record | S | H |
| DporExplorer | `explore` | 78 / 14 | C: Optional.empty | S | A |
| DporExplorer | `lambda$dporDfs$2` | 6 / 1 | C: java/util/Set::containsAll | S | E |
| DporExplorer | `lambda$dporDfs$2` | 7 / 2 | F: replaced boolean return with false for dev/samhb/interleave/dpor/DporExplorer::lambda$dporDfs$2 | S | E |
| StaticPorExplorer | `explore` | 67 / 12 | C: Optional.empty | S | A |
| StaticPorExplorer | `porDfs` | 490 / 119 | N: negated conditional | NC | D |
| StaticPorExplorer | `porDfs` | 265 / 69 | C: Integer.intValue (null-check lookup) | S | C |
| StaticPorExplorer | `porDfs` | 455 / 109 | C: Configuration.allTerminated | S | B |
| StaticPorExplorer | `porDfs` | 472 / 115 | C: java/util/List::copyOf | NC | D |
| StaticPorExplorer | `porDfs` | 474 / 116 | C: java/util/List::copyOf | NC | D |
| StaticPorExplorer | `porDfs` | 479 / 117 | C: dev/samhb/interleave/search/TracePrefix::trace | NC | D |
| StaticPorExplorer | `porDfs` | 485 / 118 | C: java/util/List::add | NC | D |
| StaticPorExplorer | `porDfs` | 495 / 120 | V: dev/samhb/interleave/search/StateVisitor::onTraceCreated | NC | D |
| DfsExplorer | `dfs` | 334 / 72 | C: Configuration.allTerminated | S | B |

The grouped counts below independently reconcile to **16 S + 18 NC = 34**. Every table entry maps
to exactly one group; no result is hidden by the grouping.

## Revalidated arguments, limits and ownership

Ownership denotes the maintenance contract that must reopen an argument when its assumptions
change. It does not schedule an optional feature. Normal resource availability is assumed throughout;
none of these arguments promises identical allocation, execution work, memory use or performance.

| Group / count | Current reasoning and prior evidence | Owner / next investigation trigger |
|---|---|---|
| **A — 2 S** | In each `explore`, `Optional.empty()` initializes observations only when `invariant == null`. Subsequent source fallback checks short-circuit on non-null invariant, and the null-property dispatch never dereferences that local. Replacing only this call's result with null cannot change traversal/evidence. Non-null invariant callbacks still must return an actual Optional. [Spec 35](35-dpor-property-contracts.md) supplies the DPOR argument; the current static-POR dispatch has the same unused-local structure. | Property-dispatch maintainer. Reopen if any null-property branch begins reading the Optional; add a public null-property dispatch regression for that changed use. |
| **B — 2 S** | `Configuration.allTerminated()` reads a final cached boolean. The earlier true branch already returned from this frame. Removing the bottom read substitutes false, exactly its remaining value. Successor configurations cannot mutate that cached fact. [Spec 30](30-dfs-terminal-contracts.md), [Spec 31](31-static-por-evidence-contracts.md). | DFS/static-POR terminal-contract maintainer. Reopen if completion ceases to return early or the flag becomes mutable; distinguish completion and deadlock through returned traces and callbacks. |
| **C — 3 S** | Each altered PC unboxing selects a local Step used solely for a null check. Enabled owners have valid, nonempty immutable `ModelThread` lists with no null entries; PC zero also yields a non-null Step. Actual preparation/execution independently retrieves the real PC. This does not apply to the main DPOR loop's PC, which drives reduction. [Spec 31](31-static-por-evidence-contracts.md), [Spec 36](36-dpor-program-counter-contracts.md). | Transition/explorer maintainer. Reopen if that local Step starts executing or affecting evidence/reduction, or the valid-model list contract changes; use a nonzero-PC public traversal oracle. |
| **D — 1 S + 18 NC** | The six uncovered mutations in each of static POR, DPOR exhaustive fallback and DPOR reduced traversal lie in a second invariant-failure body at deadlock. The earlier false result already emitted a violation and returned. No transition or observer runs between the successful first check and second check on the same inputs. A deterministic pure predicate therefore cannot enter that body. The exhaustive fallback's nested null-guard survivor cannot change that fact; `dfsDfs` also receives only a non-null UNKNOWN property. [Spec 31](31-static-por-evidence-contracts.md), [Spec 35](35-dpor-property-contracts.md). | Property/terminal-evidence maintainer. Reopen if intervening state/callback work is introduced or the purity contract changes; assert first-check violation versus genuine deadlock through public evidence. Stateful/history-sensitive predicates, mutation by observers and concurrent/reflection changes are outside this argument. |
| **E — 3 S** | Coverage accepts T only if no stored S satisfies S⊆T. Compaction removes only P with new R⊆P. Retaining such P cannot alter future coverage: P⊆T implies R⊆T. The same accepted arrivals follow inductively whether redundant supersets remain. All three mutations retain supersets. Compiler instruction identity comparisons are total/stable; no model callback runs. [Spec 37](37-dpor-coverage-compaction.md). | Source-DPOR coverage maintainer. Reopen if equality can throw/mutate or the coverage relation changes. An always-true removal predicate is different: it drops incomparable contexts and is already killed by the 21-visit regression. |
| **F — 1 S** | On compiler-produced source traversal, incoming sleepers identify enabled instructions at their owners' current PCs. Initially empty; an executed action is awake, and only its PC advances. Independent filtering/new entries preserve other owners' PCs and enabledness through complete guard/error footprints. Resets only remove entries. Thus no enabled work implies no sleepers. Removing `isEmpty` changes normalization only there, clearing an already-empty set. [Spec 37](37-dpor-coverage-compaction.md). | Source-DPOR normalization maintainer. Reopen for a broader producer/callback protocol. First derive an actual public traversal that reaches no enabled work with a nonempty incoming sleep set; arbitrary store-level stale contexts alone do not establish such a traversal. |
| **G — 1 S** | The probe runs after a successful ADVANCED chosen action. For independent compiler instructions, complete footprints preserve the other's enabledness, so the probe returns false already. For dependent pairs, `independent` is false, so the probe Boolean cannot enable sleeping. Skipping it also removes no modeled exception: it repeats the already-successful immutable instruction from the unchanged parent on a faithful scratch copy, with enabledness derived from the same inputs. [Spec 38](38-dpor-visibility-contracts.md) gives the full exception argument. | Compiler/reduction-contract maintainer. Reopen for custom SourceStep behavior, incomplete footprints or unfaithful copies; independently derive a public guard/error/evidence counterexample before adding a regression. Purity alone does not imply absence of exceptions. |
| **H — 3 S** | The explorer copies/records the private HappensBefore graph but never queries it, exposes it in results/visitors, or uses it for scheduling, keys or evidence. Integer map keys and Step-reference values invoke no modeled code. Different target/self-edge contents or absent records cannot affect an output that never reads them. Public HappensBefore helper tests remain independent. [Spec 39](39-dpor-happens-before-contracts.md). | DPOR/HappensBefore maintainer. Any future explorer graph reader reopens all three; derive scheduling/evidence oracles for the new consumer. No bookkeeping cleanup/refactor is part of this bundle. |
| **Total** | **16 S + 18 NC, all raw outcomes retained.** | No new supported-path defect established. |

### What remains unproven

These are scoped arguments, not a claim that every public Java recomposition/callback is equivalent.
In particular F/G retain the compiler-produced source protocol, complete footprints and faithful-copy
assumptions; custom SourceSteps do not inherit them. E also excludes custom side-effecting/throwing
equality. Broader inputs remain **unproven outside these dispositions**, owned by the corresponding
row above. Their next experiments are explicit, but they do not activate optional support work.

The store-level stale-action cases added in [Spec 79](79-symmetry-comparator-context-assurance.md)
characterize valid `sleepCovered` inputs. They do not invalidate F's narrower traversal invariant:
they do not establish that this DPOR generates those incoming contexts. Likewise a successful replay
or a finite passing test alone is not a general proof. The existing meaningful regression oracles
in Specs 30–39 remain the coverage evidence; this triage adds no duplicate cases.

## Verification and publication

Fresh merged-main `build javadoc pitest` succeeds: **1,652 tests, zero failures/errors/skips**,
strict Javadoc, and the three-class census above. Source, tests and build configuration are unchanged
through this documentation bundle, so a second identical PIT run adds no new evidence. The unchanged
default gate and browser suite are not rerun: no default-target code, gate accounting, report schema,
producer or presentation changes. Historical default/browser scores are not new C measurements.

Documentation validation checks residual identities/counts against the frozen XML, historical lineage,
relative file/anchor links and whitespace; all pass. CodeRabbit CLI 0.8.1 completed an actual
three-file review with zero findings (`review_completed`); the agent-setup review record is saved.
Only final review-status prose was added afterward.
Remote CI remains unverified; publish a draft PR and stop for Sam's feedback/merge update.
