# Spec 12.07 — `SharedState` Encoding Fidelity

## TL;DR

Found while implementing Spec 12.01: **`DeadlockState.encodeTo` omits the `control` field**, so two
states differing only in `control` encode to identical bytes. The class contradicts itself — `equals`,
`hashCode`, and `deepCopy` all treat `control` as part of state identity.

The severity is **latent, not active**, and the reason is worth stating precisely because it is the
difference between a real bug and a real risk. Both stores mix program counters into their keys, so
the two colliding states occur at *different* counters and the full store key still differs. Nothing
is wrong with today's verdicts. The defect becomes live the moment two configurations share program
counters while differing only in `control`, or any consumer relies on `encode()` alone.

A full audit of all six `SharedState` implementations found **one** confirmed defect and **one**
latent gap; the other four are correct, and `CounterState` even writes its array length to prevent
length confusion. So this is an outlier, not a systemic problem.

This spec fixes the defect, and — more importantly — adds the guard that would have caught it. The
defect survived because `equals`, `hashCode`, and `deepCopy` were all updated when `control` was
added, and `encodeTo` was not, and **nothing in the build notices a field that is missing from a
method**. R3 closes that: a reflection check fails if any state class declares a field with no
encoding-fidelity case.

**This spec lands before 12.01**, despite being numbered last, because 12.01's injectivity test found
it and both stores' keys route through the encode path.

## Objective

Make every `SharedState` implementation's `encodeTo` provably represent every field that the same
class treats as identity — and make the next omission a build failure rather than a latent wrong
answer.

This spec **fixes** the one confirmed defect (`DeadlockState.control`) and **closes** the
implementation for all five state classes it owns. It deliberately does **not** deliver the objective
for `DynamicState`, whose two omissions are escalated to the DSL spec set (R4); that class is
swept by the recurrence guard with its two gaps named, so the shortfall stays visible and cannot grow,
but this spec does not claim it as fixed.

## Scope

- **Package:** `interleave` / `src/main/java/dev/samhb/interleave/core`
- **Modifies:** `core/DeadlockState.java` (`encodeTo` only); `core/SharedState.java` (**Javadoc only** —
  no signature or behavioural change, see R1); adds
  `src/test/java/dev/samhb/interleave/state/StateEncodingFidelityTest.java`
- **Off-limits:** `format/dsl/DynamicState.java` — escalated to the DSL spec set (`09-json-dsl-core`,
  `10-json-dsl-invariants`), which own `encodeTo` for DSL states; see §R4. `state/CanonicalEncoder.java`
  — Spec 12.01 owns the composer. `state/HashingStateStore.java` and `state/BitstateStore.java` — Specs
  12.02 and 12.03 own the stores, including any change to how they build keys.

## Non-Goals

- Changing what any state *means*. This is about how state is represented, not what it models.
- Redesigning the encoding format. Adding `control` is one line; changing the framing (length
  prefixes, versioning) would invalidate every stored artifact and every assertion elsewhere for no
  gain here.
- Auditing `encodeTo` *correctness* beyond completeness — whether the bytes written are the right
  bytes for a given field is a different question, and for the four correct classes the audit found
  nothing to raise.
- Auditing `deepCopy` for field parity. R2 checks it indirectly via the encoding case (a field omitted
  from `deepCopy` fails the case that mutates it), but `deepCopy` has no independent checklist.

## Current State

All claims [verified] by reading each `SharedState` implementation in full, plus
`HashingStateStore.java:101–118` and `BitstateStore.java:355–357` for the store key shape.

### The defect

**As it stood at `63c6a8d^` (the pre-fix state):** `DeadlockState.encodeTo` (`L45–48`) wrote two of
the class's three identity fields:

```java
public void encodeTo(DataOutput out) throws IOException {
    out.writeBoolean(flag[0]);
    out.writeBoolean(flag[1]);
    // `control` is never written
}
```

This snippet is historical and is kept as the record of what was wrong. **It no longer matches the
source** — the fix writes `control` before the flags, and current line numbers have shifted.

```java
public void encodeTo(DataOutput out) throws IOException {
    out.writeBoolean(control);       // the fix: written first so flags stay at their old offsets
    out.writeBoolean(flag[0]);
    out.writeBoolean(flag[1]);
}
```

Field order is `control`, `flag[0]`, `flag[1]`. Writing `control` first rather than last is
deliberate: it keeps both flags at the byte offsets a pre-fix reader would assume, which matters only
for readability, but it also means a reader who skips the first byte lands on a flag rather than on
padding. The order is not part of the encoding contract — injectivity is, and any order that writes
all three fields satisfies it.

The same class treats `control` as identity in three other places:

| member | line | treats `control` as identity? |
|---|---|---|
| `deepCopy` | `L40` (`copy.control = this.control`) | yes |
| `equals` | `L54` (`&& control == that.control`) | yes |
| `hashCode` | `L60` (`31 * result + Boolean.hashCode(control)`) | yes |
| `encodeTo` | `L45–48` | **no** |

**The Javadoc states only half the contract, and the defect breaks the half it does not state.**
`SharedState.encodeTo`'s Javadoc (`L27–34`) says: *"Two states that are equal must encode to identical
bytes. The two properties are maintained together and tested together; a mismatch would make the
bitstate store prune real configurations."*

Read carefully, that guarantee is *equal states → identical bytes* — and the defect does **not** break
it. Omitting `control` makes the encoding strictly **coarser**, which makes identical states encode
identically *more* reliably, not less. An earlier draft of this spec claimed the defect violated this
documented guarantee; that was wrong, and the error is worth naming because it is the natural way to
misread a one-directional contract as bidirectional.

So the requirement this spec enforces — *distinct states → distinct bytes* — is **not** the documented
one. It is a genuine requirement, but it is **under-documented and currently implicit**, and it is
load-bearing:

