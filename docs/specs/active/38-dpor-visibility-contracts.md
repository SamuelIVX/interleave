# Spec: DPOR visibility refresh and compiler guard evidence

## Objective

Protect visible-step sleep refresh and audit the adjacent enable/disable probe in roadmap 9-V1.
Use concrete value and witness oracles, since coarse verdict equality can hide lost evidence.

## Scope

Add DporVisibilityContractTest, this specification and a roadmap update. No production, API,
dependency, report-output, mutation-scope/operator/exclusion or gate changes. Happens-before
bookkeeping remains a separate audit. No whole-class, whole-checker or unmeasured aggregate closure.

The probe disposition below is limited to compiler-produced SourceInstruction definitions, faithful
DynamicState/configuration copies, complete guard/error footprints and non-mutating model operations.
Arbitrary custom steps, incomplete accesses, identity-sensitive or history-dependent behavior do not
inherit it. No process/resource/performance equivalence is claimed; the production guard is retained.

## Requirements

1. A visible write SHALL refresh inherited sleepers before an owner's future read depends on one.
   The concrete prefix [0,1,2,1] SHALL retain its real property violation in the specified model.
2. Returned states and visitor positions SHALL independently retain literal counters and every shared
   value. Witnesses and callbacks SHALL independently match literal schedules, outcomes and classes.
3. A dependent guard disabled by an earlier write SHALL retain DEADLOCK; an erroring guard SHALL
   remain enabled until its actual attempt records a STEP/RUNTIME_ERROR/MODULO_BY_ZERO violation.
4. Source traces SHALL replay directly with their original evidence and actual property. No hidden
   mutable counters, invalid metadata or private reflection force mutation coverage.
5. The repeated enable/disable probe SHALL receive only the compiler-scoped argument below, which
   explicitly considers modeled errors rather than assuming pure operations cannot throw.

## Design and independent oracles

There are three cases: one visibility counterexample and two dependent-guard cases. Metadata is
explicitly checked so every fixture uses the intended reduced traversal rather than UNKNOWN fallback.

### Visible write before a future read

T0 writes marker=1. T1 writes phase=1 then observed=input. T2 writes input=1. Initially all four
values are zero; the property is phase>=0 && observed==0, with complete observations {phase,observed}.

After T0, T2 can sleep: its input write is independent of marker and T0 has no future actions.
T1's phase write stays awake because it is visible. Executing it must clear the inherited T2 sleeper.
T2 must not be re-added: T1's future observed=input depends on T2's write. The two orders after
prefix [0,1] now retain completed [0,1,1,2] with observed=0 and violating [0,1,2,1] with observed=1.
A second violation [1,2,1] occurs while marker is still zero. Losing the marker=1 violation can
therefore leave the same coarse outcome set, making only verdict comparison insufficient.

The exact literal witness list is:

| Prefix | Action outcomes | Terminal class |
|---|---|---|
| [0,1,1,2] | Four ADVANCED | COMPLETED |
| [0,1,2,1] | Four ADVANCED | VIOLATION |
| [1,2,1] | Three ADVANCED | VIOLATION |

Both violations carry INVARIANT/PROPERTY evidence, not failed-step outcomes. The tests replay the
actual source traces without reconstructing their metadata.

The counter product has 2×3×2=12 tuples. Once T1 and T2 finish, observed can be zero or one in each
marker position, giving two additional value states: fourteen exhaustive configurations. Reduced
traversal omits the independent pre-phase combination [1,0,1], leaving thirteen. Direct enumeration
of all thirteen literal counter/marker/phase/input/observed tuples independently cross-checks that
count. The visitor order, returned value set, thirteen visits and thirteen unique positions are
asserted. These counts and prefixes are fixture-specific oracles, not general schedule-enumeration
or minimum-state guarantees for every correct reduction algorithm.

### Dependent guards, including errors

T0 writes x=1. T1 writes y=1 under either guard x==0 or guard x==0 || 1%0==0. The compiler's
known-empty always-true property checks every configuration without introducing visibility.
The fixtures assert T0's x-write and T1's complete x-read footprint, so the pair is dependent.

If T0 runs first, the ordinary guard becomes false and produces DEADLOCK after [0]. The second
guard reaches its erroring right operand: SourceInstruction.enabled keeps that action enabled,
and the actual attempt records [0,1] ADVANCED/ASSERTION_FAILED with a STEP runtime violation,
MODULO_BY_ZERO category and the guarded atomic construct's exact source location
(`<source>`, line 3, columns 14–59, half-open), rather than the inner remainder expression's span.
The columns are counted directly from the fixture and independently cross-checked with Python.
The property itself remains true. If T1 runs first,
x is still zero and short-circuit evaluation succeeds; both models complete via [1,0].

