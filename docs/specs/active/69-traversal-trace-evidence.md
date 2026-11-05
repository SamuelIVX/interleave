# Spec: Build source trace evidence from traversal

## Objective and scope

Implement the measured 9-V3 optimization selected by Spec 68. Carry immutable source identities and
actual step-failure evidence along real traversal edges in DFS, static POR, DPOR (reduced,
all-asleep and unknown-property exhaustive paths) and CBS. Freeze existing TraceEvidence at emission
without deterministic prefix re-execution. Keep public Trace.create as the replay-based factory for
external callers and differential tests. No schema, scheduling, visited-store, observation, sleep
coverage, cutoff selection, notification order or reduction/pruning changes.

Affected files: a shared TracePrefix implementation; the four explorers and their traversal calls;
source-trace compatibility tests; this spec, measured before/after report and future-work table.
The default mutation census must be freshly measured before and after: legitimate CBS source edits
can change its generated identities. Record every introduced/removed identity and its source cause
before updating any denominator assertion; preserve the existing scope/operators and assertion-only
kill accounting. Do not lower the floor, hide mutants or credit resource errors.

## Evidence contract

A persistent prefix contains one immutable ExecutedStep per actual source attempt plus the actual
terminal step failure. Branches share ancestors and cannot mutate sibling evidence. Legacy paths
reuse the empty prefix and retain their existing factory behavior. Each public exploration starts
at the empty prefix. Metadata is captured from the parent PC of the chosen source action, including
branch jumps and expanded repeat/while identities. Independence probes never append. Successful
edges and failed attempts append once; loop/preemption cutoffs remain separate omitted actions.

At completion/deadlock/property failure, freeze the current prefix. At a failed action, freeze its
extended prefix using the actual StepTransition failure; do not commit scratch state. Diagnostic
invariant failures use the actual terminal configuration. CBS snapshots source preemption evidence
at the prune site as before; deferred legacy evidence retains its original path/factory. Source
factory compatibility assumes compiler-produced deterministic models and pure properties, as in
Spec 16; arbitrary side effects in custom callbacks are not an equivalence promise.

Immutable linked metadata adds one small node per real source traversal edge, shared across sibling
prefixes; it does not retain configuration snapshots. Final traces keep the same immutable lists,
source digest, terminal evidence and JSON/replay representation. Capturing metadata on nonterminal
branches can add allocations where few traces are emitted; the independent/context profiling
fixtures must expose that tradeoff. No universal improvement claim.

## Required validation

Compare every emitted trace field (schedule, outcomes, verdict and complete evidence) to the
unchanged replay-based Trace.create oracle across all four explorers and DPOR fallback. Replay
with the invariant-aware API. Include empty success/failure, branching/nested expansion, runtime step failure (ASSERTION_FAILED outcome), invariant failure, disabled deadlock, loop cutoff, CBS preemption cutoff, sibling
paths and repeated explorer use. A work-count assertion must demonstrate that freezing captured
prefixes does not execute source actions again; use actual public source execution plus a test
SourceStep counting wrapper rather than private AST/cursor fabrication.

Run full build/tests/strict Javadoc and default mutation ratchet; compare fresh identities and
statuses, with raw non-kills retained. Measure three sequential fresh JVM forks before and after
with the unchanged Spec 68 profileSearch runner and heap/workload settings. Validate all terminal
oracles/replay/counters. Report both material trace-heavy gains and independent/context regressions,
plus provenance/class fingerprints. Attribute no precise method time from sampled stacks. The old
runner's traceReplay family names only Trace.create; after optimization it does not measure the
new evidence-freezing path. End-to-end plain trials remain comparable.

Initial external CodeRabbit review is required; minor follow-up corrections use local verification
without another CLI invocation. Publish one coherent draft PR and stop. #10–12 and DPOR
representation/pruning changes remain separate later work.

## Measured results and current validation

Fresh main baseline: clean build, strict Javadoc, 1,303 tests and default PIT 281/291 K + 10 S,
zero uncovered/resource/run errors. The new work-count regression failed on all five baseline
routes: four executions for two actual actions. Final build/Javadoc and all 1,375 tests pass,
including 72 new cases (13 scenarios crossed with five routes, five work-count cases, CBS snapshot/
reuse and cutoff-append rejection). Complete serialized evidence matches the unchanged factory.

[Three-fork before/after measurements](../../measurements/69-traversal-trace-evidence.md) preserve
all visited counts and terminal observations. Trace-heavy medians reduce wall time 22.37–29.23%
and allocated bytes 19.97–30.38%. Trace-light DFS adds 3.53–4.35% time and 5.34% allocation;
this is the measured eager-capture tradeoff, not a universal speedup. No JFR old-factory sample
fraction is claimed to measure the new path. Final default/scoped mutation and external review are recorded below.

## Default mutation population and gate accounting

