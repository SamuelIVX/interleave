# 16.02 — Forward Control Flow and Shared Execution

## TL;DR

Use immutable forward instructions and one execution boundary for search, fixed schedules and
replay. Source atomic blocks execute transactionally. Existing `Step` implementations keep their
methods and linear behavior. [01](01-language-and-semantics.md) defines observable semantics.

## Shared transition contract

Introduce an immutable `StepTransition` result with distinct variants:

- **Success:** existing successful StepOutcome, validated next PC, and isolated successor state.
- **Blocked:** no state mutation or PC change. Preserve legacy identity-successor behavior below.
- **Failure:** ASSERTION_FAILED with failing source location, no committed successor.
- **Cutoff:** attempted instruction identity plus LOOP_LIMIT evidence, no committed successor.

`TransitionExecutor` owns copying and dispatch. It receives program, configuration and selected
thread, executes once against isolated state, and returns the result. Preserve legacy `Step.execute`
and `Configuration.successor` signatures; adapt ordinary steps to their existing successor behavior.
Add the control-flow result path without changing existing enum constants or forcing legacy/custom
Steps to implement source control flow. Source compiled instructions implement the new internal
execution protocol; their immutable targets never reside in mutable per-run Step fields.
Existing Java public step execution remains available with its documented behavior.

An enabled legacy Step can return BLOCKED. Its adapter preserves the existing identity successor
and CBS last-thread/preemption bookkeeping; eliminating that successor could change legacy visit
counts. A false source outer guard is disabled before scheduling, with no attempted transition.

Add `ExecutionDriver.runDetailed(Program, Schedule, Invariant)` returning immutable `ScheduleExecution`:
final committed configuration, freshly recorded thread choices/outcomes/metadata/snapshots, terminal
failure/cutoff evidence, and kind PREFIX, COMPLETED, VIOLATION, DEADLOCK or INCOMPLETE. PREFIX means
the supplied schedule ended before a terminal event; it creates no terminal TraceOutcome value.
Other kinds can convert the recorded evidence to the corresponding Trace. A null invariant means
none. Check source properties initially and
after commits using their timing; stop at the first terminal event, ignoring remaining choices.
Disabled/invalid scheduled choices raise IllegalScheduleException, not a modeled terminal result.
The existing `run` keeps legacy behavior. For source programs it delegates without an invariant,
returns committed configuration on PREFIX/COMPLETED/DEADLOCK, and throws an unchecked
`SourceExecutionException` carrying ScheduleExecution on runtime VIOLATION or INCOMPLETE. A source
cutoff must never be returned as an apparently successful prefix. Replay/minimization use the
detailed result directly. Source failure classification is defined in 04.

Update all execution sites: DFS; static POR including prepared transitions; DPOR reduction, fallback
and alternate paths; CBS; `ExecutionDriver`; both `TraceReplayer` paths. Callers consume returned
outcomes/state/targets rather than executing again or recomputing branch decisions. A prepared
transition reused after another transition must still belong to the same immutable parent snapshot.

For legacy programs, retain reachable configurations, outcome sequences, state counts, state-store
identity and failure snapshot behavior. Source failure/cutoff rollback is not a retroactive change
to partial legacy failure diagnostics. Add meaningful Javadoc to every new function.

## Instruction layout

Each thread retains a finite immutable instruction list, with end index `size` as the termination
sentinel. A successful source transition must select `pc < nextPc <= size`; blocked transitions
retain their PC. Reject malformed compiled targets before search. No source back edge is emitted.

- Atomic blocks become one global instruction, irrespective of their internal operation count.
- An if becomes a condition instruction and forward true/false successors. The final real action
  of an arm advances directly to its continuation; emit no separately scheduled jump instruction.
- Compile repeat by expansion. Compile bounded while into up to N body copies separated by
  condition instructions plus a final condition check: false exits, true cuts off.
- Apply continuation targets recursively to nested branches, empty arms, repeats and loop exits.
  Empty whole-thread expansion starts terminated; no dummy step is scheduled.
- A source map attaches the original span and enclosing expansion-iteration identities to every
  global instruction and internal atomic node. Distinct copies can share a source span but not an
  instruction identity. Diagnostic metadata is not state identity.

The unrolled PC records which loop occurrence/allowance is active. Use existing canonical state
bytes and ordered PCs for base identity; CBS also retains last-thread context and cost dominance.
Source-location strings and private evaluator scratch objects are not part of configuration keys.

## Atomic execution

Reuse `DynamicState`, expression parsing/type checking and `Evaluator`. A source atomic instruction
owns an immutable internal statement tree of assignments, if/else, repeats and bounded whiles.
Interpret it against scratch state with a structural program counter local to that invocation.
All internal loops are bounded and subject to 03's expanded-work limit.

Successful completion publishes only the final state and outer continuation. A source runtime error
publishes a failure location but no state; a loop cutoff publishes the exact inner loop location,
limit and completed iteration count but no state. Discard scratch in either case. A disabled outer
guard is tested without mutations. No inner guard-wait, nested atomic, host callback, or I/O executes.

On a cutoff, preserve the current committed configuration and prefix, continue exploring other
scheduled choices, and record an INCOMPLETE witness through 04. No PC advance or successor visit is
performed. Runtime errors remain conclusive failures even when another schedule cuts off.

## Implementation boundaries

Put the generic transition result/executor and immutable control-flow contract in the existing
core execution layer; keep language-specific parsing/lowering in the format layer. Reuse the
existing state and expression machinery. Avoid another explorer or arithmetic interpreter.
Compile and test the legacy adapter before routing source control flow through it. Release 07's
public source entry only after reporting, reduction and replay support are integrated.

## Acceptance

- Independent two-arm fixture selects each target correctly; false condition progresses, not blocks.
- Another thread changes the condition between decision and body without changing the chosen arm.
- Nested branch exits, empty arms and last-action-to-end successors add no artificial schedule step.
- Invalid/backward/out-of-range targets fail compilation before a store is touched.
- Repeated/concurrent runs share definitions without sharing a branch choice or scratch state.
- Loop cap and runtime failure after tentative atomic writes leave the committed state unchanged in
  every explorer, fixed driver and snapshot/replay path.
- Successful atomic interior transient values have no intermediate global observations.
- Existing typed/declarative corpus behavior is unchanged through the legacy adapter.
- Enabled-but-BLOCKED legacy CBS fixture retains its scheduling positions and cost behavior.
- Detailed driver halts on the first source failure/cutoff and classifies prefixes/terminal states;
  the existing source-call wrapper exposes terminal evidence through SourceExecutionException.
