# Spec 12.05 — A Corpus Program That Exposes Dominance Bugs

## TL;DR

**Every program in the corpus has exactly two threads.** That single fact is the root cause of the
largest gap in the set: `ContextBoundedExplorer`'s cost-aware dominance logic — the code that decides
a state reached at preemption budget *p* is dominated by one recorded at budget *q ≤ p* — is
effectively untested. Fourteen `NO_COVERAGE` and `SURVIVED` mutants sit in that logic and in the
adjacent visitor wiring.

This is not a hypothesis. **Defect injection already proved it.** Six defects were injected into
`ContextBoundedExplorer` one at a time; three passed the entire Part B suite. All three corrupt the
same mechanism in different directions, and all three are cases where wrong dominance prunes a state
that was the only route to a reachable violation.

More verdict-level tests will not fix this. A 3-thread corpus program will, because it produces
enough distinct `(config, lastThreadId, preemption)` triples for wrong dominance to be *observable*.

## Objective

Add at least one corpus program with three or more threads whose reachable state space makes
cost-aware dominance observable, and demonstrate the property by showing that defect injection into
the dominance logic now turns the suite red.

This is the only fix in the set for the `ContextBoundedExplorer` survivors. Specs 12.01–12.04 do not
touch them and cannot.

## Scope

- **Package:** `interleave` / `src/main/java/dev/samhb/interleave/corpus` (or `bugs`), `format`
- **Modifies:** the corpus resource(s), the corpus registry, `README.md` corpus listing, any
  benchmark/attestation table that enumerates programs, and adds a dominance-sensitive test
- **Off-limits:** `cb/ContextBoundedExplorer.java` internals — this spec builds the *corpus*, not the
  algorithm. If injection reveals a real dominance defect, that fix is a separate scoped change with
  its own regression test. `format/ProgramLoader.java` invariant registrations — Specs
  `09-json-dsl-core` / `10-json-dsl-invariants` own those, except as noted in R7.

## Non-Goals

- **Not pinning absolute state counts.** The register records that a pruner exploring 12% fewer states
  passes B.1 (verdicts agree), B.2 (still monotonic), and B.3 (store contract unaffected).
  A count guard would pin the implementation, not the property, and every legitimate optimisation to
  the visited key would break it. If a count assertion is added at all, it is a tripwire with a
  comment saying so — not the primary defense.
- Not changing the CBS algorithm, the `StateStore` interface, or the dominance equivalence itself.
- Not making the whole corpus multi-threaded. One program is enough to make the property observable;
  a wholesale corpus migration is a separate effort.
- Not raising the mutation score by itself. This spec's success criterion is *defect injection
  turning red*, not a percentage.

## Current State

### The corpus is uniformly 2-thread [verified]

`bugs/BugCorpus.all()` returns seven programs loaded from JSON resources. **All seven declare exactly
two threads** (ids 0 and 1) [verified]:

| program | threads | expected verdict | invariant |
|---|---|---|---|
| `peterson` | 2 | `PASS` | none |
| `broken-peterson` | 2 | `VIOLATION` | `mutual_exclusion_peterson` |
| `broken-peterson-v2` | 2 | `VIOLATION` | `mutual_exclusion_peterson` |
| `deadlock` | 2 | `DEADLOCK` | none |
| `double-checked-locking` | 2 | `VIOLATION` | `dcl_uninitialized_observed` |
| `lost-update` | 2 | `VIOLATION` | `counter_equals` |
| `torn-counter` | 2 | `VIOLATION` | `torn_read` |

### Defect-injection evidence [verified]

Six defects injected into `ContextBoundedExplorer`, one at a time, against the merged Part B suite:

| # | Injected defect | Direction | Caught by Part B |
|---|---|---|---|
| 1 | Undercount preemptions to 0 on a paid switch | explores too much | yes — 4 tests |
| 2 | Charge continuations as preemptions | over-prunes, exhausts budget early | yes — 1 test |
| 3 | Prune on `config` alone, ignoring `lastThreadId` and budget | over-prunes | yes — 1 test |
| 4 | Budget-blind visited key (`markVisited(config, tid, -1)`) | over-prunes | **NO** |
| 5 | Record `currentPreemptions + 1` (inverted dominance) | over-prunes | **NO** |
| 6 | Always record zero cost | over-prunes | **NO** |

**Defects 4–6 all survived.** Verdicts stayed correct in every miss: with 2 threads and few steps, the
corpus produces too few distinct `(config, lastThreadId, preemption)` triples for wrong dominance to
drop a state that mattered. State counts fell measurably under defect 6 (measured):