- `HashingStateStore.isVisited` (`L33–40`) treats a hash-prefilter hit as a *candidate* and confirms it
  "against the exact encoded state." If the encoding is coarser than the state, a genuinely new
  configuration is confirmed as already-seen and pruned.
- `BitstateStore` marks bits by encoding; a coarser encoding sets bits that cover distinct
  configurations, so the second one is misreported as explored.

So there are two distinct properties here, and conflating them is the easy mistake:

| | property | who needs it |
|---|---|---|
| **store key** | *(state, programCounters)* → key must be injective | both stores, for correctness today |
| **state encoding** | state → bytes must be injective **on its own** | **this spec's chosen invariant** |

The stores need only the first — but **only one of them gets there by determinism**, and the
distinction decides how strong the "this is latent" argument is:

| store | key construction | does a different counter guarantee a different key? |
|---|---|---|
| `HashingStateStore` | `base64(encode(state)) + "\|" + programCounters.toString()` | **Yes** — a concatenated string; different counters ⇒ different key, deterministically |
| `BitstateStore` | `31 * encoder.hashCode(state) + programCounters.hashCode()`, then a bit index | **No** — 32-bit arithmetic and a bit index are both lossy; different counters *usually* differ, not *always* |

So the latency argument **rests on `HashingStateStore`, where it is sound**, and does **not** transfer
to `BitstateStore`: distinct program counters there can still collide, by design — a bounded-memory
bitstate cannot be injective, which is precisely why Spec 12.03 exists. That does not make the
`DeadlockState` defect a live `BitstateStore` correctness bug, because the store is *already* lossy for
reasons of its own; it does mean that **no safety argument about `BitstateStore` is offered here**. Any
such claim would need the actual bit positions checked for the specific configurations, which this spec
does not do. Recorded so a later reader does not generalise the string-key reasoning across both stores.

What forces the stronger, self-sufficient property is a choice this spec makes deliberately:
`encodeTo` should represent every identity field **on its own**, so that no consumer depends on some
other field of the key to rescue it. That choice buys three things:

- **a property testable locally per class**, with no store, no counters, and no corpus in the way —
  which is why R2 can be a unit test at all;
- **independence from key composition**, so a future store that keys differently, or a consumer that
  uses `encode()` alone, cannot silently inherit the defect;
- **a failure that is attributable** — "this class's encoding is lossy" — instead of "some store
  somewhere merged two configurations".

So the state-level requirement is *stronger than the stores need*, and R1 records it as an invariant
this spec imposes rather than as something the stores demand. Stating it as a store requirement would
be a false claim, and would also wrongly imply that the defect is a live correctness bug — for
`HashingStateStore` it is not, because that store needs only the weaker property that does hold.

Meanwhile the interface Javadoc documents neither of these as a `SharedState` obligation. That gap is
why the defect was invisible to review and to the compiler: the contract an implementer is pointed at
does not mention the property that was broken. R1 amends `SharedState.encodeTo`'s Javadoc to state both
directions, so the next implementer reading only the documented contract gets the whole contract.

`control` is live search state, not vestigial: `bugs/DeadlockWriteFlagStep.java:43` calls
`ds.setControl(true)` on every flag write, so any `deadlock` execution sets it [verified].

### Why the severity is latent, not active — for one store, by argument

The claim needs qualifying per store, because the two keys are not the same kind of thing.

**`HashingStateStore` — sound.** Its key is a concatenated *string*
(`base64(encode(state)) + "|" + programCounters.toString()`, `L107–111`), so two configurations with
the same encoding but different program counters get **deterministically different** keys. In `deadlock`,
`control` is set by a step that *also* advances a program counter, so the colliding states occur at
different counters and no merge reaches this store. Verified by walking the corpus: 5 distinct
`DeadlockState` values by `equals`, 4 by encoding, yet the corresponding configurations stay distinct
under this store's key.

**`BitstateStore` — no argument offered.** Its key is `31 * encoder.hashCode(state) +
programCounters.hashCode()` (`L355–357`), narrowed to a bit index. Different counters therefore give a
different *hash* only usually, and a different *bit* even less reliably — the structure is lossy by
construction, which is the whole reason Spec 12.03 governs it. So the "counters rescue it" reasoning
does **not** apply here, and this spec asserts nothing about `BitstateStore`'s behaviour under the
defect. This does not make the defect a live `BitstateStore` bug: that store already merges distinct
configurations by design, so the defect adds no new failure mode. It does mean the "nothing is wrong
today" verdict is **argued for `HashingStateStore` and merely unexamined for `BitstateStore`** — a
distinction worth keeping, since collapsing the two would overstate the assurance. Bounding the
`BitstateStore` side would require checking the actual bit positions of the affected configurations,
which is 12.03's territory and is deliberately not attempted here.

So **no verdict is wrong today** — argued for `HashingStateStore`, unexamined for `BitstateStore` as
above. Three reasons that is not a dismissal:

1. **The masking is incidental, not designed.** Nothing in `HashingStateStore` documents that program
   counters are load-bearing for correctness; they are there because a `Configuration` includes them.
   Relying on an unrelated field to rescue a broken one is a coincidence, and the rescue disappears the
   moment two configurations share counters and differ only in an omitted field.
2. **The masking does not hold for `BitstateStore`** and no substitute argument is offered for it, so
   the "nothing is wrong today" claim rests on one store and not on the design.
3. **`control` has no reader** outside `deepCopy`/`equals`/`hashCode` [verified: repo-wide grep finds
   no `control()` call in `src/main`]. That is *why* the defect is latent, not evidence it is
   harmless. `equals`/`hashCode` declare it identity; a future reader would inherit a store that
   already merges those states.

### The audit

Every `SharedState` implementation, its fields, and what `encodeTo` writes:

| class | identity fields (`equals`/`hashCode`) | `encodeTo` writes | verdict |
|---|---|---|---|
| `DeadlockState` | `flag[0]`, `flag[1]`, **`control`** | `flag[0]`, `flag[1]` | **DEFECT** |
| `PetersonState` | `flag[0]`, `flag[1]`, `turn`, `inCriticalSection` | all 4 | correct |
| `CounterState` | `counter`, `control`, `registers[]` | all 3 + `registers.length` | correct |
| `PairState` | `high`, `low`, `control`, `observedHigh`, `observedLow`, `hasObservation` | all 6 | correct |
| `DclState` | `initialized`, `instance`, `locked`, `lockOwner`, `control`, `observedInstance` | all 6 | correct |
| `DynamicState` | `decl`, **`threadCount`**, `fieldValues[]`, `localValues[][]` | values only — omits `decl`, `threadCount` | latent, escalated (R4) |

Two entries in that table are worth reading closely rather than skimming:

- **`CounterState` writes `registers.length`** (`L122`) before the register values. That is a
  deliberate defence against length ambiguity, and it is why `CounterState` is correct despite being
  the only class with a variable-length field. It is the pattern the others would need if they gained
  variable-length fields.
- **`DclState` encodes two `Object` fields as presence booleans** (`instance != null`,
  `observedInstance != null`, `L94`/`L98`) rather than as values. That is *sufficient* rather than
  complete — `equals` also compares only presence — so the class is self-consistent. It is correct by
  its own contract, though it would break if either field ever held two distinguishable non-null
  values. Recorded here so a future change to those fields is a conscious one, not an accident.

`CounterState`, `PairState`, and `DclState` all encode `control`; `PetersonState` does not have one.
So the omission is specific to `DeadlockState`, not a shared misunderstanding of what `control` is.

### Why nothing caught this

`control` was added to `DeadlockState` at some point after the class was written. Whoever added it
updated `equals`, `hashCode`, and `deepCopy` — three of the four places that need to know about a new
field — and missed `encodeTo`, the fourth. There is no mechanical reason that omission should be
visible: no test walks `encodeTo`'s field coverage, and no compiler or linter compares an encoding
against an `equals`. The class compiles, the suite is green, and the store still returns correct
verdicts because program counters happen to cover for it.

That is the argument for R3. The fix without the guard leaves the same omission free to recur in the
next state class, and to be found only by a test that happens to walk a program whose states collide
on the affected field — which is exactly the accident that found this one.

## Invariants

- **Every identity field SHALL be encoded.** `encodeTo` writes every field that the same class's
  `equals`/`hashCode` treat as part of state identity. A field in `equals` but absent from `encodeTo`
  is a defect, and the asymmetry is the bug's signature — it is what makes the class
  self-contradictory. **Known exception:** `DynamicState.decl` and `DynamicState.threadCount`, which
  violate this invariant today and are escalated to the DSL spec set (R4). The exception is named in
  R3's allowlist rather than left implicit, so the set of violations is machine-checked, not asserted.
- **Both directions are required, and only one is documented.** `SharedState`'s Javadoc guarantees
  *equal states encode identically*; R2 tests that direction by construction, since two equal-valued
  states are deduplicated before the count is taken. The direction this spec **owns and introduces**
  is the converse — *distinct states encode distinctly* — and a field omitted from `encodeTo` violates
  precisely that. This converse is **this spec's chosen self-sufficiency invariant, not a requirement of
  either store**: the stores need only *(state, programCounters)* injectivity, which currently holds
  (§Current State). It is imposed because it is locally testable, independent of key composition, and
  yields attributable failures. It is not the documented guarantee either, which is why it needs stating
  rather than assuming; R1 amends the Javadoc.
  Note the asymmetry: making an encoding coarser can only ever *preserve* the documented direction, so
  the documented direction is incapable of detecting this class of defect.
- **This invariant is a design requirement; the tests only sample it, and SHALL NOT be described as
  proving it.** Full-domain injectivity over `int` fields cannot be established by any finite test, so
  R2's multi-value cases (R2a) establish *representation across a representative spread*, which is
  what catches the realistic lossy encodings — truncation, masking, parity, modulus, commutative
  summaries, dropped length prefixes. It does not establish injectivity over every `int`. Any
  acceptance criterion or PR description claiming the suite "proves the encoding injective" is
  overstating it and SHALL be corrected; the honest claim is "every identity field is represented, and
  its encoding varies with its value across the sampled domain."
- **Encoding need not be literal.** A class MAY encode a field as a faithful projection rather than its
  full value — `DclState`'s presence booleans are the existing example — provided the projection
  distinguishes every pair of values the class's own `equals` distinguishes. The requirement is
  fidelity to `equals`, not a byte-for-byte dump.
- **An omitted field is a defect even when currently masked.** Correctness-by-coincidence is not
  correctness. The obligation is on the encoding to be self-sufficient, not on some other part of the
  key to happen to compensate.
- **New fields SHALL carry their obligation.** Adding a field to a `SharedState` without a
  corresponding encoding case SHALL fail the build. This is the invariant that distinguishes this spec
  from a one-line bug fix.

## Requirements

1. **THE SYSTEM SHALL** add `control` to `DeadlockState.encodeTo`, positioned consistently with the
   other classes — `PairState` and `CounterState` both write `control` first, and matching that order
   keeps the six encodings comparable at a glance.
   **It SHALL also amend `SharedState.encodeTo`'s Javadoc to state both directions of the contract** —
   equal states encode identically (the existing text) *and* distinct states encode distinctly (the
   direction this spec enforces and the interface currently omits). The Javadoc amendment is part of the
   fix, not a follow-up: without it the defect stays invisible to the next reader, because the contract
   an implementer is pointed at does not mention the property that was broken.
