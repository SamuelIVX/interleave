# Deferred Register — Spec Set 12 (Mutation Hardening)

Problems found while implementing specs 12.01–12.07 that were **deliberately not fixed**, either
because they fell outside the owning spec's scope or because the fix belongs to a different spec.

This is not a spec and has no acceptance criteria. It exists so that nothing deferred is quietly
forgotten, and so the next person does not have to re-derive a finding from scratch. Every entry
records what was found, the evidence, why it was deferred, what it costs if left, and what closing
it would take.

**Status as of 2026-10-03**, after 12.07, 12.01, 12.02, 12.05, and 12.03 landed.
Refs are `file:line` against `main` at `92b58b6`.

## Summary

| ID | Issue | Kind | Severity | Owner |
|---|---|---|---|---|
| [A1](#a1) | `ModelThread.pc` is dead; `enabled()`/`terminated()` are correct only at pc 0 | dead code + trap | **med** | unassigned |
| [A2](#a2) | `DynamicState.encodeTo` omits `decl` and `threadCount` | latent defect | **med** | specs 09/10 |
| [A3](#a3) | `StateRegistry` hardcodes exactly 2 flags for `peterson`/`deadlock` | limitation | low | unassigned |
| [A4](#a4) | Sound `StaticPorExplorer` fix disables the reduction whenever an invariant is given | perf regression | low | unassigned |
| [B1](#b1) | `SharedState.toString()` is not value-based — `DclState.instance` prints identity hash | latent trap | **med** | unassigned |
| [B2](#b2) | `Configuration` has neither `equals` nor `hashCode` | design debt | low | unassigned |
| ~~[C1](#c1)~~ | ~~R7 (no hash assertions) forbids the only way to kill 4 `BitstateStore` mutants~~ | **closed — premise was false** | — | closed 2026-10-03 |
| [D1](#d1) | Building a `Configuration` fixture requires reflection | test tax | low | unassigned |
| [D2](#d2) | No shared procedure for deriving expected values of numeric formulas | test tax | low | unassigned |
| [E1](#e1) | Per-class PIT table drifted for two classes before 12.03 caught it | process | **med** | 12.06 |
| [E2](#e2) | Spec-recorded numbers go stale as sibling specs land | process | **med** | all future specs |
| [E3](#e3) | `CanonicalEncoder`'s equivalent `flush()` mutant has no recorded PIT suppression | accounting | low | 12.06 |
| [E4](#e4) | 9 mutants have no owning spec | accounting | low | unassigned |

Closed during this set, recorded so nobody re-investigates: [G1](#g1)–[G4](#g4).

---

## A. Production defects and dead code

### A1
`ModelThread.pc` is dead, and `enabled()`/`terminated()` are correct only when every counter is 0
{: #a1}

**What.** `ModelThread` carries a `private int pc` (`ModelThread.java:9`) that nothing ever advances.
`advance()` (`ModelThread.java:44`) and `pc()` (`ModelThread.java:22`) have **zero callers** in
`src/main` or `src/test`. The live consumers are `enabled()` (`:39`) and `terminated()` (`:31`), and
both read `pc`.

**Evidence.** `Configuration.initial()` is the sole caller of `ModelThread.enabled()`
(`Configuration.java:45`), and it is safe there only because it seeds every counter to 0
(`Configuration.java:39`, `Collections.nCopies(threads.size(), 0)`). `Configuration.successor()` does
*not* use `enabled()` — it indexes `t.steps().get(currentPc)` from the configuration's own counters,
which is correct.

**Why deferred.** No defect ships. Production takes the correct path at both call sites, so this is
not a bug fix — it is dead-code removal with a trap attached.

**Cost if left.** The trap is real and it was sprung during this set. `ModelThread.enabled()` reads
the *thread's* counter while the search selects steps from `Configuration.programCounters()`. My 12.02
corpus helper used `ModelThread.enabled()` and so walked a synthetic transition — the shared state
never advanced — while its test name and Javadoc claimed it walked "every reachable configuration."
The test passed. It was caught only because a review bot read the code; nothing else would have.

Any future test, DSL tool, or debugging aid that queries `ModelThread.enabled()` or `pc()` outside
`Configuration.initial()` gets step 0's answer regardless of real progress, and gets it *silently*.

**To close.** Either delete `pc`, `advance()`, `pc()`, `enabled()` and `terminated()`, and have
`Configuration.initial()` read `steps().get(0)` directly; or change the accessors to take the
configuration's counter. Either is a small, self-contained change that wants its own short spec — it
touches `core`, and every 12.x spec scoped itself out of `core`.

### A2
`DynamicState.encodeTo` omits `decl` and `threadCount`
{: #a2}

**What.** Two fields are absent from the encoding, so two states differing only in them encode
identically.

**Why unreachable today.** A store never spans two programs — `InterleaveRunner` builds a fresh store
per run (`InterleaveRunner.java:59–63`) and `BenchmarkHarness` gets one per strategy per program
(`BenchmarkHarness.java:163–188`).

**Why deferred.** Spec 12.07 §R4 scoped it out; the fix belongs with the DSL specs that own
`DynamicState`'s encoding (recorded as deferred to specs 09/10 in 12.07).

**Cost if left.** Silent wrong answers the moment a store spans programs. The masking reason is
*structural*, not incidental, so it will not surface on its own — and when it does, it will present as
a search missing states rather than as an encoding bug. 12.07 landed the identical class of fix for
`DeadlockState`; leaving `DynamicState` half-done is the kind of asymmetry that gets forgotten.

**To close.** Fold into specs 09/10, or promote to its own spec now while the context is fresh.

### A3
`StateRegistry` hardcodes exactly 2 flags for `peterson` and `deadlock`
{: #a3}

**What.** `StateRegistry.java:34` and `:58` both reject any `flags` array whose length is not 2, with
the message *"must have exactly 2 elements"*.

**Why deferred.** Found while writing 12.02's follow-ups. **No spec owns this** — it is DSL surface,
and every 12.x spec scoped itself to `state`, `search`, or build config.

**Cost if left.** 3+ thread programs are impossible for these state types through the declarative
format, and the built-in corpus tops out at two threads. At least the failure is loud. This is a
capability ceiling, not a correctness risk.

**To close.** Size the flag arrays from a declared thread count rather than a literal 2. Belongs with
the DSL specs alongside A2.

### A4
The sound `StaticPorExplorer` fix gives up the speedup exactly when invariants are in play
{: #a4}

**What.** 12.07 found that static POR could return a **false pass** when given an invariant, and fixed
it by applying `DporExplorer`'s existing guard: supplying an invariant disables the reduction. That is
sound, and it forfeits the reduction precisely when an invariant is being checked.

**Why deferred.** The real fix is a computed Godefroid `source` set, which keeps the reduction sound.
The 12.x set records this as *"a genuine algorithm with its own spec, not a patch"* — correctly, since
a `source`-set computation is substantially larger than a guard.

**Cost if left.** Performance only. Correctness is fine and now regression-tested by three tests that
fail if the guard is removed.

**To close.** Its own spec. Do not fold it into 12.06 — it changes `search/`, which 12.06 declares
off-limits.

---

## B. Latent correctness traps

### B1
`SharedState.toString()` is not a value-based rendering
{: #b1}

**What.** `DclState.instance` holds a bare `Object` (`DclState.java:9`), so `DclState.toString()`
(`:119`) prints its identity hash, which changes on every `deepCopy`. Two configurations the store
correctly treats as one state therefore render differently.

**Measured.** On `double-checked-locking`: **23 reachable configurations, 17 distinct encodings**, six
pairs colliding by string while the store's encoding correctly merges them.

**The encoder is *not* at fault.** `DclState.encodeTo` writes all six fields, and `instance` is in its
domain either null or set, so a presence flag is lossless.

**Why deferred.** Nothing in production depends on `SharedState.toString()` being value-based. The
defect is in a diagnostic rendering.

**Cost if left.** Silent duplicate detection for any consumer that keys on it. The failure mode is
confusing rather than obvious: a legitimately-visited duplicate makes an "unvisited before marking"
assertion fail, which reads like a store bug rather than a keying bug. That is exactly what happened
during 12.02, and it cost real time to trace back to its cause.

**To close.** Either render `instance` as presence rather than identity, or state explicitly in the
`SharedState` contract that `toString()` is diagnostic-only and must never be used as a map key. The
second is cheaper and arguably more correct — the first papers over the general problem.

### B2
`Configuration` has neither `equals` nor `hashCode`
{: #b2}

**What.** Identity semantics. Confirmed: neither method is declared on `Configuration`.

**Why deferred.** Out of scope for every 12.x spec. 12.01 deleted `CanonicalEncoder.equals`, a
different class, and rightly declined to expand from there.

**Cost if left.** Every consumer needing value identity hand-rolls a key, and **three incompatible
derivations now exist**: the explorer's `state.toString() + "|" + programCounters`, `HashingStateStore`'s
private base64 `encode`, and 12.03's probe-store `isVisited`/`markVisited` round trip. They agree
today. Nothing enforces that they will continue to, and B1 shows how quietly a string-based one can
diverge.

**To close.** Add value `equals`/`hashCode`, or — lower risk — promote one key to a single public
method and point all three call sites at it. The second also retires B1 as a hazard.

---

## C. Unresolved rule conflicts — needs a decision, not just work

### C1
~~R7 forbids the only available way to kill four `BitstateStore` mutants~~ — **closed, premise falsified**
{: #c1}

**Resolution (2026-10-03).** Closed without a ruling, because the dilemma it described did not exist.
**R7 was never the blocker.** `lossyBitstateFilter_stillSurfacesTheDeadlock` kills two of the four at
the observation level, asserting only on a reported outcome through the public seam — no hash value
appears anywhere in it. The store simply has to be exercised at a size where collisions actually
occur; 64 bits does that, and the 1,000,003-bit default never does on a corpus this small, which is
why every earlier attempt failed. Option 2 below was therefore never needed.

The three still `SURVIVED` are a different problem, and an easier one: across **1,080 measured
configurations** (7 corpus programs × 10 sizes × 6 hash counts × 2 bounds) they change **no reported
verdict at all**. They are unobservable, not forbidden. PIT agrees independently at 82 covering tests
each. They stay in the denominator as a measured floor; 12.03 §R6 records why, including the two ways
of closing them that were considered and rejected on purpose.

**No decision is needed from 12.06 or Sam.** R7 stands unamended. The record below is kept because the
wrong reasoning is more dangerous than no reasoning — it invited a permanent "rule conflict" that would
have licensed a hash-structure exception nobody needed.

---

<details>
<summary>Original entry, retained because the reasoning was wrong in an instructive way</summary>

**The conflict as originally recorded.** Spec 12.03 leaves `L333`, `L343` (×2) and `L357` `SURVIVED`.
These are the `doubleHash` index arithmetic in `BitstateStore`. Spec 12.03's R7 — inherited from 12.02
— forbids any assertion referencing a hash value. Killing those four requires constructing a pair that
collides under the mutant but not the original, which is *precisely* a hash-structure claim.

**The 12.02 verdict does not transfer, and this is the part worth being careful about.** 12.02
adjudicated nine `HashingStateStore` survivors *equivalent*, with a proof: the hash feeds only the
prefilter, and both `isVisited` overloads confirm against the exact encoding, so no hash change can
alter any answer. `BitstateStore` has **no exact confirmation layer at all** — no `contains`, no
`encode` outside the hashing itself, because the bitset *is* the answer. So a different hash genuinely
produces different answers. These four are real, reachable behaviour changes, not equivalent mutants.

**Why deferred.** Resolving it means choosing between two rules this set currently holds at once.
That is a judgement about what the suite is for, not an implementation detail, so it is recorded
rather than decided unilaterally.

**Options.**
1. Keep R7 absolute. Accept 4 as a documented floor — the status quo, and defensible: the store is
   lossy by design, so pinning *which* configurations collide would pin an implementation detail.
2. Permit exactly one narrowly-scoped, explicitly-marked hash-structure assertion for this purpose,
   and narrow R7 to "no assertion may reference a hash value **except** where the fact under test *is*
   the hash function."
3. Kill them structurally — assert that `size()` and `k` are read correctly and that the index
   sequence has the right *shape* (k indices, all in `[0, size)`) without naming any value. Weaker, and
   I have not verified it kills all four.

**Recommendation (superseded).** Option 2, scoped tightly. *Wrong*: the tension was neither real nor
permanent, and the suite was never unable to test its own hash — it just had to test it through the
seam rather than through the hash.

</details>

---

## D. Test-infrastructure debt

### D1
Building a `Configuration` fixture requires reflection
{: #d1}

**What.** `Configuration` exposes only `initial` and `successor`. Neither can place a program counter
at an arbitrary value without executing a program, so both 12.02 and 12.03 construct fixtures through
the private constructor by reflection.

**Cost if left.** Two specs paid this tax independently and wrote near-identical helpers. It also
means fixture bugs surface as `ReflectiveOperationException` rather than as clear assertion failures.

**To close.** A test-visible factory taking explicit counters, or letting `Configuration.initial`
accept them. Small, and it would remove reflection from future specs' scope discussions entirely.

### D2
No shared procedure for deriving expected values of numeric formulas
{: #d2}

**What.** 12.03 needed four expected FPR doubles and had to establish, from scratch, that literals
must be derived independently of the implementation. It also has to be stated that
`Math.pow(1 - Math.exp(...))` inside the test validates nothing — it reproduces the implementation
line for line.

**Why deferred.** Out of scope for 12.03, whose deliverable is the tests.

**Cost if left.** The next numeric assertion repeats the derivation, or worse, skips it and writes a
tautology. 12.03 records the approach in its own spec; nothing makes it discoverable from the next
spec that needs it.

**To close.** A short note in the test conventions covering: derive outside the language, cross-check
by two routes, assert against literals, and never re-derive with the same expression under test.

---

## E. Spec and process debt

### E1
The per-class PIT table drifted for two classes before 12.03 caught it
{: #e1}

**What.** The 12.x README's per-class table still showed `HashingStateStore` at its pre-12.02 figure
(46/61) after 12.02 had landed and taken it to 52/61. 12.03 found and corrected it, adding a
`moved by` column.

**Why it matters beyond the two wrong cells.** `CanonicalEncoder` moved by *shrinking its
denominator*; `HashingStateStore` and `BitstateStore` moved by *killing mutants*. Those are different
claims and a single percentage column conflates them. The new column separates them.

**To close.** Treat the table as generated from `build/reports/pitest/mutations.xml` at ratchet time,
rather than hand-maintained per spec landing.

### E2
Spec-recorded numbers go stale as sibling specs land
{: #e2}

**What.** Each spec records mutation counts measured against the tree as it stood. Any sibling spec
that changes a target's denominator or its reachable set invalidates them. 12.01's `NO_COVERAGE` fell
14 → 9; 12.07 changed `DeadlockState`'s encoding format; both moved numbers other specs had recorded.

**Checked and clear.** 12.07 explicitly warned that *"the `bitstate` metrics in Spec 12.03 depend on
`statesMarked` and bit positions, so 12.03's falsification numbers are the most likely to need
re-deriving."* Verified not to apply: 12.03's fixtures use `CounterState` exclusively, so the
`DeadlockState` format change cannot affect its literals or bit positions. Recorded here so the next
reader does not re-derive the check.

**To close.** Have each spec state which siblings can invalidate it, and re-measure rather than trust
when in doubt. The general rule worth writing down: **a spec's numbers are valid only against the tree
it names.**

### E3
`CanonicalEncoder`'s equivalent `flush()` mutant has no recorded PIT suppression
{: #e3}

**What.** Deleting `out.flush()` leaves the whole suite green, so the mutant is equivalent — but no
mechanical suppression exists, so PIT reports it `SURVIVED` with no machine-readable reason. Both 12.01
and 12.06 record deferring the filter.

**Cost if left.** The ratchet sees an unexplained survivor, and the reasoning lives only in prose.

**To close.** 12.06, which owns the denominator accounting. Deliberately not added mid-set: a filter
changes how excluded mutants count, and doing it early would move the number 12.06 has yet to pin.

### E4
Nine mutants have no owning spec
{: #e4}

**What.** 12.05 assigned `L41`'s two `NO_COVERAGE` to 12.04 and recorded that *"the remaining 9 are
unassigned and are a candidate for a future spec."*

**Why it matters.** Unassigned mutants are the ones that drift — they appear in no acceptance criterion,
so no spec's completion implies they were addressed.

**To close.** Assign them, or record an explicit decision that they are below the bar. Either is a
decision; silence is not.

---

## G. Closed during this set

Recorded so nobody re-investigates.

### G1
12.01's `≥ 100` state-value floor — **closed**
{: #g1}

Deferred because the floor was contingent on a corpus program that did not yet exist. It was promoted
as a **stronger** assertion than planned: `CanonicalEncoderContractTest` now asserts `≥ 150` reachable
*positions* against the recorded 160, as a drift tripwire. The implemented assertion is the tripwire;
the original `≥ 100` state-value framing was superseded, and the test comments explicitly separate the
three quantities in play (positions, distinct keys, state values).

### G2
12.03's falsification numbers after 12.07's `DeadlockState` change — **closed, no re-derivation needed**
{: #g2}

See [E2](#e2). Verified: `BitstateStoreDiagnosticsTest` uses `CounterState` only.

### G3
`BitstateStore`'s 9 named mutants — **closed**
{: #g3}

84/97 (86.6%) → 93/97 (95.9%), `NO_COVERAGE` 2 → 0. All seven lines fully killed; the nine mutants
killed are exactly the nine the spec named. Falsification demonstrated per mutant with the production
file restored byte-identical. The 4 survivors left by this entry were covered by [C1](#c1), which
has since been closed: one was killed at the observation level and 3 remain as a measured floor.

### G4
`HashingStateStore`'s 9 survivors — **adjudicated equivalent, deliberately left `SURVIVED`**
{: #g4}

Proven equivalent with a written enumeration of readers: the hash feeds only the prefilter, and both
`isVisited` overloads confirm against the exact encoding. Left `SURVIVED` on purpose so 12.06's ratchet
sees them as a known floor rather than losing them from the denominator. No action needed beyond
12.06 recording the number.