| program | CBS states | DFS states |
|---|---|---|
| `peterson` | 59 | 42 |
| `broken-peterson` | 60 | 46 |
| `deadlock` | 19 | 15 |
| `torn-counter` | **8** | **8** |

The `torn-counter` row is the sharpest evidence: under a defective pruner, CBS stopped exploring more
states than DFS, and **no test noticed**. A dominance bug that reduces exploration to *no better than
the baseline it is supposed to beat* is invisible to every test written.

Measured configuration count for the new program: `[unverified]` — does not exist yet. Measure it
per R2 and record the value and its margin over the 2-thread corpus here before closing this spec.

Measured configuration counts across the existing 2-thread corpus: `[unverified]` — not recorded in
this document. R2 requires the comparison against this maximum, so establishing it is part of
implementing 12.05.

### Mutants this spec must kill [verified]

| line | symbol | mutator | status |
|---|---|---|---|
| L82 | `dfs` — store-capacity read | `NON_VOID_METHOD_CALLS` | SURVIVED |
| L87 | `dfs` — visitor call | `VOID_METHOD_CALLS` | SURVIVED |
| L88 | `dfs` — visitor call | `VOID_METHOD_CALLS` | SURVIVED |
| L113 | `dfs` — visited-key construction | `NON_VOID_METHOD_CALLS` ×5 | SURVIVED ×5 |
| L41 | `explore(Program)` | ×2 | NO_COVERAGE (Spec 12.04) |

`L113` alone is 5 mutants. That line builds the explorer's own visited key; removing a component of
it is exactly defects 4–6's mechanism, and Part B's differential tests did not catch it because the
*verdict* survives even when the *pruning* is wrong.

### Two [verified] constraints that shape the design

- **`ProgramLoader.java:253` — `mutual_exclusion_peterson` requires exactly two threads**, throwing
  `RegistryException` otherwise. A multi-thread program **cannot** use the Peterson invariant.
- **`DslLoader.java:63` — the DSL accepts 1–8 threads** (`threadCount < 1 || threadCount > 8` throws).
  Multi-thread programs are expressible today; no DSL work is needed.

So the new program must use a thread-count-agnostic invariant. `counter_equals` (from `lost-update`) is
the natural fit and needs no new invariant type.

## Invariants

- **The dominance equivalence is the property under test.** A state reached at preemption budget *p*
  SHALL be pruned only if some recorded state with identical `(config, lastThreadId)` was reached at
  budget *q ≤ p*. Pruning on any other basis — ignoring budget, ignoring `lastThreadId`, or recording
  a cost greater than the true one — discards states that may be the only route to a violation.
- **A correct program SHALL be `PASS` under DFS, and `INCOMPLETE` under a bounded CBS run that
  exceeded its budget.** These are not in conflict and must not be asserted as agreement:
  `SoundnessAttestation` excludes `INCOMPLETE` from cross-strategy agreement precisely because a
  bounded run on `peterson` is `PASS` under DFS and `INCOMPLETE` under CBS at K=2 (Spec 11.05 §6).
  **CBS reports `PASS` only when it is exhaustive at the bound.** A bounded CBS run over budget
  reports `INCOMPLETE` even when the program is correct — that is the verdict's entire purpose, not a
  correctness violation, and treating it as one would make this spec unsatisfiable.
- **A buggy program SHALL report its declared verdict** under every strategy that is expected to find
  it, at the bound where it is detectable.
- **Adding a corpus program SHALL NOT change the total mutant count.** Mutators target production
  source; new tests can only move mutants *between* statuses (`NO_COVERAGE` → `SURVIVED`/`KILLED`).
  A change in the total means production code changed or scope moved — investigate before reading the
  percentage (Spec 12.06 §R5). **The expected total is the one measured after Spec 12.01 landed, not
  the 275 recorded today:** 12.01 §R3 Branch A deletes `CanonicalEncoder.equals`, removing its five
  mutants outright. Assert against that post-12.01 total and record it here when 12.01 lands.
- **Corpus programs SHALL remain small enough to explore exhaustively at the test bound.** A program
  too large to DFS makes every differential assertion meaningless.

## Requirements

1. **THE SYSTEM SHALL** add at least one corpus program with **three or more threads** whose
   reachable configuration space contains enough distinct `(config, lastThreadId, preemption)`
   triples that a dominance error changes which states are explored.
2. **WHEN** the new program is run exhaustively under DFS at the test bound, **THE SYSTEM SHALL**
   report a measured configuration count, and that count SHALL exceed the maximum across the
   existing 2-thread corpus by a margin recorded in §Current State. The requirement is the
   *measurement and its comparison*, both of which are objectively checkable; it does not depend on
   a predicted value for the new program.