2. **THE SYSTEM SHALL** carry an explicit field-parity case for **every** identity field that this
   spec fixes — every field of the five in-scope implementations (`DeadlockState`, `PetersonState`,
   `CounterState`, `PairState`, `DclState`), **plus `DynamicState.fieldValues` and
   `DynamicState.localValues`** — asserting that changing only that field changes the encoding. The
   cases are per-field rather than per-class, because a single per-class assertion passes when
   four of five fields are handled and one is not — which is the current state of `DeadlockState`.
   Each case SHALL isolate **multiple** values per field rather than one value pair (see below).
   Only `DynamicState.decl` and `DynamicState.threadCount` are excluded from parity cases, because
   they are known absent from the encoding and R4 forbids fixing them; they are carried as **tracked
   gaps** by R3 and recorded by R5. Excluding the two *fields* rather than the whole class is
   deliberate: `DynamicState`'s other two fields are encoded correctly and must be pinned like any
   other, so excluding the class wholesale would have left them unchecked.
2a. **THE SYSTEM SHALL** sample each scalar field across a **representative spread of its domain** —
   at minimum: for `int` fields, a set spanning zero, both signs, adjacent values (`n`/`n+1`), a
   negative/zero boundary, and values that differ only above the low byte (`255`/`256`,
   `-1`/`255`) and above the low short (`65535`/`65536`); for `boolean` fields, both values.
   For array fields it SHALL additionally assert that **position matters** — two states whose arrays
   are element-wise permutations of each other (`[true, false]` vs `[false, true]`) encode
   differently — and that **shape matters** — two states whose arrays differ in length encode
   differently.
   For a **nested** array whose row lengths can vary, it SHALL additionally assert that **row
   boundaries matter**: `[[1], [2, 3]]` and `[[1, 2], [3]]` hold the same three values under the same
   outer length, so an encoder that merely concatenates rows emits identical bytes for them.
   `DynamicState.localValues` is **excluded** from this clause and the row-boundary requirement is
   **deferred to specs 09/10**, which own `DynamicState`'s encoding (R4). Asserting it here would be
   asserting a probe that cannot fail — see R4a — and a check that cannot fail is worse than no check,
   because it reads as coverage. The clause is retained above because it governs *any future* nested
   array; no in-scope field is nested today, so this spec states the rule without currently exercising
   it.
   A single value pair per field is insufficient because the realistic lossy encodings are all
   *survivable* at one sample: writing `turn` as `(turn == 0)`, as `turn % 256`, as `turn & 1`, or
   writing `flag` as a count of set bits, all encode a `0`-versus-`1` probe correctly while colliding
   everywhere else. `CounterState` already writes `registers.length` and encodes `control` as a real
   boolean — the existing implementations are more careful than this, which is exactly why the weaker
   test would have been a false reassurance.
3. **THE SYSTEM SHALL** enforce case completeness by reflection: a test SHALL enumerate the **non-static
   instance** fields of all six `SharedState` implementations and fail if any is neither covered by a
   case nor listed in an explicit tracked-gaps allowlist. **A field added without a case SHALL
   fail the build** — this is the requirement that prevents recurrence, and it is what was missing
   when `control` was introduced. The allowlist SHALL contain exactly `DynamicState.decl` and
   `DynamicState.threadCount`, each citing R4's escalation to the DSL spec set. Sweeping
   `DynamicState` rather than excluding it is deliberate: the allowlist names two *known* omissions, so
   a **third** omission in that class still fails the build. An empty allowlist is the target state —
   when 09/10 land, this spec's allowlist is deleted, not emptied one entry at a time.
   Static fields SHALL be excluded from the sweep. None of the six classes declares one today
   [verified: `grep static` across all six returns only `of(...)` factory *methods*, no fields], so this
   is preventative rather than a live defect — but the first person to add a `private static final`
   logger or constant would otherwise get a spurious failure demanding a parity case for it, and the
   natural response to that failure is to delete the check. That outcome would cost the recurrence
   guard, so the filter is specified now rather than after it has been defeated once.
3a. **THE SYSTEM SHALL** treat the allowlist as **verified against `encodeTo`, not trusted**, in a
   **separate check** from R3's field accounting. `everyStateField_hasAnEncodingCase` enumerates fields
   and never calls `encodeTo`; conflating the two duties would force it either to give up that
   property or to become a muddled test. A distinct check — `trackedGaps_areStillRealGaps` — SHALL
   determine whether each allowlisted field is *actually* still unencoded, and SHALL fail if one has come
   to be encoded. The allowlist is therefore a self-invalidating record of what is still missing: when
   09/10 fix `DynamicState`'s omissions, these entries fail rather than quietly outliving their reason.
4. **THE SYSTEM SHALL** record the `DynamicState` finding — `decl` and `threadCount` are absent from
   `encodeTo` — as a latent gap in this spec's §Current State, and SHALL escalate it to the DSL spec
   set (`09-json-dsl-core`, `10-json-dsl-invariants`), which own DSL state encoding. This spec SHALL
   NOT fix it and SHALL NOT claim it as correct.
4a. **THE SYSTEM SHALL** record the *scope* of that gap precisely, because it is wider than "two
   omitted fields." `DynamicState.encodeTo` writes `decl` and `threadCount` nowhere, and the local-value
   loop (`L196–202`) emits `threadCount × decl.locals().size()` values **with no row delimiters**. The
   stream length therefore reveals only the *product*, so two different shapes with equal product —
   `threadCount=2, locals=3` versus `threadCount=3, locals=2`, both all-`INT` — emit **identical
   bytes** despite describing different state layouts [verified by reading `L196–202`]. Field arrays are
   *not* affected: the `INT_ARRAY` branch writes `arr.length` (`L190`), so `fieldValues` is delimited
   correctly.
   The ragged-row variant (`[[1],[2,3]]` vs `[[1,2],[3]]`) **cannot arise** in this class:
   `localValues` is allocated as `new Object[threadCount][decl.locals().size()]` (`L36`), so it is
   rectangular by construction and no row can differ in length from its siblings. R2a shall not assert a
   probe that cannot fail. The equal-product collision above is the real, reachable form of the same
   root cause — the omitted `decl`/`threadCount` — so it is recorded under R4 rather than treated as a
   separate defect, and it inherits R4's unreachability argument (it requires two different `decl`s
   inside one store, which does not occur today).