Fresh baseline 281/291 K + 10 S becomes 283/293 K + 10 S, zero NO_COVERAGE or resource/run errors.
CBS changes from 123/123 to 125/125. The 168 state-scope identities/statuses are exactly unchanged:
BitstateStore 95/97, HashingStateStore 45/52, CanonicalEncoder 18/19. Their ten raw survivors retain
the existing scoped dispositions; no survivor is renamed as a kill or excluded. Scopes/operators/
test filter/runtime/PIT heap/time settings are unchanged. TracePrefix is measured separately.

Compare complete identities using class, method, descriptor, mutator, serialized indexes and blocks.
There are 204 exact shared identities, all statuses unchanged; 87 old identities are replaced by
89 new ones, all CBS and all killed. The added dfs parameter changes its descriptor and bytecode
positions; the public explore body's new root call shifts later indexes. The complete local delta
is `/private/tmp/interleave-traversal-mutation-delta.json`, frozen alongside baseline/default XML
snapshots with the same prefix. The following method/operator census accounts for every replacement:

| Method / mutator | Removed killed identities | Introduced killed identities | Cause |
|---|---:|---:|---|
| dfs / NonVoidMethodCall | 51 | 52 | Existing calls relocate; new TracePrefix.append call |
| dfs / NegateConditionals | 21 | 21 | Descriptor/index relocation; unchanged conditions |
| dfs / VoidMethodCall | 9 | 9 | Descriptor/index relocation; unchanged emission/recursion calls |
| dfs / Math | 2 | 2 | Descriptor/index relocation; unchanged preemption arithmetic |
| dfs / ConditionalsBoundary | 1 | 1 | Descriptor/index relocation; unchanged bound comparison |
| explore / NonVoidMethodCall | 0 | 1 | New TracePrefix.empty root call |
| explore / VoidMethodCall | 2 | 2 | New root call shifts recursive-call indexes |
| explore / NullReturnVals | 1 | 1 | New root call shifts return indexes |
| Total | 87 | 89 | Net two new killed call mutations, not two old survivors killed |

Update EXPECTED_TOTAL_MUTANTS to the measured 293 and align all four advisory class figures with
this fresh census. The 94% floor stays unchanged. Independent integer ceiling and decimal arithmetic
agree: 94*293=27542, so 276 kills clear the gate; 275 fail. Current 283 kills retain seven kills of
headroom, exactly matching baseline 281-ceil(94*291/100)=7. R5, R6 and R7 remain enforced; no
percentage rounding, resource-error credit or suppression enters the gate. The denominator failure
before this measured update was intentional protection against silently accepting population drift.

## New-helper scope and final review

TracePrefix has 30/31 KILLED, one SURVIVED, zero NO_COVERAGE/resource/run errors. Scoped R6 passes;
R5/R7 skip and are established by the separate default run. Do not add 31 to the default denominator.
The single raw survivor is NonVoidMethodCallMutator in TracePrefix.trace, descriptor
`(Program,List,List,TraceOutcome,Invariant,Configuration,CutoffEvidence)Trace`, index 20/block 4:
removing List.size used solely as the initial ArrayList capacity. Returning zero starts an empty
capacity rather than the known prefix capacity; the same captured nodes are added, reversed and
frozen in the same order. Under the explorer's ordinary finite lists and successful allocation,
trace values and terminal evidence are unchanged. Allocation growth/cost is not equivalent and no
arbitrary callback-list or resource-exhaustion equivalence is claimed. Keep the mutation/raw status;
no exclusion or forced kill is introduced for an allocation hint.

All 1,375 tests/build/strict Javadoc pass. Default full R5/R6/R7 pass after the measured denominator
update, without repeating PIT: 283/293 K + 10 S. New-helper scope passes R6 at 30/31. Eighteen
visualizer normalization checks pass; browser rendering and unrelated compiler/Parser scoped PIT
were not rerun. Complete before/after terminal observations, source evidence and search counters
match. Latest build mutations.xml is the scoped helper report; frozen default/baseline/helper XML
are `/private/tmp/interleave-traversal-{default,baseline,prefix}.xml`.

CodeRabbit CLI 0.8.1 actually completed all ten initial files with one minor finding: refresh the
mutation README's current census. Independently verified and patched the four current figures and
provenance while retaining its dated historical figures. Markdown/table/link/diff checks pass;
no second CLI review for this minor documentation follow-up. Evidence is
`/private/tmp/interleave-traversal-review.ndjson`. Remote CI is not watched after publication.

## PR feedback: failed-action execution count

The trivial PR #111 comment adds five cases through the existing public SourceStep counting wrapper:
DFS, POR, DPOR, unknown-property DPOR and CBS each execute one runtime-failing atomic action exactly
once. Assert the counter immediately after traversal, before compatibility reconstruction/replay,
then validate the failure classification and full evidence. The successful-action test is unchanged.
Final integrated build/Javadoc and 1,380 tests pass (77 new cases in this bundle). Production code,
measurement fingerprints and mutation configuration are unchanged; PIT/profiling/CodeRabbit CLI
were not rerun for this focused test-only follow-up.