3. **THE SYSTEM SHALL** declare the new program with a thread-count-agnostic invariant. It SHALL NOT
   use `mutual_exclusion_peterson` [verified: rejected by `ProgramLoader`].
4. **WHEN** a dominance defect of the classes of injected defects 4–6 is introduced into
   `ContextBoundedExplorer`, **THE SYSTEM SHALL** observe the test suite go red.
5. **THE SYSTEM SHALL** provide at least one buggy program whose reachable violation requires
   exploring a state that over-pruning would drop.
6. **THE SYSTEM SHALL** retain a correct program in the corpus whose bounded CBS run produces only
   `COMPLETED` traces, so `INCOMPLETE` suppression behaviour stays covered after the corpus changes.
7. **THE SYSTEM SHALL NOT** assert an absolute state count as its primary defense. Any count
   assertion SHALL carry a comment marking it a tripwire.
8. **WHEN** the corpus is extended, **THE SYSTEM SHALL** update every place that enumerates programs
   and would otherwise silently omit the new one — at minimum the README corpus listing, the
   benchmark states-explored table, and the soundness attestation inputs.
9. **THE SYSTEM SHALL** keep `SoundnessAttestation` passing: all exact strategies that report a
   decided verdict must agree on every correct program — with `INCOMPLETE` excluded from that
   agreement, as it already is. A correct program SHALL never be reported `VIOLATION` by any
   strategy, bounded or not, and every reported violation SHALL replay.
10. **THE SYSTEM SHALL** record the measured configuration count for the new program, and SHALL
    re-run `./gradlew pitest` and report which of the L82/L87/L88/L113 mutants flipped.

## Acceptance Criteria

- [ ] A new ≥3-thread program exists, is registered in the corpus, and is listed in the README
      (R1, R8).
- [ ] Its invariant loads successfully (R3) — proven by the corpus load test passing.
- [ ] Its exhaustive DFS configuration count is measured and recorded, and exceeds the 2-thread
      corpus's maximum by a documented margin (R2).
- [ ] **Falsification, four runs, all four demonstrated in the PR body** (R4):
      - inject defect 4 (budget-blind visited key) → suite red
      - inject defect 5 (record `currentPreemptions + 1`) → suite red
      - inject defect 6 (always record zero cost) → suite red
      - confirm all three are reverted and the suite is green
- [ ] A buggy program exists whose violation is reachable only via states that over-pruning drops
      (R5).
- [ ] A correct program still yields a bounded CBS run with only `COMPLETED` traces (R6).
- [ ] `SoundnessAttestation` passes with the extended corpus (R9).
- [ ] `./gradlew pitest` total is **unchanged from the post-12.01 total** — a different total fails
      the acceptance criteria until explained (Invariant, R4 above). The literal number is recorded
      in this spec when 12.01 lands; it is *not* 275 unless 12.01 chose Branch B and kept `equals`.
- [ ] `./gradlew clean test javadoc` passes.
- [ ] The PR body reports the before/after status of L82, L87, L88, L113.

## Design

### Choosing the program shape

The requirement is *not* "a bigger program" — it is "a program whose reachable state space makes
wrong dominance observable." The shape that achieves it:

**N threads (start at 3) performing read-modify-write on a shared counter, with a lost-update bug in
one thread.** Under `counter_equals` with `expected: N`.

Why this shape:

- **It multiplies the distinct `(config, lastThreadId)` pairs.** Distinctness comes from the
  program-counter configurations, not from the number of schedules: two threads give O(steps²)
  distinct configurations, three give O(steps³). The dominance key's distinctness is what defects
  4–6 need in order to be caught.
- **It uses a thread-count-agnostic invariant**, so no DSL work is needed [verified].
- **It has a known bug class already represented** (`lost-update`), so the expected-verdict machinery
  and attestation are already exercised for it.
- **It stays exhaustively DFS-able.** A counter-based program with a few steps per thread is small in
  configuration space even at three threads; that is the property R2 measures rather than assumes.

Start at 3 threads, not 4+. Every additional thread multiplies the state space and the test runtime,
and 3 is the minimum that breaks the 2-thread degeneracy. If 3 does not expose the property, measure
and record why before escalating to 4.

### The falsification protocol (R4) — this is the deliverable

The point of this spec is not the new program; it is the **proof** that the program makes dominance
observable. Without R4, the new program is just more corpus, and "more corpus" is exactly the
low-yield move Spec 12.01–12.04 already declined.

Procedure, repeated for each of defects 4, 5, 6:

1. Record the green baseline: full suite passing, `./gradlew pitest` baseline numbers.
2. Inject the single defect into `ContextBoundedExplorer`.
3. Run `./gradlew test`. **It MUST go red.** Record which test(s) and the failure message.
4. Re-run `./gradlew pitest`. Record whether L82/L87/L88/L113 flipped.
5. Revert the defect. Confirm the suite is green again.
6. Repeat for the next defect.

Record all six data points in the PR body. A defect that still passes is a **finding about the
program's shape** — go back to R1 and widen the program; do not paper over it by weakening a test.

This protocol is the same one PR #29 used, and it is the reason the register could state "three of
six injected defects pass today" as a measured fact rather than a suspicion. Preserving that property
is worth more than any single new mutant kill.

### Keeping the blast radius small

Adding a corpus program touches more than it looks like:

- `BugCorpus.all()` and the resource list
- README's corpus enumeration and any stated counts (currently "7-program corpus")
- `StatesExploredTable` — CBS-vs-DFS percentages, which are **not** guaranteed to be reductions
  (Spec 11.05 §5 already made negative percentages representable; a new program will exercise that)
- `SoundnessAttestation` — every correct program must agree across exact strategies
- Spec 11's README, which states verification results "against the 7-program corpus"

Enumerate these in the PR body as an explicit checklist (R8). A benchmark table that silently omits
the new program is the same defect class as Spec 11.05 §5's hardcoded `strategyOrder` array.

## Tests

**File:** `src/test/java/dev/samhb/interleave/corpus/CorpusDominanceCoverageTest.java` (new)

- `dominanceRule_prunesOnlyOnNonGreaterOrEqualBudget` (R1, R4) — the property test: for a bounded
  search, every pruned state has a recorded dominator with budget ≤ its own
- `threeThreadProgram_dfsConfigurationCount_exceedsTwoThreadMaximum` (R2) — pins the R2 measurement
  as a floor, so a corpus change that shrinks the state space fails loudly
- `newProgram_expectedVerdict_reportedByEveryStrategyThatFindsIt` (R5, R9) — asserted per strategy
  with its bound stated, so a bounded `INCOMPLETE` is a pass and a bounded `VIOLATION` is not
- `newProgram_violationReplay_satisfiesInvariant` (R5, R9)
- `soundnessAttestation_passesWithExtendedCorpus` (R9)
- `correctProgram_boundedCbsRun_emitsOnlyCompletedTraces` (R6)
- `benchmarkTable_includesNewProgramRow` (R8) — guards against silent omission

**Falsification (not committed):** the three-defect injection protocol in R4.

## Constraints

- **Dependencies:** Spec 12.01 (encoder injectivity — a lossy encoder would make the R2 counts
  meaningless). Spec 12.04 (trace-emission tests derive bounds from the corpus; if 12.04 lands after
  this spec, re-derive them).
- **Backward compatibility:** the corpus is a shipped artifact — README counts, benchmark output,
  and the evidence artifact all change. That is intended and must be reflected in the PR body, not
  hidden. Anything asserting "7 programs" must be updated in the same commit (no drift, `AGENTS.md`
  §4).
- **Runtime:** a 3-thread program multiplies DFS configuration count. If the full suite slows
  materially, reduce steps-per-thread before reducing thread count — the thread count is the whole
  point.
- **If injection reveals a real dominance defect,** that is a success of this spec. Ship the corpus
  change and the production fix as separate commits, the fix with its own regression test, and say so
  explicitly in the PR body. Do not treat it as scope creep.

## Commands

```bash
./gradlew test --tests "*Corpus*"
./gradlew test --tests "*SoundnessAttestation*"
./gradlew run --args="--all --strategy CONTEXT_BOUNDED --max-preemptions 2"
./gradlew pitest
./gradlew clean test javadoc
```

## Map

- `src/main/java/dev/samhb/interleave/bugs/BugCorpus.java` — the corpus registry
- `src/main/resources/programs/*.json` — the seven program resources
- `src/main/java/dev/samhb/interleave/format/ProgramLoader.java` — `mutual_exclusion_peterson` two-thread guard
- `src/main/java/dev/samhb/interleave/format/dsl/DslLoader.java` — the 1–8 thread bound
- `src/main/java/dev/samhb/interleave/cb/ContextBoundedExplorer.java` — the dominance logic under test
- `src/main/java/dev/samhb/interleave/report/StatesExploredTable.java` — corpus enumeration
- `src/main/java/dev/samhb/interleave/report/SoundnessAttestation.java` — cross-strategy agreement
- `docs/plans/mutation-gap-register.md` — Gap 6, the injection experiment this spec scales up