5. **THE SYSTEM SHALL** record, for every state class, whether each identity field is encoded
   literally, encoded as a faithful projection, or omitted — so the audit in §Current State is a
   maintained artefact rather than a one-time observation.
6. **THE SYSTEM SHALL** add a targeted regression test asserting that two `DeadlockState` values
   differing only in `control` encode differently. **This test — not any injectivity assertion — is
   what guards the fix**, because Spec 12.01's R1 passes whether or not the defect is present (program
   counters mask it; see Spec 12.01 §R1).

## Acceptance Criteria

- [ ] `encodeDeadlockState_controlFlipped_changesEncoding` — two `DeadlockState` values differing only
      in `control` produce different bytes (R6). **Demonstrated failing** before the fix in R1.
- [ ] A field-parity case exists for every identity field of the **five in-scope** implementations plus
      `DynamicState.fieldValues` and `DynamicState.localValues`, and each fails if its field stops
      being encoded (R2). `DynamicState.decl` and `DynamicState.threadCount` are the only excluded
      fields and are carried as tracked gaps by R3.
- [ ] Every scalar-field case samples **multiple** values spanning zero, both signs, adjacent pairs,
      and the low-byte / low-short boundaries (`255`/`256`, `-1`/`255`, `65535`/`65536`) (R2a).
- [ ] Every sampled spread is compared **pairwise**, each variant against every other, and not only
      against a fixed baseline (R2b). **Demonstrated necessary:** against a low-byte encoder the
      baseline-relative style produces **0** failing assertions while the pairwise style produces **7**;
      and on the real code, truncating `PetersonState`'s `turn` to a byte **passes** the
      baseline-relative probe and **fails** the pairwise one. See §R2b.
- [ ] Every array-field case asserts **position matters** (a permutation such as `[true, false]` vs
      `[false, true]` encodes differently) and **shape matters** (arrays differing in length encode
      differently) (R2a). **Demonstrated** by temporarily replacing `flag`'s encoding with a count of
      set bits, confirming the position probe goes red, then reverting.
- [ ] No acceptance criterion, test name, or PR description claims the suite **proves** injectivity.
      The suite samples a representative domain (see §Invariants); full-domain injectivity is a design
      requirement the tests cannot establish, and the distinction is stated wherever the claim is made.
- [ ] `everyStateField_hasAnEncodingCase` passes: reflection over the **non-static** `getDeclaredFields()`
      of all six classes finds every instance field either covered by a case or named in the allowlist,
      and the allowlist holds exactly `DynamicState.decl` and `DynamicState.threadCount` (R3). **It makes
      no `encodeTo` call.** **Demonstrated** by adding a throwaway instance field to one class and
      confirming the check goes red and names it, then reverting.
- [ ] `everyStateField_hasAnEncodingCase` **discovers** the state classes from the classpath rather
      than from a hardcoded list (R3c). **Demonstrated necessary:** the hardcoded list was the exact
      recurrence hole this spec exists to close — a seventh `SharedState` added later would simply not
      be checked, and R3 would stay green with its encoding unpinned. Confirmed by adding a throwaway
      `UntestedState` implementing `SharedState`: R3 went red naming `UntestedState.value`, then the
      class was deleted.
- [ ] `everyStateField_hasAnEncodingCase` **ignores** static fields — adding a `private static final`
      constant to any of the six classes keeps the check green (R3). No static fields exist today, so
      this guards the check against being defeated by an unrelated future constant.
- [ ] **R2, R3, and R3a each catch a different thing, and none substitutes for another.** R2's parity
      cases fire on an *existing* field that stopped being encoded. R3 fires on a *newly declared* field
      with neither a case nor a tracked-gap entry — an omission of **coverage**. R3a fires when an
      **existing tracked gap is closed** — an entry that has stopped being true. R3 alone would pass
      today with every parity case deleted; R2 alone would not notice a newly added field; R3a alone
      would not notice an unencoded field at all. All three are required.
- [ ] `trackedGaps_areStillRealGaps` fails when an allowlisted field comes to be encoded — i.e. after
      `threadCount` is added to `DynamicState.encodeTo` (R3a). **Demonstrated** by that temporary edit,
      then reverted. It is a **separate test** from `everyStateField_hasAnEncodingCase`, which stays a
      pure accounting sweep.
- [ ] `DynamicState`'s two *encoded* fields are pinned by their own parity cases, so the excluded
      class is not left with zero encoding coverage (R2).
- [ ] `DeadlockState.encodeTo` writes `control` (R1), and the byte output for the other five classes is
      **unchanged** by this spec — verified by diffing pre- and post-change encodings of a fixed state
      of each (R1).
- [ ] `SharedState.encodeTo`'s Javadoc states **both** directions — equal states encode identically
      *and* distinct states encode distinctly — so the contract an implementer is pointed at covers the
      property that was actually broken (R1).
- [ ] `deadlock` still reports `DEADLOCK` and `statesExplored` is unchanged [verified pre-change:
      15] (R1). The defect is latent, so an unchanged verdict is the *expected* result — this is a
      regression guard, not a bug-fix demonstration, and the PR body SHALL say so.
- [ ] The `DynamicState` gap is recorded in §Current State with its reachability argument and an
      explicit escalation to 09/10 (R4).
- [ ] §Current State carries the encoding-fidelity table for all six classes, with each field marked
      literal / projection / omitted (R5).
- [ ] `./gradlew clean test javadoc` passes and Javadoc reports no errors (R1–R6).
- [ ] `./gradlew pitest` is run and its effect on the global mutant total recorded. `core.*` is
      outside the PIT scope (`state.*` + `cb.*`), so the count should not move — but the assertion in
      Spec 12.06 §R5 is what proves it did not, and recording "should not move" as a checked claim is
      what distinguishes a verified no-op from an assumed one.

