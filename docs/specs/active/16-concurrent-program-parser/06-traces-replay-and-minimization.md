# 16.06 — Trace Identity, Replay and Minimization

## TL;DR

Source control flow invalidates the viewer's thread-occurrence-as-PC assumption. Retain actual
executed instruction identities and original source locations through every trace surface.
[04](04-incompleteness-and-verdicts.md) owns cutoff metadata and aggregate verdicts.

## Additive evidence contract

Use immutable `SourceLocation` with `file` (basename or `<source>`), `line`, `column`, `endLine`,
`endColumn`; positions use 03's convention and half-open end. A source artifact also carries
`sourceDigest`, lowercase SHA-256 of the exact UTF-8 source bytes, including comments/newlines.
It identifies diagnostic source text, not semantic equivalence or a replacement for programHash.

Optional trace `executedSteps` aligns exactly with recorded thread IDs/outcomes, including a final
instruction-failure attempt and excluding cutoff attempts:

```text
executedSteps[i] = {
  instructionIndex: integer,
  source: SourceLocation,
  label: string,
  expansionPath: [{sourceOffset: integer, iteration: integer}]
}
```

`sourceOffset` is a zero-based UTF-16 offset of the enclosing repeat/while keyword; iteration is
zero-based, ordered outermost first. A final while check uses iteration N. For internal atomic
control flow, the executed outer block has one metadata entry; an inner failure/cutoff location
belongs to separate terminal evidence, not fictitious committed inner steps.
Labels are source-derived diagnostic text and must be rendered as text, never HTML.

Preserve legacy traces that lack metadata. Reject supplied misaligned executedSteps, impossible
negative identities or malformed locations at an appropriate input boundary; do not silently shift
labels. New optional evidence is omitted when unavailable. Existing constructors, factories and
`withStates` preserve or initialize metadata consistently.

## Capture and replay

Successful source actions append their actual pre-execution instruction index and source map entry.
For a source StepTransition.Failure (failure.phase STEP), keep the attempted choice/outcome as the existing failing trace does,
record its actual instruction and failure location, and return an unchanged committed snapshot for
that terminal attempt. Do not advance to a successful successor. Source trace snapshots therefore
remain choice count + 1, with a repeated final committed snapshot on failure.

PROPERTY-phase failures use 04's observation-point rule: preserve the committed state and actual
recorded outcomes, with no synthetic failed choice or repeated snapshot. This includes runtime
errors in a property, not just false predicates. An initial property error has one initial snapshot
and zero recorded choices. Replay/attestation distinguish both phases explicitly.

A cutoff records no committed attempt in the schedule arrays; use 04's separate attempted choice.
Its snapshots end at the prefix's final committed state. A dedicated cutoff-witness validator
replays that prefix then validates the attempted choice according to its reason below.
Ordinary prefix replay does not on its own validate a terminal verdict.

Replay source traces through the shared transition executor. Validate supplied instruction metadata
against actual execution; a mismatched sourceDigest against a supplied source is a clear error, not
permission to show stale labels. Thread choices and modeled values determine branch decisions;
metadata cannot override them. Runtime-failure replay validates actual outcome and halts at failure.
Add an invariant-aware `replay(Program, Trace, Invariant)` overload for validating source
property-failure evidence; its caller must supply the property. The existing overload validates
step evidence without claiming to prove an INVARIANT terminal verdict. Attestation/minimization
use the invariant-aware path.
Legacy replay behavior remains covered separately rather than silently becoming stricter.

LOOP_LIMIT witness validation reruns the attempted action on scratch. PREEMPTION_BOUND validation
reconstructs the prefix's scheduling cost and prior enabledness, verifies the attempted choice is
enabled and would exceed K, and never executes it. Use 02's detailed driver result to halt terminal
source replay and validate 04's failure kind rather than infer it from the rolled-back state.

## Minimization

Continue rejecting INCOMPLETE minimization, including source loop cutoffs. Update CBS-specific
error text to describe bounded/incomplete evidence generally.

For ordinary failures, removing scheduled choices can change the chosen arm, executed PCs and
outcomes. Execute each candidate through the shared driver, construct fresh outcomes/metadata from
what actually ran, and retain it only when it reaches the requested real violation/deadlock with
the supplied invariant when its failure kind requires one, or reproduces a real modeled runtime
error even when that invariant is absent/true. Do not carry original outcomes, source labels or
cutoff evidence into a new schedule. Verify the accepted minimized trace through source-aware
replay before returning it.
Preserve distinct DeltaDebugger regression cases and the existing mutation-hardening guarantees.

## All producers and consumers

Propagate evidence through Trace/TraceRecord, record conversion/enrichment/copying, TestResult,
VerificationResult, BenchmarkResult, ReportWriter and direct serializers. Refresh minimized
snapshots from the accepted execution. Keep metadata optional for older producer shapes.

`sourceDigest`, `executedSteps`, `failure` and `cutoff` live on each direct source trace object.
Wrappers preserve them on nested trace entries, including ReportWriter's representative trace.
For a runtime error that representative entry carries failure evidence; for loop-only incomplete
output add an optional representative `incompleteTrace` rather than pretending it is failingTrace.
Run-level reason fields live on the enclosing result/benchmark row. All copying/enrichment paths
preserve the digest and terminal evidence. A digest is absent on legacy traces without source.

The visualizer normalizer preserves metadata for direct records and every result/report wrapper.
Prefer actual instruction identity and source label when present, retaining occurrence-counter
fallback for old linear traces. Source traces must remain readable without loading source into the
browser. Loading `.ilv` into the browser, browser compilation and a graph view are outside this set.
Display source spans/digests and cutoff reasons with safe text APIs and existing accessibility
patterns. A failure's inner source location may supplement its outer atomic-block label.

## Acceptance

Test branch-skipped instructions, unrolled copies with matching spans/different expansion paths,
all wrapper shapes, withStates/enrich preservation, wrong source digest, malformed optional data,
atomic failure/cutoff snapshots, and branching minimization that changes actual outcomes.
Include true-invariant runtime-error rollback through minimization, attestation and replay.
Also test initial property-evaluation errors and errors after a successful commit: neither invents
an ASSERTION_FAILED action, and the latter preserves its ADVANCED outcome and committed write.
Use strict JSON parsing for escaped source labels and process-produced artifacts. Browser tests
must show the correct branch line and cutoff location; old fixture labels/verdicts still pass.
