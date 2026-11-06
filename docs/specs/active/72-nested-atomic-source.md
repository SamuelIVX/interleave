# Spec 72: Unguarded nested source atomic blocks

## Contract and scope

Implement selected 9-F3 as one complete syntax feature, following merged 9-F2 / PR #113 at
eb6db82. An unguarded `atomic { ... }` inside an atomic body groups private statements within
the existing outer action. It adds no action, PC, guard, observation, commit or rollback boundary.
Statements execute in source order against the outer tentative state. Empty inner blocks do
nothing; an empty outer block still has its existing one successful action.

An inner `atomic when (...)` remains unsupported, even in unchosen or zero-repeat bodies.
Only the outer guard controls enabledness. Helper calls inside atomic interiors remain rejected.
Helpers may themselves contain the new nesting as part of their ordinary outer atomic actions.
No runtime/API/trace schema, reduction, scheduling, store, dependency or existing budget change.

## Implementation and affected surfaces

SourceCompiler.block already carries insideAtomic and source nesting depth. Parse inner braces
with the same recursive path, then append their private statements into the surrounding private
list. Only the outer atomic node survives; no new node kind or execution branch is needed.
Preserve each assignment/condition/loop's original source span and expansion ancestry. Existing
SourceInstruction footprints, private execution, failure classification and rollback continue to
consume the same validated private tree. The outer label/span covers the complete outer construct.

Update source acceptance/rejection regressions, teaching fixtures, language/limit/evidence docs,
and the future-work table together. Deliberately replace the old unsupported-unguarded-nesting
fixture with guarded nesting; unguarded nesting now has positive semantic coverage. Do not delete
or weaken other malformed-source cases. Required existing work probes/checkpoints remain unchanged.

## Budgets and independent oracles

Nested braces count toward the existing inclusive depth 16 before flattening. Private assignment
and condition counts aggregate across all inner blocks into the same outer 64-instruction cap;
inner braces add zero. Repetition and expression costs retain their existing counting rules.
Flattening must not hide 65 private instructions or 5,001 expression nodes. Helper definition
validation, including unused/zero-repeat branches, retains its existing stricter private checks.

Two owners each performing a nested load/increment/store within one outer atomic action have
only terminal counter 2 and four PC positions (two-by-two). The caller-local fixture retains
which owner loaded zero versus one, so it has five complete configurations: root, two first-action
states and two distinct terminal states. Independently enumerate both serial owner orders. Temporary writes across nested block boundaries are never visible to a
competitor or an always property. Compare complete read/write footprints with manually flattened
source, and exercise DFS/static POR/DPOR/CBS with invariant-aware replay.

Regression cases cover nested empty blocks, if/repeat/while bodies, caller-local/tid binding,
outer guards/deadlock, all-or-nothing rollback for inner runtime errors and loop cutoffs, and
original inner terminal locations. Keep one outer trace entry for failure, omitted action for
cutoff, and regenerated replay/minimization evidence. Boundary oracles are counted independently
outside Java and asserted as literals.

## Verification and publication

Run fresh merged baseline clean build/tests/strict Javadoc and affected compiler PIT before edits.
Afterward run integrated checks, scoped/compiler identity/status accounting, default gate, actual
CLI/report/attestation for the new example and the 18 normalization cases. Never combine affected
and default populations, credit resource errors, or change filters/operators/floors/PIT resources.
Use the required initial CodeRabbit review for this spec-driven parser feature; minor follow-up
fixes use local checks without another CLI run. Publish one draft PR, report the link and stop.
9-F4 statement assertions remain the next sequential feature after this merge.

## Measured results

Fresh baseline passes 1,443 tests/clean build/strict Javadoc; compiler 841/891 K + 28 S + 22 NC.
Final integrated 1,472 tests/build/strict Javadoc pass, zero failures/errors/skips. Compiler final
845/895 K + 28 S + 22 NC, zero resource/run errors; scoped R6 passes, R5/R7 skip. All 769 identities
outside block retain their statuses. No new raw residual or changed mutation gate configuration.
The four extra block mutants kill; seven inherited block survivors retain their scoped staging
arguments. [Acceptance evidence](../../measurements/72-nested-atomic-source.md) records independent
oracles and full population accounting. All 18 normalizer cases pass. Default gate passes at
283/293 K + 10 S, all 293 identities/statuses retained, zero resource/run errors, R5/R6/R7 pass.
Actual Main exact CLI reports PASS across all four strategies, five visits each, soundness=true.
CodeRabbit CLI 0.8.1 completed all 15 files with zero findings. Final build/strict Javadoc pass
again after the documentation clarification. Browser rendering was not rerun: schema/renderer
are unchanged. No unverified speed, universal equivalence or whole-checker soundness claim.