## Design

### R2 — per-field cases, not per-class assertions

The obvious test is "encode two states of the same class, assert different bytes," and it is worth
almost nothing here: it passes for every class in the audit including the broken one, because any
single differing field produces different bytes. `DeadlockState` has three identity fields and encodes
two, so a two-state assertion is satisfiable by the fields it *does* encode.

The case must therefore isolate one field. For each identity field, construct states differing in
*only* that field and assert the encodings differ. That is a stricter test, and it is the only shape
that fails on the present defect.

### R2a — one value pair per field is still not enough

Isolating the field fixes *which* field is under test, but says nothing about how faithfully it is
represented, and the two are easy to conflate. A `turn` probe of `0` versus `1` passes against:

| hypothetical (wrong) encoding | `0` vs `1` probe | where it still collides |
|---|---|---|
| `out.writeBoolean(turn == 0)` | **passes** | `turn=2` vs `turn=3` |
| `out.writeInt(turn % 256)` | **passes** | `turn=0` vs `turn=256` |
| `out.writeBoolean((turn & 1) == 0)` | **passes** | `turn=0` vs `turn=2` |
| `out.writeBoolean(flagCount(flag) > 0)` | **passes** | `[true,false]` vs `[false,true]` |

Every row is a real store-collision bug — two distinct configurations merged — and every row passes a
single-value-pair test. So R2a requires a domain spread rather than one probe, and array fields get
positional and shape assertions because the lossy encodings that survive a single probe are mostly
order-insensitive or length-blind.

This is also where the temptation to overclaim lives. Sampling a spread **does not prove injectivity**
over `int`; no finite test can. What R2a establishes is *representation across a representative
domain*, which catches every realistic truncation, masking, parity, modulus, commutative-summary, and
length-loss encoding. The invariant in §Invariants is stated as a design requirement and labelled as
such, so the spec does not claim a proof its tests cannot deliver. A test named
`encoding_is_injective` would be asserting something no finite sample supports.

#### R2b — comparing each variant only to a baseline is blind to variant-to-variant collapse

The spread above was originally probed **relative to a single base value**: every sampled value was
asserted to encode differently from `BASE_INT`, and nothing compared the sampled values against *each
other*. That is weaker than it looks, and the weakness is measurable.

**[verified, 2026-10-01]** With a hypothetical encoder that writes only an `int`'s low byte
(`out.writeByte((byte) turn)`), and a base of 12_345 (low byte 57):

| probe style | assertions failing under the lossy encoder |
|---|---|
| baseline-relative only | **0** — every sampled value differs from 57, so every assertion passes |
| pairwise (every variant vs every other) | **7** — `0`/`256`, `1`/`-255`, `255`/`-1`, … all collapse |

The baseline style is not merely weaker in degree; under this encoder it is **completely blind**. Every
value in the spread looks different *from the base* while several are identical *to one another*, and
those are precisely the pairs the stores would prune wrongly.

This was then confirmed end-to-end on the real code rather than argued from a synthetic probe:
`PetersonState.encodeTo`'s `out.writeInt(turn)` was temporarily changed to `out.writeByte((byte) turn)`
and the suite was run twice.

- with the baseline-relative probe only → **PASSED** (mutation undetected)
- with the pairwise probe → **FAILED**: `PetersonState.turn across INT_SPREAD: variants 0 and 5 are
  distinct states but share the encoding [0, 0, 0, -1, -1, -1, -1]`

So R2a alone does not deliver what it claims. R2b requires the pairwise assertion, now applied to
`PetersonState.turn` / `inCriticalSection`, `CounterState.counter` / `registers[0]`,
`PairState.high` / `low` / `observedHigh` / `observedLow`, `DclState.lockOwner`,
`DynamicState.fieldValues[count]` / `localValues[t]`, and all four `DeadlockState` flag combinations.

The flag combinations matter for the commutative-summary row in the table above: `{0,1}` and `{1,0}`
are distinct states that a count-of-set-flags cannot distinguish, and no baseline-relative probe would
reveal it.

`DclState.lockOwner` needed its own spread constant (`LOCK_OWNER_SPREAD`, `INT_SPREAD` minus `-1`)
because the field is already `-1` when unlocked, making an `-1` probe a no-op. That was not handled by
special-casing the assertion: the existing vacuity guard rejected it outright, which is the guard
earning its place.

Feasibility is confirmed: every field is reachable through the public accessors already on each class
— `setControl`, `setTurn`, `setInCriticalSection`, `setFlag`, `setCounter`, `setRegister`, `setHigh` /
`setLow`, `setInitialized` / `setLockOwner` / `setLocked`, and `DynamicState.setInt` / `setBool`.
Constructing a one-field-different pair is a `deepCopy()` plus one setter call, so no test needs
reflection to *mutate* state. This matters: reflection-based mutation of `final` fields
(`CounterState.registers`, `DynamicState.decl`) is fragile across JVM versions, and R3 already uses
reflection for the narrower job of *enumerating* declared fields, which is stable.

### R3 — completeness is the part that prevents recurrence

R2 is only as good as the author's diligence: nothing stops the next person adding a field and not
adding a case. R3 closes that mechanically. It reflects over each implementation's non-static declared
fields and asserts each is either covered by a case or named in a tracked-gaps allowlist, so the
failure mode inverts — instead of a silently incomplete encoding, you get a red build naming the field
that has no coverage.

**R2 and R3 catch different things, and the distinction is the reason both are needed.** R2's parity
cases detect an *existing* field that stopped being encoded — they exercise `encodeTo` directly, so
they fail only when the encoding is wrong. R3's reflection sweep detects a *newly declared* field with
neither a case nor a tracked-gap entry — it never runs `encodeTo`, so it fails when **coverage** is
missing. Neither implies the other: R3 would pass today with every parity case deleted, and R2 would
not notice a field added tomorrow. Conflating them in a single test name was flagged in review as
misleading, and it matters because a reader who believes R3 subsumes R2 will delete the wrong one.

