# Mutation Gap Register — work NOT owned by the main plan

**Status:** active. Part B landed as PR #29 (`c5fdcd0`); this register is the remaining work.

**Source of truth:** PIT 1.30.0 scoped run on `main` @ `313df44` (post-PR #28), the baseline this
register was written against. Baseline: 275 mutants, 221 killed (80% mutation coverage), 40
survived, 14 no-coverage. Reproduce with `./gradlew pitest`; per-mutant detail in
`build/reports/pitest/index.html`. Part B has since landed, so see **Post-Part-B
re-measurement** for the current figure — the two differ, and the newer one is the number a
ratchet should be built from.

## Why this file exists

PR #28 landed the instrument. Part B addressed the *behavioral* gaps in
`ContextBoundedExplorer` and `HashingStateStore`. It did **not** cover everything the
instrument found — a ratchet pinned to today's 80% would freeze these gaps in place.

This register enumerates the 54 non-killed mutants, sorted by whether Part B touches them.
It is deliberately written alongside Part B so it did not compete with it for attention.

## Reading the two categories

| category | meaning | can a behavioral test kill it? |
|---|---|---|
| **NO_COVERAGE** | no test executes this line at all | only by exercising the line |
| **SURVIVED** | tests ran the line, assertions didn't constrain it | depends on the mutation |

A surviving mutant is not automatically a defect. Several below are **equivalent mutants** —
the code is semantically redundant, so no test *can* distinguish it. Those are marked
**equivalent** and should be suppressed, not chased. Do not "fix" production code to kill an
equivalent mutant; that means writing a test asserting an implementation detail.

---

## Part B owned these — landed as PR #29

Part B has since landed, so these are **results**, not predictions. The re-measurement below
records what actually happened.

| class | survivors | notes |
|---|---|---|
| `ContextBoundedExplorer` | L82, L87, L88, L109, L113×5, L187 (10 survived); L41×2, L176×4 (6 no-coverage) | `explore`/`dfs` core pruning. B.1 (DFS differential) and B.2 (monotonicity in K) were aimed here. |
| `HashingStateStore` | L39 | `isVisited` final return. B.3 (store equivalence) targeted exactly this — PR #26 defect class. |

Part B landed after these rows were written, so the "re-derive with the new tests in place"
check has been done. Anything from these rows still surviving is a **genuine finding**, not a
threshold to relax.

---

## Gap 1 — `CanonicalEncoder` is effectively untested (HIGH)

**6 mutants: 5 no-coverage, 1 survived. Mutation coverage 50% — worst in scope.**

The encoder is what every store's correctness rests on. `HashingStateStore.encode` and
`BitstateStore.preemptionKey` both route through `CanonicalEncoder.encode` to build the
visited-key. If the encoder is wrong, both stores are wrong together and **no store-level
differential test can detect it** — B.3 compares two stores fed the same bad encoder.

`CanonicalEncoder.equals(SharedState, SharedState)` has **no caller in `src/` at all** and
no test. Its 5 no-coverage mutants (3 × `NonVoidMethodCall`, `BooleanFalse`, `BooleanTrue`
all at L24) are dead code.

This is the highest-value item on the page: it is upstream of everything Part B tests.

### Work

1. **Decide the fate of `equals(a, b)`** — it is unreachable. Either delete it, or keep it and
   test it. Deleting removes 5 mutants and shrinks the surface; keeping it means it is public
   API on a `Serializable` class and owes tests. *Recommendation: delete.* It is not used, and
   an untested public method on the correctness foundation is worse than no method.
   Confirm nothing outside `src/` (docs, benchmark harness) references it first.
2. **`encode` L16 — `out.flush()` removal survives.** Read it as: flushing a
   `DataOutputStream` over a `ByteArrayOutputStream` changes nothing observable, because
   `toByteArray()` does not require the buffer drained. **Likely equivalent.** Confirm by
   reasoning about `DataOutputStream` internals; if confirmed, exclude rather than contrive
   a test.
3. **`encode` determinism and injectivity are tested only pairwise** (`StateHashingTest:12`
   checks one equal pair). That cannot distinguish a *lossy* encoder that maps distinct states
   to identical bytes. Add:
   - **injectivity**: N distinct states → N distinct byte arrays;
   - **field sensitivity**: two states differing in exactly one field → different bytes
     (this is the property that makes cross-thread interference detection sound, and it is
     the one a pairwise test cannot express);
   - **length-prefix disambiguation**: states whose concatenation collides without framing
     (e.g. `[A=1,B=2]` vs `[A=12,B=2]`) must encode differently. `DataOutputStream` framing
     is what prevents this; nothing currently pins it.

### Falsification check

Change `SharedState.encodeTo` to omit one field. Confirm the field-sensitivity test goes red.
A test that passes with the field omitted is worse than no test.

---

## Gap 2 — `HashingStateStore` internals beyond `isVisited` (MEDIUM-HIGH)

**15 mutants: 14 survived, 1 no-coverage. Only L39 is owned by B.3; the other 14 are not.**

| line | method | mutators | assessment |
|---|---|---|---|
| L52, L54 | `clear` | `VoidMethodCall` ×2 | dropping `preemptionHashes.clear()` / another field's clear |
| L98 | `preemptionEntryCount` | `NonVoidMethodCall`, `PrimitiveReturns` | not covered by B.3 |
| L102 | `hashCode` | `NonVoidMethodCall` | dropping the `programCounters` contribution |
| L103 | `hashCode` | `Math` ×2, `NonVoidMethodCall` | the `31 *` seed |
| L104 | `hashCode` | `PrimitiveReturns` | final hash arithmetic |
| L114 | `preemptionHash` | `Math` ×2, `NonVoidMethodCall`, `PrimitiveReturns` | the `31 *` seed and `+ lastThreadId` |
| L123 | `freshCopy` | `NullReturnVals` | returns `null` — no-coverage |

**Read L102/L103/L114 carefully before writing anything.** These are hash *mixing* constants.
Removing a `31 *` seed or a field from `hashCode` makes the store **less** selective, not
wrong: `isVisited` still answers correctly because correctness comes from `visitedStates`
(the exact `Set<String>`), and `visitedHashes` is only a fast-reject filter. So these
mutants are, for verdict purposes, **equivalent** — they cost performance and FPR, not
soundness. A behavioral test *cannot* kill them, and a test asserting a specific hash value
would be asserting an implementation detail that legitimately changes.

The right move is to **document them as equivalent** in the register rather than test them,
with one exception: if you want them covered, assert the *property* that matters —
`hashCode` differs for states differing in any field (test-strengthening the FPR), not a
specific integer.

**Genuinely testable here:**

- **L52/L54 `clear` (2 mutants)** — `clear()` must reset *all* fields. `StateStorePreemptionTest`
  covers this for bitstate (`bitstateStore_clear_resets`) but the hashing store's three-way
  clear is unpinned. Port that test to `HashingStateStore`: mark visited, mark preemption
  entries, clear, assert all three read empty. A partial `clear` leaks state between searches.
- **L98 `preemptionEntryCount`** — already asserted somewhere for bitstate; assert the hashing
  store returns a count that changes after `markPreemption` and returns to 0 after `clear`.
- **L123 `freshCopy` returning `null` (no-coverage)** — a `null` store would NPE mid-exploration.
  Assert `freshCopy()` is non-null and independent (mutating the copy does not affect the
  original), mirroring `bitstateStore_freshCopy_independent`.

### Falsification check

Delete `preemptionHashes.clear()` from `clear()`. Confirm the ported test goes red.

---

## Gap 3 — `BitstateStore` metrics arithmetic (MEDIUM)

**11 survivors, 2 no-coverage. B.3 explicitly does NOT cover these** — §B.3 says
"this item is about verdict-level agreement, not metric arithmetic" and defers to the PR #26
metric tests, which are pinned for density and FPR. Yet 5 mutants survive *inside*
`estimatedFalsePositiveRate` (L298, L300 ×2, L301 ×2).

The plan's §B.3 note is therefore **inaccurate as written**: it assumes the metric tests cover
metric arithmetic, and the run shows they do not.

| line | what breaks |
|---|---|
| L298 | `size * vectorsInUse()` → `size` alone, or the `vectorsInUse()` term dropped |
| L300 | `k * n / m` → `-k * n / m` (InvertNegs), or arithmetic in the exponent |
| L301 | `Math.pow(prob, k)` → wrong argument |

`estimatedFalsePositiveRate` is user-facing diagnostic output. A wrong FPR misleads a user
choosing `--store bitstate`, which is the decision this number exists to inform.

### Work

Add a **direct assertion against the closed form** rather than properties:

```java
// for known (n, m, k), assert the exact expected double within 1e-9
```

Use literal inputs — `statesMarked=0` → `0.0` (already handled), then a hand-computed
non-trivial case. `assertEquals(expected, actual, 1e-9)` on doubles. Existing property-style
tests can't distinguish these mutants because they tolerate a wide band.

**L68/L71 `<init>` ConditionalsBoundary** — the constructor's validation bounds. Add a test
that each documented bound is rejected at exactly its boundary and accepted one inside it.
Untested boundary validation is how an off-by-one in a capacity check reaches production.

**L201 `size()`, L210 `numHashFunctions()` (no-coverage)** — trivial accessors never called by
any test. One-line assertions each; they also pin the values the FPR formula consumes.

**L333 `preemptionHashIndices`, L343 `doubleHash` ×2, L357 `hashCode`** — index arithmetic in
the probe sequence. Same category as Gap 2's hash mutants: a mutation degrades selectivity
and FPR rather than correctness. **Likely equivalent** for verdict purposes. Document, and
cover only via the FPR property test if cheap.

---

## Gap 4 — `ContextBoundedExplorer` trace emission (MEDIUM)

**20 mutants: 14 survived, 6 no-coverage. B.1/B.2 were aimed at 10 survivors + 6 no-coverage
(the `explore`/`dfs` core). The remaining 4 survivors sit in reporting logic that no behavioral
test constrains.**

| line | method | mutators | assessment |
|---|---|---|---|
| L203 | lambda | `NonVoidMethodCall` | the `anyMatch` — dropping the DEADLOCK check |
| L203 | lambda `$0` | `NonVoidMethodCall` | the `== TraceOutcome.VIOLATION` comparison |
| L204 | lambda `$0` | `NonVoidMethodCall` | ditto, `DEADLOCK` |
| L214 | `addTrace` | `VoidMethodCall` | **drops `stateVisitor.onTraceCreated(trace)`** |
| L41, L176 | `explore`, `dfs` | 6 no-coverage | were owned by B.1/B.2; see the Part B table |

This block implements "if the budget was exceeded but a real failure was found, do not
report `INCOMPLETE`." The logic is: a run that found a violation suppresses the incomplete
trace, because the pruned region is irrelevant once there's something to reproduce.

**L214 is the one to test first.** Dropping the `onTraceCreated` callback means a caller
using the visitor interface silently never learns about traces — while `traces` is still
populated, so a test asserting on `getTraces()` passes. This is precisely the
false-confidence shape the plan's Part B preamble warns about. Test it via the
`StateVisitor` path, not the returned list.

**L203–L204** are a suppression rule (`emitIncompleteTraceIfNeeded`: if a real failure was
found, do not also report `INCOMPLETE`, because the pruned region is irrelevant once there is
something to reproduce). Test: on a program that both exceeds the budget *and* finds a
violation, assert no `INCOMPLETE` trace is reported. That is directly checkable and not
implied by B.1 (which asserts verdict *equality* with DFS, not the absence of a particular
trace kind).

**L41 / L176 no-coverage** — `explore` entry and a `dfs` helper line that no test reached when
this was written. Listed in the Part B table above because B.1/B.2 exercise the same code path.
`dfs` inside `ContextBoundedExplorer` is the fallback to exhaustive search when the context index
fails; if it is genuinely unreachable, note it. If it is reachable only under a condition no corpus
program triggers, **construct a program that triggers it** rather than leaving it dark.

### Falsification check

Remove the `onTraceCreated` call at L214. Confirm the visitor test goes red while a
`getTraces()`-based assertion stays green — that difference is the point of the test.

---

## Gap 5 — `HashingStateStore.freshCopy` null return (fold into Gap 2)

L123 `NullReturnVals` is no-coverage. A `null` store propagates into the explorer and NPEs
far from the cause. Covered by the Gap 2 `freshCopy` item.

---

## Gap 6 — The corpus cannot expose cost-aware budget bugs (HIGH — new)

**Not a mutant. A measured blind spot in the Part B suite.**

Part B (B.1 differential, B.2 monotonicity, B.3 store equivalence) landed green as PR #29. Six
defects were then injected into `ContextBoundedExplorer` one at a time, and **three of them passed
every test**. All three corrupt the same mechanism — the cost-aware visited index — in different
directions.

| # | Injected defect | Direction | Caught? |
|---|---|---|---|
| 1 | Undercount preemptions to 0 on a paid switch | explores too much | yes — 4 tests |
| 2 | Charge continuations as preemptions | over-prunes, exhausts budget early | yes — 1 test |
| 3 | Prune on `config` alone, ignoring `lastThreadId` and budget | over-prunes | yes — 1 test |
| 4 | Budget-blind visited key (`markVisited(config, tid, -1)`) | over-prunes | **no** |
| 5 | Record `currentPreemptions + 1` (inverted dominance) | over-prunes | **no** |
| 6 | Always record zero cost | over-prunes | **no** |

### Why three survived

Verdicts stayed correct in every miss. With 3–12 steps per thread, the corpus produces too few
distinct `(config, lastThreadId, preemption)` triples for wrong dominance to drop a state that
was the only route to a violation. Measured under defect 6:

```
peterson                 CBS=59  DFS=42
broken-peterson          CBS=60  DFS=46
deadlock                 CBS=19  DFS=15
torn-counter             CBS=8   DFS=8     <-- CBS no longer above DFS, and no test noticed
```

State counts fell in every case. **No existing test asserts an absolute state count**, so a pruner
that silently explores 12% fewer states passes B.1 (verdicts agree), B.2 (still monotonic), and
B.3 (store contract unaffected). The suite is structurally incapable of detecting this class.

### What this is not

Not a claim that Part B is worthless — defects 1–3 show it has real teeth, and it is the first
thing in the project that constrains CBS against DFS at all. The gap is specifically that
**dominance-direction bugs are invisible on this corpus**, and they are exactly the PR #26 defect
class (`isVisited` semantics differing between implementations) that B.3 was written to target.

### Work

1. **Add a corpus program where a violation is reachable only at higher budget.** This is the
   real fix and it is a new corpus entry, not a new test.

   The fixture must force this sequence at one shared `(config, lastThreadId)`:

   1. an **expensive** arrival — the state is first marked at preemption count `q_high`;
   2. later in the same search, a **cheaper** arrival — the same `(config, lastThreadId)` is reached
      again at preemption count `q_low < q_high`, and the violation lies beyond it, reachable only
      because the extra budget remaining on the cheap path has not been spent.

   An incorrect dominance check that prunes on *any* prior arrival rather than *only when the prior
   arrival was at least as cheap* discards the cheap arrival, and the violation is never found.
   That is the defect the corpus entry has to expose.

   Note the direction: it is the **later, cheaper** arrival that gets dropped, and it gets dropped
   because an **earlier, more expensive** one is on record. Do not conflate this with remaining
   program steps — the key is the *recorded preemption count*, which is a property of the path
   taken, not of how many steps of the program remain. Getting the direction backwards produces a
   fixture that does not exercise the bug.

   Re-run the six injected defects after adding it; defects 4–6 must go red.
2. **Add a regression guard for state counts** as a secondary net. Absolute counts are brittle
   against legitimate optimisation, so assert a *band* rather than equality — e.g. CBS at a
   non-binding bound must reach at least the count it reached when this register was written.
   Record the baseline numbers alongside, and treat a drop as a finding to explain, not a number
   to update.
3. **Re-run all six defect injections as a standing check.** They are cheap and they are the only
   thing that measures whether the suite can still detect dominance bugs. Consider folding them
   into `CbsMonotonicityTest` as a mutation-testing-of-the-test-suite exercise if the mechanics
   hold up.

### Do not

Do not "fix" this by tightening B.1 to assert absolute state counts against the current numbers.
That would pin the implementation rather than the property, and the moment someone legitimately
improves the visited key every such assertion breaks. Build the corpus program (item 1); treat the
count guard (item 2) as a tripwire only.

---

## Gap 7 — CI will not notice a silently corrupted baseline (INFRASTRUCTURE)

**Not a mutant. A property of the CI gate that decides whether this register stays meaningful.**

PR #28 added a `mutation` job that runs PIT and publishes the report. **It gates nothing** —
all three thresholds are `0`, by deliberate design so the baseline could be measured first. That
was the right call for a measurement PR. It is now a debt, and this register is the other half
of it: these 54 gaps can only be tracked if something detects when they grow.

### The wall-time kill hazard — live today

**The hazard is not hypothetical and does not wait for a plugin bump.** PIT counts a mutant as
*detected* (i.e. killed) on wall time alone. Verified by reading `DetectionStatus`'s static
initialiser in `pitest-1.30.0.jar` — each constant is built as `(name, ordinal, detected)`:

| status | detected | earned by |
|---|---|---|
| `KILLED` | yes | a failing assertion |
| `TIMED_OUT` | **yes** | the test exceeding `timeoutFactor`/`timeoutConstInMillis` |
| `MEMORY_ERROR` | **yes** | the mutant exhausting the minion heap |
| `NON_VIABLE` | **yes** | PIT rejecting the mutant at compile time |
| `RUN_ERROR` | **yes** | the mutant failing to run at all |
| `EQUIVALENT` | **yes** | PIT's own equivalence check |
| `SURVIVED` | no | — |
| `NO_COVERAGE` | no | — |

`build.gradle.kts` already sets `timeoutFactor = 1.5` and `timeoutConstInMillis = 4000`. **So the
wall-time kill path is active right now.** Any mutant that makes a covering test hang past that
threshold is scored as killed, and the mutation score rises with kills no assertion produced. For a
model checker this is the wrong trade: a mutant that merely breaks deduplication makes exploration
exponentially slower, so it is *precisely* the mutants worth catching that are most likely to be
killed on time rather than on an assertion.

The baseline run is clean on this axis — 275 mutants came back `KILLED` 221 / `SURVIVED` 40 /
`NO_COVERAGE` 14, which accounts for all 275 and leaves **zero** `TIMED_OUT`, `MEMORY_ERROR`,
`NON_VIABLE`, `RUN_ERROR` or `EQUIVALENT`. None is inflating the number. It has not bitten yet —
that is luck plus a small corpus, not a safeguard.

There is **no second vector.** An earlier draft of this register described a `fasterThreshold`
option that would auto-kill fast mutants on wall time. That option does not exist: the string
`fasterThreshold` appears zero times across `pitest-1.30.0.jar`, `pitest-entry-1.30.0.jar`,
`pitest-command-line-1.30.0.jar` and `pitest-html-report-1.30.0.jar`, and `javap` on the 1.19.0
Gradle extension returns zero matches for it. PIT 1.30.0 exposes exactly two timeout controls,
`TIMEOUT_CONST` and `TIMEOUT_FACTOR`, and both are already set above. `thresholdPrecision` is
likewise absent from the extension.

### Guard: watch for `TIMED_OUT` in the report, and fix the misleading mitigation

1. **Count non-`KILLED` detected statuses before trusting a score.** `TIMED_OUT` and
   `MEMORY_ERROR` inflating the baseline is a live risk with the thresholds as configured. When
   reading `build/reports/pitest/index.html`, check that wall-time kills are still zero.
2. **Do not fix this by widening the timeout far enough to hide it.** The main plan's risk table
   originally proposed raising the timeout to absorb `MEMORY_ERROR`/`TIMED_OUT`; **that is the
   wrong direction and should be struck.** Raising `timeoutConstInMillis` is correct as a
   one-off response to a specific slow mutant, but it is not a blanket fix — it widens the window
   in which *every* future mutant can be killed on time instead of on an assertion. If a mutant
   times out repeatedly, raise it narrowly and record why.
3. **Treat every non-`KILLED` detected status as a survivor for ratchet purposes** (§A.4). This is
   the real mitigation, and it is orthogonal to the timeout budget.

### What is needed

1. **Pin the baseline number, and assert the metric identity.** Record **80%** mutation coverage /
   275 mutants / 221 killed as the expected figure for the scoped run — the pre-Part-B figure, not
   the post-Part-B 80.7%. PR #29 moved the score by a single mutant, and pinning at 80.7% would
   bank that one mutant as a floor, making any regression elsewhere a "pass" until it cost two.
   80% makes the change score-neutral so future movement is attributable. When a ratchet lands,
   that figure is the contract.
2. **Before setting any non-zero threshold, re-measure.** The floor must come from a
   post-remediation run of *this* register's items 1–4, not from today's 80%.
3. **On every plugin or PIT bump, diff the mutant count before trusting the percentage.** A
   sudden change in *total* (275) with no code change means the mutator set, the scope, or the
   runtime changed — investigate before reading the percentage.
4. **Do not add timeout controls that do not exist.** `fasterThreshold` and `thresholdPrecision`
   are not PIT 1.30.0 or plugin 1.19.0 options. If a future version adds one, treat its arrival as a
   measurement change, not a bugfix.

### The integer-threshold blind spot (already noted, restated for completeness)

This plugin exposes all three thresholds as `Property<Integer>` with no `thresholdPrecision`.
PIT's docs describe an integer blind spot where a metric can regress by almost a full percentage
point without tripping the gate (their example: 61.47% passes a threshold of 61; losing 97
covered lines still passes). Not fixable within this plugin. Practise: when the ratchet lands,
set the floor **below** the measured figure by more than a point, and prefer re-measuring
deliberately over trusting the gate to catch small regressions.

---

## Suggested order

| # | item | effort | why first |
|---|---|---|---|
| 1 | Gap 1 — resolve `equals`, add injectivity + field sensitivity | S | upstream of both stores; no other test can catch it |
| 2 | Gap 4 — `onTraceCreated` (L214) | S | silent wrong-answer path, tests-as-written hide it |
| 3 | Gap 2 — port `clear`/`preemptionEntryCount`/`freshCopy` | S | genuine leak/NPE risks, cheap tests |
| 4 | Gap 3 — FPR closed-form + ctor boundaries | M | user-facing diagnostics |
| 5 | Gaps 2/3 — hash-mixing mutants | S | **document as equivalent; do not test** |
| 6 | Gap 4 — L41/L176 reachability | M | may need a constructed program |
| 7 | **Gap 6 — corpus program that exposes dominance bugs** | **M** | **three of six injected defects pass today; this is the only real fix** |
| 8 | Gap 7 — pin the baseline number before the ratchet | S | nothing detects baseline drift until this exists |

Gaps 6 and 7 are the two that make the rest durable: without Gap 6 nothing detects a pruner that
quietly explores less, and without Gap 7 nothing detects the tool changing underneath the numbers.

---

## What NOT to do

- **Do not weaken B.1–B.3** to make these pass. They are separate tests.
- **Do not contrive tests for equivalent mutants.** Hash-mixing constants and a redundant
  `flush()` are semantically free. Suppress with a documented reason, or accept them as
  survivors — but do not assert on specific hash values or on `flush()` being called.
- **Do not "fix" Gap 6 by pinning absolute state counts in B.1.** That pins the implementation
  rather than the property, and every legitimate optimisation to the visited key would break it.
  Add the corpus program; treat any count guard as a tripwire only.
- **Do not delete production code to raise the score**, except the
  `CanonicalEncoder.equals` decision in Gap 1, which is a genuine dead-code question
  answered on its own merits, not a mutation-score exercise.
- **Do not raise `mutationThreshold` above 80%** until at least items 1–4 are done, and pin the
  measured figure first (Gap 7). The integer-threshold blind spot (no `thresholdPrecision` in
  this plugin) means a threshold set from today's numbers is already imprecise; raise it from a
  post-remediation measurement.
- **Do not treat a `TIMED_OUT` or `MEMORY_ERROR` mutant as a kill.** A wall-time kill is not an
  assertion kill. Raise `timeoutConstInMillis` narrowly so the real assertion gets its chance,
  and count the status as a survivor for ratchet purposes until it does (Gap 7).

---

## Cross-check against the main plan

The main plan's §B.3 states that PR #26's `bitstateStore_metrics_*` tests "pin density and
FPR" and that §B.3 must not duplicate them. **This is wrong** — five mutants survive inside
`estimatedFalsePositiveRate`. Either those tests assert loosely enough to tolerate the
mutants, or they do not reach that method on this code path. Gap 3 covers the repair.

Part B landed as PR #29, so the "re-run and confirm" step has been done; see the next section.

---

## Post-Part-B re-measurement

Re-run after PR #29 (`c5fdcd0`), same config, same scope, same 12 mutators:

| | baseline (`313df44`) | now (`c5fdcd0`) | delta |
|---|---|---|---|
| total mutants | 275 | 275 | 0 |
| `KILLED` | 221 | 222 | **+1** |
| `SURVIVED` | 40 | 39 | −1 |
| `NO_COVERAGE` | 14 | 14 | 0 |
| mutation coverage | 80.0% | 80.7% | +0.7pp |

Zero `TIMED_OUT`/`MEMORY_ERROR`/`NON_VIABLE`/`RUN_ERROR`/`EQUIVALENT`, so nothing is inflating
the count — Gap 7's guard still passes.

### The uncomfortable result

**Part B moved the mutation score by one mutant.** Of the 17 mutants listed in the Part B table
above as owned by B.1–B.3, **7 are now killed and 10 are still unconstrained**:

| target | baseline | now |
|---|---|---|
| `ContextBoundedExplorer` L82, L87, L88 | 3 survived | **3 still survived** |
| `ContextBoundedExplorer` L113 ×5 | 5 survived | **5 still survived** |
| `ContextBoundedExplorer` L109 | survived | **killed** ← the +1 |
| `ContextBoundedExplorer` L187 | survived | still survived (1 of 4) |
| `ContextBoundedExplorer` L41 ×2, L176 ×4 | no-coverage | **6 still no-coverage** |
| `HashingStateStore` L39 | survived | still survived |

That is a **real finding, not a ratchet failure**, and it is consistent with Gap 6 below — which
already showed by defect injection that the corpus cannot expose cost-aware dominance bugs.
The differential and monotonicity tests pin the *verdict* property, which is why they are
valuable; they mostly do not constrain the *pruning decisions* that the surviving mutants
represent. Treat the mutant count as an underestimate of what Part B delivered, and do not
read "80.7%" as "the suite got stronger" — it got one mutant stronger.

The practical consequence for ratcheting: **do not set the floor at 80.7%.** Set it at 80% — the
pre-Part-B figure — so this change is score-neutral and any future movement is attributable.
A ratchet that requires the +1 risks rewarding regression for the single mutant it banks.

### Gaps this re-measurement confirms are untouched

Gaps 1–5 were written against the baseline and nothing in PR #29 aimed at them. Re-measurement
confirms they are all still open, with one caveat worth recording:

- **Gap 1 (`CanonicalEncoder`)** — L24 still 5 no-coverage, L16 still survived. `equals(a, b)`
  remains uncalled and untested.
- **Gap 3 (`BitstateStore`)** — L298, L300 ×2, L301 ×2 still survive inside
  `estimatedFalsePositiveRate`; L201 and L210 still no-coverage. **This is now the highest-value
  unstarted item**, because it is user-facing diagnostic output, it is reachable by ordinary
  test code, and unlike Gap 6 it needs no new corpus program to fix.
- **Gap 4 (trace emission)** — L214 `addTrace` still survived, L203/L204 still survived. Untouched
  as expected.
- **Gap 5** — `HashingStateStore` L123 `freshCopy` null return still no-coverage.

The caveat: `HashingStateStore` L52, L54, L98, L102, L103, L104 and L114 also survive (13
survivors in that class, not the 1 the Part B table claimed). Those were outside Part B's scope
and are not attributed to any gap above. They are hash-arithmetic and equivalent-mutant
candidates, grouped under Gap 2 — worth confirming individually before assuming equivalence.
