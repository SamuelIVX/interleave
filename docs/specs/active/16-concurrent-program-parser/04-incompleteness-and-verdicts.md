# 16.04 — Incompleteness, Verdicts and Stop Reasons

## TL;DR

A reached loop bound is evidence of unexplored behavior, never a successful completion or a bug.
Keep committed-prefix witnesses and distinct reasons throughout library results, reports and UI.
[02](02-control-flow-and-atomic-execution.md) owns transition rollback;
[06](06-traces-replay-and-minimization.md) owns trace representation and replay.

## Reasons and witnesses

Retain existing TraceOutcome values and result buckets. A loop cutoff records an INCOMPLETE trace
of committed choices/outcomes, with optional additive `cutoff` evidence:

```text
cutoff = {
  reason: LOOP_LIMIT | PREEMPTION_BOUND,
  threadId: integer,
  instructionIndex: integer,
  limit: integer,
  iterations?: integer,
  source?: SourceLocation,
  expansionPath?: [{sourceOffset: integer, iteration: integer}]
}
```

`iterations` is required for LOOP_LIMIT and equals the exhausted allowance. The location identifies
the specific source while, including one nested inside an atomic block. `instructionIndex` identifies
the outer attempted scheduled instruction. PREEMPTION_BOUND identifies the blocked scheduling
choice and K; its source is optional. Existing CBS factories must tag that reason explicitly.

The attempted cutoff action is separate from committed schedule arrays: it changes no state, adds
no successful step outcome and no snapshot. Prefix snapshot count remains prefix length + 1.
Record the first witness per `(reason, thread, instruction, loop source span, expansionPath, limit)`
in deterministic traversal order. Collect distinct run reasons even when witness deduplication
suppresses another prefix. Preserve separate attempted choices for cutoff validation.

Results gain optional `incompletenessReasons` (distinct LOOP_LIMIT/PREEMPTION_BOUND in enum order) and
`resourceLimitReason` (MAX_STATES or MAX_TIME). Keep `limitExceeded` and existing constructors;
legacy data without reasons remains readable as unspecified incompleteness. When both resource
thresholds are reached at one check, MAX_STATES wins, matching a defined deterministic check order.
Retain earlier loop/preemption evidence if a later resource interruption stops the run.

## Failure evidence

Source traces gain optional `failure = {kind, phase, source?, category?}`. `kind` is INVARIANT or
RUNTIME_ERROR; phase is STEP or PROPERTY (INVARIANT always uses PROPERTY). Category is required
only for RUNTIME_ERROR and is ARRAY_BOUNDS, MODULO_BY_ZERO or
OTHER_EVALUATION_ERROR. Source locations identify the actual property, guard or inner assignment.
An invariant that evaluates false gives INVARIANT; an error evaluating that predicate gives
RUNTIME_ERROR. Reuse the existing expression evaluator with a detailed source-property result;
retain the public Invariant.holds interface and legacy behavior.

A PROPERTY failure retains its actual observation-point configuration. If evaluation errors
initially, its schedule is empty; if it errors after a successful commit, retain that commit and
successful step outcome. Add no synthetic failed action or rollback of a completed action.

Attestation and minimization reproduce the classified failure. A runtime error is a valid VIOLATION
even with no invariant or with an invariant true in the rolled-back committed state. INVARIANT
evidence requires actual predicate failure at its specified observation point. Evidence describes
execution; supplied metadata cannot force a verdict. Propagation/alignment is owned by 06.

## Search aggregation

Continue other scheduled choices after cutoff; terminate only the truncated path. A false final
loop condition at the allowance exits normally. Guard false is disabledness, and a state with live
threads but no enabled choice is genuine deadlock. A resource interruption is a separate run stop.

For source models use this precedence across all strategies:

1. Any violation gives VIOLATION.
2. Otherwise any deadlock gives DEADLOCK.
3. Otherwise any loop/preemption cutoff or resource interruption gives INCOMPLETE.
4. Otherwise bitstate gives APPROXIMATE_PASS.
5. Otherwise exact-store completed exploration gives PASS.

Keep each discovered category in its result bucket even if a higher-priority verdict wins. An
empty all-terminated model has a completed empty trace. Never infer completeness from merely finding
one completed schedule. Bitstate may suppress a cutoff/bug; it can never prove source-model PASS.

Preserve historical no-cutoff JSON/Java report behavior, including their existing bitstate labels.
For every strategy/program type, a newly present INCOMPLETE trace must prevent actualVerdict from
returning PASS. Do not broaden approximate behavior changes to unrelated legacy reports.
Resource-limited legacy library results retain their existing flags/display, with additive reasons.

## Integration ownership

- Update `BenchmarkHarness.actualVerdict` as well as `cbVerdict`; updating only CBS is insufficient.
- Both harness and runner iterative-deepening paths increase K only for PREEMPTION_BOUND evidence,
  not for LOOP_LIMIT or state/time interruption. A run containing both bound reasons may deepen;
  retain the loop evidence in the final selected iteration rather than merging incompatible traces.
- `VerificationResult`, `TestResult`, `BenchmarkResult`, report JSON/Markdown and trace conversion
  preserve reasons. Legacy constructors initialize no evidence, not fabricated causes.
- `SoundnessAttestation` excludes inconclusive absence-of-bug claims while still replay-validating
  actual classified failures as above. Explain loop/resource causes rather than labeling every
  incomplete row CBS.
- The viewer shows the winning verdict plus all supplied reasons, bound, location and resource
  flag. A resource banner must not hide an already found violation or discard loop evidence.
- Expected PASS validation cannot accept an incomplete source run; report it as inconclusive,
  not a checker failure or a silently matched expectation. Preserve existing legacy expectation rules.

## Acceptance

Cover final false at exactly N versus final true; zero limits; nested per-invocation reset; atomic
cutoff after writes; successful and cutoff schedules together; cutoff alongside actual deadlock or
violation; multiple loop/preemption reasons; later state/time interruption; bitstate without exact
claims; and no false PASS through DFS/static/DPOR reporting.
Test both iterative-deepening entry points with loop-only and mixed reasons. Independently validate
the recorded prefix and reproduce LOOP_LIMIT on scratch without committing it. For PREEMPTION_BOUND,
reconstruct last-thread/enabledness/cost from prefix replay and prove the attempted enabled choice
exceeds K without executing it. Include a true invariant with atomic writes followed by a runtime
error: attestation must accept the reproduced error after rollback.