Three further properties make this worth the reflection:

- **It targets the exact mechanism of this defect.** `control` was added and three of four sites were
  updated. A checklist that must name the field is the check that would have caught the fourth.
- **It fails loudly on an innocent refactor**, because renaming a field breaks the case table. That is
  the intended behaviour — it forces a conscious decision rather than a silent one — but it WILL
  surface as a build failure on a rename that changes nothing semantically. Recorded here so the next
  person does not "fix" it by deleting the assertion.
- **The allowlist names gaps, not classes.** `DynamicState` is swept, not skipped, so a *third*
  omission there fails the build even though two are already known. Excluding the class wholesale would
  have been simpler to write and would have given up exactly the coverage most likely to be needed —
  `DynamicState` has the most fields of the six and its two omissions are already unfixed.

The sweep filters to **non-static instance fields**. None of the six classes declares a static field
today, but the first `private static final` constant would otherwise trigger a spurious failure whose
only relief is deleting the check — costing the recurrence guard to accommodate an unrelated constant.

The reflection enumerates **declared fields**, which for `DclState` includes the `Object`-typed
`instance` and `observedInstance`. Those have cases asserting the presence-boolean projection
distinguishes them, consistent with R5's "projection" allowance.

**How R2 and R4 were reconciled.** These two requirements collided on the first draft: R2 demanded a
parity case for every identity field of every implementation, while R4 forbids repairing
`DynamicState`. That combination has no satisfiable solution — a case for `decl` or `threadCount` must
fail, and making it pass requires the fix R4 disclaims.

The resolution scopes the exclusion to the **two offending fields**, not the class. `DynamicState`
declares four identity fields: `decl` and `threadCount` (omitted, escalated) and `fieldValues` and
`localValues` (encoded). Excluding all four would satisfy R4 by leaving two correctly-encoded fields
unpinned — and R3's completeness sweep would then go red on them, since they are neither covered by a
case nor named in the allowlist. So the two encoded fields get cases like every other field, and only
the two omitted ones are allowlisted. The gap stays recorded and machine-checked; it just is not
asserted as a property this spec claims to have fixed.

CodeRabbit flagged both the R2/R4 collision and the follow-on hole it created when this spec was
reviewed in place — the second only became visible once the first was fixed.

### R6 — why the regression test is separate from injectivity

Spec 12.01's R1 asserts `Configuration` injectivity, using the store's real key shape (state encoding
*plus* program counters). That is the right property for 12.01 to own — the store needs both to be
injective, and asserting state-encoding injectivity alone is strictly stronger than the store requires
and would fail on gaps the store tolerates by design.

The consequence must not be glossed over: **12.01's R1 passes whether or not this defect is fixed**,
because program counters mask the collision. So the test that actually guards R1's fix is R6 here. Any
future claim that "injectivity is pinned" must not be read as evidence the encoding is complete —
those are different properties, owned by different specs, and only one of them catches this.

### Ordering within the set

This spec lands **before 12.01**, despite the `07` numbering, because:

- 12.01's implementation found the defect, and its test is what surfaced it;
- both stores' keys route through `encodeTo`, so 12.02 and 12.03's assertions inherit whatever this
  spec fixes.

It is appended rather than renumbered into position 1 because the existing `01`–`06` cross-reference
each other — 12.05 and 12.06 name each other's requirements, and 12.06's §Derivation Record is the
single writer for the post-12.01 mutant total. Renumbering would break those references to buy a
tidier number, which is a bad trade. The implementation order lives in the set README's table, and
that table is where "lands first" belongs.

## Tests

**File:** `src/test/java/dev/samhb/interleave/state/StateEncodingFidelityTest.java`

- `encodeDeadlockState_controlFlipped_changesEncoding` (R6) — the targeted regression; the only test
  that fails without the fix
- `encodePetersonState_eachField_changesEncoding` (R2/R2a) — `flag`, `turn`, `inCriticalSection`;
  `turn` sampled over the R2a spread, `flag` additionally checked for position sensitivity
- `encodeCounterState_eachField_changesEncoding` (R2/R2a) — `counter`, `control`, `registers`;
  `registers` additionally checked for position and length sensitivity
- `encodePairState_eachField_changesEncoding` (R2/R2a) — all six fields
- `encodeDclState_eachField_changesEncoding` (R2/R2a) — all six fields, including the two
  presence-boolean projections
- `encodeDeadlockState_eachField_changesEncoding` (R2/R2a) — all three, so the fix is covered by the same
  mechanism as every other class rather than only by R6
- `encodeDynamicState_eachEncodedField_changesEncoding` (R2/R2a) — `fieldValues`, `localValues` only.
  `decl` and `threadCount` get **no** case, and that absence is deliberate (R2/R4): they are known
  absent from the encoding, so a case for them must fail, and R4 forbids the fix that would make it
  pass. `DynamicState` therefore still gets parity coverage for the fields it does encode — excluding
  the class wholesale would have left both unchecked.
- `everyStateField_hasAnEncodingCase` (R3) — **coverage accounting only.** Every **non-static** declared
  field of all six classes is either covered by a case or named in the tracked-gaps allowlist (exactly
  `DynamicState.decl` and `DynamicState.threadCount`). **Calls no `encodeTo`** — it asserts that every
  field is *accounted for*, which is a different failure mode from R2's, which asserts fields are
  encoded *correctly*.
- `trackedGaps_areStillRealGaps` (R3a) — **behavioural, and deliberately separate.** For each
  allowlisted field, establishes that it is *actually* still absent from the encoding, and fails if it
  has come to be written. Kept out of `everyStateField_hasAnEncodingCase` so that test stays a pure
  accounting sweep with no `encodeTo` call.
