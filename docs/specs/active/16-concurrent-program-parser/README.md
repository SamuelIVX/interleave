# Spec 16 — Bounded Concurrent Modeling Language

## TL;DR

**Status: specified; implementation not started.** Roadmap item #9 gives authors a readable `.ilv`
language for finite concurrent models. It includes explicit atomic blocks, thread-level and atomic
`if/else`, fixed repetition, bounded `while`, and all four exploration strategies. It models selected
behavior; Java project ingestion remains future item #12. State-space graph work (#8) stays deferred.

This set is a contract for future implementation, not a claim that its APIs or commands exist today.
Specification changes can land separately; each subsequent implementation PR updates its relevant
spec and status with measured evidence. Never label an unimplemented acceptance case passed.

## Baseline and intent

Inspected baseline: `51f7c5448056c1b001a7327e410dcfdfb727ee40`.

- `ProgramLoader` loads typed/declarative JSON; `DslLoader` validates declarations, expressions,
  types, limits, steps and properties. Existing `Parser` handles expressions and assignments.
- `DynamicStep` runs flat assignments with complete derived footprints. `DslInvariant` supports
  final/always observations. These are reusable semantics, not a second language to reimplement.
- `Configuration.successor` assumes PC+1; POR/DPOR proofs assume finite linear threads. Source
  control flow therefore requires the shared execution extension and proof work in this set.
- `TraceReplayer` and `ExecutionDriver` currently execute on live state; the viewer derives step
  indexes by counting thread appearances. Both assumptions need explicit source-program handling.
- `BenchmarkHarness.actualVerdict` currently ignores INCOMPLETE, and CBS deepening reacts to any
  INCOMPLETE trace. Neither behavior is suitable for loop cutoffs without the changes in 04.
- A focused baseline ran 172 format/DSL/registry/CLI tests with zero failures or skips. This is
  existing-code evidence, not verification of the proposed language.

## Documents and ownership

| Document | Owns |
|---|---|
| [01 — Language](01-language-and-semantics.md) | Grammar and observable meaning |
| [02 — Execution](02-control-flow-and-atomic-execution.md) | Forward control flow, transition boundary, atomic execution |
| [03 — Frontend](03-parser-validation-and-lowering.md) | Parsing, validation, lowering, source/resource bounds |
| [04 — Completeness](04-incompleteness-and-verdicts.md) | Cutoff evidence, verdicts, reasons and result compatibility |
| [05 — Reduction](05-por-and-dpor-soundness.md) | Footprints, proof obligations, DFS/POR/DPOR/CBS contracts |
| [06 — Traces](06-traces-replay-and-minimization.md) | Replay, snapshots, minimization and viewer metadata |
| [07 — Integration](07-cli-library-and-acceptance.md) | Public loader/CLI, examples, integrated acceptance |

Read 01, then 02–04 together before implementation. Implement 02's compatibility-preserving execution
foundation first; then 03's compiler and 04's complete result propagation as internal functionality.
Implement 05 and 06 before exposing the source CLI/library in 07. Every slice ships its local tests;
the source language is not released with incomplete reporting or unverified reduction support.

## Fixed decisions

- A model's declarations and thread definitions are immutable and reusable across runs.
- Thread-level condition checks are scheduled actions. Atomic interiors are private computations;
  only the whole atomic block is visible to other threads and global properties.
- Bounded loops are expanded into forward-only control flow; a final true condition causes a
  cutoff. Expanded instruction position records remaining allowance without hidden mutable counters.
- Both state-invariant failures and reachable cutoffs must survive exact-store reductions.
- JSON/Java APIs retain their existing behavior. Source programs share the checker, expression
  evaluator, state representation and footprint rules, rather than using a source-only explorer.
- Implementation adds no dependency by default. Any proposed parser dependency needs demonstrated
  necessity and an explicit change to this design.

## Acceptance and review

The set is implementation-ready only when grammar, limits, execution cases, trace evidence and proof
obligations agree across documents. Each acceptance section below is a required future test ledger.
Proof obligations in 05 must be discharged in the implementation review, not treated as proven by
this proposal. Ordinary local review and fresh-context reader review precede CodeRabbit CLI review.
Follow the selective CodeRabbit gate; a skipped/rate-limited run is not completed review evidence.

Spec-only validation: check relative links, grammar/example agreement, section ownership, limit
arithmetic, absence of unresolved placeholders, and `git diff --check`. See 07 for implementation
checks. No new Java tests or production changes belong in the specification-authoring PR.

## Out of scope

Unbounded control flow, source-level functions, dynamic thread creation, Java/C compatibility,
statement assertions, explicit jumps, nested atomic blocks, weak memory, new liveness guarantees,
and graph rendering. Source files execute no host code or I/O. A PASS describes the finite model
and selected store guarantees, not the original application.
