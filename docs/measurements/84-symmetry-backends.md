<!-- Certified-backend population lineage and visible inherited runner residuals. -->
# 84 — Certified symmetry backend evidence

## TL;DR

Fresh merged main `a0b45b9` passes build, strict Javadoc and 1,750 tests. Its outer-class baseline
is store 89/89 K and runner 96/139 K + 35 S + 8 NC. The changed nested Builder was also measured
at that exact revision in a separate clean baseline worktree: 23/26 K + 3 S. Do not substitute
the outer runner population for its nested builder's validation logic.

Final integrated build/strict Javadoc passes **1,786 tests**: 28 new test invocations and eight
additional backend invocations of existing context/alias regressions. Final affected outcomes:

| Class | Baseline K / total | Final K / total | Final S | Final NC |
|---|---:|---:|---:|---:|
| SymmetryStateStore | 89 / 89 | 96 / 96 | 0 | 0 |
| SymmetryBackend | New | 4 / 4 | 0 | 0 |
| InterleaveRunner | 96 / 139 | 106 / 139 | 29 | 4 |
| InterleaveRunner.Builder | 23 / 26 | 30 / 30 | 0 | 0 |

Scoped R6 passes. Scoped R5/R7 are not evaluated; the separate default gate is required.
No timeout, memory, nonviable or run-error outcome is counted as a kill. No operator, target-test,
floor, resource-bound or CI change. Default **436/451 K + 15 S** passes R5/R6/R7, with all 451
exact identities/statuses matching the fresh baseline. All 33 raw residuals remain below; no new backend mutation survives.
See [Spec 84](../specs/active/84-certified-symmetry-backends.md) for exactness and ownership contracts.

## Lineage rather than population arithmetic

The store retains all 61 exact identities/statuses outside its constructor, forRun and freshCopy;
all are K. Constructor changes 23 to 26, old factory 3 to 2 plus new overload 4, backend accessor 1,
and freshCopy remains 2: all K. Rewritten constructor descriptors and changed call indices are not
claimed to be unchanged identities.

The runner retains 133 exact identities outside newRunStore; 120 keep their statuses. Nine S and
one NC become K, and three NC become S. None of the matched K regresses. The six newRunStore sites
remain six K with rewritten indices/call shape. Thus snapshot/output and iterative-failure assertions
close ten inherited detections; four newly covered sites are not all new kills. The complete runner
population stays 139. These assertions do not close every ordinary-runner contract.

Builder retains all 16 exact identities outside build: 15 statuses match and the explicit-zero
maxStates boundary changes S to K. Build changes 10 to 12 sites; its two inherited String.valueOf
diagnostic mutations are now K under literal strategy-message assertions. All 12 build sites and
both new backend-setter sites are K. The enum contributes four new K. Net scope grows 254 to 269;
that growth is not credited as inherited kills. Frozen XML retains descriptors and bytecode sites.

## Residual limits and ownership

These residuals predate B6; none is newly classified universally equivalent. B6 supports trusted
finite source models with certified exact storage, not a general runner-hardening effort.

- Runner conversion: ordinary non-source snapshot fallbacks and their gate mutations are not fully
  constrained here. Source replay/snapshots are asserted for complete, failure, deadlock and cutoff
  results on both backends. Do not call the ordinary fallback sites unreachable or globally equivalent.
  Owner: runner/report maintainer; reopen with ordinary-model snapshot contracts, including failures.
- Cutoff-history merge lambdas: this feature's resource probes stop early and do not establish every
  equality/deduplication branch for prior cutoffs plus a later resource interruption. Owner: runner
  evidence maintainer; next investigation is an independent mixed-prefix/partial-result oracle.
- Runtime GC, memory and elapsed-time sites affect telemetry/work, potentially host-resource behavior.
  Unchanged modeled verdicts do not prove identical heap deltas or elapsed milliseconds. Owner: runner
  measurements maintainer; constrain deterministic clock outputs where feasible without mocking
  model logic, and do not fabricate a stable JVM heap oracle.
- Iterative search: the remaining isFailure lambda and final-return sites have incomplete terminal/
  exhaustion discrimination. The approximate-flag OR mutation concerns ordinary bitstate runs,
  outside exact symmetry. Owner: runner maintainer; use ordinary iterative terminal/approximation
  cases before claiming coverage. No B9 approximate-symmetry capability is activated.

The current production paths keep their safeguards. This ledger identifies bounded coverage limits;
it neither reports these mutations as current production bugs nor silently promises to fix all of
ordinary runner behavior in B6.

## Consumer and review checks

Artifact-level public library smoke checks execute both backends under all four strategies: eight
EXACT symmetry outputs with five visits. The unchanged CLI default produces four PASS/five-visit
rows and soundness true. All twelve fresh outputs normalize with the actual viewer function;
22 compatibility verdict cases and four symmetry assertions also pass. No DOM rerun because schema,
normalizer and renderer are unchanged. No performance or memory gain is claimed.

CodeRabbit 0.8.1 completed all ten submitted files with seven trivial findings. Valid fixture-order,
concurrent-start and documentation/table clarifications were patched and locally verified; final
measurement text replaces pending status with actual results. No CLI rerun for trivial findings.
Local review also added returned-snapshot assertions and independent literal 2,112 hash-collision
oracles. Concurrency tests gate both tasks at a shared start; independent allocation/copy tests prove
membership ownership without exposing live store state or requiring unrelated result-object identities.

## All 33 raw residuals