- `equalStates_encodeIdentically` (R2, converse direction) — two equal-valued instances of each of the
  five in-scope classes encode identically, confirming the fix does not over-separate

**Test naming.** No test may be named `…is_injective` or otherwise claim to prove injectivity. The
cases establish representation over a sampled domain (R2a); the injectivity *requirement* is real but
unprovable by finite sampling. Naming a test after the unprovable claim invites a future reader to
assume it was checked.

**Falsification checks (not committed tests), in order of value:**

1. Comment out `out.writeBoolean(control)` in `DeadlockState.encodeTo`; confirm
   `encodeDeadlockState_controlFlipped_changesEncoding` and
   `encodeDeadlockState_eachField_changesEncoding` go red; revert. Record the reverted SHA and the
   observed failures in the PR body.
2. **Prove the R2a spread is load-bearing** — this is the check that would have caught a single-pair
   suite. Temporarily encode `PetersonState.turn` as `out.writeBoolean(turn == 0)`; confirm the case
   still **passes** under a single-pair probe and goes red **only** because of the R2a spread; then
   re-encode as `out.writeInt(turn % 256)` and confirm the `255`/`256` boundary probe is what catches
   it; revert. A falsification check that shows the first version *surviving* is the evidence that the
   strengthened assertions are necessary.
3. Replace `PetersonState`'s `flag` encoding with a count of set bits; confirm the position probe
   (`[true,false]` vs `[false,true]`) goes red; revert.
4. Add a throwaway private **instance** field to one state class with no encoding case; confirm
   `everyStateField_hasAnEncodingCase` fails and names it; revert.
5. Add a `private static final` constant to one state class; confirm `everyStateField_hasAnEncodingCase`
   stays green; revert.
6. **Allowlist staleness — keep the entry, close the gap.** Temporarily add `threadCount` to
   `DynamicState.encodeTo` **while leaving the allowlist entry in place**; confirm
   `trackedGaps_areStillRealGaps` reports that entry as stale, i.e. that the field it names is no longer
   an encoding gap. Revert both.
   The direction matters: the entry must stay and the *encoding* must change. Deleting an allowlist entry
   by hand — the earlier wording of this check — proves nothing, because `everyStateField_hasAnEncodingCase`
   would then simply demand a parity case for `threadCount` and go red for an unrelated reason. The
   property under test is that the allowlist is **verified against `encodeTo`, not trusted**.

**R1 is not only a test — it also amends production Javadoc.** `SharedState.encodeTo`'s Javadoc must
come out stating both directions. There is no test for a Javadoc claim, so the criterion is that
`./gradlew javadoc` passes and the text is reviewed by a human; the reason it is in scope at all is
that the documented contract was what made the defect invisible.

## Constraints

- **Dependencies:** none. This spec is a leaf and is the entry point of the implementation order. It
  shares no code with 12.01 — different files, different property.
- **Backward compatibility:** the encoding *format* changes for `DeadlockState` only, gaining one
  boolean at the head. This is not a wire-format concern (nothing persists encodings across versions)
  but it **is** a test concern: any test asserting an exact encoded byte string, or an exact bitstate
  bit position, for `deadlock` will move. Grep for hardcoded expectations before merging — the
  `bitstate` metrics in Spec 12.03 depend on `statesMarked` and bit positions, so 12.03's falsification
  numbers are the most likely to need re-deriving.
- **`DynamicState` is out of scope** (R4). Its `decl` and `threadCount` omissions are unreachable today
  because a store never spans two programs — `InterleaveRunner` builds a fresh store per run
  (`InterleaveRunner.java:59–62`) and `BenchmarkHarness` gets one per strategy per program
  (`BenchmarkHarness.java:163–188`) — so the omission is latent for the same masking reason as
  `DeadlockState`, and for a different underlying reason. Fixing it belongs with the DSL specs that
  own `encodeTo` there.
- **No new state class may be added without parity cases** (R3). The completeness check is the
  enforcement; a new class with fields and no cases fails the build, which is intended.

## Commands

```bash
./gradlew test --tests "*StateEncodingFidelity*"
./gradlew clean test javadoc
./gradlew pitest          # confirm the global mutant total is unchanged
```

## Map

- `src/main/java/dev/samhb/interleave/core/DeadlockState.java` — `encodeTo` at `L45–48`, the defect;
  `control` in `deepCopy` `L40`, `equals` `L54`, `hashCode` `L60`
- `src/main/java/dev/samhb/interleave/core/SharedState.java` — `encodeTo` Javadoc `L27–34`: states only
  *equal → identical*, omits *distinct → distinct*. R1 amends it; it is **not** a contract the current
  defect violates (see §Current State)
- `src/main/java/dev/samhb/interleave/core/PetersonState.java` — correct, 4 fields
- `src/main/java/dev/samhb/interleave/core/CounterState.java` — correct, writes `registers.length`
- `src/main/java/dev/samhb/interleave/core/PairState.java` — correct, 6 fields
- `src/main/java/dev/samhb/interleave/core/DclState.java` — correct, two presence-boolean projections
- `src/main/java/dev/samhb/interleave/format/dsl/DynamicState.java` — latent gap, escalated to 09/10
- `src/main/java/dev/samhb/interleave/bugs/DeadlockWriteFlagStep.java` — `setControl(true)` at `L43`,
  what makes `control` live
- `src/main/java/dev/samhb/interleave/state/HashingStateStore.java` — key shape `L101–118`
- `src/main/java/dev/samhb/interleave/state/BitstateStore.java` — key shape `L355–357`
- `docs/specs/active/12-mutation-hardening/01-encoder-contract.md` — Spec 12.01, the composer, and the
  R1 test that found this
- `docs/specs/active/12-mutation-hardening/README.md` — implementation order; this spec is step 1