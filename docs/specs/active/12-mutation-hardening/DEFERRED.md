# Deferred Register — Spec Set 12 (Mutation Hardening)

Problems found while implementing specs 12.01–12.07 that were **deliberately not fixed**, either
because they fell outside the owning spec's scope or because the fix belongs to a different spec.

This is not a spec and has no acceptance criteria. It exists so that nothing deferred is quietly
forgotten, and so the next person does not have to re-derive a finding from scratch. Every entry
records what was found, the evidence, why it was deferred, what it costs if left, and what closing
it would take.

**Original evidence as of 2026-10-03**, after 12.07, 12.01, 12.02, 12.05, 12.03, and 12.04 landed.
Refs are `file:line` against `main` at `ba3fe01`. Every ref below was re-opened and re-verified at
that commit rather than trusted from the previous pin; the base label was stale, the refs were not.

## Summary

| ID | Issue | Kind | Severity | Status / owner |
|---|---|---|---|---|
| ~~[A1](#a1)~~ | `ModelThread.pc` is dead; `enabled()`/`terminated()` are correct only at pc 0 | dead code + trap | — | closed by 13.02 |
| ~~[A2](#a2)~~ | `DynamicState.encodeTo` omits `decl` and `threadCount` | latent defect | — | closed by 13.05 |
| ~~[A3](#a3)~~ | `StateRegistry` hardcodes exactly 2 flags for `peterson`/`deadlock` | limitation | — | closed by 13.06 |
| [A4](#a4) | Sound `StaticPorExplorer` fix disables the reduction whenever an invariant is given | perf regression | low | unassigned |
| ~~[B1](#b1)~~ | `SharedState.toString()` is diagnostic-only | **closed — explicit contract + canonical keys** | — | closed by 13.09 |
| ~~[B2](#b2)~~ | `Configuration` needs shared value identity | **closed — public canonical key API** | — | closed by 13.09 |
| ~~[C1](#c1)~~ | ~~R7 (no hash assertions) forbids the only way to kill 4 `BitstateStore` mutants~~ | **closed — premise was false** | — | closed 2026-10-03 |
| ~~[D1](#d1)~~ | Building a `Configuration` fixture requires reflection | test tax | — | closed by 13.01 |
| ~~[D2](#d2)~~ | No shared procedure for deriving expected values of numeric formulas | test tax | — | closed by 13.07 |
| ~~[E1](#e1)~~ | Per-class PIT table drifted for two classes before 12.03 caught it | process | — | closed by 13.07 |
| ~~[E2](#e2)~~ | Spec-recorded numbers go stale as sibling specs land | process | — | closed by 13.07 |
| ~~[E3](#e3)~~ | ~~`CanonicalEncoder`'s equivalent `flush()` mutant has no recorded PIT suppression~~ | **closed — reason now machine-readable** | — | closed 2026-10-03 |
| ~~[E4](#e4)~~ | 1 mutant has no owning spec — `ContextBoundedExplorer` L187 | accounting | — | closed by 13.04; subject removed in 13.03 |
| [E5](#e5) | Ratchet can fail CI on a wall-clock timeout indistinguishable from a regression | process | **med** | first CI run of the 12.06 gate |

The entry evidence below is historical unless its status says otherwise. **Still open:** A4 and E5.
Spec 13.09 closes B1/B2 with a shared key API and diagnostic-only rendering contract. Spec 13.03
originally documented the hazards and consolidated one predicate; it did not change
`DclState.toString()` or add a canonical value-identity API. C1 and E3 were already closed in set 12.

Closed during set 12, recorded so nobody re-investigates: [G1](#g1)–[G4](#g4).

---

## A. Production defects and dead code

### A1
`ModelThread.pc` is dead, and `enabled()`/`terminated()` are correct only when every counter is 0
{: #a1}

**Status: closed by 13.02.** The dead counter and its accessors were removed. `Configuration`
derives enabled and terminated facts from its own counters through one shared implementation.

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

**Status: closed by 13.05.** `DynamicState.encodeTo` now writes `threadCount` and the complete
declaration, including names and initializers. `StateEncodingFidelityTest` covers their encoding
parity with equality and records no remaining tracked gaps.

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

**Status: closed by 13.06.** `StateFactory.create` requires the declared thread count;
`peterson`, `deadlock` and `counter` size their per-thread arrays from it. Three-thread tests cover
construction, copying and encoding. The built-in Peterson mutual-exclusion invariant remains
two-thread-only; widening that invariant is outside this closure.

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

**Status: still open. 13.08 is a design note, not an implementation.** Adding the note's
`source_pair` (dependent on every other enabled thread) to the current computed set is a no-op,
including its singleton and nonempty fallbacks. That restricted proof says nothing about a future
path-level analysis. A4 needs both a persistence argument and preservation of the supplied invariant;
independent writes alone can skip a state an arbitrary predicate rejects. The earlier claim that
reverse reachability plus a source term necessarily restores all state reachability is withdrawn.
See [13.08](../13-deferred-debt/08-godefroid-source-set.md).

**What.** 12.07 found that static POR could return a **false pass** when given an invariant, and fixed
it by applying `DporExplorer`'s existing guard: supplying an invariant disables the reduction. That is
sound, and it forfeits the reduction precisely when an invariant is being checked.

**Why deferred.** The fix needs a specified, property-preserving reduction, not a pairwise set union.
The 12.x set records this as *"a genuine algorithm with its own spec, not a patch"* — correctly, since
a `source`-set computation is substantially larger than a guard.

**Cost if left.** Performance only. Correctness is fine and now regression-tested by three tests that
fail if the guard is removed.

**To close.** Its own session and spec. Do not fold it into a cleanup set — the payoff is performance,
the risk is reintroducing a false pass. Package note: this changes **`por/`**;
`StaticPorExplorer`, `PersistentSetComputer` and `IndependenceRelation` are all in `por/`.

---

## B. Latent correctness traps

### B1
`SharedState.toString()` is not a value-based rendering
{: #b1}

**Status: closed by [13.09](../13-deferred-debt/09-configuration-value-key.md).** `SharedState` now
explicitly declares `toString()` diagnostic-only. All explorer result maps and the exact store use
`CanonicalEncoder.configurationKey`, preserving CBS last-thread identity. Diagnostic renderings
remain free to change; the historical evidence below explains the hazard.

**What.** `DclState.instance` holds a bare `Object` (`DclState.java:9`), so `DclState.toString()`
(`:119`) prints its identity hash. `deepCopy` preserves the sentinel reference; separate allocation paths
can create different sentinels in value-equal states, so two configurations the store correctly
treats as one state can render differently.

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

**Status: closed by [13.09](../13-deferred-debt/09-configuration-value-key.md).** The public
`CanonicalEncoder.configurationKey` API supplies base and last-thread-aware keys. Explorers, the
exact store, and keying test helpers delegate to it. `Configuration` retains object equality because
it holds mutable state; each returned string snapshots its current value. Independent value-based
test oracles remain independent of the encoder.

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

**Status: closed by 13.01.** It added the `Configuration.forTest` overloads — D1's stated closure
criterion, *"a test-visible factory taking explicit counters"* — and all three fixture helpers now use
them. No test constructs a `Configuration` through reflection. The `setAccessible` calls remaining in
`state` set non-final instance fields on state objects, which is unrelated and has no factory to
delegate to. Note 13.07 initially recorded D1 as still open on the grounds that it was out of *that
item's* scope; out of scope is not undone.

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

**Status: closed by 13.07.** The four-point derivation rule is now in `AGENTS.md`
("Test conventions — numeric expectations") rather than buried in 12.03, where the next spec needing
it would not have looked.

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

**Status: closed by 13.07.** `EXPECTED_PER_CLASS` in `build.gradle.kts` now holds the four
figures beside the gate that computes them, and `mutationRatchet` prints a non-failing NOTICE on any
disagreement. Verified firing and verified silent.

The automatic comparison is XML versus `EXPECTED_PER_CLASS`, not XML versus Markdown. Reviewers
still compare the spec table with the emitted census; the task does not validate table text.

**What.** The 12.x README's per-class table still showed `HashingStateStore` at its pre-12.02 figure
(46/61) after 12.02 had landed and taken it to 52/61. 12.03 found and corrected it, adding a
`moved by` column.

**Why it matters beyond the two wrong cells.** `CanonicalEncoder` moved by *shrinking its
denominator*; `HashingStateStore` and `BitstateStore` moved by *killing mutants*. Those are different
claims and a single percentage column conflates them. The new column separates them.

**To close.** Treat the table as generated from `build/reports/pitest/mutations.xml` at ratchet time,
rather than hand-maintained per spec landing.

**Mitigated by 12.06, not closed.** The `mutationRatchet` task now reads
`build/reports/pitest/mutations.xml` and prints a per-class census on every run, so the authoritative
numbers are emitted by the build rather than transcribed by hand:

```
  per class:
    ContextBoundedExplorer :      103/103
    BitstateStore :               94/97
    CanonicalEncoder :            6/7
    HashingStateStore :           52/61
```

A reviewer now diffs those against the README table instead of re-running PIT by hand, which is what
made this drift invisible for two specs.

**Accepted closure criterion.** A *non-failing* comparison, not a gate and not a generated table.
`EXPECTED_PER_CLASS` holds the four figures beside the gate that computes them, and every run prints
any disagreement, so a hand-maintained figure cannot go quietly stale — which is what happened three
times, twice before 12.03 caught it and once more at 13.03.

Deliberately not a gate. Parsing a Markdown table inside `build.gradle.kts` would couple the build to
a documentation format, and a gate that breaks when someone reflows a table gets switched off rather
than fixed. A legitimate spec change must not turn CI red because a documentation table moved, so this
is a notice — detection, not enforcement. That is the whole trade, and it is the reason the mitigation
is not a failure.

### E2
Spec-recorded numbers go stale as sibling specs land
{: #e2}

**Status: closed by 13.07.** Same mechanism as E1 — the drift of spec-recorded numbers and
the drift of the per-class table are one problem. Spec 12's already-stale `ContextBoundedExplorer`
`104/105` was corrected to `103/103` rather than merely guarded.

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

**Closed by 12.06 — by recording, not by filtering.** The deferral's condition is discharged: 12.06
has pinned the number (256/270, floor 94). It did **not** add a PIT exclusion filter, because that
would drop the denominator to 269 and lift the score to 95.17% without a single additional test —
the exact move R2 exists to prevent. Removing a proven-equivalent mutant is defensible practice in
general; doing it *in the same change that sets the floor* would make the floor unfalsifiable.

Instead `KNOWN_EQUIVALENT_SURVIVORS` in `build.gradle.kts` names the mutant and its 12.01 §R5 reason,
and the ratchet prints the survivor decomposition:

```
  non-kills with a recorded reason : 1
    dev.samhb.interleave.state.CanonicalEncoder:16 — 12.01 R5 — out.flush() removed leaves the full suite green; verified equivalent by experiment, not inferred from PIT's status.
  non-kills with NO recorded reason: 13
```

The mutant still sits in the denominator and still counts against the floor; what changed is that its
reason is machine-readable instead of prose-only, which was this entry's entire stated cost. The
ratchet also warns when a recorded entry stops appearing among the non-kills, so the list cannot rot
silently.

**"Non-kill", not "survivor".** The list covers every status other than `KILLED`, so `NO_COVERAGE`
appears in it too — `NO_COVERAGE` is the other way a mutant fails to be killed, and it stays in the
denominator. An earlier draft excluded it and labelled the output "survivors", which under-reported
the unexercised code the gate exists to surface. Both `SURVIVED` and `NO_COVERAGE` carry
`detected = false` in `DetectionStatus`, and that is the operative test.

**Why not `EQUIVALENT_ALLOW_LIST`.** That mechanism exists for mutants PIT classifies `EQUIVALENT`.
PIT reports this one `SURVIVED` — it cannot prove equivalence, only fail to kill — so the allow-list
never sees it. The two lists are different mechanisms for the same underlying problem, which is why
both exist and why this mutant belongs to neither allow-list alone.

### E4
One mutant has no owning spec — `ContextBoundedExplorer` L187
{: #e4}

**Status: closed by 13.04.** It records the equivalence proof: `ContextBoundedExplorer.dfs`
already returned on `allTerminated()` before reaching this guard. The historical suggestion below
that the removed call could emit deadlock on a completed configuration ignores that earlier return.
Spec 13.03 consolidated the guard into `isDeadlockCandidate()` and removed the mutant's subject;
13.04 therefore records the safe removal rather than adding a survivor-list entry.

**What.** 12.05 assigned `L41`'s two `NO_COVERAGE` to 12.04 and recorded that *"the remaining 9 are
unassigned and are a candidate for a future spec."* Those nine were L176 ×4, L187, L203 ×2, L204 and
L214. 12.04 closed the eight it owned — L176 ×4, L203 ×2, L204 and L214 — leaving **L187**.

12.04 reports ten kills in this class, and the two extra are the separately-assigned `L41` pair,
which were never part of the unassigned nine. Keeping the two pools distinct is the whole point of
this entry: the number that matters here is eight-of-nine, not ten.

**Which mutant.** `ContextBoundedExplorer.java:187`, inside `dfs`, the DEADLOCK guard:

```java
if (enabled.isEmpty() && !config.allTerminated()) {
```

One `SURVIVED` mutant, `NonVoidMethodCallMutator` — *"removed call to
`Configuration::allTerminated`"* — at index 327. PIT reports `numberOfTestsRun='44'` and an empty
`killingTest`: 44 tests exercised the line and none killed it. Verified in
`build/reports/pitest/mutations.xml`.

**What the mutation does.** Removing the call leaves `!allTerminated()` vacuously true, so the
condition collapses to `enabled.isEmpty()` and the DEADLOCK trace is emitted in states where every
thread has already terminated. A completed search would then report a failure it did not find.

**Why it survives: NOT ESTABLISHED.** This is recorded as unknown rather than guessed. The obvious
explanation — that the guard is redundant, since a terminated thread is not enabled — is *not* a
proof: `enabled.isEmpty()` is equally true when threads are blocked rather than finished, and that
is precisely the deadlock case the guard is not there to catch. Distinguishing the two needs a test
that asserts a completed search emits no DEADLOCK trace, and no test asserts that.

The region itself is well covered, which is what makes the gap narrow. The other three mutants on
L187 are `KILLED`: both `NegateConditionalsMutator` variants by
`ContextBoundedExplorerTest.deadlockProgram_needsAPaidPreemption()`, and the
`List::isEmpty` removal by `ContextBoundedExplorerTest.lossyBitstateFilter_stillSurfacesTheDeadlock()`.

**Why it matters.** An unowned mutant is the kind that drifts — it appears in no acceptance
criterion, so no spec's completion implies it was addressed. Left alone it stays in 12.06's
denominator as a survivor with no owner and no recorded reasoning, which is the accounting hole
this entry exists to prevent.

**To close.** Either assert that a run whose threads all terminate emits no DEADLOCK trace — the
obvious candidate, and the most likely reason the mutant survives — or record a proven-equivalence
argument. Either is a decision. Silence is not.

Deliberately **not** claimed equivalent in the meantime: an unmeasured equivalence argument is how
this set produced three separate defects during 12.04, each an asserted mechanism that turned out
false when checked.

### E5
The ratchet can fail CI on a wall-clock timeout it cannot distinguish from a real regression
{: #e5}

**Status: still open, deliberately.** Its closure condition is accumulated evidence from
repeated *parallel* CI runs, and every local run is single-threaded by mandate — so no local run can
satisfy it. See 13.07 for the adjudication procedure and for why the timeout budget must not be loosened
pre-emptively.

**What.** 12.06 makes the build **fail** on any `TIMED_OUT` or `MEMORY_ERROR` mutant. That is R6's
intent — a mutant bought with wall time is not a kill — but it converts a previously reporting-only
signal into a hard gate, and the population most likely to trigger it is nondeterministic.

**Why CI specifically.** `InterleaveRunnerTest` drives `maxTime(1ms)` and `maxTime(30s)`, so the
covering set contains wall-clock-sensitive tests. Locally every PIT run is single-threaded, because
AGENTS.md mandates `--max-workers=1 -PpitestThreads=1` on a 10-core machine. **CI runs parallel**,
deliberately — runners are ephemeral and have no other load. So the load profile that would produce a
spurious `TIMED_OUT` is the one profile this gate has never been run under. Every local measurement in
this set, including the 256/270 floor itself, is single-threaded and therefore cannot speak to it.
**Those figures are historical**: 270 total / 256 killed was the population at `d14680d`, before 13.03
deleted two mutants — one killed and one survived, since 256/255 and 14/13 both moved. The 13.03 figures were 268 / 255, and the reasoning below is
unaffected — the floor's value was never the point, its provenance was.

**Measured.** One CI run at `d14680d`, 4m58s, full scope, default parallel PIT: **green.** 270
mutants, 256 killed, `TIMED_OUT 0`, `MEMORY_ERROR 0`, `NON_VIABLE 0`, `RUN_ERROR 0`, and the ratchet
itself ran and passed on the full-scope floor. So the risk that motivated this entry did not
materialise on its first outing, and the 256/270 floor is no longer single-threaded-only evidence.

One run is one run. It does not retire the risk — a wall-clock-sensitive mutant can pass under load
and fail under load, and CI runner load varies. What it does mean is that the entry is no longer
prediction-only, and that whoever hits a red `mutation` job now knows a clean run existed on the same
code shape. Note also that this run was *parallel by accident of configuration*: the job is named
`Mutation (scoped)` but passes no `-PpitestTargetOverride`, so it was always the full scope. Renamed
to `Mutation` in this pass; had it genuinely been scoped, this entry would still say "nothing".

**To close.** E5 closes when the full-scope parallel gate has been green across enough runs that a
single timeout is more plausibly a regression than a flake — or, more honestly, when it is judged not
worth further tracking. Not closed on one run. If a timeout does appear, adjudicate before changing
anything: re-run that single mutant scoped and single-threaded, and decide whether it is genuinely
slow or load-induced. Do **not** pre-emptively loosen the budget to avoid a hypothetical red build —
that trades a known, documented failure mode for an invisible one.

**Cost if left.** A red `mutation` job whose cause is a timing artefact rather than a regression. The
diagnosis is not obvious from the failure text, which is a deliberate choice for readability — it
does not say "this may be load-induced". Worse, the tempting response is the one R6 explicitly
forbids: raising `timeoutConstInMillis` as a blanket policy, which widens the window in which every
future mutant can be bought with time. The correct response is per-mutant.

**Deliberately not mitigated in 12.06.** Adding a retry or a "known-flaky timeout" allowance would
have been easy and would defeat the gate: R6 exists precisely because a timeout is not a kill, and
an allowance is a hole with a delay. Recorded rather than built.

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