K = KILLED, S = SURVIVED, NC = NO_COVERAGE. Dispositions do not change raw outcomes.
Class prefix is dev.samhb.interleave; mutator prefix is org.pitest.mutationtest.engine.gregor.mutators.
Indices/blocks are revision/JDK-scoped, not source-line references. The table's conversion method
is the DfsResult/Program overload; run is the Program/LongSupplier overload. Full descriptors stay
in `/private/tmp/interleave-symmetry-backend-final-scoped.xml`. Baselines use the same prefix plus
`baseline.xml` and `builder-baseline.xml`; default comparisons use `default-baseline.xml` and
`verified-default.xml`. Temporary XML may expire; this ledger retains the measured identities/statuses.

| Class | Method | Indices / blocks | Mutator | Raw | Operation |
|---|---|---|---|---|---|
| InterleaveRunner | `convertToTestResult` | 78 / 21 | `NegateConditionalsMutator` | S | negated conditional |
| InterleaveRunner | `convertToTestResult` | 81 / 23 | `NegateConditionalsMutator` | S | negated conditional |
| InterleaveRunner | `convertToTestResult` | 116 / 30 | `NegateConditionalsMutator` | S | negated conditional |
| InterleaveRunner | `convertToTestResult` | 119 / 32 | `NegateConditionalsMutator` | S | negated conditional |
| InterleaveRunner | `convertToTestResult` | 80 / 22 | `NonVoidMethodCallMutator` | S | core/Program::isSource |
| InterleaveRunner | `convertToTestResult` | 87 / 24 | `NonVoidMethodCallMutator` | S | search/TraceReplayer::captureSnapshots |
| InterleaveRunner | `convertToTestResult` | 118 / 31 | `NonVoidMethodCallMutator` | S | core/Program::isSource |
| InterleaveRunner | `convertToTestResult` | 125 / 33 | `NonVoidMethodCallMutator` | NC | search/TraceReplayer::captureSnapshots |
| InterleaveRunner | `convertToTestResult` | 131 / 34 | `NonVoidMethodCallMutator` | NC | TraceRecord::withStates |
| InterleaveRunner | `lambda$isFailure$0` | 8 / 2 | `NonVoidMethodCallMutator` | S | search/Trace::outcome |
| InterleaveRunner | `lambda$run$1` | 10 / 3 | `NegateConditionalsMutator` | S | negated conditional |
| InterleaveRunner | `lambda$run$1` | 9 / 2 | `NonVoidMethodCallMutator` | S | TraceRecord::evidence |
| InterleaveRunner | `lambda$run$1` | 17 / 6 | `NonVoidMethodCallMutator` | S | core/TraceEvidence::cutoff |
| InterleaveRunner | `lambda$run$1` | 21 / 7 | `NonVoidMethodCallMutator` | S | core/CutoffEvidence::equals |
| InterleaveRunner | `lambda$run$1` | 22 / 8 | `BooleanFalseReturnValsMutator` | S | replaced boolean return with false for InterleaveRunner::lambda$run$1 |
| InterleaveRunner | `run` | 20 / 5 | `MathMutator` | S | Replaced long subtraction with addition |
| InterleaveRunner | `run` | 190 / 34 | `MathMutator` | S | Replaced long subtraction with addition |
| InterleaveRunner | `run` | 204 / 35 | `MathMutator` | S | Replaced long subtraction with addition |
| InterleaveRunner | `run` | 285 / 58 | `MathMutator` | S | Replaced long subtraction with addition |
| InterleaveRunner | `run` | 299 / 59 | `MathMutator` | S | Replaced long subtraction with addition |
| InterleaveRunner | `run` | 17 / 3 | `NonVoidMethodCallMutator` | S | java/lang/Runtime::totalMemory |
| InterleaveRunner | `run` | 19 / 4 | `NonVoidMethodCallMutator` | S | java/lang/Runtime::freeMemory |
| InterleaveRunner | `run` | 172 / 28 | `NonVoidMethodCallMutator` | NC | java/lang/String::valueOf |
| InterleaveRunner | `run` | 187 / 32 | `NonVoidMethodCallMutator` | S | java/lang/Runtime::totalMemory |
| InterleaveRunner | `run` | 189 / 33 | `NonVoidMethodCallMutator` | S | java/lang/Runtime::freeMemory |
| InterleaveRunner | `run` | 205 / 35 | `NonVoidMethodCallMutator` | S | java/lang/Math::max |
| InterleaveRunner | `run` | 282 / 56 | `NonVoidMethodCallMutator` | S | java/lang/Runtime::totalMemory |
| InterleaveRunner | `run` | 284 / 57 | `NonVoidMethodCallMutator` | S | java/lang/Runtime::freeMemory |
| InterleaveRunner | `run` | 300 / 59 | `NonVoidMethodCallMutator` | S | java/lang/Math::max |
| InterleaveRunner | `run` | 13 / 2 | `VoidMethodCallMutator` | S | java/lang/Runtime::gc |
| InterleaveRunner | `runIterativeDeepening` | 21 / 3 | `ConditionalsBoundaryMutator` | S | changed conditional boundary |
| InterleaveRunner | `runIterativeDeepening` | 42 / 6 | `MathMutator` | S | Replaced bitwise OR with AND |
| InterleaveRunner | `runIterativeDeepening` | 111 / 21 | `NullReturnValsMutator` | NC | replaced return value with null for InterleaveRunner::runIterativeDeepening |