All four counter pairs [0,0], [1,0], [0,1], [1,1] have literal values (0,0), (1,0), (0,1), (1,1).
The independent 2×2 product and the explicit list both give four committed positions. A failed or
disabled attempt adds no successor; the terminal state at [1,1] comes from the other ordering.
Results and callbacks each match those concrete states and terminal witnesses.

## Compiler-scoped enable/disable probe disposition

In DporExplorer.dporDfs, the selected transition is executed before the sleep-update loop. Cutoffs
and failed attempts are handled before that loop. Valid enabled compiler actions cannot produce
BLOCKED there; the probe is reached after a successful ADVANCED transition with a derived successor.

For an independent pair, complete guard/error footprints mean the chosen action cannot change the
other action's enabledness or guard-error status. The probe returns false, so noInterference is true.
For a dependent pair, the earlier independent Boolean is false, so the probe's Boolean cannot make
the sleep-add condition true. Replacing the probe result with false therefore preserves eligibility.

That Boolean argument alone would be insufficient if skipping the probe removed an observable
exception. The probe executes only the already-successful chosen action, not the other action.
Its immutable instruction and unchanged parent values reproduce the same successful transition on
a faithful scratch copy. The original successor construction already derived enabledness for every
other owner's unchanged current PC using the same after-values. The probe's before-guard check also
matches the parent's cached enabledness inputs. SourceInstruction.enabled returns true for modeled
guard errors; the eventual attempted transition, not the probe, supplies failure evidence. Thus no
additional modeled exception is introduced by these repeated compiler operations. The two guard
cases protect the disabled-versus-error boundary with actual source execution and literal evidence.

This is narrower than saying purity implies no errors. It does not cover arbitrary custom SourceStep
transitions, impure/malformed models, unfaithful copies or process/resource failures. Probe allocation,
comparison and execution costs are deliberately outside the output disposition. The argument applies
to dporDfs/663 (removed hasEnableDisableInterference call), not to the visibility-refresh sites.
Bytecode indices identify mutations, not source lines.

## Acceptance

- Three new cases and the full suite pass; build and strict Javadoc pass.
- Fresh scoped baseline/final semantic populations match and all old kills remain killed.
- Record measured changed identities, the scoped probe disposition and remaining audits once;
  no exclusions, adjusted scores or whole-checker closure claims.
- Default PIT, normalization and the substantive three-file CodeRabbit CLI review complete.
  Minor feedback is patched with local checks without extra CLI runs, per Sam's preference.

## Baseline and verification

Merged PR #78 is `b6ad6ec`. Fresh baseline: all 940 tests pass, zero failures/errors/skips;
outer-DporExplorer262/289 KILLED,15 SURVIVED,12 NO_COVERAGE, zero timeout/error statuses.
All 943 full-suite tests pass, zero failures/errors/skips. Scoped final: 265/289 KILLED,
12 SURVIVED,12 NO_COVERAGE, zero timeout/error statuses. Semantic identities (class, method,
descriptor, mutator, bytecode indices and blocks) match across all 289 entries; all 262 old kills
remain killed. Three survivors become kills, each killed by visibleWriteRetainsTheConcreteFutureReadViolation:

| Method / bytecode index | Mutation | Baseline → final |
|---|---|---|
| dporDfs/569 | Removed writesOverlap call | SURVIVED → KILLED |
| dporDfs/570 | Negated visibility conditional | SURVIVED → KILLED |
| dporDfs/582 | Negated visibility ternary condition | SURVIVED → KILLED |

The new dporDfs/663 removed-probe disposition is scoped above. Including Specs 35–37, there
are 21 scoped dispositions (9 survivors and 12 uncovered), leaving three open happens-before
survivors: dporDfs/540,546,554. Thus 265 kills +21 scoped dispositions +3 open =289 identities.
Raw statuses remain 12 SURVIVED and12 NO_COVERAGE; neither dispositions nor this slice establish
whole-class/checker closure. The historical expanded9-V1 aggregate has not been remeasured.

CodeRabbit CLI0.8.1 completed its three-file review with one trivial finding: replace the source
location's non-null check with an exact span. The local fix asserts the current guarded-construct
span after checking SourceInstruction's evaluationFailure behavior, rather than changing production
diagnostics to match the suggested inner-expression span. All three targeted cases pass afterward;
no additional CLI run was made for trivial feedback. The scoped mutation measurement above preceded
only that final assertion strengthening; it was not rerun afterward. Final clean build, strict Javadoc and default PIT pass with all 943 tests, zero failures/errors/skips.
Default PIT is 281/291 KILLED,10 SURVIVED, zero uncovered/timeout/error statuses; all 291 semantic
identities and statuses match merged Spec37 evidence. All 18 normalization checks pass. The final
focused run and integrated suite include the strengthened source-span assertion. Relative links,
credential-pattern scan and git diff --check pass. Remote CI remains unverified; publish as draft.
All local Gradle commands use `--max-workers=1 -PpitestThreads=1`. Browser integration is outside
this bundle because executable production and report output are unchanged.
